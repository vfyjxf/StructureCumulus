package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/** The session ledger on a live server level: settle, conflict and whole-instance release. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class ClaimsGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesConflictsAndReleasesByInstance(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        ClaimLedger claims = state.claims(level);
        LatticePoint cell = LatticePoint.of(1_000_000, 0, 1_000_000);
        Siting siting = Siting.of(cell, Orientation.identity);
        Siting otherSiting = Siting.of(LatticePoint.of(1_000_001, 0, 1_000_000), Orientation.identity);
        InstanceId firstInstance = new InstanceId(claims.world(), 2_000_000L);
        InstanceId secondInstance = new InstanceId(claims.world(), 2_000_001L);
        ClaimRow rowA = new ClaimRow(
            LatticeKey.of(cell),
            Sharing.exclusive,
            siting,
            NamespacedId.of(Constants.modId, "bay_a"),
            firstInstance
        );
        ClaimRow rowB = new ClaimRow(
            LatticeKey.of(cell),
            Sharing.exclusive,
            otherSiting,
            NamespacedId.of(Constants.modId, "bay_b"),
            secondInstance
        );

        claims.release(firstInstance);
        claims.release(secondInstance);
        long revision = claims.revision();

        ClaimVerdict first = claims.settle(new SettleRequest(firstInstance, List.of(rowA), revision));
        if (!(first instanceof ClaimVerdict.Settled)) {
            helper.fail("the first settlement did not settle: " + first);
            return;
        }
        if (claims.revision() != revision + 1) {
            helper.fail("the first settlement did not bump once: " + claims.revision());
            return;
        }
        ClaimView held = claims.read(Cuboid.of(cell));
        helper.assertValueEqual(1, held.rows().size(), "the first claim is readable");
        helper.assertValueEqual(rowA, held.rows().get(0), "the first claim row");

        ClaimVerdict conflict = claims.settle(new SettleRequest(secondInstance, List.of(rowB), claims.revision()));
        if (!(conflict instanceof ClaimVerdict.Conflict rejected)) {
            helper.fail("a second exclusive instance did not conflict: " + conflict);
            return;
        }
        helper.assertValueEqual(List.of(rowA), rejected.incumbents(), "the incumbent row");
        long afterConflict = claims.revision();
        helper.assertValueEqual(revision + 1, afterConflict, "a conflict writes nothing");

        ClaimVerdict released = claims.release(firstInstance);
        if (!(released instanceof ClaimVerdict.Settled)) {
            helper.fail("release did not settle: " + released);
            return;
        }
        helper.assertValueEqual(afterConflict + 1, claims.revision(), "release bumps once");
        helper.assertValueEqual(0, claims.read(Cuboid.of(cell)).rows().size(), "release removes the row");

        ClaimVerdict second = claims.settle(new SettleRequest(secondInstance, List.of(rowB), claims.revision()));
        if (!(second instanceof ClaimVerdict.Settled)) {
            helper.fail("the second instance could not settle after the release: " + second);
            return;
        }
        helper.assertValueEqual(List.of(rowB), claims.read(Cuboid.of(cell)).rows(), "the second claim row");

        claims.release(secondInstance);
        helper.assertValueEqual(0, claims.read(Cuboid.of(cell)).rows().size(), "the cleanup releases the row");
        helper.succeed();
    }
}
