package com.takumistudios.takumi_skins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Limpieza de la caché de texturas ({@code .takumi/skins/}). */
class SkinCacheTest {
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);

    private static Path file(Path dir, String name, Duration age) throws IOException {
        Path path = Files.write(dir.resolve(name), new byte[] {1, 2, 3});
        Files.setLastModifiedTime(path, FileTime.from(NOW.minus(age)));
        return path;
    }

    @Test
    void deletesImagesUnusedForThirtyDaysAndKeepsRecentOnes(@TempDir Path dir) throws IOException {
        Path old = file(dir, SHA_A + ".png", SkinCache.MAX_UNUSED.plusDays(1));
        Path recent = file(dir, SHA_B + ".png", SkinCache.MAX_UNUSED.minusDays(1));
        assertEquals(1, SkinCache.prune(dir, NOW));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
    }

    @Test
    void usingAnImageKeepsItInTheCache(@TempDir Path dir) throws IOException {
        Path used = file(dir, SHA_A + ".png", SkinCache.MAX_UNUSED.plusDays(5));
        SkinCache.touch(used, NOW);
        assertEquals(0, SkinCache.prune(dir, NOW));
        assertTrue(Files.exists(used));
    }

    @Test
    void deletesUnfinishedDownloadsAfterAnHour(@TempDir Path dir) throws IOException {
        Path stale = file(dir, SHA_A + ".tmp", Duration.ofHours(2));
        Path writing = file(dir, SHA_B + ".tmp", Duration.ofMinutes(1));
        assertEquals(1, SkinCache.prune(dir, NOW));
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(writing));
    }

    @Test
    void neverTouchesFilesThatAreNotCacheEntries(@TempDir Path dir) throws IOException {
        Duration ancient = Duration.ofDays(400);
        Path other = file(dir, "notas.txt", ancient);
        Path renamed = file(dir, "mi-skin.png", ancient);
        Path upper = file(dir, "A".repeat(64) + ".png", ancient);
        Files.createDirectory(dir.resolve(SHA_B + ".png"));
        assertEquals(0, SkinCache.prune(dir, NOW));
        assertTrue(Files.exists(other) && Files.exists(renamed) && Files.exists(upper));
    }

    @Test
    void missingFolderIsNotAnError(@TempDir Path dir) {
        assertEquals(0, SkinCache.prune(dir.resolve("no-existe"), NOW));
    }
}
