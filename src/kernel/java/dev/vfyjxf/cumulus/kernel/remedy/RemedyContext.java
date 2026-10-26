package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** What a provider may see while proposing remedies. */
public record RemedyContext(Blueprint blueprint, Siting siting, @Nullable ClaimView claims,
                            long ledgerRevision, @Nullable MatchOutcome outcome,
                            @Nullable StructureContext context, int maxResults) {

    public RemedyContext {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        if (ledgerRevision < 0) {
            throw new IllegalArgumentException("ledgerRevision >= 0");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults >= 1");
        }
    }
}
