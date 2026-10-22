package dev.kirisame.waves.iris;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.math.ShoreMath;
import dev.kirisame.waves.math.WaveMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Nearby client collision field. Eight rows per tick keep scans off the frame's critical path. */
public final class ShoreField {
    public static final int SIZE = 128;
    private static final int ROWS_PER_TICK = 8;
    private static final boolean[] solid = new boolean[SIZE * SIZE];
    private static final boolean[] water = new boolean[SIZE * SIZE];
    private static float[] distances;
    private static final float[] groundTop = new float[SIZE * SIZE];
    private static final boolean[] runupWater = new boolean[SIZE * SIZE];
    private static int originX, originZ, scanX, scanZ, row, scanY;
    private static int texture;
    private static boolean ready;
    private static long ticks, impactCount;
    private static int contactCursor;
    private static final List<Contact> contacts = new ArrayList<>();

    private ShoreField() {}

    public static void reset() {
        ready = false;
        row = 0;
        distances = null;
        contacts.clear();
        impactCount = 0;
        ticks = 0;
        dev.kirisame.waves.particle.SurfSound.reset();
        dev.kirisame.waves.particle.ShoreSpray.reset();
        dev.kirisame.waves.particle.StormWind.reset();
        dev.kirisame.waves.render.RunupSurface.reset();
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        ticks++;
        if (row == 0) {
            scanX = client.player.getBlockX() - SIZE / 2;
            scanZ = client.player.getBlockZ() - SIZE / 2;
            scanY = (int) Math.round(WavesClient.config.seaLevel);
            Arrays.fill(solid, false);
            Arrays.fill(water, false);
            Arrays.fill(groundTop, Float.POSITIVE_INFINITY);
            Arrays.fill(runupWater, false);
        }
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int end = Math.min(SIZE, row + ROWS_PER_TICK);
        for (int z = row; z < end; z++) for (int x = 0; x < SIZE; x++) {
            int index = z * SIZE + x;
            pos.set(scanX + x, scanY - 1, scanZ + z);
            if (!client.world.isChunkLoaded(pos)) continue;
            water[index] = client.world.getFluidState(pos).isIn(FluidTags.WATER);
            // Solids intersecting the surface, plus very shallow rocks, react.
            for (int y = scanY - 2; y <= scanY; y++) {
                pos.setY(y);
                var state = client.world.getBlockState(pos);
                if (!state.getCollisionShape(client.world, pos).isEmpty()) {
                    solid[index] = true;
                    break;
                }
            }
            if (WavesClient.config.runupEnabled) {
                for (int y = scanY + 5; y >= scanY - 2; y--) {
                    pos.setY(y);
                    var shape = client.world.getBlockState(pos).getCollisionShape(client.world, pos);
                    if (shape.isEmpty()) continue;
                    var boxes = shape.getBoundingBoxes();
                    var above = client.world.getBlockState(pos.up()).getCollisionShape(client.world, pos.up());
                    if (boxes.size() == 1 && above.isEmpty()) {
                        var box = boxes.getFirst();
                        if (box.minX <= .001 && box.minZ <= .001 && box.maxX >= .999 && box.maxZ >= .999) {
                            groundTop[index] = y + (float) box.maxY;
                        }
                    }
                    break;
                }
                runupWater[index] = water[index] && (!solid[index] || groundTop[index] < scanY - .12f);
            }
        }
        row = end;
        if (row == SIZE) {
            distances = ShoreMath.distances(solid, SIZE);
            originX = scanX;
            originZ = scanZ;
            upload(distances);
            ready = true;
            rebuildContacts(client);
            dev.kirisame.waves.render.RunupSurface.rebuild(client, groundTop, runupWater, SIZE, originX, originZ);
            row = 0;
        }
        emitImpacts(client);
    }

    private static void rebuildContacts(MinecraftClient client) {
        // Keep recent contacts/cooldowns when the window did not move.
        var previous = new java.util.HashMap<Long, Long>();
        for (Contact contact : contacts) previous.put(contact.key(), contact.lastImpact);
        contacts.clear();
        for (int z = 1; z < SIZE - 1; z++) for (int x = 1; x < SIZE - 1; x++) {
            int i = z * SIZE + x;
            if (!water[i] || solid[i]) continue;
            double wx = originX + x + .5, wz = originZ + z + .5;
            double dx = wx - client.player.getX(), dz = wz - client.player.getZ();
            if (dx * dx + dz * dz > 28 * 28) continue;
            int nx = (solid[i - 1] ? 1 : 0) - (solid[i + 1] ? 1 : 0);
            int nz = (solid[i - SIZE] ? 1 : 0) - (solid[i + SIZE] ? 1 : 0);
            if (nx == 0 && nz == 0) continue;
            Contact contact = new Contact(wx, wz, nx, nz);
            contact.lastImpact = previous.getOrDefault(contact.key(), -100L);
            contacts.add(contact);
        }
        if (contactCursor >= contacts.size()) contactCursor = 0;
    }

    private static void emitImpacts(MinecraftClient client) {
        var c = WavesClient.config;
        double amplitude = WavesClient.amplitude();
        if (!c.shorelineEnabled || !c.splashes || c.speed == 0 || amplitude < .05 || contacts.isEmpty()) return;
        int budget = Math.min(12, contacts.size());
        int sprayBudget = 96;
        for (int i = 0; i < budget; i++) {
            Contact contact = contacts.get(contactCursor++ % contacts.size());
            if (ticks - contact.lastImpact < 24) continue;
            double time = WavesClient.seconds();
            double height = incomingHeight(contact.x, contact.z, time, amplitude);
            double before = incomingHeight(contact.x, contact.z, time - .12, amplitude);
            if (height < amplitude * .18 || height - before < amplitude * .035) continue;

            contact.lastImpact = ticks;
            impactCount++;
            double wind = Math.toRadians(c.direction);
            double energy = Math.max(.25, -(Math.cos(wind) * contact.nx + Math.sin(wind) * contact.nz));
            double y = c.seaLevel - .111111 + Math.max(0, height) * .2;
            // The normal points out of the rock: launch spray upwards and back towards open water.
            if (!c.splashes || c.splashDensity <= 0) continue;
            double normalLength = Math.hypot(contact.nx, contact.nz);
            double nx = contact.nx / normalLength, nz = contact.nz / normalLength;
            int count = Math.min(sprayBudget, Math.min(40, (int) ((10 + amplitude * energy * 16) * c.splashDensity)));
            sprayBudget -= count;
            for (int n = 0; n < count; n++) {
                double spread = client.world.random.nextDouble() - .5;
                double x = contact.x - nz * spread * 1.2;
                double z = contact.z + nx * spread * 1.2;
                double launch = (.12 + Math.sqrt(amplitude) * .09) * c.splashHeight;
                double outward = (.10 + amplitude * .06) * (.6 + client.world.random.nextDouble() * .8);
                double vx = nx * outward - nz * spread * .32;
                double vz = nz * outward + nx * spread * .32;
                double vy = launch * (.65 + client.world.random.nextDouble() * .7);
                dev.kirisame.waves.particle.ShoreSpray.emit(client, x, y + .05, z, vx, vy, vz);
            }
        }
    }

    private static double incomingHeight(double x, double z, double time, double amplitude) {
        var c = WavesClient.config;
        return WaveMath.height(x, z, time, amplitude, c.wavelength, c.direction, c.speed, c.sharpness, c.irregularity);
    }

    public static double nearestContactDistance(MinecraftClient client) {
        if (!ready || client.player == null) return Double.POSITIVE_INFINITY;
        double nearest = Double.POSITIVE_INFINITY;
        for (Contact contact : contacts) {
            double dx = contact.x - client.player.getX(), dz = contact.z - client.player.getZ();
            double dy = WavesClient.config.seaLevel - client.player.getY();
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return nearest;
    }

    public static double enabled() { return ready && WavesClient.config.shorelineEnabled ? 1 : 0; }
    public static double originRelativeX() { return originX - MinecraftClient.getInstance().gameRenderer.getCamera().getPos().x; }
    public static double originRelativeZ() { return originZ - MinecraftClient.getInstance().gameRenderer.getCamera().getPos().z; }
    public static boolean ready() { return ready; }
    public static long impactCount() { return impactCount; }
    public static int contactCount() { return contacts.size(); }

    public static double distanceAt(int x, int z) {
        int gx = x - originX, gz = z - originZ;
        if (!ready || gx < 0 || gz < 0 || gx >= SIZE || gz >= SIZE) return ShoreMath.MAX_DISTANCE;
        return distances[gz * SIZE + gx];
    }

    public static int textureId() {
        if (texture == 0) {
            float[] empty = new float[SIZE * SIZE];
            Arrays.fill(empty, ShoreMath.MAX_DISTANCE);
            upload(distances == null ? empty : distances);
        }
        return texture;
    }

    private static void upload(float[] data) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        var buffer = MemoryUtil.memAllocFloat(data.length);
        try {
            // Minecraft uploads atlases with non-default pixel-store state.
            // A tight client buffer must not inherit its stride or PBO binding.
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            buffer.put(data).flip();
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, SIZE, SIZE, 0, GL11.GL_RED, GL11.GL_FLOAT, buffer);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            MemoryUtil.memFree(buffer);
        }
    }

    public static void close() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        reset();
    }

    private static final class Contact {
        final double x, z;
        final int nx, nz;
        long lastImpact;
        Contact(double x, double z, int nx, int nz) { this.x = x; this.z = z; this.nx = nx; this.nz = nz; }
        long key() { return ((long) Math.floor(x) << 32) ^ ((long) Math.floor(z) & 0xffffffffL); }
    }
}
