package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Label;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.context.ContextDraft;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.ContextTier;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.engine.MatchSession;
import dev.vfyjxf.cumulus.kernel.engine.MatchSettler;
import dev.vfyjxf.cumulus.kernel.engine.UndecidedReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The synchronous facade: a check runs the matcher and, for {@code SETTLE}, the settle inline;
 * {@code derive} runs derivers over a store. No member reads a world except through its arguments.
 */
public final class StructureKernel {

    private static final FindingCode NO_LEDGER = FindingCode.of(FindingCode.Family.ENG, 103);
    private static final Object ABSENT = new Object();

    private final Catalogs catalogs;
    private final @Nullable ClaimLedger ledger;

    private StructureKernel(Catalogs catalogs, @Nullable ClaimLedger ledger) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.ledger = ledger;
    }

    public static StructureKernel create(Catalogs catalogs) {
        return new StructureKernel(catalogs, null);
    }

    public static StructureKernel create(Catalogs catalogs, @Nullable ClaimLedger ledger) {
        return new StructureKernel(catalogs, ledger);
    }

    public Catalogs catalogs() {
        return catalogs;
    }

    public @Nullable ClaimLedger ledger() {
        return ledger;
    }

    /**
     * Runs one attempt. The capture is checked once, after the units ran and before anything is
     * published, and every fact it reads comes from the request's lease.
     */
    public MatchResult check(MatchRequest request) {
        Objects.requireNonNull(request, "request");
        ClaimLedger claims = ledger;
        if (request.stage() == Stage.SETTLE && claims == null) {
            return noLedger(request.stage());
        }
        return new MatchSession(request, claims == null ? null : MatchSettler.inline(claims)).run();
    }

    public StructureContext derive(MatchResult.Formed formed, ContextStore store, List<Deriver<?>> derivers) {
        Objects.requireNonNull(formed, "formed");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(derivers, "derivers");
        StructureContext base = store.context(formed.id());
        List<Deriver<?>> ordered = new ArrayList<>(derivers);
        ordered.sort(Comparator.comparingInt(deriver -> deriver.tier().ordinal()));
        Map<ContextKey<?>, Object> overlay = new LinkedHashMap<>();
        for (ContextTier tier : ContextTier.values()) {
            List<Deriver<?>> atTier = ordered.stream().filter(deriver -> deriver.tier() == tier).toList();
            if (atTier.isEmpty()) {
                continue;
            }
            publishTier(formed, store, tier, atTier, overlay);
            base = store.context(formed.id());
        }
        return base;
    }

    private void publishTier(MatchResult.Formed formed, ContextStore store, ContextTier tier,
                             List<Deriver<?>> derivers, Map<ContextKey<?>, Object> overlay) {
        FindingBundle failure = FindingBundle.empty();
        for (int attempt = 0; attempt < 2; attempt++) {
            ContextDraft draft = store.draft(tier, store.revision());
            ContextView view = new DerivedView(store, formed, tier, overlay);
            for (Deriver<?> deriver : derivers) {
                runDeriver(deriver, view, overlay, draft);
            }
            Outcome<Void> published = store.publish(draft);
            if (!(published instanceof Outcome.Missing<?> missing)) {
                return;
            }
            failure = missing.findings();
        }
        throw new IllegalStateException("derive at " + tier + " could not publish: " + failure);
    }

    private static <T> void runDeriver(Deriver<T> deriver, ContextView view,
                                       Map<ContextKey<?>, Object> overlay, ContextDraft draft) {
        T value = deriver.derive(new DeriveView(view));
        if (value == null) {
            overlay.put(deriver.output(), ABSENT);
            return;
        }
        overlay.put(deriver.output(), value);
        draft.put(deriver.output(), value);
    }

    private static MatchResult noLedger(Stage stage) {
        Finding finding = Finding.builder(NO_LEDGER)
                .severity(Severity.ERROR)
                .message("SETTLE needs a claim ledger and this kernel holds none")
                .stage(stage)
                .build();
        return new MatchResult.Undecided(UndecidedReason.DESIGN_DEFECT, List.of(finding), null, null);
    }

    private record DeriveView(ContextView view) implements DeriveContext {

        @Override
        public <T> Outcome<T> read(ContextKey<T> key) {
            return view.require(key);
        }

        @Override
        public ContextView context() {
            return view;
        }
    }

    private static final class DerivedView implements ContextView {

        private final ContextStore store;
        private final MatchResult.Formed formed;
        private final ContextTier tier;
        private final Map<ContextKey<?>, Object> overlay;

        private DerivedView(ContextStore store, MatchResult.Formed formed, ContextTier tier,
                            Map<ContextKey<?>, Object> overlay) {
            this.store = store;
            this.formed = formed;
            this.tier = tier;
            this.overlay = overlay;
        }

        @Override
        public ContextTier tier() {
            return tier;
        }

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            Objects.requireNonNull(key, "key");
            if (overlay.containsKey(key)) {
                Object value = overlay.get(key);
                return value == ABSENT ? null : key.type().cast(value);
            }
            T published = store.context(formed.id()).at(tier).get(key);
            return published != null ? published : formed.outcome().value(key);
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            T value = get(key);
            if (value != null) {
                return new Outcome.Found<>(value, List.of(), FindingBundle.empty());
            }
            String message = "context key " + key.id() + " has no value at " + tier;
            Finding absent = Finding.builder(FindingCode.of(FindingCode.Family.CTX, 202))
                    .severity(Severity.ERROR)
                    .message(message)
                    .label(Label.primary(
                            Locus.key(formed.id(), key.id()), message))
                    .stage(stageOf(tier))
                    .build();
            return new Outcome.Missing<>(FindingBundle.of(absent));
        }

        @Override
        public boolean has(ContextKey<?> key) {
            return get(key) != null;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            Set<ContextKey<?>> keys = new TreeSet<>();
            for (Map.Entry<ContextKey<?>, Object> entry : overlay.entrySet()) {
                if (entry.getValue() != ABSENT) {
                    keys.add(entry.getKey());
                }
            }
            keys.addAll(store.context(formed.id()).at(tier).keys());
            keys.addAll(formed.outcome().context().keySet());
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public ContextView at(ContextTier narrower) {
            return this;
        }
    }

    private static Stage stageOf(ContextTier tier) {
        return switch (tier) {
            case BLUEPRINT -> Stage.DESIGN;
            case PROBE -> Stage.PROBE;
            case FORM -> Stage.FORM;
            case SETTLE -> Stage.SETTLE;
        };
    }
}
