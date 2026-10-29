package dev.vfyjxf.cumulus.mc.capability;

import net.neoforged.neoforge.attachment.AttachmentType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One attachment source bound to exactly one holder id; it mirrors {@code IAttachmentHolder}. */
@NullMarked
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
