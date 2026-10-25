package dev.vfyjxf.cumulus.kernel.context;

public enum AugmentFold {
    SUM,
    MAX,
    MIN,
    MULTIPLY,
    SET;

    public double combine(double running, double amount) {
        return switch (this) {
            case SUM -> running + amount;
            case MAX -> Math.max(running, amount);
            case MIN -> Math.min(running, amount);
            case MULTIPLY -> running * amount;
            case SET -> amount;
        };
    }
}
