package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** The built-in context value codecs, keyed by value class name. */
@NullMarked
final class ContextCodecs {

    private static final String valueKey = "value";
    private static final String minKey = "min";
    private static final String maxKey = "max";
    private static final String boxesKey = "boxes";

    private static final Map<String, Codec<?>> defaults = defaults();

    interface Codec<T> {
        Class<T> type();

        void write(CompoundTag tag, T value);

        T read(CompoundTag tag);
    }

    static Map<String, Codec<?>> builtin() {
        return defaults;
    }

    private static Map<String, Codec<?>> defaults() {
        Map<String, Codec<?>> codecs = new LinkedHashMap<>();
        add(
            codecs,
            simple(Byte.class, Tag.TAG_BYTE, (tag, value) -> tag.putByte(valueKey, value), tag -> tag.getByte(valueKey))
        );
        add(
            codecs,
            simple(
                Short.class,
                Tag.TAG_SHORT,
                (tag, value) -> tag.putShort(valueKey, value),
                tag -> tag.getShort(valueKey)
            )
        );
        add(
            codecs,
            simple(Integer.class, Tag.TAG_INT, (tag, value) -> tag.putInt(valueKey, value), tag -> tag.getInt(valueKey))
        );
        add(
            codecs,
            simple(Long.class, Tag.TAG_LONG, (tag, value) -> tag.putLong(valueKey, value), tag -> tag.getLong(valueKey))
        );
        add(
            codecs,
            simple(
                Float.class,
                Tag.TAG_FLOAT,
                (tag, value) -> tag.putFloat(valueKey, value),
                tag -> tag.getFloat(valueKey)
            )
        );
        add(
            codecs,
            simple(
                Double.class,
                Tag.TAG_DOUBLE,
                (tag, value) -> tag.putDouble(valueKey, value),
                tag -> tag.getDouble(valueKey)
            )
        );
        add(
            codecs,
            simple(
                Boolean.class,
                Tag.TAG_BYTE,
                (tag, value) -> tag.putBoolean(valueKey, value),
                tag -> tag.getBoolean(valueKey)
            )
        );
        add(
            codecs,
            simple(
                String.class,
                Tag.TAG_STRING,
                (tag, value) -> tag.putString(valueKey, value),
                tag -> tag.getString(valueKey)
            )
        );
        add(codecs, namespacedId());
        add(codecs, cuboid());
        add(codecs, cuboidSet());
        return Collections.unmodifiableMap(codecs);
    }

    private static void add(Map<String, Codec<?>> codecs, Codec<?> codec) {
        codecs.put(codec.type().getName(), codec);
    }

    private static <T> Codec<T> simple(
        Class<T> type,
        int tagType,
        BiConsumer<CompoundTag, T> writer,
        Function<CompoundTag, T> reader
    ) {
        Objects.requireNonNull(type, "type");
        return new Codec<>() {
            @Override
            public Class<T> type() {
                return type;
            }

            @Override
            public void write(CompoundTag tag, T value) {
                writer.accept(tag, value);
            }

            @Override
            public T read(CompoundTag tag) {
                require(tag, valueKey, tagType);
                return reader.apply(tag);
            }
        };
    }

    private static Codec<NamespacedId> namespacedId() {
        return new Codec<>() {
            @Override
            public Class<NamespacedId> type() {
                return NamespacedId.class;
            }

            @Override
            public void write(CompoundTag tag, NamespacedId value) {
                tag.putString(valueKey, value.text());
            }

            @Override
            public NamespacedId read(CompoundTag tag) {
                require(tag, valueKey, Tag.TAG_STRING);
                NamespacedId id = NamespacedId.parse(tag.getString(valueKey));
                if (id == null) {
                    throw new IllegalArgumentException("invalid namespaced id");
                }
                return id;
            }
        };
    }

    private static Codec<Cuboid> cuboid() {
        return new Codec<>() {
            @Override
            public Class<Cuboid> type() {
                return Cuboid.class;
            }

            @Override
            public void write(CompoundTag tag, Cuboid value) {
                tag.putIntArray(minKey, point(value.min()));
                tag.putIntArray(maxKey, point(value.max()));
            }

            @Override
            public Cuboid read(CompoundTag tag) {
                return Cuboid.of(point(tag, minKey), point(tag, maxKey));
            }
        };
    }

    private static Codec<CuboidSet> cuboidSet() {
        return new Codec<>() {
            @Override
            public Class<CuboidSet> type() {
                return CuboidSet.class;
            }

            @Override
            public void write(CompoundTag tag, CuboidSet value) {
                ListTag boxes = new ListTag();
                for (Cuboid box : value.boxes()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putIntArray(minKey, point(box.min()));
                    entry.putIntArray(maxKey, point(box.max()));
                    boxes.add(entry);
                }
                tag.put(boxesKey, boxes);
            }

            @Override
            public CuboidSet read(CompoundTag tag) {
                ListTag boxes = tag.getList(boxesKey, Tag.TAG_COMPOUND);
                List<Cuboid> decoded = new ArrayList<>(boxes.size());
                for (int index = 0; index < boxes.size(); index++) {
                    CompoundTag entry = boxes.getCompound(index);
                    decoded.add(Cuboid.of(point(entry, minKey), point(entry, maxKey)));
                }
                return CuboidSet.of(decoded);
            }
        };
    }

    private static int[] point(LatticePoint point) {
        return new int[]{point.x(), point.y(), point.z()};
    }

    private static LatticePoint point(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) {
            throw new IllegalArgumentException(key + " must hold three ints");
        }
        return LatticePoint.of(values[0], values[1], values[2]);
    }

    private static void require(CompoundTag tag, String key, int tagType) {
        if (!tag.contains(key, tagType)) {
            throw new IllegalArgumentException("missing context value tag " + key);
        }
    }

    private ContextCodecs() {}
}
