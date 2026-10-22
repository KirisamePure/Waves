package dev.kirisame.waves.render;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Exact cell coverage for replacing original water geometry. Preserves Minecraft's pixel-store state. */
final class SurfaceMask {
    private int texture;
    int id() { if (texture == 0) upload(new byte[128 * 128]); return texture; }
    void upload(byte[] pixels) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] keys = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS};
        int[] saved = new int[keys.length];
        ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        try {
            for (int i = 0; i < keys.length; i++) { saved[i] = GL11.glGetInteger(keys[i]); GL11.glPixelStorei(keys[i], i == 0 ? 1 : 0); }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            buffer.put(pixels).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, 128, 128, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buffer);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], saved[i]);
            MemoryUtil.memFree(buffer);
        }
    }
    void close() { if (texture != 0) GL11.glDeleteTextures(texture); texture = 0; }
}
