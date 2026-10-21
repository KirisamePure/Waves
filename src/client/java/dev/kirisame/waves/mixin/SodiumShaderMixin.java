package dev.kirisame.waves.mixin;

import dev.kirisame.waves.render.NativeWaves;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public abstract class SodiumShaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void waves$native(Identifier id, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(NativeWaves.patch(id, cir.getReturnValue()));
    }
}
