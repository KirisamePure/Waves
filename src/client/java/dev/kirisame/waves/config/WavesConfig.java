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

    public double splashHeight = 1;
    public double splashSize = 1;
    public boolean sounds = true;
    public double soundVolume = .7;
    public double soundDistance = 24;

    public boolean runupEnabled = true;
    public double runupReach = 4;
    public double runupOpacity = .65;
    public double runupRetreat = .7;
    public boolean windEnabled = true;
    public double windVolume = .7;
    public double windRain = .35;
    public double windThunder = 1;
    public double windGusts = .35;
    public double windPitch = .9;
    public boolean windShelter = true;

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
        splashHeight = bounded(splashHeight, 1, .25, 3);
        splashSize = bounded(splashSize, 1, .5, 3);
        soundVolume = bounded(soundVolume, .7, 0, 1);
        soundDistance = bounded(soundDistance, 24, 8, 28);
        runupReach = bounded(runupReach, 4, 1, 8);
        runupOpacity = bounded(runupOpacity, .65, .1, 1);
        runupRetreat = bounded(runupRetreat, .7, .05, 4);
        windVolume = bounded(windVolume, .7, 0, 1);
        windRain = bounded(windRain, .35, 0, 1);
        windThunder = bounded(windThunder, 1, windRain, 2);
        windGusts = bounded(windGusts, .35, 0, 1);
        windPitch = bounded(windPitch, .9, .6, 1.4);
    }

    private static double bounded(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
