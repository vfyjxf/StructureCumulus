package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Siting;

/** One handle per world; settle and release are the only writers. */
public interface ClaimLedger {

    World world();

    long revision();

    ClaimVerdict settle(SettleRequest request);

    ClaimVerdict release(OwnerId owner, Siting siting);

    ClaimView read(Cuboid box);
}
