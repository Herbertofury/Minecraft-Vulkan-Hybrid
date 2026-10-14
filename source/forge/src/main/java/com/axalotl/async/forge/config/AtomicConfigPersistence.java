package com.axalotl.async.forge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** One complete replacement: Forge's watcher never observes partially updated settings. */
public final class AtomicConfigPersistence {
    private AtomicConfigPersistence() {}

    public static synchronized void save(Path file, Map<String, Object> values) {
        try {
            CommentedConfig snapshot = new TomlParser().parse(Files.readString(file, StandardCharsets.UTF_8));
            values.forEach((name, value) -> snapshot.set(List.of("Async Config", name), value));
            Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".harimt-", ".tmp");
            try {
                Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                new TomlWriter().write(snapshot, temporary, WritingMode.REPLACE);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot save Hari configuration " + file, failure);
        }
    }
}
