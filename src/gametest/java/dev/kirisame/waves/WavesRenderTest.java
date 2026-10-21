package dev.kirisame.waves;

import dev.kirisame.waves.gui.WavesScreen;
import dev.kirisame.waves.config.WavesConfig;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ReferencePack;
import java.nio.file.Path;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.biome.BiomeKeys;

/** Separate test mod, excluded from the distributable JAR. */
public final class WavesRenderTest implements FabricClientGameTest {
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static void requireRenderedImage(Path path) {
        try {
            var image = ImageIO.read(path.toFile());
            int first = image.getRGB(0, 0);
            int different = 0;
            for (int y = 0; y < image.getHeight(); y += 4) {
                for (int x = 0; x < image.getWidth(); x += 4) {
                    if (image.getRGB(x, y) != first) different++;
                }
            }
            require(different > 1000, "Frame is blank or uniform: " + path);
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WavesClient.config = new WavesConfig();
            client.options.pauseOnLostFocus = false;
            Iris.getIrisConfig().setShadersEnabled(false);
            Iris.getIrisConfig().setDebugEnabled(true);
            try { Iris.getIrisConfig().save(); Iris.reload(); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        try (var world = context.worldBuilder().adjustSettings(creator -> {
            var preset = creator.getGeneratorOptionsHolder().getCombinedRegistryManager()
                    .getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT);
            creator.setWorldType(new WorldCreator.WorldType(preset));
        }).create()) {
            var server = world.getServer();
            context.setScreen(() -> new WavesScreen(null));
            require(context.computeOnClient(client -> WavesClient.statusKey().equals("screen.waves.no_shaders")), "Missing shaders must be reported as inactive");
            context.takeScreenshot("waves-shaders-disabled-settings");
            context.clickScreenButton("screen.waves.enable_reference");
            require(context.computeOnClient(client -> IrisBridge.shadersEnabled()), "One-click reference pack activation failed");
            context.clickScreenButton("gui.done");
            var ocean = server.computeOnServer(s -> {
                var found = s.getOverworld().locateBiome(entry -> entry.matchesKey(BiomeKeys.DEEP_OCEAN),
                        new BlockPos(2048, 63, -2048), 6400, 64, 64);
                require(found != null, "No natural deep ocean found for seed 1");
                return found.getFirst();
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a " + ocean.getX() + " 66 " + ocean.getZ() + " -35 12");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            context.runOnClient(client -> {
                client.options.hudHidden = true;
                client.options.getViewDistance().setValue(3);
                WavesClient.config.speed = 0;
            });
            context.waitFor(client -> client.world != null && client.world.getBlockState(new BlockPos(ocean.getX(), 62, ocean.getZ())).isOf(Blocks.WATER), 1200);
            context.waitTicks(40);
            // The client test runner does not draw ordinary tick frames. Explicitly
            // render to initialize Iris's material map and upload terrain meshes.
            context.takeScreenshot("waves-warmup");
            context.waitTicks(10);
            requireRenderedImage(context.takeScreenshot("waves-calm"));
            require(context.computeOnClient(client -> IrisApi.getInstance().isShaderPackInUse()), "Reference shader pack failed to compile");
            require(context.computeOnClient(client -> IrisBridge.active()), "Iris wave uniforms or water mapping are not connected");
            double calm = context.computeOnClient(client -> WavesClient.amplitude());
            require(calm > .30 && calm < .34, "Unexpected calm-weather amplitude: " + calm);
            context.runOnClient(client -> WavesClient.config.speed = 1);
            double phaseBefore = context.computeOnClient(client -> WavesClient.phase(0));
            context.waitTicks(20);
            double phaseAfter = context.computeOnClient(client -> WavesClient.phase(0));
            require(Math.abs(Math.IEEEremainder(phaseAfter - phaseBefore, Math.PI * 2)) > .1,
                    "Wave phases did not advance while the client ticked");
            requireRenderedImage(context.takeScreenshot("waves-animated-ocean"));
            context.runOnClient(client -> WavesClient.config.speed = 0);
            context.runOnClient(client -> WavesClient.config.enabled = false);
            context.waitTicks(3);
            require(context.computeOnClient(client -> WavesClient.amplitude()) == 0, "Disabling waves did not zero the amplitude");
            context.takeScreenshot("waves-disabled");
            context.runOnClient(client -> {
                WavesClient.config.enabled = true;
                WavesClient.config.transitionSeconds = .25;
            });
            server.runCommand("weather thunder 600");
            // Observe the ordinary server-driven weather ramp, rather than
            // assigning gradients that bypass Minecraft's weather packets.
            context.waitFor(client -> client.world.getThunderGradient(1) > .99, 1800);
            context.waitFor(client -> WavesClient.amplitude() > calm * 4.5, 400);
            double storm = context.computeOnClient(client -> WavesClient.amplitude());
            require(storm > calm * 4.5, "Thunder did not substantially increase the swell: " + storm);
            requireRenderedImage(context.takeScreenshot("waves-thunder"));
            context.runOnClient(client -> {
                client.getLanguageManager().setLanguage("zh_cn");
                client.getLanguageManager().reload(client.getResourceManager());
            });
            context.setScreen(() -> new WavesScreen(null));
            context.waitTick();
            context.takeScreenshot("waves-settings");
            context.clickScreenButton("screen.waves.next");
            context.waitTick();
            context.takeScreenshot("waves-weather-settings");
            context.clickScreenButton("gui.done");
            require(context.computeOnClient(client -> client.currentScreen == null), "Settings did not close/save");
            WavesClient.LOGGER.info("WAVES_RENDER_TEST_PASS naturalOcean={} calm={} storm={} pack={}", ocean, calm, storm, ReferencePack.fileName());
        }
    }
}
