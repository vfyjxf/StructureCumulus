package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One member of a {@code variants} declaration: a blueprint document id and its optional label.
 * The label is presentation text and shares the carry law of {@code description}: copied, never
 * interpreted (design 15 §12, D-94/D-98).
 */
public record VariantMemberSpec(NamespacedId id, @Nullable String label, @Nullable SourceSpan source) {

    public VariantMemberSpec {
        Objects.requireNonNull(id, "id");
    }

    public VariantMemberSpec withoutSpans() {
        return source == null ? this : new VariantMemberSpec(id, label, null);
    }
}
