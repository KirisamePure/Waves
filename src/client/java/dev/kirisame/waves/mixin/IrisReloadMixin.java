package dev.kirisame.waves.mixin;

import dev.kirisame.waves.iris.IrisBridge;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Iris.class, remap = false)
public abstract class IrisReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"), remap = false)
    private static void waves$resetStatus(CallbackInfo ci) {
        IrisBridge.resetStatus();
    }
}
