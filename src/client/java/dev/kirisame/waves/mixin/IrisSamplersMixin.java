package dev.kirisame.waves.mixin;

import com.google.common.collect.ImmutableSet;
import dev.kirisame.waves.iris.ShoreField;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisSamplersMixin {
    @Inject(method = "addGbufferOrShadowSamplers", at = @At("TAIL"), remap = false)
    private void waves$shoreSampler(SamplerHolder samplers, ImageHolder images, Supplier<ImmutableSet<Integer>> flipped,
                                   boolean shadow, boolean textured, boolean lightmap, boolean overlay, CallbackInfo ci) {
        samplers.addDynamicSampler(ShoreField::textureId, "waves_shore_field");
        samplers.addDynamicSampler(dev.kirisame.waves.render.RunupSurface::textureId, "waves_surface_mask");
    }
}
