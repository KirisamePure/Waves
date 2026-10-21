package dev.kirisame.waves.mixin;

import dev.kirisame.waves.iris.IrisBridge;
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
}
