package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** The shipped combinators and element tests; each returns one {@code Expectation} of one subject type. */
public final class Expectations {

    static final FindingCode TYPE_MISMATCH = FindingCode.of(FindingCode.Family.EXP, 1);
    static final FindingCode NEGATED_MATCH = FindingCode.of(FindingCode.Family.EXP, 2);
    static final FindingCode THRESHOLD = FindingCode.of(FindingCode.Family.EXP, 4);

    private static final NamespacedId BLOCK = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "block");
    private static final NamespacedId AIR = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "air");
    private static final NamespacedId ALL_OF = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "all_of");
    private static final NamespacedId ANY_OF = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "any_of");
    private static final NamespacedId NOT = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "not");
    private static final NamespacedId AT_LEAST = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "at_least");
    private static final NamespacedId EACH_CELL = NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "each_cell");

    private static final int ELEMENT_SPECIFICITY = 50;

    private static final Class<OccupantSnapshot> OCCUPANT = OccupantSnapshot.class;
    private static final Class<BayView> BAY = BayView.class;

    private Expectations() {
    }

    public static Expectation<OccupantSnapshot> block(NamespacedId type) {
        Objects.requireNonNull(type, "type");
        ExpectationMeta meta = new ExpectationMeta(BLOCK, ELEMENT_SPECIFICITY, Set.of(), TYPE_MISMATCH);
        return new Built<>(OCCUPANT, meta, ReadScope.none(), ctx -> {
            OccupantSnapshot found = ctx.subject();
            if (found.descriptor().type().equals(type)) {
                return Reply.match();
            }
            return Reply.mismatch(finding(meta.mismatchCode(), ctx,
                    "expected " + type + ", found " + found.descriptor().type() + " at " + found.ref().anchor()));
        });
    }

    public static Expectation<OccupantSnapshot> air() {
        ExpectationMeta meta = new ExpectationMeta(AIR, ELEMENT_SPECIFICITY, Set.of(), TYPE_MISMATCH);
        return new Built<>(OCCUPANT, meta, ReadScope.none(), ctx -> {
            OccupantSnapshot found = ctx.subject();
            if (found.isAir()) {
                return Reply.match();
            }
            return Reply.mismatch(finding(meta.mismatchCode(), ctx,
                    "expected " + OccupantSnapshot.AIR + ", found " + found.descriptor().type()
                            + " at " + found.ref().anchor()));
        });
    }

    @SafeVarargs
    public static <T> Expectation<T> allOf(Expectation<T>... children) {
        Expectation<T>[] body = children.clone();
        ExpectationMeta meta = new ExpectationMeta(ALL_OF, maxSpecificity(body), requiresOf(body), TYPE_MISMATCH);
        return new Built<>(subjectOf(body), meta, readsOf(body), ctx -> {
            Bindings bindings = Bindings.EMPTY;
            Reply firstUnreadable = null;
            boolean abstained = false;
            for (Expectation<T> child : body) {
                Reply reply = child.test(ctx);
                if (reply instanceof Reply.Mismatch) {
                    return reply;
                }
                if (reply instanceof Reply.Unreadable unreadable) {
                    if (firstUnreadable == null) {
                        firstUnreadable = unreadable;
                    }
                } else if (reply instanceof Reply.Abstain) {
                    abstained = true;
                } else if (reply instanceof Reply.Match match) {
                    Reply merged = bindings.merge(match.bindings());
                    if (merged instanceof Reply.Mismatch) {
                        return merged;
                    }
                    bindings = ((Reply.Match) merged).bindings();
                }
            }
            if (firstUnreadable != null) {
                return firstUnreadable;
            }
            return abstained ? Reply.abstain() : Reply.match(bindings);
        });
    }

    @SafeVarargs
    public static <T> Expectation<T> anyOf(Expectation<T>... children) {
        Expectation<T>[] body = children.clone();
        ExpectationMeta meta = new ExpectationMeta(ANY_OF, minSpecificity(body), requiresOf(body), TYPE_MISMATCH);
        return new Built<>(subjectOf(body), meta, readsOf(body), ctx -> {
            Reply firstMismatch = null;
            Reply firstUnreadable = null;
            boolean abstained = false;
            for (Expectation<T> child : body) {
                Reply reply = child.test(ctx);
                if (reply instanceof Reply.Match) {
                    return reply;
                }
                if (reply instanceof Reply.Mismatch mismatch) {
                    if (firstMismatch == null) {
                        firstMismatch = mismatch;
                    }
                } else if (reply instanceof Reply.Unreadable unreadable) {
                    if (firstUnreadable == null) {
                        firstUnreadable = unreadable;
                    }
                } else {
                    abstained = true;
                }
            }
            if (firstUnreadable != null) {
                return firstUnreadable;
            }
            if (abstained) {
                return Reply.abstain();
            }
            return firstMismatch != null ? firstMismatch : Reply.abstain();
        });
    }

    public static <T> Expectation<T> not(Expectation<T> child) {
        Objects.requireNonNull(child, "child");
        ExpectationMeta meta = new ExpectationMeta(NOT, 0, child.meta().requires(), TYPE_MISMATCH);
        return new Built<>(child.subject(), meta, child.reads(), ctx -> {
            Reply reply = child.test(ctx);
            if (reply instanceof Reply.Match) {
                return Reply.mismatch(finding(NEGATED_MATCH, ctx, "the negated test matched"));
            }
            if (reply instanceof Reply.Mismatch) {
                return Reply.match();
            }
            return reply;
        });
    }

    @SafeVarargs
    public static <T> Expectation<T> atLeast(int k, Expectation<T>... children) {
        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative: " + k);
        }
        Expectation<T>[] body = children.clone();
        int n = body.length;
        int specificity = k >= n ? maxSpecificity(body) : minSpecificity(body);
        ExpectationMeta meta = new ExpectationMeta(AT_LEAST, specificity, requiresOf(body), TYPE_MISMATCH);
        return new Built<>(subjectOf(body), meta, readsOf(body), ctx -> {
            if (k > n) {
                return Reply.mismatch(finding(THRESHOLD, ctx,
                        "at least " + k + " of " + n + " tests cannot be met"));
            }
            Bindings bindings = Bindings.EMPTY;
            Reply firstUnreadable = null;
            int matched = 0;
            int unreadable = 0;
            int abstained = 0;
            for (Expectation<T> child : body) {
                Reply reply = child.test(ctx);
                if (reply instanceof Reply.Match match) {
                    matched++;
                    Reply merged = bindings.merge(match.bindings());
                    if (merged instanceof Reply.Mismatch) {
                        return merged;
                    }
                    bindings = ((Reply.Match) merged).bindings();
                } else if (reply instanceof Reply.Unreadable unreadableReply) {
                    unreadable++;
                    if (firstUnreadable == null) {
                        firstUnreadable = unreadableReply;
                    }
                } else if (reply instanceof Reply.Abstain) {
                    abstained++;
                }
            }
            if (matched >= k) {
                return Reply.match(bindings);
            }
            if (matched + unreadable + abstained < k) {
                return Reply.mismatch(finding(THRESHOLD, ctx,
                        "matched " + matched + " of " + n + ", needs " + k));
            }
            return firstUnreadable != null ? firstUnreadable : Reply.abstain();
        });
    }

    public static Expectation<BayView> eachCell(Expectation<OccupantSnapshot> cell) {
        Objects.requireNonNull(cell, "cell");
        ExpectationMeta cellMeta = cell.meta();
        ExpectationMeta meta = new ExpectationMeta(EACH_CELL, cellMeta.specificity(), cellMeta.requires(),
                cellMeta.mismatchCode());
        return new Built<>(BAY, meta, cell.reads(), ctx -> {
            Bindings bindings = Bindings.EMPTY;
            Reply firstUnreadable = null;
            boolean abstained = false;
            for (LatticePoint point : ctx.bay().points()) {
                OccupantSnapshot snapshot = ctx.bay().occupantAt(point);
                if (snapshot == null) {
                    if (firstUnreadable == null) {
                        String message = "no readable snapshot at " + point;
                        firstUnreadable = Reply.unreadable(Finding.builder(Focuses.UNREADABLE_CELL)
                                .severity(Severity.ERROR)
                                .message(message)
                                .label(Locus.world(CuboidSet.of(Cuboid.of(point))), message)
                                .stage(ctx.stage())
                                .build());
                    }
                    continue;
                }
                Reply reply = cell.test(ctx.withSubject(snapshot, subjectsOf(snapshot)));
                if (reply instanceof Reply.Mismatch) {
                    return reply;
                }
                if (reply instanceof Reply.Unreadable unreadable) {
                    if (firstUnreadable == null) {
                        firstUnreadable = unreadable;
                    }
                } else if (reply instanceof Reply.Abstain) {
                    abstained = true;
                } else if (reply instanceof Reply.Match match) {
                    Reply merged = bindings.merge(match.bindings());
                    if (merged instanceof Reply.Mismatch) {
                        return merged;
                    }
                    bindings = ((Reply.Match) merged).bindings();
                }
            }
            if (firstUnreadable != null) {
                return firstUnreadable;
            }
            return abstained ? Reply.abstain() : Reply.match(bindings);
        });
    }

    private static List<OccupantRef> subjectsOf(OccupantSnapshot snapshot) {
        return snapshot.isAir() ? List.of() : List.of(snapshot.ref());
    }

    private static Finding finding(FindingCode code, FocusContext ctx, String message) {
        return Finding.builder(code)
                .severity(Severity.ERROR)
                .message(message)
                .label(ctx.locus(), message)
                .stage(ctx.stage())
                .build();
    }

    private static <T> Class<T> subjectOf(Expectation<T>[] children) {
        return children.length == 0 ? anySubject() : children[0].subject();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> anySubject() {
        return (Class<T>) (Class<?>) Object.class;
    }

    private static Set<ContextKey<?>> requiresOf(Expectation<?>[] children) {
        Set<ContextKey<?>> requires = new TreeSet<>();
        for (Expectation<?> child : children) {
            requires.addAll(child.meta().requires());
        }
        return requires;
    }

    private static ReadScope readsOf(Expectation<?>[] children) {
        ReadScope scope = ReadScope.none();
        for (Expectation<?> child : children) {
            scope = scope.union(child.reads());
        }
        return scope;
    }

    private static int maxSpecificity(Expectation<?>[] children) {
        int max = 0;
        for (Expectation<?> child : children) {
            max = Math.max(max, child.meta().specificity());
        }
        return max;
    }

    private static int minSpecificity(Expectation<?>[] children) {
        int min = Integer.MAX_VALUE;
        for (Expectation<?> child : children) {
            min = Math.min(min, child.meta().specificity());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private record Built<T>(Class<T> subject, ExpectationMeta meta, ReadScope reads,
                            Function<ExpectationContext<T>, Reply> body) implements Expectation<T> {

        @Override
        public Reply test(ExpectationContext<T> ctx) {
            return body.apply(Objects.requireNonNull(ctx, "ctx"));
        }
    }
}
