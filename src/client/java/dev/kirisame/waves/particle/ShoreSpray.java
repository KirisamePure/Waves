package dev.kirisame.waves.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Local-only spray: actual launch velocity, gravity, drag and block collision. */
public final class ShoreSpray extends BillboardParticle {
    public static final SimpleParticleType TYPE = FabricParticleTypes.simple(false);
    private static long spawned, returned;
    private final double surface, launchY;
    private static double maximumRise;

    private ShoreSpray(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, Sprite sprite) {
        super(world, x, y, z, sprite);
        setVelocity(vx, vy, vz); // Vanilla splash factories discard most of the supplied launch velocity.
        gravityStrength = .7f;
        velocityMultiplier = .97f;
        collidesWithWorld = true;
        maxAge = 34 + random.nextInt(14);
        scale = (.07f + random.nextFloat() * .10f) * (float) dev.kirisame.waves.WavesClient.config.splashSize;
        red = .78f; green = .91f; blue = 1;
        surface = dev.kirisame.waves.WavesClient.config.seaLevel - .111111;
        launchY = y;
        spawned++;
    }

    @Override public void tick() {
        super.tick();
        maximumRise = Math.max(maximumRise, y - launchY);
        alpha = Math.min(1, (maxAge - age) / 7f);
        if (onGround || (velocityY < 0 && y <= surface && age > 3)) {
            if (isAlive()) {
                returned++;
                world.addParticleClient(net.minecraft.particle.ParticleTypes.SPLASH, x, y, z, 0, 0, 0);
            }
            markDead();
        }
    }
    @Override protected RenderType getRenderType() { return RenderType.PARTICLE_ATLAS_TRANSLUCENT; }
    public static double maximumRise() { return maximumRise; }
    public static long spawned() { return spawned; }
    public static long returned() { return returned; }

    public static void initialize() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of("waves", "shore_spray"), TYPE);
        ParticleFactoryRegistry.getInstance().register(TYPE, sprites ->
                (effect, world, x, y, z, vx, vy, vz, random) ->
                        new ShoreSpray(world, x, y, z, vx, vy, vz, sprites.getSprite(random)));
    }
}
