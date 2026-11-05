package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
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
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Opaque tech ids, char patterns, sited fills and the kernel plumbing shared by the tech stress batch. */
@NullMarked
final class TechFixtures {

    static final OwnerId owner = OwnerId.of(id("owner"));
    static final OwnerId rival = OwnerId.of(id("rival"));
    static final InstanceId instance = new InstanceId(TestWorld.world, 1);
    static final InstanceId rivalInstance = new InstanceId(TestWorld.world, 2);

    static final NamespacedId casing = id("opaque/casing");
    static final NamespacedId hotCoil = id("opaque/coil_hot");
    static final NamespacedId coldCoil = id("opaque/coil_cold");
    static final NamespacedId hatch = id("opaque/hatch");
    static final NamespacedId cpu = id("opaque/cpu");
    static final NamespacedId storage = id("opaque/storage");
    static final NamespacedId refineryCasing = id("opaque/refinery_casing");
    static final NamespacedId refineryOutput = id("opaque/refinery_output");
    static final NamespacedId controller = id("opaque/controller");
    static final NamespacedId fusionTorus = id("opaque/fusion_torus");
    static final NamespacedId anchorBlock = id("opaque/anchor");
    static final NamespacedId cow = id("opaque/cow");
    static final NamespacedId crystal = id("opaque/crystal");

    private TechFixtures() {}

    static NamespacedId id(String path) {
        return NamespacedId.of("tech_stress", path);
    }

    static LatticePoint p(int x, int y, int z) {
        return LatticePoint.of(x, y, z);
    }

    static Cuboid box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static Cuboid cell(LatticePoint point) {
        return Cuboid.of(point);
    }

    static CuboidSet cells(Cuboid... boxes) {
        return CuboidSet.of(boxes);
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

    static ClaimView emptyClaims() {
        return new ClaimView(List.of(), 0, true);
    }

    static ClaimView claims(ClaimLedger ledger, Blueprint blueprint, Siting siting) {
        return ledger.read(Objects.requireNonNull(siting.toWorld(blueprint.bounds()), "the bounds map"));
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

    static MatchResult check(Blueprint blueprint, Siting siting, TestWorld.View view) {
        return check(blueprint, siting, view, emptyClaims());
    }

    static MatchResult settle(Blueprint blueprint, Siting siting, TestWorld.View view, ClaimLedger ledger) {
        return settle(blueprint, siting, view, ledger, owner, instance);
    }

    static MatchResult settle(
        Blueprint blueprint,
        Siting siting,
        TestWorld.View view,
        ClaimLedger ledger,
        OwnerId requestOwner,
        InstanceId requestInstance
    ) {
        MatchRequest request = request(
            blueprint,
            siting,
            view,
            claims(ledger, blueprint, siting),
            requestOwner,
            requestInstance
        );
        MatchResult checked = MatchEngine.check(request);
        if (!(checked instanceof MatchResult.Admitted admitted)) {
            return checked;
        }
        return MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request));
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

    static List<ClaimRow> rows(MatchResult.Formed formed) {
        return formed.claims().rows();
    }

    static List<LatticeKey> rowKeys(MatchResult.Formed formed) {
        List<LatticeKey> keys = new ArrayList<>();
        for (ClaimRow row : formed.claims().rows()) {
            keys.add(row.cell());
        }
        return keys;
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

    static boolean hasCode(MatchResult result, CodeEntry code) {
        return result.findings().findings().stream().anyMatch(finding -> finding.code().equals(code.code()));
    }

    static OccupantSnapshot entity(NamespacedId type, LatticePoint anchor, Cuboid footprint) {
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, type, OccupantKind.entity, anchor);
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity);
        return new OccupantSnapshot(ref, descriptor, footprint, List.of());
    }

    static Pattern pattern(String... layers) {
        return Pattern.parse(layers);
    }

    /** One string per y layer, '/' between z rows, one char per x cell; '.' and ' ' are air. */
    static final class Pattern {

        private final List<List<String>> layers;
        private final int width;
        private final int height;
        private final int depth;

        private Pattern(List<List<String>> layers, int width, int height, int depth) {
            this.layers = layers;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        static Pattern parse(String... layers) {
            if (layers.length == 0) {
                throw new IllegalArgumentException("a pattern needs at least one layer");
            }
            List<List<String>> parsed = new ArrayList<>(layers.length);
            String[] firstRows = layers[0].split("/", -1);
            int depth = firstRows.length;
            int width = firstRows[0].length();
            if (width == 0) {
                throw new IllegalArgumentException("a pattern row must not be empty");
            }
            for (String layer : layers) {
                String[] rows = layer.split("/", -1);
                if (rows.length != depth) {
                    throw new IllegalArgumentException("every layer must have " + depth + " rows");
                }
                for (String row : rows) {
                    if (row.length() != width) {
                        throw new IllegalArgumentException("every row must have " + width + " chars");
                    }
                }
                parsed.add(List.of(rows));
            }
            return new Pattern(List.copyOf(parsed), width, parsed.size(), depth);
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        int depth() {
            return depth;
        }

        Cuboid bounds() {
            return box(0, 0, 0, width, height, depth);
        }

        char at(int x, int y, int z) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
                throw new IllegalArgumentException("outside the pattern: " + x + "," + y + "," + z);
            }
            return layers.get(y).get(z).charAt(x);
        }

        List<LatticePoint> points(char... kinds) {
            Set<Character> wanted = new LinkedHashSet<>();
            for (char kind : kinds) {
                wanted.add(kind);
            }
            List<LatticePoint> points = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        if (wanted.contains(at(x, y, z))) {
                            points.add(LatticePoint.of(x, y, z));
                        }
                    }
                }
            }
            return points;
        }

        CuboidSet cells(char... kinds) {
            List<LatticePoint> points = points(kinds);
            Cuboid[] boxes = new Cuboid[points.size()];
            for (int i = 0; i < points.size(); i++) {
                boxes[i] = Cuboid.of(points.get(i));
            }
            return CuboidSet.of(boxes);
        }

        void fill(TestWorld.View view, Siting siting, Map<Character, NamespacedId> legend) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        char kind = at(x, y, z);
                        if (kind == '.' || kind == ' ') {
                            continue;
                        }
                        NamespacedId type = legend.get(kind);
                        if (type == null) {
                            throw new IllegalArgumentException("no legend entry for '" + kind + "'");
                        }
                        put(view, siting, LatticePoint.of(x, y, z), type);
                    }
                }
            }
        }

        void fill(TestWorld.View view, Siting siting, char kind, NamespacedId type) {
            fill(view, siting, Map.of(kind, type));
        }
    }
}
