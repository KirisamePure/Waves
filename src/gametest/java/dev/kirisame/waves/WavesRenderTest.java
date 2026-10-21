package dev.kirisame.waves;

import dev.kirisame.waves.gui.WavesScreen;
import dev.kirisame.waves.config.WavesConfig;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ShoreField;
import java.nio.file.Path;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.biome.BiomeKeys;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

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

    private static void requireShoreOnGpu(ClientGameTestContext context, int x, int z) {
        context.runOnClient(client -> {
            int program = dev.kirisame.waves.render.NativeWaves.program();
            int enabled = GL20.glGetUniformLocation(program, "waves_shore_enabled");
            require(enabled >= 0 && GL20.glGetUniformf(program, enabled) > .5, "Shore response was not enabled on the water GPU program");
            int originX = (int) Math.round(ShoreField.originRelativeX() + client.gameRenderer.getCamera().getPos().x);
            int originZ = (int) Math.round(ShoreField.originRelativeZ() + client.gameRenderer.getCamera().getPos().z);
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
            int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
            int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
            var buffer = MemoryUtil.memAllocFloat(ShoreField.SIZE * ShoreField.SIZE);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShoreField.textureId());
                require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == ShoreField.SIZE, "Unexpected shore texture size");
                require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) == ShoreField.SIZE, "Unexpected shore texture height");
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RED, GL11.GL_FLOAT, buffer);
                float distance = buffer.get((z - originZ) * ShoreField.SIZE + x - originX);
                require(distance == 0, "Uploaded shore texture does not contain the reef");
                WavesClient.LOGGER.info("SHORE_GPU_CHECK enabled={} fieldOrigin={},{} shaderOrigin={},{} texture={} reefDistance={}",
                        GL20.glGetUniformf(program, enabled), originX, originZ,
                        GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, "waves_field_origin_x")),
                        GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, "waves_field_origin_z")), ShoreField.textureId(), distance);
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
                MemoryUtil.memFree(buffer);
            }
        });
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            WavesClient.config = new WavesConfig();
            client.options.pauseOnLostFocus = false;
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) IrisTestSupport.disable();
        });
        try (var world = context.worldBuilder().adjustSettings(creator -> {
            var preset = creator.getGeneratorOptionsHolder().getCombinedRegistryManager()
                    .getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT);
            creator.setWorldType(new WorldCreator.WorldType(preset));
        }).create()) {
            var server = world.getServer();
            require(context.computeOnClient(client -> {
                var sounds = client.getSoundManager().get(net.minecraft.util.Identifier.of("waves", "shore_ambience"));
                return sounds != null && sounds.getWeight() == 1;
            }), "The seamless surf loop was not loaded");
            context.setScreen(() -> new WavesScreen(null));
            require(context.computeOnClient(client -> WavesClient.statusKey().equals("screen.waves.native")), "Native renderer must work with no shader pack");
            context.takeScreenshot("waves-native-settings");
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
            // render to compile the native program and upload terrain meshes.
            context.takeScreenshot("waves-warmup");
            context.waitTicks(10);
            requireRenderedImage(context.takeScreenshot("waves-calm"));
            require(context.computeOnClient(client -> dev.kirisame.waves.render.NativeWaves.active()), "Native Sodium terrain wave program is not connected");
            require(!context.computeOnClient(client -> IrisBridge.shadersEnabled()), "Native test unexpectedly activated a shader pack");
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
            int reefX = ocean.getX() + 7, reefZ = ocean.getZ() + 10;
            server.runCommand("fill " + reefX + " 58 " + reefZ + " " + (reefX + 4) + " 64 " + (reefZ + 3) + " minecraft:stone");
            server.runCommand("fill " + (reefX - 2) + " 60 " + (reefZ + 2) + " " + reefX + " 63 " + (reefZ + 5) + " minecraft:stone");
            context.runOnClient(client -> {
                WavesClient.config.speed = 1;
                WavesClient.config.splashDensity = 1;
                WavesClient.config.foam = 1.2;
            });
            context.waitFor(client -> ShoreField.ready() && ShoreField.distanceAt(reefX, reefZ) == 0, 600);
            require(context.computeOnClient(client -> ShoreField.contactCount()) > 0, "Reef-water contacts were not found");
            long previousImpacts = context.computeOnClient(client -> ShoreField.impactCount());
            context.waitFor(client -> ShoreField.impactCount() > previousImpacts, 800);
            requireRenderedImage(context.takeScreenshot("waves-reef-impact"));
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.spawned()) > 0, "No ballistic spray particles spawned");
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.SurfSound.count()) > 0, "No positional surf sounds played");
            long spawned = context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.spawned());
            context.runOnClient(client -> { WavesClient.config.speed = 0; WavesClient.config.splashes = false; });
            context.waitTicks(45);
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.returned()) > 0, "Spray did not fall back or collide");
            long sounds = context.computeOnClient(client -> dev.kirisame.waves.particle.SurfSound.count());
            context.waitTicks(450);
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.SurfSound.looping()
                    && dev.kirisame.waves.particle.SurfSound.volume() > .01), "Shore ambience must continue with frozen waves and particles disabled");
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.SurfSound.count()) == sounds,
                    "Shore loop was needlessly restarted");
            context.runOnClient(client -> { WavesClient.config.sounds = false; WavesClient.config.speed = 1; });
            context.waitTicks(80);
            require(context.computeOnClient(client -> !dev.kirisame.waves.particle.SurfSound.looping()), "Sound toggle did not fade out the loop");
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.spawned()) == spawned, "Spray toggle did not stop emission");
            context.runOnClient(client -> { WavesClient.config.speed = 0; WavesClient.config.sounds = true; });
            context.waitFor(client -> dev.kirisame.waves.particle.SurfSound.volume() > .01, 100);
            server.runCommand("tp @a " + ocean.getX() + " 110 " + ocean.getZ() + " -35 12");
            context.waitFor(client -> !dev.kirisame.waves.particle.SurfSound.looping(), 200);
            server.runCommand("tp @a " + ocean.getX() + " 66 " + ocean.getZ() + " -35 12");
            context.waitFor(client -> dev.kirisame.waves.particle.SurfSound.looping(), 200);
            requireRenderedImage(context.takeScreenshot("waves-reef-foam"));
            requireShoreOnGpu(context, reefX, reefZ);
            context.runOnClient(client -> WavesClient.config.foam = 0);
            context.takeScreenshot("waves-reef-foam-off");
            context.runOnClient(client -> WavesClient.config.foam = .8);
            // A dry-weather close-up separates ballistic spray from vanilla falling rain.
            server.runCommand("weather clear 600");
            context.waitFor(client -> client.world.getRainGradient(1) < .01, 1800);
            server.runCommand("tp @a " + (reefX - 4) + " 65 " + (reefZ - 5) + " -35 9");
            context.runOnClient(client -> {
                WavesClient.config.amplitude = 1.1;
                WavesClient.config.speed = 1;
                WavesClient.config.splashes = true;
                WavesClient.config.splashHeight = 1.4;
            });
            long closeupImpacts = context.computeOnClient(client -> ShoreField.impactCount());
            context.waitFor(client -> ShoreField.impactCount() > closeupImpacts, 800);
            context.waitTicks(6);
            requireRenderedImage(context.takeScreenshot("waves-native-spray-closeup"));
            context.waitTicks(6);
            context.takeScreenshot("waves-native-spray-falling");
            require(context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.maximumRise()) > .1,
                    "Spray never rose above the water surface");
            server.runCommand("fill " + (reefX - 2) + " 63 " + reefZ + " " + (reefX + 4) + " 65 " + (reefZ + 5) + " minecraft:air");
            server.runCommand("fill " + (reefX - 2) + " 58 " + reefZ + " " + (reefX + 4) + " 62 " + (reefZ + 5) + " minecraft:water");
            context.waitFor(client -> ShoreField.distanceAt(reefX, reefZ) > 6, 600);
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
            context.clickScreenButton("screen.waves.shore_page");
            context.waitTick();
            context.takeScreenshot("waves-shore-settings");
            context.clickScreenButton("screen.waves.sound_page");
            context.waitTick();
            context.takeScreenshot("waves-audio-settings");
            context.clickScreenButton("gui.done");
            require(context.computeOnClient(client -> client.currentScreen == null), "Settings did not close/save");
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                context.runOnClient(client -> IrisTestSupport.enableFixture());
                context.waitTicks(30);
                requireRenderedImage(context.takeScreenshot("waves-custom-iris-pack"));
                require(context.computeOnClient(client -> IrisBridge.active()), "Optional Iris integration failed");
                server.runCommand("fill " + reefX + " 58 " + reefZ + " " + (reefX + 4) + " 64 " + (reefZ + 3) + " minecraft:stone");
                long irisSpray = context.computeOnClient(client -> dev.kirisame.waves.particle.ShoreSpray.spawned());
                long irisSounds = context.computeOnClient(client -> dev.kirisame.waves.particle.SurfSound.count());
                context.waitFor(client -> dev.kirisame.waves.particle.ShoreSpray.spawned() > irisSpray
                        && dev.kirisame.waves.particle.SurfSound.looping(), 800);
                context.waitTicks(6);
                context.takeScreenshot("waves-iris-spray-closeup");
                context.runOnClient(client -> {
                    int program = IrisTestSupport.program();
                    int amplitude = GL20.glGetUniformLocation(program, "waves_amplitude");
                    require(amplitude >= 0 && GL20.glGetUniformf(program, amplitude) > .3, "No waves in optional Iris pack");
                });
            }
            WavesClient.LOGGER.info("WAVES_RENDER_TEST_PASS naturalOcean={} calm={} storm={} impacts={} spray={} returned={} sounds={} maxRise={} irisInstalled={}",
                    ocean, calm, storm, ShoreField.impactCount(), dev.kirisame.waves.particle.ShoreSpray.spawned(),
                    dev.kirisame.waves.particle.ShoreSpray.returned(), dev.kirisame.waves.particle.SurfSound.count(),
                    dev.kirisame.waves.particle.ShoreSpray.maximumRise(), net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"));
        }
    }
}
