package com.takumistudios.takumi_skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What Takumi Skins exchanges with Takumi Launcher and the Takumi website. Pure logic (no Minecraft classes), unit
 * tested; anything malformed is ignored so a bad answer can never break the game.
 *
 * <ul>
 *   <li>The launcher passes {@code TAKUMI_SKINS_TOKEN} (and {@code TAKUMI_WEB_URL}); without a valid token the mod
 *   stays inactive, so it only works when the game is started from Takumi Launcher.</li>
 *   <li>{@code POST /api/skins/lookup {players:[{uuid,name}]}} answers {@code {profiles:{<uuid>:{skin,model,cape}|null}}}
 *   with texture hashes, downloaded from {@code GET /api/skins/textures/<sha256>.png}.</li>
 * </ul>
 */
public final class SkinProtocol {
    public static final String TOKEN_ENV = "TAKUMI_SKINS_TOKEN";
    public static final String WEB_URL_ENV = "TAKUMI_WEB_URL";
    /** Same values as system properties, for tests and development runs. */
    public static final String TOKEN_PROPERTY = "takumi_skins.token";
    public static final String WEB_URL_PROPERTY = "takumi_skins.web_url";
    public static final String DEFAULT_WEB_URL = "https://takumilabs.org";
    /** The website answers at most this many players per lookup. */
    public static final int MAX_BATCH = 64;
    /** The website never stores a larger texture. */
    public static final int MAX_TEXTURE_BYTES = 256 * 1024;
    public static final int MAX_LOOKUP_RESPONSE_BYTES = 64 * 1024;

    private static final Pattern TOKEN = Pattern.compile("sktk_[A-Za-z0-9_-]{43}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MC_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private SkinProtocol() {
    }

    /** Where to ask and with which token. */
    public record Settings(String baseUrl, String token) {
        @Override
        public String toString() {
            return "Settings[" + baseUrl + "]"; // never log the token
        }
    }

    /** A player's Takumi skin: texture hashes (null keeps the player's own) and the arm model. */
    public record Profile(String skin, boolean slim, String cape) {
    }

    /**
     * Settings from the environment ({@code TAKUMI_SKINS_TOKEN}, {@code TAKUMI_WEB_URL}) or the matching system
     * properties; empty when there is no valid token or the URL is not safe.
     */
    public static Optional<Settings> settings(Function<String, String> env, Function<String, String> properties) {
        String token = firstNonBlank(env.apply(TOKEN_ENV), properties.apply(TOKEN_PROPERTY));
        if (token == null || !TOKEN.matcher(token.trim()).matches()) {
            return Optional.empty();
        }
        String url = firstNonBlank(env.apply(WEB_URL_ENV), properties.apply(WEB_URL_PROPERTY));
        url = url == null ? DEFAULT_WEB_URL : url.trim();
        if (!isSafeUrl(url)) {
            return Optional.empty();
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // "localhost" can resolve to ::1 first, while local dev servers (Vite) usually listen only on IPv4.
        url = url.replaceFirst("(?i)^(http://)localhost(?=[:/]|$)", "$1127.0.0.1");
        return Optional.of(new Settings(url, token.trim()));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    /** https, or http only for this computer (local development). */
    public static boolean isSafeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
            return "https".equals(uri.getScheme()) && !host.isEmpty() || "http".equals(uri.getScheme()) && local;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** UUID an offline-mode server gives this name ({@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)}). */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Whether this profile can belong to a Takumi user, so it is worth asking the website: premium ids (version 4), and
     * offline ids (version 3) that really are the offline id of that exact name. NPCs and fake players use other ids.
     */
    public static boolean shouldLookup(UUID id, String name) {
        if (id == null) {
            return false;
        }
        return switch (id.version()) {
            case 4 -> true;
            case 3 -> name != null && MC_NAME.matcher(name).matches() && offlineUuid(name).equals(id);
            default -> false;
        };
    }

    /** Lowercase id without dashes, as the website uses it. */
    public static String compact(UUID id) {
        return id.toString().replace("-", "");
    }

    public static String lookupBody(Map<UUID, String> players) {
        JsonArray list = new JsonArray();
        players.forEach((id, name) -> {
            JsonObject player = new JsonObject();
            player.addProperty("uuid", compact(id));
            player.addProperty("name", name == null ? "" : name);
            list.add(player);
        });
        JsonObject root = new JsonObject();
        root.add("players", list);
        return root.toString();
    }

    /**
     * Answer of a lookup: every asked id is present, mapped to its profile or to empty (no Takumi skin). Profiles with
     * invalid hashes are dropped. Throws {@link IllegalArgumentException} when the body is not a lookup answer.
     */
    public static Map<UUID, Optional<Profile>> parseLookup(String json, Collection<UUID> asked) {
        JsonObject profiles;
        try {
            JsonElement root = JsonParser.parseString(json);
            profiles = root.getAsJsonObject().getAsJsonObject("profiles");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Respuesta de skins no válida", e);
        }
        if (profiles == null) {
            throw new IllegalArgumentException("Respuesta de skins sin perfiles");
        }
        Map<UUID, Optional<Profile>> result = new HashMap<>();
        for (UUID id : asked) {
            JsonElement entry = profiles.get(compact(id));
            result.put(id, entry != null && entry.isJsonObject() ? parseProfile(entry.getAsJsonObject()) : Optional.empty());
        }
        return result;
    }

    private static Optional<Profile> parseProfile(JsonObject object) {
        String skin = sha(object.get("skin"));
        String cape = sha(object.get("cape"));
        if (skin == null && cape == null) {
            return Optional.empty();
        }
        JsonElement model = object.get("model");
        boolean slim = model != null && model.isJsonPrimitive() && "slim".equals(model.getAsString());
        return Optional.of(new Profile(skin, slim, cape));
    }

    private static String sha(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return isSha(value) ? value : null;
    }

    public static boolean isSha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** PNG signature check before handing bytes to the image decoder. */
    public static boolean looksLikePng(byte[] data) {
        return data.length > 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }
}
