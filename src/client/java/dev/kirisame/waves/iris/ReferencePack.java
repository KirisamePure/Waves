package dev.kirisame.waves.iris;

import dev.kirisame.waves.WavesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;

public final class ReferencePack {
    private ReferencePack() {}

    public static String fileName() {
        String version = FabricLoader.getInstance().getModContainer("waves").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        return "Waves-Reference-" + version + ".zip";
    }

    public static void install(Path gameDirectory) {
        Path destination = gameDirectory.resolve("shaderpacks").resolve(fileName());
        if (Files.exists(destination)) return;
        try (var stream = ReferencePack.class.getResourceAsStream("/waves-reference.zip")) {
            if (stream == null) throw new IOException("Missing bundled Waves Reference pack");
            Files.createDirectories(destination.getParent());
            // No replacement: a pre-existing user pack always wins.
            Files.copy(stream, destination);
            WavesClient.LOGGER.info("Installed reference shader pack at {}. Select it in Iris to enable waves.", destination);
        } catch (IOException error) {
            WavesClient.LOGGER.error("Could not install reference shader pack; an existing compatible Iris pack can still be used", error);
        }
    }

    /** Explicit user action: select the bundled pack, never silently replace a chosen pack. */
    public static boolean enable() {
        install(FabricLoader.getInstance().getGameDir());
        var irisConfig = Iris.getIrisConfig();
        String previousPack = irisConfig.getShaderPackName().orElse(null);
        boolean previousEnabled = irisConfig.areShadersEnabled();
        try {
            irisConfig.setShaderPackName(fileName());
            irisConfig.setShadersEnabled(true);
            irisConfig.save();
            Iris.reload();
            if (!IrisApi.getInstance().isShaderPackInUse()) throw new IOException("Reference shader pack did not activate");
            return true;
        } catch (Exception error) {
            WavesClient.LOGGER.error("Could not enable the reference pack; restoring previous Iris selection", error);
            try {
                irisConfig.setShaderPackName(previousPack);
                irisConfig.setShadersEnabled(previousEnabled);
                irisConfig.save();
                Iris.reload();
            } catch (Exception restoreError) {
                WavesClient.LOGGER.error("Could not restore the previous Iris selection", restoreError);
            }
            return false;
        }
    }
}
