package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The only persisted form of context: the settled frames, the augment totals and the revision they were folded at. */
public final class RuntimeState {

    private final StructureContext context;
    private final Map<AugmentKey, Double> augments;
    private final long contextRevision;
    private final List<Finding> findings;

    private RuntimeState(StructureContext context, Map<AugmentKey, Double> augments, long contextRevision,
                         List<Finding> findings) {
        this.context = context;
        this.augments = augments;
        this.contextRevision = contextRevision;
        this.findings = findings;
    }

    public static RuntimeState of(StructureContext context, List<Augment> augments, long contextRevision) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(augments, "augments");
        if (contextRevision < 0) {
            throw new IllegalArgumentException("contextRevision");
        }
        List<Augment> ordered = new ArrayList<>(augments);
        ordered.sort(Comparator.comparing(Augment::key));
        Map<AugmentKey, Double> totals = new LinkedHashMap<>();
        for (Augment augment : ordered) {
            AugmentKey key = augment.key();
            @Nullable Double running = totals.get(key);
            totals.put(key, running == null
                    ? augment.amount()
                    : key.fold().combine(running, augment.amount()));
        }
        List<Finding> findings = new ArrayList<>();
        Iterator<Map.Entry<AugmentKey, Double>> entries = totals.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<AugmentKey, Double> entry = entries.next();
            if (!Double.isFinite(entry.getValue())) {
                entries.remove();
                findings.add(ContextFindings.nonFiniteTotal(entry.getKey()));
            }
        }
        return new RuntimeState(context, Collections.unmodifiableMap(totals), contextRevision, List.copyOf(findings));
    }

    public StructureContext context() {
        return context;
    }

    public Map<AugmentKey, Double> augments() {
        return augments;
    }

    public long contextRevision() {
        return contextRevision;
    }

    public List<Finding> findings() {
        return findings;
    }

    public <T> RuntimeState upgrade(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (key.retention() != Retention.CONTEXT) {
            throw new IllegalArgumentException("key");
        }
        if (contextRevision != context.revision()) {
            List<Finding> refused = new ArrayList<>(findings);
            refused.add(ContextFindings.staleBase(context.instance(), contextRevision, context.revision()));
            return new RuntimeState(context, augments, contextRevision, List.copyOf(refused));
        }
        long next = context.revision() + 1;
        ContextFrame frame = new ContextFrame(ContextTier.SETTLE, next, Map.<ContextKey<?>, Object>of(key, value));
        List<ContextFrame> frames = new ArrayList<>(context.frames());
        frames.add(frame);
        StructureContext upgraded = new StructureContext(context.instance(), next, frames);
        return new RuntimeState(upgraded, augments, next, findings);
    }
}
