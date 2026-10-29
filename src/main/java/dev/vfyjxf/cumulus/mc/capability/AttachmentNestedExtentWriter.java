package dev.vfyjxf.cumulus.mc.capability;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Maps a nested extent to one host attachment value; null means the writer accepted no value. */
@NullMarked
@FunctionalInterface
public interface AttachmentNestedExtentWriter<T> {

    @Nullable
    T write(NestedExtent extent);
}
