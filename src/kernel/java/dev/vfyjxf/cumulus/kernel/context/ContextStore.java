package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The single publication point of one instance; every write is a draft published against a base revision. */
public final class ContextStore {

    private final InstanceId instance;
    private volatile StructureContext context;

    public ContextStore(InstanceId instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.context = StructureContext.empty(instance);
    }

    public InstanceId instance() {
        return instance;
    }

    public long revision() {
        return context.revision();
    }

    public StructureContext context(InstanceId instance) {
        Objects.requireNonNull(instance, "instance");
        if (!this.instance.equals(instance)) {
            throw new IllegalArgumentException("instance");
        }
        return context;
    }

    public ContextDraft draft(ContextTier tier, long baseRevision) {
        Objects.requireNonNull(tier, "tier");
        return new ContextDraft(this, tier, baseRevision);
    }

    public synchronized Outcome<Void> publish(ContextDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (!draft.ownedBy(this)) {
            throw new IllegalArgumentException("draft");
        }
        if (draft.consumed()) {
            throw new IllegalStateException("draft already published");
        }
        StructureContext current = context;
        if (draft.baseRevision() != current.revision()) {
            draft.consume();
            return new Outcome.Missing<>(ContextFindings.bundle(
                    ContextFindings.staleBase(instance, draft.baseRevision(), current.revision())));
        }
        FindingBundle conflicts = draft.conflictFindings();
        if (!conflicts.isEmpty()) {
            draft.consume();
            return new Outcome.Missing<>(conflicts);
        }
        long nextRevision = current.revision() + 1;
        ContextFrame frame = draft.buildFrame(nextRevision);
        List<ContextFrame> frames = new ArrayList<>(current.frames());
        frames.add(frame);
        context = new StructureContext(instance, nextRevision, frames);
        draft.consume();
        return success();
    }

    private static Outcome<Void> success() {
        @SuppressWarnings("NullAway")
        Outcome<Void> published = new Outcome.Found<>(null, List.of(), FindingBundle.empty());
        return published;
    }
}
