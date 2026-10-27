package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/** M3 GameTest: settle, conflict, release and a saved-data reload through the live ledger. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class ClaimsGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesReleasesAndReloads(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        SavedClaims claims = state.claims(level);
        OwnerId ownerA = OwnerId.of(NamespacedId.of(Constants.modId, "gametest_a"));
        OwnerId ownerB = OwnerId.of(NamespacedId.of(Constants.modId, "gametest_b"));
        LatticePoint cell = LatticePoint.of(1_000_000, 0, 1_000_000);
        Siting siting = Siting.of(cell, Orientation.identity);
        ClaimRow rowA = new ClaimRow(
            LatticeKey.of(cell),
            Sharing.exclusive,
            ownerA,
            siting,
            NamespacedId.of(Constants.modId, "bay_a")
        );
        ClaimRow rowB = new ClaimRow(
            LatticeKey.of(cell),
            Sharing.exclusive,
            ownerB,
            siting,
            NamespacedId.of(Constants.modId, "bay_b")
        );

        claims.release(ownerA, siting);
        claims.release(ownerB, siting);
        long revision = claims.revision();

        ClaimVerdict first = claims.settle(new SettleRequest(ownerA, siting, List.of(rowA), revision));
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

        ClaimVerdict conflict = claims.settle(new SettleRequest(ownerB, siting, List.of(rowB), claims.revision()));
        if (!(conflict instanceof ClaimVerdict.Conflict rejected)) {
            helper.fail("a second exclusive owner did not conflict: " + conflict);
            return;
        }
        helper.assertValueEqual(List.of(rowA), rejected.incumbents(), "the incumbent row");
        helper.assertTrue(!rejected.findings().isEmpty(), "a conflict explains itself");
        long afterConflict = claims.revision();
        helper.assertValueEqual(revision + 1, afterConflict, "a conflict writes nothing");

        ClaimVerdict released = claims.release(ownerA, siting);
        if (!(released instanceof ClaimVerdict.Settled)) {
            helper.fail("release did not settle: " + released);
            return;
        }
        helper.assertValueEqual(afterConflict + 1, claims.revision(), "release bumps once");
        helper.assertValueEqual(0, claims.read(Cuboid.of(cell)).rows().size(), "release removes the row");

        ClaimVerdict second = claims.settle(new SettleRequest(ownerB, siting, List.of(rowB), claims.revision()));
        if (!(second instanceof ClaimVerdict.Settled)) {
            helper.fail("the second owner could not settle after the release: " + second);
            return;
        }
        long finalRevision = claims.revision();
        helper.assertValueEqual(List.of(rowB), claims.read(Cuboid.of(cell)).rows(), "the second claim row");

        CompoundTag tag = claims.save(new CompoundTag(), level.registryAccess());
        SavedClaims reloaded = SavedClaims.load(claims.world(), tag);
        helper.assertValueEqual(finalRevision, reloaded.revision(), "the reloaded revision");
        helper.assertValueEqual(
            claims.read(Cuboid.of(cell)).rows(),
            reloaded.read(Cuboid.of(cell)).rows(),
            "the reloaded rows"
        );

        claims.release(ownerB, siting);
        helper.succeed();
    }
}
