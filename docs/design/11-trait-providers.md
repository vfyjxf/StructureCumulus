# 11. Generic trait providers

> Status: proposed, not implemented. This document replaces the `readBlock`/`readEntity` shape of
> `TraitProvider`, removes the generic NBT traits, and makes capability traits return the capability
> itself.

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
item, attachment proxy, …) cannot be added without editing the provider interface again.

Two more problems are in the standard traits:

- inventory is copied into a `List<ItemStack>`, fluid into a `List<FluidStack>`, and energy into an
  `Integer`; each wrapper loses the real capability object and invents a second value model;
- `blockEntityNbt` / `entityNbt` read data through full NBT serialization. That is not an API:
  concrete block entities expose typed state, capabilities or attachments. A generic NBT trait
  encourages callers to depend on serialized implementation details and to reimplement what the
  owning API already models.

This is the same mistake the host-data round removed: a kernel/adapter interface pre-declares
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

A trait value is the provider's real value, not a copy invented by the framework. For capability
traits that means the capability object itself.

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

`OccupantTraits` keeps the stable trait ids and asks by holder:

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

### 7.1 Three capability traits, direct values

The standard trait set is exactly the three NeoForge capabilities. Their values are the capability
objects; no list, integer or NBT wrapping:

| Field | Id | Java type |
| --- | --- | --- |
| `inventory` | `structurecumulus:inventory` | `IItemHandler` |
| `energy` | `structurecumulus:energy` | `IEnergyStorage` |
| `fluid` | `structurecumulus:fluid` | `IFluidHandler` |

A consumer that wants slot contents calls `getStackInSlot`; one that wants tank contents calls
`getFluidInTank`; one that wants stored energy calls `getEnergyStored`. The trait system does not
duplicate those APIs.

The value is the live capability handle. The capture lease is the validity boundary: a consumer must
not retain a handle after the lease stops being current. No copy is made, and no mutation goes
through a second wrapper.

### 7.2 Split by holder

```java
public final class BlockTraitProviders {
    public static final TraitProvider<IItemHandler, BlockTraitContext> inventory;
    public static final TraitProvider<IEnergyStorage, BlockTraitContext> energy;
    public static final TraitProvider<IFluidHandler, BlockTraitContext> fluid;
}

public final class EntityTraitProviders {
    public static final TraitProvider<IItemHandler, EntityTraitContext> inventory;
    public static final TraitProvider<IEnergyStorage, EntityTraitContext> energy;
    public static final TraitProvider<IFluidHandler, EntityTraitContext> fluid;
}
```

Each provider:

```java
private static final class BlockInventory implements TraitProvider<IItemHandler, BlockTraitContext> {

    @Override
    public TraitKey<IItemHandler> key() {
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
        return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), handler);
    }
}
```

- block and entity providers are separate classes; no class implements both;
- the capability context is still passed to the five-argument block lookup;
- a null capability is `Absent`;
- a block entity state with a null block entity stays `Refused(notCaptured)`;
- malformed capability access stays `Refused(failed)`.

### 7.3 No generic NBT traits

`blockEntityNbt` and `entityNbt` are removed. The framework does not serialize a block entity or an
entity into a trait.

If a host needs block-entity or entity data, it registers a concrete trait for the API that owns the
data:

```java
public static final TraitKey<MyMachineState> machineState =
    new TraitKey<>(id("machine_state"), MyMachineState.class);

public final class MachineStateProvider
    implements TraitProvider<MyMachineState, BlockTraitContext> {

    @Override
    public TraitKey<MyMachineState> key() {
        return machineState;
    }

    @Override
    public TraitHolder<BlockTraitContext> holder() {
        return TraitHolders.block;
    }

    @Override
    public TraitSlot get(BlockTraitContext actual, TraitCaptureContext capture) {
        if (actual.blockEntity() instanceof MyMachineBlockEntity machine) {
            return present(key(), machine.state());
        }
        return new TraitSlot.Absent(key().id());
    }
}
```

The same rule applies to attachments, data components, item handlers and any future host API: a
typed provider over the owning API, not a generic serialization escape hatch.

## 8. Extensibility

Adding a subject type:

1. define `TraitHolder<NewContext>`;
2. define the actual holder record;
3. implement `TraitProvider<T, NewContext>` where needed;
4. register it;
5. add the capture dispatch point that calls `read(..., holder, actual, capture)`.

No existing provider, registry method, or registration method changes.

Adding a trait:

1. define `TraitKey<T>` over the real API type;
2. implement one provider per holder that can supply it;
3. register;
4. add the id to the capture policy.

A trait with only a block provider is simply absent on entities; a trait with both is two providers,
not one interface with two defaults.

## 9. Impact

Kernel:

- none; `TraitKey`, `TraitSlot`, `Trait`, `SnapshotPolicy` are unchanged. The concrete `TraitKey`
  types change in the adapter, not the kernel type.

Adapter:

- `TraitProvider<T>` -> `TraitProvider<T, H>`;
- new `TraitHolder<H>` and `TraitHolders`;
- `BlockTraitContext` / `EntityTraitContext` lose the `capture` field;
- `TraitLookup` keyed by `(trait, holder)`, adds `knows`;
- `TraitRegister` becomes `<T, H> register(...)`;
- `StandardTraitProviders` split into `BlockTraitProviders` / `EntityTraitProviders`;
- `OccupantTraits` keys become `IItemHandler`, `IEnergyStorage`, `IFluidHandler`; the two NBT keys
  are removed;
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
- block/entity inventory returns the exact `IItemHandler` identity, not a copy;
- block/entity energy returns the exact `IEnergyStorage`;
- block/entity fluid returns the exact `IFluidHandler`;
- no `blockEntityNbt` / `entityNbt` keys exist;
- capability-absent -> absent; null block entity -> refused(notCaptured); malformed capability ->
  refused(failed);
- plugin registration and built-in lookup parity;
- fake provider tests use `get`, not `readBlock`/`readEntity`.

## 11. Non-goals

- no `readBlock`/`readEntity` on the generic provider;
- no default `Absent` in the provider interface;
- no provider implementing both current subject kinds;
- no `Class<?>` in `TraitHolder`;
- no `List<ItemStack>`, `List<FluidStack>` or `Integer` wrappers for capability traits;
- no `blockEntityNbt` or `entityNbt`;
- no generic serialization trait;
- no kernel trait API change;
- no merge of trait capture into the host snapshot.

## 12. Migration

| Step | Change |
| --- | --- |
| T1 | `TraitHolder`, `TraitHolders`, generic `TraitProvider<T,H>`, `TraitRegister`, `TraitLookup` keying |
| T2 | split capability providers, change keys to direct capability types, remove NBT traits, update `OccupantTraits` dispatch and contexts |
| T3 | update tests/docs, `TraitCaptureGameTest` fake providers, `TraitLookupTest`; remove NBT assertions |

Implementation starts after this design is accepted.
