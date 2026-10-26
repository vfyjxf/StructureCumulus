package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

/** The roles the kernel reserves; a custom role is a plain id. */
public final class AnchorRoles {

    public static final NamespacedId primary = NamespacedId.of("primary");
    public static final NamespacedId front = NamespacedId.of("front");
    public static final NamespacedId center = NamespacedId.of("center");

    private AnchorRoles() {}
}
