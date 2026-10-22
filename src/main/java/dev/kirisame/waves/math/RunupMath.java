package dev.kirisame.waves.math;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Connected overland reach. A higher intervening surface remains a barrier to lower ground behind it. */
public final class RunupMath {
    public record Paths(float[] distance, float[] barrier) {}
    private RunupMath() {}
    public static Paths paths(float[] top, boolean[] water, int size, float sea, int reach) {
        if (top.length != size * size || water.length != top.length) throw new IllegalArgumentException("Invalid run-up grid");
        float[] distance = new float[top.length], barrier = new float[top.length];
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        Arrays.fill(barrier, Float.POSITIVE_INFINITY);
        var queue = new ArrayDeque<Integer>();
        for (int i = 0; i < top.length; i++) if (water[i]) {
            distance[i] = 0; barrier[i] = sea; queue.add(i);
        }
        while (!queue.isEmpty()) {
            int i = queue.removeFirst(), x = i % size, z = i / size;
            if (distance[i] >= reach) continue;
            int[] neighbors = {x > 0 ? i - 1 : -1, x + 1 < size ? i + 1 : -1,
                    z > 0 ? i - size : -1, z + 1 < size ? i + size : -1};
            for (int j : neighbors) {
                if (j < 0 || water[j] || !Float.isFinite(top[j])) continue;
                float d = distance[i] + 1, b = Math.max(barrier[i], top[j]);
                if (d < distance[j] || (d == distance[j] && b < barrier[j])) {
                    distance[j] = d; barrier[j] = b; queue.addLast(j);
                }
            }
        }
        return new Paths(distance, barrier);
    }
    public static double coverage(double level, double ground, double barrier, double distance, double reach) {
        if (level <= barrier - .015 || distance >= reach) return 0;
        double depth = Math.clamp((level - ground + .015) / .1, 0, 1);
        double edge = Math.clamp((reach - distance) / .8, 0, 1);
        return depth * depth * (3 - 2 * depth) * edge * edge * (3 - 2 * edge);
    }
}
