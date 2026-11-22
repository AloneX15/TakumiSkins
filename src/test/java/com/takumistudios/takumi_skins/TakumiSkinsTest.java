package com.takumistudios.takumi_skins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests unitarios de la lógica pura (sin Minecraft). Todo bug corregido añade su caso aquí o en los gametests. */
class TakumiSkinsTest {
    private static final String TOKEN = "sktk_" + "A".repeat(43);
    private static final String SHA = "4560efdd50bf8789b9833880ccdc3be3c1a1d9dc02c41dd23a63cc6d095322d0";

    private static Map<String, String> env(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void modIdIsValid() {
        assertTrue(TakumiSkins.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
    }

    @Test
    void inactiveWithoutAValidLauncherToken() {
        Map<String, String> none = env();
        assertTrue(SkinProtocol.settings(none::get, none::get).isEmpty());
        Map<String, String> bad = env(SkinProtocol.TOKEN_ENV, "tkt_notaskinstoken");
        assertTrue(SkinProtocol.settings(bad::get, none::get).isEmpty());
        Map<String, String> shortToken = env(SkinProtocol.TOKEN_ENV, "sktk_abc");
        assertTrue(SkinProtocol.settings(shortToken::get, none::get).isEmpty());
    }

    @Test
    void settingsUseTheLauncherUrlOrTheDefault() {
        Map<String, String> none = env();
        Map<String, String> launcher = env(SkinProtocol.TOKEN_ENV, " " + TOKEN + "\n");
        var defaults = SkinProtocol.settings(launcher::get, none::get).orElseThrow();
        assertEquals(SkinProtocol.DEFAULT_WEB_URL, defaults.baseUrl());
        assertEquals(TOKEN, defaults.token());
        assertFalse(defaults.toString().contains(TOKEN), "the token must never be logged");

        Map<String, String> local = env(SkinProtocol.TOKEN_ENV, TOKEN, SkinProtocol.WEB_URL_ENV, "http://localhost:5173//");
        assertEquals("http://127.0.0.1:5173", SkinProtocol.settings(local::get, none::get).orElseThrow().baseUrl());

        Map<String, String> insecure = env(SkinProtocol.TOKEN_ENV, TOKEN, SkinProtocol.WEB_URL_ENV, "http://evil.example");
        assertTrue(SkinProtocol.settings(insecure::get, none::get).isEmpty());

        Map<String, String> properties = env(SkinProtocol.TOKEN_PROPERTY, TOKEN);
        assertTrue(SkinProtocol.settings(none::get, properties::get).isPresent());
    }

    @Test
    void offlineIdsMatchVanilla() {
        // UUID.nameUUIDFromBytes("OfflinePlayer:Notch"), same value as the launcher and the website tests.
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", SkinProtocol.compact(SkinProtocol.offlineUuid("Notch")));
    }

    @Test
    void onlyPlausibleTakumiProfilesAreLookedUp() {
        UUID offline = SkinProtocol.offlineUuid("Notch");
        assertTrue(SkinProtocol.shouldLookup(offline, "Notch"));
        assertFalse(SkinProtocol.shouldLookup(offline, "Steve"), "offline id of another name");
        assertFalse(SkinProtocol.shouldLookup(offline, null));
        assertTrue(SkinProtocol.shouldLookup(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch"));
        assertFalse(SkinProtocol.shouldLookup(UUID.fromString("069a79f4-44e9-2726-a5be-fca90e38aaf5"), "Npc"),
                "NPC ids (version 2) are never sent");
        assertFalse(SkinProtocol.shouldLookup(null, "Notch"));
    }

    @Test
    void lookupBodyUsesCompactIds() {
        UUID id = SkinProtocol.offlineUuid("Notch");
        Map<UUID, String> players = new LinkedHashMap<>();
        players.put(id, "Notch");
        var root = JsonParser.parseString(SkinProtocol.lookupBody(players)).getAsJsonObject();
        var player = root.getAsJsonArray("players").get(0).getAsJsonObject();
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", player.get("uuid").getAsString());
        assertEquals("Notch", player.get("name").getAsString());
    }

    @Test
    void lookupAnswersAreParsedDefensively() {
        UUID slim = SkinProtocol.offlineUuid("Alex");
        UUID capeOnly = SkinProtocol.offlineUuid("Cape");
        UUID none = SkinProtocol.offlineUuid("Nadie");
        UUID missing = SkinProtocol.offlineUuid("Missing");
        UUID broken = SkinProtocol.offlineUuid("Broken");
        String json = """
                {"profiles":{
                  "%s":{"skin":"%s","model":"slim","cape":null},
                  "%s":{"skin":null,"model":"classic","cape":"%s"},
                  "%s":null,
                  "%s":{"skin":"../../etc/passwd","cape":42}
                }}""".formatted(SkinProtocol.compact(slim), SHA, SkinProtocol.compact(capeOnly), SHA,
                SkinProtocol.compact(none), SkinProtocol.compact(broken));
        var result = SkinProtocol.parseLookup(json, List.of(slim, capeOnly, none, missing, broken));
        assertEquals(Optional.of(new SkinProtocol.Profile(SHA, true, null)), result.get(slim));
        assertEquals(Optional.of(new SkinProtocol.Profile(null, false, SHA)), result.get(capeOnly));
        assertEquals(Optional.empty(), result.get(none));
        assertEquals(Optional.empty(), result.get(missing));
        assertEquals(Optional.empty(), result.get(broken), "invalid hashes are dropped");
        assertThrows(IllegalArgumentException.class, () -> SkinProtocol.parseLookup("<html>", List.of(slim)));
        assertThrows(IllegalArgumentException.class, () -> SkinProtocol.parseLookup("{}", List.of(slim)));
    }

    @Test
    void texturesAreCheckedByHash() {
        byte[] data = "hola".getBytes(StandardCharsets.UTF_8);
        assertEquals("b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79", SkinProtocol.sha256(data));
        assertTrue(SkinProtocol.isSha(SHA));
        assertFalse(SkinProtocol.isSha(SHA.toUpperCase()));
        assertFalse(SkinProtocol.isSha(null));
        assertFalse(SkinProtocol.looksLikePng(data));
        assertTrue(SkinProtocol.looksLikePng(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0}));
    }
}
