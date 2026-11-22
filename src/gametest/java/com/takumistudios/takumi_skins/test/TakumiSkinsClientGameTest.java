package com.takumistudios.takumi_skins.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.takumistudios.takumi_skins.SkinProtocol;
import com.takumistudios.takumi_skins.TakumiSkins;
import com.takumistudios.takumi_skins.client.SkinRegistry;
import com.takumistudios.takumi_skins.client.TakumiSkinsHooked;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * End to end in a real client: a local HTTP server plays the Takumi website (token check, lookup, textures), the mod
 * is pointed at it, and the local player must end up wearing the Takumi skin, cape and arm model. The screenshot in
 * versions/<v>/build/run/clientGameTest/screenshots/ shows the player from the front (red skin, blue cape).
 * Run with ./gradlew :26.3:runClientGameTest (also with -PcompatPack). With Iris in the pack the build turns on the test
 * shaderpack (compat/iris) and sets {@code takumi_skins.expectShaders}: the player must then be drawn through Iris'
 * shader pipeline, so the screenshot shows the Takumi skin with shaders.
 */
public class TakumiSkinsClientGameTest implements FabricClientGameTest {
    private static final String TOKEN = "sktk_" + "T".repeat(43);
    private static final boolean EXPECT_SHADERS = Boolean.getBoolean("takumi_skins.expectShaders");

    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean hooked = context.computeOnClient(mc -> mc.getSkinManager() instanceof TakumiSkinsHooked);
        if (!hooked) {
            throw new AssertionError("SkinManagerMixin no se ha aplicado");
        }

        byte[] skin = png(64, 64, 0xFFD02020);
        byte[] cape = png(64, 32, 0xFF2050E0);
        String skinSha = SkinProtocol.sha256(skin);
        String capeSha = SkinProtocol.sha256(cape);
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger unauthorized = new AtomicInteger();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/api/skins/lookup", exchange -> {
            if (!authorized(exchange)) {
                unauthorized.incrementAndGet();
                send(exchange, 401, "application/json", "{\"error\":\"token\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            lookups.incrementAndGet();
            var request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject profiles = new JsonObject();
            request.getAsJsonArray("players").forEach(player -> {
                JsonObject profile = new JsonObject();
                profile.addProperty("skin", skinSha);
                profile.addProperty("model", "slim");
                profile.addProperty("cape", capeSha);
                profiles.add(player.getAsJsonObject().get("uuid").getAsString(), profile);
            });
            JsonObject answer = new JsonObject();
            answer.add("profiles", profiles);
            send(exchange, 200, "application/json", answer.toString().getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/api/skins/textures/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.endsWith(skinSha + ".png") ? skin : path.endsWith(capeSha + ".png") ? cape : null;
            if (!authorized(exchange)) {
                unauthorized.incrementAndGet();
                send(exchange, 401, "text/plain", new byte[0]);
            } else if (body == null) {
                send(exchange, 404, "text/plain", new byte[0]);
            } else {
                send(exchange, 200, "image/png", body);
            }
        });
        server.start();
        try {
            SkinRegistry.configure(new SkinProtocol.Settings("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN));
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                waitForChunks(world);
                world.getServer().runCommand("time set noon");
                world.getServer().runCommand("weather clear");

                String wanted = "skin=takumi_skins:skins/" + skinSha + " cape=takumi_skins:skins/" + capeSha + " model=slim";
                String found = "";
                for (int second = 0; second < 20 && !found.equals(wanted); second++) {
                    context.waitTicks(20);
                    found = context.computeOnClient(mc -> mc.player == null ? "" : describe(mc.player.getSkin()));
                }
                TakumiSkins.LOGGER.info("E2E skins: {} (lookups={}, 401={})", found, lookups.get(), unauthorized.get());
                if (!found.equals(wanted)) {
                    throw new AssertionError("El jugador no lleva la skin de Takumi. Esperado «" + wanted + "», hay «" + found + "»");
                }
                if (unauthorized.get() > 0) {
                    throw new AssertionError("El mod hizo peticiones sin el token del launcher");
                }

                if (EXPECT_SHADERS && !context.computeOnClient(mc -> irisShadersInUse())) {
                    throw new AssertionError("Iris no tiene el shaderpack de prueba en uso");
                }

                context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                context.waitTicks(20);
                context.takeScreenshot(EXPECT_SHADERS ? "takumi_skins_player_iris" : "takumi_skins_player");
                context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            }
        } finally {
            server.stop(0);
        }
    }

    /** Iris' public API, by reflection so the test compiles without Iris (it is only on the compat pack runtime). */
    private static boolean irisShadersInUse() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            return (boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("No se pudo consultar Iris", e);
        }
    }

    private static String describe(PlayerSkin skin) {
        return "skin=" + skin.body().texturePath() + " cape=" + (skin.cape() == null ? null : skin.cape().texturePath())
                + " model=" + skin.model().getSerializedName();
    }

    private static boolean authorized(HttpExchange exchange) {
        return ("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("authorization"));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("content-type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static byte[] png(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
