package dev.vfyjxf.cumulus.kernel.core;

import java.util.Objects;

/** A half-open character range in one document. */
public record SourceSpan(NamespacedId document, int start, int end) {

    public SourceSpan {
        Objects.requireNonNull(document, "document");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("0 <= start <= end");
        }
    }

    public int length() {
        return end - start;
    }

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }
}
