package dev.vfyjxf.cumulus.kernel.base;

public record FindingId(int value) {

    public static final FindingId NONE = new FindingId(0);

    public FindingId {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative: " + value);
        }
    }

    public static FindingId of(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive: " + value);
        }
        return new FindingId(value);
    }

    public boolean assigned() {
        return value > 0;
    }
}
