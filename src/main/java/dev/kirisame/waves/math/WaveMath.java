package dev.kirisame.waves.math;

/** Shared double-precision phase math; shader coordinates remain camera-relative. */
public final class WaveMath {
    public static final double TAU = Math.PI * 2;
    public static final double[] LENGTHS = {1, .57, .29};
    public static final double[] ANGLES = {0, .85, -1.15};
    public static final double[] WEIGHTS = {.62, .26, .12};

    private WaveMath() {}

    public static double phase(int component, double x, double z, double seconds,
                               double wavelength, double direction, double speed) {
        double k = TAU / (wavelength * LENGTHS[component]);
        double angle = Math.toRadians(direction) + ANGLES[component];
        return Math.IEEEremainder(k * (Math.cos(angle) * x + Math.sin(angle) * z)
                - Math.sqrt(9.81 * k) * seconds * speed, TAU);
    }

    public static double height(double x, double z, double seconds, double amplitude,
                                double wavelength, double direction, double speed, double sharpness) {
        double h = 0;
        for (int i = 0; i < 3; i++) {
            double p = phase(i, x, z, seconds, wavelength, direction, speed);
            h += WEIGHTS[i] * (Math.sin(p) + sharpness * .25 * Math.sin(2 * p));
        }
        return amplitude * h;
    }

    public static double approach(double current, double target, double seconds, double transition) {
        return current + (target - current) * -Math.expm1(-seconds / Math.max(.05, transition));
    }

    public static double weatherMultiplier(double rain, double thunder, double rainMultiplier,
                                           double thunderMultiplier) {
        double wet = Math.clamp(rain, 0, 1);
        double storm = Math.clamp(thunder, 0, 1);
        return 1 + wet * (rainMultiplier - 1) + storm * (thunderMultiplier - rainMultiplier);
    }
}
