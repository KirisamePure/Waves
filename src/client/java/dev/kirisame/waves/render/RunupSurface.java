package dev.kirisame.waves.render;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.math.RunupMath;
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
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Original client-only shore sheet: connected terrain, wave-driven coverage, delayed retreat. */
public final class RunupSurface {
    private static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "waves:runup");
    private static List<Tile> tiles = new ArrayList<>();
    private static BufferAllocator allocator;
    private static long drawnQuads;
    private static int wetTiles, lastDrawn;
    private record DrawTile(float x, float z, float[] y, float[] alpha, int color, int light) {}
    private record Frame(List<DrawTile> tiles, Sprite sprite, float opacity) {}
    private static final class Tile {
        final int x, z, color, light;
        final float ground, distance, barrier;
        final float[] nodeDistance = new float[9];
        final float[] y = new float[9], previousY = new float[9], alpha = new float[9], previousAlpha = new float[9];
        Tile(int x, int z, float ground, float distance, float barrier, int color, int light) {
            this.x = x; this.z = z; this.ground = ground; this.distance = distance; this.barrier = barrier;
            this.color = color; this.light = light;
            java.util.Arrays.fill(y, ground + .012f); java.util.Arrays.fill(previousY, ground + .012f);
        }
        long key() { return ((long) x << 32) ^ (z & 0xffffffffL); }
    }
    private RunupSurface() {}
    public static void reset() { tiles = new ArrayList<>(); wetTiles = 0; drawnQuads = 0; }
    public static int candidateCount() { return tiles.size(); }
    public static int wetCount() { return wetTiles; }
    public static long drawnQuads() { return drawnQuads; }
    public static int lastDrawnQuads() { return lastDrawn; }

    public static void rebuild(MinecraftClient client, float[] top, boolean[] sea, int size, int originX, int originZ) {
        var c = WavesClient.config;
        if (!c.runupEnabled) { reset(); return; }
        var paths = RunupMath.paths(top, sea, size, (float) (c.seaLevel - .111111), 8);
        var old = new HashMap<Long, Tile>();
        for (Tile tile : tiles) old.put(tile.key(), tile);
        var next = new ArrayList<Tile>();
        for (int i = 0; i < top.length; i++) {
            if (sea[i] || !Float.isFinite(paths.distance()[i]) || paths.distance()[i] > 8 || !Float.isFinite(top[i])) continue;
            int x = originX + i % size, z = originZ + i / size;
            double dx = x + .5 - client.player.getX(), dz = z + .5 - client.player.getZ();
            if (dx * dx + dz * dz > 40 * 40) continue;
            var pos = BlockPos.ofFloored(x, top[i] + .1, z);
            int color = BiomeColors.getWaterColor(client.world, pos);
            Tile tile = new Tile(x, z, top[i], paths.distance()[i], paths.barrier()[i], color,
                    WorldRenderer.getLightmapCoordinates(client.world, pos));
            for (int n = 0; n < 9; n++) {
                double gx = i % size - .5 + (n % 3) * .5;
                double gz = i / size - .5 + (n / 3) * .5;
                int x0 = (int) Math.floor(gx), z0 = (int) Math.floor(gz);
                double fx = gx - x0, fz = gz - z0;
                double d00 = gridDistance(paths.distance(), size, x0, z0);
                double d10 = gridDistance(paths.distance(), size, x0 + 1, z0);
                double d01 = gridDistance(paths.distance(), size, x0, z0 + 1);
                double d11 = gridDistance(paths.distance(), size, x0 + 1, z0 + 1);
                tile.nodeDistance[n] = (float) Math.max(0, (d00 * (1 - fx) + d10 * fx) * (1 - fz)
                        + (d01 * (1 - fx) + d11 * fx) * fz - .5);
            }
            Tile previous = old.get(tile.key());
            if (previous != null && previous.ground == tile.ground) {
                System.arraycopy(previous.y, 0, tile.y, 0, 9);
                System.arraycopy(previous.previousY, 0, tile.previousY, 0, 9);
                System.arraycopy(previous.alpha, 0, tile.alpha, 0, 9);
                System.arraycopy(previous.previousAlpha, 0, tile.previousAlpha, 0, 9);
            }
            next.add(tile);
        }
        next.sort(Comparator.comparingDouble(t -> Math.pow(t.x - client.player.getX(), 2) + Math.pow(t.z - client.player.getZ(), 2)));
        tiles = new ArrayList<>(next.subList(0, Math.min(2048, next.size())));
    }

    private static double gridDistance(float[] grid, int size, int x, int z) {
        if (x < 0 || z < 0 || x >= size || z >= size) return 9;
        float value = grid[z * size + x];
        return Float.isFinite(value) ? value : 9;
    }

    public static void tick() {
        var c = WavesClient.config;
        double amplitude = WavesClient.amplitude();
        wetTiles = 0;
        var sampler = new WaveMath.Sampler(WavesClient.seconds(), amplitude, c.wavelength, c.direction,
                c.speed, c.sharpness, c.irregularity);
        for (Tile tile : tiles) {
            if (tile.distance - .5 >= c.runupReach) {
                java.util.Arrays.fill(tile.alpha, 0);
                java.util.Arrays.fill(tile.previousAlpha, 0);
                continue;
            }
            boolean wet = false;
            for (int i = 0; i < 9; i++) {
                tile.previousY[i] = tile.y[i]; tile.previousAlpha[i] = tile.alpha[i];
                double distance = tile.nodeDistance[i];
                double level = c.seaLevel - .111111 + .72 * sampler.height(tile.x + (i % 3) * .5,
                        tile.z + (i / 3) * .5, distance * .12) - distance * .025;
                float target = c.runupEnabled && amplitude > 0 ? (float) RunupMath.coverage(level, tile.ground,
                        tile.barrier, distance, c.runupReach) : 0;
                tile.alpha[i] = target > tile.alpha[i] ? target : (float) (tile.alpha[i] * Math.exp(-.05 / Math.max(.05, c.runupRetreat)));
                if (!c.runupEnabled || amplitude == 0) tile.alpha[i] = 0;
                tile.y[i] = (float) Math.max(tile.ground + .012, target > .01 ? level : tile.ground + .012);
                wet |= tile.alpha[i] > .01;
            }
            if (wet) wetTiles++;
        }
    }

    public static void initialize() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            var c = WavesClient.config;
            var result = new ArrayList<DrawTile>();
            var camera = context.camera().getPos();
            float partial = context.tickCounter().getTickProgress(false);
            if (c.enabled && c.runupEnabled && WavesClient.amplitude() > 0) for (Tile tile : tiles) {
                float[] y = new float[9], alpha = new float[9]; boolean visible = false;
                for (int i = 0; i < 9; i++) {
                    y[i] = (float) (tile.previousY[i] + (tile.y[i] - tile.previousY[i]) * partial - camera.y);
                    alpha[i] = tile.previousAlpha[i] + (tile.alpha[i] - tile.previousAlpha[i]) * partial;
                    visible |= alpha[i] > .01;
                }
                if (visible) result.add(new DrawTile((float) (tile.x - camera.x), (float) (tile.z - camera.z), y, alpha, tile.color, tile.light));
            }
            result.sort(Comparator.comparingDouble((DrawTile t) -> t.x * t.x + t.z * t.z).reversed());
            Sprite sprite = MinecraftClient.getInstance().getAtlasManager().getSprite(new SpriteIdentifier(
                    SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/water_still")));
            context.worldState().setData(KEY, new Frame(List.copyOf(result), sprite, (float) c.runupOpacity));
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            lastDrawn = 0;
            Frame frame = context.worldState().getData(KEY);
            if (frame == null || frame.tiles.isEmpty() || frame.opacity <= 0) return;
            if (allocator == null) allocator = new BufferAllocator(262144);
            var consumers = VertexConsumerProvider.immediate(allocator);
            var vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
            var matrix = context.matrices().peek();
            net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil.markSpriteActive(frame.sprite);
            for (DrawTile tile : frame.tiles) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
                int a = z * 3 + x;
                int[] indices = {a, a + 3, a + 4, a + 1};
                float maxAlpha = 0;
                for (int i : indices) maxAlpha = Math.max(maxAlpha, tile.alpha[i]);
                if (maxAlpha < .01) continue;
                for (int i : indices) {
                    float px = (i % 3) * .5f, pz = (i / 3) * .5f;
                    float alpha = Math.min(1, tile.alpha[i]) * frame.opacity;
                    // Narrow pale moving front, not a permanent white block-wide band.
                    float foam = tile.alpha[i] > .05 && tile.alpha[i] < .65 ? .35f : 0;
                    int r = (int) (((tile.color >> 16) & 255) * (1 - foam) + 215 * foam);
                    int g = (int) (((tile.color >> 8) & 255) * (1 - foam) + 235 * foam);
                    int b = (int) ((tile.color & 255) * (1 - foam) + 245 * foam);
                    vertices.vertex(matrix, tile.x + px, tile.y[i], tile.z + pz).color(r, g, b, (int) (alpha * 255))
                            .texture(frame.sprite.getFrameU(px), frame.sprite.getFrameV(pz)).overlay(OverlayTexture.DEFAULT_UV)
                            .light(tile.light).normal(matrix, 0, 1, 0);
                }
                drawnQuads++; lastDrawn++;
            }
            consumers.draw();
        });
    }
    public static void close() { if (allocator != null) allocator.close(); allocator = null; reset(); }
}
