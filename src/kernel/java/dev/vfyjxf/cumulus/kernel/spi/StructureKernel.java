package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.engine.MatchEngine;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** The facade: four pipeline operations, three helpers and four lookups. */
public final class StructureKernel {

    private final Catalogs catalogs;
    private final Host host;

    private StructureKernel(Catalogs catalogs, Host host) {
        this.catalogs = catalogs;
        this.host = host;
    }

    public static StructureKernel create(Catalogs catalogs) {
        return create(catalogs, Host.empty());
    }

    public static StructureKernel create(Catalogs catalogs, Host host) {
        Objects.requireNonNull(catalogs, "catalogs");
        Objects.requireNonNull(host, "host");
        return new StructureKernel(catalogs, host);
    }

    public Catalogs catalogs() {
        return catalogs;
    }

    public @Nullable Document document(NamespacedId id) {
        return host.documents().find(id);
    }

    public @Nullable ClaimLedger ledger() {
        return host.ledger();
    }

    public ContextStore contexts() {
        return host.contexts();
    }

    public StructureManager structures() {
        return host.structures();
    }

    public CompileResult compile(Document document) {
        return DocumentCompiler.compile(document, context());
    }

    /** The structure-set document under this id, when the host's source loads both document kinds. */
    public @Nullable StructureSetDocument structureSet(NamespacedId id) {
        return host.documents().findSet(id);
    }

    /** The only structure-set lowering path, with the same catalogs and source {@link #compile} uses. */
    public StructureSetCompileResult compileSet(StructureSetDocument document) {
        return StructureSetCompiler.compile(document, context());
    }

    private CompilerContext context() {
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            host.types(),
            host.documents()
        );
    }

    public MatchResult check(MatchRequest request) {
        return MatchEngine.check(request);
    }

    /** Settles through the attempt that reserved the instance. */
    public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted, StructureAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        return MatchEngine.settle(request, admitted, host.ledger(), attempt);
    }

    public StructureContext derive(MatchResult.Formed formed, List<DerivationSpec> derivations) {
        return MatchEngine.derive(formed, derivations, catalogs.derivers(), host.contexts());
    }

    public SolveResult solve(FindingBundle findings, RemedyContext context) {
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(context, "context");
        List<Remedy> remedies = new ArrayList<>();
        List<Finding> problems = new ArrayList<>();
        Set<Remedy> unique = new LinkedHashSet<>();
        Map<NamespacedId, Remedy.Preview> previews = new LinkedHashMap<>();
        for (Finding finding : findings.findings()) {
            for (var id : catalogs.remedies().ids()) {
                RemedyProvider provider = catalogs.remedies().require(id);
                try {
                    for (Remedy remedy : provider.solve(finding, context)) {
                        if (remedy == null) {
                            problems.add(
                                solveProblem(
                                    Codes.remedyMalformed,
                                    finding,
                                    "provider " + id + " returned a null remedy"
                                )
                            );
                        } else if (remedy instanceof Remedy.Preview preview) {
                            previews.merge(id, preview, StructureKernel::mergePreview);
                        } else {
                            unique.add(remedy);
                        }
                    }
                } catch (RuntimeException failure) {
                    problems.add(
                        solveProblem(Codes.remedyFailed, finding, "provider " + id + " failed: " + failure.getMessage())
                    );
                }
            }
        }
        remedies.addAll(unique);
        remedies.addAll(previews.values());
        remedies.sort(Comparator.comparingInt(StructureKernel::applicability));
        if (remedies.size() > context.maxResults()) {
            remedies = new ArrayList<>(remedies.subList(0, context.maxResults()));
        }
        return new SolveResult(remedies, FindingBundle.of(problems));
    }

    public @Nullable Explanation explain(FindingCode code) {
        CodeEntry entry = catalogs.codes().get(codeKey(code));
        return entry == null ? null : entry.explanation();
    }

    public Explanation explainOrFallback(FindingCode code) {
        Explanation explanation = explain(code);
        if (explanation != null) {
            return explanation;
        }
        return new Explanation(
            code,
            "no explanation is registered for " + code.text(),
            "The code belongs to " + code.family().subject() + ".",
            List.of()
        );
    }

    /** One provider's previews describe one overlay: a cell keeps the loudest mark, markers keep processing order. */
    private static Remedy.Preview mergePreview(Remedy.Preview left, Remedy.Preview right) {
        Map<LatticeKey, CellMark> cells = new TreeMap<>(left.overlay().cells());
        for (Map.Entry<LatticeKey, CellMark> mark : right.overlay().cells().entrySet()) {
            cells.merge(mark.getKey(), mark.getValue(), CellMark::louder);
        }
        List<Marker> markers = new ArrayList<>(left.overlay().markers());
        markers.addAll(right.overlay().markers());
        OverlayModel overlay = new OverlayModel(
            left.overlay().bounds().bounding(right.overlay().bounds()),
            cells,
            markers
        );
        return new Remedy.Preview(overlay, left.rationale());
    }

    private static int applicability(Remedy remedy) {
        return remedy instanceof Remedy.Repair repair
                ? repair.applicability().rank()
                : Applicability.unspecified.rank();
    }

    private static Finding solveProblem(CodeEntry entry, Finding finding, String message) {
        Finding.Builder builder = Finding.builder(entry).phase(finding.phase()).message(message);
        Label primary = finding.primaryLabel();
        if (primary != null) {
            builder.label(Label.primary(primary.locus(), message));
        }
        return builder.build();
    }

    private static NamespacedId codeKey(FindingCode code) {
        return NamespacedId.of("structurecumulus", code.text().toLowerCase(Locale.ROOT));
    }
}
