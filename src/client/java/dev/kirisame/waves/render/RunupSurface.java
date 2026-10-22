package dev.kirisame.waves.render;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ShoreField;
import dev.kirisame.waves.math.RunupMath;
import dev.kirisame.waves.math.ShoreMath;
import dev.kirisame.waves.math.SurfaceClip;
import dev.kirisame.waves.math.WaveMath;
import java.util.ArrayList;
import java.util.Arrays;
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
    private record DrawTile(float[] nodes, int color, int light) {}
    private record Frame(List<DrawTile> tiles, Sprite sprite, Sprite foamSprite, float opacity) {}
    private static final class Tile {
        final int x, z, color, light;
        final boolean sea;
        boolean initialized;
        final float ground, pathBarrier;
        final float[] reach = new float[NODES];
        final float[] ocean = new float[NODES], height = new float[NODES], previousHeight = new float[NODES];
        final float[] depth = new float[NODES], previousDepth = new float[NODES];
        Tile(int x, int z, boolean sea, float ground, float pathBarrier, int color, int light) {
            this.x = x; this.z = z; this.sea = sea; this.ground = ground; this.pathBarrier = pathBarrier; this.color = color; this.light = light;
        }
        long key() { return ((long) x << 32) ^ (z & 0xffffffffL); }
    }
    private RunupSurface() {}
    public static void reset() { tiles = new ArrayList<>(); ready = false; wetTiles = seaTiles = lastDrawn = 0; drawnQuads = 0; }
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
        float[] ground = top.clone(), barriers = paths.barrier().clone(), ocean = new float[top.length];
        for (int i = 0; i < top.length; i++) {
            land[i] = !sea[i];
            if (sea[i]) {
                if (!Float.isFinite(ground[i])) ground[i] = (float) c.seaLevel - 10;
                barriers[i] = ground[i]; ocean[i] = 1;
            } else {
                if (!Float.isFinite(ground[i])) ground[i] = (float) c.seaLevel + 8;
                if (!Float.isFinite(barriers[i])) barriers[i] = (float) c.seaLevel + 8;
            }
        }
        float[] shore = ShoreMath.distances(land, size);
        var old = new HashMap<Long, Tile>();
        for (Tile tile : tiles) old.put(tile.key(), tile);
        var next = new ArrayList<Tile>();
        for (int i = 0; i < top.length; i++) {
            boolean nearWater = sea[i] && shore[i] <= 3;
            boolean dry = !sea[i] && Float.isFinite(top[i]) && paths.distance()[i] <= c.runupReach + 1;
            if (!nearWater && !dry) continue;
            int x = ox + i % size, z = oz + i / size;
            double dx = x + .5 - client.player.getX(), dz = z + .5 - client.player.getZ();
            if (dx * dx + dz * dz > 36 * 36) continue;
            BlockPos pos = BlockPos.ofFloored(x, Math.max(c.seaLevel, ground[i]) + .1, z);
            Tile tile = new Tile(x, z, sea[i], ground[i], barriers[i], BiomeColors.getWaterColor(client.world, pos),
                    WorldRenderer.getLightmapCoordinates(client.world, pos));
            for (int n = 0; n < NODES; n++) {
                double gx = i % size - .5 + (n % N) / (double) SUB;
                double gz = i / size - .5 + (n / N) / (double) SUB;
                tile.reach[n] = Math.max(0, sample(paths.distance(), size, gx, gz, 9) - .5f);
                tile.ocean[n] = sample(ocean, size, gx, gz, 0);
            }
            next.add(tile);
        }
        next.sort(Comparator.comparingDouble(t -> Math.pow(t.x - client.player.getX(), 2) + Math.pow(t.z - client.player.getZ(), 2)));
        next = new ArrayList<>(next.subList(0, Math.min(1024, next.size())));
        byte[] mask = new byte[top.length];
        for (Tile tile : next) { int i = (tile.z - oz) * size + tile.x - ox; mask[i] = (byte) 255; }
        seaTiles = 0;
        for (Tile tile : next) {
            if (tile.sea) seaTiles++;
            Tile previous = old.get(tile.key());
            if (previous != null && previous.ground == tile.ground) {
                System.arraycopy(previous.height, 0, tile.height, 0, NODES);
                System.arraycopy(previous.previousHeight, 0, tile.previousHeight, 0, NODES);
                System.arraycopy(previous.depth, 0, tile.depth, 0, NODES);
                System.arraycopy(previous.previousDepth, 0, tile.previousDepth, 0, NODES);
                tile.initialized = previous.initialized;
            } else {
                Arrays.fill(tile.height, (float) c.seaLevel - .111111f);
                System.arraycopy(tile.height, 0, tile.previousHeight, 0, NODES);
            }
        }
        tiles = next; originX = ox; originZ = oz;
        MASK.upload(mask); ready = !tiles.isEmpty();
    }

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
    private static double seaHeight(WaveMath.Sampler sampler, double x, double z) {
        var c = WavesClient.config;
        double h = sampler.height(x, z, 0);
        if (c.shorelineEnabled) {
            double d = ShoreField.surfaceDistance(x, z);
            h *= ShoreMath.attenuation(d, c.shoreFade);
            double phase = d * 1.7 - WavesClient.seconds() * c.speed * 1.9
                    + .7 * Math.sin(sampler.phase(2, x, z, 0) + sampler.phase(4, x, z, 0));
            h += WavesClient.amplitude() * .22 * c.reflection * Math.exp(-d * .45) * smooth(0, .9, d) * Math.sin(phase);
        }
        return c.seaLevel - .111111 + h;
    }
    public static void tick() {
        var c = WavesClient.config;
        double amplitude = WavesClient.amplitude(); wetTiles = 0;
        var sampler = new WaveMath.Sampler(WavesClient.seconds(), amplitude, c.wavelength, c.direction, c.speed, c.sharpness, c.irregularity);
        var samples = new HashMap<Long, Double>();
        for (Tile tile : tiles) {
            if (tile.sea) continue;
            boolean wet = false;
            for (int i = 0; i < NODES; i++) {
                tile.previousHeight[i] = tile.height[i]; tile.previousDepth[i] = tile.depth[i];
                int sx = tile.x * SUB + i % N, sz = tile.z * SUB + i / N;
                long key = ((long) sx << 32) ^ (sz & 0xffffffffL);
                double x = sx / (double) SUB, z = sz / (double) SUB;
                double level = samples.computeIfAbsent(key, ignored -> seaHeight(sampler, x, z));
                if (tile.ocean[i] > 0) {
                    int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
                    double fx = x - ix, fz = z - iz;
                    double h00 = seaHeight(sampler, ix, iz), h10 = seaHeight(sampler, ix + 1, iz);
                    double h01 = seaHeight(sampler, ix, iz + 1), h11 = seaHeight(sampler, ix + 1, iz + 1);
                    // Match the original coarse ocean at the outer edge, then smoothly refine inward.
                    double coarse = fx + fz <= 1 ? h00 + fx * (h10 - h00) + fz * (h01 - h00)
                            : h11 + (1 - fx) * (h01 - h11) + (1 - fz) * (h10 - h11);
                    double refine = 1 - smooth(0, .5, tile.ocean[i]);
                    level = coarse * (1 - refine) + level * refine;
                }
                tile.height[i] = (float) level; // Never lift water onto individual block tops.
                float targetDepth = (float) (level - (tile.sea ? tile.ground : Math.max(tile.ground, tile.pathBarrier)) + .01);
                tile.depth[i] = !tile.sea && targetDepth < tile.previousDepth[i]
                        ? (float) WaveMath.approach(tile.previousDepth[i], targetDepth, .05, c.runupRetreat * .25)
                        : targetDepth;
                if (!c.runupEnabled || amplitude == 0) tile.depth[i] = -1;
                wet |= tile.depth[i] > 0 && level > tile.ground + .01;
            }
            if (!tile.initialized) {
                System.arraycopy(tile.height, 0, tile.previousHeight, 0, NODES);
                System.arraycopy(tile.depth, 0, tile.previousDepth, 0, NODES);
                tile.initialized = true;
            }
            if (wet && !tile.sea) wetTiles++;
        }
    }

    public static void initialize() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            var c = WavesClient.config; var result = new ArrayList<DrawTile>();
            var camera = context.camera().getPos();
            float partial = context.tickCounter().getTickProgress(false);
            if (replacementEnabled() > .5) for (Tile tile : tiles) {
                if (tile.sea) continue; // Keep the original ocean material; only its obsolete vertical shore walls are masked.
                float[] nodes = new float[NODES * ATTRS]; boolean visible = false;
                for (int i = 0; i < NODES; i++) {
                    int o = i * ATTRS;
                    float y = tile.previousHeight[i] + (tile.height[i] - tile.previousHeight[i]) * partial;
                    float depth = tile.previousDepth[i] + (tile.depth[i] - tile.previousDepth[i]) * partial;
                    nodes[o] = (float) (tile.x + (i % N) / (double) SUB - camera.x);
                    nodes[o + 1] = (float) (y - camera.y);
                    nodes[o + 2] = (float) (tile.z + (i / N) / (double) SUB - camera.z);
                    nodes[o + DEPTH] = depth;
                    nodes[o + REACH] = tile.sea ? 10 : (float) c.runupReach - tile.reach[i];
                    nodes[o + 5] = (float) (.7 * tile.ocean[i] + c.runupOpacity * (1 - tile.ocean[i]));
                    int left = i % N > 0 ? i - 1 : i, right = i % N < SUB ? i + 1 : i;
                    int back = i / N > 0 ? i - N : i, front = i / N < SUB ? i + N : i;
                    nodes[o + 6] = -(tile.height[right] - tile.height[left]) * SUB / (right == left ? 1 : right - left);
                    nodes[o + 7] = -(tile.height[front] - tile.height[back]) * SUB / (front == back ? 1 : (front - back) / N);
                    double wx = tile.x + (i % N) / (double) SUB, wz = tile.z + (i / N) / (double) SUB;
                    nodes[o + 11] = (float) foam(wx, wz, y);
                    nodes[o + 8] = tile.ocean[i]; nodes[o + 9] = (i % N) / (float) SUB; nodes[o + 10] = (i / N) / (float) SUB;
                    visible |= depth >= 0 && nodes[o + REACH] >= 0;
                }
                if (visible) result.add(new DrawTile(nodes, tile.color, tile.light));
            }
            result.sort(Comparator.comparingDouble((DrawTile t) -> t.nodes[0] * t.nodes[0] + t.nodes[2] * t.nodes[2]).reversed());
            Sprite sprite = MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(
                    SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/water_still")));
            context.worldState().setData(KEY, new Frame(List.copyOf(result), sprite, MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(
                    SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/white_concrete"))), (float) c.runupOpacity));
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            lastDrawn = 0; Frame frame = context.worldState().getData(KEY);
            if (frame == null || frame.tiles.isEmpty()) return;
            if (allocator == null) allocator = new BufferAllocator(1048576);
            var consumers = VertexConsumerProvider.immediate(allocator);
            var vertices = consumers.getBuffer(RenderLayer.getTranslucentMovingBlock());
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
        int alpha = (int) (255 * Math.min(.95, p[5] + fresnel * .15) * edge);
        vertices.vertex(matrix, p[0], p[1], p[2]).color(r, g, b, alpha)
                .texture(sprite.getFrameU(p[9]), sprite.getFrameV(p[10])).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(matrix, nx, ny, nz);
    }
    public static void close() { MASK.close(); if (allocator != null) allocator.close(); allocator = null; reset(); }
}
