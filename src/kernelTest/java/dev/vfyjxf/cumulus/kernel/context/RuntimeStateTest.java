package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStateTest {

    private static final InstanceId INSTANCE = InstanceId.of(NamespacedId.of("test", "world"), 1L);

    private static final AugmentKey BONUS = AugmentKey.of(NamespacedId.of("test", "bonus"), AugmentFold.SUM);
    private static final AugmentKey CAP = AugmentKey.of(NamespacedId.of("test", "cap"), AugmentFold.MAX);
    private static final AugmentKey FLOOR = AugmentKey.of(NamespacedId.of("test", "floor"), AugmentFold.MIN);
    private static final AugmentKey SCALE = AugmentKey.of(NamespacedId.of("test", "scale"), AugmentFold.MULTIPLY);
    private static final AugmentKey OVERRIDE = AugmentKey.of(NamespacedId.of("test", "override"), AugmentFold.SET);
    private static final AugmentKey HUGE = AugmentKey.of(NamespacedId.of("test", "huge"), AugmentFold.SUM);

    private static final ContextKey<Integer> COUNTER = ContextKeys.of(
            NamespacedId.of("test", "counter"), Integer.class, Retention.CONTEXT, null, null);

    private static final ContextKey<Integer> ATTEMPT_ONLY = ContextKeys.of(
            NamespacedId.of("test", "attempt"), Integer.class, Retention.ATTEMPT, null, null);

    @Test
    void augmentsFoldPerKeyInDeclarationOrder() {
        RuntimeState state = RuntimeState.of(StructureContext.empty(INSTANCE), List.of(
                new Augment(BONUS, 3.0),
                new Augment(CAP, 2.0),
                new Augment(BONUS, 1.5),
                new Augment(CAP, 8.0),
                new Augment(FLOOR, 9.0),
                new Augment(FLOOR, 4.0),
                new Augment(SCALE, 2.0),
                new Augment(SCALE, 3.0),
                new Augment(OVERRIDE, 1.0),
                new Augment(OVERRIDE, 5.0)), 0L);

        assertEquals(4.5, state.augments().get(BONUS));
        assertEquals(8.0, state.augments().get(CAP));
        assertEquals(4.0, state.augments().get(FLOOR));
        assertEquals(6.0, state.augments().get(SCALE));
        assertEquals(5.0, state.augments().get(OVERRIDE));
        assertTrue(state.findings().isEmpty());
    }

    @Test
    void anUnrepresentedKeyIsAbsentRatherThanZeroOrOne() {
        RuntimeState state = RuntimeState.of(StructureContext.empty(INSTANCE), List.of(), 0L);

        assertTrue(state.augments().isEmpty());
        assertFalse(state.augments().containsKey(BONUS));
    }

    @Test
    void aNonFiniteAmountIsRefusedAtConstruction() {
        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class,
                () -> new Augment(BONUS, Double.NaN));
        assertEquals("amount", nan.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Augment(BONUS, Double.POSITIVE_INFINITY));
    }

    @Test
    void aNonFiniteTotalIsOmittedAndReported() {
        RuntimeState state = RuntimeState.of(StructureContext.empty(INSTANCE), List.of(
                new Augment(HUGE, 1e308),
                new Augment(HUGE, 1e308),
                new Augment(BONUS, 1.0)), 0L);

        assertFalse(state.augments().containsKey(HUGE));
        assertEquals(1.0, state.augments().get(BONUS));
        List<Finding> findings = state.findings();
        assertEquals(1, findings.size());
        assertEquals("SC-CTX-0301", findings.get(0).code().text());
    }

    @Test
    void upgradePublishesAtTheNextRevisionAndLeavesTheOldContextAlone() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, 0L);
        draft.put(COUNTER, 4);
        store.publish(draft);
        StructureContext before = store.context(INSTANCE);

        RuntimeState state = RuntimeState.of(before, List.of(), before.revision());
        RuntimeState upgraded = state.upgrade(COUNTER, 144);

        assertEquals(before.revision() + 1, upgraded.context().revision());
        assertEquals(Integer.valueOf(144), upgraded.context().get(COUNTER));
        assertEquals(Integer.valueOf(4), before.get(COUNTER));
    }

    @Test
    void upgradeRefusesAKeyThatIsNotContextRetained() {
        StructureContext context = StructureContext.empty(INSTANCE);
        RuntimeState state = RuntimeState.of(context, List.of(), context.revision());

        assertThrows(IllegalArgumentException.class, () -> state.upgrade(ATTEMPT_ONLY, 1));
    }

    @Test
    void upgradeRefusesWhenTheBaseMoved() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, 0L);
        draft.put(COUNTER, 4);
        store.publish(draft);
        StructureContext current = store.context(INSTANCE);

        RuntimeState stale = RuntimeState.of(current, List.of(), 0L);
        RuntimeState refused = stale.upgrade(COUNTER, 144);

        assertSame(current, refused.context());
        assertEquals(1, refused.findings().size());
        assertEquals("SC-CTX-0108", refused.findings().get(0).code().text());
    }
}
