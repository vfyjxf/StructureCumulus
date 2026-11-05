package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial capture/host cases that need no running server: section refusal and partials, typed
 * host reads at negative coordinates, the report relay, host leaves, anchors and manager bounds.
 */
class CaptureAdversarialTest {

    private static final World world = World.of(NamespacedId.of("test", "adversarial_world"));
    private static final SnapshotId snapshot = new SnapshotId(world, 11);
    private static final OwnerId owner = OwnerId.of(NamespacedId.of("test", "adversarial_owner"));
    private static final Cuboid cellBounds = Cuboid.of(0, 0, 0, 2, 2, 2);
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId zombie = NamespacedId.of("minecraft", "zombie");
    private static final NamespacedId cow = NamespacedId.of("test", "cow");

    @BeforeAll
    static void bindTheVanillaFluidTag() {
        bind(holder(BuiltInRegistries.FLUID, Fluids.WATER), FluidTags.WATER);
    }

    // --- section slots: bounds, refusal, partial -------------------------------------------------

    @Test
    void anOutOfBoundsReadIsUnreadableNotAir() {
        LevelSnapshot view = view(cellBounds, Map.of(), null);

        CellRead read = view.read(LatticePoint.of(2, 0, 0));

        CellRead.Unreadable unreadable = assertInstanceOf(CellRead.Unreadable.class, read);
        assertEquals(ReadStatus.outOfBounds, unreadable.refusal().status(), "out-of-bounds status");
        assertEquals(Codes.unreadableRead.code(), unreadable.refusal().code(), "the kernel refusal code");
        assertEquals(ReadStatus.outOfBounds, view.statusOf(LatticePoint.of(2, 0, 0)), "statusOf agrees");
        assertNull(view.occupant(LatticePoint.of(2, 0, 0)), "an unreadable cell has no occupant");
        assertNotNull(view.refusalAt(LatticePoint.of(2, 0, 0)), "refusalAt names the refusal");
    }

    @Test
    void aRefusedSectionKeepsEveryCellUnreadable() {
        LatticePoint cell = LatticePoint.of(16, 0, 0);
        SectionKey key = SectionKey.of(world, 0, cell, 16);
        LevelSnapshot view = view(
            Cuboid.of(0, 0, 0, 32, 16, 16),
            Map.of(key, new SectionSlot.Refused(ReadStatus.notCaptured, AdapterCodes.notCaptured)),
            null
        );

        CellRead.Unreadable unreadable = assertInstanceOf(CellRead.Unreadable.class, view.read(cell));
        assertEquals(ReadStatus.notCaptured, unreadable.refusal().status(), "a refused section is not captured");
        assertEquals(AdapterCodes.notCaptured, unreadable.refusal().code(), "the host refusal code survives");
        assertFalse(view.slot(key).readable(), "the refused slot is not readable");
    }

    @Test
    void aPartialSectionRefusesOnlyItsCells() {
        LatticePoint unreadableCell = LatticePoint.of(0, 0, 0);
        LatticePoint readableCell = LatticePoint.of(1, 0, 0);
        SectionKey key = SectionKey.of(world, 0, readableCell, 16);
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(readableCell, block(readableCell)), 4L);
        LevelSnapshot view = view(
            cellBounds,
            Map.of(
                key,
                new SectionSlot.Partial(
                    section,
                    Map.of(unreadableCell, new ReadRefusal(ReadStatus.denied, AdapterCodes.unreadableTrait))
                )
            ),
            null
        );

        CellRead.Unreadable refused = assertInstanceOf(CellRead.Unreadable.class, view.read(unreadableCell));
        assertEquals(ReadStatus.denied, refused.refusal().status(), "a partial cell keeps its refusal");
        CellRead.Found found = assertInstanceOf(CellRead.Found.class, view.read(readableCell));
        assertEquals(stone, found.occupant().descriptor().type(), "the readable cell keeps its occupant");
        assertEquals(ReadStatus.readable, view.statusOf(readableCell), "the readable cell status");
        assertTrue(view.contentDigest(key) != 4L, "a partial digest includes the refused cells");
        assertEquals(
            0L,
            view.contentDigest(SectionKey.of(world, 0, LatticePoint.of(16, 0, 0), 16)),
            "an air section digests to zero"
        );
    }

    @Test
    void aPartialSectionRejectsAnOccupantThatIsAlsoUnreadable() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        SectionKey key = SectionKey.of(world, 0, cell, 16);
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(cell, block(cell)), 1L);

        assertThrows(
            IllegalArgumentException.class,
            () -> new SectionSlot.Partial(
                section,
                Map.of(cell, new ReadRefusal(ReadStatus.failed, AdapterCodes.unreadableTrait))
            ),
            "an occupant cannot be both present and unreadable"
        );
    }

    @Test
    void aPartialSectionRequiresAtLeastOneUnreadableCell() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new SectionSlot.Partial(
                SectionSnapshot.of(SectionKey.of(world, 0, LatticePoint.origin, 16), Map.of(), 0L),
                Map.of()
            ),
            "a partial section with no refused cell is a contradiction"
        );
    }

    // --- frame tree: nested bodies and per-cell precedence ---------------------------------------

    @Test
    void aNestedFrameIsReachableOnlyFromItsAnchor() {
        LatticePoint anchor = LatticePoint.of(3, 0, 3);
        LatticePoint childCell = LatticePoint.of(1, 0, 0);
        SectionKey childKey = SectionKey.of(world, 1, childCell, 16);
        FrameData child = new FrameData(
            snapshot,
            1,
            1,
            LatticePoint.of(100, 10, 100),
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(
                childKey,
                new SectionSlot.Present(SectionSnapshot.of(childKey, Map.of(childCell, block(childCell)), 2L))
            ),
            List.of(),
            List.of(),
            Map.of(),
            null
        );
        NamespacedId childType = NamespacedId.of("test", "child");
        OccupantSnapshot nestedBody = new OccupantSnapshot(
            new OccupantRef(snapshot, 0, childType, OccupantKind.nested, anchor),
            new OccupantDescriptor(childType, OccupantKind.nested),
            Cuboid.of(0, 0, 0, 2, 2, 2),
            List.of()
        );
        LevelSnapshot view = view(cellBounds, Map.of(), null, List.of(), List.of(nestedBody), Map.of(anchor, child));

        WorldView childView = view.nested(anchor);
        assertNotNull(childView, "the anchor opens the child");
        assertEquals(1, childView.frame(), "the child frame");
        assertEquals(1, childView.depth(), "the child depth");
        assertSame(view, childView.parent(), "the child parent is the root");
        assertEquals(LatticePoint.of(100, 10, 100), childView.origin(), "the child keeps its own origin");
        assertNull(view.nested(LatticePoint.origin), "only the anchor opens a child");
        assertEquals(
            1,
            view.occupants(cellBounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
            "one nested body in the parent"
        );
        assertEquals(
            stone,
            Objects.requireNonNull(childView.occupant(childCell)).descriptor().type(),
            "the child local cell is readable"
        );
        OccupantSnapshot nestedAtCell = view.occupant(childCell);
        assertEquals(
            OccupantKind.nested,
            nestedAtCell == null ? OccupantKind.block : nestedAtCell.descriptor().kind(),
            "a nested body footprint covers its parent cells"
        );
    }

    @Test
    void anEntityWinsOverAirButNotOverABlock() {
        LatticePoint blockCell = LatticePoint.of(0, 0, 0);
        LatticePoint airCell = LatticePoint.of(1, 0, 0);
        OccupantSnapshot entity = new OccupantSnapshot(
            new OccupantRef(snapshot, 0, zombie, OccupantKind.entity, airCell),
            new OccupantDescriptor(zombie, OccupantKind.entity),
            Cuboid.of(0, 0, 0, 2, 2, 1),
            List.of()
        );
        SectionKey key = SectionKey.of(world, 0, blockCell, 16);
        LevelSnapshot view = view(
            cellBounds,
            Map.of(key, new SectionSlot.Present(SectionSnapshot.of(key, Map.of(blockCell, block(blockCell)), 0L))),
            null,
            List.of(entity),
            List.of(),
            Map.of()
        );

        assertEquals(
            OccupantKind.entity,
            Objects.requireNonNull(view.occupant(airCell)).descriptor().kind(),
            "an entity wins over air"
        );
        assertEquals(
            OccupantKind.block,
            Objects.requireNonNull(view.occupant(blockCell)).descriptor().kind(),
            "a one-cell block wins over the entity footprint"
        );
        assertEquals(2, view.occupants(cellBounds, OccupantFilter.any()).size(), "block plus entity");
    }

    // --- typed host snapshot reads ---------------------------------------------------------------

    @Test
    void typedHostReadsDispatchUnknownTypesToNull() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        SnapshotLevel host = host(LatticePoint.origin, cellBounds, Map.of(cell, stairs));
        LevelSnapshot view = view(cellBounds, Map.of(), host);

        assertSame(stairs, view.get(cell, HostTypes.blockState), "the captured state is returned as captured");
        assertEquals(
            stairs.getFluidState(),
            view.get(cell, HostTypes.fluidState),
            "the fluid state is the captured one"
        );
        assertNull(view.get(cell, HostTypes.entityType), "no entity was captured");
        assertNull(view.get(cell, HostType.of(NamespacedId.of("test", "custom"))), "an unknown host type answers null");
        BlockState outside = view.get(LatticePoint.of(9, 0, 0), HostTypes.blockState);
        assertTrue(outside != null && outside.isAir(), "an out-of-bounds host read is air, not null");
        assertTrue(
            Objects.requireNonNull(view.get(LatticePoint.of(9, 0, 0), HostTypes.fluidState)).isEmpty(),
            "an out-of-bounds fluid read is empty"
        );
    }

    @Test
    void aViewWithoutAHostSnapshotAnswersNull() {
        LevelSnapshot view = view(cellBounds, Map.of(), null);

        assertNull(view.get(LatticePoint.origin, HostTypes.blockState), "no snapshot captures no block state");
        assertNull(view.get(LatticePoint.origin, HostTypes.fluidState), "no snapshot captures no fluid");
    }

    @Test
    void negativeCoordinatesReadTheirOwnSection() {
        LatticePoint negative = LatticePoint.of(-1, -1, -1);
        LatticePoint other = LatticePoint.of(-20, 5, 7);
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        Cuboid bounds = Cuboid.of(-24, -2, -2, 8, 8, 12);
        SnapshotLevel host = host(LatticePoint.origin, bounds, Map.of(negative, water, other, log));
        LevelSnapshot view = view(bounds, Map.of(), host);

        assertSame(water, view.get(negative, HostTypes.blockState), "the negative cell reads its own state");
        assertSame(log, view.get(other, HostTypes.blockState), "a far negative cell reads its own state");
        assertEquals(
            water.getFluidState(),
            view.get(negative, HostTypes.fluidState),
            "the negative cell resolves its own fluid"
        );
        BlockState outside = view.get(LatticePoint.of(-40, 0, 0), HostTypes.blockState);
        assertTrue(outside != null && outside.isAir(), "outside the snapshot is air");
    }

    @Test
    void theSectionDigestChangesWithTheBlockState() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        Cuboid region = Cuboid.of(0, 0, 0, 1, 1, 1);
        SnapshotLevel stoneHost = host(LatticePoint.origin, cellBounds, Map.of(cell, Blocks.STONE.defaultBlockState()));
        SnapshotLevel dirtHost = host(LatticePoint.origin, cellBounds, Map.of(cell, Blocks.DIRT.defaultBlockState()));

        assertTrue(
            stoneHost.contentDigest(region) != dirtHost.contentDigest(region),
            "same occupant type with a different block state changes the digest"
        );
        assertEquals(
            stoneHost.contentDigest(region),
            host(LatticePoint.origin, cellBounds, Map.of(cell, Blocks.STONE.defaultBlockState())).contentDigest(region),
            "the same snapshot content digests the same"
        );
    }

    // --- the capture report relay ---------------------------------------------------------------

    @Test
    void theRelayKeepsAKernelCodeUnderTheTrustGuard() {
        CaptureReport report = CaptureReport
                .of(List.of(new CaptureReport.Entry(Codes.unreadableRead.code(), "host claims a kernel code")));

        FindingBundle relayed = CaptureReports.relay(report, AdapterCodes.catalog());

        Finding finding = Objects.requireNonNull(relayed.primary(), "one relayed finding");
        assertEquals(Codes.unreadableRead.code(), finding.code(), "a kernel code is relayed as SC-OCC-0004");
        assertTrue(
            finding.notes().stream().anyMatch(note -> note.message().contains("is a kernel code")),
            "the relay notes the trust guard"
        );
        assertTrue(relayed.ofCode(AdapterCodes.unreadableTrait).isEmpty(), "the host code is not passed through");
    }

    @Test
    void theRelayUsesTheHostCatalogSeverityAndDefaultsToError() {
        CaptureReport report = CaptureReport.of(
            List.of(
                new CaptureReport.Entry(AdapterCodes.cut, "a cut child"),
                new CaptureReport.Entry(FindingCode.of(FindingCode.Family.mc, 4242), "unknown host code")
            )
        );

        FindingBundle relayed = CaptureReports.relay(report, AdapterCodes.catalog());

        assertEquals(2, relayed.size(), "every entry becomes a finding");
        Finding cut = relayed.findings().stream().filter(finding -> finding.code().equals(AdapterCodes.cut)).findFirst()
                .orElseThrow(() -> new AssertionError("the known host code is missing: " + relayed.findings()));
        assertEquals(Severity.warning, cut.severity(), "the host catalog supplies the severity");
        assertEquals(Severity.error, AdapterCodes.overCapEntry.severity(), "the over-cap catalog entry stays an error");
        Finding unknown = relayed.findings().stream()
                .filter(finding -> finding.code().equals(FindingCode.of(FindingCode.Family.mc, 4242))).findFirst()
                .orElseThrow(() -> new AssertionError("the unknown host code is missing: " + relayed.findings()));
        assertEquals(Severity.error, unknown.severity(), "an unknown host code defaults to an error");
        assertTrue(cut.labels().isEmpty(), "a null cell region adds no label");
    }

    @Test
    void theRelayLabelsTheCapturedRegion() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CaptureReport report = CaptureReport
                .of(List.of(CaptureReport.Entry.at(AdapterCodes.cut, "a cut child", cells)));

        Finding finding = Objects
                .requireNonNull(CaptureReports.relay(report, AdapterCodes.catalog()).primary(), "one finding");

        assertEquals(1, finding.labels().size(), "the region becomes one label");
        Label label = finding.labels().get(0);
        assertEquals(cells, label.locus().cells(), "the label names the captured cells");
        assertEquals("capture region", label.message(), "the label is the capture region");
    }

    @Test
    void anEmptyReportRelaysToAnEmptyBundle() {
        assertTrue(CaptureReports.relay(CaptureReport.empty(), AdapterCodes.catalog()).isEmpty(), "nothing to relay");
        assertTrue(CaptureReport.empty().isEmpty(), "the empty report");
        assertEquals(0, CaptureReport.empty().size(), "the empty report size");
    }

    @Test
    void aReportEntryRejectsBlankProseAndCopiesItsNotes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CaptureReport.Entry(AdapterCodes.overCap, "  "),
            "a blank message is rejected"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CaptureReport.Entry(AdapterCodes.overCap, "ok", List.of("  "), null),
            "a blank note is rejected"
        );
        List<String> notes = new ArrayList<>(List.of("one"));
        CaptureReport.Entry entry = new CaptureReport.Entry(AdapterCodes.overCap, "ok", notes, null);
        notes.add("two");
        assertEquals(List.of("one"), entry.notes(), "the entry copies its notes");
    }

    // --- host leaves: waterlogged slab, fluid, property/state/tag -------------------------------

    @Test
    void aWaterloggedSlabIsABlockPropertyAndStateButOnlyTheBlockLeafFails() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
        LevelSnapshot view = view(cellBounds, Map.of(), host(LatticePoint.origin, cellBounds, Map.of(cell, slab)));

        assertEquals(
            "match",
            replyText(AdapterExpectations.property("waterlogged", "true"), view, cell),
            "waterlogged is a block property"
        );
        assertEquals("match", replyText(AdapterExpectations.state(slab), view, cell), "the exact state matches");
        assertEquals(
            "match",
            replyText(AdapterExpectations.fluidTag(FluidTags.WATER), view, cell),
            "the slab reports the water fluid state"
        );
        Reply.Mismatch mismatch = assertInstanceOf(
            Reply.Mismatch.class,
            reply(AdapterExpectations.fluid(Fluids.WATER), view, cell)
        );
        assertEquals(
            AdapterCodes.fluidMismatch,
            Objects.requireNonNull(mismatch.findings().primary(), "primary").code(),
            "the failing fluid leaf keeps its own code"
        );
        assertTrue(mismatch.findings().ofCode(AdapterCodes.fluidMismatch).size() >= 1, "at least the block leaf fails");
        assertEquals(
            Codes.expected.code(),
            AdapterExpectations.fluid(Fluids.WATER).meta().mismatchCode(),
            "the composed all_of keeps the junction meta"
        );
    }

    @Test
    void realWaterMatchesTheFluidLeavesAndMissesPropertyLeaves() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        LevelSnapshot view = view(
            cellBounds,
            Map.of(),
            host(LatticePoint.origin, cellBounds, Map.of(cell, Blocks.WATER.defaultBlockState()))
        );

        assertEquals("match", replyText(AdapterExpectations.fluid(Fluids.WATER), view, cell), "real water matches");
        assertEquals(
            "match",
            replyText(AdapterExpectations.fluidTag(FluidTags.WATER), view, cell),
            "the water tag matches"
        );
        assertEquals(
            "mismatch:" + AdapterCodes.propertyMismatch,
            replyText(AdapterExpectations.property("waterlogged", "true"), view, cell),
            "water has no waterlogged property"
        );
        assertEquals(
            "mismatch:" + AdapterCodes.stateMismatch,
            replyText(AdapterExpectations.state(Blocks.OAK_SLAB.defaultBlockState()), view, cell),
            "the exact state mismatches"
        );
        assertEquals(
            "mismatch:" + AdapterCodes.tagMismatch,
            replyText(AdapterExpectations.blockTag(BlockTags.LOGS), view, cell),
            "a block tag leaf reads the captured state"
        );
    }

    @Test
    void anUncapturedHostValueIsUnreadableNotAMismatch() {
        LevelSnapshot view = view(cellBounds, Map.of(), null);
        LatticePoint cell = LatticePoint.of(0, 0, 0);

        Reply.Unreadable property = assertInstanceOf(
            Reply.Unreadable.class,
            reply(AdapterExpectations.property("lit", "true"), view, cell)
        );
        assertEquals(
            AdapterCodes.valueNotCaptured,
            Objects.requireNonNull(property.findings().primary(), "primary").code(),
            "an absent host value is SC-MC-0010"
        );
        Reply.Unreadable fluid = assertInstanceOf(
            Reply.Unreadable.class,
            reply(AdapterExpectations.fluid(Fluids.WATER), view, cell)
        );
        assertEquals(
            AdapterCodes.valueNotCaptured,
            Objects.requireNonNull(fluid.findings().primary(), "primary").code(),
            "the composed fluid leaves stay unreadable"
        );
    }

    // --- anchors and orientation policies -------------------------------------------------------

    @Test
    void anAnchorPropertyThatIsNotAHeadingIsInvalid() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.WATERLOGGED, true);
        SitingResult result = McAnchors.resolve(
            anchorBlueprint("waterlogged", "true"),
            LatticePoint.origin,
            view(cellBounds, Map.of(), host(LatticePoint.origin, cellBounds, Map.of(cell, stairs)))
        );

        assertFalse(result.succeeded(), "a non-heading property cannot resolve a siting");
        assertEquals(
            Codes.anchorInvalid.code(),
            Objects.requireNonNull(result.findings().primary(), "primary").code(),
            "anchor invalid"
        );
    }

    @Test
    void anAnchorHeadingOutsideThePolicyIsInvalid() {
        LatticePoint cell = LatticePoint.of(0, 0, 0);
        BlockState south = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState east = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        LevelSnapshot southView = view(
            cellBounds,
            Map.of(),
            host(LatticePoint.origin, cellBounds, Map.of(cell, south))
        );
        LevelSnapshot eastView = view(cellBounds, Map.of(), host(LatticePoint.origin, cellBounds, Map.of(cell, east)));

        SitingResult rejected = McAnchors.resolve(anchorBlueprint("facing", "south"), LatticePoint.origin, southView);
        assertFalse(rejected.succeeded(), "the policy allows only none and clockwise");
        SitingResult accepted = McAnchors.resolve(anchorBlueprint("facing", "east"), LatticePoint.origin, eastView);
        assertTrue(accepted.succeeded(), "east resolves through the clockwise turn");
        assertEquals(
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
            accepted.outcome().fold(siting -> siting.placement().orientation(), () -> null),
            "the resolved orientation is the allowed clockwise image"
        );
    }

    @Test
    void anAnchorOrAPolicyCannotStandWithoutTheOther() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(
            NamespacedId.of("test", "adv_anchor_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        NamespacedId role = NamespacedId.of("test", "main");
        Check<OccupantSnapshot> check = Check.of(
            NamespacedId.of("test", "adv_anchor_cell"),
            bay,
            Focuses.occupant(LatticePoint.origin),
            Expectations.anyOccupant()
        );

        IllegalArgumentException anchorOnly = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(NamespacedId.of("test", "adv_anchor_only")).bounds(bounds)
                    .anchor(new AnchorSpec(role, "facing", "north")).check(check).build(),
            "an anchor without a policy cannot build"
        );
        String anchorOnlyMessage = Objects.requireNonNull(anchorOnly.getMessage(), "anchor-only message");
        assertTrue(anchorOnlyMessage.contains("SC-BPT-0006"), () -> "anchor without policy: " + anchorOnlyMessage);
        IllegalArgumentException policyOnly = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(NamespacedId.of("test", "adv_policy_only")).bounds(bounds)
                    .orientation(OrientationPolicy.builder(role).allowTurns(Turn.none).build()).check(check).build(),
            "a policy without an anchor cannot build"
        );
        String policyOnlyMessage = Objects.requireNonNull(policyOnly.getMessage(), "policy-only message");
        assertTrue(policyOnlyMessage.contains("SC-BPT-0006"), () -> "policy without anchor: " + policyOnlyMessage);
    }

    @Test
    void twoAllowedOrientationsWithOneNorthImagePickTheCanonicalFirst() {
        Map<Heading, List<Orientation>> byHeading = new LinkedHashMap<>();
        for (Orientation orientation : Orientation.all()) {
            byHeading.computeIfAbsent(orientation.apply(Heading.north), ignored -> new ArrayList<>()).add(orientation);
        }
        Map.Entry<Heading, List<Orientation>> duplicated = byHeading.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1).findFirst()
                .orElseThrow(() -> new AssertionError("no two orientations share a north image"));
        Heading heading = duplicated.getKey();
        List<Orientation> candidates = duplicated.getValue();
        OrientationPolicy policy = OrientationPolicy.builder(NamespacedId.of("test", "role"))
                .allow(candidates.get(1), candidates.get(0)).build();

        assertEquals(candidates.get(0), policy.select(heading), "the lowest ordinal wins");
        assertEquals(2, policy.allowed().size(), "both orientations stay allowed");
        OrientationPolicy uprightOnly = OrientationPolicy.builder(NamespacedId.of("test", "role"))
                .allowTurns(Turn.none, Turn.clockwise).build();
        assertEquals(
            Orientation.identity,
            uprightOnly.select(Heading.north),
            "the identity is the first north image among the upright turns"
        );
        assertNull(
            OrientationPolicy.builder(NamespacedId.of("test", "other"))
                    .allow(Orientation.of(Turn.none, Tilt.pitchUp, Flip.none)).build().select(Heading.east),
            "a heading no allowed orientation faces answers null"
        );
        assertEquals(48, Orientation.all().size(), "all 48 orientations");
    }

    @Test
    void uprightOnlyRejectsATiltedOrientation() {
        Orientation pitched = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);

        assertThrows(
            IllegalArgumentException.class,
            () -> OrientationPolicy.builder(NamespacedId.of("test", "role")).allow(pitched).uprightOnly().build(),
            "uprightOnly refuses an orientation that moves UP"
        );
    }

    // --- entity windows and the sited policy ----------------------------------------------------

    @Test
    void anEntityWindowInflatesAndClampsToTheLattice() {
        assertEquals(3, EntityWindow.maxEntitySpan, "the span is three");
        assertNull(EntityWindow.of(Cuboid.of(0, 0, 0, 0, 0, 0), cow), "an empty window is rejected");
        EntityWindow window = Objects
                .requireNonNull(EntityWindow.of(Cuboid.of(0, 0, 0, 1, 1, 1), cow), "the window exists");
        assertEquals(Cuboid.of(-3, -3, -3, 4, 4, 4), window.cells(), "the window inflates by the span");
        EntityWindow clamped = Objects.requireNonNull(
            EntityWindow.of(
                Cuboid.of(LatticePoint.of(LatticePoint.maxXz - 1, 0, 0), LatticePoint.of(LatticePoint.maxXz, 1, 1)),
                cow
            ),
            "the clamped window exists"
        );
        assertEquals(LatticePoint.maxXz, clamped.cells().max().x(), "the window stops at the lattice edge");
    }

    @Test
    void aSitedPolicyMapsLocalWindowsThroughANegativeSiting() {
        Blueprint blueprint = entityWindowBlueprint();
        Cuboid local = blueprint.reads().windows().get(0).cells();

        SnapshotPolicy policy = CommandSupport
                .policy(blueprint, Siting.of(LatticePoint.of(-8, 0, -8), Orientation.identity));

        assertEquals(1, policy.windows().size(), "one window survives");
        assertEquals(
            local.translate(-8, 0, -8),
            policy.windows().get(0).cells(),
            "a negative siting shifts the window"
        );
        SnapshotPolicy dropped = CommandSupport
                .policy(blueprint, Siting.of(LatticePoint.of(LatticePoint.minXz, 0, 0), Orientation.identity));
        assertTrue(dropped.windows().isEmpty(), "a window that leaves the lattice is dropped");
    }

    // --- manager bounds integration -------------------------------------------------------------

    @Test
    void commitBoundsAreWorldFrameAndFindAtUsesThemVerbatim() {
        StructureManager manager = StructureManager.create();
        Siting siting = Siting.of(LatticePoint.of(10, 64, 10), Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
        StructureInstance instance = commit(manager, siting, CuboidSet.of(Cuboid.of(10, 64, 10, 12, 66, 12)));

        assertSame(instance, manager.find(instance.id()), "the id lookup");
        assertSame(instance, manager.find(owner, siting), "the owner/siting lookup");
        assertEquals(1, manager.findAt(LatticePoint.of(11, 65, 11)).size(), "a world cell inside the bounds finds it");
        assertTrue(
            manager.findAt(LatticePoint.of(1, 1, 1)).isEmpty(),
            "a local-frame cell does not find the sited structure"
        );
        assertTrue(
            manager.findIn(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))).isEmpty(),
            "a local-frame box finds nothing"
        );
    }

    @Test
    void aLocalFrameCommitIsAcceptedVerbatimAndDoesNotAnswerWorldCells() {
        StructureManager manager = StructureManager.create();
        Siting sited = Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity);
        commit(manager, sited, CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)));

        assertTrue(
            manager.findAt(LatticePoint.of(10, 64, 10)).isEmpty(),
            "the manager never translates the committed bounds"
        );
        assertEquals(1, manager.findAt(LatticePoint.of(0, 0, 0)).size(), "the box answers its own coordinates");
    }

    @Test
    void findAtCoversEachDisjointBoxAndTheGapBetweenThem() {
        StructureManager manager = StructureManager.create();
        CuboidSet bounds = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(4, 0, 0, 6, 1, 1));
        commit(manager, Siting.of(LatticePoint.origin, Orientation.identity), bounds);

        assertEquals(1, manager.findAt(LatticePoint.of(0, 0, 0)).size(), "the first box");
        assertEquals(1, manager.findAt(LatticePoint.of(5, 0, 0)).size(), "the second box");
        assertTrue(manager.findAt(LatticePoint.of(3, 0, 0)).isEmpty(), "the gap between the boxes contains nothing");
        assertEquals(
            1,
            manager.findIn(CuboidSet.of(Cuboid.of(1, 0, 0, 5, 1, 1))).size(),
            "a query touching both boxes finds the instance once"
        );
    }

    @Test
    void unloadIsWorldScopedAndLoadRestoresTheSerial() {
        StructureManager manager = StructureManager.create();
        World firstWorld = World.of(NamespacedId.of("test", "first_world"));
        World secondWorld = World.of(NamespacedId.of("test", "second_world"));
        OwnerId secondOwner = OwnerId.of(NamespacedId.of("test", "second_owner"));
        StructureInstance first = commitIn(
            manager,
            firstWorld,
            owner,
            "first",
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))
        );
        StructureInstance second = commitIn(
            manager,
            secondWorld,
            secondOwner,
            "second",
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))
        );
        assertEquals(2, manager.instances().size(), "two worlds, two instances");

        manager.onEvent(new StructureEvent.Unloaded(firstWorld));

        assertNull(manager.find(first.id()), "the unloaded world drops its instance");
        assertNotNull(manager.find(second.id()), "the other world keeps its instance");
        manager.onEvent(new StructureEvent.Loaded(firstWorld, List.of(first)));
        assertNotNull(manager.find(first.id()), "the loaded instance is restored");
        assertEquals(2, manager.instances().size(), "a world-scoped load replaces only the loaded world");
        assertNotNull(manager.find(second.id()), "the other world's instance survives the load");
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(firstWorld, List.of(first, first))),
            "a duplicate siting in the loaded list is rejected"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.onEvent(new StructureEvent.Loaded(firstWorld, List.of(second))),
            "an instance restored for the wrong world is rejected"
        );
        StructureAttempt next = manager.begin(
            firstWorld,
            new StructureSpec(
                owner,
                Siting.of(LatticePoint.of(5, 0, 5), Orientation.identity),
                NamespacedId.of("test", "next")
            )
        );
        assertTrue(next.id().serial() > first.id().serial(), "the serial advances past every loaded instance");
    }

    @Test
    void oneManagerScopesOwnerSitingsPerWorldAndTheUnscopedLookupIsAmbiguous() {
        StructureManager manager = StructureManager.create();
        World firstWorld = World.of(NamespacedId.of("test", "global_first_world"));
        World secondWorld = World.of(NamespacedId.of("test", "global_second_world"));
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        StructureInstance first = commitIn(
            manager,
            firstWorld,
            owner,
            "global_first",
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))
        );
        StructureInstance second = commitIn(
            manager,
            secondWorld,
            owner,
            "global_second",
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))
        );

        assertEquals(2, manager.instances().size(), "two worlds can form the same owner/siting");
        assertSame(first, manager.find(firstWorld, owner, siting), "the world-scoped lookup answers each instance");
        assertSame(second, manager.find(secondWorld, owner, siting), "the world-scoped lookup answers each instance");
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.find(owner, siting),
            "the unscoped lookup is ambiguous across worlds"
        );
    }

    @Test
    void aCommitClosesTheAttemptAndReplacesAnExistingOwnerSiting() {
        StructureManager manager = StructureManager.create();
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        StructureAttempt aborted = manager.begin(
            world,
            new StructureSpec(
                owner,
                Siting.of(LatticePoint.of(9, 0, 9), Orientation.identity),
                NamespacedId.of("test", "aborted")
            )
        );
        aborted.abort();
        assertThrows(
            IllegalStateException.class,
            () -> aborted.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))),
            "a closed attempt cannot commit"
        );

        StructureInstance first = commit(manager, siting, CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
        StructureAttempt replacement = manager
                .begin(world, new StructureSpec(owner, siting, NamespacedId.of("test", "replacement")));
        StructureInstance second = replacement.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
        assertEquals(1, manager.instances().size(), "the replacement evicts the previous instance");
        assertSame(second, manager.find(owner, siting), "the new instance owns the siting");
        assertNull(manager.find(first.id()), "the previous instance is gone");
        assertThrows(
            IllegalStateException.class,
            () -> replacement.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))),
            "a committed attempt is closed"
        );
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static LevelSnapshot view(
        Cuboid bounds,
        Map<SectionKey, SectionSlot> sections,
        @Nullable SnapshotLevel host
    ) {
        return view(bounds, sections, host, List.of(), List.of(), Map.of());
    }

    private static LevelSnapshot view(
        Cuboid bounds,
        Map<SectionKey, SectionSlot> sections,
        @Nullable SnapshotLevel host,
        List<OccupantSnapshot> entities,
        List<OccupantSnapshot> nestedBodies,
        Map<LatticePoint, FrameData> children
    ) {
        FrameData data = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            sections,
            entities,
            nestedBodies,
            children,
            host
        );
        return new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), data);
    }

    private static OccupantSnapshot block(LatticePoint cell) {
        return new OccupantSnapshot(
            new OccupantRef(snapshot, 0, stone, OccupantKind.block, cell),
            new OccupantDescriptor(stone, OccupantKind.block),
            Cuboid.of(cell),
            List.of()
        );
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
        return new SnapshotLevel(origin, bounds, -64, 384, sections, fluids, List.of());
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

    private static Blueprint entityWindowBlueprint() {
        NamespacedId id = NamespacedId.of("test", "adv_window_blueprint");
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(
            NamespacedId.of("test", "adv_window_bay"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.shared
        );
        Check<OccupantSnapshot> check = Check
                .of(NamespacedId.of("test", "adv_window_check"), bay, Focuses.entity(cow), Expectations.anyOccupant());
        return Blueprint.builder(id).bounds(bounds).check(check).build();
    }

    private static Blueprint anchorBlueprint(String property, String value) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(
            NamespacedId.of("test", "adv_anchor_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        NamespacedId role = NamespacedId.of("test", "main");
        return Blueprint.builder(NamespacedId.of("test", "adv_anchor_" + property + "_" + value)).bounds(bounds)
                .anchor(new AnchorSpec(role, property, value))
                .orientation(OrientationPolicy.builder(role).allowTurns(Turn.none, Turn.clockwise).build())
                .check(
                    Check.of(
                        NamespacedId.of("test", "adv_anchor_cell"),
                        bay,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.anyOccupant()
                    )
                ).build();
    }

    private static String replyText(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        return switch (reply(expectation, view, cell)) {
            case Reply.Match ignored -> "match";
            case Reply.Mismatch mismatch ->
                "mismatch:" + Objects.requireNonNull(mismatch.findings().primary(), "primary").code();
            case Reply.Unreadable unreadable ->
                "unreadable:" + Objects.requireNonNull(unreadable.findings().primary(), "primary").code();
            case Reply.Abstain ignored -> "abstain";
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Reply reply(Expectation<?> expectation, WorldView view, LatticePoint cell) {
        OccupantSnapshot subject = view.read(cell) instanceof CellRead.Found found ? found.occupant() : block(cell);
        ExpectationContext context = ExpectationContext.of(new AdversarialFocus(view, cell), subject, List.of(subject));
        return ((Expectation) expectation).test(context);
    }

    private static StructureInstance commit(StructureManager manager, Siting siting, CuboidSet bounds) {
        return commitIn(manager, world, owner, "adv", bounds, siting);
    }

    private static StructureInstance commitIn(
        StructureManager manager,
        World targetWorld,
        OwnerId targetOwner,
        String path,
        CuboidSet bounds
    ) {
        return commitIn(
            manager,
            targetWorld,
            targetOwner,
            path,
            bounds,
            Siting.of(LatticePoint.origin, Orientation.identity)
        );
    }

    private static StructureInstance commitIn(
        StructureManager manager,
        World targetWorld,
        OwnerId targetOwner,
        String path,
        CuboidSet bounds,
        Siting siting
    ) {
        StructureAttempt attempt = manager.begin(
            targetWorld,
            new StructureSpec(targetOwner, siting, NamespacedId.of("test", path + "_structure"))
        );
        return attempt.commit(bounds);
    }

    private record AdversarialFocus(WorldView view, LatticePoint cell) implements FocusContext {

        @Override
        public BayView bay() {
            return new BayView(NamespacedId.of("test", "adv_bay"), CuboidSet.of(Cuboid.of(cell)), view, siting());
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
            return Locus.world(cells());
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
}
