package dev.kirisame.waves.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Logger logger;
    private boolean writable = true;

    public ConfigStore(Path path, Logger logger) { this.path = path; this.logger = logger; }

    public WavesConfig load() {
        if (!Files.exists(path)) {
            WavesConfig config = new WavesConfig();
            save(config);
            return config;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WavesConfig config = GSON.fromJson(reader, WavesConfig.class);
            if (config == null) throw new IOException("Empty Waves configuration");
            config.sanitize();
            return config;
        } catch (Exception error) {
            logger.error("Cannot read {}; keeping the original file and using defaults", path, error);
            // Preserve malformed/user-owned content; subsequent edits must not silently destroy it.
            try {
                Files.copy(path, path.resolveSibling("waves.json.invalid-" + System.currentTimeMillis()));
            } catch (IOException backupError) {
                writable = false;
                logger.error("Could not back up Waves config; disk writes disabled this session", backupError);
            }
            return new WavesConfig();
        }
    }

    public boolean save(WavesConfig config) {
        if (!writable) return false;
        config.sanitize();
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "waves-", ".tmp");
            Files.writeString(temporary, GSON.toJson(config) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            logger.error("Could not save Waves configuration {}", path, error);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
