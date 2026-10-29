package dev.vfyjxf.cumulus.mc.capability;

import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Resolves the attachment holder of one subject; a null result means the subject has none. */
@NullMarked
@FunctionalInterface
public interface AttachmentHolder<H> {

    @Nullable
    IAttachmentHolder holder(H actual);
}
