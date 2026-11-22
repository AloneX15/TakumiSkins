package com.takumistudios.takumi_skins.client;

import com.takumistudios.takumi_skins.SkinCache;
import com.takumistudios.takumi_skins.SkinProtocol;
import com.takumistudios.takumi_skins.SkinProtocol.Profile;
import com.takumistudios.takumi_skins.SkinProtocol.Settings;
import com.takumistudios.takumi_skins.TakumiSkins;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

/**
 * Which Takumi skin each player wears. The render thread only reads a map ({@link #current}); everything else happens
 * on one daemon thread: lookups are batched (up to 64 players every 250 ms), textures are downloaded once, checked
 * against their SHA-256, cached on disk and registered on the render thread. Answers are kept 5 minutes.
 *
 * <p>Inactive unless Takumi Launcher passed a valid {@code TAKUMI_SKINS_TOKEN}: then nothing is scheduled, no thread is
 * started and every hook returns the vanilla skin untouched. A 401 from the website (token revoked) turns it off until
 * the next launch. Any error keeps the vanilla skin.
 */
public final class SkinRegistry {
    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long RETRY_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long IDLE_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long BATCH_MS = 250;
    private static final String SIGNAL = ".takumi/skin.json";

    /** What we know about one player. Fields are written on the IO thread and read on the render thread. */
    private static final class Entry {
        final String name;
        volatile Profile profile;
        volatile long expiresAt;
        volatile boolean queued;
        volatile boolean settled;
        volatile long lastAccess;

        Entry(String name, long now) {
            this.name = name;
            this.lastAccess = now;
        }
    }

    private static volatile boolean initialized;
    private static volatile Settings settings;
    private static volatile boolean warnedOff;
    private static ScheduledExecutorService executor;
    private static HttpClient http;

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PENDING = new ConcurrentHashMap<>();
    /** Textures registered on the render thread (by hash). */
    private static final Set<String> READY = ConcurrentHashMap.newKeySet();
    /** Textures being downloaded, or failed recently (hash -> time it may be retried). */
    private static final Map<String, Long> LOADING = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> WAITERS = new ConcurrentHashMap<>();
    private static FileTime signalSeen;

    private SkinRegistry() {
    }

    /** True when the game was started from Takumi Launcher with a skins token. Cheap after the first call. */
    public static boolean active() {
        if (!initialized) {
            initialize();
        }
        return settings != null;
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            Optional<Settings> found = SkinProtocol.settings(System::getenv, System::getProperty);
            if (found.isPresent()) {
                start(found.get());
            } else {
                TakumiSkins.LOGGER.info("Takumi Skins inactivo: el juego no se ha lanzado desde Takumi Launcher.");
            }
        } catch (RuntimeException e) {
            TakumiSkins.LOGGER.error("No se pudo iniciar Takumi Skins; se muestran las skins normales", e);
        } finally {
            initialized = true;
        }
    }

    /**
     * Starts (or restarts) with explicit settings. Used by the client tests, which point it at a local server; Takumi
     * Launcher users get the settings from the environment.
     */
    public static synchronized void configure(Settings next) {
        if (executor != null) {
            executor.shutdownNow();
        }
        ENTRIES.clear();
        PENDING.clear();
        LOADING.clear();
        start(next);
        initialized = true;
    }

    private static void start(Settings next) {
        http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Takumi Skins-IO");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(SkinRegistry::safely, BATCH_MS, BATCH_MS, TimeUnit.MILLISECONDS);
        // Images the website no longer has (nobody wears them) would stay on disk forever otherwise.
        executor.execute(() -> {
            int deleted = SkinCache.prune(cacheDir(), Instant.now());
            if (deleted > 0) {
                TakumiSkins.LOGGER.info("Caché de Takumi Skins: {} imágenes sin usar borradas", deleted);
            }
        });
        settings = next;
        TakumiSkins.LOGGER.info("Takumi Skins activo ({})", next.baseUrl());
    }

    private static void turnOff(String reason) {
        settings = null;
        if (!warnedOff) {
            warnedOff = true;
            TakumiSkins.LOGGER.warn("Takumi Skins desactivado hasta reiniciar el juego: {}", reason);
        }
        WAITERS.values().forEach(future -> future.complete(null));
        WAITERS.clear();
    }

    // ─── Render thread ─────────────────────────────────────────────────────────

    /**
     * The player's Takumi skin once its textures are ready, or null (no Takumi skin, not known yet, or inactive). Asks
     * the website in the background when the answer is missing or old. Never blocks and never throws.
     */
    public static Profile current(UUID id, String name) {
        if (!active() || id == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            if (!SkinProtocol.shouldLookup(id, name)) {
                return null;
            }
            entry = ENTRIES.computeIfAbsent(id, key -> new Entry(name, now));
        }
        entry.lastAccess = now;
        if (!entry.queued && entry.expiresAt <= now) {
            entry.queued = true;
            PENDING.put(id, entry.name);
        }
        Profile profile = entry.profile;
        return profile != null && texturesReady(profile) ? profile : null;
    }

    private static boolean texturesReady(Profile profile) {
        return (profile.skin() == null || READY.contains(profile.skin()))
                && (profile.cape() == null || READY.contains(profile.cape()));
    }

    /** Completes once this player's Takumi skin is known and loaded (or known to be absent). */
    public static CompletableFuture<Void> whenSettled(UUID id, String name) {
        if (!active() || id == null || !SkinProtocol.shouldLookup(id, name)) {
            return CompletableFuture.completedFuture(null);
        }
        current(id, name);
        Entry entry = ENTRIES.get(id);
        if (entry != null && entry.settled && (entry.profile == null || texturesReady(entry.profile))) {
            return CompletableFuture.completedFuture(null);
        }
        return WAITERS.computeIfAbsent(id, key -> new CompletableFuture<>());
    }

    /** {@code /takumiskins recargar} and the launcher's skin.json signal: ask again for everyone. */
    public static void reloadAll() {
        if (!active()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Entry> item : ENTRIES.entrySet()) {
            item.getValue().expiresAt = 0;
            if (!item.getValue().queued && now - item.getValue().lastAccess < IDLE_MS) {
                item.getValue().queued = true;
                PENDING.put(item.getKey(), item.getValue().name);
            }
        }
    }

    // ─── IO thread ─────────────────────────────────────────────────────────────

    private static void safely() {
        try {
            tick();
        } catch (RuntimeException e) {
            TakumiSkins.LOGGER.warn("Error en Takumi Skins; se reintentará", e);
        }
    }

    private static long lastSweep;
    private static long lastSignalCheck;

    private static void tick() {
        Settings current = settings;
        if (current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSignalCheck > 2000) {
            lastSignalCheck = now;
            checkSignal();
        }
        if (!PENDING.isEmpty()) {
            lookup(current, drainBatch());
        }
        if (now - lastSweep > 60_000) {
            lastSweep = now;
            sweep(now);
        }
    }

    private static Map<UUID, String> drainBatch() {
        Map<UUID, String> batch = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, String>> it = PENDING.entrySet().iterator();
        while (it.hasNext() && batch.size() < SkinProtocol.MAX_BATCH) {
            Map.Entry<UUID, String> item = it.next();
            batch.put(item.getKey(), item.getValue());
            it.remove();
        }
        return batch;
    }

    private static void lookup(Settings current, Map<UUID, String> batch) {
        Map<UUID, Optional<Profile>> answer;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(current.baseUrl() + "/api/skins/lookup"))
                    .timeout(Duration.ofSeconds(10))
                    .header("authorization", "Bearer " + current.token())
                    .header("content-type", "application/json")
                    .header("user-agent", "TakumiSkins/" + TakumiSkins.version())
                    .POST(HttpRequest.BodyPublishers.ofString(SkinProtocol.lookupBody(batch)))
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() == 401) {
                    turnOff("la web no acepta el token de Takumi Launcher");
                    return;
                }
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                String json = new String(body.readNBytes(SkinProtocol.MAX_LOOKUP_RESPONSE_BYTES), StandardCharsets.UTF_8);
                answer = SkinProtocol.parseLookup(json, batch.keySet());
            }
        } catch (IOException | RuntimeException e) {
            logFailure("Consulta de skins fallida; se reintentará en un minuto", e);
            retryLater(batch);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        long now = System.currentTimeMillis();
        answer.forEach((id, profile) -> {
            Entry entry = ENTRIES.get(id);
            if (entry == null) {
                return;
            }
            entry.profile = profile.orElse(null);
            entry.expiresAt = now + TTL_MS;
            entry.queued = false;
            entry.settled = true;
            profile.ifPresent(found -> {
                ensureTexture(current, found.skin());
                ensureTexture(current, found.cape());
            });
            notifySettled(id, entry);
        });
    }

    private static volatile boolean warnedFailure;

    /** The first network problem is a warning (useful to diagnose); later ones only go to the debug log. */
    private static void logFailure(String message, Exception e) {
        if (!warnedFailure) {
            warnedFailure = true;
            TakumiSkins.LOGGER.warn("{}: {}", message, e.toString());
        }
        TakumiSkins.LOGGER.debug(message, e);
    }

    private static void retryLater(Map<UUID, String> batch) {
        long retryAt = System.currentTimeMillis() + RETRY_MS;
        batch.keySet().forEach(id -> {
            Entry entry = ENTRIES.get(id);
            if (entry != null) {
                entry.expiresAt = retryAt;
                entry.queued = false;
                entry.settled = true; // keep whatever we had; do not hold player heads waiting
                notifySettled(id, entry);
            }
        });
    }

    private static void notifySettled(UUID id, Entry entry) {
        if (entry.profile == null || texturesReady(entry.profile)) {
            CompletableFuture<Void> waiter = WAITERS.remove(id);
            if (waiter != null) {
                waiter.complete(null);
            }
        }
    }

    private static void notifyTexture() {
        WAITERS.forEach((id, waiter) -> {
            Entry entry = ENTRIES.get(id);
            if (entry != null && entry.settled) {
                notifySettled(id, entry);
            }
        });
    }

    private static void ensureTexture(Settings current, String sha) {
        if (sha == null || READY.contains(sha)) {
            return;
        }
        Long retryAt = LOADING.get(sha);
        if (retryAt != null && (retryAt == Long.MAX_VALUE || retryAt > System.currentTimeMillis())) {
            return;
        }
        LOADING.put(sha, Long.MAX_VALUE);
        try {
            byte[] png = readCached(sha);
            if (png == null) {
                png = download(current, sha);
                writeCache(sha, png);
            }
            var image = SkinTextures.decode(png);
            Minecraft.getInstance().execute(() -> {
                try {
                    SkinTextures.register(sha, image);
                    READY.add(sha);
                    LOADING.remove(sha);
                    executor.execute(SkinRegistry::notifyTexture);
                } catch (RuntimeException | LinkageError e) {
                    image.close();
                    LOADING.put(sha, System.currentTimeMillis() + RETRY_MS);
                    TakumiSkins.LOGGER.warn("No se pudo registrar una textura de Takumi Skins", e);
                }
            });
        } catch (IOException | RuntimeException e) {
            LOADING.put(sha, System.currentTimeMillis() + RETRY_MS);
            logFailure("No se pudo descargar una textura de Takumi Skins; se reintentará", e);
        } catch (InterruptedException e) {
            LOADING.remove(sha);
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] download(Settings current, String sha) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(current.baseUrl() + "/api/skins/textures/" + sha + ".png"))
                .timeout(Duration.ofSeconds(10))
                .header("authorization", "Bearer " + current.token())
                .header("user-agent", "TakumiSkins/" + TakumiSkins.version())
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 401) {
                turnOff("la web no acepta el token de Takumi Launcher");
                throw new IOException("HTTP 401");
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            byte[] data = body.readNBytes(SkinProtocol.MAX_TEXTURE_BYTES + 1);
            if (data.length > SkinProtocol.MAX_TEXTURE_BYTES || !SkinProtocol.looksLikePng(data)
                    || !sha.equals(SkinProtocol.sha256(data))) {
                throw new IOException("Textura no válida");
            }
            return data;
        }
    }

    private static Path cacheDir() {
        return TakumiSkins.gameDir().resolve(".takumi").resolve("skins");
    }

    private static byte[] readCached(String sha) {
        try {
            Path file = cacheDir().resolve(sha + ".png");
            if (!Files.isRegularFile(file) || Files.size(file) > SkinProtocol.MAX_TEXTURE_BYTES) {
                return null;
            }
            byte[] data = Files.readAllBytes(file);
            if (sha.equals(SkinProtocol.sha256(data))) {
                SkinCache.touch(file, Instant.now()); // still in use: keep it out of the clean-up
                return data;
            }
            Files.deleteIfExists(file); // damaged: download again
        } catch (IOException e) {
            TakumiSkins.LOGGER.debug("Caché de skins ilegible", e);
        }
        return null;
    }

    private static void writeCache(String sha, byte[] data) {
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Path tmp = dir.resolve(sha + ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, dir.resolve(sha + ".png"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // The cache only saves downloads; the texture is still used.
            TakumiSkins.LOGGER.debug("No se pudo guardar la skin en caché", e);
        }
    }

    /** Takumi Launcher touches .takumi/skin.json after the player changes their skin. */
    private static void checkSignal() {
        try {
            Path file = TakumiSkins.gameDir().resolve(SIGNAL);
            if (!Files.isRegularFile(file)) {
                return;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (signalSeen == null) {
                signalSeen = modified; // only changes made while the game runs
                return;
            }
            if (!modified.equals(signalSeen)) {
                signalSeen = modified;
                reloadAll();
            }
        } catch (IOException | RuntimeException e) {
            TakumiSkins.LOGGER.debug("No se pudo leer {}", SIGNAL, e);
        }
    }

    /** Forgets players not seen for 10 minutes and frees the textures nobody wears any more. */
    private static void sweep(long now) {
        ENTRIES.entrySet().removeIf(item -> now - item.getValue().lastAccess > IDLE_MS && !item.getValue().queued);
        Set<String> used = new HashSet<>();
        for (Entry entry : ENTRIES.values()) {
            Profile profile = entry.profile;
            if (profile != null) {
                used.add(profile.skin());
                used.add(profile.cape());
            }
        }
        Set<String> unused = new HashSet<>(READY);
        unused.removeAll(used);
        if (!unused.isEmpty()) {
            READY.removeAll(unused);
            Minecraft.getInstance().execute(() -> unused.forEach(SkinTextures::release));
        }
    }
}
