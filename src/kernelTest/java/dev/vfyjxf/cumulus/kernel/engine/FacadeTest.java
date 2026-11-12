package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void facadeChecksSettlesAndExplains() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("one")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air())).build();
        TestWorld.View view = new TestWorld.View().air(LatticePoint.origin);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        StructureManager structures = StructureManager.create();
        StructureKernel kernel = StructureKernel
                .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        StructureAttempt draft = structures
                .begin(TestWorld.world, new StructureSpec(OwnerId.of(id("owner")), siting, blueprint.id()));
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            draft.id(),
            OwnerId.of(id("owner")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, kernel.check(request));
        assertInstanceOf(MatchResult.Formed.class, kernel.settle(request, admitted, draft));
        draft.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
        assertEquals(1, ledger.revision());

        Explanation explanation = kernel.explain(Codes.claimConflict.code());
        assertNotNull(explanation);
        assertEquals(Codes.claimConflict.code(), explanation.code());
        FindingCode unknown = FindingCode.of(FindingCode.Family.spi, 9999);
        assertNull(kernel.explain(unknown));
        assertNotNull(kernel.explainOrFallback(unknown));
        assertFalse(kernel.explainOrFallback(unknown).summary().isBlank());
    }

    @Test
    void solveIsOrderedDedupedAndCapped() {
        RemedyProvider provider = new RemedyProvider() {
            @Override
            public NamespacedId id() {
                return FacadeTest.id("provider");
            }

            @Override
            public List<Remedy> solve(Finding finding, RemedyContext context) {
                Remedy repair = new Remedy.Repair(
                    new EditPlan(List.of(), "fix"),
                    Applicability.machineApplicable,
                    "fix"
                );
                Remedy preview = new Remedy.Preview(
                    new OverlayModel(Cuboid.of(0, 0, 0, 1, 1, 1), Map.of(), List.of()),
                    "preview"
                );
                return List.of(preview, repair, repair);
            }
        };
        Catalog<RemedyProvider> remedies = Catalog.<RemedyProvider>builder(id("remedies")).put(provider.id(), provider)
                .build();
        StructureKernel kernel = StructureKernel.create(Catalogs.builder().remedies(remedies).build());
        Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("missing")
                .label(Label.primary(Locus.local(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))), "missing")).build();
        Blueprint blueprint = Blueprint.builder(id("empty")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(
                    Check.of(
                        id("air"),
                        Bay.of(
                            id("bay"),
                            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
                            Coverage.everyCell,
                            Footprint.box(),
                            Sharing.exclusive
                        ),
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.air()
                    )
                ).build();
        RemedyContext context = new RemedyContext(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            null,
            0,
            null,
            null,
            2,
            null
        );
        SolveResult result = kernel.solve(FindingBundle.of(List.of(finding)), context);
        // the custom repair outranks the previews; two providers' previews never merge, so the cap keeps one
        assertEquals(2, result.remedies().size());
        assertInstanceOf(Remedy.Repair.class, result.remedies().get(0));
        assertInstanceOf(Remedy.Preview.class, result.remedies().get(1));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void solveMergesOneProvidersPreviewsIntoOneOverlay() {
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin());
        Bay bay = Bay.of(
            id("merge_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Blueprint blueprint = Blueprint.builder(id("merge")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(
            Check.of(id("merge_check"), bay, Focuses.occupant(LatticePoint.origin), Expectations.block(TestWorld.stone))
        ).build();
        CuboidSet cell = CuboidSet.of(Cuboid.of(LatticePoint.origin));
        Finding missing = Finding.builder(Codes.expected).phase(Phase.check).message("missing")
                .label(Label.primary(Locus.world(cell), "missing")).build();
        Finding blocked = Finding.builder(Codes.claimConflict).phase(Phase.check).message("blocked")
                .label(Label.primary(Locus.world(cell), "blocked")).build();
        Finding orientation = Finding.builder(FindingCode.of(FindingCode.Family.mc, 4), Severity.error)
                .phase(Phase.check).message("orientation").label(Label.primary(Locus.world(cell), "orientation"))
                .build();
        Finding cancelled = Finding.builder(Codes.cancelled).phase(Phase.check).message("cancelled")
                .label(Label.primary(Locus.world(cell), "cancelled")).build();
        MatchOutcome outcome = new MatchOutcome(
            new TestWorld.View().air(LatticePoint.origin),
            Map.of(),
            List.of(),
            ReadScope.none(),
            Spend.zero
        );
        RemedyContext context = new RemedyContext(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            null,
            0,
            outcome,
            null,
            8,
            null
        );

        SolveResult result = kernel.solve(FindingBundle.of(List.of(missing, blocked, orientation, cancelled)), context);

        List<Remedy.Preview> previews = new ArrayList<>();
        for (Remedy remedy : result.remedies()) {
            if (remedy instanceof Remedy.Preview preview) {
                previews.add(preview);
            }
        }
        assertEquals(1, previews.size(), () -> result.remedies().toString());
        OverlayModel overlay = previews.get(0).overlay();
        assertEquals(CellMark.blocked, overlay.cells().get(LatticeKey.of(LatticePoint.origin)));
        // the bundle sorts by severity then code, so the orientation finding precedes the cancellation
        assertEquals(List.of("orientation", "cancelled"), overlay.markers().stream().map(Marker::label).toList());
        assertEquals(blueprint.bounds(), overlay.bounds());
    }

    @Test
    void anEmptyHostCompilesChecksAndSettlesThroughAnAttempt() {
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin());
        assertNull(kernel.ledger());
        assertNull(kernel.document(id("nowhere")));
        assertSame(kernel.structures(), kernel.contexts());

        MatchResult.Formed formed = settleAndCommitAClaimFreeBlueprint(kernel);
        assertTrue(formed.claims().rows().isEmpty());
        assertEquals(0L, formed.ledgerRevision());
        assertEquals(1, kernel.structures().instances().size());
    }

    @Test
    void anEmptyHostSettlesClaimableRowsAsUndecided() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay bay = Bay.of(id("bay"), CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("box")).bounds(bounds)
                .check(Check.of(id("any"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
        TestWorld.View view = new TestWorld.View();
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.origin));
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.of(1, 0, 0)));
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin());

        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        OwnerId owner = OwnerId.of(id("owner"));
        StructureAttempt draft = kernel.structures()
                .begin(TestWorld.world, new StructureSpec(owner, siting, blueprint.id()));
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            draft.id(),
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, kernel.check(request));
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            kernel.settle(request, admitted, draft)
        );
        assertEquals(UndecidedReason.ledgerUnavailable, undecided.reason());
    }

    @Test
    void twoKernelsDoNotShareState() {
        StructureKernel first = StructureKernel.create(Catalogs.builtin());
        StructureKernel second = StructureKernel.create(Catalogs.builtin());
        settleAndCommitAClaimFreeBlueprint(first);
        assertEquals(1, first.structures().instances().size());
        assertTrue(second.structures().instances().isEmpty());
    }

    @Test
    void deriveReadsTheHostManagerAfterCommit() {
        ContextKey<Long> output = ContextKeys.of(id("derived"), Long.class, Retention.instance);
        Deriver<Long> deriver = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.found(7L);
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("constant_deriver"), deriver)
                .build();
        StructureKernel kernel = StructureKernel.create(Catalogs.builder().derivers(derivers).build());

        MatchResult.Formed formed = settleAndCommitAClaimFreeBlueprint(kernel);
        kernel.derive(formed, List.of(new DerivationSpec(id("constant_deriver"), Value.object(), output.id(), 0)));
        StructureInstance instance = kernel.structures().find(formed.instance());
        assertNotNull(instance);
        assertEquals(Long.valueOf(7L), instance.context().get(output));
    }

    @Test
    void solveCatchesAFailingProviderAsAProblemFinding() {
        RemedyProvider failing = new RemedyProvider() {
            @Override
            public NamespacedId id() {
                return FacadeTest.id("failing");
            }

            @Override
            public List<Remedy> solve(Finding finding, RemedyContext context) {
                throw new IllegalStateException("boom");
            }
        };
        Catalog<RemedyProvider> remedies = Catalog.<RemedyProvider>builder(id("remedies")).put(failing.id(), failing)
                .build();
        StructureKernel kernel = StructureKernel.create(Catalogs.builder().remedies(remedies).build());
        Bay bay = Bay.of(
            id("solve_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Blueprint blueprint = Blueprint.builder(id("empty")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("probe"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant()))
                .build();
        Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("missing")
                .label(Label.primary(Locus.local(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))), "missing")).build();
        RemedyContext context = new RemedyContext(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            null,
            0,
            null,
            null,
            4,
            null
        );

        SolveResult result = kernel.solve(FindingBundle.of(List.of(finding)), context);
        // the failing provider yields nothing; the built-in preview still answers SC-OCC-0001
        assertEquals(1, result.remedies().size());
        assertInstanceOf(Remedy.Preview.class, result.remedies().get(0));
        assertEquals(1, result.findings().findings().size());
        assertEquals(Codes.remedyFailed.code(), result.findings().findings().get(0).code());
    }

    private static MatchResult.Formed settleAndCommitAClaimFreeBlueprint(StructureKernel kernel) {
        BaySpec bay = new BaySpec(
            id("claim_free_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared,
            null,
            null,
            null
        );
        CheckSpec probe = new CheckSpec(
            id("claim_free_probe"),
            id("claim_free_bay"),
            NamespacedId.of("bay"),
            Value.object(),
            NamespacedId.of("each_cell"),
            Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("any_occupant"))))),
            null,
            null
        );
        BlueprintSpec root = new BlueprintSpec(
            id("claim_free"),
            Cuboid.of(0, 0, 0, 2, 1, 1),
            false,
            false,
            Value.object(),
            List.of(bay),
            List.of(),
            List.of(probe),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
        CompileResult compiled = kernel.compile(new Document(1, root, Map.of(), Map.of(), null));
        assertTrue(compiled.findings().isEmpty(), () -> compiled.findings().findings().toString());
        Blueprint blueprint = Objects.requireNonNull(compiled.blueprint(), "blueprint");

        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        OwnerId owner = OwnerId.of(id("owner"));
        TestWorld.View view = new TestWorld.View();
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.origin));
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.of(1, 0, 0)));
        StructureAttempt draft = kernel.structures()
                .begin(TestWorld.world, new StructureSpec(owner, siting, blueprint.id()));
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            draft.id(),
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, kernel.check(request));
        MatchResult.Formed formed = assertInstanceOf(MatchResult.Formed.class, kernel.settle(request, admitted, draft));
        draft.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)));
        return formed;
    }
}
