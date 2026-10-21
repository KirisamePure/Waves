package dev.kirisame.waves;

import dev.kirisame.waves.config.ConfigStore;
import dev.kirisame.waves.config.WavesConfig;
import dev.kirisame.waves.gui.WavesScreen;
import dev.kirisame.waves.iris.IrisBridge;
import dev.kirisame.waves.iris.ReferencePack;
import dev.kirisame.waves.math.WaveMath;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WavesClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("waves");
    public static final ConfigStore STORE = new ConfigStore(
            FabricLoader.getInstance().getConfigDir().resolve("waves.json"), LOGGER);
    public static WavesConfig config = new WavesConfig();
    private static long ticks;
    private static double weather = 1;
    private static double previousWeather = 1;
    private static Object lastWorld;

    @Override
    public void onInitializeClient() {
        config = STORE.load();
        ReferencePack.install(FabricLoader.getInstance().getGameDir());
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.waves.configure", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyBinding.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed()) client.setScreen(new WavesScreen(client.currentScreen));
            if (client.world != lastWorld) {
                lastWorld = client.world;
                ticks = 0;
                weather = previousWeather = client.world == null ? 1 : targetWeather(client);
                IrisBridge.invalidateWaterId();
            }
            if (client.world == null || client.isPaused()) return;
            ticks++;
            if (ticks == 60 && config.enabled && client.player != null && !IrisBridge.shadersEnabled()) {
                client.player.sendMessage(Text.translatable("message.waves.no_shaders"), false);
            }
            previousWeather = weather;
            weather = WaveMath.approach(weather, targetWeather(client), .05, config.transitionSeconds);
        });
        LOGGER.info("Waves initialized. Enable an Iris shader pack, then press O to configure.");
    }

    private static double targetWeather(MinecraftClient client) {
        return WaveMath.weatherMultiplier(client.world.getRainGradient(1), client.world.getThunderGradient(1),
                config.rainMultiplier, config.thunderMultiplier);
    }

    public static double seconds() {
        MinecraftClient client = MinecraftClient.getInstance();
        return (ticks + client.getRenderTickCounter().getTickProgress(false)) / 20.0;
    }

    public static double amplitude() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled || client.world == null ||
                (config.overworldOnly && !client.world.getRegistryKey().equals(World.OVERWORLD))) return 0;
        double partial = client.getRenderTickCounter().getTickProgress(false);
        double multiplier = previousWeather + (weather - previousWeather) * partial;
        return Math.min(6, config.amplitude * multiplier);
    }

    public static double phase(int component) {
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        return WaveMath.phase(component, camera.x, camera.z, seconds(), config.wavelength, config.direction, config.speed);
    }

    public static String statusKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled) return "screen.waves.disabled";
        if (!IrisBridge.shadersEnabled()) return "screen.waves.no_shaders";
        if (!IrisBridge.active()) return "screen.waves.unsupported";
        if (config.overworldOnly && client.world != null && !client.world.getRegistryKey().equals(World.OVERWORLD)) {
            return "screen.waves.wrong_dimension";
        }
        if (config.amplitude == 0) return "screen.waves.zero_amplitude";
        return "screen.waves.active";
    }
}
