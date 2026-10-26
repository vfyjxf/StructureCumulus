package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;

import java.util.List;

/** A pure proposer of remedies. */
public interface RemedyProvider {

    NamespacedId id();

    List<Remedy> solve(Finding finding, RemedyContext context);
}
