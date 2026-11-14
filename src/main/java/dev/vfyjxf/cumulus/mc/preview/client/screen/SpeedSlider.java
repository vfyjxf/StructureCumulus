package dev.vfyjxf.cumulus.mc.preview.client.screen;

/**
 * The player group's speed slider (design 17 §4.5, §7.5): the one config key's range, a quarter step
 * and the label a value reads as. The value is a step index, so every value is exact and round trips.
 */
record SpeedSlider(int steps) {

    /** The key's own range and the slider's step (design 17 §7.5, D-114). */
    static final double min = 0.25D;
    static final double max = 4.0D;
    static final double step = 0.25D;
    static final int maxSteps = (int) Math.round((max - min) / step);

    SpeedSlider {
        steps = Math.max(0, Math.min(maxSteps, steps));
    }

    /** The nearest quarter step a config value asks for, clamped to the key's own range. */
    static SpeedSlider of(double speed) {
        double clamped = Math.max(min, Math.min(max, speed));
        return new SpeedSlider((int) Math.round((clamped - min) / step));
    }

    /** The step a position along the track asks for: 0 is the floor, 1 the ceiling. */
    static SpeedSlider at(double fraction) {
        double clamped = Math.max(0.0D, Math.min(1.0D, fraction));
        return new SpeedSlider((int) Math.round(clamped * maxSteps));
    }

    double value() {
        return min + steps * step;
    }

    /** The thumb's share of the track: the value's own position, 0 at the floor. */
    double fraction() {
        return (double) steps / maxSteps;
    }

    /** The value as the slider's label reads it: {@code 1.5×}, {@code 0.25×}, {@code 4.0×}. */
    String label() {
        int hundredths = (int) Math.round(value() * 100.0D);
        int rest = hundredths % 100;
        String decimals = rest % 10 == 0
                ? Integer.toString(rest / 10)
                : rest < 10 ? "0" + rest : Integer.toString(rest);
        return (hundredths / 100) + "." + decimals + "\u00D7";
    }
}
