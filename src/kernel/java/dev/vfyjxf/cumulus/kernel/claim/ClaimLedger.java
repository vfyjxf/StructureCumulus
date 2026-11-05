package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

/** One handle per world; settle and release are the only writers. */
public interface ClaimLedger {

    /** The ledger the adapter and the tests use: in-memory, dropped with its world. */
    static ClaimLedger inMemory(World world) {
        return new InMemoryClaimLedger(world);
    }

    World world();

    long revision();

    ClaimVerdict settle(SettleRequest request);

    ClaimVerdict release(InstanceId instance);

    ClaimView read(Cuboid box);

    /**
     * Ends this ledger's life: its rows are dropped and its revision restarts at zero. A reader that
     * still holds the handle observes the empty ledger, not the state the world had before.
     */
    void retire();
}
