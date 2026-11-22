package com.takumistudios.takumi_skins.mixin.client;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_skins.client.SkinOverlay;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.1 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * The skin of every player in the world and the tab list, the local player included: the lookup {@code PlayerInfo}
 * keeps and calls each frame gets the Takumi skin on top. {@code require = 0}: if another mod changes this method,
 * the game starts normally and players keep their usual skins.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "createSkinLookup", at = @At("RETURN"), cancellable = true, require = 0)
    private static void takumi_skins$wrapLookup(GameProfile profile, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        cir.setReturnValue(SkinOverlay.wrap(profile, cir.getReturnValue()));
    }
}
