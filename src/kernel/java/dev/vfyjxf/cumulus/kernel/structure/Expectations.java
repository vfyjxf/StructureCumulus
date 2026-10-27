package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** The shipped leaf expectations and combinators. */
@SuppressWarnings("varargs")
public final class Expectations {

    public static Expectation<OccupantSnapshot> block(NamespacedId type) {
        return new Block(type);
    }

    public static Expectation<OccupantSnapshot> air() {
        return new Air(code(1), id("air"));
    }

    public static Expectation<OccupantSnapshot> envelopeAir() {
        return new Air(code(6), id("envelope_air"));
    }

    public static Expectation<OccupantSnapshot> property(String key, String value) {
        return new Property(key, value);
    }

    public static Expectation<OccupantSnapshot> trait(TraitKey<?> key) {
        return new HasTrait(key);
    }

    public static <T> Expectation<OccupantSnapshot> trait(TraitKey<T> key, Predicate<T> test) {
        return new TraitValue<>(key, test);
    }

    public static Expectation<OccupantSnapshot> traitAbsent(TraitKey<?> key) {
        return new TraitAbsent(key);
    }

    public static Expectation<OccupantSnapshot> anyOccupant() {
        return new AnyOccupant();
    }

    @SafeVarargs
    public static <T> Expectation<T> allOf(Expectation<T>... children) {
        return new Junction<>(Junction.Mode.all, List.of(children));
    }

    @SafeVarargs
    public static <T> Expectation<T> anyOf(Expectation<T>... children) {
        return new Junction<>(Junction.Mode.any, List.of(children));
    }

    @SafeVarargs
    public static <T> Expectation<T> sequence(Expectation<T>... children) {
        return new Junction<>(Junction.Mode.sequence, List.of(children));
    }

    public static <T> Expectation<T> not(Expectation<T> child) {
        return new Not<>(child);
    }

    @SafeVarargs
    public static <T> Expectation<T> atLeast(int count, Expectation<T>... children) {
        return new Threshold<>(Threshold.Mode.atLeast, count, List.of(children));
    }

    @SafeVarargs
    public static <T> Expectation<T> atMost(int count, Expectation<T>... children) {
        return new Threshold<>(Threshold.Mode.atMost, count, List.of(children));
    }

    @SafeVarargs
    public static <T> Expectation<T> exactly(int count, Expectation<T>... children) {
        return new Threshold<>(Threshold.Mode.exactly, count, List.of(children));
    }

    public static Expectation<BayView> eachCell(Expectation<OccupantSnapshot> element) {
        return new EachCell(element);
    }

    public static <T> Expectation<T> custom(
        Class<T> subject,
        ExpectationMeta meta,
        Function<ExpectationContext<T>, Reply> body
    ) {
        return new Custom<>(subject, meta, body);
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private static FindingCode code(int number) {
        return FindingCode.of(FindingCode.Family.exp, number);
    }

    private static Locus locusOf(FocusContext ctx, OccupantSnapshot occupant) {
        CuboidSet cells = CuboidSet.of(Cuboid.of(occupant.ref().anchor()));
        return ctx.view().frame() == 0 ? Locus.world(cells) : Locus.local(cells);
    }

    private static Finding problem(FocusContext ctx, OccupantSnapshot occupant, String message) {
        return problem(ctx, occupant, code(1), message);
    }

    private static Finding problem(
        FocusContext ctx,
        OccupantSnapshot occupant,
        FindingCode mismatchCode,
        String message
    ) {
        Locus locus = locusOf(ctx, occupant);
        return Finding.builder(mismatchCode, Severity.error).phase(ctx.phase()).message(message)
                .label(Label.primary(locus, message)).build();
    }

    private static Reply mismatch(Finding finding) {
        return Reply.mismatch(FindingBundle.of(List.of(finding)));
    }

    private static Reply traitUnreadable(
        FocusContext ctx,
        TraitKey<?> key,
        @Nullable ReadRefusal refusal,
        @Nullable String note
    ) {
        String message = "trait " + key.id() + " is unreadable";
        Finding finding = refusal == null
                ? Check.unreadable(ctx.phase(), ctx.locus(), message, note)
                : Check.relay(ctx.phase(), refusal, ctx.locus(), message);
        return Reply.unreadable(FindingBundle.of(List.of(finding)));
    }

    private record Block(NamespacedId type) implements Expectation<OccupantSnapshot> {

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            OccupantSnapshot subject = ctx.subject();
            if (subject.descriptor().type().equals(type)) {
                return Reply.match();
            }
            return mismatch(problem(ctx, subject, "expected " + type + ", found " + subject.descriptor().type()));
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("block"), 10, code(1));
        }
    }

    private record Air(FindingCode mismatchCode, NamespacedId metaId) implements Expectation<OccupantSnapshot> {

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            if (ctx.subject().isAir()) {
                return Reply.match();
            }
            return mismatch(
                problem(ctx, ctx.subject(), mismatchCode, "expected air, found " + ctx.subject().descriptor().type())
            );
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(metaId, 10, mismatchCode);
        }
    }

    private record Property(String key, String value) implements Expectation<OccupantSnapshot> {

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            String found = ctx.subject().descriptor().property(key);
            if (Objects.equals(value, found)) {
                return Reply.match();
            }
            return mismatch(
                problem(
                    ctx,
                    ctx.subject(),
                    "expected " + key + "=" + value + ", found " + (found == null ? "nothing" : found)
                )
            );
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("property"), 10, code(1));
        }
    }

    private record HasTrait(TraitKey<?> key) implements Expectation<OccupantSnapshot> {

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            TraitSlot slot = ctx.subject().slot(key.id());
            if (slot instanceof TraitSlot.Present) {
                return Reply.match();
            }
            if (slot instanceof TraitSlot.Absent) {
                return mismatch(problem(ctx, ctx.subject(), "expected trait " + key.id()));
            }
            if (slot instanceof TraitSlot.Refused refused) {
                return traitUnreadable(ctx, key, refused.refusal(), null);
            }
            return traitUnreadable(ctx, key, null, "the trait was not captured");
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("trait"), 10, code(1));
        }

        @Override
        public ReadScope reads() {
            return ReadScope.none().withTraits(Set.of(key.id()));
        }
    }

    private record TraitValue<T>(TraitKey<T> key, Predicate<T> test) implements Expectation<OccupantSnapshot> {

        private TraitValue {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(test, "test");
        }

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            TraitSlot slot = ctx.subject().slot(key.id());
            if (slot instanceof TraitSlot.Present present) {
                Object value = present.trait().value();
                if (value == null) {
                    return Reply.match();
                }
                if (!key.type().isInstance(value)) {
                    return traitUnreadable(ctx, key, null, "the captured value is not a " + key.type().getName());
                }
                @SuppressWarnings("unchecked")
                T typed = (T) value;
                if (test.test(typed)) {
                    return Reply.match();
                }
                return mismatch(problem(ctx, ctx.subject(), "trait " + key.id() + " did not satisfy"));
            }
            if (slot instanceof TraitSlot.Absent) {
                return mismatch(problem(ctx, ctx.subject(), "expected trait " + key.id()));
            }
            if (slot instanceof TraitSlot.Refused refused) {
                return traitUnreadable(ctx, key, refused.refusal(), null);
            }
            return traitUnreadable(ctx, key, null, "the trait was not captured");
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("trait"), 10, code(1));
        }

        @Override
        public ReadScope reads() {
            return ReadScope.none().withTraits(Set.of(key.id()));
        }
    }

    private record TraitAbsent(TraitKey<?> key) implements Expectation<OccupantSnapshot> {

        private TraitAbsent {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            TraitSlot slot = ctx.subject().slot(key.id());
            if (slot instanceof TraitSlot.Absent) {
                return Reply.match();
            }
            if (slot instanceof TraitSlot.Present) {
                return mismatch(problem(ctx, ctx.subject(), "expected no trait " + key.id()));
            }
            if (slot instanceof TraitSlot.Refused refused) {
                return traitUnreadable(ctx, key, refused.refusal(), null);
            }
            return traitUnreadable(ctx, key, null, "the trait was not captured");
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("trait_absent"), 10, code(1));
        }

        @Override
        public ReadScope reads() {
            return ReadScope.none().withTraits(Set.of(key.id()));
        }
    }

    private record AnyOccupant() implements Expectation<OccupantSnapshot> {

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            return Reply.match();
        }

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id("any_occupant"), 0, code(1));
        }
    }

    private record Junction<T>(Mode mode, List<Expectation<T>> children) implements Expectation<T> {

        private enum Mode {
            all, any, sequence
        }

        private Junction {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(children, "children");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("a combinator needs at least one child");
            }
            Class<T> subject = children.get(0).subject();
            for (Expectation<T> child : children) {
                if (!child.subject().equals(subject)) {
                    throw new IllegalArgumentException("combinator children must share a subject");
                }
            }
            children = List.copyOf(children);
        }

        @Override
        public Reply test(ExpectationContext<T> ctx) {
            List<Finding> findings = new ArrayList<>();
            boolean matched = false;
            boolean mismatched = false;
            boolean unreadable = false;
            boolean abstained = false;
            for (Expectation<T> child : children) {
                Reply reply = child.test(ctx);
                switch (reply) {
                    case Reply.Match ignored -> matched = true;
                    case Reply.Mismatch mismatch -> {
                        mismatched = true;
                        findings.addAll(mismatch.findings().findings());
                    }
                    case Reply.Unreadable unknown -> {
                        unreadable = true;
                        findings.addAll(unknown.findings().findings());
                    }
                    case Reply.Abstain ignored -> abstained = true;
                }
            }
            if (mode == Mode.any) {
                if (matched) {
                    return Reply.match();
                }
                if (unreadable) {
                    return Reply.unreadable(FindingBundle.of(findings));
                }
                if (mismatched) {
                    return Reply.mismatch(FindingBundle.of(findings));
                }
                return Reply.abstain();
            }
            if (mismatched) {
                return Reply.mismatch(FindingBundle.of(findings));
            }
            if (unreadable) {
                return Reply.unreadable(FindingBundle.of(findings));
            }
            if (abstained) {
                return Reply.abstain();
            }
            return Reply.match();
        }

        @Override
        public Class<T> subject() {
            return children.get(0).subject();
        }

        @Override
        public ExpectationMeta meta() {
            int specificity = switch (mode) {
                case all, sequence -> children.stream().mapToInt(child -> child.meta().specificity()).max().orElse(0);
                case any -> children.stream().mapToInt(child -> child.meta().specificity()).min().orElse(0);
            };
            Set<ContextKey<?>> requires = new LinkedHashSet<>();
            for (Expectation<T> child : children) {
                requires.addAll(child.meta().requires());
            }
            String name = switch (mode) {
                case all -> "all_of";
                case any -> "any_of";
                case sequence -> "sequence";
            };
            return new ExpectationMeta(id(name), specificity, requires, code(1));
        }

        @Override
        public ReadScope reads() {
            ReadScope scope = ReadScope.none();
            for (Expectation<T> child : children) {
                scope = scope.union(child.reads());
            }
            return scope;
        }
    }

    private record Not<T>(Expectation<T> child) implements Expectation<T> {

        private Not {
            Objects.requireNonNull(child, "child");
        }

        @Override
        public Reply test(ExpectationContext<T> ctx) {
            return switch (child.test(ctx)) {
                case Reply.Match ignored -> {
                    Finding finding = Finding.builder(code(2), Severity.error).phase(ctx.phase())
                            .message("a negated test matched")
                            .label(Label.primary(ctx.locus(), "the negated test matched")).build();
                    yield mismatch(finding);
                }
                case Reply.Mismatch ignored -> Reply.match();
                case Reply.Unreadable unknown -> unknown;
                case Reply.Abstain ignored -> Reply.abstain();
            };
        }

        @Override
        public Class<T> subject() {
            return child.subject();
        }

        @Override
        public ExpectationMeta meta() {
            return new ExpectationMeta(id("not"), 0, child.meta().requires(), code(1));
        }

        @Override
        public ReadScope reads() {
            return child.reads();
        }
    }

    private record Threshold<T>(Mode mode, int count, List<Expectation<T>> children) implements Expectation<T> {

        private enum Mode {
            atLeast, atMost, exactly
        }

        private Threshold {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(children, "children");
            if (count < 0) {
                throw new IllegalArgumentException("count >= 0");
            }
            if (children.isEmpty()) {
                throw new IllegalArgumentException("a threshold needs at least one child");
            }
            Class<T> subject = children.get(0).subject();
            for (Expectation<T> child : children) {
                if (!child.subject().equals(subject)) {
                    throw new IllegalArgumentException("threshold children must share a subject");
                }
            }
            children = List.copyOf(children);
        }

        @Override
        public Reply test(ExpectationContext<T> ctx) {
            List<Finding> findings = new ArrayList<>();
            int matches = 0;
            int unreadable = 0;
            int abstained = 0;
            for (Expectation<T> child : children) {
                Reply reply = child.test(ctx);
                switch (reply) {
                    case Reply.Match ignored -> matches++;
                    case Reply.Mismatch mismatch -> findings.addAll(mismatch.findings().findings());
                    case Reply.Unreadable unknown -> {
                        unreadable++;
                        findings.addAll(unknown.findings().findings());
                    }
                    case Reply.Abstain ignored -> abstained++;
                }
            }
            boolean fail = switch (mode) {
                case atLeast -> matches + unreadable + abstained < count;
                case atMost -> matches > count;
                case exactly -> matches > count || matches + unreadable + abstained < count;
            };
            if (fail) {
                findings.add(
                    Finding.builder(code(3), Severity.error).phase(ctx.phase())
                            .message("threshold " + mode + "(" + count + ") cannot be met")
                            .label(Label.primary(ctx.locus(), "the threshold failed")).build()
                );
                return Reply.mismatch(FindingBundle.of(findings));
            }
            boolean canStill = switch (mode) {
                case atLeast -> matches >= count;
                case atMost -> matches + unreadable + abstained <= count;
                case exactly -> matches == count && unreadable + abstained == 0;
            };
            if (canStill) {
                return Reply.match();
            }
            if (unreadable > 0) {
                return Reply.unreadable(FindingBundle.of(findings));
            }
            return Reply.abstain();
        }

        @Override
        public Class<T> subject() {
            return children.get(0).subject();
        }

        @Override
        public ExpectationMeta meta() {
            Set<ContextKey<?>> requires = new LinkedHashSet<>();
            for (Expectation<T> child : children) {
                requires.addAll(child.meta().requires());
            }
            String name = switch (mode) {
                case atLeast -> "at_least";
                case atMost -> "at_most";
                case exactly -> "exactly";
            };
            return new ExpectationMeta(id(name), 0, requires, code(1));
        }

        @Override
        public ReadScope reads() {
            ReadScope scope = ReadScope.none();
            for (Expectation<T> child : children) {
                scope = scope.union(child.reads());
            }
            return scope;
        }
    }

    private record EachCell(Expectation<OccupantSnapshot> element) implements Expectation<BayView> {

        private EachCell {
            Objects.requireNonNull(element, "element");
        }

        @Override
        public Reply test(ExpectationContext<BayView> ctx) {
            List<Finding> findings = new ArrayList<>();
            Map<LatticePoint, ReadRefusal> unreadableCells = new TreeMap<>();
            boolean mismatched = false;
            boolean unreadable = false;
            boolean abstained = false;
            for (Cuboid box : ctx.cells().boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            LatticePoint cell = LatticePoint.of(x, y, z);
                            CellRead read = ctx.read(cell);
                            if (read instanceof CellRead.Unreadable refused) {
                                unreadable = true;
                                unreadableCells.put(cell, refused.refusal());
                                continue;
                            }
                            OccupantSnapshot occupant = ((CellRead.Found) read).occupant();
                            ExpectationContext<OccupantSnapshot> sub = ctx
                                    .withSubject(occupant, occupant.isAir() ? List.of() : List.of(occupant));
                            Reply reply = element.test(sub);
                            switch (reply) {
                                case Reply.Match ignored -> {}
                                case Reply.Mismatch mismatch -> {
                                    mismatched = true;
                                    findings.addAll(mismatch.findings().findings());
                                }
                                case Reply.Unreadable unknown -> {
                                    unreadable = true;
                                    findings.addAll(unknown.findings().findings());
                                }
                                case Reply.Abstain ignored -> abstained = true;
                            }
                        }
                    }
                }
            }
            if (!unreadableCells.isEmpty()) {
                findings.addAll(
                    Check.relayAll(ctx.phase(), unreadableCells, Locus::local, "required read unavailable").findings()
                );
            }
            if (mismatched) {
                return Reply.mismatch(FindingBundle.of(findings));
            }
            if (unreadable) {
                return Reply.unreadable(FindingBundle.of(findings));
            }
            if (abstained) {
                return Reply.abstain();
            }
            return Reply.match();
        }

        @Override
        public Class<BayView> subject() {
            return BayView.class;
        }

        @Override
        public ExpectationMeta meta() {
            return new ExpectationMeta(
                id("each_cell"),
                element.meta().specificity(),
                element.meta().requires(),
                element.meta().mismatchCode()
            );
        }

        @Override
        public ReadScope reads() {
            return element.reads();
        }
    }

    private record Custom<T>(Class<T> subject, ExpectationMeta meta, Function<ExpectationContext<T>, Reply> body)
            implements
                Expectation<T> {

        private Custom {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(meta, "meta");
            Objects.requireNonNull(body, "body");
        }

        @Override
        public Reply test(ExpectationContext<T> ctx) {
            return Objects.requireNonNull(body.apply(ctx), "custom expectation returned null");
        }

        @Override
        public Class<T> subject() {
            return subject;
        }

        @Override
        public ExpectationMeta meta() {
            return meta;
        }
    }

    private Expectations() {}
}
