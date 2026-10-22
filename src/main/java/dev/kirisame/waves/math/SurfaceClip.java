package dev.kirisame.waves.math;

/** Allocation-free polygon clipping against a linearly interpolated scalar half-space. */
public final class SurfaceClip {
    private SurfaceClip() {}
    public static int clip(float[][] input, int count, int scalar, float[][] output) {
        if (count == 0) return 0;
        int written = 0;
        float[] previous = input[count - 1];
        boolean previousInside = previous[scalar] >= 0;
        for (int i = 0; i < count; i++) {
            float[] current = input[i];
            boolean inside = current[scalar] >= 0;
            if (inside != previousInside) {
                float t = previous[scalar] / (previous[scalar] - current[scalar]);
                float[] vertex = output[written++];
                for (int j = 0; j < current.length; j++) vertex[j] = previous[j] + t * (current[j] - previous[j]);
                vertex[scalar] = 0;
            }
            if (inside) System.arraycopy(current, 0, output[written++], 0, current.length);
            previous = current; previousInside = inside;
        }
        return written;
    }
}
