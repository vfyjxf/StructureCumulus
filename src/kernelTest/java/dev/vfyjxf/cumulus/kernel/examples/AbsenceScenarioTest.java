package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.LocusFrame;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.occupant.ReadStatus;
import dev.vfyjxf.cumulus.kernel.occupant.SectionSlot;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 22 §7.2, §6.2, §6.6, P5; 17 §5: a hole defeats the fold, and absence reaches the check as UNREADABLE. */
class AbsenceScenarioTest {

    private static final NamespacedId DIMENSION = TestWorld.DEFAULT_DIMENSION;
    private static final int CELL = 4;
    private static final NamespacedId STONE = NamespacedId.of("minecraft", "stone");
    private static final LatticePoint FIRST = LatticePoint.of(0, 0, 0);
    private static final LatticePoint MIDDLE = LatticePoint.of(4, 0, 0);
    private static final LatticePoint LAST = LatticePoint.of(8, 0, 0);
    private static final Cuboid REGION = Cuboid.of(0, 0, 0, 9, 1, 1);
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final List<SectionKey> SECTIONS = List.of(
            SectionKey.of(DIMENSION, FIRST, CELL),
            SectionKey.of(DIMENSION, MIDDLE, CELL),
            SectionKey.of(DIMENSION, LAST, CELL));
    private static final ContextKey<Long> VOLUME = ContextKeys.of(NamespacedId.of("mymod", "volume"),
            Long.class, Retention.CONTEXT, Merges.sum(), null);

    @Test
    void aRefusedMiddleSectionMakesTheKeyAbsentInsteadOfShort() {
        TestWorld world = threeSections();

        SectionSlot middle = world.slot(SECTIONS.get(1));
        assertInstanceOf(SectionSlot.Refused.class, middle, "a section that was never captured is refused");
        assertEquals(ReadStatus.NOT_CAPTURED, ((SectionSlot.Refused) middle).status());
        assertFalse(middle.readable(), "a refused slot is never readable");
        assertNull(world.occupant(MIDDLE), "22 §5.1: a refused cell is never air, so it reads as null");
        assertEquals(2L, SECTIONS.stream().filter(section -> world.slot(section).readable()).count(),
                "two of three sections are readable; the short sum 2 exists as data and must not surface");

        ContextView view = foldThreeSections(world);
        assertNull(view.get(VOLUME),
                "22 §7.2: a refused section makes the key absent, never a short sum and never an identity");
        assertNotEquals(2L, view.get(VOLUME), "P5: absence is never a number");

        Outcome.Missing<Long> missing = assertInstanceOf(Outcome.Missing.class,
                Focuses.context(VOLUME).take(context(world, view)),
                "22 §6.3: Focuses.context(key) is exactly require(key), and absence is Missing");
        assertFalse(missing.findings().isEmpty(), "a missing key carries findings, never silence");
        Locus locus = missing.findings().primary() == null ? null : missing.findings().primary().primaryLocus();
        assertNotNull(locus, "the absence has a primary label");
        assertEquals(LocusFrame.KEY, locus.frame(), "22 §6.6: a context absence is labelled on the KEY locus");
    }

    @Test
    void theCheckIsUnreadableAndTheExpectationIsNeverCalled() {
        TestWorld world = threeSections();
        FocusContext ctx = context(world, foldThreeSections(world));
        AtomicInteger expectationCalls = new AtomicInteger();
        Expectation<Long> spy = new Expectation<>() {
            @Override
            public Class<Long> subject() {
                return Long.class;
            }

            @Override
            public Reply test(ExpectationContext<Long> ctx) {
                expectationCalls.incrementAndGet();
                return Reply.match();
            }
        };
        BayBuilder bay = BayBuilder.start(NamespacedId.of("mymod", "region"),
                Cuboid.of(0, 0, 0, 1, 1, 1), Coverage.PRESENCE, ClaimMode.SHARED, new ClaimScope.Audit());
        Check<Long> check = new Check<>(NamespacedId.of("mymod", "volume_is_100"), Stage.FORM,
                bay.bayId(), Focuses.context(VOLUME), spy, null);
        bay.attach(new Requirement(List.of(check), List.of()));

        assertInstanceOf(Reply.Unreadable.class, check.run(ctx).reply(),
                "22 §6.2: a missing key folds to UNREADABLE, never to MISMATCH — nothing was refuted");
        assertEquals(0, expectationCalls.get(),
                "22 §6.6: a missing subject is never test(null) and never silence");
    }

    private static FocusContext context(TestWorld world, ContextView view) {
        return FocusContext.of(new BayView(NamespacedId.of("mymod", "region"), REGION, world), AT, view,
                Stage.FORM, Locus.local(REGION.asSet()));
    }

    private static TestWorld threeSections() {
        return TestWorld.builder()
                .cellSize(CELL)
                .putBlock(FIRST, STONE)
                .refuseSection(MIDDLE)
                .putBlock(LAST, STONE)
                .build();
    }

    private static ContextView foldThreeSections(TestWorld world) {
        ContextStore store = new ContextStore(TestWorld.DEFAULT_INSTANCE);
        ContextDraft draft = store.draft(ContextTier.FORM, 0L);
        for (SectionKey section : SECTIONS) {
            if (world.slot(section).readable()) {
                draft.forSection(section).contribute(VOLUME, 1L);
            } else {
                draft.refuse(VOLUME);
            }
        }
        assertInstanceOf(Outcome.Found.class, store.publish(draft));
        return store.context(TestWorld.DEFAULT_INSTANCE);
    }
}
