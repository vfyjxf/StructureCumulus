package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host data over a realistic adapter layout: a rotated structure set, a nested child frame, a
 * waterlogged slab and a snapshot that did not capture host values.
 */
class HostDataScenarioTest {

    private static final World world = World.of(NamespacedId.of("test", "host_world"));
    private static final SnapshotId snapshot = new SnapshotId(world, 1);
    private static final OwnerId owner = OwnerId.of(NamespacedId.of("test", "owner"));
    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
    private static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
    private static final LatticePoint rootOrigin = LatticePoint.of(64, 8, 64);
    private static final Cuboid rootBounds = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final Cuboid satelliteBounds = Cuboid.of(0, 0, 0, 1, 2, 1);
    private static final LatticePoint furnaceCell = LatticePoint.of(0, 0, 0);
    private static final LatticePoint logCell = LatticePoint.of(1, 0, 0);
    private static final LatticePoint fluidCell = LatticePoint.of(0, 0, 0);
    private static final LatticePoint stairCell = LatticePoint.of(0, 1, 0);
    private static final LatticePoint satelliteAt = LatticePoint.of(4, 0, 0);

    private static final NamespacedId rootId = id("host_root");
    private static final NamespacedId satelliteId = id("host_satellite");
    private static final NamespacedId setId = id("host_set");
    private static final NamespacedId rootBayId = id("host_root_bay");
    private static final NamespacedId satelliteBayId = id("host_satellite_bay");
    private static final NamespacedId furnace = mc("furnace");
    private static final NamespacedId oakLog = mc("oak_log");
    private static final NamespacedId water = mc("water");
    private static final NamespacedId oakStairs = mc("oak_stairs");
    private static final NamespacedId oakSlab = mc("oak_slab");
    private static final NamespacedId nestedBody = id("nested_body");

    private static final BlockState furnaceState = Blocks.FURNACE.defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST).setValue(AbstractFurnaceBlock.LIT, true);
    private static final BlockState logState = Blocks.OAK_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    private static final BlockState waterState = Blocks.WATER.defaultBlockState();
    private static final BlockState stairsState = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.SOUTH);
    private static final BlockState slabState = Blocks.OAK_SLAB.defaultBlockState()
            .setValue(SlabBlock.WATERLOGGED, true);

    @BeforeAll
    static void bindTheVanillaTags() {
        bind(holder(BuiltInRegistries.BLOCK, Blocks.OAK_LOG), BlockTags.LOGS);
        bind(holder(BuiltInRegistries.FLUID, Fluids.WATER), FluidTags.WATER);
    }

    @Test
    void aRotatedStructureSetFormsFromTheCapturedHostSnapshot() {
        Scenario scenario = scenario(true);

        StructureSetResult result = scenario.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, satelliteId), result.settled());
        assertFalse(result.claimsOf(rootId).isEmpty(), "the root wrote its rows");
        assertFalse(result.claimsOf(satelliteId).isEmpty(), "the satellite wrote its rows");
        assertEquals(furnaceState, scenario.blockState(furnaceCell));
        assertEquals(logState, scenario.blockState(logCell));
        assertEquals(waterState, scenario.satelliteBlockState(fluidCell));
        assertEquals(stairsState, scenario.satelliteBlockState(stairCell));
        assertEquals(
            "south",
            propertyName(Objects.requireNonNull(scenario.satelliteBlockState(stairCell), "stairs"), "facing"),
            "the half-turned satellite captured the rotated state"
        );
        assertEquals(
            waterState.getFluidState(),
            scenario.view.get(scenario.satelliteWorld(fluidCell), HostTypes.fluidState)
        );
    }

    @Test
    void aWaterloggedSlabIsABlockStateAndNotTheRealFluid() {
        LevelSnapshot view = view(Map.of(fluidCell, oakSlab), Map.of(fluidCell, slabState), true);

        assertEquals("match", replyText(AdapterExpectations.property("waterlogged", "true"), view, fluidCell));
        assertEquals("match", replyText(AdapterExpectations.state(slabState), view, fluidCell));
        assertEquals("match", replyText(AdapterExpectations.fluidTag(FluidTags.WATER), view, fluidCell));
        assertEquals(
            "mismatch:" + AdapterCodes.fluidMismatch,
            replyText(AdapterExpectations.fluid(Fluids.WATER), view, fluidCell)
        );
        assertEquals(
            Codes.expected.code(),
            AdapterExpectations.fluid(Fluids.WATER).meta().mismatchCode(),
            "the composed all_of keeps the kernel junction meta, not the leaf fluid code"
        );
    }

    @Test
    void aNestedChildFrameServesItsOwnHostSnapshot() {
        LevelSnapshot root = nestedView(true);
        LatticePoint anchor = nestedAnchor();
        LatticePoint childCell = LatticePoint.of(0, 0, 0);
        LatticePoint childLog = LatticePoint.of(1, 0, 0);
        LatticePoint childWater = LatticePoint.of(0, 0, 1);

        WorldView child = root.nested(anchor);
        assertNotNull(child, "the nested frame exists");
        assertEquals(1, child.frame());
        assertEquals(1, child.depth());
        assertEquals(0, Objects.requireNonNull(child.parent(), "parent").frame());
        assertEquals(LatticePoint.of(100, 10, 100), child.origin());
        assertEquals(furnaceState, child.get(childCell, HostTypes.blockState));
        assertEquals("match", replyText(AdapterExpectations.property("lit", "true"), child, childCell));
        assertEquals("match", replyText(AdapterExpectations.state(furnaceState), child, childCell));
        assertEquals("match", replyText(AdapterExpectations.blockTag(BlockTags.LOGS), child, childLog));
        assertEquals("match", replyText(AdapterExpectations.fluidTag(FluidTags.WATER), child, childWater));
        assertEquals("match", replyText(AdapterExpectations.fluid(Fluids.WATER), child, childWater));
        BlockState rootValue = root.get(childCell, HostTypes.blockState);
        assertTrue(rootValue == null || rootValue.isAir(), "the root frame does not answer the child's local cell");
    }

    @Test
    void aSetWithoutACapturedHostSnapshotIsUnreadable() {
        Scenario scenario = scenario(false);

        StructureSetResult result = scenario.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(
            result.setFindings().stream().anyMatch(finding -> finding.code().equals(AdapterCodes.valueNotCaptured)),
            () -> result.setFindings().toString()
        );
        for (Finding finding : result.setFindings()) {
            assertFalse(finding.code().equals(AdapterCodes.propertyMismatch), finding.message());
            assertFalse(finding.code().equals(AdapterCodes.stateMismatch), finding.message());
            assertFalse(finding.code().equals(AdapterCodes.tagMismatch), finding.message());
            assertFalse(finding.code().equals(AdapterCodes.fluidMismatch), finding.message());
        }
    }

    private static Scenario scenario(boolean captureHostData) {
        StructureSet plan = place();
        Siting rootSiting = plan.rootSiting();
        StructureSet.Satellite satellite = Objects.requireNonNull(plan.satellite(satelliteId), "satellite");
        Map<LatticePoint, BlockState> states = new LinkedHashMap<>();
        states.put(world(rootSiting, furnaceCell), furnaceState);
        states.put(world(rootSiting, logCell), logState);
        states.put(world(satellite.siting(), fluidCell), waterState);
        states.put(world(satellite.siting(), stairCell), stairsState);
        Map<LatticePoint, NamespacedId> occupants = new LinkedHashMap<>();
        occupants.put(world(rootSiting, furnaceCell), furnace);
        occupants.put(world(rootSiting, logCell), oakLog);
        occupants.put(world(satellite.siting(), fluidCell), water);
        occupants.put(world(satellite.siting(), stairCell), oakStairs);
        LevelSnapshot view = view(boundsOf(occupants.keySet()), occupants, states, captureHostData);
        return new Scenario(plan, rootSiting, satellite.siting(), view);
    }

    private static StructureSet place() {
        SatelliteSpec spec = new SatelliteSpec(
            satelliteId,
            satelliteId,
            satelliteAt,
            half,
            false,
            Value.object(),
            null,
            List.of(),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            rootBlueprint(),
            List.of(new CompiledSatellite(spec, satelliteBlueprint()))
        );
        StructureSet.Placement placement = StructureSet.place(compiled, Siting.of(rootOrigin, clockwise));
        return Objects.requireNonNull(placement.set(), () -> placement.findings().toString());
    }

    private static Blueprint rootBlueprint() {
        Bay bay = Bay.of(rootBayId, CuboidSet.of(rootBounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(rootId).bounds(rootBounds).checks(
            List.of(
                Check.of(
                    id("furnace_lit"),
                    bay,
                    Focuses.occupant(furnaceCell),
                    AdapterExpectations.property("lit", "true")
                ),
                Check.of(
                    id("furnace_state"),
                    bay,
                    Focuses.occupant(furnaceCell),
                    AdapterExpectations.state(furnaceState)
                ),
                Check.of(id("log_tag"), bay, Focuses.occupant(logCell), AdapterExpectations.blockTag(BlockTags.LOGS)),
                Check.of(id("log_axis"), bay, Focuses.occupant(logCell), AdapterExpectations.property("axis", "x"))
            )
        ).build();
    }

    private static Blueprint satelliteBlueprint() {
        Bay bay = Bay.of(
            satelliteBayId,
            CuboidSet.of(satelliteBounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(satelliteId).bounds(satelliteBounds).checks(
            List.of(
                Check.of(
                    id("water_tag"),
                    bay,
                    Focuses.occupant(fluidCell),
                    AdapterExpectations.fluidTag(FluidTags.WATER)
                ),
                Check.of(id("water_fluid"), bay, Focuses.occupant(fluidCell), AdapterExpectations.fluid(Fluids.WATER)),
                Check.of(
                    id("stairs_facing"),
                    bay,
                    Focuses.occupant(stairCell),
                    AdapterExpectations.property("facing", "south")
                ),
                Check.of(id("stairs_state"), bay, Focuses.occupant(stairCell), AdapterExpectations.state(stairsState))
            )
        ).build();
    }

    private static LevelSnapshot nestedView(boolean captureHostData) {
        LatticePoint anchor = nestedAnchor();
        LatticePoint childOrigin = LatticePoint.of(100, 10, 100);
        Cuboid childWorld = Cuboid.of(100, 10, 100, 102, 12, 102);
        Cuboid childLocal = Cuboid.of(0, 0, 0, 2, 2, 2);
        Map<LatticePoint, BlockState> childStates = new LinkedHashMap<>();
        childStates.put(childOrigin, furnaceState);
        childStates.put(childOrigin.offset(1, 0, 0), logState);
        childStates.put(childOrigin.offset(0, 0, 1), waterState);
        SnapshotLevel childHost = captureHostData ? host(childOrigin, childLocal, childStates) : null;
        FrameData child = new FrameData(
            snapshot,
            1,
            1,
            childOrigin,
            childLocal,
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            childHost
        );
        OccupantSnapshot body = new OccupantSnapshot(
            new OccupantRef(snapshot, 0, nestedBody, OccupantKind.nested, anchor, null),
            new OccupantDescriptor(nestedBody, OccupantKind.nested),
            childWorld,
            List.of()
        );
        Cuboid bounds = boundsOf(List.of(anchor));
        FrameData root = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            Map.of(),
            List.of(),
            List.of(body),
            Map.of(anchor, child),
            captureHostData ? host(LatticePoint.origin, bounds, Map.of()) : null
        );
        return new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), root);
    }

    private static LevelSnapshot view(
        Map<LatticePoint, NamespacedId> occupants,
        Map<LatticePoint, BlockState> states,
        boolean captureHostData
    ) {
        return view(boundsOf(occupants.keySet()), occupants, states, captureHostData);
    }

    private static LevelSnapshot view(
        Cuboid bounds,
        Map<LatticePoint, NamespacedId> occupants,
        Map<LatticePoint, BlockState> states,
        boolean captureHostData
    ) {
        Map<SectionKey, Map<LatticePoint, OccupantSnapshot>> groups = new LinkedHashMap<>();
        for (Map.Entry<LatticePoint, NamespacedId> entry : occupants.entrySet()) {
            LatticePoint cell = entry.getKey();
            SectionKey key = SectionKey.of(world, 0, cell, 16);
            groups.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(cell, block(cell, entry.getValue()));
        }
        Map<SectionKey, SectionSlot> sections = new LinkedHashMap<>();
        for (Map.Entry<SectionKey, Map<LatticePoint, OccupantSnapshot>> entry : groups.entrySet()) {
            sections.put(
                entry.getKey(),
                new SectionSlot.Present(SectionSnapshot.of(entry.getKey(), entry.getValue(), 0L))
            );
        }
        FrameData data = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            sections,
            List.of(),
            List.of(),
            Map.of(),
            captureHostData ? host(LatticePoint.origin, bounds, states) : null
        );
        return new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), data);
    }

    private static SnapshotLevel host(LatticePoint origin, Cuboid bounds, Map<LatticePoint, BlockState> states) {
        Map<Long, PalettedContainer<BlockState>> sections = new LinkedHashMap<>();
        Map<Long, FluidState[]> fluids = new LinkedHashMap<>();
        for (Map.Entry<LatticePoint, BlockState> entry : states.entrySet()) {
            LatticePoint cell = entry.getKey();
            BlockState state = entry.getValue();
            long key = SnapshotLevel.worldSectionKey(cell.x(), cell.y(), cell.z());
            sections.computeIfAbsent(
                key,
                ignored -> new PalettedContainer<>(
                    Block.BLOCK_STATE_REGISTRY,
                    Blocks.AIR.defaultBlockState(),
                    PalettedContainer.Strategy.SECTION_STATES
                )
            ).set(cell.x() & 15, cell.y() & 15, cell.z() & 15, state);
            FluidState[] fluid = fluids.computeIfAbsent(key, ignored -> {
                FluidState[] array = new FluidState[SnapshotLevel.sectionSize
                        * SnapshotLevel.sectionSize
                        * SnapshotLevel.sectionSize];
                Arrays.fill(array, Fluids.EMPTY.defaultFluidState());
                return array;
            });
            fluid[SnapshotLevel.fluidIndex(cell.x(), cell.y(), cell.z())] = state.getFluidState();
        }
        return new SnapshotLevel(origin, bounds, -64, 384, sections, fluids, Map.of(), List.of());
    }

    private static OccupantSnapshot block(LatticePoint cell, NamespacedId type) {
        return new OccupantSnapshot(
            new OccupantRef(snapshot, 0, type, OccupantKind.block, cell, null),
            new OccupantDescriptor(type, OccupantKind.block),
            Cuboid.of(cell),
            List.of()
        );
    }

    private static Cuboid boundsOf(Collection<LatticePoint> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (LatticePoint cell : cells) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        if (minX == Integer.MAX_VALUE) {
            return Cuboid.of(0, 0, 0, 1, 1, 1);
        }
        return Cuboid.of(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static LatticePoint world(Siting siting, LatticePoint local) {
        return Objects.requireNonNull(siting.toWorld(local), () -> "unmapped " + local + " at " + siting);
    }

    private static LatticePoint nestedAnchor() {
        return LatticePoint.of(70, 9, 72);
    }

    private static String replyText(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        Reply reply = reply(expectation, view, cell);
        return switch (reply) {
            case Reply.Match ignored -> "match";
            case Reply.Mismatch mismatch -> "mismatch:" + Objects.requireNonNull(mismatch.findings().primary()).code();
            case Reply.Unreadable unreadable ->
                "unreadable:" + Objects.requireNonNull(unreadable.findings().primary()).code();
            case Reply.Abstain ignored -> "abstain";
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Reply reply(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        OccupantSnapshot subject = subject(view, cell);
        ExpectationContext context = ExpectationContext.of(new TestFocus(view, cell), subject, List.of(subject));
        return ((Expectation) expectation).test(context);
    }

    private static OccupantSnapshot subject(WorldView view, LatticePoint cell) {
        NamespacedId type = view.frame() == 0 ? furnace : nestedBody;
        OccupantRef ref = new OccupantRef(snapshot, view.frame(), type, OccupantKind.block, cell, null);
        return new OccupantSnapshot(ref, new OccupantDescriptor(type, OccupantKind.block), Cuboid.of(cell), List.of());
    }

    private static String propertyName(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return valueName(state, property);
            }
        }
        throw new AssertionError("no property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId mc(String path) {
        return NamespacedId.of("minecraft", path);
    }

    private static <T> Holder.Reference<T> holder(Registry<T> registry, T value) {
        return registry.getResourceKey(value).flatMap(registry::getHolder).orElseThrow();
    }

    private static void bind(Holder.Reference<?> holder, TagKey<?> tag) {
        try {
            Method method = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
            method.setAccessible(true);
            method.invoke(holder, Set.of(tag));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not bind " + tag + " to " + holder, failure);
        }
    }

    private record Scenario(StructureSet plan, Siting rootSiting, Siting satelliteSiting, LevelSnapshot view) {

        StructureSetResult run() {
            StructureSetTestSupport.FakeLedger ledger = new StructureSetTestSupport.FakeLedger();
            StructureKernel kernel = StructureKernel.create(
                Catalogs.builtin(),
                new Host(DocumentSource.empty(), ledger, ContextStore.inMemory(), Map.of())
            );
            List<InstanceId> instances = new ArrayList<>();
            return new StructureSetHost(kernel).run(plan, new Lease(view), owner, Budget.unbounded(), () -> {
                InstanceId instance = new InstanceId(world, instances.size() + 1);
                instances.add(instance);
                return instance;
            });
        }

        @Nullable
        BlockState blockState(LatticePoint local) {
            return view.get(world(rootSiting, local), HostTypes.blockState);
        }

        @Nullable
        BlockState satelliteBlockState(LatticePoint local) {
            return view.get(satelliteWorld(local), HostTypes.blockState);
        }

        LatticePoint satelliteWorld(LatticePoint local) {
            return world(satelliteSiting, local);
        }
    }

    private static final class Lease implements SnapshotLease {

        private final WorldView view;
        private boolean current = true;

        private Lease(WorldView view) {
            this.view = view;
        }

        @Override
        public SnapshotId id() {
            return snapshot;
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public void close() {
            current = false;
        }
    }

    private enum EmptyContext implements ContextView {
        instance;

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            return null;
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            return Outcome.missing();
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return false;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            return Set.of();
        }
    }

    private record TestFocus(WorldView view, LatticePoint cell) implements FocusContext {

        @Override
        public BayView bay() {
            return new BayView(id("host_bay"), CuboidSet.of(Cuboid.of(cell)), view, siting());
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public Siting siting() {
            return Siting.of(LatticePoint.origin, Orientation.identity);
        }

        @Override
        public InstanceId instance() {
            return new InstanceId(world, 1);
        }

        @Override
        public CuboidSet cells() {
            return CuboidSet.of(Cuboid.of(cell));
        }

        @Override
        public Phase phase() {
            return Phase.check;
        }

        @Override
        public Locus locus() {
            CuboidSet cells = CuboidSet.of(Cuboid.of(cell));
            return view.frame() == 0 ? Locus.world(cells) : Locus.local(cells);
        }

        @Override
        public ContextView context() {
            return EmptyContext.instance;
        }

        @Override
        public Budget remaining() {
            return Budget.unbounded();
        }

        @Override
        public CancelToken cancel() {
            return CancelToken.never();
        }

        @Override
        public CellRead read(LatticePoint point) {
            throw new AssertionError("a host expectation read a live cell");
        }

        @Override
        public List<ClaimRow> claimsAt(LatticePoint point) {
            return List.of();
        }
    }
}
