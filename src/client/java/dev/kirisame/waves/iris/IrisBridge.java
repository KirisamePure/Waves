package dev.kirisame.waves.iris;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.math.ShaderPatcher;
import dev.kirisame.waves.math.WaveMath;
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
    private static boolean replacementSupported = true;

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

    public static String patchFragment(String fragment, String vertex, String geometry, String tessControl, String tessEval) {
        if ((geometry != null && !geometry.isBlank()) || (tessControl != null && !tessControl.isBlank()) || (tessEval != null && !tessEval.isBlank())) {
            replacementSupported = false;
            return fragment;
        }
        if (vertex == null || !ShaderPatcher.patch(vertex, LIBRARY).contains("waves_original_main")) return fragment;
        return ShaderPatcher.patchFragment(fragment, dev.kirisame.waves.render.NativeWaves.resource("surface_mask.glsl"));
    }

    public static boolean surfaceReplacementSupported() { return replacementSupported; }

    public static void register(UniformHolder holder) {
        bind(holder, "waves_surface_replace", dev.kirisame.waves.render.RunupSurface::replacementEnabled);
        bind(holder, "waves_surface_origin_x", dev.kirisame.waves.render.RunupSurface::originRelativeX);
        bind(holder, "waves_surface_origin_z", dev.kirisame.waves.render.RunupSurface::originRelativeZ);
        bind(holder, "waves_amplitude", WavesClient::amplitude);
        bind(holder, "waves_wavelength", () -> WavesClient.config.wavelength);
        bind(holder, "waves_direction", () -> Math.toRadians(WavesClient.config.direction));
        bind(holder, "waves_sharpness", () -> WavesClient.config.sharpness);
        bind(holder, "waves_water_id", IrisBridge::waterId);
        bind(holder, "waves_camera_y", () -> MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y);
        bind(holder, "waves_sea_level", () -> WavesClient.config.seaLevel);
        bind(holder, "waves_sea_only", () -> WavesClient.config.seaLevelOnly ? 1 : 0);
        bind(holder, "waves_irregularity", () -> WavesClient.config.irregularity);
        bind(holder, "waves_time", () -> WavesClient.seconds() * WavesClient.config.speed);
        bind(holder, "waves_shore_enabled", ShoreField::enabled);
        bind(holder, "waves_field_origin_x", ShoreField::originRelativeX);
        bind(holder, "waves_field_origin_z", ShoreField::originRelativeZ);
        bind(holder, "waves_shore_fade", () -> WavesClient.config.shoreFade);
        bind(holder, "waves_reflection", () -> WavesClient.config.reflection);
        bind(holder, "waves_foam", () -> WavesClient.config.enabled ? WavesClient.config.foam : 0);
        bind(holder, "waves_noise_x", () -> noiseOrigin(true));
        bind(holder, "waves_noise_z", () -> noiseOrigin(false));
        for (int i = 0; i < WaveMath.COMPONENTS; i++) {
            int component = i;
            bind(holder, "waves_phase_" + i, () -> WavesClient.phase(component));
        }
        uniformsReady = true;
    }

    private static double noiseOrigin(boolean x) {
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double value = x ? camera.x : camera.z;
        return value - Math.floor(value / 256) * 256;
    }

    private static void bind(UniformHolder holder, String name, DoubleSupplier supplier) {
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, name, supplier);
    }

    public static void invalidateWaterId() { lastMap = null; waterId = -1; }

    public static void resetStatus() {
        invalidateWaterId();
        patchedShaders = 0;
        uniformsReady = false;
        replacementSupported = true;
    }

    public static double waterId() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return -1;
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
        if (waterId < 0) WavesClient.LOGGER.warn("Shader pack has no exclusive water material ID; Waves is inactive for this pack. Disable shader packs for native waves, or use a pack with an exclusive water ID.");
        else WavesClient.LOGGER.info("Waves resolved this shader pack's water material ID: {}", waterId);
        return waterId;
    }

    public static boolean active() {
        return shadersEnabled() && uniformsReady && patchedShaders > 0 && waterId() >= 0;
    }

    public static boolean shadersEnabled() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris") && IrisApi.getInstance().isShaderPackInUse();
    }

    public static Screen shaderScreen(Screen parent) {
        return (Screen) IrisApi.getInstance().openMainIrisScreenObj(parent);
    }
}
