# Module `kernel.core`

> Rank 0. Values with no kernel dependency: names, identity, time-free budget, typed keys, the neutral
> data tree, and the frozen catalog. If a type here needs `Cuboid` or `Finding`, it belongs in a
> higher package.

## 1. Scope

**Owns:** `NamespacedId`, `World`, `InstanceId`, `SnapshotId`, `OwnerId`, `Phase`, `SourceSpan`,
`Budget`, `Spend`, `ContextKey<T>`, `ContextKeys`, `Merge<T>`, `Retention`, `Outcome<T>`,
`ContextView`, `Catalog<T>`, `Value`, `Values`, `Checks`.

**Does not own:** geometry, occupants, findings, claims, blueprints, computation execution, document
parsing, catalogs of concrete extension families (only the generic `Catalog<T>`).

**Imports:** JDK and `org.jspecify` only.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `NamespacedId` | record | `ns:path`, the only naming type; ordered by namespace then path |
| `World` | record | one level, named by id |
| `InstanceId` | record | `(world, serial)`, one placed structure |
| `SnapshotId` | record | `(world, revision)`, one capture |
| `OwnerId` | record | who holds a claim |
| `Phase` | enum | `COMPILE`, `CHECK`, `SETTLE` |
| `SourceSpan` | record | half-open character range in one document |
| `Budget` | record | `(cells, sections)` ceiling |
| `Spend` | record | `(cells, sections)` consumed |
| `ContextKey<T>` | record | typed identity + retention + optional merge + optional absent value |
| `ContextKeys` | final class | the only key factory |
| `Merge<T>` | functional interface | associative fold, no identity |
| `Retention` | enum | `MATCH` (attempt only), `INSTANCE` (published) |
| `Outcome<T>` | sealed interface | `Found(value)` / `Missing` |
| `CancelToken` | functional interface | host-injected cooperative cancellation |
| `FindingCode` | record | `SC-<FAMILY>-<NNNN>`; identity only, shared by every module |
| `ContextView` | interface | typed read face over a key→value map |
| `Catalog<T>` | final class | frozen namespaced family, insertion ordered |
| `Value` | sealed interface | six scalar/aggregate nodes with optional source spans |
| `Values` | final class | content equality, canonical object order, construction helpers |
| `Checks` | final class | the only argument assertion helper |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.core;

public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {
    public static final String DEFAULT_NAMESPACE = "structurecumulus";
    public static NamespacedId of(String namespace, String path);
    public static NamespacedId of(String path);
    public static @Nullable NamespacedId parse(String text);
    public String text();
    @Override public int compareTo(NamespacedId other);
}

public record World(NamespacedId id) implements Comparable<World> {
    public static World of(NamespacedId id);
    @Override public int compareTo(World other);
}

public record InstanceId(World world, long serial) implements Comparable<InstanceId> {
    public InstanceId { if (serial < 1) throw new IllegalArgumentException("serial >= 1"); }
    @Override public int compareTo(InstanceId other);
}

public record SnapshotId(World world, long revision) implements Comparable<SnapshotId> {
    public SnapshotId { if (revision < 1) throw new IllegalArgumentException("revision >= 1"); }
    @Override public int compareTo(SnapshotId other);
}

public record OwnerId(NamespacedId id) {
    public static OwnerId of(NamespacedId id);
}

public enum Phase {
    COMPILE, CHECK, SETTLE;
    public int rank();
}

public record SourceSpan(NamespacedId document, int start, int end) {
    public SourceSpan {
        if (start < 0 || end < start) throw new IllegalArgumentException("0 <= start <= end");
    }
    public int length();
    public boolean contains(int offset);
}

public record Budget(int cells, int sections) {
    public static final Budget ZERO;
    public static final Budget UNBOUNDED;
    public Budget { if (cells < 0 || sections < 0) throw new IllegalArgumentException("non-negative"); }
    public static Budget of(int cells, int sections);
    public static Budget unbounded();
    public boolean admits(Spend spend);
}

public record Spend(int cells, int sections) {
    public static final Spend ZERO;
    public Spend { if (cells < 0 || sections < 0) throw new IllegalArgumentException("non-negative"); }
    public Spend plus(Spend other);
}

public enum Retention { MATCH, INSTANCE }

@FunctionalInterface
public interface Merge<T> {
    T combine(T left, T right);
}

public record ContextKey<T>(NamespacedId id, Class<T> type, Retention retention,
                            @Nullable Merge<T> merge, @Nullable T whenAbsent) {
    public ContextKey { /* non-null id, type, retention */ }
    public boolean contributed();
}

public final class ContextKeys {
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention);
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge);
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge, @Nullable T whenAbsent);
    private ContextKeys() {}
}

public sealed interface Outcome<T> permits Outcome.Found, Outcome.Missing {
    record Found<T>(T value) implements Outcome<T> {
        public Found { /* value non-null */ }
    }
    record Missing<T>() implements Outcome<T> {}

    <R> R fold(java.util.function.Function<T, R> onFound, java.util.function.Supplier<R> onMissing);
    default boolean isPresent();
    static <T> Outcome<T> found(T value);
    static <T> Outcome<T> missing();
}

@FunctionalInterface
public interface CancelToken {
    boolean isCancelled();
    static CancelToken never();
}

public record FindingCode(Family family, int number) implements Comparable<FindingCode> {
    public enum Family {
        SPC, OCC, EXP, CLM, BPT, CHK, DOC, CTX, ENG, DIA, RMD, SPI, MC;
        public String subject();
    }
    public static FindingCode of(Family family, int number);       // 1..9999
    public static FindingCode of(String text);                     // parse or IAE
    public static @Nullable FindingCode parse(String text);
    public String text();                                          // "SC-EXP-0001"
    @Override public int compareTo(FindingCode other);
}

public interface ContextView {
    <T> @Nullable T get(ContextKey<T> key);
    <T> Outcome<T> require(ContextKey<T> key);
    boolean has(ContextKey<?> key);
    java.util.Set<ContextKey<?>> keys();
}

public final class Catalog<T> {
    public NamespacedId family();
    public @Nullable T get(NamespacedId id);
    public T require(NamespacedId id);
    public boolean has(NamespacedId id);
    public java.util.List<NamespacedId> ids();
    public java.util.Map<NamespacedId, T> asMap();
    public int size();
    public boolean isEmpty();
    public static <T> Catalog<T> empty(NamespacedId family);
    public static <T> Builder<T> builder(NamespacedId family);

    public static final class Builder<T> {
        public Builder<T> put(NamespacedId id, T value);
        public Builder<T> putAll(Catalog<T> catalog);
        public Catalog<T> build();
    }
}

public sealed interface Value permits Value.ObjectValue, Value.ArrayValue, Value.StringValue,
        Value.NumberValue, Value.BooleanValue, Value.NullValue {

    @Nullable SourceSpan span();

    record ObjectValue(java.util.Map<String, Value> members, @Nullable SourceSpan span)
            implements Value { /* canonical code-point order */ }
    record ArrayValue(java.util.List<Value> elements, @Nullable SourceSpan span) implements Value { }
    record StringValue(String value, @Nullable SourceSpan span) implements Value { }
    record NumberValue(long value, @Nullable SourceSpan span) implements Value { }
    record BooleanValue(boolean value, @Nullable SourceSpan span) implements Value { }
    record NullValue(@Nullable SourceSpan span) implements Value { }

    static Value object(java.util.Map<String, Value> members);
    static Value object();
    static Value array(java.util.List<Value> elements);
    static Value array();
    static Value string(String value);
    static Value number(long value);
    static Value bool(boolean value);
    static Value nil();
    static Value withSpan(Value value, SourceSpan span);

    default boolean isObject();
    default boolean isArray();
    default boolean isString();
    default boolean isNumber();
    default boolean isBoolean();
    default boolean isNull();
    default @Nullable Value get(String member);
    default java.util.List<Value> elements();
    default java.util.Map<String, Value> members();
    default String asString(String fallback);
    default long asNumber(long fallback);
    default boolean asBoolean(boolean fallback);
}

public final class Values {
    public static boolean contentEquals(Value left, Value right);
    public static boolean contentEquals(java.util.List<Value> left, java.util.List<Value> right);
    public static java.util.Comparator<String> codePointOrder();
    private Values() {}
}

public final class Checks {
    public static void checkArgument(boolean condition, String message);
    public static <T> T checkNotNull(@Nullable T value, String message);
    private Checks() {}
}
```

## 4. Laws

**L1 — id grammar.** `namespace` matches `[a-z0-9_.-]{1,64}`, `path` matches
`[a-z0-9/._-]{1,256}` with no leading, trailing or doubled `/`. Uppercase is refused, never folded.
`of(namespace, path)` has no default; `of(path)` uses `DEFAULT_NAMESPACE`. `parse` returns `null` for
malformed input; it never partially accepts. `text()` is `namespace + ":" + path`; `compareTo` is
`namespace` then `path`, both `String.compareTo` (code point, locale-free).

**L2 — identities are values.** `World`, `OwnerId`, `InstanceId` and `SnapshotId` are records with
structural equality. `InstanceId.serial` and `SnapshotId.revision` are positive. There is no clock
and no global counter in the kernel; the host supplies the numbers.

**L3 — outcomes are not findings.** `Outcome.Missing` carries no payload. A consumer that needs to
explain a missing value builds the finding where the locus is known; `Outcome` is the common
vocabulary of a lookup, not a report.

**L4 — key identity.** `ContextKey` equality is the full record: id, type, retention, merge and
`whenAbsent`. A catalog owns uniqueness by id; two keys with one id and different types are a
catalog error (`SC-SPI-0003`). `ContextKeys.of` is the only constructor that the kernel uses; the
canonical constructor validates and `ContextKeys` is the intended factory.

**L5 — merge is associative, with no identity.** `merge.combine` must be associative; the kernel
never supplies an identity. A key with `merge != null` is section-contributed; a key with
`merge == null` is single-writer. `whenAbsent` is used only when every section of the region was
readable and no contribution was produced. It may be `null`, which means "absent".

**L6 — retention is lifetime.** `MATCH` values live in `MatchOutcome` and are readable by later
units of the same attempt; they are never published. `INSTANCE` values are published once by a
successful `settle` or `derive`, keyed by `InstanceId`.

**L7 — value tree order and equality.** `Value.ObjectValue.members()` iterates in ascending Unicode
code-point order; `ArrayValue` preserves order. `Value.equals` and `Values.contentEquals` ignore
spans; a span is provenance, never content.

**L8 — catalog freeze.** `put(id, value)` refuses a second, different value for one id with
`IllegalArgumentException`; re-putting the identical instance is a no-op. `build()` copies ids and
values into an immutable insertion-ordered structure. A catalog has no mutator after `build`.

**L9 — budget arithmetic.** `Budget.admits(spend)` is `spend.cells <= cells && spend.sections <=
sections`. A budget is a ceiling, not a prediction; `Spend` only grows. `unbounded()` uses
`Integer.MAX_VALUE` on both legs, not a sentinel.

**L10 — no exceptions for data.** `Outcome`, `Phase`, `Value`, `Catalog` never throw for a value
they can represent. `NamespacedId.of`, `ContextKey` validation, `SourceSpan` validation and
`Budget` validation throw only for a violated programmer precondition.

## 5. Pipeline role

`core` is used in every phase. It has no phase-specific behaviour. `Value` is the neutral data tree
for the document compiler and for extension arguments; it does not know JSON, SNBT or Minecraft.

## 6. Codes

This module produces no findings. It defines `SourceSpan` and `Phase`, which every finding carries.

## 7. Tests

- `NamespacedId`: parse/round-trip for valid ids; null for uppercase, double colon, empty path,
  leading/trailing/double slash, overlong fields; `compareTo` total and independent of locale.
- `Catalog`: insertion order; duplicate identical put is a no-op; duplicate different put throws;
  `asMap` is unmodifiable and ordered.
- `Value`: object key order is code-point; `contentEquals` ignores spans; arrays preserve order;
  number is a `long`.
- `Budget`/`Spend`: admits, plus, zero, unbounded.
- `ContextKey`: equality includes all components; `contributed()` matches `merge != null`.
- `Outcome`: fold exhaustiveness.

## 8. Dependencies

JDK 21 and `org.jspecify:jspecify`. Nothing else. `core` must never import another kernel package.
