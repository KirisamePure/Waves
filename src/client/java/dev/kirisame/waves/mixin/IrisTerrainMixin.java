package dev.kirisame.waves.mixin;

import dev.kirisame.waves.iris.IrisBridge;
import com.llamalad7.mixinextras.sugar.Local;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = TransformPatcher.class, remap = false)
public abstract class IrisTerrainMixin {
    // Strings are name, vertex, geometry, tessControl, tessEval, fragment: ordinal 1 is vertex.
    @ModifyVariable(method = "patchSodium", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static String waves$terrain(String vertex) {
        return IrisBridge.patch(vertex);
    }
    @ModifyVariable(method = "patchSodium", at = @At("HEAD"), argsOnly = true, ordinal = 5, remap = false)
    private static String waves$fragment(String fragment, @Local(argsOnly = true, ordinal = 1) String vertex,
                                         @Local(argsOnly = true, ordinal = 2) String geometry,
                                         @Local(argsOnly = true, ordinal = 3) String tessControl,
                                         @Local(argsOnly = true, ordinal = 4) String tessEval) {
        return IrisBridge.patchFragment(fragment, vertex, geometry, tessControl, tessEval);
    }
}
