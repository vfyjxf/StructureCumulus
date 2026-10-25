package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The instance's own generation counter: every emitted {@code Formed} consumes one, so a re-emission
 * is distinguishable from a new match without the engine ever bumping a number by hand.
 */
public final class InstanceRecord {

    private final InstanceId id;
    private long generation;
    private MatchResult.@Nullable Formed previous;

    private InstanceRecord(InstanceId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static InstanceRecord of(InstanceId id) {
        return new InstanceRecord(id);
    }

    public InstanceId id() {
        return id;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized MatchResult.@Nullable Formed previous() {
        return previous;
    }

    public synchronized long nextGeneration() {
        generation++;
        return generation;
    }

    public synchronized void remember(MatchResult.Formed formed) {
        Objects.requireNonNull(formed, "formed");
        if (!formed.id().equals(id)) {
            throw new IllegalArgumentException("formed " + formed.id() + " is not instance " + id);
        }
        generation = Math.max(generation, formed.generation());
        previous = formed;
    }

    /**
     * The previous {@code Formed} again, with {@code generation + 1} and the same outcome and claims;
     * {@code null} when this instance never emitted one.
     */
    public synchronized MatchResult.@Nullable Formed reemit(FindingBundle findings) {
        Objects.requireNonNull(findings, "findings");
        var last = previous;
        if (last == null) {
            return null;
        }
        MatchResult.Formed again = new MatchResult.Formed(last.id(), last.siting(), last.outcome(),
                last.claims(), generation + 1L, last.contextRevision(), last.ledgerRevision(),
                last.lease(), findings);
        generation = again.generation();
        previous = again;
        return again;
    }
}
