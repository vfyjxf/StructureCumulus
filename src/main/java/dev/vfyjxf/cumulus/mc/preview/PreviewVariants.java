package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.VariantGroup;
import dev.vfyjxf.cumulus.kernel.structure.VariantMember;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one conversion from the compiled carry into the wire summary, plus the switch arithmetic that
 * both the session and the catalog share (design 15 §12). The order is the declaration order, so a
 * switch is deterministic.
 */
final class PreviewVariants {

    private PreviewVariants() {}

    /** The blueprint's group summary, or null when it is not a member of one. */
    static @Nullable VariantSummary summary(Blueprint blueprint) {
        VariantGroup group = blueprint.variants();
        if (group == null) {
            return null;
        }
        List<VariantSummary.Member> members = new ArrayList<>(group.members().size());
        for (VariantMember member : group.members()) {
            members.add(new VariantSummary.Member(member.id(), member.label()));
        }
        return new VariantSummary(group.group(), members);
    }

    /** The member after the current one, wrapping; null when the summary does not hold the current id. */
    static @Nullable NamespacedId next(VariantSummary summary, NamespacedId current, int step) {
        int index = summary.indexOf(current);
        if (index < 0) {
            return null;
        }
        int size = summary.members().size();
        int next = Math.floorMod(index + step, size);
        return next == index ? null : summary.members().get(next).id();
    }
}
