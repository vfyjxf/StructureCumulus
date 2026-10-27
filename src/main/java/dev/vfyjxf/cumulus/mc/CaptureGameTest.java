package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** M2a GameTests: capture, the first check, and an entity over a block. */
@NullMarked
@GameTestHolder(Constants.modId)
public final class CaptureGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void capturesTheTemplateCell(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        CaptureResult result;
        try {
            LevelCapture capture = new LevelCapture(helper.getLevel());
            result = capture.capture(
                new CaptureRequest(
                    Cuboid.of(
                        origin.getX(),
                        origin.getY(),
                        origin.getZ(),
                        origin.getX() + 1,
                        origin.getY() + 1,
                        origin.getZ() + 1
                    ),
                    SnapshotPolicy.minimal()
                )
            );
        } catch (Throwable failure) {
            helper.fail("capture threw: " + failure + " / " + failure.getCause());
            return;
        }
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("capture did not succeed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            OccupantSnapshot cell = lease.view().occupant(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ()));
            if (cell == null) {
                helper.fail("captured cell is unreadable");
                return;
            }
            helper.assertValueEqual(
                ReadStatus.readable,
                lease.view().statusOf(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
                "cell status"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checksTheBuiltinDocument(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        Document document = state.kernel().document(BuiltinDocuments.airCellId());
        if (document == null) {
            helper.fail("built-in document is missing");
            return;
        }
        CompileResult compiled = state.kernel().compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            helper.fail(
                "built-in document does not compile: "
                        + FindingRenderer.human(compiled.findings(), RenderContext.plain())
            );
            return;
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Cuboid bounds = blueprint.bounds().translate(origin.getX(), origin.getY(), origin.getZ());
        if (bounds == null) {
            helper.fail("the blueprint bounds leave the world");
            return;
        }
        CaptureResult capture = state.capture(helper.getLevel())
                .capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(capture instanceof CaptureResult.Captured captured)) {
            helper.fail("capture did not succeed: " + capture);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            MatchResult result = state.kernel().check(
                MatchRequest.of(
                    blueprint,
                    Siting.of(point, Orientation.identity),
                    lease,
                    state.instance(helper.getLevel()),
                    state.owner(),
                    Budget.unbounded(),
                    null
                )
            );
            if (result instanceof MatchResult.Failed failed) {
                helper.fail("check failed: " + FindingRenderer.human(failed.findings(), RenderContext.plain()));
                return;
            }
            if (!(result instanceof MatchResult.Admitted)) {
                helper.fail("check did not admit: " + result);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void capturesEntityOverBlock(GameTestHelper helper) {
        BlockPos templateCell = new BlockPos(0, 1, 0);
        BlockPos aboveTemplateCell = new BlockPos(0, 2, 0);
        helper.setBlock(templateCell, Blocks.STONE);
        helper.setBlock(aboveTemplateCell, Blocks.AIR);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(0.5, 1.5, 0.5));
        BlockPos origin = helper.absolutePos(templateCell);
        Cuboid bounds = Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 1,
            origin.getY() + 2,
            origin.getZ() + 1
        );
        LevelCapture capture = new LevelCapture(helper.getLevel());

        CaptureResult plain = capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(plain instanceof CaptureResult.Captured captured)) {
            helper.fail("plain capture did not succeed: " + plain);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertValueEqual(
                0,
                lease.view().occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity)).size(),
                "entities without a request"
            );
        }

        NamespacedId zombie = NamespacedId.of("minecraft", "zombie");
        NamespacedId stone = NamespacedId.of("minecraft", "stone");
        EntityWindow window = EntityWindow.of(bounds, zombie);
        if (window == null) {
            helper.fail("the entity window is empty");
            return;
        }
        SnapshotPolicy policy = new SnapshotPolicy(
            1_000_000,
            0,
            Set.of(OccupantKind.entity),
            Set.of(),
            List.of(window)
        );
        CaptureResult result = capture.capture(new CaptureRequest(bounds, policy));
        if (!(result instanceof CaptureResult.Captured withEntity)) {
            helper.fail("entity capture did not succeed: " + result);
            return;
        }
        try (SnapshotLease lease = withEntity.lease()) {
            WorldView view = lease.view();
            LatticePoint foot = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
            LatticePoint head = LatticePoint.of(origin.getX(), origin.getY() + 1, origin.getZ());
            LatticePoint tail = LatticePoint.of(origin.getX(), origin.getY() + 2, origin.getZ());
            OccupantSnapshot block = view.occupant(foot);
            if (block == null) {
                helper.fail("the foot cell is unreadable");
                return;
            }
            helper.assertValueEqual(OccupantKind.block, block.descriptor().kind(), "the one-cell block wins");
            helper.assertValueEqual(stone, block.descriptor().type(), "block type");
            OccupantSnapshot entity = view.occupant(head);
            if (entity == null) {
                helper.fail("the head cell is unreadable");
                return;
            }
            helper.assertValueEqual(OccupantKind.entity, entity.descriptor().kind(), "the entity wins over air");
            helper.assertValueEqual(zombie, entity.descriptor().type(), "entity type");
            helper.assertTrue(entity.footprint().contains(head), "footprint covers the body");
            helper.assertTrue(entity.footprint().contains(tail), "footprint is not clipped to the capture");
            if (view.occupant(tail) != null) {
                helper.fail("a cell outside the capture became readable");
                return;
            }
            SectionSlot slot = view.slot(view.sectionOf(foot));
            if (!(slot instanceof SectionSlot.Present present)) {
                helper.fail("the block section is not present");
                return;
            }
            OccupantSnapshot sectionBlock = present.section().occupantAt(foot);
            if (sectionBlock == null) {
                helper.fail("the underlying block was lost");
                return;
            }
            helper.assertValueEqual(block.ref(), sectionBlock.ref(), "the section keeps the block");
            helper.assertValueEqual(
                block.ref(),
                Objects.requireNonNull(view.occupant(block.ref()), "block ref").ref(),
                "block ref"
            );
            helper.assertValueEqual(
                entity.ref(),
                Objects.requireNonNull(view.occupant(entity.ref()), "entity ref").ref(),
                "entity ref"
            );

            List<OccupantSnapshot> entities = view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity));
            helper.assertValueEqual(1, entities.size(), "one entity despite two indexed cells");
            helper.assertValueEqual(entity.ref(), entities.get(0).ref(), "entity box query");
            List<OccupantSnapshot> typed = view.occupants(bounds, OccupantFilter.ofType(zombie));
            helper.assertValueEqual(1, typed.size(), "typed entity query");
            List<OccupantSnapshot> blocks = view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.block));
            long stones = blocks.stream().filter(occupant -> occupant.descriptor().type().equals(stone)).count();
            helper.assertValueEqual(1L, stones, "the stone appears once");
            helper.assertValueEqual(2, view.occupants(bounds, OccupantFilter.any()).size(), "block plus entity");
        }
        helper.succeed();
    }
}
