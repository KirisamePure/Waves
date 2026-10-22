package dev.kirisame.waves;

import dev.kirisame.waves.iris.ShoreField;
import dev.kirisame.waves.render.NativeWaves;
import dev.kirisame.waves.render.RunupSurface;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.*;

/** Actual wave vertex shader feedback, independent of the CPU shore solver. Test Mod only. */
final class BoundaryGpuTest {
    private static void uniform(int program, String name, double value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1f(location, (float) value);
    }
    static double verify(int x, int z) {
        var client = MinecraftClient.getInstance(); var c = WavesClient.config;
        var camera = client.gameRenderer.getCamera().getPos();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        int previousBase = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0);
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean discard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        GL13.glActiveTexture(GL13.GL_TEXTURE7);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), program = GL20.glCreateProgram();
        int vao = GL30.glGenVertexArrays(), buffer = GL15.glGenBuffers();
        try {
            GL20.glShaderSource(shader, "#version 330 core\n" + NativeWaves.resource("waves.glsl").replace("texture2D(", "texture(")
                    + "\nuniform vec3 test_position; out float test_height; void main(){ vec3 n; vec4 p=waves_displace(vec4(test_position,1),vec3(0,1,0),1,n); test_height=p.y+waves_camera_y; gl_Position=vec4(0,0,0,1); }\n");
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(program, shader);
            GL30.glTransformFeedbackVaryings(program, new String[]{"test_height"}, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, buffer);
            GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 4, GL15.GL_STREAM_READ);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            uniform(program, "waves_amplitude", WavesClient.amplitude());
            uniform(program, "waves_wavelength", c.wavelength); uniform(program, "waves_direction", Math.toRadians(c.direction));
            uniform(program, "waves_sharpness", c.sharpness); uniform(program, "waves_irregularity", c.irregularity);
            uniform(program, "waves_water_id", 1); uniform(program, "waves_camera_y", camera.y);
            uniform(program, "waves_sea_level", c.seaLevel); uniform(program, "waves_sea_only", c.seaLevelOnly ? 1 : 0);
            uniform(program, "waves_time", WavesClient.seconds() * c.speed);
            uniform(program, "waves_shore_enabled", ShoreField.enabled());
            uniform(program, "waves_field_origin_x", ShoreField.originRelativeX()); uniform(program, "waves_field_origin_z", ShoreField.originRelativeZ());
            uniform(program, "waves_shore_fade", c.shoreFade); uniform(program, "waves_reflection", c.reflection);
            for (int i = 0; i < 8; i++) uniform(program, "waves_phase_" + i, WavesClient.phase(i));
            GL33.glBindSampler(7, 0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShoreField.textureId());
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "waves_shore_field"), 7);
            int position = GL20.glGetUniformLocation(program, "test_position");
            double maximum = 0; float[] result = new float[1];
            for (int i = 0; i < 12; i++) {
                int px = x + i % 4, pz = z + i / 4;
                GL20.glUniform3f(position, (float) (px - camera.x), (float) (c.seaLevel - .112111 - camera.y), (float) (pz - camera.z));
                GL30.glBeginTransformFeedback(GL11.GL_POINTS); GL11.glDrawArrays(GL11.GL_POINTS, 0, 1); GL30.glEndTransformFeedback();
                GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
                maximum = Math.max(maximum, Math.abs(result[0] - RunupSurface.sampleOceanVertex(px, pz)));
            }
            if (maximum > .005) throw new AssertionError("CPU shore differs from current GPU ocean by " + maximum + " blocks");
            WavesClient.LOGGER.info("OCEAN_BOUNDARY_GPU_PASS samples=12 maxError={}", maximum);
            return maximum;
        } finally {
            if (!discard) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, previousBase);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, previousBuffer);
            GL30.glBindVertexArray(previousVao); GL20.glUseProgram(previousProgram);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture); GL33.glBindSampler(7, previousSampler);
            GL13.glActiveTexture(previousActive);
            GL15.glDeleteBuffers(buffer); GL30.glDeleteVertexArrays(vao); GL20.glDeleteProgram(program); GL20.glDeleteShader(shader);
        }
    }
}
