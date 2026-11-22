package com.takumistudios.takumi_skins;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Upkeep of the texture cache in {@code <game>/.takumi/skins/} ({@code <sha256>.png}). Each use updates the file's
 * modification time; images not used for {@link #MAX_UNUSED} are deleted, as are downloads left half-written. The
 * website deletes images nobody wears, so the cache would otherwise only grow. Takumi Launcher applies the same rule
 * before starting the game ({@code skins_mod.rs}), also in instances where the mod no longer runs.
 *
 * <p>Pure logic without Minecraft classes, unit tested. Only files named like a cache entry are touched, and an error
 * never reaches the game: a file that cannot be deleted stays for the next time.
 */
public final class SkinCache {
    /** Images unused for this long are deleted. Same value as {@code CACHE_MAX_UNUSED} in Takumi Launcher. */
    public static final Duration MAX_UNUSED = Duration.ofDays(30);
    /** A {@code .tmp} older than this is a download that never finished. */
    public static final Duration MAX_TEMP_AGE = Duration.ofHours(1);

    private static final Pattern ENTRY = Pattern.compile("[0-9a-f]{64}\\.png");
    private static final Pattern TEMP = Pattern.compile("[0-9a-f]{64}\\.tmp");

    private SkinCache() {
    }

    /** Marks a cached image as just used, so {@link #prune} keeps it. */
    public static void touch(Path file, Instant now) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(now));
        } catch (IOException | RuntimeException e) {
            TakumiSkins.LOGGER.debug("No se pudo marcar {} como usada", file, e);
        }
    }

    /** Deletes old cache entries and leftovers in {@code dir}. Returns how many files were deleted. */
    public static int prune(Path dir, Instant now) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                Duration maxAge = ENTRY.matcher(name).matches() ? MAX_UNUSED
                        : TEMP.matcher(name).matches() ? MAX_TEMP_AGE : null;
                if (maxAge != null && isOlder(file, now.minus(maxAge)) && delete(file)) {
                    deleted++;
                }
            }
        } catch (IOException | RuntimeException e) {
            TakumiSkins.LOGGER.debug("No se pudo limpiar la caché de skins", e);
        }
        return deleted;
    }

    private static boolean isOlder(Path file, Instant cutoff) {
        try {
            return Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false; // in use or read-only: next time
        }
    }
}
