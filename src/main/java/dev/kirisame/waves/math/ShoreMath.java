package dev.kirisame.waves.math;

import java.util.Arrays;

/** Bounded chamfer distance transform. Unknown/unloaded cells are never obstacles. */
public final class ShoreMath {
    public static final float MAX_DISTANCE = 12;
    private static final float DIAGONAL = (float) Math.sqrt(2);
    private ShoreMath() {}

    public static float[] distances(boolean[] solid, int size) {
        if (solid.length != size * size || size < 1) throw new IllegalArgumentException("Invalid shore grid");
        float[] result = new float[solid.length];
        Arrays.fill(result, MAX_DISTANCE);
        for (int i = 0; i < solid.length; i++) if (solid[i]) result[i] = 0;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int i = z * size + x;
            if (x > 0) result[i] = Math.min(result[i], result[i - 1] + 1);
            if (z > 0) {
                result[i] = Math.min(result[i], result[i - size] + 1);
                if (x > 0) result[i] = Math.min(result[i], result[i - size - 1] + DIAGONAL);
                if (x + 1 < size) result[i] = Math.min(result[i], result[i - size + 1] + DIAGONAL);
            }
        }
        for (int z = size - 1; z >= 0; z--) for (int x = size - 1; x >= 0; x--) {
            int i = z * size + x;
            if (x + 1 < size) result[i] = Math.min(result[i], result[i + 1] + 1);
            if (z + 1 < size) {
                result[i] = Math.min(result[i], result[i + size] + 1);
                if (x > 0) result[i] = Math.min(result[i], result[i + size - 1] + DIAGONAL);
                if (x + 1 < size) result[i] = Math.min(result[i], result[i + size + 1] + DIAGONAL);
            }
        }
        return result;
    }

    public static double attenuation(double distance, double fadeDistance) {
        double t = Math.clamp(distance / Math.max(.5, fadeDistance * .35), 0, 1);
        return .72 + .28 * t * t * (3 - 2 * t);
    }
}
