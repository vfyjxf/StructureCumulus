package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Label;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.BayBuilder;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.blueprint.Requirement;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.CatalogsDraft;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ComputationContext;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.Merge;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.context.SectionContext;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.predicate.BayView;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.Expectations;
import dev.vfyjxf.cumulus.kernel.predicate.Focus;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §7.2, §13; 17 §2: one structure, a shell plus an owned cavity, a section fold and its aggregate. */
class FurnaceScenarioTest {

    private static final InstanceId INSTANCE = TestWorld.DEFAULT_INSTANCE;
    private static final int CELL = 2;
    private static final Cuboid BOUNDS = Cuboid.of(0, 0, 0, 7, 6, 7);
    private static final Cuboid CAVITY = Cuboid.of(1, 1, 1, 6, 5, 6);
    private static final Siting AT = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final NamespacedId SHELL = NamespacedId.of("minecraft", "blast_furnace");

    private static final ContextKey<Long> VOLUME = ContextKeys.of(id("volume"), Long.class,
            Retention.CONTEXT, Merges.sum(), null);
    private static final ContextKey<Long> CAPACITY = ContextKeys.of(id("capacity"), Long.class,
            Retention.CONTEXT, null, null);
    private static final Merge<String> JOIN = (left, right) -> left + ">" + right;
    private static final ContextKey<String> ORDER = ContextKeys.of(id("order"), String.class,
            Retention.CONTEXT, JOIN, null);

    @Test
    void shellAndCavityPartitionTheBounds() {
        TestWorld world = furnaceWorld();

        assertEquals(294L, BOUNDS.volume(), "7x6x7 bounds");
        assertEquals(100L, CAVITY.volume(), "the cavity is 5x4x5");
        assertEquals(194, world.occupants(BOUNDS, OccupantFilter.any()).size(),
                "17 §1: shell 194 = 294 - 100; the shell never counts the air it encloses");
        for (LatticePoint cell : TestWorld.cellsOf(CAVITY)) {
            OccupantSnapshot occupant = world.occupant(cell);
            assertNotNull(occupant, "every cavity cell is captured, air included: " + cell);
            assertTrue(occupant.isAir(), "the cavity is empty at " + cell);
        }
    }

    @Test
    void sectionContributionsFoldTo100AndTheAggregatePublishes400() {
        TestWorld world = furnaceWorld();
        TreeSet<SectionKey> sections = sectionsOf(world);
        assertEquals(27, sections.size(), "the 5x4x5 cavity straddles 27 sections at cellSize 2");

        ContextStore store = furnaceStore(world);
        ContextView atForm = store.context(INSTANCE).at(ContextTier.FORM);

        long folded = atForm.require(VOLUME).fold((volume, subjects) -> volume,
                findings -> fail("VOLUME must be Found after a complete fold", findings));
        assertEquals(100L, folded, "22 §7.2: 27 contributions fold to the cavity volume");

        String expected = sections.stream().map(FurnaceScenarioTest::label).collect(Collectors.joining(">"));
        assertEquals(expected, atForm.get(ORDER),
                "22 §7.2: contributions fold in SectionKey (x, z, y) order, never in arrival order");

        Computation<Long> capacity = capacityComputation();
        long capacityValue = capacity.compute(Attempt.aggregate(atForm, world)).fold(
                (value, subjects) -> value,
                findings -> fail("the aggregate reads the fold, never a zero", findings));
        assertEquals(400L, capacityValue, "22 §12.3: capacity = 4 x the folded volume = 400");

        ContextDraft settle = store.draft(ContextTier.SETTLE, store.revision());
        settle.put(CAPACITY, capacityValue);
        assertInstanceOf(Outcome.Found.class, store.publish(settle));

        ContextView view = store.context(INSTANCE);
        assertEquals(400L, view.get(CAPACITY), "the aggregate value is published as a CONTEXT key");
        assertEquals(100L, view.get(VOLUME), "and the fold is still visible under its own key");
    }

    @Test
    void theCheckReadsTheFoldedKeyThroughAContextFocus() {
        TestWorld world = furnaceWorld();
        ContextView published = furnaceStore(world).context(INSTANCE).at(ContextTier.FORM);
        Bay interior = interiorBay();

        assertEquals(CAVITY.asSet(), interior.cells().asSet(), "the cavity bay is one cuboid");
        assertEquals(Coverage.CUBIC, interior.coverage());
        assertEquals(ClaimMode.EXCLUSIVE, interior.mode());
        assertInstanceOf(ClaimScope.Owned.class, interior.scope(),
                "22 §10.2: the empty cavity declares Owned, so its 100 cells are protected");

        assertEquals(2, interior.requirement().checks().size(),
                "the cavity carries one per-cell air check and one context check");
        Check<?> check = interior.requirement().checks().stream()
                .filter(candidate -> candidate.id().equals(id("volume_at_least_64")))
                .findFirst().orElseThrow();
        assertEquals(id("interior"), check.bay(), "22 §6.2: a check names its bay by id");
        assertEquals(Long.class, check.focus().subject(), "Focuses.context(VOLUME) keeps the key's subject type");
        assertTrue(check.reads(CAVITY).keys().contains(VOLUME),
                "22 §6.2: a key-reading check declares the key, so the barrier orders it after the fold");

        Focus<Long> direct = Focuses.context(VOLUME);
        assertEquals(Long.class, direct.subject(), "Focuses.context(key) is typed by the key");

        ContextView atForm = published;
        FocusContext ctx = FocusContext.of(new BayView(id("interior"), CAVITY, world), AT, atForm,
                Stage.FORM, Locus.world(CAVITY.asSet()));
        assertInstanceOf(Reply.Match.class, check.run(ctx).reply(),
                "22 §6.3: the check sees the fold (100 >= 64), never a zero and never a short sum");

        assertEquals(StatePhase.SECTION, interior.requirement().computations().get(0).phase());
        assertEquals(CAVITY.asSet(), interior.requirement().computations().get(0).region());
        assertEquals(StatePhase.AGGREGATE, interior.requirement().computations().get(1).phase());
        assertTrue(interior.requirement().computations().get(1).inputs().contains(VOLUME),
                "22 §6.4: the aggregate declares the folded key as an input instead of reading it anonymously");
    }

    @Test
    void aKeyIsIdentifiedByItsIdAndTypeOnly() {
        CatalogsDraft draft = CatalogsDraft.create();
        assertSame(VOLUME, ContextKeys.register(draft, VOLUME));
        assertThrows(IllegalStateException.class, () -> ContextKeys.register(draft, VOLUME),
                "22 §6.4: SC-CTX-0102 — two declarations of one id are refused");
        assertEquals(VOLUME, ContextKeys.of(id("volume"), Long.class, Retention.ATTEMPT, null, null),
                "22 §6.4: equality is (id, type); retention and merge are properties, not identity");
    }

    private static TestWorld furnaceWorld() {
        return TestWorld.builder().cellSize(CELL).fill(BOUNDS, SHELL).fillAir(CAVITY).build();
    }

    private static ContextStore furnaceStore(TestWorld world) {
        ContextStore store = new ContextStore(INSTANCE);
        ContextDraft form = store.draft(ContextTier.FORM, 0L);
        Computation<Long> volume = volumeComputation();
        for (SectionKey section : sectionsOf(world)) {
            CuboidSet cells = CAVITY.asSet().intersect(TestWorld.sectionBounds(section, world.cellSize()).asSet());
            long partial = volume.compute(Attempt.section(new Section(section, cells, world), world)).fold(
                    (count, subjects) -> count,
                    findings -> fail("a readable section contributes one partial", findings));
            form.forSection(section).contribute(VOLUME, partial);
        }
        List<SectionKey> reversed = new ArrayList<>(sectionsOf(world));
        Collections.reverse(reversed);
        for (SectionKey section : reversed) {
            form.forSection(section).contribute(ORDER, label(section));
        }
        assertInstanceOf(Outcome.Found.class, store.publish(form),
                "22 §7.3: the barrier folds before any reader sees the keys");
        return store;
    }

    private static Bay interiorBay() {
        BayBuilder builder = BayBuilder.start(id("interior"), CAVITY, Coverage.CUBIC,
                ClaimMode.EXCLUSIVE, new ClaimScope.Owned(), "the hollow");
        Check<Long> check = new Check<>(id("volume_at_least_64"), Stage.FORM, builder.bayId(),
                Focuses.context(VOLUME), volumeExpectation(), null);
        Check<BayView> air = new Check<>(id("interior_air"), Stage.FORM, builder.bayId(),
                Focuses.bay(), Expectations.eachCell(Expectations.air()), null);
        return builder.attach(new Requirement(List.of(check, air),
                List.of(volumeComputation(), capacityComputation())));
    }

    private static TreeSet<SectionKey> sectionsOf(TestWorld world) {
        TreeSet<SectionKey> sections = new TreeSet<>();
        for (LatticePoint cell : TestWorld.cellsOf(CAVITY)) {
            sections.add(SectionKey.of(world.dimension(), cell, world.cellSize()));
        }
        return sections;
    }

    private static String label(SectionKey section) {
        return section.x() + "," + section.y() + "," + section.z();
    }

    private static Computation<Long> volumeComputation() {
        return new Computation<Long>() {

            @Override
            public NamespacedId id() {
                return FurnaceScenarioTest.id("volume");
            }

            @Override
            public Stage stage() {
                return Stage.FORM;
            }

            @Override
            public StatePhase phase() {
                return StatePhase.SECTION;
            }

            @Override
            public ContextKey<Long> output() {
                return VOLUME;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return CAVITY.asSet();
            }

            @Override
            public Outcome<Long> compute(ComputationContext ctx) {
                SectionContext section = ctx.section();
                if (section == null) {
                    throw new AssertionError("a SECTION computation always runs with a section");
                }
                return new Outcome.Found<>(section.cells().intersect(CAVITY.asSet()).volume(), List.of(),
                        FindingBundle.empty());
            }
        };
    }

    private static Computation<Long> capacityComputation() {
        return new Computation<Long>() {

            @Override
            public NamespacedId id() {
                return FurnaceScenarioTest.id("capacity");
            }

            @Override
            public Stage stage() {
                return Stage.FORM;
            }

            @Override
            public StatePhase phase() {
                return StatePhase.AGGREGATE;
            }

            @Override
            public ContextKey<Long> output() {
                return CAPACITY;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of(VOLUME);
            }

            @Override
            public CuboidSet region() {
                return CAVITY.asSet();
            }

            @Override
            public Outcome<Long> compute(ComputationContext ctx) {
                return ctx.read(VOLUME).fold(
                        (volume, subjects) -> new Outcome.Found<>(4L * volume, List.of(), FindingBundle.empty()),
                        findings -> new Outcome.Missing<>(findings));
            }
        };
    }

    private static Expectation<Long> volumeExpectation() {
        return new Expectation<Long>() {

            @Override
            public Class<Long> subject() {
                return Long.class;
            }

            @Override
            public Reply test(ExpectationContext<Long> ctx) {
                long volume = ctx.subject();
                if (volume >= 64L) {
                    return Reply.match();
                }
                return Reply.mismatch(Finding.builder(FindingCode.of("SC-EXP-0001"))
                        .severity(Severity.ERROR)
                        .message("expected at least 64 cavity cells, found " + volume)
                        .label(ctx.locus(), "the cavity is smaller than the declared minimum")
                        .stage(ctx.stage()).build());
            }
        };
    }

    /** The engine's two contexts over one fixture: a section run has no {@code read}, an aggregate has no section. */
    private static final class Attempt implements ComputationContext {

        private final SectionContext section;
        private final ContextView view;
        private final TestWorld world;

        private Attempt(SectionContext section, ContextView view, TestWorld world) {
            this.section = section;
            this.view = view;
            this.world = world;
        }

        static Attempt section(SectionContext section, TestWorld world) {
            return new Attempt(section, StructureContext.empty(TestWorld.DEFAULT_INSTANCE), world);
        }

        static Attempt aggregate(ContextView view, TestWorld world) {
            return new Attempt(null, view, world);
        }

        @Override
        public SectionContext section() {
            return section;
        }

        @Override
        public <T> Outcome<T> read(ContextKey<T> key) {
            if (section != null) {
                throw new AssertionError("P6: a SECTION unit must not read " + key.id());
            }
            return view.require(key);
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box) {
            return world.occupants(box, OccupantFilter.any());
        }

        @Override
        public ContextView context() {
            return view;
        }
    }

    private record Section(SectionKey section, CuboidSet cells, TestWorld world) implements SectionContext {

        @Override
        public List<OccupantSnapshot> occupants() {
            Cuboid bounds = cells.bounds();
            return bounds == null ? List.of() : world.occupants(bounds, OccupantFilter.any());
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box) {
            return world.occupants(box, OccupantFilter.any());
        }
    }

    private static <T> T fail(String message, FindingBundle findings) {
        throw new AssertionError(message + ": " + findings);
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("mymod", path);
    }
}
