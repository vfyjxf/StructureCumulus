package dev.vfyjxf.cumulus.mc.preview.client.cine;

/**
 * Easing curves over a clamped unit interval. Every curve is a total function so a tween can never
 * blow up on an out-of-range tick; overshoot lives only in {@link #outBack}.
 */
public enum Ease {
    linear, smoothstep, inOutCubic, outCubic, outQuad, outBack;

    public final double applyClamped(double t) {
        return apply(Math.min(1.0, Math.max(0.0, t)));
    }

    private double apply(double t) {
        return switch (this) {
            case linear -> t;
            case smoothstep -> t * t * (3.0 - 2.0 * t);
            case inOutCubic -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case outCubic -> 1.0 - Math.pow(1.0 - t, 3.0);
            case outQuad -> 1.0 - (1.0 - t) * (1.0 - t);
            case outBack -> {
                double c1 = 1.70158;
                double c3 = c1 + 1.0;
                double u = t - 1.0;
                yield 1.0 + c3 * u * u * u + c1 * u * u;
            }
        };
    }
}
