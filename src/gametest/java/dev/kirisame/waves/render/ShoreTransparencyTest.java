package dev.kirisame.waves.render;

import dev.kirisame.waves.IrisTestSupport;
import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.config.WavesConfig;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.WorldPresets;

/** Reads rendered pixels through the real shore layer. Test-only, no third-party shader is distributed. */
public final class ShoreTransparencyTest {
    private static boolean draw, legacy;
    private static int alpha;
    private static BufferAllocator allocator;
    private ShoreTransparencyTest() {}

    private static double difference(Path a, Path b) {
        try {
            var first = ImageIO.read(a.toFile()); var second = ImageIO.read(b.toFile());
            double total = 0;
            for (int y = first.getHeight() / 2 - 8; y < first.getHeight() / 2 + 8; y++)
                for (int x = first.getWidth() / 2 - 8; x < first.getWidth() / 2 + 8; x++) {
                    int c = first.getRGB(x, y), d = second.getRGB(x, y);
                    for (int shift = 0; shift <= 16; shift += 8) total += Math.abs(((c >> shift) & 255) - ((d >> shift) & 255));
                }
            return total / (16 * 16 * 3);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static void checkAlpha(ClientGameTestContext context, String name) {
        context.runOnClient(client -> draw = false); context.waitTicks(3);
        Path absent = context.takeScreenshot(name + "-absent");
        context.runOnClient(client -> { draw = true; alpha = 0; }); context.waitTicks(3);
        Path transparent = context.takeScreenshot(name + "-alpha-zero");
        context.runOnClient(client -> alpha = 64); context.waitTicks(3);
        Path partial = context.takeScreenshot(name + "-alpha-quarter");
        context.runOnClient(client -> alpha = 255); context.waitTicks(3);
        Path opaque = context.takeScreenshot(name + "-alpha-full");
        double zeroError = difference(absent, transparent), partialChange = difference(absent, partial), fullChange = difference(absent, opaque);
        WavesClient.LOGGER.info("SHORE_ALPHA_PIXELS backend={} zeroError={} partialChange={} fullChange={}", name, zeroError, partialChange, fullChange);
        if (zeroError > 3 || partialChange <= zeroError + 1 || fullChange <= partialChange + 1)
            throw new AssertionError("Shore opacity is not transparent/partial/opaque for " + name);
        context.runOnClient(client -> draw = false);
    }
    public static void run(ClientGameTestContext context, Path shader) {
        context.runOnClient(client -> {
            IrisTestSupport.disable(); WavesClient.config = new WavesConfig(); WavesClient.config.runupEnabled = false;
            client.options.pauseOnLostFocus = false;
            allocator = new BufferAllocator(4096);
            WorldRenderEvents.END_MAIN.register(render -> {
                if (!draw || allocator == null) return;
                var camera = client.gameRenderer.getCamera().getPos();
                var consumers = VertexConsumerProvider.immediate(allocator);
                var vertices = consumers.getBuffer(legacy ? net.minecraft.client.render.RenderLayer.getTranslucentMovingBlock() : RunupSurface.waterLayer());
                var sprite = client.getAtlasManager().getSprite(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/white_concrete")));
                net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil.markSpriteActive(sprite);
                var matrix = render.matrices().peek();
                int light = WorldRenderer.getLightmapCoordinates(client.world, new BlockPos(1862, 64, -2041));
                float[][] points = {{1862, -2041, 0, 0}, {1862, -2040, 0, 1}, {1863, -2040, 1, 1}, {1863, -2041, 1, 0}};
                for (float[] p : points) vertices.vertex(matrix, (float) (p[0] - camera.x), (float) (63.02 - camera.y), (float) (p[1] - camera.z))
                        .color(255,255,255,alpha).texture(sprite.getFrameU(p[2]),sprite.getFrameV(p[3]))
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrix,0,1,0);
                consumers.draw();
            });
        });
        try (var world = context.worldBuilder().adjustSettings(creator -> {
            var preset = creator.getGeneratorOptionsHolder().getCombinedRegistryManager().getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT);
            creator.setWorldType(new WorldCreator.WorldType(preset));
        }).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 1862.5 72 -2040.5 0 90");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon"); server.runCommand("weather clear");
            context.runOnClient(client -> { client.options.hudHidden = true; client.options.getViewDistance().setValue(3); });
            context.waitFor(client -> client.world != null && client.world.getBlockState(new BlockPos(1862,62,-2041)).isOf(net.minecraft.block.Blocks.WATER),1200);
            server.runCommand("fill 1858 60 -2045 1869 62 -2036 minecraft:stone");
            context.waitFor(client -> client.world.getBlockState(new BlockPos(1862,62,-2041)).isOf(net.minecraft.block.Blocks.STONE),600);
            context.waitTicks(60); context.takeScreenshot("shore-alpha-warmup");
            checkAlpha(context,"native-shore");
            context.runOnClient(client -> IrisTestSupport.enableExternal(shader));
            context.waitTicks(30); checkAlpha(context,"external-shore");
            Path absent = context.takeScreenshot("external-legacy-absent");
            context.runOnClient(client -> { legacy = true; draw = true; alpha = 0; }); context.waitTicks(3);
            Path oldZero = context.takeScreenshot("external-legacy-alpha-zero");
            WavesClient.LOGGER.info("SHORE_ALPHA_LEGACY_CONTROL zeroError={}", difference(absent,oldZero));
            context.runOnClient(client -> { legacy = false; draw = false; });
            server.runCommand("tp @a 1856 67 -2048 -35 24");
            context.runOnClient(client -> {
                WavesClient.config.runupEnabled = true; WavesClient.config.amplitude = 2;
                WavesClient.config.speed = 0; WavesClient.config.splashes = false; WavesClient.config.foam = 0;
            });
            context.waitTicks(60); context.takeScreenshot("external-shore-no-foam");
            context.runOnClient(client -> WavesClient.config.foam = .8);
            context.waitTicks(5); context.takeScreenshot("external-shore-foam");
            if (context.computeOnClient(client -> RunupSurface.wetCount()) == 0) throw new AssertionError("No wet shore in external-pack alpha test");
            WavesClient.LOGGER.info("SHORE_TRANSPARENCY_TEST_PASS external={}",shader.getFileName());
        } finally {
            context.runOnClient(client -> { draw = false; if (allocator != null) allocator.close(); allocator = null; });
        }
    }
}
