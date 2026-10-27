package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedClaimsTest {

    private static final World world = World.of(NamespacedId.of("test", "claims"));
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
    void settleReplacesTheWholeKeyAndBumpsTheRevision() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow first = row(ownerA, Sharing.exclusive, 0, "bay");
        ClaimVerdict firstVerdict = settle(claims, ownerA, siting, 0, first);

        assertInstanceOf(ClaimVerdict.Settled.class, firstVerdict);
        assertEquals(1, claims.revision());
        assertEquals(List.of(first), ((ClaimVerdict.Settled) firstVerdict).rows());

        ClaimRow second = row(ownerA, Sharing.exclusive, 1, "bay");
        ClaimVerdict secondVerdict = settle(claims, ownerA, siting, 1, second);

        assertInstanceOf(ClaimVerdict.Settled.class, secondVerdict);
        assertEquals(2, claims.revision());
        assertEquals(List.of(second), claims.read(everything).rows());
    }

    @Test
    void anEmptySettlementStillClearsTheKeyAndBumps() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow row = row(ownerA, Sharing.exclusive, 0, "bay");
        settle(claims, ownerA, siting, 0, row);

        ClaimVerdict removed = settle(claims, ownerA, siting, 1);

        assertInstanceOf(ClaimVerdict.Settled.class, removed);
        assertEquals(2, claims.revision());
        assertTrue(claims.read(everything).rows().isEmpty());
    }

    @Test
    void anIdenticalReplacementStillBumpsTheRevision() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow row = row(ownerA, Sharing.exclusive, 17, "bay");
        settle(claims, ownerA, siting, 0, row);

        ClaimVerdict again = settle(claims, ownerA, siting, 1, row);

        assertInstanceOf(ClaimVerdict.Settled.class, again);
        assertEquals(2, claims.revision());
        assertEquals(List.of(row), claims.read(everything).rows());
    }

    @Test
    void refusedSettlementsDoNotMarkTheLedgerDirty() {
        SavedClaims claims = new SavedClaims(world);
        settle(claims, ownerA, siting, 0, row(ownerA, Sharing.exclusive, 18, "bay"));
        SavedClaims loaded = SavedClaims.load(world, claims.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertFalse(loaded.isDirty());

        ClaimVerdict moved = settle(loaded, ownerB, siting, 0, row(ownerB, Sharing.exclusive, 19, "bay"));
        assertInstanceOf(ClaimVerdict.RevisionMoved.class, moved);
        assertFalse(loaded.isDirty());

        ClaimVerdict conflict = settle(loaded, ownerB, siting, 1, row(ownerB, Sharing.exclusive, 18, "bay"));
        assertInstanceOf(ClaimVerdict.Conflict.class, conflict);
        assertFalse(loaded.isDirty());
        assertEquals(1, loaded.revision());
    }

    @Test
    void settleRefusesAConflictAndLeavesBytesUntouched() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow incumbent = row(ownerA, Sharing.exclusive, 0, "bay");
        settle(claims, ownerA, siting, 0, incumbent);

        ClaimVerdict verdict = settle(claims, ownerB, siting, 1, row(ownerB, Sharing.exclusive, 0, "bay"));

        ClaimVerdict.Conflict conflict = assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(List.of(incumbent), conflict.incumbents());
        assertFalse(conflict.findings().isEmpty());
        assertEquals(1, claims.revision());
        assertEquals(List.of(incumbent), claims.read(everything).rows());
    }

    @Test
    void sharedClaimsCoexistButAnExclusiveOneConflicts() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow sharedA = row(ownerA, Sharing.shared, 3, "a");
        ClaimRow sharedB = row(ownerB, Sharing.shared, 3, "b");
        settle(claims, ownerA, siting, 0, sharedA);
        assertInstanceOf(ClaimVerdict.Settled.class, settle(claims, ownerB, siting, 1, sharedB));
        assertEquals(2, claims.revision());

        ClaimVerdict verdict = settle(claims, ownerB, siting, 2, row(ownerB, Sharing.exclusive, 3, "b"));
        assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(2, claims.revision());
    }

    @Test
    void aSharedClaimStillConflictsWithAnExclusiveIncumbent() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow incumbent = row(ownerA, Sharing.exclusive, 3, "a");
        settle(claims, ownerA, siting, 0, incumbent);

        ClaimVerdict verdict = settle(claims, ownerB, siting, 1, row(ownerB, Sharing.shared, 3, "b"));

        ClaimVerdict.Conflict conflict = assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(List.of(incumbent), conflict.incumbents());
        assertEquals(1, claims.revision());
    }

    @Test
    void aDifferentCellOrTheSameKeyDoesNotConflict() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow first = row(ownerA, Sharing.exclusive, 4, "bay");
        settle(claims, ownerA, siting, 0, first);

        ClaimVerdict sameKey = settle(claims, ownerA, siting, 1, row(ownerA, Sharing.exclusive, 4, "other"));
        assertInstanceOf(ClaimVerdict.Settled.class, sameKey);

        ClaimVerdict otherCell = settle(claims, ownerB, siting, 2, row(ownerB, Sharing.exclusive, 5, "bay"));
        assertInstanceOf(ClaimVerdict.Settled.class, otherCell);
        assertEquals(3, claims.revision());
    }

    @Test
    void aMovedRevisionWritesNothingAndOneRetrySucceeds() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow first = row(ownerA, Sharing.exclusive, 6, "bay");
        settle(claims, ownerA, siting, 0, first);

        ClaimVerdict moved = settle(claims, ownerB, siting, 0, row(ownerB, Sharing.exclusive, 6, "bay"));
        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), moved);
        assertEquals(1, claims.revision());
        assertEquals(List.of(first), claims.read(everything).rows());

        ClaimVerdict retried = settle(claims, ownerB, siting, 1, row(ownerB, Sharing.exclusive, 7, "bay"));
        assertInstanceOf(ClaimVerdict.Settled.class, retried);
        assertEquals(2, claims.revision());
    }

    @Test
    void releaseBumpsOnlyWhenItRemovesRows() {
        SavedClaims claims = new SavedClaims(world);
        settle(claims, ownerA, siting, 0, row(ownerA, Sharing.exclusive, 8, "a"), row(ownerA, Sharing.shared, 9, "b"));
        ClaimRow other = row(ownerB, Sharing.shared, 10, "b");
        settle(claims, ownerB, siting, 1, other);

        ClaimVerdict absent = claims.release(OwnerId.of(NamespacedId.of("test", "absent")), siting);
        assertEquals(new ClaimVerdict.Settled(2, List.of()), absent);
        assertEquals(2, claims.revision());

        ClaimVerdict released = claims.release(ownerA, siting);
        assertEquals(new ClaimVerdict.Settled(3, List.of()), released);
        assertEquals(3, claims.revision());
        assertEquals(List.of(other), claims.read(everything).rows());

        assertEquals(new ClaimVerdict.Settled(3, List.of()), claims.release(ownerA, siting));
        assertEquals(3, claims.revision());
    }

    @Test
    void readReturnsACopyAndCanonicalOrder() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow a = row(ownerA, Sharing.shared, 11, "b");
        ClaimRow b = row(ownerB, Sharing.shared, 11, "a");
        settle(claims, ownerA, siting, 0, a);
        settle(claims, ownerB, siting, 1, b);

        ClaimView view = claims.read(everything);
        List<ClaimRow> ordered = List.of(a, b);
        assertEquals(ordered, view.rows());
        assertEquals(ordered, claims.read(everything).rows());
        assertNotEquals(view.rows(), List.of(b, a));
        assertThrows(RuntimeException.class, () -> view.rows().add(a));
    }

    @Test
    void nbtRoundTripKeepsRowsRevisionAndSiting() {
        SavedClaims claims = new SavedClaims(world);
        Siting rotated = new Siting(
            LatticePoint.of(5, -2, 7),
            new Placement(new Orientation(17), LatticePoint.of(2, -3, 4))
        );
        ClaimRow first = row(ownerA, Sharing.exclusive, 12, "a", rotated);
        ClaimRow second = row(ownerB, Sharing.shared, -3, "b", siting);
        settle(claims, ownerA, rotated, 0, first);
        settle(claims, ownerB, siting, 1, second);
        assertEquals(2, claims.revision());

        CompoundTag tag = claims.save(new CompoundTag(), RegistryAccess.EMPTY);
        SavedClaims loaded = SavedClaims.load(world, tag);

        assertEquals(world, loaded.world());
        assertEquals(claims.revision(), loaded.revision());
        assertEquals(claims.read(everything).rows(), loaded.read(everything).rows());
        ClaimRow loadedFirst = loaded.read(everything).rows().stream().filter(row -> row.owner().equals(ownerA))
                .findFirst().orElseThrow();
        assertEquals(rotated, loadedFirst.siting());
        assertEquals(0, claims.read(Cuboid.of(20, 20, 20, 21, 21, 21)).rows().size());
    }

    @Test
    void theFactoryDeserializesAndKeepsTheReloadIdentity() {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow first = row(ownerA, Sharing.exclusive, 13, "a");
        ClaimRow second = row(ownerB, Sharing.exclusive, 14, "b");
        settle(claims, ownerA, siting, 0, first);
        settle(claims, ownerB, siting, 1, second);

        CompoundTag tag = claims.save(new CompoundTag(), RegistryAccess.EMPTY);
        SavedData.Factory<SavedClaims> factory = SavedClaims.factory(world);
        SavedClaims reloaded = factory.deserializer().apply(tag, RegistryAccess.EMPTY);

        assertEquals(2, reloaded.revision());
        assertEquals(world, reloaded.world());
        assertEquals(claims.read(everything).rows(), reloaded.read(everything).rows());

        ClaimVerdict continued = settle(reloaded, ownerA, siting, 2, row(ownerA, Sharing.exclusive, 15, "a"));
        assertInstanceOf(ClaimVerdict.Settled.class, continued);
        assertEquals(3, reloaded.revision());
        assertEquals(2, claims.revision());
    }

    @Test
    void dimensionDataStorageReadsThePersistedFile(@TempDir Path temp) throws IOException {
        SavedClaims claims = new SavedClaims(world);
        ClaimRow row = row(ownerA, Sharing.exclusive, 15, "bay");
        settle(claims, ownerA, siting, 0, row);

        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", claims.save(new CompoundTag(), RegistryAccess.EMPTY));
        NbtUtils.addCurrentDataVersion(wrapper);
        NbtIo.writeCompressed(wrapper, temp.resolve(SavedClaims.dataName + ".dat"));

        DimensionDataStorage storage = new DimensionDataStorage(temp.toFile(), null, RegistryAccess.EMPTY);
        SavedClaims reloaded = storage.computeIfAbsent(SavedClaims.factory(world), SavedClaims.dataName);

        assertEquals(1, reloaded.revision());
        assertEquals(List.of(row), reloaded.read(everything).rows());
    }

    @Test
    void loadRejectsAForeignVersion() {
        SavedClaims claims = new SavedClaims(world);
        CompoundTag tag = claims.save(new CompoundTag(), RegistryAccess.EMPTY);
        tag.putInt("version", SavedClaims.dataVersion + 1);
        assertThrows(IllegalArgumentException.class, () -> SavedClaims.load(world, tag));
    }
}
