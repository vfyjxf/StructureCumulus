# 13. Nested extent access: capability and attachment sources

> Status: approved and implemented. This supplements D-36 and replaces the previous
> capability-only `NestedExtentAccess`. The capability/attachment split is implemented in
> `mc.capability`; 411 adapter unit tests and 126/126 GameTests are green.

## 1. Correction

The previous draft invented a separate `NestedExtentReadContext`. That was wrong. The context is
the **capability's own `C` generic** (for example `@Nullable Direction`), passed straight to
`BaseCapability`'s `getCapability` call. Capability and attachment are different sources and get
different access/provider types; they are not forced into one `NestedExtentAccess` interface.

## 2. Capability source

```java
package dev.vfyjxf.cumulus.mc.capability;

@FunctionalInterface
public interface CapabilityNestedExtentAccess<H, C extends @Nullable Object> {
    @Nullable
    NestedExtent get(BaseCapability<NestedExtent, C> capability, H actual, C context);
}

public interface CapabilityNestedExtentProvider<H, C extends @Nullable Object> {
    NestedExtentHolder<H> holder();

    @Nullable
    NestedExtent get(H actual, C context);
}
```

`C` is the capability context, not a new context record:

```java
public final class CapabilityNestedExtentAccesses {
    public static CapabilityNestedExtentAccess<BlockNestedExtentContext, @Nullable Direction> block() {
        return (capability, actual, context) -> {
            @SuppressWarnings("unchecked")
            BlockCapability<NestedExtent, @Nullable Direction> block =
                (BlockCapability<NestedExtent, @Nullable Direction>) capability;
            return actual.level().getCapability(
                block, actual.pos(), actual.state(), actual.blockEntity(), context);
        };
    }

    public static CapabilityNestedExtentAccess<Entity, @Nullable Direction> entity() { ... }
    public static CapabilityNestedExtentAccess<Entity, @Nullable Void> entityVoid() { ... }
}
```

The concrete provider binds one capability and one access:

```java
public final class CapabilityNestedExtentProviders {
    public static <H, C extends @Nullable Object> CapabilityNestedExtentProvider<H, C> of(
        NestedExtentHolder<H> holder,
        BaseCapability<NestedExtent, C> capability,
        CapabilityNestedExtentAccess<H, C> access);
}
```

Standard declarations:

```java
public final class NestedExtentProviders {
    public static final CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction> block =
        CapabilityNestedExtentProviders.of(
            NestedExtentHolders.block,
            NestedExtents.BLOCK,                 // BlockCapability<NestedExtent, @Nullable Direction>
            CapabilityNestedExtentAccesses.block());

    public static final CapabilityNestedExtentProvider<Entity, @Nullable Direction> entity = ...;
}
```

`NestedExtents.BLOCK` and `NestedExtents.ENTITY` are created with the real capability context type
with `BlockCapability.createSided` / `EntityCapability.createSided` (the `C` is `@Nullable Direction`),
not a void-context capability; this is what makes `C` usable. A separate Void-context capability can
still be exposed through `entityVoid()` and a `createVoid`-style capability.

## 3. Attachment source

Attachments are split out completely; they do not use `BaseCapability` or `C`.

```java
@FunctionalInterface
public interface AttachmentNestedExtentReader<T> {
    @Nullable
    NestedExtent read(T data);
}

@FunctionalInterface
public interface AttachmentNestedExtentWriter<T> {
    @Nullable
    T write(NestedExtent extent);
}

@FunctionalInterface
public interface AttachmentHolder<H> {
    @Nullable
    IAttachmentHolder holder(H actual);
}

public interface AttachmentNestedExtentProvider<H, T> {
    NestedExtentHolder<H> holder();

    AttachmentType<T> type();

    boolean hasData(H actual);

    @Nullable
    NestedExtent getExistingDataOrNull(H actual);

    @Nullable
    NestedExtent getData(H actual);

    @Nullable
    NestedExtent setData(H actual, NestedExtent value);

    @Nullable
    NestedExtent removeData(H actual);
}
```

Semantics mirror `IAttachmentHolder`:

- `hasData` -> `holder.hasData(type)`;
- `getExistingDataOrNull` -> `reader.read(holder.getExistingDataOrNull(type))`;
- `getData` -> `reader.read(holder.getData(type))` (may create/store the default);
- `setData` -> `writer.write(value)` then `holder.setData(type, raw)`;
- `removeData` -> `holder.removeData(type)` then reader;
- a null holder (block without block entity) is absent;
- capture only calls `getExistingDataOrNull`, never `getData`/`setData`/`removeData`;
- `setData` requires an `AttachmentNestedExtentWriter`; without a writer it throws
  `UnsupportedOperationException`.

Registration factory:

```java
public final class AttachmentNestedExtentProviders {
    public static <H, T> AttachmentNestedExtentProvider<H, T> readOnly(
        NestedExtentHolder<H> holder,
        AttachmentType<T> type,
        AttachmentHolder<H> holderOf,
        AttachmentNestedExtentReader<T> reader);

    public static <H, T> AttachmentNestedExtentProvider<H, T> readWrite(
        NestedExtentHolder<H> holder,
        AttachmentType<T> type,
        AttachmentHolder<H> holderOf,
        AttachmentNestedExtentReader<T> reader,
        AttachmentNestedExtentWriter<T> writer);
}
```

Standard holder adapters:

- block entity: `BlockNestedExtentContext::blockEntity` (null-safe; `BlockEntity implements
  IAttachmentHolder`);
- entity: `Entity` itself implements `IAttachmentHolder`.

There is no built-in attachment type/provider: the host owns the `AttachmentType` and holder id.

## 4. Lookup

The two sources remain separate in the API; the lookup can hold both and exposes explicit methods:

```java
public final class NestedExtentLookup {

    public <H, C extends @Nullable Object> @Nullable NestedExtent getCapability(
        NestedExtentHolder<H> holder, H actual, C context);

    public <H> @Nullable NestedExtent getAttachment(
        NestedExtentHolder<H> holder, H actual);

    /** Capability first, then attachment; used by capture. */
    public <H, C extends @Nullable Object> @Nullable NestedExtent get(
        NestedExtentHolder<H> holder, H actual, C context);

    public static Builder builder();
    public static final class Builder {
        public <H, C extends @Nullable Object> Builder add(CapabilityNestedExtentProvider<H, C> provider);
        public <H, T> Builder add(AttachmentNestedExtentProvider<H, T> provider);
        // duplicate ids rejected within one source; the two sources may share a holder id
    }
}
```

Registration:

```java
NestedExtentLookup.builder()
    .add(NestedExtentProviders.block)
    .add(NestedExtentProviders.entity)
    .add(AttachmentNestedExtentProviders.readOnly(myHolder, MY_TYPE, ctx -> ctx.blockEntity(), reader))
    .build();
```

Precedence: `get` is capability-first. If the capability provider answers `null` — or no capability
provider is registered for that holder — it falls back to `getAttachment`. The two maps may hold the
same holder id (a capability and an attachment can declare the same holder); the builder rejects a
duplicate only within one source. `getCapability` and `getAttachment` never cross the source boundary.

### 4.1 Host injection and registration boundary

`ServerState.installNestedExtentLookup(NestedExtentLookup)` (`ServerState.java:148-157`) installs the
host's configured lookup; the default is `NestedExtentLookup.capabilities()`. Open captures are
closed because a `LevelCapture` keeps the lookup it was built with, and later captures use the
installed lookup.

This repository does **not** call `RegisterCapabilitiesEvent` and registers no nested provider for
another mod. `NestedExtentProviders.block`/`entity` are the standard capability declarations and
accesses, not a capability registration; addons register their own providers on the MOD bus through
`RegisterCapabilitiesEvent` against `NestedExtents.BLOCK`/`NestedExtents.ENTITY` (and register
attachment providers themselves). The in-repo seam is the lookup plus
`ServerState.installNestedExtentLookup`: a host or test builds a `NestedExtentLookup` and installs it;
production defaults to the standard capability pair.

## 5. Capture flow

`LevelCapture` passes the capability context directly; no new context object:

```java
Direction side = traitCapture.capabilityContext(); // may be null

nestedLookup.get(NestedExtentHolders.block,
    new BlockNestedExtentContext(level, pos, state, blockEntity), side);

nestedLookup.get(NestedExtentHolders.entity, candidate.entity(), side);
```

Attachment fallback is the same `get` call: capability first, attachment second, attachment read
uses `getExistingDataOrNull`.

## 6. Migration

- Delete `NestedExtentAccess<H, C>`, `NestedExtentAccesses`, and the old unified
  `CapabilityNestedExtentProvider` shape.
- Add `CapabilityNestedExtentAccess`, `CapabilityNestedExtentAccesses`,
  `CapabilityNestedExtentProvider`/`CapabilityNestedExtentProviders`,
  `AttachmentNestedExtentReader`, `AttachmentNestedExtentWriter`, `AttachmentHolder`,
  `AttachmentNestedExtentProvider`/`AttachmentNestedExtentProviders`.
- Keep `NestedExtentHolder`, `NestedExtentLookup`, `NestedExtentProviders` (now capability-only
  standard declarations).
- Update `LevelCapture`, `NestedExtents` (`BLOCK`/`ENTITY` context types), all fakes/tests.
- No compatibility shim; old capability-only access interface is removed.

## 7. Tests

- capability context: a direction-sensitive custom capability sees the side; standard block passes
  it; entity Void stays null-safe;
- attachment read: block-entity attachment present, absent, null block entity; entity attachment;
  `getExistingDataOrNull` does not create a default/dirty holder;
- attachment write: `readWrite` factory maps set/remove; `readOnly` rejects writes;
- lookup: explicit `getCapability`/`getAttachment`, capability-first `get`, per-source duplicate
  rejection (the capability and attachment sources may share a holder id), unknown holder, provider
  exception warning;
- existing nested frame/cut/provider tests remain green.

## 8. Resolved decision

`NestedExtentProviders` keeps its name for the capability-only standard declarations, and
`CapabilityNestedExtentProviders.of(...)` is the capability binding factory. There is no
`NestedExtentProviders` registration facade; the lookup's builder is the registration point. This is
the implemented shape.
