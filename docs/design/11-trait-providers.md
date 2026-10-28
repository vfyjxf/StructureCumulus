# 11. Generic trait providers

> Status: proposed, not implemented. This document replaces the `readBlock`/`readEntity` shape of
> `TraitProvider` with one generic provider per holder type.

## 1. Problem

The current provider aggregates two unrelated subjects in one interface:

```java
public interface TraitProvider<T> {
    TraitKey<T> key();

    default TraitSlot readBlock(BlockTraitContext context) { ... }

    default TraitSlot readEntity(EntityTraitContext context) { ... }
}
```

Every provider therefore carries both methods, even when it only knows one subject. The defaults
turn "this provider does not apply" into a hidden `Absent`, and a new subject type (block entity,
item, capability proxy, …) cannot be added without editing the provider interface again.

That is the same mistake the host-data round removed: a kernel/adapter interface pre-declares
subject kinds instead of making the subject a type token.

## 2. Shape

```java
public interface TraitProvider<T, H> {

    TraitKey<T> key();

    TraitHolder<H> holder();

    TraitSlot get(H actual, TraitCaptureContext capture);
}
```

- `T` is the trait value type.
- `H` is the actual holder type the provider reads.
- `holder()` is the type token; no `Class<?>`.
- `get(actual, capture)` is the only read method.
- no `readBlock`, no `readEntity`, no default `Absent`.

A provider is registered for exactly one `(trait id, holder id)` pair. The same trait may have
several providers, one per holder; inventory, energy and fluid naturally do.

## 3. TraitHolder

```java
package dev.vfyjxf.cumulus.mc.trait;

public record TraitHolder<H>(NamespacedId id) implements Comparable<TraitHolder<?>> {

    public TraitHolder {
        Objects.requireNonNull(id, "id");
    }

    public static <H> TraitHolder<H> of(NamespacedId id) {
        return new TraitHolder<>(id);
    }

    @Override
    public int compareTo(TraitHolder<?> other) {
        return id.compareTo(other.id);
    }
}
```

Adapter declarations:

```java
public final class TraitHolders {

    public static final TraitHolder<BlockTraitContext> block =
        TraitHolder.of(id("block"));

    public static final TraitHolder<EntityTraitContext> entity =
        TraitHolder.of(id("entity"));

    private TraitHolders() {}
}
```

`TraitHolders` is the only place that names the current subject kinds. The provider interface does
not.

## 4. Actual holders

The actual holder records stop carrying the capture context; the capture is passed to `get`:

```java
public record BlockTraitContext(
    ServerLevel level,
    BlockPos pos,
    BlockState state,
    @Nullable BlockEntity blockEntity
) { }

public record EntityTraitContext(Entity entity) { }
```

This keeps "what is being read" separate from "under which capture policy it is read".

## 5. Registry

`TraitLookup` is keyed by `(trait id, holder id)`:

```java
record ProviderKey(NamespacedId trait, NamespacedId holder) { }

public final class TraitLookup {

    private final Map<ProviderKey, TraitProvider<?, ?>> providers;

    public <T, H> @Nullable TraitProvider<T, H> provider(TraitKey<T> key, TraitHolder<H> holder);

    public <H> @Nullable TraitProvider<?, H> provider(NamespacedId trait, TraitHolder<H> holder);

    public boolean knows(NamespacedId trait);
}
```

- duplicate `(trait, holder)` is rejected;
- the same trait with a different holder is a different registration;
- `knows` distinguishes "unknown trait" from "known trait, no provider for this holder".

Registration:

```java
@FunctionalInterface
public interface TraitRegister {

    <T, H> void register(TraitProvider<T, H> provider);
}
```

No separate `registerBlock`/`registerEntity` methods; a new holder type does not change the
registration interface.

## 6. Dispatch and absence

`OccupantTraits` keeps the existing five trait ids and asks by holder:

```java
private static <H> TraitSlot read(
    TraitLookup lookup,
    NamespacedId id,
    TraitHolder<H> holder,
    H actual,
    TraitCaptureContext capture
) {
    TraitProvider<?, H> provider = lookup.provider(id, holder);
    if (provider == null) {
        return lookup.knows(id)
            ? new TraitSlot.Absent(id)
            : refused(id, ReadStatus.denied);
    }
    try {
        TraitSlot slot = provider.get(actual, capture);
        return slot == null || !slot.id().equals(id) ? refused(id, ReadStatus.failed) : slot;
    } catch (RuntimeException failure) {
        return refused(id, ReadStatus.failed);
    }
}
```

- unknown trait id -> `Refused(denied)`, as today;
- known trait, no provider for this holder -> `Absent`, preserving the old default behavior;
- provider throws or returns a wrong id -> `Refused(failed)`, as today.

`blockSlot` and `entitySlot` are thin wrappers that pass `TraitHolders.block` or
`TraitHolders.entity`; they do not test subject kinds themselves.

## 7. Standard providers

The five standard traits are split by holder; no class implements both block and entity reads.

```java
public final class BlockTraitProviders {
    public static final TraitProvider<List<ItemStack>, BlockTraitContext> inventory;
    public static final TraitProvider<Integer, BlockTraitContext> energy;
    public static final TraitProvider<List<FluidStack>, BlockTraitContext> fluid;
    public static final TraitProvider<CompoundTag, BlockTraitContext> blockEntityNbt;
}

public final class EntityTraitProviders {
    public static final TraitProvider<List<ItemStack>, EntityTraitContext> inventory;
    public static final TraitProvider<Integer, EntityTraitContext> energy;
    public static final TraitProvider<List<FluidStack>, EntityTraitContext> fluid;
    public static final TraitProvider<CompoundTag, EntityTraitContext> entityNbt;
}
```

Each provider:

```java
private static final class BlockInventory implements TraitProvider<List<ItemStack>, BlockTraitContext> {

    @Override
    public TraitKey<List<ItemStack>> key() {
        return OccupantTraits.inventory;
    }

    @Override
    public TraitHolder<BlockTraitContext> holder() {
        return TraitHolders.block;
    }

    @Override
    public TraitSlot get(BlockTraitContext actual, TraitCaptureContext capture) {
        IItemHandler handler = actual.level().getCapability(
            Capabilities.ItemHandler.BLOCK,
            actual.pos(),
            actual.state(),
            actual.blockEntity(),
            capture.capabilityContext()
        );
        return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), inventoryValue(handler));
    }
}
```

- block and entity NBT are now ordinary one-holder providers, not special cases;
- value extraction helpers are shared as plain static functions, not through a mixed interface;
- `BuiltinTraitPlugin` registers both groups through the same generic `TraitRegister`.

## 8. Extensibility

Adding a subject type:

1. define `TraitHolder<NewContext>`;
2. define the actual holder record;
3. implement `TraitProvider<T, NewContext>` where needed;
4. register it;
5. add the capture dispatch point that calls `read(..., holder, actual, capture)`.

No existing provider, registry method, or registration method changes.

Adding a trait:

1. define `TraitKey<T>`;
2. implement one provider per holder that can supply it;
3. register;
4. add the id to the capture policy.

A trait with only a block provider is simply absent on entities; a trait with both is two providers,
not one interface with two defaults.

## 9. Impact

Kernel:

- none; `TraitKey`, `TraitSlot`, `Trait`, `SnapshotPolicy` are unchanged.

Adapter:

- `TraitProvider<T>` -> `TraitProvider<T, H>`;
- new `TraitHolder<H>` and `TraitHolders`;
- `BlockTraitContext` / `EntityTraitContext` lose the `capture` field;
- `TraitLookup` keyed by `(trait, holder)`, adds `knows`;
- `TraitRegister` becomes `<T, H> register(...)`;
- `StandardTraitProviders` split into `BlockTraitProviders` / `EntityTraitProviders`;
- `OccupantTraits` generic dispatch;
- plugin registration and tests updated.

No JSON, document, kernel or host-snapshot change.

## 10. Tests

- same trait with block and entity providers resolves each by holder;
- duplicate `(trait, holder)` rejected;
- same trait, different holders allowed;
- unknown trait -> denied;
- known trait without a provider for the holder -> absent;
- provider receives exactly the actual holder and capture;
- provider throwing / returning wrong id -> failed;
- block inventory/energy/fluid/NBT and entity inventory/energy/fluid/NBT preserve M2c semantics;
- plugin registration and built-in lookup parity;
- fake provider tests use `get`, not `readBlock`/`readEntity`.

## 11. Non-goals

- no `readBlock`/`readEntity` on the generic provider;
- no default `Absent` in the provider interface;
- no provider implementing both current subject kinds;
- no `Class<?>` in `TraitHolder`;
- no kernel trait API change;
- no merge of trait capture into the host snapshot.

## 12. Migration

| Step | Change |
| --- | --- |
| T1 | `TraitHolder`, `TraitHolders`, generic `TraitProvider<T,H>`, `TraitRegister`, `TraitLookup` keying |
| T2 | split standard providers, update `OccupantTraits` dispatch and contexts |
| T3 | update tests/docs, `TraitCaptureGameTest` fake providers, `TraitLookupTest` |

Implementation starts after this design is accepted.
