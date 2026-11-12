package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.pref.BuildPreset;
import dev.vfyjxf.cumulus.mc.pref.RemovalPolicy;

import java.util.Objects;

/**
 * The build context a live preview session holds: the blueprint id, the pose, the item-source form and
 * the removal policy. The preset save verb copies it, so nothing outside the preview package reaches
 * into the session object (design 15 §13.1).
 */
public record PreviewContext(
    NamespacedId blueprintId,
    Siting siting,
    BuildPreset.Source source,
    RemovalPolicy removalPolicy
) {

    public PreviewContext {
        Objects.requireNonNull(blueprintId, "blueprintId");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(removalPolicy, "removalPolicy");
    }
}
