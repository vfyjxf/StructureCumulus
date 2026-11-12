package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The document's {@code variants} declaration: the group id and its members in declaration order.
 * The declaration is repeated verbatim on every member document, so a compiled member always knows
 * its own group without a cross-document lookup (design 15 §12, D-98).
 */
public record VariantGroupSpec(NamespacedId group, List<VariantMemberSpec> members, @Nullable SourceSpan source) {

    public VariantGroupSpec {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(members, "members");
        members = List.copyOf(members);
    }

    /** The content-only form used for round-trip and cross-document comparison. */
    public VariantGroupSpec withoutSpans() {
        List<VariantMemberSpec> stripped = new ArrayList<>(members.size());
        for (VariantMemberSpec member : members) {
            stripped.add(member.withoutSpans());
        }
        return new VariantGroupSpec(group, stripped, null);
    }

    /** True when both documents declare the same group id and the same members in the same order. */
    boolean sameDeclaration(VariantGroupSpec other) {
        return withoutSpans().equals(other.withoutSpans());
    }
}
