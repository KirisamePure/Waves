package dev.kirisame.waves.render;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.iris.ShoreField;
import dev.kirisame.waves.math.WaveMath;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;

/** Native Sodium terrain path. No Iris classes or shader pack are involved. */
public final class NativeWaves {
    public static final int WATER_BIT = 8; // Sodium 0.7.3 uses only material bits 0..2.
    private static final Map<Integer, Map<String, Integer>> locations = new HashMap<>();
    private static int lastProgram;
    private static final int[] previousTexture = new int[2], previousSampler = new int[2];
    private static boolean bound;

    private NativeWaves() {}

    public static String resource(String name) {
        try (var stream = NativeWaves.class.getResourceAsStream("/assets/waves/shaders/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing Waves shader " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    public static String patch(Identifier id, String source) {
        if (!id.getNamespace().equals("sodium")) return source;
        if (id.getPath().equals("blocks/block_layer_opaque.vsh")) {
            locations.clear();
            String declarations = resource("waves.glsl").replace("texture2D(", "texture(")
                    + "\nout vec3 waves_source;\nout vec3 waves_surface;\nout vec3 waves_surface_normal;\nflat out float waves_is_water;\n";
            source = source.replace("void main()", declarations + "\nvoid main()");
            source = source.replace("vec3 position = _vert_position + translation;", """
                    vec3 position = _vert_position + translation;
                    waves_is_water = (_material_params & 8u) != 0u ? 1.0 : 0.0;
                    waves_source = position;
                    vec3 waveNormal;
                    position = waves_displace(vec4(position, 1.0), vec3(0, 1, 0),
                        waves_is_water > 0.5 ? 1.0 : -1.0, waveNormal).xyz;
                    waves_surface = position;
                    waves_surface_normal = waveNormal;
                    """);
        } else if (id.getPath().equals("blocks/block_layer_opaque.fsh")) {
            source = source.replace("void main()", resource("surface_mask.glsl").replace("texture2D(", "texture(") + "\n" + resource("native_water.glsl") + "\nvoid main()");
            source = source.replace("void main() {", "void main() {\n    if (waves_surface_replace > 0.5 && waves_is_water > 0.5 && waves_cell_covered(waves_surface.xz) > 0.5 && abs(normalize(cross(dFdx(waves_source), dFdy(waves_source))).y) < 0.1) discard;");
            source = source.replace("fragColor = _linearFog", "color = waves_shade(color);\n    fragColor = _linearFog");
        }
        return source;
    }

    public static void begin(int program) {
        // Iris programs are different and already bind their own uniforms/samplers.
        if (GL20.glGetUniformLocation(program, "waves_is_native") < 0) return;
        lastProgram = program;
        var c = WavesClient.config;
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        set(program, "waves_is_native", 1);
        set(program, "waves_surface_replace", RunupSurface.replacementEnabled());
        set(program, "waves_surface_origin_x", RunupSurface.originRelativeX());
        set(program, "waves_surface_origin_z", RunupSurface.originRelativeZ());
        set(program, "waves_amplitude", WavesClient.amplitude());
        set(program, "waves_wavelength", c.wavelength);
        set(program, "waves_direction", Math.toRadians(c.direction));
        set(program, "waves_sharpness", c.sharpness);
        set(program, "waves_irregularity", c.irregularity);
        set(program, "waves_water_id", 1);
        set(program, "waves_camera_y", camera.y);
        set(program, "waves_sea_level", c.seaLevel);
        set(program, "waves_sea_only", c.seaLevelOnly ? 1 : 0);
        set(program, "waves_time", WavesClient.seconds() * c.speed);
        set(program, "waves_shore_enabled", ShoreField.enabled());
        set(program, "waves_field_origin_x", ShoreField.originRelativeX());
        set(program, "waves_field_origin_z", ShoreField.originRelativeZ());
        set(program, "waves_shore_fade", c.shoreFade);
        set(program, "waves_reflection", c.reflection);
        set(program, "waves_foam", c.enabled ? c.foam : 0);
        set(program, "waves_noise_x", camera.x - Math.floor(camera.x / 256) * 256);
        set(program, "waves_noise_z", camera.z - Math.floor(camera.z / 256) * 256);
        for (int i = 0; i < WaveMath.COMPONENTS; i++) set(program, "waves_phase_" + i, WavesClient.phase(i));
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 2; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2 + i);
            previousTexture[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            previousSampler[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            GL33.glBindSampler(2 + i, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, i == 0 ? ShoreField.textureId() : RunupSurface.textureId());
            GL20.glUniform1i(location(program, i == 0 ? "waves_shore_field" : "waves_surface_mask"), 2 + i);
        }
        GL13.glActiveTexture(active);
        bound = true;
    }

    public static void end() {
        if (!bound) return;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < 2; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture[i]);
            GL33.glBindSampler(2 + i, previousSampler[i]);
        }
        GL13.glActiveTexture(active);
        bound = false;
    }

    private static int location(int program, String name) {
        return locations.computeIfAbsent(program, ignored -> new HashMap<>())
                .computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key));
    }
    private static void set(int program, String name, double value) { GL20.glUniform1f(location(program, name), (float) value); }
    public static boolean active() { return lastProgram != 0; }
    public static int program() { return lastProgram; }
}
