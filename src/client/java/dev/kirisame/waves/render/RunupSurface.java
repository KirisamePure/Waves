package dev.kirisame.waves.render;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ShoreField;
import dev.kirisame.waves.math.RunupMath;
import dev.kirisame.waves.math.RunupFlow;
import dev.kirisame.waves.math.ShoreMath;
import dev.kirisame.waves.math.SurfaceClip;
import dev.kirisame.waves.math.WaveMath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Terrain-clipped run-up with matching ocean boundary heights and masking of obsolete water side walls. */
public final class RunupSurface {
    private static final int SUB = 4, N = SUB + 1, NODES = N * N, ATTRS = 12;
    private static final int DEPTH = 3, REACH = 4;
    private static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "waves:continuous_shore");
    private static final SurfaceMask MASK = new SurfaceMask();
    private static List<Tile> tiles = new ArrayList<>();
    private static BufferAllocator allocator;
    private static boolean ready;
    private static int originX, originZ, wetTiles, lastDrawn, seaTiles;
    private static long drawnQuads;
    private static List<Node> graph = List.of();
    private static int[][] neighbors = new int[0][];
    private static double boundaryError;
    private static int connectedNodes;
    private static double lastFrameTime;
    private static final class Node {
        final int x, z;
        float ground = Float.POSITIVE_INFINITY;
        boolean sea;
        double arrival = Double.POSITIVE_INFINITY;
        float previousHead = Float.NEGATIVE_INFINITY;
        Node(int x, int z) { this.x = x; this.z = z; }
    }
    private record DrawTile(float[] nodes, int color, int light) {}
    private record Frame(List<DrawTile> tiles, Sprite sprite, Sprite foamSprite) {}
    private static final class Tile {
        final int x, z, color, light;
        final boolean sea;
        final float ground;
        final int[] indices = new int[NODES];
        final float[] ocean = new float[NODES], reach = new float[NODES];
        Tile(int x, int z, boolean sea, float ground, int color, int light) {
            this.x = x; this.z = z; this.sea = sea; this.ground = ground; this.color = color; this.light = light;
        }
    }
    private RunupSurface() {}
    public static void reset() { tiles = new ArrayList<>(); ready = false; wetTiles = seaTiles = lastDrawn = 0; drawnQuads = 0; graph = List.of(); neighbors = new int[0][]; connectedNodes = 0; boundaryError = 0; lastFrameTime = 0; }
    public static double boundaryError() { return boundaryError; }
    public static int connectedNodes() { return connectedNodes; }
    public static int candidateCount() { return tiles.size(); }
    public static int wetCount() { return wetTiles; }
    public static int seaCount() { return seaTiles; }
    public static long drawnQuads() { return drawnQuads; }
    public static int lastDrawnQuads() { return lastDrawn; }
    public static int textureId() { return MASK.id(); }
    public static double originRelativeX() { return originX - MinecraftClient.getInstance().gameRenderer.getCamera().getPos().x; }
    public static double originRelativeZ() { return originZ - MinecraftClient.getInstance().gameRenderer.getCamera().getPos().z; }
    public static double replacementEnabled() {
        var c = WavesClient.config;
        return ready && c.enabled && c.runupEnabled && WavesClient.amplitude() > 0
                && (!IrisBridge.shadersEnabled() || (IrisBridge.active() && IrisBridge.surfaceReplacementSupported())) ? 1 : 0;
    }

    public static void rebuild(MinecraftClient client, float[] top, boolean[] sea, int size, int ox, int oz) {
        var c = WavesClient.config;
        if (!c.runupEnabled) { reset(); return; }
        var paths = RunupMath.paths(top, sea, size, (float) (c.seaLevel - .111111), 8);
        boolean[] land = new boolean[top.length];
        float[] ground = top.clone(), ocean = new float[top.length];
        for (int i = 0; i < top.length; i++) {
            land[i] = !sea[i];
            if (sea[i]) {
                if (!Float.isFinite(ground[i])) ground[i] = (float) c.seaLevel - 10;
                ocean[i] = 1;
            } else {
                if (!Float.isFinite(ground[i])) ground[i] = (float) c.seaLevel + 8;
            }
        }
        float[] shore = ShoreMath.distances(land, size);
        var next = new ArrayList<Tile>();
        for (int i = 0; i < top.length; i++) {
            boolean nearWater = sea[i] && shore[i] <= 3;
            boolean dry = !sea[i] && Float.isFinite(top[i]) && paths.distance()[i] <= c.runupReach + 1;
            if (!nearWater && !dry) continue;
            int x = ox + i % size, z = oz + i / size;
            double dx = x + .5 - client.player.getX(), dz = z + .5 - client.player.getZ();
            if (dx * dx + dz * dz > 36 * 36) continue;
            BlockPos pos = BlockPos.ofFloored(x, Math.max(c.seaLevel, ground[i]) + .1, z);
            Tile tile = new Tile(x, z, sea[i], ground[i], BiomeColors.getWaterColor(client.world, pos),
                    WorldRenderer.getLightmapCoordinates(client.world, pos));
            for (int n = 0; n < NODES; n++) {
                double gx = i % size - .5 + (n % N) / (double) SUB;
                double gz = i / size - .5 + (n / N) / (double) SUB;
                tile.ocean[n] = sample(ocean, size, gx, gz, 0);
                tile.reach[n] = Math.max(0, sample(paths.distance(), size, gx, gz, 9) - .5f);
            }
            next.add(tile);
        }
        next.sort(Comparator.comparingDouble(t -> Math.pow(t.x - client.player.getX(), 2) + Math.pow(t.z - client.player.getZ(), 2)));
        next = new ArrayList<>(next.subList(0, Math.min(1024, next.size())));
        byte[] mask = new byte[top.length];
        for (Tile tile : next) { int i = (tile.z - oz) * size + tile.x - ox; mask[i] = (byte) 255; }
        seaTiles = 0;
        var previousNodes = new HashMap<Long, Node>();
        for (Node node : graph) previousNodes.put(key(node.x, node.z), node);
        var indices = new HashMap<Long, Integer>();
        var nodes = new ArrayList<Node>();
        for (Tile tile : next) {
            if (tile.sea) seaTiles++;
            for (int n = 0; n < NODES; n++) {
                int x = tile.x * SUB + n % N, z = tile.z * SUB + n / N;
                long key = key(x, z);
                Integer index = indices.get(key);
                if (index == null) { index = nodes.size(); indices.put(key, index); nodes.add(new Node(x, z)); }
                tile.indices[n] = index;
                Node node = nodes.get(index);
                node.ground = Math.min(node.ground, tile.ground);
                node.sea |= tile.sea;
            }
        }
        neighbors = new int[nodes.size()][4];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            neighbors[i][0] = indices.getOrDefault(key(node.x - 1, node.z), -1);
            neighbors[i][1] = indices.getOrDefault(key(node.x + 1, node.z), -1);
            neighbors[i][2] = indices.getOrDefault(key(node.x, node.z - 1), -1);
            neighbors[i][3] = indices.getOrDefault(key(node.x, node.z + 1), -1);
        }
        for (Node node : nodes) {
            Node previous = previousNodes.get(key(node.x, node.z));
            if (previous != null && previous.ground == node.ground && previous.sea == node.sea) {
                node.arrival = previous.arrival; node.previousHead = previous.previousHead;
            }
        }
        graph = nodes;
        tiles = next; originX = ox; originZ = oz;
        MASK.upload(mask); ready = !tiles.isEmpty();
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static float sample(float[] grid, int size, double x, double z, float fallback) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        return (float) ((cell(grid, size, ix, iz, fallback) * (1 - fx) + cell(grid, size, ix + 1, iz, fallback) * fx) * (1 - fz)
                + (cell(grid, size, ix, iz + 1, fallback) * (1 - fx) + cell(grid, size, ix + 1, iz + 1, fallback) * fx) * fz);
    }
    private static float cell(float[] grid, int size, int x, int z, float fallback) {
        if (x < 0 || z < 0 || x >= size || z >= size) return fallback;
        float value = grid[z * size + x]; return Float.isFinite(value) ? value : fallback;
    }
    private static double smooth(double a, double b, double v) {
        double t = Math.clamp((v - a) / (b - a), 0, 1); return t * t * (3 - 2 * t);
    }
    private static double seaHeight(WaveMath.Sampler sampler, double x, double z, double time, double amplitude) {
        var c = WavesClient.config;
        double h = sampler.height(x, z, 0);
        if (c.shorelineEnabled) {
            double d = ShoreField.surfaceDistance(x, z);
            h *= ShoreMath.attenuation(d, c.shoreFade);
            double phase = d * 1.7 - time * c.speed * 1.9
                    + .7 * Math.sin(sampler.phase(2, x, z, 0) + sampler.phase(4, x, z, 0));
            h += amplitude * .22 * c.reflection * Math.exp(-d * .45) * smooth(0, .9, d) * Math.sin(phase);
        }
        return c.seaLevel - .112111 + h; // Sodium lowers fluid tops by 0.001 before encoding.
    }
    public static double sampleOceanVertex(double x, double z) {
        var c = WavesClient.config;
        double time = WavesClient.seconds(), amplitude = WavesClient.amplitude();
        return seaHeight(new WaveMath.Sampler(time, amplitude, c.wavelength, c.direction, c.speed, c.sharpness, c.irregularity),
                x, z, time, amplitude);
    }

    private static float oceanHeight(WaveMath.Sampler sampler, double x, double z, double time, double amplitude,
                                     HashMap<Long, Double> corners) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double h00 = corners.computeIfAbsent(key(ix, iz), ignored -> seaHeight(sampler, ix, iz, time, amplitude));
        double h10 = corners.computeIfAbsent(key(ix + 1, iz), ignored -> seaHeight(sampler, ix + 1, iz, time, amplitude));
        double h01 = corners.computeIfAbsent(key(ix, iz + 1), ignored -> seaHeight(sampler, ix, iz + 1, time, amplitude));
        double h11 = corners.computeIfAbsent(key(ix + 1, iz + 1), ignored -> seaHeight(sampler, ix + 1, iz + 1, time, amplitude));
        return (float) RunupFlow.triangle(h00, h10, h01, h11, x - ix, z - iz);
    }

    static RenderLayer waterLayer() {
        // Block shader vertex alpha can encode ambient occlusion. A free water sheet needs
        // the entity shader transparency contract when a shader pack replaces vanilla programs.
        return IrisBridge.shadersEnabled()
                ? RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                : RenderLayer.getTranslucentMovingBlock();
    }

    public static void initialize() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            var c = WavesClient.config; var result = new ArrayList<DrawTile>();
            var camera = context.camera().getPos();
            wetTiles = 0; boundaryError = 0; connectedNodes = 0;
            if (replacementEnabled() > .5) {
                // Evaluate at the same render time as the terrain uniforms: no tick-lagged shoreline endpoints.
                double time = WavesClient.seconds(), amplitude = WavesClient.amplitude();
                var sampler = new WaveMath.Sampler(time, amplitude, c.wavelength, c.direction, c.speed, c.sharpness, c.irregularity);
                int count = graph.size();
                float[] ground = new float[count], boundary = new float[count]; boolean[] sea = new boolean[count];
                var corners = new HashMap<Long, Double>();
                for (int i = 0; i < count; i++) {
                    Node node = graph.get(i); ground[i] = node.ground; sea[i] = node.sea;
                    if (node.sea) boundary[i] = oceanHeight(sampler, node.x / (double) SUB, node.z / (double) SUB, time, amplitude, corners);
                }
                var flow = RunupFlow.solve(ground, sea, boundary, neighbors, 1f / SUB, (float) c.runupReach, .035f);
                double[] previousArrival = new double[count];
                for (int i = 0; i < count; i++) previousArrival[i] = graph.get(i).arrival;
                double velocity = Math.clamp(Math.sqrt(9.81 * amplitude) * 1.5, 2, 10);
                double[] arrival = RunupFlow.arrivals(flow.head(), sea, neighbors, previousArrival, time, 1.0 / SUB, velocity);
                double dt = Math.clamp(time - lastFrameTime, 0, .1); lastFrameTime = time;
                for (int i = 0; i < count; i++) {
                    Node node = graph.get(i); node.arrival = arrival[i];
                    float head = flow.head()[i];
                    if (Float.isFinite(head) && !sea[i]) {
                        double front = smooth(0, .25 / velocity, time - arrival[i]);
                        head = (float) (ground[i] + (head - ground[i]) * front - .002 * (1 - front));
                        if (Float.isFinite(node.previousHead) && node.previousHead > head && time > arrival[i])
                            head = (float) WaveMath.approach(node.previousHead, head, dt, c.runupRetreat * .25);
                        flow.head()[i] = head;
                    }
                    node.previousHead = head;
                }
                boolean[] visibleFront = RunupFlow.visible(flow.head(), ground, sea, neighbors);
                connectedNodes = 0;
                for (int i = 0; i < count; i++) if (!sea[i] && flow.head()[i] > ground[i] + .001f) {
                    if (visibleFront[i]) connectedNodes++;
                    else {
                        flow.head()[i] = Float.NEGATIVE_INFINITY;
                        flow.potential()[i] = Math.min(flow.potential()[i], ground[i] - .002f);
                        graph.get(i).previousHead = Float.NEGATIVE_INFINITY;
                        graph.get(i).arrival = Double.POSITIVE_INFINITY;
                    }
                }
                for (Tile tile : tiles) {
                    // A half-block apron feathers over the original ocean, sealing the shared edge without replacing its material.
                    if (tile.sea) {
                        boolean coast = false;
                        for (float fraction : tile.ocean) coast |= fraction < .999;
                        if (!coast) continue;
                    }
                    float[] nodes = new float[NODES * ATTRS]; boolean visible = false;
                    float[] heights = new float[NODES];
                    for (int i = 0; i < NODES; i++) {
                        int index = tile.indices[i]; float head = flow.head()[index], potential = flow.potential()[index];
                        heights[i] = Float.isFinite(head) ? head : Float.isFinite(potential) ? potential : ground[index] - .05f;
                    }
                    boolean wet = false;
                    for (int i = 0; i < NODES; i++) {
                        int o = i * ATTRS, index = tile.indices[i];
                        double wx = tile.x + (i % N) / (double) SUB, wz = tile.z + (i / N) / (double) SUB;
                        float y = heights[i];
                        boolean connected = Float.isFinite(flow.head()[index]);
                        float depth = connected ? y - tile.ground : Math.min(-.001f, y - tile.ground);
                        double apron = tile.sea ? 1 - smooth(.5, 1, tile.ocean[i]) : 1;
                        nodes[o] = (float) (wx - camera.x);
                        nodes[o + 1] = (float) (y - camera.y + (tile.sea ? .0015 * apron : .0015));
                        nodes[o + 2] = (float) (wz - camera.z);
                        nodes[o + DEPTH] = depth;
                        nodes[o + REACH] = tile.sea ? (float) (.999 - tile.ocean[i]) : (float) c.runupReach - tile.reach[i];
                        // Dry vertices need a finite reach scalar for stable polygon interpolation.
                        if (!Float.isFinite(nodes[o + REACH])) nodes[o + REACH] = -1;
                        nodes[o + 5] = (float) (c.runupOpacity * apron);
                        int left = i % N > 0 ? i - 1 : i, right = i % N < SUB ? i + 1 : i;
                        int back = i / N > 0 ? i - N : i, front = i / N < SUB ? i + N : i;
                        nodes[o + 6] = -(heights[right] - heights[left]) * SUB / (right == left ? 1 : right - left);
                        nodes[o + 7] = -(heights[front] - heights[back]) * SUB / (front == back ? 1 : (front - back) / N);
                        nodes[o + 11] = (float) (foam(wx, wz, y) * apron);
                        nodes[o + 8] = (float) apron; nodes[o + 9] = (i % N) / (float) SUB; nodes[o + 10] = (i / N) / (float) SUB;
                        visible |= depth > 0 && nodes[o + REACH] >= 0 && apron > .001;
                        wet |= depth > 0 && nodes[o + REACH] >= 0;
                        if (!tile.sea && sea[index]) boundaryError = Math.max(boundaryError, Math.abs(y - boundary[index]));
                    }
                    if (wet && !tile.sea) wetTiles++;
                    if (visible) result.add(new DrawTile(nodes, tile.color, tile.light));
                }
            }
            result.sort(Comparator.comparingDouble((DrawTile t) -> t.nodes[0] * t.nodes[0] + t.nodes[2] * t.nodes[2]).reversed());
            Sprite sprite = MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(
                    SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/water_still")));
            context.worldState().setData(KEY, new Frame(List.copyOf(result), sprite, MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(
                    SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/white_concrete")))));
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            lastDrawn = 0; Frame frame = context.worldState().getData(KEY);
            if (frame == null || frame.tiles.isEmpty()) return;
            if (allocator == null) allocator = new BufferAllocator(1048576);
            var consumers = VertexConsumerProvider.immediate(allocator);
            var vertices = consumers.getBuffer(waterLayer());
            var matrix = context.matrices().peek();
            net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil.markSpriteActive(frame.sprite);
            net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil.markSpriteActive(frame.foamSprite);
            float[][] input = new float[3][ATTRS], work = new float[8][ATTRS], output = new float[8][ATTRS];
            for (DrawTile tile : frame.tiles) for (int z = 0; z < SUB; z++) for (int x = 0; x < SUB; x++) {
                int a = z * N + x;
                for (int triangle = 0; triangle < 2; triangle++) {
                    System.arraycopy(tile.nodes, (triangle == 0 ? a : a + 1) * ATTRS, input[0], 0, ATTRS);
                    System.arraycopy(tile.nodes, (a + N) * ATTRS, input[1], 0, ATTRS);
                    System.arraycopy(tile.nodes, (triangle == 0 ? a + 1 : a + N + 1) * ATTRS, input[2], 0, ATTRS);
                    int count = SurfaceClip.clip(input, 3, DEPTH, work);
                    count = SurfaceClip.clip(work, count, REACH, output);
                    if (count < 3) continue;
                    for (int i = 1; i + 1 < count; i++) {
                        emit(vertices, matrix, frame.sprite, output[0], tile.color, tile.light);
                        emit(vertices, matrix, frame.sprite, output[i], tile.color, tile.light);
                        emit(vertices, matrix, frame.sprite, output[i + 1], tile.color, tile.light);
                        emit(vertices, matrix, frame.sprite, output[i + 1], tile.color, tile.light);
                        if (output[0][11] + output[i][11] + output[i + 1][11] > .01) {
                            emitFoam(vertices, matrix, frame.foamSprite, output[0], tile.light);
                            emitFoam(vertices, matrix, frame.foamSprite, output[i], tile.light);
                            emitFoam(vertices, matrix, frame.foamSprite, output[i + 1], tile.light);
                            emitFoam(vertices, matrix, frame.foamSprite, output[i + 1], tile.light);
                        }
                        drawnQuads++; lastDrawn++;
                    }
                }
            }
            consumers.draw();
        });
    }
    private static double hash(double x, double z) {
        x -= Math.floor(x / 256) * 256; z -= Math.floor(z / 256) * 256;
        double h = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return h - Math.floor(h);
    }
    private static double noise(double x, double z) {
        double ix = Math.floor(x), iz = Math.floor(z), fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        return (hash(ix, iz) * (1 - fx) + hash(ix + 1, iz) * fx) * (1 - fz)
                + (hash(ix, iz + 1) * (1 - fx) + hash(ix + 1, iz + 1) * fx) * fz;
    }
    private static double foam(double x, double z, double y) {
        var c = WavesClient.config;
        double time = WavesClient.seconds() * c.speed, amplitude = WavesClient.amplitude();
        double n = .65 * noise(x * 2 + .13 * time, z * 2 - .09 * time)
                + .35 * noise(x * 4 - .13 * time, z * 4 + .09 * time);
        double crest = (y - (c.seaLevel - .111111)) / Math.max(.1, amplitude);
        double value = smooth(.35, .8, crest) * smooth(.43, .8, n) * smooth(.4, 1.4, amplitude);
        if (c.shorelineEnabled) {
            double d = ShoreField.surfaceDistance(x, z);
            double surge = .5 + .5 * Math.sin(time * 1.6 - d * 1.9 + noise(x, z) * 4);
            value += Math.exp(-d * 1.1) * smooth(.2, .8, n + surge * .45) * smooth(.3, .85, surge);
        }
        return Math.clamp(value * c.foam, 0, .95);
    }
    private static void emitFoam(VertexConsumer vertices, net.minecraft.client.util.math.MatrixStack.Entry matrix,
                                 Sprite sprite, float[] p, int light) {
        float alpha = p[11] * (float) smooth(0, .12, p[DEPTH]);
        vertices.vertex(matrix, p[0], p[1] + .001f, p[2]).color(217, 240, 250, (int) (255 * alpha))
                .texture(sprite.getFrameU(p[9]), sprite.getFrameV(p[10])).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(matrix, 0, 1, 0);
    }
    private static void emit(VertexConsumer vertices, net.minecraft.client.util.math.MatrixStack.Entry matrix,
                             Sprite sprite, float[] p, int color, int light) {
        float length = (float) Math.sqrt(1 + p[6] * p[6] + p[7] * p[7]);
        float nx = p[6] / length, ny = 1 / length, nz = p[7] / length;
        float shade = .7f + .4f * Math.max(.3f, (nx * .3f + ny + nz * .4f) / 1.12f);
        float edge = (float) smooth(0, .12, p[DEPTH]);
        float distance = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        float facing = distance > .01f ? Math.max(0, -(nx * p[0] + ny * p[1] + nz * p[2]) / distance) : 1;
        float fresnel = (float) Math.pow(1 - facing, 4);
        float reflection = fresnel * .3f;
        int r = Math.min(255, (int) ((((color >> 16) & 255) * (1 - reflection) + 190 * reflection) * shade));
        int g = Math.min(255, (int) ((((color >> 8) & 255) * (1 - reflection) + 210 * reflection) * shade));
        int b = Math.min(255, (int) (((color & 255) * (1 - reflection) + 240 * reflection) * shade));
        int alpha = (int) (255 * Math.min(.95, p[5] + fresnel * .15 * p[8]) * edge);
        vertices.vertex(matrix, p[0], p[1], p[2]).color(r, g, b, alpha)
                .texture(sprite.getFrameU(p[9]), sprite.getFrameV(p[10])).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(matrix, nx, ny, nz);
    }
    public static void close() { MASK.close(); if (allocator != null) allocator.close(); allocator = null; reset(); }
}
