package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.Merge;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.Seam;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The only lowering function: data in, blueprint or findings out. */
public final class DocumentCompiler {

    public static CompileResult compile(Document document, CompilerContext context) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(context, "context");
        try {
            return compileChecked(document, context);
        } catch (IllegalArgumentException rejected) {
            List<Finding> findings = new ArrayList<>();
            error(
                findings,
                Codes.documentField,
                "the document cannot be lowered: " + rejected.getMessage(),
                document.root().source(),
                documentLocus(document)
            );
            return new CompileResult(FindingBundle.of(findings), null);
        }
    }

    private static CompileResult compileChecked(Document document, CompilerContext context) {
        List<Finding> findings = new ArrayList<>();
        if (document.schemaVersion() != Document.currentVersion) {
            error(
                findings,
                Codes.schemaVersion,
                "unsupported schema version " + document.schemaVersion(),
                null,
                documentLocus(document)
            );
            return new CompileResult(FindingBundle.of(findings), null);
        }
        Flat flat = new Flat(document.root());
        Set<String> active = new HashSet<>();
        flatten(document.root(), document, context, "", LatticePoint.origin, flat, findings, active);
        if (hasErrors(findings)) {
            return new CompileResult(FindingBundle.of(findings), null);
        }

        Map<NamespacedId, ContextKey<?>> keys = buildKeys(flat.keys, context, findings);
        Map<NamespacedId, Bay> bays = buildBays(flat.bays, flat.bounds, findings);
        List<Seam> seams = buildSeams(flat.seams, findings);
        List<Check<?>> checks = buildChecks(flat.checks, bays, keys, document, context, findings);
        List<Computation<?>> computations = buildComputations(
            flat.computations,
            bays,
            keys,
            document,
            context,
            findings
        );
        List<DerivationSpec> derivations = buildDerivations(flat.derivations, keys, findings, documentLocus(document));
        if (hasErrors(findings)) {
            return new CompileResult(FindingBundle.of(findings), null);
        }

        BlueprintBuilder builder = Blueprint.builder(document.root().id()).bounds(flat.bounds).checks(checks)
                .computations(computations).seams(seams).anchors(flat.anchors).derivations(derivations)
                .arguments(document.root().arguments());
        if (document.root().envelope()) {
            builder.envelope();
        }
        if (flat.orientation != null) {
            builder.orientation(flat.orientation);
        }
        if (document.root().source() != null) {
            builder.source(document.root().source());
        }
        try {
            Blueprint blueprint = builder.build();
            return new CompileResult(FindingBundle.of(findings), blueprint);
        } catch (IllegalArgumentException rejected) {
            CodeEntry entry = codeFromMessage(rejected.getMessage());
            error(
                findings,
                entry,
                "the compiled blueprint is invalid: " + rejected.getMessage(),
                document.root().source(),
                documentLocus(document)
            );
            return new CompileResult(FindingBundle.of(findings), null);
        }
    }

    private static final class Flat {
        private final Cuboid bounds;
        private final List<BaySpec> bays = new ArrayList<>();
        private final List<SeamSpec> seams = new ArrayList<>();
        private final List<CheckSpec> checks = new ArrayList<>();
        private final List<ComputationSpec> computations = new ArrayList<>();
        private final List<KeySpec> keys = new ArrayList<>();
        private final List<DerivationSpec> derivations = new ArrayList<>();
        private final List<AnchorSpec> anchors = new ArrayList<>();
        private @Nullable OrientationPolicy orientation;

        private Flat(BlueprintSpec root) {
            this.bounds = root.bounds();
            this.anchors.addAll(root.anchors());
            this.orientation = root.orientation();
        }
    }

    private static void flatten(
        BlueprintSpec spec,
        Document owner,
        CompilerContext context,
        String prefix,
        LatticePoint offset,
        Flat flat,
        List<Finding> findings,
        Set<String> active
    ) {
        String key = owner.root().id() + "/" + spec.id();
        if (!active.add(key)) {
            error(findings, Codes.definitionCycle, "definition cycle at " + spec.id(), spec.source(), null);
            return;
        }
        if (!prefix.isEmpty()) {
            if (!spec.checks().isEmpty()
                    || !spec.computations().isEmpty()
                    || !spec.keys().isEmpty()
                    || !spec.derivations().isEmpty()
                    || !spec.anchors().isEmpty()
                    || spec.orientation() != null
                    || spec.envelope()) {
                error(
                    findings,
                    Codes.documentField,
                    "a definition may contribute only bays, seams and placements",
                    spec.source(),
                    null
                );
                active.remove(key);
                return;
            }
        } else {
            flat.checks.addAll(spec.checks());
            flat.computations.addAll(spec.computations());
            flat.keys.addAll(spec.keys());
            flat.derivations.addAll(spec.derivations());
        }
        for (BaySpec bay : spec.bays()) {
            CuboidSet translated = translate(bay.cells(), offset);
            if (translated == null) {
                error(findings, Codes.placementOutside, "bay " + bay.id() + " leaves the lattice", bay.source(), null);
                continue;
            }
            Footprint footprint = translate(bay.footprint(), offset);
            if (footprint == null) {
                error(
                    findings,
                    Codes.placementOutside,
                    "the footprint of " + bay.id() + " leaves the lattice",
                    bay.source(),
                    null
                );
                continue;
            }
            flat.bays.add(
                new BaySpec(
                    qualify(prefix, bay.id()),
                    translated,
                    bay.coverage(),
                    footprint,
                    bay.sharing(),
                    bay.description(),
                    bay.source()
                )
            );
        }
        for (SeamSpec seam : spec.seams()) {
            Cuboid translated = seam.cells().translate(offset.x(), offset.y(), offset.z());
            if (translated == null) {
                error(
                    findings,
                    Codes.placementOutside,
                    "seam " + seam.id() + " leaves the lattice",
                    seam.source(),
                    null
                );
                continue;
            }
            flat.seams.add(
                new SeamSpec(
                    new SeamId(qualify(prefix, seam.id().id())),
                    translated,
                    seam.kind(),
                    seam.properties(),
                    seam.source()
                )
            );
        }
        for (PlacementSpec placement : spec.placements()) {
            BlueprintSpec definition = owner.definitions().get(placement.definition());
            Document nextOwner;
            if (definition == null) {
                Document external = context.documents().find(placement.definition());
                if (external == null) {
                    error(
                        findings,
                        Codes.definitionMissing,
                        "no definition " + placement.definition(),
                        placement.source(),
                        null
                    );
                    continue;
                }
                definition = external.root();
                nextOwner = external;
            } else {
                nextOwner = owner;
            }
            LatticePoint next = offset.offset(placement.offset().x(), placement.offset().y(), placement.offset().z());
            if (next == null) {
                error(findings, Codes.placementOutside, "placement leaves the lattice", placement.source(), null);
                continue;
            }
            String nextPrefix = prefix.isEmpty()
                    ? placement.alias().path() + "/"
                    : prefix + placement.alias().path() + "/";
            flatten(definition, nextOwner, context, nextPrefix, next, flat, findings, active);
        }
        active.remove(key);
    }

    private static Map<NamespacedId, ContextKey<?>> buildKeys(
        List<KeySpec> specs,
        CompilerContext context,
        List<Finding> findings
    ) {
        Map<NamespacedId, ContextKey<?>> keys = new LinkedHashMap<>();
        for (KeySpec spec : specs) {
            Class<?> type = type(spec.type(), context, spec, findings);
            if (type == null) {
                continue;
            }
            Merge<?> merge = merge(spec.merge(), type, spec, findings);
            if (spec.merge() != null && merge == null) {
                continue;
            }
            Object absent = spec.whenAbsent() == null ? null : decode(spec.whenAbsent(), type, spec, findings);
            if (spec.whenAbsent() != null && absent == null) {
                continue;
            }
            ContextKey<?> key = rawKey(spec, type, merge, absent);
            ContextKey<?> previous = keys.putIfAbsent(key.id(), key);
            if (previous != null && !previous.equals(key)) {
                error(
                    findings,
                    Codes.documentDuplicate,
                    "key " + key.id() + " is declared twice with different content",
                    spec.source(),
                    null
                );
            }
        }
        return keys;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ContextKey<?> rawKey(
        KeySpec spec,
        Class<?> type,
        @Nullable Merge<?> merge,
        @Nullable Object absent
    ) {
        return ContextKeys.of(spec.id(), (Class) type, spec.retention(), (Merge) merge, absent);
    }

    private static @Nullable Class<?> type(String name, CompilerContext context, KeySpec spec, List<Finding> findings) {
        Class<?> builtin = switch (name) {
            case "long" -> Long.class;
            case "int" -> Integer.class;
            case "boolean" -> Boolean.class;
            case "string" -> String.class;
            case "id" -> NamespacedId.class;
            case "cuboid" -> Cuboid.class;
            case "cuboid_set" -> CuboidSet.class;
            default -> null;
        };
        if (builtin != null) {
            return builtin;
        }
        NamespacedId id = NamespacedId.parse(name);
        Class<?> custom = id == null ? null : context.types().get(id);
        if (custom == null) {
            error(findings, Codes.keyType, "unknown key type " + name, spec.source(), null);
        }
        return custom;
    }

    private static @Nullable Merge<?> merge(
        @Nullable NamespacedId merge,
        Class<?> type,
        KeySpec spec,
        List<Finding> findings
    ) {
        if (merge == null) {
            return null;
        }
        if (!merge.namespace().equals(NamespacedId.defaultNamespace)) {
            error(findings, Codes.keyType, "unknown merge " + merge, spec.source(), null);
            return null;
        }
        String name = merge.path();
        Object candidate = switch (name) {
            case "sum" -> Long.class.equals(type) ? Merges.sum() : null;
            case "min" -> Long.class.equals(type) ? Merges.min() : null;
            case "max" -> Long.class.equals(type) ? Merges.max() : null;
            case "union" -> CuboidSet.class.equals(type) ? Merges.union() : null;
            case "and" -> Boolean.class.equals(type) ? Merges.and() : null;
            case "or" -> Boolean.class.equals(type) ? Merges.or() : null;
            default -> null;
        };
        if (candidate == null) {
            error(
                findings,
                Codes.keyType,
                "merge " + merge + " does not fit type " + type.getName(),
                spec.source(),
                null
            );
            return null;
        }
        return (Merge<?>) candidate;
    }

    private static @Nullable Object decode(Value value, Class<?> type, KeySpec spec, List<Finding> findings) {
        if (type.equals(Long.class)) {
            long number = value.asNumber(Long.MIN_VALUE);
            if (number != Long.MIN_VALUE) {
                return number;
            }
        } else if (type.equals(Integer.class)) {
            long number = value.asNumber(Long.MIN_VALUE);
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return (int) number;
            }
        } else if (type.equals(Boolean.class)) {
            if (value.isBoolean()) {
                return value.asBoolean(false);
            }
        } else if (type.equals(String.class)) {
            if (value instanceof Value.StringValue stringValue) {
                return stringValue.value();
            }
        } else if (type.equals(NamespacedId.class)) {
            NamespacedId id = value instanceof Value.StringValue stringValue
                    ? NamespacedId.parse(stringValue.value())
                    : null;
            if (id != null) {
                return id;
            }
        }
        error(findings, Codes.keyType, "whenAbsent does not fit key " + spec.id(), spec.source(), null);
        return null;
    }

    private static Map<NamespacedId, Bay> buildBays(List<BaySpec> specs, Cuboid bounds, List<Finding> findings) {
        Map<NamespacedId, Bay> bays = new LinkedHashMap<>();
        CuboidSet boundsSet = bounds.asSet();
        for (BaySpec spec : specs) {
            if (!boundsSet.containsAll(spec.cells())) {
                error(
                    findings,
                    Codes.placementOutside,
                    "bay " + spec.id() + " leaves the blueprint bounds",
                    spec.source(),
                    Locus.local(spec.cells())
                );
                continue;
            }
            try {
                Bay bay = new Bay(
                    spec.id(),
                    spec.cells(),
                    spec.coverage(),
                    spec.footprint(),
                    spec.sharing(),
                    spec.description()
                );
                Bay previous = bays.putIfAbsent(bay.id(), bay);
                if (previous != null && !previous.equals(bay)) {
                    error(
                        findings,
                        Codes.documentDuplicate,
                        "two different bays share id " + bay.id(),
                        spec.source(),
                        null
                    );
                }
            } catch (IllegalArgumentException rejected) {
                String message = rejected.getMessage();
                error(findings, Codes.documentField, message == null ? "invalid bay" : message, spec.source(), null);
            }
        }
        return bays;
    }

    private static List<Seam> buildSeams(List<SeamSpec> specs, List<Finding> findings) {
        List<Seam> seams = new ArrayList<>();
        Set<SeamId> ids = new HashSet<>();
        for (SeamSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.documentDuplicate, "duplicate seam id " + spec.id(), spec.source(), null);
                continue;
            }
            seams.add(new Seam(spec.id(), spec.cells(), spec.kind(), spec.properties()));
        }
        return seams;
    }

    private static List<Check<?>> buildChecks(
        List<CheckSpec> specs,
        Map<NamespacedId, Bay> bays,
        Map<NamespacedId, ContextKey<?>> keys,
        Document document,
        CompilerContext context,
        List<Finding> findings
    ) {
        List<Check<?>> checks = new ArrayList<>();
        Set<NamespacedId> ids = new HashSet<>();
        for (CheckSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.documentDuplicate, "duplicate check id " + spec.id(), spec.source(), null);
                continue;
            }
            Bay bay = bays.get(spec.bay());
            if (bay == null) {
                error(
                    findings,
                    Codes.bayUnresolved,
                    "check " + spec.id() + " names no merged bay " + spec.bay(),
                    spec.source(),
                    null
                );
                continue;
            }
            FactoryContext factory = new FactoryContext(document.root().id(), spec.source(), keys::get, findings::add);
            Focus<?> focus = focus(context, spec.focusKind(), spec.focusArgs(), factory, findings);
            if (focus == null) {
                continue;
            }
            Expectation<?> expectation = expectation(
                context,
                spec.expectationKind(),
                spec.expectationArgs(),
                factory,
                findings
            );
            if (expectation == null) {
                continue;
            }
            try {
                checks.add(check(spec, bay, focus, expectation));
            } catch (IllegalArgumentException rejected) {
                error(
                    findings,
                    Codes.documentField,
                    "check " + spec.id() + ": " + rejected.getMessage(),
                    spec.source(),
                    null
                );
            }
        }
        return checks;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Check<?> check(CheckSpec spec, Bay bay, Focus<?> focus, Expectation<?> expectation) {
        return Check.of(spec.id(), bay, (Focus) focus, (Expectation) expectation, spec.description());
    }

    private static @Nullable Focus<?> focus(
        CompilerContext context,
        NamespacedId kind,
        Value args,
        FactoryContext factory,
        List<Finding> findings
    ) {
        if (kind.path().equals("missing_kind")) {
            error(findings, Codes.factoryUnknown, "a focus has no kind", factory.source(), null);
            return null;
        }
        FocusFactory registered = lookup(context.focuses(), kind);
        if (registered == null) {
            error(findings, Codes.documentFactory, "no focus factory " + kind, factory.source(), null);
            return null;
        }
        Focus<?> focus;
        try {
            focus = registered.create(args, factory);
        } catch (RuntimeException failure) {
            error(
                findings,
                Codes.factoryBad,
                "focus factory " + kind + " failed: " + failure.getMessage(),
                factory.source(),
                null
            );
            return null;
        }
        if (focus == null) {
            error(findings, Codes.factoryBad, "focus factory " + kind + " returned null", factory.source(), null);
        }
        return focus;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable Expectation<?> expectation(
        CompilerContext context,
        NamespacedId kind,
        Value args,
        FactoryContext factory,
        List<Finding> findings
    ) {
        String name = grammar(kind);
        if (name == null) {
            name = ""; // no grammar case matches; the default branch resolves the catalog
        } else if (name.equals("missing_kind")) {
            error(findings, Codes.factoryUnknown, "an expectation has no kind", factory.source(), null);
            return null;
        }
        List<Expectation<?>> children = new ArrayList<>();
        switch (name) {
            case "all_of", "any_of", "sequence" -> {
                children = children(args, context, factory, findings);
                if (children.isEmpty()) {
                    error(findings, Codes.documentField, name + " needs at least one child", factory.source(), null);
                    return null;
                }
                if (!subjectsAgree(children, factory, findings)) {
                    return null;
                }
                Expectation<?>[] array = children.toArray(new Expectation<?>[0]);
                return switch (name) {
                    case "all_of" -> Expectations.allOf((Expectation[]) array);
                    case "any_of" -> Expectations.anyOf((Expectation[]) array);
                    default -> Expectations.sequence((Expectation[]) array);
                };
            }
            case "not" -> {
                Value child = args.get("child");
                if (child == null) {
                    error(findings, Codes.documentField, "not needs a child", factory.source(), null);
                    return null;
                }
                Expectation<?> compiled = expectation(context, kindOf(child), child, factory, findings);
                return compiled == null ? null : Expectations.not((Expectation) compiled);
            }
            case "each_cell" -> {
                Value element = args.get("element");
                if (element == null) {
                    error(findings, Codes.documentField, "each_cell needs an element", factory.source(), null);
                    return null;
                }
                Expectation<?> compiled = expectation(context, kindOf(element), element, factory, findings);
                if (compiled == null) {
                    return null;
                }
                if (!compiled.subject().equals(OccupantSnapshot.class)) {
                    error(
                        findings,
                        Codes.documentField,
                        "each_cell needs an OccupantSnapshot expectation",
                        factory.source(),
                        null
                    );
                    return null;
                }
                return Expectations.eachCell((Expectation<OccupantSnapshot>) compiled);
            }
            case "at_least", "at_most", "exactly" -> {
                Value count = args.get("count");
                long n = count == null ? -1 : count.asNumber(-1);
                if (n < 0 || n > Integer.MAX_VALUE) {
                    error(findings, Codes.documentField, name + " needs a count", factory.source(), null);
                    return null;
                }
                children = children(args, context, factory, findings);
                if (children.isEmpty()) {
                    error(findings, Codes.documentField, name + " needs children", factory.source(), null);
                    return null;
                }
                if (!subjectsAgree(children, factory, findings)) {
                    return null;
                }
                Expectation<?>[] array = children.toArray(new Expectation<?>[0]);
                return switch (name) {
                    case "at_least" -> Expectations.atLeast((int) n, (Expectation[]) array);
                    case "at_most" -> Expectations.atMost((int) n, (Expectation[]) array);
                    default -> Expectations.exactly((int) n, (Expectation[]) array);
                };
            }
            default -> {
                ExpectationFactory registered = lookup(context.expectations(), kind);
                if (registered == null) {
                    error(findings, Codes.documentFactory, "no expectation factory " + kind, factory.source(), null);
                    return null;
                }
                try {
                    Expectation<?> compiled = registered.create(args, factory);
                    if (compiled == null) {
                        error(
                            findings,
                            Codes.factoryBad,
                            "expectation factory " + kind + " returned null",
                            factory.source(),
                            null
                        );
                    }
                    return compiled;
                } catch (RuntimeException failure) {
                    error(
                        findings,
                        Codes.factoryBad,
                        "expectation factory " + kind + " failed: " + failure.getMessage(),
                        factory.source(),
                        null
                    );
                    return null;
                }
            }
        }
    }

    private static boolean subjectsAgree(
        List<Expectation<?>> children,
        FactoryContext factory,
        List<Finding> findings
    ) {
        Class<?> subject = children.get(0).subject();
        for (Expectation<?> child : children) {
            if (!child.subject().equals(subject)) {
                error(
                    findings,
                    Codes.documentField,
                    "children of one combinator disagree on subject: " + subject.getName() + " vs "
                            + child.subject().getName(),
                    factory.source(),
                    null
                );
                return false;
            }
        }
        return true;
    }

    private static List<Expectation<?>> children(
        Value args,
        CompilerContext context,
        FactoryContext factory,
        List<Finding> findings
    ) {
        Value array = args.get("children");
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<Expectation<?>> children = new ArrayList<>();
        for (Value child : array.elements()) {
            Expectation<?> compiled = expectation(context, kindOf(child), child, factory, findings);
            if (compiled != null) {
                children.add(compiled);
            }
        }
        return children;
    }

    private static NamespacedId kindOf(Value value) {
        Value kindValue = value.get("kind");
        String kind = kindValue instanceof Value.StringValue stringValue ? stringValue.value() : null;
        if (kind == null) {
            return NamespacedId.of("missing_kind");
        }
        NamespacedId parsed = NamespacedId.parse(kind);
        return parsed == null ? NamespacedId.of("missing_kind") : parsed;
    }

    private static List<Computation<?>> buildComputations(
        List<ComputationSpec> specs,
        Map<NamespacedId, Bay> bays,
        Map<NamespacedId, ContextKey<?>> keys,
        Document document,
        CompilerContext context,
        List<Finding> findings
    ) {
        List<Computation<?>> computations = new ArrayList<>();
        Set<NamespacedId> ids = new HashSet<>();
        for (ComputationSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.documentDuplicate, "duplicate computation id " + spec.id(), spec.source(), null);
                continue;
            }
            ContextKey<?> output = keys.get(spec.output());
            if (output == null) {
                error(
                    findings,
                    Codes.keyUndeclared,
                    "computation " + spec.id() + " names undeclared key " + spec.output(),
                    spec.source(),
                    null
                );
                continue;
            }
            if (spec.bay() != null && spec.region() != null) {
                error(
                    findings,
                    Codes.documentField,
                    "computation " + spec.id() + " declares both a bay and a region",
                    spec.source(),
                    null
                );
                continue;
            }
            CuboidSet region;
            if (spec.bay() != null) {
                Bay bay = bays.get(spec.bay());
                if (bay == null) {
                    error(
                        findings,
                        Codes.bayUnresolved,
                        "computation " + spec.id() + " names no merged bay " + spec.bay(),
                        spec.source(),
                        null
                    );
                    continue;
                }
                region = bay.cells();
            } else if (spec.region() != null) {
                region = spec.region();
            } else {
                error(
                    findings,
                    Codes.documentField,
                    "computation " + spec.id() + " needs a bay or a region",
                    spec.source(),
                    null
                );
                continue;
            }
            for (NamespacedId input : spec.inputs()) {
                if (!keys.containsKey(input)) {
                    error(
                        findings,
                        Codes.keyUndeclared,
                        "computation " + spec.id() + " reads undeclared key " + input,
                        spec.source(),
                        null
                    );
                }
            }
            ComputationFactory factory = lookup(context.computations(), spec.kind());
            if (factory == null) {
                error(findings, Codes.documentFactory, "no computation factory " + spec.kind(), spec.source(), null);
                continue;
            }
            FactoryContext fctx = new FactoryContext(document.root().id(), spec.source(), keys::get, findings::add);
            Computation<?> created;
            try {
                created = factory.create(spec.args(), output, region, fctx);
            } catch (RuntimeException failure) {
                error(
                    findings,
                    Codes.factoryBad,
                    "computation factory " + spec.kind() + " failed: " + failure.getMessage(),
                    spec.source(),
                    null
                );
                continue;
            }
            if (created == null) {
                error(
                    findings,
                    Codes.factoryBad,
                    "computation factory " + spec.kind() + " returned null",
                    spec.source(),
                    null
                );
                continue;
            }
            if (!created.output().equals(output)) {
                error(
                    findings,
                    Codes.documentDeclared,
                    "computation factory " + spec.kind() + " produced " + created.output().id() + ", not "
                            + output.id(),
                    spec.source(),
                    null
                );
                continue;
            }
            Set<ContextKey<?>> resolvedInputs = inputKeys(spec.inputs(), keys);
            if (!spec.id().equals(spec.output())) {
                error(
                    findings,
                    Codes.documentDeclared,
                    "a computation's id is its output key: " + spec.id() + " != " + spec.output(),
                    spec.source(),
                    null
                );
                continue;
            }
            if (!created.inputs().equals(resolvedInputs)) {
                error(
                    findings,
                    Codes.documentDeclared,
                    "computation " + spec.output() + " declares inputs " + spec.inputs() + " but its factory declares "
                            + created.inputs(),
                    spec.source(),
                    null
                );
                continue;
            }
            computations.add(created);
        }
        return computations;
    }

    private static Set<ContextKey<?>> inputKeys(Set<NamespacedId> ids, Map<NamespacedId, ContextKey<?>> keys) {
        Set<ContextKey<?>> resolved = new HashSet<>();
        for (NamespacedId id : ids) {
            ContextKey<?> key = keys.get(id);
            if (key != null) {
                resolved.add(key);
            }
        }
        return Set.copyOf(resolved);
    }

    private static List<DerivationSpec> buildDerivations(
        List<DerivationSpec> specs,
        Map<NamespacedId, ContextKey<?>> keys,
        List<Finding> findings,
        @Nullable Locus fallback
    ) {
        List<DerivationSpec> derivations = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        Set<NamespacedId> outputs = new HashSet<>();
        for (DerivationSpec spec : specs) {
            if (!keys.containsKey(spec.output())) {
                error(
                    findings,
                    Codes.keyUndeclared,
                    "derivation " + spec.deriver() + " names undeclared key " + spec.output(),
                    null,
                    fallback
                );
                continue;
            }
            if (!orders.add(spec.order())) {
                error(findings, Codes.deriveDuplicate, "duplicate derivation order " + spec.order(), null, fallback);
                continue;
            }
            if (!outputs.add(spec.output())) {
                error(findings, Codes.deriveDuplicate, "two derivations produce " + spec.output(), null, fallback);
                continue;
            }
            derivations.add(spec);
        }
        return derivations;
    }

    private static <T> @Nullable T lookup(Catalog<T> catalog, NamespacedId id) {
        return catalog.get(id);
    }

    /** Grammar is the default namespace's; a qualified id is a catalog kind. */
    private static @Nullable String grammar(NamespacedId kind) {
        return kind.namespace().equals(NamespacedId.defaultNamespace) ? kind.path() : null;
    }

    private static @Nullable Locus documentLocus(Document document) {
        if (document.root().source() != null) {
            return Locus.definition(document.root().source());
        }
        CuboidSet cells = document.root().bounds().asSet();
        return cells.isEmpty() ? null : Locus.local(cells);
    }

    private static CodeEntry codeFromMessage(@Nullable String message) {
        if (message != null) {
            int space = message.indexOf(' ');
            String text = space < 0 ? message : message.substring(0, space);
            while (!text.isEmpty() && ":;,.".indexOf(text.charAt(text.length() - 1)) >= 0) {
                text = text.substring(0, text.length() - 1);
            }
            FindingCode code = FindingCode.parse(text);
            for (CodeEntry entry : Codes.all) {
                if (entry.code().equals(code)) {
                    return entry;
                }
            }
        }
        return Codes.blueprintInvalid;
    }

    private static @Nullable CuboidSet translate(CuboidSet cells, LatticePoint offset) {
        return cells.translate(offset.x(), offset.y(), offset.z());
    }

    private static @Nullable Footprint translate(Footprint footprint, LatticePoint offset) {
        return switch (footprint) {
            case Footprint.Declared declared -> {
                CuboidSet translated = translate(declared.cells(), offset);
                yield translated == null ? null : new Footprint.Declared(translated);
            }
            case Footprint.Box ignored -> Footprint.box();
            case Footprint.Bound ignored -> Footprint.bound();
            case Footprint.None ignored -> Footprint.none();
        };
    }

    private static NamespacedId qualify(String prefix, NamespacedId id) {
        if (prefix.isEmpty()) {
            return id;
        }
        return NamespacedId.of(id.namespace(), prefix + id.path());
    }

    private static void error(
        List<Finding> findings,
        CodeEntry entry,
        String message,
        @Nullable SourceSpan source,
        @Nullable Locus locus
    ) {
        Finding.Builder builder = Finding.builder(entry).phase(Phase.compile).message(message);
        if (source != null) {
            builder.label(Label.primary(Locus.definition(source), message));
        } else if (locus != null) {
            builder.label(Label.primary(locus, message));
        }
        findings.add(builder.build());
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private DocumentCompiler() {}
}
