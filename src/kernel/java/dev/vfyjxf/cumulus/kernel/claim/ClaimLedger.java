package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

public interface ClaimLedger {

    /** The ledger-wide revision a CAS is based on; independent of any read box. */
    long revision();

    ClaimVerdict settle(SettleRequest request);

    ClaimView read(Cuboid box);
}
