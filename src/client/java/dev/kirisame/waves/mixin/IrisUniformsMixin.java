package dev.kirisame.waves.mixin;

import dev.kirisame.waves.iris.IrisBridge;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
public abstract class IrisUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), remap = false)
    private static void waves$uniforms(UniformHolder holder, IdMap idMap,
                                      PackDirectives directives, FrameUpdateNotifier notifier, CallbackInfo ci) {
        IrisBridge.register(holder);
    }
}
