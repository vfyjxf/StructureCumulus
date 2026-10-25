package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextStoreTest {

    private static final NamespacedId WORLD = NamespacedId.of("test", "world");
    private static final InstanceId INSTANCE = InstanceId.of(WORLD, 1L);

    private static final ContextKey<String> WORD = ContextKeys.of(
            NamespacedId.of("test", "word"), String.class, Retention.CONTEXT,
            Merges.combine((left, right) -> left + right), null);

    private static final ContextKey<Integer> COUNT = ContextKeys.of(
            NamespacedId.of("test", "count"), Integer.class, Retention.CONTEXT, null, null);

    private static final ContextKey<Integer> OPTIONAL = ContextKeys.of(
            NamespacedId.of("test", "optional"), Integer.class, Retention.CONTEXT,
            Merges.sum(), 7);

    private static final ContextKey<Integer> SETTLED = ContextKeys.of(
            NamespacedId.of("test", "settled"), Integer.class, Retention.CONTEXT, null, null);

    private static SectionKey section(int x, int y, int z) {
        return new SectionKey(WORLD, x, y, z);
    }

    @Test
    void contributionsFoldInCanonicalSectionOrder() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, store.revision());
        draft.forSection(section(1, 0, 0)).contribute(WORD, "a");
        draft.forSection(section(0, 0, 1)).contribute(WORD, "b");
        draft.forSection(section(0, 1, 0)).contribute(WORD, "c");

        assertTrue(store.publish(draft) instanceof Outcome.Found);

        assertEquals("cba", store.context(INSTANCE).get(WORD));
    }

    @Test
    void refusedSectionDefeatsTheFoldAndShadowsAnOlderValue() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft first = store.draft(ContextTier.FORM, store.revision());
        first.forSection(section(0, 0, 0)).contribute(WORD, "kept");
        store.publish(first);

        ContextDraft second = store.draft(ContextTier.FORM, store.revision());
        second.forSection(section(0, 0, 0)).contribute(WORD, "short");
        second.refuse(WORD);
        store.publish(second);

        assertNull(store.context(INSTANCE).get(WORD));
        assertTrue(store.context(INSTANCE).require(WORD) instanceof Outcome.Missing);
    }

    @Test
    void whenAbsentAppliesOnlyWhenNothingWasContributed() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, store.revision());
        draft.declined(OPTIONAL);
        store.publish(draft);

        assertEquals(Integer.valueOf(7), store.context(INSTANCE).get(OPTIONAL));
    }

    @Test
    void aStaleBaseIsRefusedAndWritesNothing() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft first = store.draft(ContextTier.FORM, 0L);
        first.put(COUNT, 1);
        store.publish(first);

        ContextDraft stale = store.draft(ContextTier.FORM, 0L);
        stale.put(COUNT, 2);
        Outcome<Void> refused = store.publish(stale);

        assertTrue(refused instanceof Outcome.Missing);
        assertEquals(1L, store.context(INSTANCE).revision());
        assertEquals(Integer.valueOf(1), store.context(INSTANCE).get(COUNT));
    }

    @Test
    void twoDifferentValuesInOneFrameAreRefusedButAnIdenticalDuplicateIsNot() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft conflicting = store.draft(ContextTier.FORM, store.revision());
        conflicting.put(COUNT, 1);
        conflicting.put(COUNT, 2);
        assertTrue(store.publish(conflicting) instanceof Outcome.Missing);
        assertNull(store.context(INSTANCE).get(COUNT));

        ContextDraft duplicate = store.draft(ContextTier.FORM, store.revision());
        duplicate.put(COUNT, 4);
        duplicate.put(COUNT, 4);
        assertTrue(store.publish(duplicate) instanceof Outcome.Found);
        assertEquals(Integer.valueOf(4), store.context(INSTANCE).get(COUNT));
    }

    @Test
    void theWrongVerbForTheKeyIsRefused() {
        ContextStore store = new ContextStore(INSTANCE);

        IllegalStateException merged = assertThrows(IllegalStateException.class,
                () -> store.draft(ContextTier.FORM, 0L).put(WORD, "x"));
        assertTrue(Objects.requireNonNull(merged.getMessage()).contains("SC-CTX-0109"));

        IllegalStateException single = assertThrows(IllegalStateException.class,
                () -> store.draft(ContextTier.FORM, 0L).contribute(COUNT, 1));
        assertTrue(Objects.requireNonNull(single.getMessage()).contains("SC-CTX-0109"));
    }

    @Test
    void readsAreTierStamped() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft form = store.draft(ContextTier.FORM, store.revision());
        form.forSection(section(0, 0, 0)).contribute(WORD, "form");
        form.put(COUNT, 3);
        store.publish(form);

        ContextDraft settle = store.draft(ContextTier.SETTLE, store.revision());
        settle.put(SETTLED, 9);
        store.publish(settle);

        StructureContext context = store.context(INSTANCE);
        assertNull(context.at(ContextTier.PROBE).get(COUNT));
        assertEquals(Integer.valueOf(3), context.at(ContextTier.FORM).get(COUNT));
        assertEquals(Integer.valueOf(3), context.get(COUNT));
        assertNull(context.at(ContextTier.FORM).get(SETTLED));
        assertEquals(Integer.valueOf(9), context.get(SETTLED));
        assertTrue(context.at(ContextTier.FORM).keys().contains(COUNT));
        assertFalse(context.at(ContextTier.FORM).keys().contains(SETTLED));
    }

    @Test
    void aPublishNeverMutatesAnOlderContext() {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft first = store.draft(ContextTier.FORM, 0L);
        first.put(COUNT, 1);
        store.publish(first);
        StructureContext before = store.context(INSTANCE);

        ContextDraft second = store.draft(ContextTier.FORM, store.revision());
        second.put(COUNT, 2);
        store.publish(second);

        assertEquals(Integer.valueOf(1), before.get(COUNT));
        assertEquals(1, before.frames().size());
        assertEquals(Integer.valueOf(2), store.context(INSTANCE).get(COUNT));
    }

    @Test
    void aKeyIsEqualByIdAndTypeOnly() {
        ContextKey<Integer> first = ContextKeys.of(COUNT.id(), Integer.class, Retention.OUTCOME, null, null);
        ContextKey<Integer> second = ContextKeys.of(COUNT.id(), Integer.class, Retention.CONTEXT, Merges.sum(), 4);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(0, first.compareTo(second));
    }

    @Test
    void aSecondRegistrationOfOneIdIsRefused() {
        CatalogsDraft catalogs = CatalogsDraft.create();
        ContextKeys.register(catalogs, COUNT);

        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> ContextKeys.register(catalogs, ContextKeys.of(
                        COUNT.id(), String.class, Retention.CONTEXT, null, null)));
        assertTrue(Objects.requireNonNull(duplicate.getMessage()).contains("SC-CTX-0102"));
    }
}
