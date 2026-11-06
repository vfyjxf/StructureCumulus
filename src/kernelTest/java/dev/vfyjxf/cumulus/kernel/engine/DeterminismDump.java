package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.ComputationSpec;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Renders one fixed check/settle/derive scenario as canonical text; the determinism gate diffs it. */
public final class DeterminismDump {

    private static final NamespacedId cow = NamespacedId.of("minecraft", "cow");
    private static final LatticePoint center = LatticePoint.of(1, 0, 1);
    private static final LatticePoint plateCell = LatticePoint.of(1, 1, 1);
    private static final LatticePoint penCell = LatticePoint.of(2, 1, 0);
    private static final CuboidSet baseCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 3));
    private static final CuboidSet bayCells = baseCells.union(CuboidSet.of(Cuboid.of(plateCell)));
    private static final CuboidSet hollowCells = CuboidSet.of(Cuboid.of(0, 1, 0, 3, 2, 3))
            .subtract(CuboidSet.of(Cuboid.of(plateCell))).subtract(CuboidSet.of(Cuboid.of(penCell)));
    private static final ContextKey<Long> airCells = ContextKeys
            .of(id("air_cells"), Long.class, Retention.instance, Merges.sum());
    private static final ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);

    private DeterminismDump() {}

    /** With one argument the text goes to that file, otherwise to stdout. */
    public static void main(String[] args) throws IOException {
        Blueprint blueprint = blueprint();
        TestWorld.View view = view();
        MatchRequest request = MatchRequest.of(
            blueprint,
            Siting.of(LatticePoint.origin, Orientation.identity),
            new TestWorld.Lease(view),
            new InstanceId(TestWorld.world, 1),
            OwnerId.of(id("owner")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult checked = MatchEngine.check(request);
        if (!(checked instanceof MatchResult.Admitted admitted)) {
            throw new IllegalStateException("the fixed scenario must admit: " + checked.findings().findings());
        }
        TestAttempt attempt = TestAttempt.of(request);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchResult settled = MatchEngine.settle(request, admitted, ledger, attempt);
        if (!(settled instanceof MatchResult.Formed formed)) {
            throw new IllegalStateException("the fixed scenario must form: " + settled.findings().findings());
        }
        StructureContext context = MatchEngine.derive(formed, blueprint.derivations(), derivers(), attempt.store());
        StringBuilder out = new StringBuilder();
        out.append("blueprint ").append(Long.toUnsignedString(digest(blueprint))).append('\n');
        for (Finding finding : context.findings().findings()) {
            out.append("finding ").append(finding.phase()).append(' ').append(finding.code()).append(' ')
                    .append(finding.message()).append('\n');
        }
        for (ClaimRow row : formed.claims().rows()) {
            out.append("claim ").append(row.cell().point()).append(' ').append(row.instance().world().id().text())
                    .append('#').append(row.instance().serial()).append(' ').append(row.bay().text()).append(' ')
                    .append(row.sharing()).append('\n');
        }
        for (ContextKey<?> key : context.values().keySet()) {
            out.append("context ").append(key.id().text()).append(' ').append(key.type().getSimpleName()).append('\n');
        }
        WorldView frame = view;
        while (frame != null) {
            out.append("frame ").append(frame.depth()).append(' ').append(frame.origin()).append('\n');
            frame = frame.parent();
        }
        if (args.length == 1) {
            Path target = Path.of(args[0]);
            Files.createDirectories(target.getParent());
            Files.writeString(target, out, StandardCharsets.UTF_8);
        } else {
            System.out.print(out);
        }
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("determinism", path);
    }

    private static Blueprint blueprint() {
        Bay base = Bay.of(id("base_bay"), bayCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay pen = Bay.of(
            id("pen_bay"),
            CuboidSet.of(Cuboid.of(penCell)),
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.exclusive
        );
        BlueprintBuilder builder = Blueprint.builder(id("template")).bounds(Cuboid.of(0, 0, 0, 3, 2, 3))
                .anchor(new AnchorSpec(id("seat"), "facing", "north"))
                .orientation(OrientationPolicy.builder(id("seat")).allowTurns(Turn.none, Turn.clockwise).build());
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                NamespacedId type = x == 1 && z == 1 ? id("base_inner") : id("base_outer");
                builder.check(
                    Check.of(
                        id("base_" + x + "_" + z),
                        base,
                        Focuses.occupant(LatticePoint.of(x, 0, z)),
                        Expectations.block(type)
                    )
                );
            }
        }
        builder.check(Check.of(id("plate_check"), base, Focuses.occupant(plateCell), Expectations.block(id("plate"))));
        builder.check(Check.of(id("pen_check"), pen, Focuses.entity(cow), Expectations.anyOccupant()));
        builder.computation(Computations.air(airCells, hollowCells));
        builder.derive(new DerivationSpec(id("sizing"), Value.object(), capacity.id(), 0));
        builder.derive(new DerivationSpec(id("ghost"), Value.object(), id("ghost_key"), 1));
        builder.derive(new DerivationSpec(id("crossed"), Value.object(), id("declared_output"), 2));
        return builder.build();
    }

    private static TestWorld.View view() {
        TestWorld.View view = MagicFixtures.view();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                NamespacedId type = x == 1 && z == 1 ? id("base_inner") : id("base_outer");
                view.put(TestWorld.block(type, LatticePoint.of(x, 0, z)));
            }
        }
        view.put(TestWorld.block(id("plate"), plateCell));
        for (Cuboid box : hollowCells.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        view.air(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        view.put(TechFixtures.entity(cow, penCell, Cuboid.of(penCell)));
        return view;
    }

    private static Catalog<Deriver<?>> derivers() {
        Deriver<Long> sizing = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return capacity;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(airCells);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return ctx.require(airCells).fold(value -> Outcome.found(value * 10L), Outcome::missing);
            }
        };
        Deriver<Long> crossed = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return capacity;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.found(0L);
            }
        };
        return Catalog.<Deriver<?>>builder(id("derivers")).put(id("sizing"), sizing).put(id("crossed"), crossed)
                .build();
    }

    /** Folds the policy and spec collection iteration orders into the digest; order leaks change it. */
    private static long digest(Blueprint blueprint) {
        SnapshotPolicy policy = policy();
        ComputationSpec spec = computationSpec();
        long digest = 17L;
        digest = fold(digest, blueprint.id().text());
        digest = fold(digest, blueprint.bounds().min().toString());
        digest = fold(digest, blueprint.bounds().max().toString());
        for (Bay bay : blueprint.bays()) {
            digest = fold(digest, bay.id().text());
            digest = fold(digest, Long.toString(bay.cells().volume()));
            digest = fold(digest, bay.sharing().toString());
        }
        for (AnchorSpec anchor : blueprint.anchors()) {
            digest = fold(digest, anchor.role().text() + '=' + anchor.property() + ':' + anchor.value());
        }
        for (EntityWindow window : blueprint.reads().windows()) {
            digest = fold(digest, window.type().text());
            digest = fold(digest, window.cells().min().toString());
            digest = fold(digest, window.cells().max().toString());
        }
        for (DerivationSpec derivation : blueprint.derivations()) {
            digest = fold(digest, derivation.deriver().text());
            digest = fold(digest, derivation.output().text());
            digest = fold(digest, Integer.toString(derivation.order()));
        }
        digest = fold(digest, Integer.toString(policy.maxElements()));
        digest = fold(digest, Integer.toString(policy.maxDepth()));
        for (OccupantKind kind : policy.kinds()) {
            digest = fold(digest, kind.id().text());
        }
        for (NamespacedId trait : policy.traits()) {
            digest = fold(digest, trait.text());
        }
        for (EntityWindow window : policy.windows()) {
            digest = fold(digest, window.type().text());
            digest = fold(digest, window.cells().min().toString());
            digest = fold(digest, window.cells().max().toString());
        }
        digest = fold(digest, spec.id().text());
        digest = fold(digest, spec.kind().text());
        digest = fold(digest, spec.output().text());
        for (NamespacedId input : spec.inputs()) {
            digest = fold(digest, input.text());
        }
        return digest;
    }

    private static SnapshotPolicy policy() {
        Set<OccupantKind> kinds = new LinkedHashSet<>(
            List.of(OccupantKind.entity, OccupantKind.block, OccupantKind.nested, OccupantKind.blockEntity)
        );
        Set<NamespacedId> traits = new LinkedHashSet<>(List.of(id("trait/heat"), id("trait/aura"), id("trait/charge")));
        EntityWindow window = Objects.requireNonNull(EntityWindow.of(Cuboid.of(-3, -1, -3, 4, 2, 4), cow), "window");
        return new SnapshotPolicy(4_096, 2, kinds, traits, List.of(window));
    }

    private static ComputationSpec computationSpec() {
        Set<NamespacedId> inputs = new LinkedHashSet<>(
            List.of(id("in/f"), id("in/a"), id("in/d"), id("in/b"), id("in/e"), id("in/c"))
        );
        return new ComputationSpec(
            id("air_count"),
            id("air"),
            Value.object(),
            CuboidSet.of(Cuboid.of(0, 1, 0, 3, 2, 3)),
            id("base_bay"),
            id("air_count"),
            inputs,
            null
        );
    }

    private static long fold(long digest, String piece) {
        return digest * 31L + piece.hashCode();
    }
}
