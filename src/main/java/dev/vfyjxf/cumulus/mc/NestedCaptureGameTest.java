package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.AttachmentNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.AttachmentNestedExtentProviders;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentAccesses;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolder;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import dev.vfyjxf.cumulus.mc.capability.NestedExtents;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** M2d GameTests: nested frames, cut rules and source precedence through the fake-provider seam. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class NestedCaptureGameTest {

    private static final int maxElements = 1_000_000;

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void nestedFrameKeepsTheParentOriginAndLocalOccupant(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        BlockPos inner = new BlockPos(1, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        helper.setBlock(inner, Blocks.DIRT);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        FakeLookup lookup = new FakeLookup();
        NestedExtent extent = extent("test:nested", worldAnchor, 2, 2, 2, worldAnchor);
        lookup.blocks.put(worldAnchor, extent);
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("nested capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            WorldView child = view.nested(localAnchor);
            if (child == null) {
                helper.fail("the nested view is missing");
                return;
            }
            helper.assertValueEqual(view.frame(), 0, "root frame");
            helper.assertValueEqual(view.depth(), 0, "root depth");
            helper.assertTrue(view.parent() == null, "the root has no parent");
            helper.assertValueEqual(view.origin(), LatticePoint.origin, "root origin");
            helper.assertValueEqual(child.frame(), 1, "child frame");
            helper.assertValueEqual(child.depth(), 1, "child depth");
            helper.assertValueEqual(child.parent(), view, "child parent");
            helper.assertValueEqual(child.origin(), extent.origin(), "child origin");
            helper.assertValueEqual(child.bounds(), Cuboid.of(0, 0, 0, 2, 2, 2), "child local bounds");
            helper.assertValueEqual(child.snapshotId(), view.snapshotId(), "one snapshot id");
            helper.assertValueEqual(child.policy(), view.policy(), "one policy");

            LatticePoint localInner = LatticePoint.of(1, 0, 0);
            OccupantSnapshot dirt = child.occupant(localInner);
            if (dirt == null) {
                helper.fail("the child local cell is unreadable");
                return;
            }
            helper.assertValueEqual(dirt.descriptor().type(), NamespacedId.of("minecraft", "dirt"), "child cell type");
            helper.assertValueEqual(dirt.ref().frame(), 1, "the child body is minted in the child");
            helper.assertValueEqual(
                child.sectionOf(localInner).frame(),
                1,
                "the child section belongs to the child frame"
            );
            helper.assertTrue(view.nested(localInner) == null, "only the anchor registers a child");
            helper.assertTrue(child.nested(LatticePoint.origin) == null, "the child has no grandchild");

            List<OccupantSnapshot> nestedBodies = view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested));
            helper.assertValueEqual(nestedBodies.size(), 1, "one nested body in the parent");
            OccupantSnapshot body = nestedBodies.get(0);
            helper.assertValueEqual(body.descriptor().type(), NamespacedId.of("test", "nested"), "nested body type");
            helper.assertValueEqual(body.ref().anchor(), localAnchor, "nested body anchor");
            helper.assertTrue(body.ref().body() == null, "a nested body has no body identity");
            helper.assertValueEqual(view.occupant(body.ref()), body, "the nested body resolves by ref");
            helper.assertValueEqual(
                view.occupants(bounds, OccupantFilter.ofType(NamespacedId.of("test", "nested"))).size(),
                1,
                "the nested body answers a type filter"
            );
            helper.assertValueEqual(view.occupants(bounds, OccupantFilter.any()).size(), 2, "block plus nested body");
            helper.assertTrue(
                view.occupants(
                    Cuboid.of(
                        bounds.min().x() + 1_000,
                        bounds.min().y(),
                        bounds.min().z(),
                        bounds.max().x() + 1_000,
                        bounds.max().y(),
                        bounds.max().z()
                    ),
                    OccupantFilter.any()
                ).isEmpty(),
                "a far box answers nothing"
            );

            OccupantSnapshot stone = view.occupant(localAnchor);
            if (stone == null) {
                helper.fail("the root anchor is unreadable");
                return;
            }
            helper.assertValueEqual(stone.descriptor().kind(), OccupantKind.block, "the smaller block wins the cell");
            helper.assertTrue(view.occupant(dirt.ref()) == null, "the parent rejects a child ref");
            helper.assertTrue(child.occupant(stone.ref()) == null, "the child rejects a parent ref");
            helper.assertTrue(child.occupant(body.ref()) == null, "the child rejects the parent's nested body");
            helper.assertTrue(
                view.occupant(
                    new OccupantRef(view.snapshotId(), 1, body.descriptor().type(), OccupantKind.nested, localAnchor)
                ) == null,
                "a nested ref minted in the child frame is not answerable in the root"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void depthZeroQueriesOnceAndDepthOneBuildsOneLevel(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent("test:nested", worldAnchor, 1, 1, 1, worldAnchor));
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult flat = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 0, false)));
        if (!(flat instanceof CaptureResult.Captured captured)) {
            helper.fail("depth-zero capture failed: " + flat);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.view().nested(localAnchor) == null, "depth zero cuts everything");
            helper.assertValueEqual(
                lease.view().occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                1,
                "depth zero keeps the discovered body"
            );
        }
        helper.assertValueEqual(lookup.blockQueries, 1, "depth zero queries the provider once");
        helper.assertValueEqual(1, captured.report().size(), "depth zero reports the cut");
        helper.assertValueEqual(AdapterCodes.cut, captured.report().entries().get(0).code(), "the cut code");
        helper.assertTrue(
            captured.report().entries().get(0).notes().stream().anyMatch(note -> note.equals("reason: depth")),
            "the cut names the depth ceiling"
        );

        CaptureResult nested = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(nested instanceof CaptureResult.Captured nestedCaptured)) {
            helper.fail("depth-one capture failed: " + nested);
            return;
        }
        try (SnapshotLease lease = nestedCaptured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("depth one did not build the child");
                return;
            }
            helper.assertValueEqual(child.depth(), 1, "one nested level");
            helper.assertTrue(child.nested(LatticePoint.origin) == null, "the cap child builds no grandchild");
            helper.assertValueEqual(
                child.occupants(child.bounds(), OccupantFilter.ofKind(OccupantKind.nested)).size(),
                1,
                "the cap child keeps its discovered body"
            );
        }
        helper.assertValueEqual(lookup.blockQueries, 3, "the root and the cap child each query once");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void rootOverTheCeilingIsTooLarge(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        Cuboid bounds = Cuboid.of(
            anchor.getX(),
            anchor.getY(),
            anchor.getZ(),
            anchor.getX() + 1,
            anchor.getY() + 1,
            anchor.getZ() + 1
        );
        LevelCapture capture = new LevelCapture(helper.getLevel(), new FakeLookup().lookup());
        CaptureResult result = capture.capture(new CaptureRequest(bounds, policy(0, 1, true)));

        if (!(result instanceof CaptureResult.TooLarge tooLarge)) {
            helper.fail("the root over the ceiling was not rejected: " + result);
            return;
        }
        helper.assertValueEqual(tooLarge.bounds(), bounds, "the rejected bounds");
        helper.assertValueEqual(tooLarge.elements(), 1, "the root volume");
        helper.assertValueEqual(capture.currentRevision(), 0L, "a rejected root stamps no revision");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void siblingFramesFollowTheAnchorOrder(GameTestHelper helper) {
        BlockPos first = new BlockPos(0, 1, 0);
        BlockPos second = new BlockPos(1, 1, 0);
        helper.setBlock(first, Blocks.STONE);
        helper.setBlock(second, Blocks.DIRT);
        BlockPos worldFirst = helper.absolutePos(first);
        BlockPos worldSecond = helper.absolutePos(second);
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldFirst, extent("test:first", worldFirst, 1, 1, 1, worldFirst));
        lookup.blocks.put(worldSecond, extent("test:second", worldSecond, 1, 1, 1, worldSecond));
        Cuboid bounds = Cuboid.of(
            worldFirst.getX(),
            worldFirst.getY(),
            worldFirst.getZ(),
            worldSecond.getX() + 1,
            worldFirst.getY() + 1,
            worldFirst.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("sibling capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            WorldView firstChild = view
                    .nested(LatticePoint.of(worldFirst.getX(), worldFirst.getY(), worldFirst.getZ()));
            WorldView secondChild = view
                    .nested(LatticePoint.of(worldSecond.getX(), worldSecond.getY(), worldSecond.getZ()));
            if (firstChild == null || secondChild == null) {
                helper.fail("a sibling child is missing");
                return;
            }
            helper.assertValueEqual(firstChild.frame(), 1, "the first anchor gets frame 1");
            helper.assertValueEqual(secondChild.frame(), 2, "the second anchor gets frame 2");
            helper.assertValueEqual(firstChild.depth(), 1, "the first sibling depth");
            helper.assertValueEqual(secondChild.depth(), 1, "the second sibling depth");
            helper.assertValueEqual(firstChild.parent(), view, "the first sibling parent");
            helper.assertValueEqual(secondChild.parent(), view, "the second sibling parent");
            helper.assertValueEqual(
                view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                2,
                "both nested bodies are kept"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aCycleKeepsTheBodyAndCutsTheChild(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        BlockPos inner = new BlockPos(1, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        helper.setBlock(inner, Blocks.DIRT);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        BlockPos worldInner = helper.absolutePos(inner);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        NestedExtent extent = extent("test:cycle", worldInner, 2, 2, 2, worldInner);
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent);
        lookup.blocks.put(worldInner, extent);
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 2, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("cycle capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            WorldView child = view.nested(localAnchor);
            if (child == null) {
                helper.fail("the first child was cut");
                return;
            }
            LatticePoint repeated = LatticePoint.origin;
            helper.assertTrue(child.nested(repeated) == null, "the repeated declaration is cut");
            List<OccupantSnapshot> bodies = child.occupants(child.bounds(), OccupantFilter.ofKind(OccupantKind.nested));
            helper.assertValueEqual(bodies.size(), 1, "the cut keeps the nested body");
            helper.assertValueEqual(bodies.get(0).ref().anchor(), repeated, "the cut body anchor");
        }
        helper.assertTrue(lookup.blockQueries >= 2, "the root and the child were queried");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void budgetCountsTheRootAndEveryBuiltChild(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        BlockPos inner = new BlockPos(2, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        helper.setBlock(inner, Blocks.DIRT);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        BlockPos worldInner = helper.absolutePos(inner);
        BlockPos childOrigin = new BlockPos(worldAnchor.getX() + 1, worldAnchor.getY(), worldAnchor.getZ());
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        LatticePoint localInner = LatticePoint.of(1, 0, 0);
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent("test:budget", childOrigin, 2, 1, 1, childOrigin));
        lookup.blocks.put(worldInner, extent("test:budget_child", worldInner, 1, 1, 1, worldInner));
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 2,
            worldAnchor.getY() + 2,
            worldAnchor.getZ() + 1
        );

        CaptureResult cut = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(5, 1, false)));
        if (!(cut instanceof CaptureResult.Captured captured)) {
            helper.fail("budget capture failed: " + cut);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.view().nested(localAnchor) == null, "the over-budget child is cut");
            helper.assertValueEqual(
                lease.view().occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                1,
                "the cut keeps the nested body"
            );
        }

        CaptureResult childFits = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(6, 2, false)));
        if (!(childFits instanceof CaptureResult.Captured childCaptured)) {
            helper.fail("child capture failed: " + childFits);
            return;
        }
        try (SnapshotLease lease = childCaptured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("the fitting child is missing");
                return;
            }
            helper.assertTrue(child.nested(localInner) == null, "the grandchild is over the shared ceiling");
            helper.assertValueEqual(
                child.occupants(child.bounds(), OccupantFilter.ofKind(OccupantKind.nested)).size(),
                1,
                "the cut keeps the child nested body"
            );
        }

        CaptureResult allFit = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(7, 2, false)));
        if (!(allFit instanceof CaptureResult.Captured allCaptured)) {
            helper.fail("grandchild capture failed: " + allFit);
            return;
        }
        try (SnapshotLease lease = allCaptured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("the child is missing at the full budget");
                return;
            }
            helper.assertTrue(child.nested(localInner) != null, "the grandchild fits the shared ceiling");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void unloadedChildChunkIsCutAndNeverLoaded(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        BlockPos far = new BlockPos(worldAnchor.getX() + 1_000_000, worldAnchor.getY(), worldAnchor.getZ());
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent("test:unloaded", far, 1, 1, 1, far));
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("unloaded capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.view().nested(localAnchor) == null, "the unloaded child is cut");
        }
        helper.assertTrue(lookup.blockQueries > 0, "the source was declared");
        helper.assertTrue(
            helper.getLevel().getChunkSource().getChunkNow(Math.floorDiv(far.getX(), 16), Math.floorDiv(far.getZ(), 16))
                    == null,
            "the cut child chunk was not loaded"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void overCapCarriesOne0005Entry(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Cuboid bounds = Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 2,
            origin.getY() + 2,
            origin.getZ() + 2
        );
        SnapshotPolicy tiny = new SnapshotPolicy(7, 0, Set.of(), Set.of(), List.of());
        CaptureResult result = new LevelCapture(helper.getLevel(), new FakeLookup().lookup())
                .capture(new CaptureRequest(bounds, tiny));
        if (!(result instanceof CaptureResult.TooLarge tooLarge)) {
            helper.fail("the tiny ceiling did not refuse the root: " + result);
            return;
        }
        helper.assertValueEqual(1, tooLarge.report().size(), "one over-cap entry");
        var entry = tooLarge.report().entries().get(0);
        helper.assertValueEqual(AdapterCodes.overCap, entry.code(), "the over-cap code");
        helper.assertValueEqual(bounds.asSet(), entry.cells(), "the root bounds are the report region");
        helper.assertTrue(
            entry.notes().stream().anyMatch(note -> note.contains("ceiling")),
            "the entry names the ceiling"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void rootFrameCutCarriesOne0003EntryWithWorldCells(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        BlockPos far = new BlockPos(worldAnchor.getX() + 1_000_000, worldAnchor.getY(), worldAnchor.getZ());
        FakeLookup lookup = new FakeLookup();
        NestedExtent extent = extent("test:report_cut", far, 1, 1, 1, far);
        lookup.blocks.put(worldAnchor, extent);
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the cut capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.view().nested(localAnchor) == null, "the cut child answers null");
        }
        helper.assertValueEqual(1, captured.report().size(), "one cut entry");
        var entry = captured.report().entries().get(0);
        helper.assertValueEqual(AdapterCodes.cut, entry.code(), "the cut code");
        helper.assertValueEqual(extent.bounds().asSet(), entry.cells(), "a root-frame cut carries world cells");
        helper.assertTrue(
            entry.notes().stream().anyMatch(note -> note.equals("reason: unloaded")),
            "the entry names the cut reason"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aPlainCaptureHasAnEmptyReport(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Cuboid bounds = Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 1,
            origin.getY() + 1,
            origin.getZ() + 1
        );
        CaptureResult result = new LevelCapture(helper.getLevel(), new FakeLookup().lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 0, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the plain capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.isCurrent(), "the plain capture is leased");
        }
        helper.assertTrue(captured.report().isEmpty(), "a clean capture has an empty report");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void providerExceptionsAreSkipped(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        Entity chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 1.0, 0.5));
        FakeLookup lookup = new FakeLookup();
        lookup.throwingBlocks.add(worldAnchor);
        lookup.throwingEntities.add(chicken.getUUID());
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("a provider exception failed the capture: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            helper.assertTrue(view.nested(localAnchor) == null, "the throwing source declares nothing");
            helper.assertValueEqual(
                view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested)).size(),
                0,
                "no nested body survives a throwing provider"
            );
            OccupantSnapshot stone = view.occupant(localAnchor);
            if (stone == null) {
                helper.fail("the block occupant was lost");
                return;
            }
            helper.assertValueEqual(stone.descriptor().type(), NamespacedId.of("minecraft", "stone"), "block type");
        }
        helper.assertTrue(lookup.blockQueries > 0, "the block provider was asked");
        helper.assertTrue(lookup.entityQueries > 0, "the entity provider was asked");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockSourceWinsOverAnEntitySourceAtOneCell(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        Entity chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 1.0, 0.5));
        FakeLookup lookup = new FakeLookup();
        NestedExtent blockExtent = extent("test:block_source", worldAnchor, 2, 2, 2, worldAnchor);
        NestedExtent entityExtent = extent(
            "test:entity_source",
            worldAnchor,
            2,
            2,
            2,
            new BlockPos(worldAnchor.getX() + 1, worldAnchor.getY(), worldAnchor.getZ())
        );
        lookup.blocks.put(worldAnchor, blockExtent);
        lookup.entities.put(chicken.getUUID(), entityExtent);
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("precedence capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("the block source did not build a child");
                return;
            }
            helper.assertValueEqual(
                child.origin(),
                LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ()),
                "the block origin wins"
            );
            List<OccupantSnapshot> bodies = lease.view().occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested));
            helper.assertValueEqual(bodies.size(), 1, "one source per cell");
            helper.assertValueEqual(
                bodies.get(0).descriptor().type(),
                NamespacedId.of("test", "block_source"),
                "the block source wins"
            );
        }
        helper.assertValueEqual(lookup.entityQueries, 0, "the entity source is not consulted over a block source");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anEntityDeclaresANestedExtentWithoutBeingAnOccupant(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        Entity chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 1.0, 0.5));
        FakeLookup lookup = new FakeLookup();
        lookup.entities.put(chicken.getUUID(), extent("test:entity_nested", worldAnchor, 2, 2, 2, worldAnchor));
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("entity source capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            helper.assertTrue(view.nested(localAnchor) != null, "the entity source built the child");
            helper.assertValueEqual(
                view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity)).size(),
                0,
                "the entity is not an occupant under a nested-only policy"
            );
            List<OccupantSnapshot> bodies = view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.nested));
            helper.assertValueEqual(bodies.size(), 1, "one entity-hosted nested body");
            helper.assertValueEqual(
                bodies.get(0).descriptor().type(),
                NamespacedId.of("test", "entity_nested"),
                "the entity source type"
            );
        }
        helper.assertTrue(lookup.entityQueries > 0, "the entity source was queried");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void childEntitiesKeepTheirBodyIdentity(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        Entity first = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(1.5, 1.0, 0.5));
        Entity second = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(1.5, 1.0, 0.5));
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent("test:entity_child", worldAnchor, 3, 3, 3, worldAnchor));
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, true)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("child entity capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("the child view is missing");
                return;
            }
            List<OccupantSnapshot> bodies = child.occupants(child.bounds(), OccupantFilter.ofKind(OccupantKind.entity));
            helper.assertValueEqual(bodies.size(), 2, "two same-type entities stay two bodies");
            Set<NamespacedId> identities = new HashSet<>();
            for (OccupantSnapshot body : bodies) {
                helper.assertValueEqual(body.ref().frame(), 1, "the child mints its own bodies");
                helper.assertTrue(body.ref().body() != null, "an entity body has an identity");
                identities.add(Objects.requireNonNull(body.ref().body(), "body"));
            }
            helper.assertValueEqual(identities.size(), 2, "the body identities are distinct");
            helper.assertTrue(
                identities.contains(NamespacedId.of("minecraft", first.getUUID().toString())),
                "the first uuid is the first body"
            );
            helper.assertTrue(
                identities.contains(NamespacedId.of("minecraft", second.getUUID().toString())),
                "the second uuid is the second body"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void childBuildHeightOutsideIsRefusedAndPartlyRefused(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        ServerLevel level = helper.getLevel();
        int buildMin = level.getMinBuildHeight();
        int originY = worldAnchor.getY() + 1;
        if (Math.floorMod(originY, 16) == 0) {
            originY++;
        }
        LatticePoint childOrigin = LatticePoint.of(worldAnchor.getX(), originY, worldAnchor.getZ());
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "height"),
            Cuboid.of(
                worldAnchor.getX(),
                buildMin - 25,
                worldAnchor.getZ(),
                worldAnchor.getX() + 1,
                originY + 2,
                worldAnchor.getZ() + 1
            ),
            childOrigin
        );
        FakeLookup lookup = new FakeLookup();
        lookup.blocks.put(worldAnchor, extent);
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 1,
            worldAnchor.getZ() + 1
        );

        CaptureResult result = new LevelCapture(level, lookup.lookup())
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, false)));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("build-height capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView child = lease.view().nested(localAnchor);
            if (child == null) {
                helper.fail("the height child was cut");
                return;
            }
            int localBoundary = buildMin - originY;
            LatticePoint outside = LatticePoint.of(0, localBoundary - 17, 0);
            LatticePoint boundary = LatticePoint.of(0, localBoundary, 0);
            LatticePoint partlyOutside = LatticePoint.of(0, localBoundary - 1, 0);
            helper.assertTrue(child.bounds().contains(outside), "the outside cell is in the child bounds");
            helper.assertTrue(child.bounds().contains(partlyOutside), "the partial cell is in the child bounds");
            SectionSlot refused = child.slot(child.sectionOf(outside));
            if (!(refused instanceof SectionSlot.Refused)) {
                helper.fail("a wholly outside section is not refused: " + refused);
                return;
            }
            helper.assertValueEqual(refused.status(), ReadStatus.outOfBounds, "the refused section status");
            SectionSlot partial = child.slot(child.sectionOf(partlyOutside));
            if (!(partial instanceof SectionSlot.Partial)) {
                helper.fail("a straddling section is not partial: " + partial);
                return;
            }
            helper.assertValueEqual(child.statusOf(outside), ReadStatus.outOfBounds, "outside is refused");
            helper.assertValueEqual(
                child.statusOf(partlyOutside),
                ReadStatus.outOfBounds,
                "the outside cell is refused"
            );
            helper.assertValueEqual(child.statusOf(boundary), ReadStatus.readable, "the in-range cell is readable");
            helper.assertValueEqual(
                child.statusOf(LatticePoint.of(0, 0, 0)),
                ReadStatus.readable,
                "the child origin is readable"
            );
            helper.assertTrue(child.occupant(outside) == null, "outside is never air");
        }
        helper.succeed();
    }

    private static SnapshotPolicy policy(int maxElements, int maxDepth, boolean entity) {
        Set<OccupantKind> kinds = entity
                ? Set.of(OccupantKind.nested, OccupantKind.entity)
                : Set.of(OccupantKind.nested);
        return new SnapshotPolicy(maxElements, maxDepth, kinds, Set.of(), List.of());
    }

    private static NestedExtent extent(String type, BlockPos min, int sizeX, int sizeY, int sizeZ, BlockPos origin) {
        return new NestedExtent(
            Objects.requireNonNull(NamespacedId.parse(type), "type"),
            Cuboid.of(min.getX(), min.getY(), min.getZ(), min.getX() + sizeX, min.getY() + sizeY, min.getZ() + sizeZ),
            LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())
        );
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void capturePassesTheCapabilitySideToTheBlockAndEntityProviders(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(0, 1, 0);
        helper.setBlock(anchor, Blocks.STONE);
        helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 2.0, 0.5));
        BlockPos worldAnchor = helper.absolutePos(anchor);
        LatticePoint localAnchor = LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        Cuboid bounds = Cuboid.of(
            worldAnchor.getX(),
            worldAnchor.getY(),
            worldAnchor.getZ(),
            worldAnchor.getX() + 1,
            worldAnchor.getY() + 2,
            worldAnchor.getZ() + 1
        );
        AtomicReference<@Nullable Direction> blockSide = new AtomicReference<>();
        AtomicReference<@Nullable Direction> entitySide = new AtomicReference<>();
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
                        blockSide.set(context);
                        return null;
                    }
                }).add(new CapabilityNestedExtentProvider<Entity, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<Entity> holder() {
                        return NestedExtentHolders.entity;
                    }

                    @Override
                    public @Nullable NestedExtent get(Entity actual, @Nullable Direction context) {
                        entitySide.set(context);
                        return null;
                    }
                }).build();
        TraitCaptureContext capture = new TraitCaptureContext(Direction.WEST, TraitContext.empty());
        SnapshotPolicy policy = new SnapshotPolicy(
            maxElements,
            1,
            Set.of(OccupantKind.nested, OccupantKind.entity),
            Set.of(),
            List.of()
        );

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup, TraitLookup.builtin(), capture)
                .capture(new CaptureRequest(bounds, policy));

        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the capability-side capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertValueEqual(Direction.WEST, blockSide.get(), "the block provider sees the capture side");
            helper.assertValueEqual(Direction.WEST, entitySide.get(), "the entity provider sees the capture side");
            helper.assertTrue(lease.view().nested(localAnchor) == null, "a null declaration yields no child");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theStandardCapabilityAccessesAreDirectionTypedAndNullSafe(GameTestHelper helper) {
        BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(relative);
        ServerLevel level = helper.getLevel();
        BlockNestedExtentContext context = new BlockNestedExtentContext(
            level,
            chestPos,
            level.getBlockState(chestPos),
            level.getBlockEntity(chestPos)
        );
        Entity chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(2.5, 1.0, 0.5));
        EntityCapability<NestedExtent, @Nullable Void> voidCapability = EntityCapability
                .createVoid(ResourceLocation.fromNamespaceAndPath(Constants.modId, "void_nested"), NestedExtent.class);

        helper.assertValueEqual(Direction.class, NestedExtents.BLOCK.contextClass(), "the block context is a side");
        helper.assertValueEqual(Direction.class, NestedExtents.ENTITY.contextClass(), "the entity context is a side");
        helper.assertTrue(
            CapabilityNestedExtentAccesses.block().get(NestedExtents.BLOCK, context, Direction.WEST) == null,
            "no block provider is registered, so the five-argument query answers null"
        );
        helper.assertTrue(
            CapabilityNestedExtentAccesses.entity().get(NestedExtents.ENTITY, chicken, Direction.WEST) == null,
            "no entity provider is registered, so the sided query answers null"
        );
        helper.assertTrue(
            CapabilityNestedExtentAccesses.entityVoid().get(voidCapability, chicken, null) == null,
            "a Void-context capability stays null-safe"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockEntityAttachmentProvidesTheNestedExtent(GameTestHelper helper) {
        BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(relative);
        LatticePoint anchor = LatticePoint.of(chestPos.getX(), chestPos.getY(), chestPos.getZ());
        NestedExtent extent = childExtent(anchor, "attachment_block");
        AttachmentType<String> attachment = attachmentType();
        FakeAttachmentHolder holder = new FakeAttachmentHolder();
        holder.setData(attachment, "child");
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(
                    AttachmentNestedExtentProviders.<BlockNestedExtentContext, String>readOnly(
                        NestedExtentHolders.block,
                        attachment,
                        context -> holder,
                        data -> extent
                    )
                ).build();

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup)
                .capture(new CaptureRequest(cellAround(chestPos), policy(maxElements, 1, false)));

        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the attachment capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            WorldView child = lease.view().nested(anchor);
            if (child == null) {
                helper.fail("the attachment extent did not open a child frame");
                return;
            }
            helper.assertValueEqual(extent.origin(), child.origin(), "the child uses the attachment origin");
            helper.assertValueEqual(1, child.depth(), "the child depth");
            helper.assertTrue(captured.report().isEmpty(), "an attachment declaration is not a cut");
            helper.assertValueEqual(0, holder.getDataCalls, "the capture read only existing data");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anAbsentAttachmentIsNoChildAndNeverDirtiesTheHolder(GameTestHelper helper) {
        BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(relative);
        LatticePoint anchor = LatticePoint.of(chestPos.getX(), chestPos.getY(), chestPos.getZ());
        AttachmentType<String> attachment = attachmentType();
        FakeAttachmentHolder holder = new FakeAttachmentHolder();
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(
                    AttachmentNestedExtentProviders.<BlockNestedExtentContext, String>readOnly(
                        NestedExtentHolders.block,
                        attachment,
                        context -> holder,
                        data -> childExtent(anchor, "absent_attachment")
                    )
                ).build();

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup)
                .capture(new CaptureRequest(cellAround(chestPos), policy(maxElements, 1, false)));

        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the absent-attachment capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.view().nested(anchor) == null, "absent attachment data yields no child");
        }
        helper.assertValueEqual(0, holder.getDataCalls, "the capture never calls getData");
        helper.assertTrue(!holder.hasAttachments(), "the capture read never creates a default attachment");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aBlockWithoutABlockEntityNeverReachesAnAttachmentHolder(GameTestHelper helper) {
        BlockPos chestRelative = new BlockPos(0, 1, 0);
        helper.setBlock(chestRelative, Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(chestRelative);
        ServerLevel level = helper.getLevel();
        BlockNestedExtentContext withBlockEntity = new BlockNestedExtentContext(
            level,
            chestPos,
            level.getBlockState(chestPos),
            level.getBlockEntity(chestPos)
        );
        BlockNestedExtentContext withoutBlockEntity = new BlockNestedExtentContext(
            level,
            chestPos,
            level.getBlockState(chestPos),
            null
        );
        AttachmentType<String> attachment = attachmentType();
        AttachmentNestedExtentProvider<BlockNestedExtentContext, String> provider = AttachmentNestedExtentProviders
                .readOnly(
                    NestedExtentHolders.block,
                    attachment,
                    AttachmentNestedExtentProviders.blockEntity(),
                    data -> childExtent(
                        LatticePoint.of(chestPos.getX(), chestPos.getY(), chestPos.getZ()),
                        "no_block_entity"
                    )
                );

        helper.assertTrue(
            AttachmentNestedExtentProviders.blockEntity().holder(withBlockEntity) == level.getBlockEntity(chestPos),
            "the block-entity adapter is the already-read block entity"
        );
        helper.assertTrue(
            AttachmentNestedExtentProviders.blockEntity().holder(withoutBlockEntity) == null,
            "a null block entity has no attachment holder"
        );
        helper.assertTrue(!provider.hasData(withoutBlockEntity), "a null block entity has no attachment data");
        helper.assertTrue(
            provider.getExistingDataOrNull(withoutBlockEntity) == null,
            "a null block entity is absent, never an error"
        );
        helper.assertTrue(provider.removeData(withoutBlockEntity) == null, "removing from a null holder answers null");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityAttachmentProvidesTheNestedExtent(GameTestHelper helper) {
        Entity chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 2.0, 0.5));
        AttachmentType<String> attachment = attachmentType();
        FakeAttachmentHolder holder = new FakeAttachmentHolder();
        holder.setData(attachment, "child");
        BlockPos entityPos = chicken.blockPosition();
        LatticePoint anchor = LatticePoint.of(entityPos.getX(), entityPos.getY(), entityPos.getZ());
        NestedExtent extent = childExtent(anchor, "attachment_entity");
        NestedExtentLookup lookup = NestedExtentLookup.builder().add(
            AttachmentNestedExtentProviders
                    .<Entity, String>readOnly(NestedExtentHolders.entity, attachment, actual -> holder, data -> extent)
        ).build();
        BlockPos base = helper.absolutePos(new BlockPos(0, 1, 0));
        Cuboid bounds = Cuboid
                .of(base.getX(), base.getY(), base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1);

        CaptureResult result = new LevelCapture(helper.getLevel(), lookup)
                .capture(new CaptureRequest(bounds, policy(maxElements, 1, true)));

        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the entity-attachment capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(
                lease.view().nested(anchor) != null,
                "the entity attachment opens the child at the entity anchor"
            );
            helper.assertValueEqual(
                chicken,
                AttachmentNestedExtentProviders.entity().holder(chicken),
                "an entity is its own attachment holder"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void readOnlyRejectsWritesAndReadWriteMapsTheHolder(GameTestHelper helper) {
        AttachmentType<String> attachment = attachmentType();
        FakeAttachmentHolder holder = new FakeAttachmentHolder();
        LatticePoint anchor = LatticePoint.of(0, 0, 0);
        NestedExtent extent = childExtent(anchor, "attachment_write");
        NestedExtentHolder<String> holderId = NestedExtentHolder.of(NamespacedId.of("test", "attachment_write"));
        AttachmentNestedExtentProvider<String, String> readOnly = AttachmentNestedExtentProviders
                .readOnly(holderId, attachment, actual -> holder, data -> extent);
        AttachmentNestedExtentProvider<String, String> readWrite = AttachmentNestedExtentProviders.readWrite(
            holderId,
            attachment,
            actual -> holder,
            data -> extent,
            value -> "raw:" + value.type().text()
        );

        try {
            readOnly.setData("subject", extent);
            helper.fail("a read-only attachment provider accepted a write");
            return;
        } catch (UnsupportedOperationException expected) {
            // expected: read-only providers expose no writer
        }
        helper.assertTrue(readWrite.setData("subject", extent) == null, "the first write has no previous value");
        helper.assertValueEqual(
            "raw:test:attachment_write",
            holder.getExistingDataOrNull(attachment),
            "the writer stored the raw value"
        );
        helper.assertValueEqual(extent, readWrite.getExistingDataOrNull("subject"), "the reader maps the stored value");
        helper.assertValueEqual(extent, readWrite.removeData("subject"), "removal answers the previous extent");
        helper.assertTrue(holder.getExistingDataOrNull(attachment) == null, "removal clears the attachment");
        helper.succeed();
    }

    private static Cuboid cellAround(BlockPos pos) {
        return Cuboid.of(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static NestedExtent childExtent(LatticePoint anchor, String path) {
        LatticePoint origin = LatticePoint.of(anchor.x() + 2, anchor.y(), anchor.z() + 2);
        return new NestedExtent(
            NamespacedId.of("test", path),
            Cuboid.of(origin.x(), origin.y(), origin.z(), origin.x() + 1, origin.y() + 1, origin.z() + 1),
            origin
        );
    }

    private static AttachmentType<String> attachmentType() {
        return AttachmentType.builder(() -> "").build();
    }

    private static final class FakeAttachmentHolder implements IAttachmentHolder {

        private final Map<AttachmentType<?>, Object> data = new HashMap<>();
        private int getDataCalls;

        @Override
        public boolean hasAttachments() {
            return !data.isEmpty();
        }

        @Override
        public boolean hasData(AttachmentType<?> type) {
            return data.containsKey(type);
        }

        @Override
        public <T> T getData(AttachmentType<T> type) {
            getDataCalls++;
            T existing = getExistingDataOrNull(type);
            if (existing != null) {
                return existing;
            }
            throw new AssertionError("the capture must not create a default attachment");
        }

        @Override
        public <T> @Nullable T getExistingDataOrNull(AttachmentType<T> type) {
            @SuppressWarnings("unchecked")
            T value = (T) data.get(type);
            return value;
        }

        @Override
        public <T> @Nullable T setData(AttachmentType<T> type, T value) {
            @SuppressWarnings("unchecked")
            T previous = (T) data.put(type, value);
            return previous;
        }

        @Override
        public <T> @Nullable T removeData(AttachmentType<T> type) {
            @SuppressWarnings("unchecked")
            T previous = (T) data.remove(type);
            return previous;
        }
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theHostConfiguredLookupReachesTheServerCapture(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.CHEST);
        BlockPos chestPos = helper.absolutePos(relative);
        LatticePoint anchor = LatticePoint.of(chestPos.getX(), chestPos.getY(), chestPos.getZ());
        AttachmentType<String> attachment = attachmentType();
        FakeAttachmentHolder holder = new FakeAttachmentHolder();
        holder.setData(attachment, "child");
        NestedExtent extent = childExtent(anchor, "configured_lookup");
        NestedExtentLookup custom = NestedExtentLookup.builder()
                .add(
                    AttachmentNestedExtentProviders.<BlockNestedExtentContext, String>readOnly(
                        NestedExtentHolders.block,
                        attachment,
                        context -> holder,
                        data -> extent
                    )
                ).build();
        SnapshotPolicy policy = policy(maxElements, 1, false);
        CaptureRequest request = new CaptureRequest(cellAround(chestPos), policy);
        try {
            CaptureResult before = state.capture(helper.getLevel()).capture(request);
            if (!(before instanceof CaptureResult.Captured beforeCaptured)) {
                helper.fail("the default-lookup capture failed: " + before);
                return;
            }
            try (SnapshotLease lease = beforeCaptured.lease()) {
                helper.assertTrue(
                    lease.view().nested(anchor) == null,
                    "the default lookup has no attachment provider for the block holder"
                );
            }

            state.installNestedExtentLookup(custom);

            CaptureResult installed = state.capture(helper.getLevel()).capture(request);
            if (!(installed instanceof CaptureResult.Captured installedCaptured)) {
                helper.fail("the installed-lookup capture failed: " + installed);
                return;
            }
            try (SnapshotLease lease = installedCaptured.lease()) {
                WorldView child = lease.view().nested(anchor);
                if (child == null) {
                    helper.fail("the host-installed lookup did not reach the capture");
                    return;
                }
                helper.assertValueEqual(extent.origin(), child.origin(), "the attachment origin");
                helper.assertValueEqual(0, holder.getDataCalls, "the capture still only reads existing data");
            }
        } finally {
            state.installNestedExtentLookup(NestedExtentLookup.capabilities());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void installingAHostLookupInvalidatesAnOpenCaptureLease(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(0, 1, 0);
        helper.setBlock(relative, Blocks.STONE);
        CaptureRequest request = new CaptureRequest(
            cellAround(helper.absolutePos(relative)),
            policy(maxElements, 0, false)
        );
        CaptureResult opened = state.capture(level).capture(request);
        if (!(opened instanceof CaptureResult.Captured captured)) {
            helper.fail("the capture that should be invalidated failed: " + opened);
            return;
        }
        SnapshotLease stale = captured.lease();
        helper.assertTrue(stale.isCurrent(), "a fresh server capture lease is current");

        try {
            state.installNestedExtentLookup(NestedExtentLookup.builder().build());
            helper.assertTrue(!stale.isCurrent(), "installing a host lookup invalidates the open lease");

            CaptureResult reopened = state.capture(level).capture(request);
            if (!(reopened instanceof CaptureResult.Captured fresh)) {
                helper.fail("the capture after installing the lookup failed: " + reopened);
                return;
            }
            try (SnapshotLease lease = fresh.lease()) {
                helper.assertTrue(lease.isCurrent(), "a capture opened after the install is current");
            }
        } finally {
            state.installNestedExtentLookup(NestedExtentLookup.capabilities());
        }
        helper.assertTrue(!stale.isCurrent(), "the old lease stays invalid after restoring the default");
        stale.close();
        helper.succeed();
    }

    private static final class FakeLookup {

        private final Map<BlockPos, NestedExtent> blocks = new HashMap<>();
        private final Map<UUID, NestedExtent> entities = new HashMap<>();
        private final Set<BlockPos> throwingBlocks = new HashSet<>();
        private final Set<UUID> throwingEntities = new HashSet<>();
        private final NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
                        blockQueries++;
                        if (throwingBlocks.contains(actual.pos())) {
                            throw new IllegalStateException("fake block provider");
                        }
                        return blocks.get(actual.pos());
                    }
                }).add(new CapabilityNestedExtentProvider<Entity, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<Entity> holder() {
                        return NestedExtentHolders.entity;
                    }

                    @Override
                    public @Nullable NestedExtent get(Entity actual, @Nullable Direction context) {
                        entityQueries++;
                        if (throwingEntities.contains(actual.getUUID())) {
                            throw new IllegalStateException("fake entity provider");
                        }
                        return entities.get(actual.getUUID());
                    }
                }).build();
        private int blockQueries;
        private int entityQueries;

        private NestedExtentLookup lookup() {
            return lookup;
        }
    }
}
