package io.github.zatone0.ae2craftingrecovery.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import net.minecraftforge.fml.loading.FMLPaths;

import io.github.zatone0.ae2craftingrecovery.AE2CraftingRecovery;

/** Small independent rolling log for recovery evidence that would overwhelm latest.log. */
public final class RecoveryDiagnostics {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int GENERATIONS = 3;
    private static final Object LOCK = new Object();

    private RecoveryDiagnostics() {
    }

    public static void record(String message) {
        synchronized (LOCK) {
            try {
                Path log = FMLPaths.GAMEDIR.get().resolve("logs").resolve("ae2-crafting-recovery.log");
                Files.createDirectories(log.getParent());
                if (Files.exists(log) && Files.size(log) >= MAX_BYTES) {
                    rotate(log);
                }
                String line = Instant.now() + " " + message + System.lineSeparator();
                Files.writeString(log, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                AE2CraftingRecovery.LOGGER.warn("Could not write dedicated AE2 recovery diagnostic log", e);
            }
        }
    }

    private static void rotate(Path log) throws IOException {
        Files.deleteIfExists(Path.of(log + "." + GENERATIONS));
        for (int i = GENERATIONS - 1; i >= 1; i--) {
            Path from = Path.of(log + "." + i);
            if (Files.exists(from)) {
                Files.move(from, Path.of(log + "." + (i + 1)), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(log, Path.of(log + ".1"), StandardCopyOption.REPLACE_EXISTING);
    }
}
