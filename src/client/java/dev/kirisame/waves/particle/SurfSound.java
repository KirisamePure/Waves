package dev.kirisame.waves.particle;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.iris.ShoreField;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** A single seamless proximity ambience, independent of wave impact/particle timing. */
public final class SurfSound {
    private static final SoundEvent SURF = SoundEvent.of(Identifier.of("waves", "shore_ambience"));
    private static Loop loop;
    private static long count;
    private SurfSound() {}
    public static void reset() {
        if (loop != null) MinecraftClient.getInstance().getSoundManager().stop(loop);
        loop = null;
        count = 0;
    }
    public static long count() { return count; }
    public static float volume() { return loop == null ? 0 : loop.getVolume(); }
    public static boolean looping() { return loop != null && !loop.isDone(); }

    public static void tick(MinecraftClient client) {
        var c = WavesClient.config;
        double distance = ShoreField.nearestContactDistance(client);
        boolean allowed = c.enabled && c.sounds && c.soundVolume > 0 && client.world != null
                && (!c.overworldOnly || client.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD));
        double proximity = Math.clamp((c.soundDistance - distance) / Math.max(1, c.soundDistance - 3), 0, 1);
        float target = allowed ? (float) (c.soundVolume * proximity * proximity * (3 - 2 * proximity)) : 0;
        if (loop == null && target > .001) {
            loop = new Loop(client);
            var result = client.getSoundManager().play(loop);
            if (result == net.minecraft.client.sound.SoundSystem.PlayResult.NOT_STARTED) loop = null;
            else count++;
        }
        if (loop != null) {
            loop.target = target;
            // Update with client ticks, including tests that advance ticks without drawing frames.
            loop.fade();
            if (target == 0 && loop.getVolume() < .002) {
                client.getSoundManager().stop(loop);
                loop = null;
            }
        }
    }

    private static final class Loop extends MovingSoundInstance {
        private float target;
        Loop(MinecraftClient client) {
            super(SURF, SoundCategory.AMBIENT, client.world.random);
            repeat = true;
            repeatDelay = 0;
            relative = true;
            attenuationType = AttenuationType.NONE; // Distance is applied above, smoothly over shoreline proximity.
            volume = .001f;
            pitch = 1;
        }
        void fade() { volume += (target - volume) * .08f; }
        @Override public void tick() {}
        @Override public boolean shouldAlwaysPlay() { return true; }
    }
}
