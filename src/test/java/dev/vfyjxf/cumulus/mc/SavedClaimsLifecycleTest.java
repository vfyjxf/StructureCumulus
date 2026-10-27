package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedClaimsLifecycleTest {

    private static final World world = World.of(NamespacedId.of("test", "claim_lifecycle"));
    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final OwnerId ownerA = OwnerId.of(NamespacedId.of("test", "a"));
    private static final OwnerId ownerB = OwnerId.of(NamespacedId.of("test", "b"));
    private static final Cuboid everything = Cuboid.of(-64, -64, -64, 64, 64, 64);

    private static ClaimRow row(OwnerId owner, Sharing sharing, int x, String bay) {
        return row(owner, sharing, x, bay, siting);
    }

    private static ClaimRow row(OwnerId owner, Sharing sharing, int x, String bay, Siting at) {
        return new ClaimRow(LatticeKey.of(LatticePoint.of(x, 0, 0)), sharing, owner, at, NamespacedId.of("test", bay));
    }

    private static ClaimVerdict settle(SavedClaims claims, OwnerId owner, Siting at, long expected, ClaimRow... rows) {
        return claims.settle(new SettleRequest(owner, at, List.of(rows), expected));
    }

    @Test
    void aStaleCasWritesNothingAndOneRetrySucceeds() {
        SavedClaims claims = new SavedClaims(world);
        settle(claims, ownerA, siting, 0, row(ownerA, Sharing.exclusive, 0, "bay"));
        SavedClaims clean = SavedClaims.load(world, claims.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertFalse(clean.isDirty(), "a loaded ledger starts clean");
        CompoundTag before = clean.save(new CompoundTag(), RegistryAccess.EMPTY);

        ClaimVerdict moved = settle(clean, ownerB, siting, 0, row(ownerB, Sharing.exclusive, 1, "other"));
        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), moved, "the stale expected revision is reported");
        assertEquals(1, clean.revision(), "the moved CAS does not move the revision");
        assertFalse(clean.isDirty(), "the moved CAS does not mark the ledger dirty");
        assertEquals(
            before,
            clean.save(new CompoundTag(), RegistryAccess.EMPTY),
            "the moved CAS leaves bytes untouched"
        );

        ClaimVerdict retried = settle(clean, ownerB, siting, 1, row(ownerB, Sharing.exclusive, 1, "other"));
        assertInstanceOf(ClaimVerdict.Settled.class, retried, "the retry with the fresh revision succeeds");
        assertEquals(2, clean.revision(), "the retry bumps the revision");
        assertTrue(clean.isDirty(), "the retry writes the ledger");
    }

    @Test
    void aConflictLeavesTheLedgerBytesUntouched() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow incumbent = row(ownerA, Sharing.exclusive, 0, "bay");
        settle(claims, ownerA, siting, 0, incumbent);
        SavedClaims clean = SavedClaims.load(world, claims.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertFalse(clean.isDirty(), "the loaded ledger starts clean");
        CompoundTag before = clean.save(new CompoundTag(), RegistryAccess.EMPTY);

        ClaimVerdict verdict = settle(clean, ownerB, siting, 1, row(ownerB, Sharing.exclusive, 0, "bay"));
        ClaimVerdict.Conflict conflict = assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(List.of(incumbent), conflict.incumbents(), "the conflict names the incumbent");
        assertEquals(1, clean.revision(), "the conflict does not move the revision");
        assertFalse(clean.isDirty(), "the conflict does not mark the ledger dirty");
        assertEquals(
            before,
            clean.save(new CompoundTag(), RegistryAccess.EMPTY),
            "the conflict leaves bytes untouched"
        );
    }

    @Test
    void releaseRemovesTheWholeOwnerSitingKey() {
        SavedClaims claims = new SavedClaims(world);
        Siting elsewhere = Siting.of(LatticePoint.of(10, 0, 0), Orientation.identity);
        ClaimRow first = row(ownerA, Sharing.exclusive, 0, "a");
        ClaimRow second = row(ownerA, Sharing.exclusive, 1, "a");
        ClaimRow otherSiting = row(ownerA, Sharing.exclusive, 5, "a", elsewhere);
        ClaimRow otherOwner = row(ownerB, Sharing.exclusive, 2, "b");
        settle(claims, ownerA, siting, 0, first, second);
        settle(claims, ownerA, elsewhere, 1, otherSiting);
        settle(claims, ownerB, siting, 2, otherOwner);

        ClaimVerdict released = claims.release(ownerA, siting);
        assertEquals(new ClaimVerdict.Settled(4, List.of()), released, "the release bumps the revision");
        assertEquals(4, claims.revision());
        assertEquals(
            List.of(otherOwner, otherSiting),
            claims.read(everything).rows(),
            "only the whole owner/siting key is removed"
        );

        ClaimVerdict absent = claims.release(ownerA, siting);
        assertEquals(new ClaimVerdict.Settled(4, List.of()), absent, "an absent key does not bump");
        assertEquals(4, claims.revision(), "the absent release leaves the revision");
    }

    @Test
    @DisplayName("characterization: duplicate rows collapse and the ledger stores one canonical order")
    void duplicateRowsCollapseAndReadIsCanonical() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow first = row(ownerA, Sharing.shared, 11, "b");
        ClaimRow second = row(ownerA, Sharing.shared, 9, "b");
        ClaimRow third = row(ownerA, Sharing.shared, 10, "b");

        ClaimVerdict verdict = settle(claims, ownerA, siting, 0, first, second, third, first);
        ClaimVerdict.Settled settled = assertInstanceOf(ClaimVerdict.Settled.class, verdict);
        List<ClaimRow> canonical = List.of(second, third, first);
        assertEquals(canonical, settled.rows(), "the settlement returns the de-duplicated canonical rows");
        assertEquals(canonical, claims.read(everything).rows(), "the read is in canonical cell order");
        assertEquals(3, claims.read(everything).rows().size(), "the duplicate row is stored once");
        assertEquals(1, claims.revision(), "one settlement bumps once");
    }
}
