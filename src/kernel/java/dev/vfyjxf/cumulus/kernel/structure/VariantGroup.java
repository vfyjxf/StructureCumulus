package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The variant group a compiled blueprint carries, in declaration order: the group id and its
 * members. The group is a static combination of blueprint documents, not a parameterized form; the
 * kernel carries it and never interprets it, and it is part of the compiled value's equality
 * (design 15 §12, D-98).
 */
public record VariantGroup(NamespacedId group, List<VariantMember> members) {

    public VariantGroup {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(members, "members");
        members = List.copyOf(members);
    }

    /** The member with that id, or null when the group does not name it. */
    public @Nullable VariantMember member(NamespacedId id) {
        for (VariantMember member : members) {
            if (member.id().equals(id)) {
                return member;
            }
        }
        return null;
    }

    /** The member's declaration index, or -1 when the group does not name it. */
    public int indexOf(NamespacedId id) {
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }
}
