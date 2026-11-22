package com.takumistudios.takumi_skins.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.takumistudios.takumi_skins.TakumiSkins;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Takumi skin textures in Minecraft's texture manager, one per hash: {@code takumi_skins:skins/<sha256>}. Each texture
 * is shared by every player wearing it. Registering and releasing must happen on the render thread.
 */
public final class SkinTextures {
    private SkinTextures() {
    }

    //? if >=26.1 {
    public static Identifier id(String sha) {
        return Identifier.fromNamespaceAndPath(TakumiSkins.MOD_ID, "skins/" + sha);
    }
    //?} else {
    /*public static ResourceLocation id(String sha) {
        return ResourceLocation.fromNamespaceAndPath(TakumiSkins.MOD_ID, "skins/" + sha);
    }
    *///?}

    /** Decodes a PNG off the render thread (no GPU work). */
    public static NativeImage decode(byte[] png) throws IOException {
        return NativeImage.read(png);
    }

    /** Uploads the image and registers it under {@link #id}; takes ownership of the image. */
    public static void register(String sha, NativeImage image) {
        //? if >=26.1 {
        DynamicTexture texture = new DynamicTexture(() -> "Takumi Skins " + sha, image);
        //?} else {
        /*DynamicTexture texture = new DynamicTexture(image);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id(sha), texture);
    }

    public static void release(String sha) {
        try {
            Minecraft.getInstance().getTextureManager().release(id(sha));
        } catch (RuntimeException e) {
            TakumiSkins.LOGGER.debug("No se pudo liberar la textura {}", sha, e);
        }
    }
}
