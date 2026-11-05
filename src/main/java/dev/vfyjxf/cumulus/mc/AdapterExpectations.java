package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The adapter's host expectation factories over the typed snapshot reads. */
@NullMarked
final class AdapterExpectations {

    private interface Body {
        @Nullable
        Expectation<?> create(Value args, FactoryContext context);
    }

    private record Factory(NamespacedId id, Body body) implements ExpectationFactory {

        @Override
        public @Nullable Expectation<?> create(Value args, FactoryContext context) {
            try {
                return body.create(args, context);
            } catch (RuntimeException failure) {
                report(context, Codes.factoryBad, "factory " + id + " failed: " + failure.getMessage());
                return null;
            }
        }
    }

    static Catalog<ExpectationFactory> catalog() {
        Catalog.Builder<ExpectationFactory> builder = Catalog.builder(id("expectations"));
        builder.put(id("property"), new Factory(id("property"), AdapterExpectations::property));
        builder.put(id("state"), new Factory(id("state"), AdapterExpectations::state));
        builder.put(id("tag"), new Factory(id("tag"), AdapterExpectations::tag));
        builder.put(id("fluid"), new Factory(id("fluid"), AdapterExpectations::fluid));
        return builder.build();
    }

    static Expectation<OccupantSnapshot> property(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return Expectations.host(
            HostTypes.blockState,
            (state, cell) -> hasProperty(state, key, value),
            AdapterCodes.propertyMismatch,
            AdapterCodes.valueNotCaptured,
            "expected property " + key + "=" + value
        );
    }

    static Expectation<OccupantSnapshot> state(BlockState expected) {
        Objects.requireNonNull(expected, "expected");
        return Expectations.host(
            HostTypes.blockState,
            (state, cell) -> state.equals(expected),
            AdapterCodes.stateMismatch,
            AdapterCodes.valueNotCaptured,
            "expected block state " + expected
        );
    }

    static Expectation<OccupantSnapshot> blockTag(TagKey<Block> tag) {
        Objects.requireNonNull(tag, "tag");
        return Expectations.host(
            HostTypes.blockState,
            (state, cell) -> state.is(tag),
            AdapterCodes.tagMismatch,
            AdapterCodes.valueNotCaptured,
            "expected block tag " + tag.location()
        );
    }

    static Expectation<OccupantSnapshot> fluidTag(TagKey<Fluid> tag) {
        Objects.requireNonNull(tag, "tag");
        return Expectations.host(
            HostTypes.fluidState,
            (fluid, cell) -> fluid.is(tag),
            AdapterCodes.tagMismatch,
            AdapterCodes.valueNotCaptured,
            "expected fluid tag " + tag.location()
        );
    }

    static Expectation<OccupantSnapshot> entityTag(TagKey<EntityType<?>> tag) {
        Objects.requireNonNull(tag, "tag");
        return Expectations.host(
            HostTypes.entityType,
            (entityType, cell) -> entityType.is(tag),
            AdapterCodes.tagMismatch,
            AdapterCodes.valueNotCaptured,
            "expected entity tag " + tag.location()
        );
    }

    static Expectation<OccupantSnapshot> fluid(Fluid expected) {
        Objects.requireNonNull(expected, "expected");
        String name = name(expected);
        return Expectations.allOf(
            Expectations.host(
                HostTypes.fluidState,
                (fluid, cell) -> fluid.getType() == expected,
                AdapterCodes.fluidMismatch,
                AdapterCodes.valueNotCaptured,
                "expected fluid " + name
            ),
            Expectations.host(
                HostTypes.blockState,
                (block, cell) -> block.getBlock() == block.getFluidState().createLegacyBlock().getBlock(),
                AdapterCodes.fluidMismatch,
                AdapterCodes.valueNotCaptured,
                "expected the fluid block of " + name
            )
        );
    }

    private static @Nullable Expectation<?> property(Value args, FactoryContext context) {
        String key = string(args, "key", context);
        String value = string(args, "value", context);
        return key == null || value == null ? null : property(key, value);
    }

    private static @Nullable Expectation<?> state(Value args, FactoryContext context) {
        NamespacedId blockId = id(args, "block", context);
        if (blockId == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location(blockId));
        if (block == null) {
            report(context, Codes.factoryBad, "unknown block " + blockId);
            return null;
        }
        BlockState resolved = block.defaultBlockState();
        Value properties = args.get("properties");
        if (properties != null) {
            if (!properties.isObject()) {
                report(context, Codes.documentField, "'properties' must be an object");
                return null;
            }
            for (Map.Entry<String, Value> entry : properties.members().entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    report(context, Codes.factoryBad, "block " + blockId + " has no property " + entry.getKey());
                    return null;
                }
                if (!(entry.getValue() instanceof Value.StringValue text)) {
                    report(context, Codes.documentField, "property values are strings at " + entry.getKey());
                    return null;
                }
                Optional<?> value = property.getValue(text.value());
                if (value.isEmpty()) {
                    report(
                        context,
                        Codes.factoryBad,
                        "block " + blockId + " has no " + entry.getKey() + "=" + text.value()
                    );
                    return null;
                }
                resolved = withValue(resolved, property, value.get());
            }
        }
        return state(resolved);
    }

    private static @Nullable Expectation<?> tag(Value args, FactoryContext context) {
        String registry = string(args, "registry", context);
        NamespacedId tagId = id(args, "id", context);
        if (registry == null || tagId == null) {
            return null;
        }
        ResourceLocation location = location(tagId);
        return switch (registry) {
            case "block" -> blockTag(TagKey.create(Registries.BLOCK, location));
            case "fluid" -> fluidTag(TagKey.create(Registries.FLUID, location));
            case "entity" -> entityTag(TagKey.create(Registries.ENTITY_TYPE, location));
            default -> {
                report(context, Codes.documentField, "unknown tag registry '" + registry + "'");
                yield null;
            }
        };
    }

    private static @Nullable Expectation<?> fluid(Value args, FactoryContext context) {
        NamespacedId fluidId = id(args, "id", context);
        if (fluidId == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(location(fluidId));
        if (fluid == null) {
            report(context, Codes.factoryBad, "unknown fluid " + fluidId);
            return null;
        }
        return fluid(fluid);
    }

    private static @Nullable NamespacedId id(Value args, String key, FactoryContext context) {
        String text = string(args, key, context);
        if (text == null) {
            return null;
        }
        NamespacedId id = NamespacedId.parse(text);
        if (id == null) {
            report(context, Codes.documentField, "invalid id at " + key + ": " + text);
        }
        return id;
    }

    private static @Nullable String string(Value args, String key, FactoryContext context) {
        Value value = args.get(key);
        if (!(value instanceof Value.StringValue string) || string.value().isBlank()) {
            report(context, Codes.documentField, "expected a string at " + key);
            return null;
        }
        return string.value();
    }

    private static ResourceLocation location(NamespacedId id) {
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }

    private static boolean hasProperty(BlockState state, String key, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(key)) {
                return propertyName(state, property).equals(value);
            }
        }
        return false;
    }

    private static <T extends Comparable<T>> String propertyName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withValue(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property) property, (Comparable) value);
    }

    private static String name(Fluid fluid) {
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        return key == null ? fluid.toString() : key.toString();
    }

    private static void report(FactoryContext context, CodeEntry entry, String message) {
        Finding.Builder builder = Finding.builder(entry).phase(Phase.compile).message(message);
        if (context.source() != null) {
            Locus locus = Locus.definition(context.source());
            builder.label(Label.primary(locus, message));
        }
        context.report().accept(builder.build());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private AdapterExpectations() {}
}
