package dev.vfyjxf.cumulus.mc.capability;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Binds one attachment type, one holder adapter and one reader/writer pair into a provider. */
@NullMarked
public final class AttachmentNestedExtentProviders {

    public static <H, T> AttachmentNestedExtentProvider<H, T> readOnly(
        NestedExtentHolder<H> holder,
        AttachmentType<T> type,
        AttachmentHolder<H> holderOf,
        AttachmentNestedExtentReader<T> reader
    ) {
        return new Bound<>(holder, type, holderOf, reader, null);
    }

    public static <H, T> AttachmentNestedExtentProvider<H, T> readWrite(
        NestedExtentHolder<H> holder,
        AttachmentType<T> type,
        AttachmentHolder<H> holderOf,
        AttachmentNestedExtentReader<T> reader,
        AttachmentNestedExtentWriter<T> writer
    ) {
        return new Bound<>(holder, type, holderOf, reader, writer);
    }

    /** The standard block-entity adapter: the context's already-read block entity, null-safe. */
    public static AttachmentHolder<BlockNestedExtentContext> blockEntity() {
        return BlockNestedExtentContext::blockEntity;
    }

    /** The standard entity adapter: a loaded entity is its own attachment holder. */
    public static AttachmentHolder<Entity> entity() {
        return actual -> actual;
    }

    private record Bound<H, T>(
        NestedExtentHolder<H> holder,
        AttachmentType<T> type,
        AttachmentHolder<H> holderOf,
        AttachmentNestedExtentReader<T> reader,
        @Nullable AttachmentNestedExtentWriter<T> writer
    ) implements AttachmentNestedExtentProvider<H, T> {

        private Bound {
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(holderOf, "holderOf");
            Objects.requireNonNull(reader, "reader");
        }

        @Override
        public boolean hasData(H actual) {
            IAttachmentHolder holder = holderOf.holder(actual);
            return holder != null && holder.hasData(type);
        }

        @Override
        public @Nullable NestedExtent getExistingDataOrNull(H actual) {
            IAttachmentHolder holder = holderOf.holder(actual);
            if (holder == null) {
                return null;
            }
            T data = holder.getExistingDataOrNull(type);
            return data == null ? null : reader.read(data);
        }

        @Override
        public @Nullable NestedExtent getData(H actual) {
            IAttachmentHolder holder = holderOf.holder(actual);
            return holder == null ? null : reader.read(holder.getData(type));
        }

        @Override
        public @Nullable NestedExtent setData(H actual, NestedExtent value) {
            AttachmentNestedExtentWriter<T> writer = this.writer;
            if (writer == null) {
                throw new UnsupportedOperationException("the attachment provider for " + holder.id() + " is read-only");
            }
            IAttachmentHolder holder = holderOf.holder(actual);
            if (holder == null) {
                return null;
            }
            T raw = Objects.requireNonNull(writer.write(value), "the attachment writer answered null");
            T previous = holder.setData(type, raw);
            return previous == null ? null : reader.read(previous);
        }

        @Override
        public @Nullable NestedExtent removeData(H actual) {
            IAttachmentHolder holder = holderOf.holder(actual);
            if (holder == null) {
                return null;
            }
            T previous = holder.removeData(type);
            return previous == null ? null : reader.read(previous);
        }
    }

    private AttachmentNestedExtentProviders() {}
}
