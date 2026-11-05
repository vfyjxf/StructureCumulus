package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Opaque ids, real kernel values and TestWorld plumbing shared by the magic/claims stress batch. */
@NullMarked
final class MagicFixtures {

    static final OwnerId owner = OwnerId.of(id("owner"));
    static final OwnerId rival = OwnerId.of(id("rival"));
    static final InstanceId instance = new InstanceId(TestWorld.world, 1);
    static final InstanceId rivalInstance = new InstanceId(TestWorld.world, 2);

    private MagicFixtures() {}

    static NamespacedId id(String path) {
        return NamespacedId.of("magic_stress", path);
    }

    static NamespacedId mc(String path) {
        return NamespacedId.of("minecraft", path);
    }

    static Siting identity(LatticePoint origin) {
        return Siting.of(origin, Orientation.identity);
    }

    static Siting turn(LatticePoint origin, Turn turn) {
        return Siting.of(origin, Orientation.of(turn, Tilt.level, Flip.none));
    }

    static Siting orientation(LatticePoint origin, Orientation orientation) {
        return Siting.of(origin, orientation);
    }

    static Bay bay(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint, Sharing sharing) {
        return Bay.of(id, cells, coverage, footprint, sharing);
    }

    static Check<OccupantSnapshot> occupant(
        NamespacedId id,
        Bay bay,
        LatticePoint cell,
        Expectation<OccupantSnapshot> expectation
    ) {
        return Check.of(id, bay, Focuses.occupant(cell), expectation);
    }

    static Check<BayView> eachCell(NamespacedId id, Bay bay, Expectation<OccupantSnapshot> element) {
        return Check.of(id, bay, Focuses.bay(), Expectations.eachCell(element));
    }

    static TestWorld.View view() {
        return new TestWorld.View();
    }

    static ClaimLedger ledger() {
        return ClaimLedger.inMemory(TestWorld.world);
    }

    static LatticePoint world(Siting siting, LatticePoint local) {
        return Objects.requireNonNull(siting.toWorld(local), "the siting maps the cell");
    }

    static Cuboid worldBounds(Blueprint blueprint, Siting siting) {
        return Objects.requireNonNull(siting.toWorld(blueprint.bounds()), "the siting maps the bounds");
    }

    static Set<LatticeKey> worldKeys(Siting siting, CuboidSet localCells) {
        Set<LatticeKey> keys = new LinkedHashSet<>();
        for (LatticePoint local : points(localCells)) {
            keys.add(world(siting, local).key());
        }
        return keys;
    }

    static void fill(TestWorld.View view, Siting siting, CuboidSet localCells, NamespacedId type) {
        for (LatticePoint local : points(localCells)) {
            view.put(TestWorld.block(type, world(siting, local)));
        }
    }

    static void put(TestWorld.View view, Siting siting, LatticePoint local, NamespacedId type) {
        view.put(TestWorld.block(type, world(siting, local)));
    }

    static void air(TestWorld.View view, Siting siting, LatticePoint local) {
        view.air(world(siting, local));
    }

    static List<LatticePoint> points(CuboidSet cells) {
        List<LatticePoint> points = new ArrayList<>();
        for (Cuboid box : cells.boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        points.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return points;
    }

    static CuboidSet cellsAt(LatticePoint... cells) {
        Cuboid[] boxes = new Cuboid[cells.length];
        for (int i = 0; i < cells.length; i++) {
            boxes[i] = Cuboid.of(cells[i]);
        }
        return CuboidSet.of(boxes);
    }

    static List<LatticeKey> rowKeys(MatchResult.Formed formed) {
        List<LatticeKey> keys = new ArrayList<>();
        for (ClaimRow row : formed.claims().rows()) {
            keys.add(row.cell());
        }
        return keys;
    }

    static MatchRequest request(Blueprint blueprint, Siting siting, TestWorld.View view, ClaimView claims) {
        return request(blueprint, siting, view, claims, owner, instance);
    }

    static MatchRequest request(
        Blueprint blueprint,
        Siting siting,
        TestWorld.View view,
        ClaimView claims,
        OwnerId requestOwner,
        InstanceId requestInstance
    ) {
        return MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            requestInstance,
            requestOwner,
            Budget.unbounded(),
            claims
        );
    }

    static MatchResult check(Blueprint blueprint, Siting siting, TestWorld.View view, ClaimView claims) {
        return MatchEngine.check(request(blueprint, siting, view, claims));
    }

    static MatchResult check(
        Blueprint blueprint,
        Siting siting,
        TestWorld.View view,
        ClaimView claims,
        OwnerId requestOwner,
        InstanceId requestInstance
    ) {
        return MatchEngine.check(request(blueprint, siting, view, claims, requestOwner, requestInstance));
    }

    static MatchResult settle(Blueprint blueprint, Siting siting, TestWorld.View view, ClaimLedger ledger) {
        ClaimView claims = ledger.read(worldBounds(blueprint, siting));
        MatchRequest request = request(blueprint, siting, view, claims);
        MatchResult checked = MatchEngine.check(request);
        if (!(checked instanceof MatchResult.Admitted admitted)) {
            return checked;
        }
        return MatchEngine.settle(request, admitted, ledger, ContextStore.inMemory());
    }

    static boolean hasCode(MatchResult result, CodeEntry code) {
        return result.findings().findings().stream().anyMatch(finding -> finding.code().equals(code.code()));
    }
}
