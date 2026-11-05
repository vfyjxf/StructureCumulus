package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
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
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            2
        );
        SolveResult result = kernel.solve(FindingBundle.of(List.of(finding)), context);
        assertEquals(2, result.remedies().size());
        assertInstanceOf(Remedy.Repair.class, result.remedies().get(0));
        assertTrue(result.findings().isEmpty());
    }
}
