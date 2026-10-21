package dev.kirisame.waves.iris;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.math.ShaderPatcher;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.DoubleSupplier;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Adapter for the pinned Iris 1.9.7 terrain pipeline. */
public final class IrisBridge {
    private static final String LIBRARY = loadLibrary();
    private static Object lastMap;
    private static int waterId = -1;
    private static int patchedShaders;
    private static boolean uniformsReady;

    private IrisBridge() {}

    private static String loadLibrary() {
        try (var stream = IrisBridge.class.getResourceAsStream("/assets/waves/shaders/waves.glsl")) {
            if (stream == null) throw new IOException("Missing bundled wave shader");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    public static String patch(String source) {
        String result = ShaderPatcher.patch(source, LIBRARY);
        if (result != source && result != null && !result.equals(source)) {
            patchedShaders++;
            WavesClient.LOGGER.debug("Patched Iris terrain vertex shader #{}", patchedShaders);
        }
        return result;
    }

    public static void register(UniformHolder holder) {
        bind(holder, "waves_amplitude", WavesClient::amplitude);
        bind(holder, "waves_wavelength", () -> WavesClient.config.wavelength);
        bind(holder, "waves_direction", () -> Math.toRadians(WavesClient.config.direction));
        bind(holder, "waves_sharpness", () -> WavesClient.config.sharpness);
        bind(holder, "waves_water_id", IrisBridge::waterId);
        bind(holder, "waves_camera_y", () -> MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y);
        bind(holder, "waves_sea_level", () -> WavesClient.config.seaLevel);
        bind(holder, "waves_sea_only", () -> WavesClient.config.seaLevelOnly ? 1 : 0);
        for (int i = 0; i < 3; i++) {
            int component = i;
            bind(holder, "waves_phase_" + i, () -> WavesClient.phase(component));
        }
        uniformsReady = true;
    }

    private static void bind(UniformHolder holder, String name, DoubleSupplier supplier) {
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, name, supplier);
    }

    public static void invalidateWaterId() { lastMap = null; waterId = -1; }

    public static void resetStatus() {
        invalidateWaterId();
        patchedShaders = 0;
        uniformsReady = false;
    }

    public static double waterId() {
        Object2IntMap<BlockState> map = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (map == null) return -1;
        if (map == lastMap) return waterId;
        lastMap = map;
        BlockState water = Blocks.WATER.getDefaultState();
        waterId = map.containsKey(water) ? map.getInt(water) : -1;
        if (waterId >= 0) {
            for (var entry : map.object2IntEntrySet()) {
                if (entry.getIntValue() == waterId && !entry.getKey().isOf(Blocks.WATER)) {
                    waterId = -1;
                    break;
                }
            }
        }
        if (waterId < 0) WavesClient.LOGGER.warn("Shader pack has no exclusive water material ID; Waves is inactive for this pack. Use Waves Reference or fix block.properties.");
        else WavesClient.LOGGER.info("Waves resolved this shader pack's water material ID: {}", waterId);
        return waterId;
    }

    public static boolean active() {
        return shadersEnabled() && uniformsReady && patchedShaders > 0 && waterId() >= 0;
    }

    public static boolean shadersEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static Screen shaderScreen(Screen parent) {
        return (Screen) IrisApi.getInstance().openMainIrisScreenObj(parent);
    }
}
