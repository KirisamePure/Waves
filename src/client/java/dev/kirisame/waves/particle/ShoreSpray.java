package dev.kirisame.waves.particle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleTypes;

/** Real vanilla SPLASH particles, with configurable launch and size. No custom sprite or registry. */
public final class ShoreSpray {
    private record Flight(Particle particle, double launchY) {}
    private static final List<Flight> flights = new ArrayList<>();
    private static long spawned, returned;
    private static double maximumRise;
    private ShoreSpray() {}
    public static void initialize() {}
    public static void reset() { flights.clear(); spawned = returned = 0; maximumRise = 0; }

    public static void emit(MinecraftClient client, double x, double y, double z, double vx, double vy, double vz) {
        // Respect vanilla's particle preference even though we need the returned instance for sizing.
        int setting = client.options.getParticles().getValue().getId();
        if (setting == 2 || (setting == 1 && client.world.random.nextBoolean())) return;
        Particle particle = client.particleManager.addParticle(ParticleTypes.SPLASH, x, y, z, 0, 0, 0);
        if (particle == null) return;
        particle.setVelocity(vx, vy, vz);
        particle.scale((float) dev.kirisame.waves.WavesClient.config.splashSize);
        particle.setMaxAge(16 + client.world.random.nextInt(10));
        spawned++;
        flights.add(new Flight(particle, particle.getBoundingBox().minY));
    }

    public static void tick() {
        var iterator = flights.iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next();
            maximumRise = Math.max(maximumRise, flight.particle.getBoundingBox().minY - flight.launchY);
            if (!flight.particle.isAlive()) { returned++; iterator.remove(); }
        }
    }
    public static double maximumRise() { return maximumRise; }
    public static long spawned() { return spawned; }
    public static long returned() { return returned; }
}
