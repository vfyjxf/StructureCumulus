package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The variant group a session or a catalog entry belongs to: the group id and its members in
 * declaration order. A standalone blueprint carries no summary. The document schema bounds the
 * declaration (at most 16 members, 128-character labels), so the summary needs no payload cap of its
 * own — an over-cap declaration is a compile finding, never a truncated list (design 15 §12).
 */
public record VariantSummary(NamespacedId group, List<Member> members) {

    public VariantSummary {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(members, "members");
        members = List.copyOf(members);
        if (members.size() < 2) {
            throw new IllegalArgumentException("a variant group has at least two members");
        }
    }

    /** The member with that id, or null when the group does not name it. */
    public @Nullable Member member(NamespacedId id) {
        for (Member member : members) {
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

    /** One member: its blueprint document id and the carried label, absent when none was declared. */
    public record Member(NamespacedId id, @Nullable String label) {

        public Member {
            Objects.requireNonNull(id, "id");
        }

        /** The display text: the label, or the id when the declaration carried none. */
        public String text() {
            return label == null ? id.text() : label;
        }
    }
}
