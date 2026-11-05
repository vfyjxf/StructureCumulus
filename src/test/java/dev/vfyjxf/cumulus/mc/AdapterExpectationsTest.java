package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
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
import dev.vfyjxf.cumulus.kernel.world.HostType;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterExpectationsTest {

    private static final World world = World.of(NamespacedId.of("test", "world"));
    private static final SnapshotId snapshot = new SnapshotId(world, 1);
    private static final InstanceId instance = new InstanceId(world, 1);
    private static final NamespacedId documentId = NamespacedId.of("test", "host");
    private static final NamespacedId bayId = NamespacedId.of("test", "host_bay");
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 4, 1, 1);
    private static final LatticePoint furnaceCell = LatticePoint.of(0, 0, 0);
    private static final LatticePoint logCell = LatticePoint.of(1, 0, 0);
    private static final LatticePoint waterCell = LatticePoint.of(2, 0, 0);
    private static final LatticePoint entityCell = LatticePoint.of(3, 0, 0);

    @BeforeAll
    static void bindTheVanillaTags() {
        bind(holder(BuiltInRegistries.BLOCK, Blocks.OAK_LOG), BlockTags.LOGS);
        bind(holder(BuiltInRegistries.FLUID, Fluids.WATER), FluidTags.WATER);
        bind(holder(BuiltInRegistries.ENTITY_TYPE, EntityType.ZOMBIE), EntityTypeTags.UNDEAD);
    }

    private static <T> Holder.Reference<T> holder(Registry<T> registry, T value) {
        return registry.getResourceKey(value).flatMap(registry::getHolder).orElseThrow();
    }

    @Test
    void propertyFactoryMatchesMismatchesAndRejectsAMissingProperty() {
        LevelSnapshot view = matching();

        assertEquals("match", reply(AdapterExpectations.property("lit", "true"), view, furnaceCell));
        assertEquals(
            "mismatch:" + AdapterCodes.propertyMismatch,
            reply(AdapterExpectations.property("lit", "false"), view, furnaceCell)
        );
        assertEquals(
            "mismatch:" + AdapterCodes.propertyMismatch,
            reply(AdapterExpectations.property("absent", "true"), view, furnaceCell)
        );
    }

    @Test
    void aMissingHostValueIsUnreadableWithTheNotCapturedCode() {
        LevelSnapshot uncaptured = new LevelSnapshot(snapshot, bounds, Map.of());

        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.property("lit", "true"), uncaptured, furnaceCell)
        );
        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.state(furnace(true)), uncaptured, furnaceCell)
        );
        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.blockTag(BlockTags.LOGS), uncaptured, logCell)
        );
        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.fluidTag(FluidTags.WATER), uncaptured, waterCell)
        );
        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.entityTag(EntityTypeTags.UNDEAD), uncaptured, entityCell)
        );
        assertEquals(
            "unreadable:" + AdapterCodes.valueNotCaptured,
            reply(AdapterExpectations.fluid(Fluids.WATER), uncaptured, waterCell)
        );
    }

    @Test
    void exactStateMatchesTheResolvedStateOnly() {
        LevelSnapshot view = matching();

        assertEquals("match", reply(AdapterExpectations.state(furnace(true)), view, furnaceCell));
        assertEquals(
            "mismatch:" + AdapterCodes.stateMismatch,
            reply(AdapterExpectations.state(furnace(false)), view, furnaceCell)
        );
    }

    @Test
    void blockTagMatchesTheRegistryTag() {
        assertEquals("match", reply(AdapterExpectations.blockTag(BlockTags.LOGS), matching(), logCell));
        assertEquals(
            "mismatch:" + AdapterCodes.tagMismatch,
            reply(AdapterExpectations.blockTag(BlockTags.LOGS), mismatching(), logCell)
        );
    }

    @Test
    void fluidTagMatchesTheRegistryTag() {
        assertEquals("match", reply(AdapterExpectations.fluidTag(FluidTags.WATER), matching(), waterCell));
        assertEquals(
            "mismatch:" + AdapterCodes.tagMismatch,
            reply(AdapterExpectations.fluidTag(FluidTags.WATER), mismatching(), waterCell)
        );
    }

    @Test
    void entityTagMatchesTheRegistryTag() {
        assertEquals("match", reply(AdapterExpectations.entityTag(EntityTypeTags.UNDEAD), matching(), entityCell));
        assertEquals(
            "mismatch:" + AdapterCodes.tagMismatch,
            reply(AdapterExpectations.entityTag(EntityTypeTags.UNDEAD), mismatching(), entityCell)
        );
    }

    @Test
    void fluidMatchesRealWaterAndRejectsAWaterloggedBlock() {
        assertEquals("match", reply(AdapterExpectations.fluid(Fluids.WATER), matching(), waterCell));
        assertEquals(
            "mismatch:" + AdapterCodes.fluidMismatch,
            reply(AdapterExpectations.fluid(Fluids.WATER), waterloggedSlab(), waterCell)
        );
        assertEquals(
            "mismatch:" + AdapterCodes.fluidMismatch,
            reply(AdapterExpectations.fluid(Fluids.WATER), mismatching(), waterCell)
        );
    }

    @Test
    void factoriesReadOnlyTheTypedSnapshot() {
        CountingView property = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.property("lit", "true"), property, furnaceCell));
        assertEquals(Map.of(HostTypes.blockState, 1), property.reads());

        CountingView state = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.state(furnace(true)), state, furnaceCell));
        assertEquals(Map.of(HostTypes.blockState, 1), state.reads());

        CountingView blockTag = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.blockTag(BlockTags.LOGS), blockTag, logCell));
        assertEquals(Map.of(HostTypes.blockState, 1), blockTag.reads());

        CountingView fluidTag = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.fluidTag(FluidTags.WATER), fluidTag, waterCell));
        assertEquals(Map.of(HostTypes.fluidState, 1), fluidTag.reads());

        CountingView entityTag = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.entityTag(EntityTypeTags.UNDEAD), entityTag, entityCell));
        assertEquals(Map.of(HostTypes.entityType, 1), entityTag.reads());

        CountingView fluid = new CountingView(matching());
        assertEquals("match", reply(AdapterExpectations.fluid(Fluids.WATER), fluid, waterCell));
        assertEquals(Map.of(HostTypes.fluidState, 1, HostTypes.blockState, 1), fluid.reads());
    }

    @Test
    void theAdapterCatalogCarriesTheFiveExpectationCodes() {
        Catalog<CodeEntry> catalog = AdapterCodes.catalog();
        assertEquals(10, AdapterCodes.entries().size());
        for (FindingCode code : List.of(
            AdapterCodes.propertyMismatch,
            AdapterCodes.stateMismatch,
            AdapterCodes.tagMismatch,
            AdapterCodes.fluidMismatch,
            AdapterCodes.valueNotCaptured
        )) {
            CodeEntry entry = catalog.get(NamespacedId.of("structurecumulus", code.text().toLowerCase(Locale.ROOT)));
            assertNotNull(entry, code.text());
            assertEquals(code, entry.code());
            assertEquals(Severity.error, entry.severity());
        }
    }

    @Test
    void theKernelCatalogDoesNotKnowTheAdapterKinds() {
        CompileResult result = DocumentCompiler.compile(read(hostDocument()), context(Catalogs.builtin()));

        assertNull(result.blueprint());
        assertTrue(
            result.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.documentFactory.code())),
            () -> result.findings().findings().toString()
        );
    }

    @Test
    void jsonWithoutTheAdapterCatalogRejectsAMcChecksMember() {
        String text = """
                {"schemaVersion":1,"id":"test:mc_checks","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],"mc_checks":[]}
                """;
        List<Finding> errors = new ArrayList<>();

        Document document = new JsonReader().read(documentId, text, errors);

        assertNull(document);
        assertTrue(
            errors.stream().anyMatch(finding -> finding.code().equals(Codes.documentField.code())),
            () -> errors.toString()
        );
    }

    @Test
    void jsonAndJavaHostExpectationsCompileAlike() {
        CompileResult result = DocumentCompiler.compile(read(hostDocument()), context(adapterCatalogs()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint json = result.blueprint();
        assertNotNull(json);

        Blueprint javaBlueprint = javaBlueprint();
        assertEquals(json.id(), javaBlueprint.id());
        assertEquals(json.bounds(), javaBlueprint.bounds());
        assertEquals(json.bays(), javaBlueprint.bays());
        assertEquals(shape(json), shape(javaBlueprint));

        LevelSnapshot matching = matching();
        assertEquals(replies(json, matching), replies(javaBlueprint, matching));
        LevelSnapshot mismatching = mismatching();
        assertEquals(replies(json, mismatching), replies(javaBlueprint, mismatching));
    }

    @Test
    void stateRejectsAnUnknownBlock() {
        List<Finding> findings = compileExpect("{\"kind\":\"state\",\"block\":\"minecraft:stonee\"}");
        assertTrue(anyFactoryBad(findings), () -> findings.toString());
    }

    @Test
    void fluidRejectsAnUnknownFluid() {
        List<Finding> findings = compileExpect("{\"kind\":\"fluid\",\"id\":\"minecraft:watr\"}");
        assertTrue(anyFactoryBad(findings), () -> findings.toString());
    }

    @Test
    void aBareBlockIdIsRejected() {
        List<Finding> findings = compileExpect("{\"kind\":\"state\",\"block\":\"stone\"}");
        assertTrue(
            findings.stream().anyMatch(finding ->
                finding.code().equals(Codes.factoryBad.code()) && finding.message().contains("minecraft:stone")
            ),
            () -> findings.toString()
        );
    }

    @Test
    void aBareFluidIdIsRejected() {
        List<Finding> findings = compileExpect("{\"kind\":\"fluid\",\"id\":\"water\"}");
        assertTrue(anyFactoryBad(findings), () -> findings.toString());
    }

    @Test
    void aTagIdStillNeedsANamespace() {
        List<Finding> findings = compileExpect("{\"kind\":\"tag\",\"registry\":\"block\",\"id\":\"logs\"}");
        assertTrue(anyFactoryBad(findings), () -> findings.toString());
    }

    private static boolean anyFactoryBad(List<Finding> findings) {
        return findings.stream().anyMatch(finding -> finding.code().equals(Codes.factoryBad.code()));
    }

    private static List<Finding> compileExpect(String expect) {
        CompileResult result = DocumentCompiler.compile(read(documentWithExpect(expect)), context(adapterCatalogs()));
        assertNull(result.blueprint());
        return result.findings().findings();
    }

    private static String documentWithExpect(String expect) {
        return """
                {"schemaVersion":1,"id":"test:host","bounds":[0,0,0,1,1,1],
                 "bays":[{"id":"test:host_bay","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"shared"}],
                 "checks":[
                   {"id":"test:check","bay":"test:host_bay","focus":{"kind":"occupant","cell":[0,0,0]},
                    "expect":%s}
                 ]}
                """.formatted(expect);
    }

    private static List<String> shape(Blueprint blueprint) {
        List<String> shape = new ArrayList<>();
        for (Check<?> check : blueprint.checks()) {
            shape.add(
                check.id() + "|" + check.bay().id() + "|" + check.expectation().subject().getName() + "|"
                        + check.expectation().meta().id() + "|" + check.expectation().meta().mismatchCode() + "|"
                        + check.description()
            );
        }
        return shape;
    }

    private static List<String> replies(Blueprint blueprint, WorldView view) {
        List<LatticePoint> cells = List.of(furnaceCell, furnaceCell, logCell, waterCell, entityCell, waterCell);
        List<String> replies = new ArrayList<>();
        for (int index = 0; index < blueprint.checks().size(); index++) {
            replies.add(reply(blueprint.checks().get(index).expectation(), view, cells.get(index)));
        }
        return replies;
    }

    private static String reply(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        Reply reply = test(expectation, view, cell);
        return switch (reply) {
            case Reply.Match ignored -> "match";
            case Reply.Mismatch mismatch -> "mismatch:" + Objects.requireNonNull(mismatch.findings().primary()).code();
            case Reply.Unreadable unreadable ->
                "unreadable:" + Objects.requireNonNull(unreadable.findings().primary()).code();
            case Reply.Abstain ignored -> "abstain";
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Reply test(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        OccupantSnapshot subject = occupant(cell);
        ExpectationContext context = ExpectationContext.of(new TestFocus(view, cell), subject, List.of(subject));
        return ((Expectation) expectation).test(context);
    }

    private static Blueprint javaBlueprint() {
        Bay bay = Bay.of(bayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.shared);
        return Blueprint.builder(documentId).bounds(bounds)
                .check(
                    Check.of(
                        NamespacedId.of("test", "property"),
                        bay,
                        Focuses.occupant(furnaceCell),
                        AdapterExpectations.property("lit", "true")
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "state"),
                        bay,
                        Focuses.occupant(furnaceCell),
                        AdapterExpectations.state(furnace(true))
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "block_tag"),
                        bay,
                        Focuses.occupant(logCell),
                        AdapterExpectations.blockTag(BlockTags.LOGS)
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "fluid_tag"),
                        bay,
                        Focuses.occupant(waterCell),
                        AdapterExpectations.fluidTag(FluidTags.WATER)
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "entity_tag"),
                        bay,
                        Focuses.occupant(entityCell),
                        AdapterExpectations.entityTag(EntityTypeTags.UNDEAD)
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "fluid_check"),
                        bay,
                        Focuses.occupant(waterCell),
                        AdapterExpectations.fluid(Fluids.WATER)
                    )
                ).build();
    }

    private static String hostDocument() {
        return """
                {"schemaVersion":1,"id":"test:host","bounds":[0,0,0,4,1,1],
                 "bays":[{"id":"test:host_bay","box":[0,0,0,4,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"shared"}],
                 "checks":[
                   {"id":"test:property","bay":"test:host_bay","focus":{"kind":"occupant","cell":[0,0,0]},
                    "expect":{"kind":"property","key":"lit","value":"true"}},
                   {"id":"test:state","bay":"test:host_bay","focus":{"kind":"occupant","cell":[0,0,0]},
                    "expect":{"kind":"state","block":"minecraft:furnace",
                              "properties":{"facing":"east","lit":"true"}}},
                   {"id":"test:block_tag","bay":"test:host_bay","focus":{"kind":"occupant","cell":[1,0,0]},
                    "expect":{"kind":"tag","registry":"block","id":"minecraft:logs"}},
                   {"id":"test:fluid_tag","bay":"test:host_bay","focus":{"kind":"occupant","cell":[2,0,0]},
                    "expect":{"kind":"tag","registry":"fluid","id":"minecraft:water"}},
                   {"id":"test:entity_tag","bay":"test:host_bay","focus":{"kind":"occupant","cell":[3,0,0]},
                    "expect":{"kind":"tag","registry":"entity","id":"minecraft:undead"}},
                   {"id":"test:fluid_check","bay":"test:host_bay","focus":{"kind":"occupant","cell":[2,0,0]},
                    "expect":{"kind":"fluid","id":"minecraft:water"}}
                 ]}
                """;
    }

    private static Catalogs adapterCatalogs() {
        return Catalogs.builder().expectations(AdapterExpectations.catalog()).build();
    }

    private static CompilerContext context(Catalogs catalogs) {
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static Document read(String text) {
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertTrue(errors.isEmpty(), () -> "unexpected findings: " + errors);
        assertNotNull(document, "the document resolves");
        return document;
    }

    private static BlockState furnace(boolean lit) {
        return Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(AbstractFurnaceBlock.LIT, lit);
    }

    private static LevelSnapshot matching() {
        BlockState water = Blocks.WATER.defaultBlockState();
        return snapshot(
            Map.of(furnaceCell, furnace(true), logCell, Blocks.OAK_LOG.defaultBlockState(), waterCell, water),
            List.of(entity(entityCell, EntityType.ZOMBIE))
        );
    }

    private static LevelSnapshot mismatching() {
        BlockState lava = Blocks.LAVA.defaultBlockState();
        return snapshot(
            Map.of(
                furnaceCell,
                Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                        .setValue(AbstractFurnaceBlock.LIT, false),
                logCell,
                Blocks.STONE.defaultBlockState(),
                waterCell,
                lava
            ),
            List.of(entity(entityCell, EntityType.MARKER))
        );
    }

    private static LevelSnapshot waterloggedSlab() {
        return snapshot(
            Map.of(waterCell, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true)),
            List.of()
        );
    }

    private static LevelSnapshot snapshot(
        Map<LatticePoint, BlockState> states,
        List<SnapshotLevel.CapturedEntity> entities
    ) {
        PalettedContainer<BlockState> section = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        FluidState[] fluids = new FluidState[SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize];
        Arrays.fill(fluids, Fluids.EMPTY.defaultFluidState());
        for (Map.Entry<LatticePoint, BlockState> entry : states.entrySet()) {
            LatticePoint cell = entry.getKey();
            section.set(cell.x() & 15, cell.y() & 15, cell.z() & 15, entry.getValue());
            fluids[SnapshotLevel.fluidIndex(cell.x(), cell.y(), cell.z())] = entry.getValue().getFluidState();
        }
        SnapshotLevel host = new SnapshotLevel(
            LatticePoint.origin,
            bounds,
            -64,
            384,
            Map.of(SnapshotLevel.worldSectionKey(0, 0, 0), section),
            Map.of(SnapshotLevel.worldSectionKey(0, 0, 0), fluids),
            Map.of(),
            entities
        );
        FrameData data = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            host
        );
        return new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), data);
    }

    private static SnapshotLevel.CapturedEntity entity(LatticePoint cell, EntityType<?> entityType) {
        var location = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(entityType), "entity type id");
        NamespacedId type = NamespacedId.of(location.getNamespace(), location.getPath());
        return new SnapshotLevel.CapturedEntity(UUID.nameUUIDFromBytes(new byte[]{1}), type, Cuboid.of(cell), entityType);
    }

    private static OccupantSnapshot occupant(LatticePoint cell) {
        NamespacedId type = NamespacedId.of("minecraft", "furnace");
        OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(type, OccupantKind.block), Cuboid.of(cell), List.of());
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
            return new BayView(bayId, CuboidSet.of(Cuboid.of(cell)), view, siting());
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
            return instance;
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
            return Locus.world(CuboidSet.of(Cuboid.of(cell)));
        }

        @Override
        public ContextView context() {
            return EmptyContext.instance;
        }

        @Override
        public Budget remaining() {
            return Budget.unbounded;
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

    private static final class CountingView implements WorldView {

        private final WorldView delegate;
        private final Map<HostType<?>, Integer> reads = new LinkedHashMap<>();

        private CountingView(WorldView delegate) {
            this.delegate = delegate;
        }

        private Map<HostType<?>, Integer> reads() {
            return Map.copyOf(reads);
        }

        @Override
        public <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
            reads.merge(type, 1, Integer::sum);
            return delegate.get(pos, type);
        }

        @Override
        public SnapshotId snapshotId() {
            return delegate.snapshotId();
        }

        @Override
        public int frame() {
            return delegate.frame();
        }

        @Override
        public int depth() {
            return delegate.depth();
        }

        @Override
        public LatticePoint origin() {
            return delegate.origin();
        }

        @Override
        public @Nullable WorldView parent() {
            return delegate.parent();
        }

        @Override
        public int cellSize() {
            return delegate.cellSize();
        }

        @Override
        public Cuboid bounds() {
            return delegate.bounds();
        }

        @Override
        public SnapshotPolicy policy() {
            return delegate.policy();
        }

        @Override
        public SectionKey sectionOf(LatticePoint cell) {
            return delegate.sectionOf(cell);
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            throw new AssertionError("a host expectation read a live section");
        }

        @Override
        public CellRead read(LatticePoint cell) {
            throw new AssertionError("a host expectation read a live cell");
        }

        @Override
        public OccupantSnapshot occupant(OccupantRef ref) {
            throw new AssertionError("a host expectation read a live occupant");
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            throw new AssertionError("a host expectation swept live occupants");
        }

        @Override
        public WorldView nested(LatticePoint cell) {
            throw new AssertionError("a host expectation read a live nested view");
        }

        @Override
        public long contentDigest(SectionKey key) {
            throw new AssertionError("a host expectation read a live digest");
        }
    }
}
