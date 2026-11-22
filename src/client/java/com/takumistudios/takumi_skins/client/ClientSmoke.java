package com.takumistudios.takumi_skins.client;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_skins.SkinProtocol;
import com.takumistudios.takumi_skins.TakumiSkins;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.network.chat.Component;

/**
 * Opt-in startup check for every loader ({@code -Dtakumi_skins.clientSmoke=true}), used by the CI where there are no
 * client gametests (Fabric 1.21.1, Forge, NeoForge): the skin mixins are applied, a lookup goes through them and still
 * returns a skin, messages read as text. With {@code -Dtakumi_skins.smokeExpect=<name>} and a token it also waits up to
 * 30 s for that offline player to get their Takumi skin from the website (the whole download and texture path).
 * With {@code -Dtakumi_skins.smokeWorld=true} and a token (1.21.1 production run against a simulated website) it also
 * creates a flat world, waits for the local player to wear their Takumi skin, looks at them from the front and saves
 * {@code screenshots/takumi_skins_player.png}. With {@code -Dtakumi_skins.expectShaders=true} (compat pack with Iris)
 * Iris must then be drawing that world with a shaderpack.
 * Writes {@code takumi-skins-smoke.json} in the game folder and closes the game.
 */
public final class ClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("takumi_skins.clientSmoke");
    private static final String EXPECT = System.getProperty("takumi_skins.smokeExpect", "");
    private static final boolean EXPECT_SHADERS = Boolean.getBoolean("takumi_skins.expectShaders");
    private static final boolean WORLD = Boolean.getBoolean("takumi_skins.smokeWorld");
    private static final long WAIT_MS = 30_000;
    private static final long WORLD_WAIT_MS = 120_000;
    /** Ticks in the world with the Takumi skin before the screenshot, so textures and shaders have settled. */
    private static final int SETTLE_TICKS = 60;
    private static boolean checked;
    private static boolean finished;
    private static Supplier<?> expected;
    private static long deadline;
    private static boolean inWorld;
    private static int settled;

    private ClientSmoke() {
    }

    public static void tick() {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!checked) {
            //? if >=26.2 {
            boolean loading = client.gui.overlay() != null;
            //?} else {
            /*boolean loading = client.getOverlay() != null;
            *///?}
            if (!loading) {
                checked = true;
                startupChecks(client);
            }
            return;
        }
        if (inWorld) {
            worldTick(client);
            return;
        }
        if (expected != null) {
            String found = describe(expected.get());
            if (found.contains(TakumiSkins.MOD_ID + ":skins/")) {
                finish(client, true, "active=true " + found);
            } else if (System.currentTimeMillis() > deadline) {
                finish(client, false, "La skin de Takumi no llegó a " + EXPECT + ": " + found);
            }
        }
    }

    private static void startupChecks(Minecraft client) {
        try {
            SkinManager skins = client.getSkinManager();
            if (!(skins instanceof TakumiSkinsHooked)) {
                throw new IllegalStateException("SkinManagerMixin no se ha aplicado");
            }
            String name = EXPECT.isBlank() ? "TakumiSmoke" : EXPECT;
            GameProfile profile = new GameProfile(SkinProtocol.offlineUuid(name), name);
            long before = SkinOverlay.hookCalls();
            //? if >=26.1 {
            Supplier<?> lookup = skins.createLookup(profile, false);
            //?} else {
            /*Supplier<?> lookup = skins.lookupInsecure(profile);
            *///?}
            if (SkinOverlay.hookCalls() == before) {
                throw new IllegalStateException("El hook de skins no se ha ejecutado");
            }
            if (lookup.get() == null) {
                throw new IllegalStateException("La skin vanilla se ha perdido");
            }
            // Language files may be missing (Fabric without Fabric API): messages must still read as text.
            String key = "takumi_skins.command.reloaded";
            if (Component.translatableWithFallback(key, "fallback").getString().equals(key)) {
                throw new IllegalStateException("Los mensajes de Takumi Skins se ven como claves");
            }
            if (WORLD) {
                if (!SkinRegistry.active()) {
                    finish(client, false, "Falta el token de Takumi Launcher para el mundo de prueba");
                    return;
                }
                inWorld = true;
                deadline = System.currentTimeMillis() + WORLD_WAIT_MS;
                openWorld(client);
                return;
            }
            if (EXPECT.isBlank()) {
                finish(client, true, "active=" + SkinRegistry.active());
            } else if (!SkinRegistry.active()) {
                finish(client, false, "Falta el token de Takumi Launcher para comprobar la skin de " + EXPECT);
            } else {
                expected = lookup;
                deadline = System.currentTimeMillis() + WAIT_MS;
            }
        } catch (RuntimeException | LinkageError error) {
            TakumiSkins.LOGGER.error("Client startup check failed", error);
            finish(client, false, String.valueOf(error.getMessage()));
        }
    }

    private static void worldTick(Minecraft client) {
        try {
            String found = client.player == null ? "sin jugador" : describe(client.player.getSkin());
            if (System.currentTimeMillis() > deadline) {
                finish(client, false, "El jugador no llegó a llevar la skin de Takumi en el mundo: " + found);
                return;
            }
            // Loading screens are screens: the world is on screen once there is none
            if (client.player == null || client.level == null || screenOpen(client)
                    || !found.contains(TakumiSkins.MOD_ID + ":skins/")) {
                settled = 0;
                return;
            }
            if (settled++ == 0) {
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }
            if (settled < SETTLE_TICKS) {
                return;
            }
            if (EXPECT_SHADERS && !irisShadersInUse()) {
                finish(client, false, "Iris no está dibujando el mundo con shaders");
                return;
            }
            screenshot(client, EXPECT_SHADERS ? "takumi_skins_player_iris.png" : "takumi_skins_player.png");
            finish(client, true, "world=true " + found + (EXPECT_SHADERS ? " shaders=true" : ""));
        } catch (RuntimeException | LinkageError error) {
            TakumiSkins.LOGGER.error("Client world check failed", error);
            finish(client, false, String.valueOf(error.getMessage()));
        }
    }

    private static boolean screenOpen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen() != null;
        //?} else {
        /*return client.screen != null;
        *///?}
    }

    /** A new flat creative world; only where there are no client gametests (1.21.1 and its Forge/NeoForge jars). */
    private static void openWorld(Minecraft client) {
        //? if <26.1 {
        /*var settings = new net.minecraft.world.level.LevelSettings("Takumi Skins smoke",
                net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("takumi-skins-smoke-" + System.currentTimeMillis(), settings,
                new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                client.screen);
        *///?} else {
        throw new IllegalStateException("En 26.x el mundo de prueba es runClientGameTest");
        //?}
    }

    private static void screenshot(Minecraft client, String name) {
        //? if <26.1 {
        /*net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {
        });
        *///?}
    }

    /** Iris' public API by reflection: the mod never depends on Iris. */
    private static boolean irisShadersInUse() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            return (boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo consultar Iris: " + e);
        }
    }

    private static String describe(Object skin) {
        //? if >=26.1 {
        if (skin instanceof net.minecraft.world.entity.player.PlayerSkin playerSkin) {
            return "skin=" + playerSkin.body().texturePath() + " cape="
                    + (playerSkin.cape() == null ? null : playerSkin.cape().texturePath()) + " model=" + playerSkin.model();
        }
        //?} else {
        /*if (skin instanceof net.minecraft.client.resources.PlayerSkin playerSkin) {
            return "skin=" + playerSkin.texture() + " cape=" + playerSkin.capeTexture() + " model=" + playerSkin.model()
                    + " secure=" + playerSkin.secure();
        }
        *///?}
        return String.valueOf(skin);
    }

    private static void finish(Minecraft client, boolean success, String detail) {
        finished = true;
        String message = detail.replace("\\", "/").replace("\"", "'");
        TakumiSkins.LOGGER.info("Takumi Skins smoke: success={} {}", success, message);
        Thread writer = new Thread(() -> {
            try {
                var target = TakumiSkins.gameDir().resolve("takumi-skins-smoke.json");
                var temporary = target.resolveSibling("takumi-skins-smoke.json.tmp");
                Files.writeString(temporary, "{\"success\":" + success + ",\"detail\":\"" + message + "\"}\n");
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException error) {
                TakumiSkins.LOGGER.error("Could not write client smoke result", error);
            } finally {
                client.execute(client::stop);
            }
        }, "Takumi Skins-ClientSmoke");
        writer.setDaemon(true);
        writer.start();
    }
}
