package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.finding.Finding;

/** What a derivation sees. */
public interface DeriveContext extends ContextView {

    StructureContext base();

    Budget remaining();

    CancelToken cancel();

    void report(Finding finding);
}
