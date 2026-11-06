package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.AttachmentNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolder;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Expanded real-level set/capture tests: many nested frames, windows, attachments and claim volume. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class ExpandedSetCaptureGameTest {

    private static final NamespacedId rootBlueprintId = id("expanded_root");
    private static final NamespacedId satelliteBlueprintId = id("expanded_satellite");
    private static final NamespacedId setId = id("expanded_set_capture");
    private static final NamespacedId rootBayId = id("expanded_root_bay");
    private static final NamespacedId rootCheckId = id("expanded_root_check");
    private static final NamespacedId satelliteBayId = id("expanded_satellite_bay");
    private static final NamespacedId satelliteCheckId = id("expanded_satellite_check");
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId chicken = NamespacedId.of("minecraft", "chicken");
    private static final AttachmentType<String> attachmentType = AttachmentType.builder(() -> "").build();

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void manyNestedFramesEntitiesAndAttachmentsUseTheInjectedLookup(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        int count = 6;
        Map<BlockPos, NestedExtent> capabilityChildren = new LinkedHashMap<>();
        Map<BlockPos, NestedExtent> attachmentChildren = new LinkedHashMap<>();
        List<BlockPos> anchors = new ArrayList<>();
        List<EntityWindow> windows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int x = index * 2;
            BlockPos relativeAnchor = new BlockPos(x, 1, 0);
            helper.setBlock(relativeAnchor, Blocks.STONE);
            helper.setBlock(new BlockPos(x + 1, 1, 0), Blocks.DIRT);
            BlockPos anchor = helper.absolutePos(relativeAnchor);
            anchors.add(anchor);
            windows.add(
                new EntityWindow(
                    Cuboid.of(
                        anchor.getX(),
                        anchor.getY(),
                        anchor.getZ(),
                        anchor.getX() + 1,
                        anchor.getY() + 2,
                        anchor.getZ() + 1
                    ),
                    chicken
                )
            );
            helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(x + 0.5, 2.0, 0.5));
            NestedExtent extent = new NestedExtent(
                id("expanded_child_" + index),
                Cuboid.of(
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    anchor.getX() + 2,
                    anchor.getY() + 2,
                    anchor.getZ() + 1
                ),
                LatticePoint.of(anchor.getX(), anchor.getY(), anchor.getZ())
            );
            if (index % 2 == 0) {
                capabilityChildren.put(anchor, extent);
            } else {
                attachmentChildren.put(anchor, extent);
            }
        }
        NestedExtentLookup.Builder builder = NestedExtentLookup.builder()
                .add(new MapCapabilityProvider(capabilityChildren))
                .add(new MapAttachmentProvider(NestedExtentHolders.block, attachmentChildren));
        List<NestedExtentHolder<BlockNestedExtentContext>> dummyHolders = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            NestedExtentHolder<BlockNestedExtentContext> holder = NestedExtentHolder.of(id("dummy_provider_" + index));
            NestedExtent extent = new NestedExtent(
                id("dummy_extent_" + index),
                Cuboid.of(
                    anchors.get(0).getX(),
                    anchors.get(0).getY(),
                    anchors.get(0).getZ(),
                    anchors.get(0).getX() + 1,
                    anchors.get(0).getY() + 1,
                    anchors.get(0).getZ() + 1
                ),
                LatticePoint.of(anchors.get(0).getX(), anchors.get(0).getY(), anchors.get(0).getZ())
            );
            dummyHolders.add(holder);
            builder.add(new MapAttachmentProvider(holder, Map.of(anchors.get(0), extent)));
        }
        NestedExtentLookup lookup = builder.build();
        for (NestedExtentHolder<BlockNestedExtentContext> holder : dummyHolders) {
            helper.assertTrue(lookup.knows(holder), "the injected lookup knows " + holder.id());
        }
        Cuboid bounds = worldBounds(anchors);
        SnapshotPolicy policy = new SnapshotPolicy(
            10_000,
            1,
            Set.of(OccupantKind.nested, OccupantKind.entity),
            Set.of(),
            windows
        );
        ServerLevel level = helper.getLevel();
        state.installNestedExtentLookup(lookup);
        try {
            CaptureResult captured = state.capture(level).capture(new CaptureRequest(bounds, policy));
            if (!(captured instanceof CaptureResult.Captured result)) {
                helper.fail("the expanded nested capture failed: " + captured);
                return;
            }
            try (SnapshotLease lease = result.lease()) {
                WorldView view = lease.view();
                helper.assertValueEqual(
                    count,
                    view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                    "one nested body per structure"
                );
                helper.assertValueEqual(
                    count,
                    view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity)).size(),
                    "one entity body per structure"
                );
                for (BlockPos anchor : anchors) {
                    WorldView child = view.nested(lattice(anchor));
                    helper.assertTrue(child != null, "missing nested child at " + anchor);
                    if (child == null) {
                        return;
                    }
                    helper.assertValueEqual(
                        NamespacedId.of("minecraft", "dirt"),
                        Objects.requireNonNull(child.occupant(LatticePoint.of(1, 0, 0)), "child block").descriptor()
                                .type(),
                        "the child frame read its own block"
                    );
                }
                BlockNestedExtentContext context = new BlockNestedExtentContext(
                    level,
                    anchors.get(0),
                    level.getBlockState(anchors.get(0)),
                    null
                );
                for (int index = 0; index < dummyHolders.size(); index++) {
                    helper.assertTrue(
                        lookup.getAttachment(dummyHolders.get(index), context) != null,
                        "dummy provider " + index + " did not resolve through the injected lookup"
                    );
                }
            }
        } finally {
            state.installNestedExtentLookup(NestedExtentLookup.capabilities());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theCaptureBudgetCutsTheLastChildrenWhileKeepingTheirBodies(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        int count = 6;
        Map<BlockPos, NestedExtent> children = new LinkedHashMap<>();
        List<BlockPos> anchors = new ArrayList<>();
        int maxWorldX = Integer.MIN_VALUE;
        for (int index = 0; index < count; index++) {
            int x = index * 3;
            BlockPos relativeAnchor = new BlockPos(x, 1, 0);
            helper.setBlock(relativeAnchor, Blocks.STONE);
            helper.setBlock(new BlockPos(x + 1, 1, 0), Blocks.DIRT);
            BlockPos anchor = helper.absolutePos(relativeAnchor);
            anchors.add(anchor);
            maxWorldX = Math.max(maxWorldX, anchor.getX());
            children.put(
                anchor,
                new NestedExtent(
                    id("budget_child_" + index),
                    Cuboid.of(
                        anchor.getX(),
                        anchor.getY(),
                        anchor.getZ(),
                        anchor.getX() + 2,
                        anchor.getY() + 2,
                        anchor.getZ() + 1
                    ),
                    LatticePoint.of(anchor.getX(), anchor.getY(), anchor.getZ())
                )
            );
        }
        BlockPos first = anchors.get(0);
        Cuboid bounds = Cuboid.of(
            first.getX() - 1,
            first.getY() - 1,
            first.getZ() - 1,
            maxWorldX + 3,
            first.getY() + 2,
            first.getZ() + 2
        );
        long rootVolume = bounds.volume();
        long childVolume = 2L * 2 * 1;
        int exact = Math.toIntExact(rootVolume + childVolume * count);
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(new MapCapabilityProvider(children)).build();
        SnapshotPolicy exactPolicy = new SnapshotPolicy(exact, 1, Set.of(OccupantKind.nested), Set.of(), List.of());
        ServerLevel level = helper.getLevel();
        state.installNestedExtentLookup(lookup);
        try {
            CaptureResult exactCapture = state.capture(level).capture(new CaptureRequest(bounds, exactPolicy));
            if (!(exactCapture instanceof CaptureResult.Captured exactResult)) {
                helper.fail("the exact capture failed: " + exactCapture);
                return;
            }
            try (SnapshotLease lease = exactResult.lease()) {
                for (BlockPos anchor : anchors) {
                    helper.assertTrue(lease.view().nested(lattice(anchor)) != null, "the exact budget cut " + anchor);
                }
                helper.assertTrue(
                    exactResult.report().entries().stream()
                            .noneMatch(entry -> entry.notes().stream().anyMatch(note -> note.equals("reason: budget"))),
                    "the exact budget cuts nothing for budget"
                );
            }

            CaptureResult shortCapture = state.capture(level).capture(
                new CaptureRequest(
                    bounds,
                    new SnapshotPolicy(exact - 1, 1, Set.of(OccupantKind.nested), Set.of(), List.of())
                )
            );
            if (!(shortCapture instanceof CaptureResult.Captured shortResult)) {
                helper.fail("the one-short capture failed: " + shortCapture);
                return;
            }
            try (SnapshotLease lease = shortResult.lease()) {
                helper.assertValueEqual(
                    count,
                    lease.view().occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                    "every cut child keeps its body"
                );
                long childCount = anchors.stream().filter(anchor -> lease.view().nested(lattice(anchor)) != null)
                        .count();
                helper.assertValueEqual(count - 1L, childCount, "the last child is cut by the shared budget");
                helper.assertTrue(
                    shortResult.report().entries().stream().anyMatch(
                        entry -> entry.code().equals(AdapterCodes.cut)
                                && entry.notes().stream().anyMatch(note -> note.contains("budget"))
                    ),
                    "the budget cut is reported: " + shortResult.report()
                );
            }

            CaptureResult tooLarge = state.capture(level).capture(
                new CaptureRequest(
                    bounds,
                    new SnapshotPolicy(
                        Math.toIntExact(rootVolume - 1),
                        1,
                        Set.of(OccupantKind.nested),
                        Set.of(),
                        List.of()
                    )
                )
            );
            if (!(tooLarge instanceof CaptureResult.TooLarge oversized)) {
                helper.fail("the over-ceiling root was not TooLarge: " + tooLarge);
                return;
            }
            helper.assertTrue(
                oversized.report().entries().stream().anyMatch(entry -> entry.code().equals(AdapterCodes.overCap)),
                "the over-ceiling report: " + oversized.report()
            );
        } finally {
            state.installNestedExtentLookup(NestedExtentLookup.capabilities());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aRepeatedManyMemberSetUsesOneInstanceAndOneClaimRowPerMember(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = plan(origin, 6);
        placeBlocks(helper, plan);
        StructureManager structures = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        cleanup(structures, ledger, state, plan);
        try (SnapshotLease lease = capture(state, level, bounds)) {
            StructureSetHost host = new StructureSetHost(state.kernel(level), structures);
            List<InstanceId> first = new ArrayList<>();
            StructureSetResult firstResult = host
                    .run(plan, lease, state.owner(), Budget.unbounded(), structures, spec -> {
                        StructureAttempt attempt = structures.begin(state.world(level), spec);
                        first.add(attempt.id());
                        return attempt;
                    });
            helper.assertTrue(firstResult.formed(), "first set: " + firstResult.setFindings());
            helper.assertValueEqual(7, firstResult.settled().size(), "root plus six satellites");
            helper.assertValueEqual(7, ledger.read(bounds).rows().size(), "one claim row per member");
            List<InstanceId> second = new ArrayList<>();
            StructureSetResult secondResult = host
                    .run(plan, lease, state.owner(), Budget.unbounded(), structures, spec -> {
                        StructureAttempt attempt = structures.begin(state.world(level), spec);
                        second.add(attempt.id());
                        return attempt;
                    });
            helper.assertTrue(secondResult.formed(), "second set: " + secondResult.setFindings());
            helper.assertValueEqual(7, ledger.read(bounds).rows().size(), "the repeated set replaces its rows");
            helper.assertValueEqual(7, second.size(), "one new instance per member");
            for (InstanceId old : first) {
                helper.assertTrue(structures.find(old) == null, "the old instance " + old + " was replaced");
            }
            for (InstanceId replacement : second) {
                helper.assertTrue(structures.find(replacement) != null, "the replacement " + replacement + " formed");
            }
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    private static StructureSet plan(BlockPos origin, int satellites) {
        List<CompiledSatellite> compiled = new ArrayList<>();
        for (int index = 0; index < satellites; index++) {
            SatelliteSpec spec = new SatelliteSpec(
                id("expanded_set_satellite_" + index),
                satelliteBlueprintId,
                LatticePoint.of(2 + index * 2, 0, 0),
                Orientation.identity,
                false,
                Value.object(),
                null,
                List.of(LinkSpec.none()),
                null
            );
            compiled.add(new CompiledSatellite(spec, satelliteBlueprint()));
        }
        StructureSet.Placement placement = StructureSet.place(
            new CompiledStructureSet(setId, rootBlueprint(), List.copyOf(compiled)),
            Siting.of(lattice(origin), Orientation.identity)
        );
        if (!placement.succeeded()) {
            throw new GameTestAssertException("the expanded set does not place: " + placement.findings());
        }
        return Objects.requireNonNull(placement.set(), "expanded set");
    }

    private static void placeBlocks(GameTestHelper helper, StructureSet plan) {
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            LatticePoint world = satellite.siting().origin();
            LatticePoint root = plan.rootSiting().origin();
            helper.setBlock(
                new BlockPos(world.x() - root.x(), world.y() - root.y(), world.z() - root.z()),
                Blocks.STONE
            );
        }
    }

    private static SnapshotLease capture(ServerState state, ServerLevel level, Cuboid bounds) {
        CaptureResult result = state.capture(level).capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(result instanceof CaptureResult.Captured captured)) {
            throw new GameTestAssertException("the expanded set capture failed: " + result);
        }
        return captured.lease();
    }

    private static Cuboid worldBounds(List<BlockPos> anchors) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos anchor : anchors) {
            minX = Math.min(minX, anchor.getX() - 1);
            minY = Math.min(minY, anchor.getY() - 1);
            minZ = Math.min(minZ, anchor.getZ() - 1);
            maxX = Math.max(maxX, anchor.getX() + 3);
            maxY = Math.max(maxY, anchor.getY() + 2);
            maxZ = Math.max(maxZ, anchor.getZ() + 2);
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void cleanup(StructureManager structures, ClaimLedger ledger, ServerState state, StructureSet plan) {
        for (StructureSet.Satellite satellite : plan.satellites()) {
            release(structures, ledger, state, satellite.siting());
        }
        release(structures, ledger, state, plan.rootSiting());
    }

    private static void release(StructureManager structures, ClaimLedger ledger, ServerState state, Siting siting) {
        StructureInstance instance = structures.find(state.owner(), siting);
        if (instance == null) {
            return;
        }
        structures.onEvent(new StructureEvent.Broken(instance.id()));
        ledger.release(instance.id());
    }

    private static Blueprint rootBlueprint() {
        return blueprint(rootBlueprintId, rootBayId, rootCheckId);
    }

    private static Blueprint satelliteBlueprint() {
        return blueprint(satelliteBlueprintId, satelliteBayId, satelliteCheckId);
    }

    private static Blueprint blueprint(NamespacedId blueprintId, NamespacedId bayId, NamespacedId checkId) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(bayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(bounds)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))).build();
    }

    private static LatticePoint lattice(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private static final class MapCapabilityProvider
            implements
                CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction> {

        private final Map<BlockPos, NestedExtent> extents;

        private MapCapabilityProvider(Map<BlockPos, NestedExtent> extents) {
            this.extents = extents;
        }

        @Override
        public NestedExtentHolder<BlockNestedExtentContext> holder() {
            return NestedExtentHolders.block;
        }

        @Override
        public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
            return extents.get(actual.pos());
        }
    }

    private static final class MapAttachmentProvider
            implements
                AttachmentNestedExtentProvider<BlockNestedExtentContext, String> {

        private final NestedExtentHolder<BlockNestedExtentContext> holder;
        private final Map<BlockPos, NestedExtent> extents;

        private MapAttachmentProvider(
            NestedExtentHolder<BlockNestedExtentContext> holder,
            Map<BlockPos, NestedExtent> extents
        ) {
            this.holder = holder;
            this.extents = extents;
        }

        @Override
        public NestedExtentHolder<BlockNestedExtentContext> holder() {
            return holder;
        }

        @Override
        public AttachmentType<String> type() {
            return attachmentType;
        }

        @Override
        public boolean hasData(BlockNestedExtentContext actual) {
            return extents.containsKey(actual.pos());
        }

        @Override
        public @Nullable NestedExtent getExistingDataOrNull(BlockNestedExtentContext actual) {
            return extents.get(actual.pos());
        }

        @Override
        public @Nullable NestedExtent getData(BlockNestedExtentContext actual) {
            return extents.get(actual.pos());
        }

        @Override
        public @Nullable NestedExtent setData(BlockNestedExtentContext actual, NestedExtent value) {
            throw new UnsupportedOperationException("the expanded capture lookup is read-only");
        }

        @Override
        public @Nullable NestedExtent removeData(BlockNestedExtentContext actual) {
            throw new UnsupportedOperationException("the expanded capture lookup is read-only");
        }
    }
}
