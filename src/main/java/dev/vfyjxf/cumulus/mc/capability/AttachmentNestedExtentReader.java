package dev.vfyjxf.cumulus.mc.capability;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Maps one host attachment value to a nested extent; a null result is an absent extent. */
@NullMarked
@FunctionalInterface
public interface AttachmentNestedExtentReader<T> {

    @Nullable
    NestedExtent read(T data);
}
