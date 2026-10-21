package dev.kirisame.waves.iris;

import dev.kirisame.waves.WavesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReferencePack {
    private ReferencePack() {}

    public static void install(Path gameDirectory) {
        Path destination = gameDirectory.resolve("shaderpacks/Waves-Reference-0.1.0.zip");
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
}
