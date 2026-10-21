package dev.kirisame.waves.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kirisame.waves.render.NativeWaves;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class WaterMaterialMixin {
    private static final java.util.concurrent.ConcurrentHashMap<Material, Material> waves$materials = new java.util.concurrent.ConcurrentHashMap<>();
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private Material waves$water(Material material, @Local(argsOnly = true) FluidState fluid) {
        if (!fluid.isIn(FluidTags.WATER)) return material;
        return waves$materials.computeIfAbsent(material, original -> new Material(original.pass, original.alphaCutoff, original.mipped) {
            @Override public int bits() { return super.bits() | NativeWaves.WATER_BIT; }
        });
    }
}
