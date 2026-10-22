package dev.kirisame.waves.math;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Ocean-fed hydraulic head on a surface graph. Dry islands cannot seed their own waves. */
public final class RunupFlow {
    public record Surface(float[] head, float[] potential, float[] distance) {}
    private record Front(int node, float head, float distance) {}
    private record Arrival(int node, double time) {}
    private RunupFlow() {}

    public static Surface solve(float[] ground, boolean[] ocean, float[] boundary, int[][] neighbors,
                                float spacing, float reach, float loss) {
        int count = ground.length;
        if (ocean.length != count || boundary.length != count || neighbors.length != count || spacing <= 0)
            throw new IllegalArgumentException("Invalid shore graph");
        float[] head = new float[count], potential = new float[count], distance = new float[count];
        Arrays.fill(head, Float.NEGATIVE_INFINITY);
        Arrays.fill(potential, Float.NEGATIVE_INFINITY);
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        var fronts = new PriorityQueue<Front>(Comparator.comparingDouble(Front::head).reversed());
        for (int i = 0; i < count; i++) if (ocean[i] && Float.isFinite(boundary[i])) {
            head[i] = potential[i] = boundary[i]; distance[i] = 0;
            for (int j : neighbors[i]) if (j >= 0 && !ocean[j]) { fronts.add(new Front(i, head[i], 0)); break; }
        }
        while (!fronts.isEmpty()) {
            Front front = fronts.remove();
            int i = front.node;
            if (front.head != head[i] || front.distance != distance[i]) continue;
            float nextDistance = front.distance + spacing;
            if (nextDistance > reach) continue;
            for (int j : neighbors[i]) {
                if (j < 0 || ocean[j]) continue; // Dirichlet boundary: never lift the original ocean edge.
                float incoming = front.head - loss * spacing;
                potential[j] = Math.max(potential[j], incoming);
                if (incoming <= ground[j] + .001f) continue;
                if (incoming > head[j] || (incoming == head[j] && nextDistance < distance[j])) {
                    head[j] = incoming; distance[j] = nextDistance;
                    fronts.add(new Front(j, incoming, nextDistance));
                }
            }
        }
        return new Surface(head, potential, distance);
    }

    /** Advect a wet front from existing ocean-connected water; a newly opened inland region takes time to fill. */
    public static double[] arrivals(float[] head, boolean[] ocean, int[][] neighbors, double[] previous,
                                    double now, double spacing, double velocity) {
        double[] arrival = new double[head.length];
        Arrays.fill(arrival, Double.POSITIVE_INFINITY);
        var queue = new PriorityQueue<Arrival>(Comparator.comparingDouble(Arrival::time));
        for (int i = 0; i < head.length; i++) if (Float.isFinite(head[i])) {
            arrival[i] = ocean[i] ? now : previous[i];
            if (Double.isFinite(arrival[i])) queue.add(new Arrival(i, arrival[i]));
        }
        double travel = spacing / Math.max(.1, velocity);
        while (!queue.isEmpty()) {
            Arrival front = queue.remove();
            if (front.time != arrival[front.node]) continue;
            for (int j : neighbors[front.node]) {
                if (j < 0 || ocean[j] || !Float.isFinite(head[j])) continue;
                double next = Math.max(now, front.time) + travel;
                if (next < arrival[j]) { arrival[j] = next; queue.add(new Arrival(j, next)); }
            }
        }
        return arrival;
    }

    /** Validate the visible front too: pressure connectivity alone is insufficient during delayed filling/retreat. */
    public static boolean[] visible(float[] head, float[] ground, boolean[] ocean, int[][] neighbors) {
        boolean[] connected = new boolean[head.length];
        int[] queue = new int[head.length]; int first = 0, last = 0;
        for (int i = 0; i < head.length; i++) if (ocean[i] && Float.isFinite(head[i])) {
            connected[i] = true; queue[last++] = i;
        }
        while (first < last) {
            int i = queue[first++];
            for (int j : neighbors[i]) if (j >= 0 && !connected[j] && Float.isFinite(head[j]) && head[j] > ground[j] + .001f) {
                connected[j] = true; queue[last++] = j;
            }
        }
        return connected;
    }

    /** Original ocean triangles use this diagonal; shoreline edges reduce to exact linear interpolation. */
    public static double triangle(double h00, double h10, double h01, double h11, double x, double z) {
        return x + z <= 1 ? h00 + x * (h10 - h00) + z * (h01 - h00)
                : h11 + (1 - x) * (h01 - h11) + (1 - z) * (h10 - h11);
    }
}
