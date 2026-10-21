package dev.kirisame.waves.mixin;

import dev.kirisame.waves.render.NativeWaves;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumRendererMixin {
    @Inject(method = "begin", at = @At("TAIL"))
    private void waves$begin(CallbackInfo ci) { NativeWaves.begin(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
    @Inject(method = "end", at = @At("HEAD"))
    private void waves$end(CallbackInfo ci) { NativeWaves.end(); }
}
