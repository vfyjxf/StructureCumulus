package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** One stage's writes; {@code put} is single-writer, {@code contribute} is one section's partial. */
public final class ContextDraft {

    private final ContextStore owner;
    private final ContextTier tier;
    private final long baseRevision;
    private final DraftState state;
    private final @Nullable SectionKey section;

    ContextDraft(ContextStore owner, ContextTier tier, long baseRevision) {
        this(owner, tier, baseRevision, new DraftState(), null);
    }

    private ContextDraft(ContextStore owner, ContextTier tier, long baseRevision, DraftState state,
                         @Nullable SectionKey section) {
        this.owner = owner;
        this.tier = tier;
        this.baseRevision = baseRevision;
        this.state = state;
        this.section = section;
    }

    public ContextTier tier() {
        return tier;
    }

    public long baseRevision() {
        return baseRevision;
    }

    public <T> ContextDraft put(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        requireValue(key, value);
        if (key.contributed()) {
            throw new IllegalStateException("SC-CTX-0109: put targets the section-contributed key " + key.id());
        }
        checkWritable();
        state.put(key, value);
        return this;
    }

    public <T> ContextDraft contribute(ContextKey<T> key, T partial) {
        Objects.requireNonNull(key, "key");
        requireValue(key, partial);
        if (!key.contributed()) {
            throw new IllegalStateException("SC-CTX-0109: contribute targets the single-writer key " + key.id());
        }
        if (section == null) {
            throw new IllegalStateException("contribute needs a section; call forSection(...) first");
        }
        checkWritable();
        state.contribute(key, section, partial);
        return this;
    }

    /** A section-scoped handle stamps every contribution with that section. */
    public ContextDraft forSection(SectionKey section) {
        Objects.requireNonNull(section, "section");
        if (this.section != null) {
            throw new IllegalStateException("a section-scoped draft cannot be re-scoped");
        }
        checkWritable();
        return new ContextDraft(owner, tier, baseRevision, state, section);
    }

    /** Records that a section of the key's region was refused, which defeats the fold for that key. */
    public <T> ContextDraft refuse(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        requireContributed(key);
        checkWritable();
        state.refuse(key);
        return this;
    }

    /** Records that every section was readable and no partial was produced; the only path to whenAbsent. */
    public <T> ContextDraft declined(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        requireContributed(key);
        checkWritable();
        state.decline(key);
        return this;
    }

    boolean ownedBy(ContextStore store) {
        return owner == store;
    }

    boolean consumed() {
        return state.consumed;
    }

    void consume() {
        state.consumed = true;
    }

    FindingBundle conflictFindings() {
        if (state.conflicts.isEmpty()) {
            return FindingBundle.empty();
        }
        List<Finding> findings = new ArrayList<>();
        for (ContextKey<?> key : state.conflicts) {
            findings.add(ContextFindings.conflictingValue(owner.instance(), key, tier));
        }
        return ContextFindings.bundle(findings);
    }

    ContextFrame buildFrame(long revision) {
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        for (ContextKey<?> key : keys()) {
            Object value = valueOf(key);
            values.put(key, value == null ? ContextFrame.absent() : value);
        }
        return new ContextFrame(tier, revision, values);
    }

    /** This draft's folded value for the key; {@code null} when the draft decides the key is absent. */
    public <T> @Nullable T read(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object value = valueOf(key);
        return value == null ? null : key.type().cast(value);
    }

    /** True when the draft decides the key, including a decision that it carries no value. */
    public boolean publishes(ContextKey<?> key) {
        Objects.requireNonNull(key, "key");
        return decided(key);
    }

    /** Every key this draft decides, in canonical order. */
    public Set<ContextKey<?>> keys() {
        Set<ContextKey<?>> keys = new TreeSet<>(state.puts.keySet());
        keys.addAll(state.contributions.keySet());
        keys.addAll(state.refused);
        keys.addAll(state.declined);
        return Collections.unmodifiableSet(keys);
    }

    private boolean decided(ContextKey<?> key) {
        return state.puts.containsKey(key)
                || state.contributions.containsKey(key)
                || state.refused.contains(key)
                || state.declined.contains(key);
    }

    private @Nullable Object valueOf(ContextKey<?> key) {
        if (state.puts.containsKey(key)) {
            return state.puts.get(key);
        }
        if (state.refused.contains(key)) {
            return null;
        }
        TreeMap<SectionKey, Object> parts = state.contributions.get(key);
        if (parts != null && !parts.isEmpty()) {
            return fold(key, parts.values());
        }
        return state.declined.contains(key) ? key.whenAbsent() : null;
    }

    private void checkWritable() {
        if (state.consumed) {
            throw new IllegalStateException("draft already published");
        }
    }

    private static <T> void requireValue(ContextKey<T> key, T value) {
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException("value");
        }
    }

    private static void requireContributed(ContextKey<?> key) {
        if (!key.contributed()) {
            throw new IllegalStateException("SC-CTX-0109: " + key.id() + " is written with put, not folded");
        }
    }

    private static <T> @Nullable T fold(ContextKey<T> key, Collection<Object> partials) {
        Merge<T> merge = key.merge();
        if (merge == null) {
            throw new IllegalStateException("SC-CTX-0109: " + key.id() + " has no merge");
        }
        @Nullable T total = null;
        for (Object partial : partials) {
            T value = key.type().cast(partial);
            total = total == null ? value : merge.combine(total, value);
        }
        return total;
    }

    private static final class DraftState {

        private final Map<ContextKey<?>, Object> puts = new LinkedHashMap<>();
        private final Map<ContextKey<?>, TreeMap<SectionKey, Object>> contributions = new TreeMap<>();
        private final TreeSet<ContextKey<?>> refused = new TreeSet<>();
        private final TreeSet<ContextKey<?>> declined = new TreeSet<>();
        private final TreeSet<ContextKey<?>> conflicts = new TreeSet<>();
        private boolean consumed;

        private <T> void put(ContextKey<T> key, T value) {
            @Nullable Object existing = puts.putIfAbsent(key, value);
            if (existing != null && !existing.equals(value)) {
                conflicts.add(key);
            }
        }

        private <T> void contribute(ContextKey<T> key, SectionKey section, T partial) {
            TreeMap<SectionKey, Object> bySection = contributions.computeIfAbsent(key, ignored -> new TreeMap<>());
            @Nullable Object existing = bySection.putIfAbsent(section, partial);
            if (existing != null && !existing.equals(partial)) {
                conflicts.add(key);
            }
        }

        private void refuse(ContextKey<?> key) {
            refused.add(key);
            declined.remove(key);
        }

        private void decline(ContextKey<?> key) {
            declined.add(key);
        }
    }
}
