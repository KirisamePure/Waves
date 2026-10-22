package dev.kirisame.waves.particle;

import dev.kirisame.waves.WavesClient;
import dev.kirisame.waves.math.WaveMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** One weather-driven wind bed, with smooth gusts and optional indoor/underwater shelter. */
public final class StormWind {
    private static final SoundEvent WIND = SoundEvent.of(Identifier.of("waves", "storm_wind"));
    private static Loop loop;
    private static long starts;
    private StormWind() {}
    public static void reset() {
        if (loop != null) MinecraftClient.getInstance().getSoundManager().stop(loop);
        loop = null; starts = 0;
    }
    public static float volume() { return loop == null ? 0 : loop.getVolume(); }
    public static boolean looping() { return loop != null; }
    public static long starts() { return starts; }
    public static void tick(MinecraftClient client) {
        var c = WavesClient.config;
        double target = 0;
        if (client.world != null && client.player != null && c.enabled && c.windEnabled
                && (!c.overworldOnly || client.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD))) {
            double weather = WaveMath.windStrength(client.world.getRainGradient(1), client.world.getThunderGradient(1), c.windRain, c.windThunder);
            double t = WavesClient.seconds();
            double gust = 1 - c.windGusts * (.5 + .3 * Math.sin(t * .67) + .2 * Math.sin(t * 1.31 + 1.4));
            double shelter = 1;
            if (c.windShelter) {
                if (client.player.isSubmergedInWater()) shelter = .1;
                else if (!client.world.isSkyVisible(client.player.getBlockPos())) shelter = .2;
            }
            target = Math.clamp(c.windVolume * weather * gust * shelter, 0, 1);
        }
        if (loop == null && target > .002) {
            loop = new Loop(client);
            if (client.getSoundManager().play(loop) == net.minecraft.client.sound.SoundSystem.PlayResult.NOT_STARTED) loop = null;
            else starts++;
        }
        if (loop != null) {
            loop.update((float) target, (float) c.windPitch);
            if (target == 0 && loop.getVolume() < .002) { client.getSoundManager().stop(loop); loop = null; }
        }
    }
    private static final class Loop extends MovingSoundInstance {
        Loop(MinecraftClient client) {
            super(WIND, SoundCategory.AMBIENT, client.world.random);
            repeat = true; repeatDelay = 0; relative = true;
            attenuationType = AttenuationType.NONE; volume = .001f;
        }
        void update(float target, float newPitch) { volume += (target - volume) * .04f; pitch = newPitch; }
        @Override public void tick() {}
        @Override public boolean shouldAlwaysPlay() { return true; }
    }
}
