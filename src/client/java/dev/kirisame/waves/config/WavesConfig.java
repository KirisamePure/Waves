package dev.kirisame.waves.config;

public final class WavesConfig {
    public boolean enabled = true;
    public boolean seaLevelOnly = true;
    public boolean overworldOnly = true;
    public double amplitude = .32;
    public double wavelength = 24;
    public double speed = 1;
    public double direction = 35;
    public double sharpness = .65;
    public double rainMultiplier = 1.6;
    public double thunderMultiplier = 5;
    public double transitionSeconds = 8;
    public double seaLevel = 63;
    public double irregularity = .75;
    public boolean shorelineEnabled = true;
    public double shoreFade = 3;
    public double reflection = .35;
    public double foam = .8;
    public boolean splashes = true;
    public double splashDensity = .6;

    public void sanitize() {
        amplitude = bounded(amplitude, .32, 0, 2);
        wavelength = bounded(wavelength, 24, 8, 96);
        speed = bounded(speed, 1, 0, 4);
        direction = bounded(direction, 35, 0, 360);
        sharpness = bounded(sharpness, .65, 0, 1);
        rainMultiplier = bounded(rainMultiplier, 1.6, 1, 5);
        thunderMultiplier = bounded(thunderMultiplier, 5, rainMultiplier, 10);
        transitionSeconds = bounded(transitionSeconds, 8, .25, 30);
        seaLevel = bounded(seaLevel, 63, -64, 320);
        irregularity = bounded(irregularity, .75, 0, 1);
        shoreFade = bounded(shoreFade, 3, .5, 8);
        reflection = bounded(reflection, .35, 0, 1);
        foam = bounded(foam, .8, 0, 1.5);
        splashDensity = bounded(splashDensity, .6, 0, 1);
    }

    private static double bounded(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
