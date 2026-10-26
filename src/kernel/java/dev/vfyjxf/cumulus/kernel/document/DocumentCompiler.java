package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.Merge;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
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
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
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
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.structure.SectionContext;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
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
        List<Finding> findings = new ArrayList<>();
        if (document.schemaVersion() != Document.CURRENT_VERSION) {
            error(findings, Codes.SCHEMA_VERSION,
                    "unsupported schema version " + document.schemaVersion(), null, null);
            return new CompileResult(FindingBundle.of(findings), null);
        }
        Flat flat = new Flat(document.root());
        Set<NamespacedId> active = new HashSet<>();
        flatten(document.root(), document, context, "", LatticePoint.ORIGIN, flat, findings, active);
        if (hasErrors(findings)) {
            return new CompileResult(FindingBundle.of(findings), null);
        }

        Map<NamespacedId, ContextKey<?>> keys = buildKeys(flat.keys, context, findings);
        Map<NamespacedId, Bay> bays = buildBays(flat.bays, flat.bounds, findings);
        List<Seam> seams = buildSeams(flat.seams, findings);
        List<Check<?>> checks = buildChecks(flat.checks, bays, keys, document, context, findings);
        List<Computation<?>> computations = buildComputations(flat.computations, bays, keys,
                document, context, findings);
        List<DerivationSpec> derivations = buildDerivations(flat.derivations, keys, findings);
        if (hasErrors(findings)) {
            return new CompileResult(FindingBundle.of(findings), null);
        }

        BlueprintBuilder builder =
                Blueprint.builder(document.root().id())
                        .bounds(flat.bounds)
                        .checks(checks)
                        .computations(computations)
                        .seams(seams)
                        .anchors(flat.anchors)
                        .derivations(derivations)
                        .arguments(document.root().arguments());
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
            error(findings, Codes.BLUEPRINT_INVALID,
                    "the compiled blueprint is invalid: " + rejected.getMessage(),
                    document.root().source(), null);
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
        private final List<AnchorSpec> anchors =
                new ArrayList<>();
        private @Nullable OrientationPolicy orientation;

        private Flat(BlueprintSpec root) {
            this.bounds = root.bounds();
            this.anchors.addAll(root.anchors());
            this.orientation = root.orientation();
        }
    }

    private static void flatten(BlueprintSpec spec, Document document, CompilerContext context,
                                String prefix, LatticePoint offset, Flat flat, List<Finding> findings,
                                Set<NamespacedId> active) {
        if (!active.add(spec.id())) {
            error(findings, Codes.DEFINITION_CYCLE,
                    "definition cycle at " + spec.id(), spec.source(), null);
            return;
        }
        if (!prefix.isEmpty()) {
            if (!spec.checks().isEmpty() || !spec.computations().isEmpty()
                    || !spec.keys().isEmpty() || !spec.derivations().isEmpty()
                    || !spec.anchors().isEmpty() || spec.orientation() != null) {
                error(findings, Codes.DOCUMENT_FIELD,
                        "a definition may contribute only bays, seams and placements",
                        spec.source(), null);
                active.remove(spec.id());
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
                error(findings, Codes.PLACEMENT_OUTSIDE,
                        "bay " + bay.id() + " leaves the lattice", bay.source(), null);
                continue;
            }
            Footprint footprint = translate(bay.footprint(), offset);
            if (footprint == null) {
                error(findings, Codes.PLACEMENT_OUTSIDE,
                        "the footprint of " + bay.id() + " leaves the lattice", bay.source(), null);
                continue;
            }
            flat.bays.add(new BaySpec(qualify(prefix, bay.id()), translated, bay.coverage(),
                    footprint, bay.sharing(), bay.description(), bay.source()));
        }
        for (SeamSpec seam : spec.seams()) {
            Cuboid translated = seam.cells().translate(offset.x(), offset.y(), offset.z());
            if (translated == null) {
                error(findings, Codes.PLACEMENT_OUTSIDE,
                        "seam " + seam.id() + " leaves the lattice", seam.source(), null);
                continue;
            }
            flat.seams.add(new SeamSpec(new SeamId(qualify(prefix, seam.id().id())), translated,
                    seam.kind(), seam.properties(), seam.source()));
        }
        for (PlacementSpec placement : spec.placements()) {
            BlueprintSpec definition = document.definitions().get(placement.definition());
            if (definition == null) {
                Document external = context.documents().find(placement.definition());
                definition = external == null ? null : external.root();
            }
            if (definition == null) {
                error(findings, Codes.DEFINITION_MISSING,
                        "no definition " + placement.definition(), placement.source(), null);
                continue;
            }
            LatticePoint next = offset.offset(placement.offset().x(), placement.offset().y(),
                    placement.offset().z());
            if (next == null) {
                error(findings, Codes.PLACEMENT_OUTSIDE,
                        "placement leaves the lattice", placement.source(), null);
                continue;
            }
            String nextPrefix = prefix.isEmpty()
                    ? placement.alias().path() + "/"
                    : prefix + placement.alias().path() + "/";
            flatten(definition, document, context, nextPrefix, next, flat, findings, active);
        }
        active.remove(spec.id());
    }

    private static Map<NamespacedId, ContextKey<?>> buildKeys(List<KeySpec> specs,
                                                              CompilerContext context,
                                                              List<Finding> findings) {
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
            Object absent = spec.whenAbsent() == null ? null
                    : decode(spec.whenAbsent(), type, spec, findings);
            if (spec.whenAbsent() != null && absent == null) {
                continue;
            }
            ContextKey<?> key = rawKey(spec, type, merge, absent);
            ContextKey<?> previous = keys.putIfAbsent(key.id(), key);
            if (previous != null && !previous.equals(key)) {
                error(findings, Codes.DOCUMENT_DUPLICATE,
                        "key " + key.id() + " is declared twice with different content",
                        spec.source(), null);
            }
        }
        return keys;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ContextKey<?> rawKey(KeySpec spec, Class<?> type, @Nullable Merge<?> merge,
                                        @Nullable Object absent) {
        return ContextKeys.of(spec.id(), (Class) type, spec.retention(), (Merge) merge, absent);
    }

    private static @Nullable Class<?> type(String name, CompilerContext context, KeySpec spec,
                                           List<Finding> findings) {
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
            error(findings, Codes.KEY_TYPE, "unknown key type " + name, spec.source(), null);
        }
        return custom;
    }

    private static @Nullable Merge<?> merge(@Nullable NamespacedId merge, Class<?> type,
                                            KeySpec spec, List<Finding> findings) {
        if (merge == null) {
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
            error(findings, Codes.KEY_TYPE,
                    "merge " + merge + " does not fit type " + type.getName(), spec.source(), null);
            return null;
        }
        return (Merge<?>) candidate;
    }

    private static @Nullable Object decode(Value value, Class<?> type, KeySpec spec,
                                           List<Finding> findings) {
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
            String text = value.asString(null);
            if (text != null) {
                return text;
            }
        } else if (type.equals(NamespacedId.class)) {
            NamespacedId id = NamespacedId.parse(value.asString(null));
            if (id != null) {
                return id;
            }
        }
        error(findings, Codes.KEY_TYPE,
                "whenAbsent does not fit key " + spec.id(), spec.source(), null);
        return null;
    }

    private static Map<NamespacedId, Bay> buildBays(List<BaySpec> specs, Cuboid bounds,
                                                    List<Finding> findings) {
        Map<NamespacedId, Bay> bays = new LinkedHashMap<>();
        CuboidSet boundsSet = bounds.asSet();
        for (BaySpec spec : specs) {
            if (!boundsSet.containsAll(spec.cells())) {
                error(findings, Codes.PLACEMENT_OUTSIDE,
                        "bay " + spec.id() + " leaves the blueprint bounds", spec.source(),
                        Locus.local(spec.cells()));
                continue;
            }
            try {
                Bay bay = new Bay(spec.id(), spec.cells(), spec.coverage(), spec.footprint(),
                        spec.sharing(), spec.description());
                Bay previous = bays.putIfAbsent(bay.id(), bay);
                if (previous != null && !previous.equals(bay)) {
                    error(findings, Codes.DOCUMENT_DUPLICATE,
                            "two different bays share id " + bay.id(), spec.source(), null);
                }
            } catch (IllegalArgumentException rejected) {
                error(findings, Codes.DOCUMENT_FIELD, rejected.getMessage(), spec.source(), null);
            }
        }
        return bays;
    }

    private static List<Seam> buildSeams(List<SeamSpec> specs, List<Finding> findings) {
        List<Seam> seams = new ArrayList<>();
        Set<SeamId> ids = new HashSet<>();
        for (SeamSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.DOCUMENT_DUPLICATE,
                        "duplicate seam id " + spec.id(), spec.source(), null);
                continue;
            }
            seams.add(new Seam(spec.id(), spec.cells(), spec.kind(), spec.properties()));
        }
        return seams;
    }

    private static List<Check<?>> buildChecks(List<CheckSpec> specs, Map<NamespacedId, Bay> bays,
                                              Map<NamespacedId, ContextKey<?>> keys,
                                              Document document, CompilerContext context,
                                              List<Finding> findings) {
        List<Check<?>> checks = new ArrayList<>();
        Set<NamespacedId> ids = new HashSet<>();
        for (CheckSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.DOCUMENT_DUPLICATE,
                        "duplicate check id " + spec.id(), spec.source(), null);
                continue;
            }
            Bay bay = bays.get(spec.bay());
            if (bay == null) {
                error(findings, Codes.BAY_UNRESOLVED,
                        "check " + spec.id() + " names no merged bay " + spec.bay(),
                        spec.source(), null);
                continue;
            }
            FactoryContext factory = new FactoryContext(document.root().id(), spec.source(),
                    keys::get, findings::add);
            Focus<?> focus = focus(context, spec.focusKind(), spec.focusArgs(), factory, findings);
            if (focus == null) {
                continue;
            }
            Expectation<?> expectation = expectation(context, spec.expectationKind(),
                    spec.expectationArgs(), factory, findings);
            if (expectation == null) {
                continue;
            }
            try {
                checks.add(check(spec, bay, focus, expectation));
            } catch (IllegalArgumentException rejected) {
                error(findings, Codes.DOCUMENT_FIELD,
                        "check " + spec.id() + ": " + rejected.getMessage(), spec.source(), null);
            }
        }
        return checks;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Check<?> check(CheckSpec spec, Bay bay, Focus<?> focus,
                                  Expectation<?> expectation) {
        return Check.of(spec.id(), bay, (Focus) focus, (Expectation) expectation);
    }

    private static @Nullable Focus<?> focus(CompilerContext context, NamespacedId kind, Value args,
                                            FactoryContext factory, List<Finding> findings) {
        if (kind.path().equals("missing_kind")) {
            error(findings, Codes.FACTORY_UNKNOWN, "a focus has no kind", factory.source(), null);
            return null;
        }
        FocusFactory registered = lookup(context.focuses(), kind);
        if (registered == null) {
            error(findings, Codes.EXTENSION_UNKNOWN,
                    "no focus factory " + kind, factory.source(), null);
            return null;
        }
        Focus<?> focus;
        try {
            focus = registered.create(args, factory);
        } catch (RuntimeException failure) {
            error(findings, Codes.FACTORY_BAD,
                    "focus factory " + kind + " failed: " + failure.getMessage(),
                    factory.source(), null);
            return null;
        }
        if (focus == null) {
            error(findings, Codes.FACTORY_BAD, "focus factory " + kind + " returned null",
                    factory.source(), null);
        }
        return focus;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable Expectation<?> expectation(CompilerContext context, NamespacedId kind,
                                                        Value args, FactoryContext factory,
                                                        List<Finding> findings) {
        String name = kind.path();
        if (name.equals("missing_kind")) {
            error(findings, Codes.FACTORY_UNKNOWN, "an expectation has no kind", factory.source(),
                    null);
            return null;
        }
        List<Expectation<?>> children = new ArrayList<>();
        switch (name) {
            case "all_of", "any_of", "sequence" -> {
                children = children(args, context, factory, findings);
                if (children.isEmpty()) {
                    error(findings, Codes.DOCUMENT_FIELD,
                            name + " needs at least one child", factory.source(), null);
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
                    error(findings, Codes.DOCUMENT_FIELD, "not needs a child", factory.source(),
                            null);
                    return null;
                }
                Expectation<?> compiled = expectation(context, kindOf(child), child, factory,
                        findings);
                return compiled == null ? null : Expectations.not((Expectation) compiled);
            }
            case "each_cell" -> {
                Value element = args.get("element");
                if (element == null) {
                    error(findings, Codes.DOCUMENT_FIELD, "each_cell needs an element",
                            factory.source(), null);
                    return null;
                }
                Expectation<?> compiled = expectation(context, kindOf(element), element, factory,
                        findings);
                if (compiled == null) {
                    return null;
                }
                if (!compiled.subject().equals(OccupantSnapshot.class)) {
                    error(findings, Codes.DOCUMENT_FIELD,
                            "each_cell needs an OccupantSnapshot expectation", factory.source(),
                            null);
                    return null;
                }
                return Expectations.eachCell((Expectation<OccupantSnapshot>) compiled);
            }
            case "at_least", "at_most", "exactly" -> {
                Value count = args.get("count");
                long n = count == null ? -1 : count.asNumber(-1);
                if (n < 0 || n > Integer.MAX_VALUE) {
                    error(findings, Codes.DOCUMENT_FIELD, name + " needs a count",
                            factory.source(), null);
                    return null;
                }
                children = children(args, context, factory, findings);
                if (children.isEmpty()) {
                    error(findings, Codes.DOCUMENT_FIELD, name + " needs children",
                            factory.source(), null);
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
                    error(findings, Codes.EXTENSION_UNKNOWN,
                            "no expectation factory " + kind, factory.source(), null);
                    return null;
                }
                try {
                    Expectation<?> compiled = registered.create(args, factory);
                    if (compiled == null) {
                        error(findings, Codes.FACTORY_BAD,
                                "expectation factory " + kind + " returned null",
                                factory.source(), null);
                    }
                    return compiled;
                } catch (RuntimeException failure) {
                    error(findings, Codes.FACTORY_BAD,
                            "expectation factory " + kind + " failed: " + failure.getMessage(),
                            factory.source(), null);
                    return null;
                }
            }
        }
    }

    private static List<Expectation<?>> children(Value args, CompilerContext context,
                                                 FactoryContext factory, List<Finding> findings) {
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
        String kind = value.get("kind") == null ? null : value.get("kind").asString(null);
        if (kind == null) {
            return NamespacedId.of("missing_kind");
        }
        NamespacedId parsed = NamespacedId.parse(kind);
        return parsed == null ? NamespacedId.of("missing_kind") : parsed;
    }

    private static List<Computation<?>> buildComputations(List<ComputationSpec> specs,
                                                          Map<NamespacedId, Bay> bays,
                                                          Map<NamespacedId, ContextKey<?>> keys,
                                                          Document document,
                                                          CompilerContext context,
                                                          List<Finding> findings) {
        List<Computation<?>> computations = new ArrayList<>();
        Set<NamespacedId> ids = new HashSet<>();
        for (ComputationSpec spec : specs) {
            if (!ids.add(spec.id())) {
                error(findings, Codes.DOCUMENT_DUPLICATE,
                        "duplicate computation id " + spec.id(), spec.source(), null);
                continue;
            }
            ContextKey<?> output = keys.get(spec.output());
            if (output == null) {
                error(findings, Codes.KEY_UNDECLARED,
                        "computation " + spec.id() + " names undeclared key " + spec.output(),
                        spec.source(), null);
                continue;
            }
            if (spec.bay() != null && spec.region() != null) {
                error(findings, Codes.DOCUMENT_FIELD,
                        "computation " + spec.id() + " declares both a bay and a region",
                        spec.source(), null);
                continue;
            }
            CuboidSet region;
            if (spec.bay() != null) {
                Bay bay = bays.get(spec.bay());
                if (bay == null) {
                    error(findings, Codes.BAY_UNRESOLVED,
                            "computation " + spec.id() + " names no merged bay " + spec.bay(),
                            spec.source(), null);
                    continue;
                }
                region = bay.cells();
            } else if (spec.region() != null) {
                region = spec.region();
            } else {
                error(findings, Codes.DOCUMENT_FIELD,
                        "computation " + spec.id() + " needs a bay or a region", spec.source(),
                        null);
                continue;
            }
            for (NamespacedId input : spec.inputs()) {
                if (!keys.containsKey(input)) {
                    error(findings, Codes.KEY_UNDECLARED,
                            "computation " + spec.id() + " reads undeclared key " + input,
                            spec.source(), null);
                }
            }
            ComputationFactory factory = lookup(context.computations(), spec.kind());
            if (factory == null) {
                error(findings, Codes.EXTENSION_UNKNOWN,
                        "no computation factory " + spec.kind(), spec.source(), null);
                continue;
            }
            FactoryContext fctx = new FactoryContext(document.root().id(), spec.source(), keys::get,
                    findings::add);
            Computation<?> created;
            try {
                created = factory.create(spec.args(), output, region, fctx);
            } catch (RuntimeException failure) {
                error(findings, Codes.FACTORY_BAD,
                        "computation factory " + spec.kind() + " failed: " + failure.getMessage(),
                        spec.source(), null);
                continue;
            }
            if (created == null) {
                error(findings, Codes.FACTORY_BAD,
                        "computation factory " + spec.kind() + " returned null", spec.source(),
                        null);
                continue;
            }
            Set<ContextKey<?>> resolvedInputs = inputKeys(spec.inputs(), keys);
            computations.add(created.id().equals(spec.id())
                    && created.inputs().equals(resolvedInputs)
                    ? created
                    : wrap(created, spec.id(), resolvedInputs));
        }
        return computations;
    }

    private static Computation<?> wrap(Computation<?> computation, NamespacedId id,
                                       Set<ContextKey<?>> inputs) {
        if (computation instanceof SectionComputation<?> section) {
            return new WrappedSection(section, id, inputs);
        }
        if (computation instanceof AggregateComputation<?> aggregate) {
            return new WrappedAggregate(aggregate, id, inputs);
        }
        throw new IllegalArgumentException("unknown computation kind");
    }

    private record WrappedSection(SectionComputation<?> delegate, NamespacedId id,
                                  Set<ContextKey<?>> inputs) implements SectionComputation<Object> {
        private WrappedSection {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(id, "id");
            inputs = Set.copyOf(inputs);
        }

        @Override
        public Outcome<Object> compute(SectionContext ctx) {
            @SuppressWarnings("unchecked")
            Outcome<Object> result = (Outcome<Object>) delegate.compute(ctx);
            return result;
        }

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public ContextKey<Object> output() {
            @SuppressWarnings("unchecked")
            ContextKey<Object> output = (ContextKey<Object>) delegate.output();
            return output;
        }

        @Override
        public Set<ContextKey<?>> inputs() {
            return inputs;
        }

        @Override
        public CuboidSet region() {
            return delegate.region();
        }
    }

    private record WrappedAggregate(AggregateComputation<?> delegate, NamespacedId id,
                                    Set<ContextKey<?>> inputs)
            implements AggregateComputation<Object> {
        private WrappedAggregate {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(id, "id");
            inputs = Set.copyOf(inputs);
        }

        @Override
        public Outcome<Object> compute(AggregateContext ctx) {
            @SuppressWarnings("unchecked")
            Outcome<Object> result = (Outcome<Object>) delegate.compute(ctx);
            return result;
        }

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public ContextKey<Object> output() {
            @SuppressWarnings("unchecked")
            ContextKey<Object> output = (ContextKey<Object>) delegate.output();
            return output;
        }

        @Override
        public Set<ContextKey<?>> inputs() {
            return inputs;
        }

        @Override
        public CuboidSet region() {
            return delegate.region();
        }
    }

    private static Set<ContextKey<?>> inputKeys(Set<NamespacedId> ids,
                                                Map<NamespacedId, ContextKey<?>> keys) {
        Set<ContextKey<?>> resolved = new HashSet<>();
        for (NamespacedId id : ids) {
            ContextKey<?> key = keys.get(id);
            if (key != null) {
                resolved.add(key);
            }
        }
        return Set.copyOf(resolved);
    }

    private static List<DerivationSpec> buildDerivations(List<DerivationSpec> specs,
                                                         Map<NamespacedId, ContextKey<?>> keys,
                                                         List<Finding> findings) {
        List<DerivationSpec> derivations = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        Set<NamespacedId> outputs = new HashSet<>();
        for (DerivationSpec spec : specs) {
            if (!keys.containsKey(spec.output())) {
                error(findings, Codes.KEY_UNDECLARED,
                        "derivation " + spec.deriver() + " names undeclared key " + spec.output(),
                        null, null);
                continue;
            }
            if (!orders.add(spec.order())) {
                error(findings, Codes.DERIVE_DUPLICATE,
                        "duplicate derivation order " + spec.order(), null, null);
                continue;
            }
            if (!outputs.add(spec.output())) {
                error(findings, Codes.DERIVE_DUPLICATE,
                        "two derivations produce " + spec.output(), null, null);
                continue;
            }
            derivations.add(spec);
        }
        return derivations;
    }

    private static <T> @Nullable T lookup(Catalog<T> catalog, NamespacedId id) {
        T value = catalog.get(id);
        if (value == null) {
            value = catalog.get(NamespacedId.of("structurecumulus", id.path()));
        }
        return value;
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

    private static void error(List<Finding> findings, CodeEntry entry, String message,
                              @Nullable SourceSpan source, @Nullable Locus locus) {
        Finding.Builder builder = Finding.builder(entry).phase(Phase.COMPILE).message(message);
        if (source != null) {
            builder.label(Label.primary(Locus.definition(source), message));
        } else if (locus != null) {
            builder.label(Label.primary(locus, message));
        }
        findings.add(builder.build());
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    private DocumentCompiler() {
    }
}
