package com.takumistudios.takumi_skins.mixin.client;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_skins.client.SkinOverlay;
import com.takumistudios.takumi_skins.client.TakumiSkinsHooked;
import java.util.function.Supplier;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.1 {
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * Skins resolved outside {@code PlayerInfo}: player heads, the skin widgets and mods that ask the skin manager. Only
 * the returned value is wrapped (no redirects or overwrites); {@code require = 0} keeps the game working if a target
 * changes.
 */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin implements TakumiSkinsHooked {
    //? if >=26.1 {
    @Inject(method = "createLookup", at = @At("RETURN"), cancellable = true, require = 0)
    private void takumi_skins$wrapLookup(GameProfile profile, boolean requireSecure,
                                         CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        cir.setReturnValue(SkinOverlay.wrap(profile, cir.getReturnValue()));
    }

    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private void takumi_skins$wrapGet(GameProfile profile,
                                      CallbackInfoReturnable<CompletableFuture<Optional<PlayerSkin>>> cir) {
        cir.setReturnValue(SkinOverlay.wrapFuture(profile, cir.getReturnValue()));
    }
    //?} else {
    /*@Inject(method = {"lookupInsecure", "m_293884_"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void takumi_skins$wrapLookup(GameProfile profile, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        cir.setReturnValue(SkinOverlay.wrap(profile, cir.getReturnValue()));
    }

    @Inject(method = {"getInsecureSkin", "m_293307_"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void takumi_skins$wrapSkin(GameProfile profile, CallbackInfoReturnable<PlayerSkin> cir) {
        cir.setReturnValue(SkinOverlay.apply(profile, cir.getReturnValue()));
    }
    *///?}
}
