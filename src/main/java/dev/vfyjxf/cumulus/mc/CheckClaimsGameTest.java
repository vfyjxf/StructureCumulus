package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** The check command reads the ledger: an incumbent row decides the check, and a check writes nothing. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CheckClaimsGameTest {

    private static final Cuboid cell = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "check_claims_cell");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "check_claims_bay");
    private static final NamespacedId checkId = NamespacedId.of(Constants.modId, "check_claims_check");
    private static final NamespacedId otherBayId = NamespacedId.of(Constants.modId, "check_claims_incumbent");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checkSeesIncumbentClaimsAndWritesNothing(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Siting siting = Siting.of(point, Orientation.identity);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        ClaimLedger ledger = state.claims(level);
        InstanceId incumbentInstance = new InstanceId(ledger.world(), 1_000_000L);
        try {
            long cleanRevision = ledger.revision();
            MatchResult clean = CumulusCommand.check(source, state, level, cellBlueprint(), point);
            if (!(clean instanceof MatchResult.Admitted)) {
                helper.fail("a clean check did not admit: " + clean + " / " + recording.text());
                return;
            }
            helper.assertValueEqual(cleanRevision, ledger.revision(), "a clean check does not write the ledger");

            ClaimRow incumbentRow = new ClaimRow(
                LatticeKey.of(point),
                Sharing.exclusive,
                siting,
                otherBayId,
                incumbentInstance
            );
            ledger.settle(new SettleRequest(incumbentInstance, List.of(incumbentRow), ledger.revision()));
            long claimedRevision = ledger.revision();
            MatchResult contested = CumulusCommand.check(source, state, level, cellBlueprint(), point);
            if (!(contested instanceof MatchResult.Failed)) {
                helper.fail("a contested check did not fail: " + contested + " / " + recording.text());
                return;
            }
            helper.assertTrue(
                contested.findings().findings().stream()
                        .anyMatch(finding -> finding.code().equals(Codes.claimConflict.code())),
                "the check reports the incumbent claim conflict: "
                        + contested.findings().findings().stream().map(finding -> finding.code().text()).toList()
                        + " / " + recording.text()
            );
            helper.assertValueEqual(claimedRevision, ledger.revision(), "a failed check does not write the ledger");
            helper.assertValueEqual(1, ledger.read(Cuboid.of(point)).rows().size(), "the incumbent row is untouched");
        } finally {
            ledger.release(incumbentInstance);
        }
        helper.succeed();
    }

    private static Blueprint cellBlueprint() {
        Bay bay = Bay.of(bayId, CuboidSet.of(cell), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(cell)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
    }

    private static CommandSourceStack stack(GameTestHelper helper, BlockPos origin, CommandSource source) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(origin),
            Vec2.ZERO,
            helper.getLevel(),
            4,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            null
        );
    }

    private static final class RecordingSource implements CommandSource {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        private String text() {
            return String.join("\n", messages);
        }
    }
}
