package dev.vfyjxf.cumulus.kernel.base;

public record SourceSpan(NamespacedId document, int start, int end) {

    public SourceSpan {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end " + end + " precedes start " + start);
        }
    }

    public static SourceSpan of(NamespacedId document, int start, int end) {
        return new SourceSpan(document, start, end);
    }

    public int length() {
        return end - start;
    }

    public boolean contains(SourceSpan other) {
        return start <= other.start && other.end <= end;
    }

    public boolean contains(int offset) {
        return start <= offset && offset < end;
    }
}
