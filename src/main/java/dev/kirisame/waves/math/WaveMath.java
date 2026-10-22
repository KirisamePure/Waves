package dev.kirisame.waves.math;

/** Shared double-precision phase math; shader coordinates remain camera-relative. */
public final class WaveMath {
    public static final double TAU = Math.PI * 2;
    public static final int COMPONENTS = 8;
    public static final double[] LENGTHS = {1, .743, .527, .389, .281, .197, .143, .103};
    public static final double[] ANGLES = {0, .41, -.72, 1.17, -1.63, .83, 2.35, -2.71};
    public static final double[] WEIGHTS = {.28, .22, .17, .12, .085, .06, .04, .025};
    public static final double[] OFFSETS = {0, 1.37, 4.51, 2.83, 5.72, .64, 3.9, 5.04};

    private WaveMath() {}

    public static double windStrength(double rain, double thunder, double rainStrength, double stormStrength) {
        rain = Math.clamp(rain, 0, 1); thunder = Math.clamp(thunder, 0, 1);
        return rain * rainStrength + thunder * (stormStrength - rainStrength);
    }

    public static double phase(int component, double x, double z, double seconds,
                               double wavelength, double direction, double speed) {
        return phase(component, x, z, seconds, wavelength, direction, speed, .75);
    }

    public static double waveNumber(int component, double wavelength) {
        return TAU / Math.max(3.5, wavelength * LENGTHS[component]);
    }

    public static double angle(int component, double direction, double irregularity) {
        return Math.toRadians(direction) + ANGLES[component] * (.3 + irregularity * 1.3);
    }

    public static double phase(int component, double x, double z, double seconds,
                               double wavelength, double direction, double speed, double irregularity) {
        double k = waveNumber(component, wavelength);
        double angle = angle(component, direction, irregularity);
        return Math.IEEEremainder(k * (Math.cos(angle) * x + Math.sin(angle) * z)
                - Math.sqrt(9.81 * k) * seconds * speed + OFFSETS[component], TAU);
    }

    public static double height(double x, double z, double seconds, double amplitude,
                                double wavelength, double direction, double speed, double sharpness) {
        return height(x, z, seconds, amplitude, wavelength, direction, speed, sharpness, .75);
    }

    public static double height(double x, double z, double seconds, double amplitude,
                                double wavelength, double direction, double speed, double sharpness, double irregularity) {
        double[] phases = new double[COMPONENTS];
        for (int i = 0; i < COMPONENTS; i++) phases[i] = phase(i, x, z, seconds, wavelength, direction, speed, irregularity);
        return amplitude * normalizedHeight(phases, sharpness, irregularity);
    }

    /** Per-tick sampler reuses coefficients and scratch phases across many shore vertices. */
    public static final class Sampler {
        private final double[] kx = new double[COMPONENTS], kz = new double[COMPONENTS], omega = new double[COMPONENTS];
        private final double[] base = new double[COMPONENTS], scratch = new double[COMPONENTS];
        private final double amplitude, sharpness, irregularity;
        public Sampler(double seconds, double amplitude, double wavelength, double direction, double speed,
                       double sharpness, double irregularity) {
            this.amplitude = amplitude; this.sharpness = sharpness; this.irregularity = irregularity;
            for (int i = 0; i < COMPONENTS; i++) {
                double k = waveNumber(i, wavelength), a = angle(i, direction, irregularity);
                kx[i] = k * Math.cos(a); kz[i] = k * Math.sin(a);
                omega[i] = Math.sqrt(9.81 * k) * speed;
                base[i] = Math.IEEEremainder(-omega[i] * seconds + OFFSETS[i], TAU);
            }
        }
        public double phase(int i, double x, double z, double delay) {
            return Math.IEEEremainder(kx[i] * x + kz[i] * z + base[i] + omega[i] * delay, TAU);
        }
        public double height(double x, double z, double delay) {
            for (int i = 0; i < COMPONENTS; i++) scratch[i] = Math.IEEEremainder(kx[i] * x + kz[i] * z + base[i] + omega[i] * delay, TAU);
            return amplitude * normalizedHeight(scratch, sharpness, irregularity);
        }
    }

    public static double normalizedHeight(double[] phases, double sharpness, double irregularity) {
        double h = 0;
        for (int i = 0; i < COMPONENTS; i++) {
            double warp = phases[(i + 3) % COMPONENTS] + phases[(i + 5) % COMPONENTS];
            double p = phases[i] + .45 * irregularity * Math.sin(warp);
            h += WEIGHTS[i] * (Math.sin(p) + sharpness * .2 * Math.sin(2 * p));
        }
        return h * (1 + .25 * irregularity * Math.sin(phases[6] - phases[7]));
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
