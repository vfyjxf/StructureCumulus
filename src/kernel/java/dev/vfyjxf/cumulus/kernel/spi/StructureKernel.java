package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
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
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.SolveResult;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Anchors;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** The facade: five pipeline operations, three helpers and three lookups. */
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

    public CompileResult compile(Document document) {
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            host.types(),
            host.documents()
        );
        return DocumentCompiler.compile(document, context);
    }

    public SitingResult siting(Blueprint blueprint, LatticePoint origin, WorldView view) {
        return Anchors.resolve(blueprint, origin, view);
    }

    public MatchResult check(MatchRequest request) {
        return MatchEngine.check(request);
    }

    public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted) {
        return MatchEngine.settle(request, admitted, host.ledger(), host.contexts());
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
        for (Finding finding : findings.findings()) {
            for (var id : catalogs.remedies().ids()) {
                RemedyProvider provider = catalogs.remedies().require(id);
                try {
                    for (Remedy remedy : provider.solve(finding, context)) {
                        if (remedy == null) {
                            problems.add(
                                solveProblem(
                                    Codes.REMEDY_MALFORMED,
                                    finding,
                                    "provider " + id + " returned a null remedy"
                                )
                            );
                        } else {
                            unique.add(remedy);
                        }
                    }
                } catch (RuntimeException failure) {
                    problems.add(
                        solveProblem(
                            Codes.REMEDY_FAILED,
                            finding,
                            "provider " + id + " failed: " + failure.getMessage()
                        )
                    );
                }
            }
        }
        remedies.addAll(unique);
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

    private static int applicability(Remedy remedy) {
        return remedy instanceof Remedy.Repair repair
                ? repair.applicability().rank()
                : Applicability.UNSPECIFIED.rank();
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
