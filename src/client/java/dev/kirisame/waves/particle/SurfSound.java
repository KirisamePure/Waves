package dev.kirisame.waves.particle;

import dev.kirisame.waves.WavesClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SurfSound {
    private static final SoundEvent SURF = SoundEvent.of(Identifier.of("waves", "shore_break"));
    private static long nextSoundTick, count;
    private SurfSound() {}
    public static void reset() { nextSoundTick = 0; count = 0; }
    public static long count() { return count; }
    public static void impact(MinecraftClient client, long tick, double x, double y, double z, double energy) {
        var c = WavesClient.config;
        if (!c.sounds || c.soundVolume <= 0 || tick < nextSoundTick) return;
        // Global cooldown prevents an entire shoreline stacking dozens of recordings.
        nextSoundTick = tick + 18 + client.world.random.nextInt(12);
        float volume = (float) (c.soundVolume * Math.clamp(.35 + energy * .4, .3, 1));
        float pitch = .85f + client.world.random.nextFloat() * .25f;
        var result = client.getSoundManager().play(new PositionedSoundInstance(SURF, SoundCategory.AMBIENT,
                volume, pitch, client.world.random, x, y, z));
        if (result != net.minecraft.client.sound.SoundSystem.PlayResult.NOT_STARTED) count++;
    }
}
