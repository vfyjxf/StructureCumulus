package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/** The adapter dispatches the host lifecycle events into the per-level structure manager. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class StructureLifecycleGameTest {

    private static final OwnerId owner = OwnerId.of(NamespacedId.of(Constants.modId, "lifecycle_owner"));
    private static final NamespacedId blueprint = NamespacedId.of(Constants.modId, "lifecycle_blueprint");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockBreakDispatchesBroken(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        StructureManager manager = state.structures(level);
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        StructureInstance instance = instance(manager, state, level, pos);
        ClaimLedger ledger = state.claims(level);
        LatticePoint point = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        ledger.settle(
            new SettleRequest(
                instance.id(),
                List.of(
                    new ClaimRow(LatticeKey.of(point), Sharing.exclusive, instance.siting(), blueprint, instance.id())
                ),
                ledger.revision()
            )
        );

        CumulusHooks.blockChanged(level, pos, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);

        helper.assertTrue(manager.find(instance.id()) == null, "the broken structure is removed");
        helper.assertTrue(ledger.read(Cuboid.of(point)).rows().isEmpty(), "the broken structure releases its claims");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockPlaceDispatchesInvalidated(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        StructureManager manager = state.structures(level);
        StructureInstance instance = instance(manager, state, level, pos);

        CumulusHooks.blockChanged(level, pos, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);

        helper.assertTrue(manager.find(instance.id()) == null, "a placement invalidates the structure");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100, batch = "unload")
    public void levelUnloadDispatchesUnloaded(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        StructureManager manager = state.structures(level);
        StructureInstance instance = instance(manager, state, level, pos);

        ServerState.unload(new LevelEvent.Unload(level));

        helper.assertTrue(manager.find(instance.id()) == null, "the unloaded world drops the structure");
        state.openLevel(level);
        helper.succeed();
    }

    private static StructureInstance instance(
        StructureManager manager,
        ServerState state,
        ServerLevel level,
        BlockPos pos
    ) {
        LatticePoint point = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        Siting siting = Siting.of(point, Orientation.identity);
        StructureAttempt attempt = manager.begin(state.world(level), new StructureSpec(owner, siting, blueprint));
        return attempt.commit(CuboidSet.of(Cuboid.of(point)));
    }
}
