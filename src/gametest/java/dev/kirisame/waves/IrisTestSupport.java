package dev.kirisame.waves;

import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;

/** Only loaded by the test when Iris is installed. The fixture has no built-in water displacement. */
public final class IrisTestSupport {
    public static void disable() {
        Iris.getIrisConfig().setShadersEnabled(false);
        Iris.getIrisConfig().setDebugEnabled(false);
        reload();
    }
    static void enableFixture() {
        var path = FabricLoader.getInstance().getGameDir().resolve("shaderpacks/Waves-Test-No-Water-Motion.zip");
        try (var stream = IrisTestSupport.class.getResourceAsStream("/waves-test-pack.zip")) {
            Files.createDirectories(path.getParent());
            Files.copy(stream, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        Iris.getIrisConfig().setShaderPackName(path.getFileName().toString());
        Iris.getIrisConfig().setShadersEnabled(true);
        reload();
        if (!IrisApi.getInstance().isShaderPackInUse()) throw new AssertionError("Iris fixture failed to load");
    }
    public static void enableExternal(java.nio.file.Path source) {
        var path = FabricLoader.getInstance().getGameDir().resolve("shaderpacks/Waves-External-Alpha-Test.zip");
        try {
            Files.createDirectories(path.getParent());
            Files.copy(source, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(path.resolveSibling(path.getFileName() + ".txt"), "AA_TYPE=0\n");
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        Iris.getIrisConfig().setShaderPackName(path.getFileName().toString());
        Iris.getIrisConfig().setShadersEnabled(true); reload();
        if (!IrisApi.getInstance().isShaderPackInUse()) throw new AssertionError("External pack failed to load");
    }
    static int program() {
        return ((IrisRenderingPipeline) Iris.getPipelineManager().getPipelineNullable())
                .getSodiumPrograms().getProgram(DefaultTerrainRenderPasses.TRANSLUCENT).handle();
    }
    private static void reload() {
        try { Iris.getIrisConfig().save(); Iris.reload(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
