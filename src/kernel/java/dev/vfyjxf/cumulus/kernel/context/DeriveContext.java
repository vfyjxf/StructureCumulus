package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.finding.Finding;

/** What a derivation sees: the table this pass is building. */
public interface DeriveContext extends ContextView {

    Budget remaining();

    CancelToken cancel();

    void report(Finding finding);
}
