package dev.vfyjxf.cumulus.kernel.engine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
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
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
class FacadeTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void facadeChecksSettlesAndExplains() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.EVERY_CELL, Footprint.box(),
                Sharing.EXCLUSIVE);
        Blueprint blueprint = Blueprint.builder(id("one"))
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.ORIGIN),
                        Expectations.air())).build();
        TestWorld.View view = new TestWorld.View().air(LatticePoint.ORIGIN);
        TestWorld.Ledger ledger = new TestWorld.Ledger();
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin(),
                new Host(DocumentSource.empty(), ledger, ContextStore.inMemory(), Map.of()));
        MatchRequest request = MatchRequest.of(blueprint, Siting.of(LatticePoint.ORIGIN,
                        Orientation.IDENTITY), new TestWorld.Lease(view),
                new InstanceId(TestWorld.WORLD, 1),
                OwnerId.of(id("owner")), Budget.unbounded(), new ClaimView(List.of(), 0, true));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class,
                kernel.check(request));
        assertInstanceOf(MatchResult.Formed.class, kernel.settle(request, admitted));
        assertEquals(1, ledger.revision());

        Explanation explanation = kernel.explain(Codes.CLAIM_CONFLICT.code());
        assertNotNull(explanation);
        assertEquals(Codes.CLAIM_CONFLICT.code(), explanation.code());
        FindingCode unknown = FindingCode.of(FindingCode.Family.SPI, 9999);
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
                Remedy repair = new Remedy.Repair(new EditPlan(List.of(), "fix"),
                        Applicability.MACHINE_APPLICABLE, "fix");
                Remedy preview = new Remedy.Preview(new OverlayModel(
                        Cuboid.of(0, 0, 0, 1, 1, 1), Map.of(), List.of()), "preview");
                return List.of(preview, repair, repair);
            }
        };
        Catalog<RemedyProvider> remedies = Catalog.<RemedyProvider>builder(id("remedies"))
                .put(provider.id(), provider).build();
        StructureKernel kernel = StructureKernel.create(
                Catalogs.builder().remedies(remedies).build());
        Finding finding = Finding.builder(Codes.UNREADABLE_CELL).phase(Phase.CHECK)
                .message("missing").label(Label.primary(Locus.local(
                        CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))), "missing")).build();
        Blueprint blueprint = Blueprint.builder(id("empty"))
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), Bay.of(id("bay"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
                                Coverage.EVERY_CELL, Footprint.box(), Sharing.EXCLUSIVE),
                        Focuses.occupant(LatticePoint.ORIGIN), Expectations.air())).build();
        RemedyContext context = new RemedyContext(blueprint, Siting.of(LatticePoint.ORIGIN,
                Orientation.IDENTITY), null, 0, null, null, 2);
        SolveResult result = kernel.solve(FindingBundle.of(List.of(finding)), context);
        assertEquals(2, result.remedies().size());
        assertInstanceOf(Remedy.Repair.class, result.remedies().get(0));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void sitingReportsWhyItCannotPlace() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.EVERY_CELL, Footprint.box(),
                Sharing.EXCLUSIVE);
        Blueprint blueprint = Blueprint.builder(id("unplaced"))
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.ORIGIN),
                        Expectations.air())).build();
        TestWorld.View view = new TestWorld.View().air(LatticePoint.ORIGIN);
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin());
        SitingResult result =
                kernel.siting(blueprint, LatticePoint.ORIGIN, view);
        assertFalse(result.succeeded());
        assertNotNull(result.findings().primary());
        assertEquals(Codes.ANCHOR_INVALID.code(), result.findings().primary().code());
    }
}
