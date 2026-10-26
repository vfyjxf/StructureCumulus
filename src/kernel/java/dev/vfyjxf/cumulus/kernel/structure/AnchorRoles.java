package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
/** The roles the kernel reserves; a custom role is a plain id. */
public final class AnchorRoles {

    public static final NamespacedId PRIMARY = NamespacedId.of("cumulus", "primary");
    public static final NamespacedId FRONT = NamespacedId.of("cumulus", "front");
    public static final NamespacedId CENTER = NamespacedId.of("cumulus", "center");

    private AnchorRoles() {
    }
}
