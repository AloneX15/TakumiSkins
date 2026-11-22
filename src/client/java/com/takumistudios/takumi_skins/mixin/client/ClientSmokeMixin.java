package com.takumistudios.takumi_skins.mixin.client;

import com.takumistudios.takumi_skins.client.ClientSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the opt-in CI startup check on every loader (also the Fabric versions without client gametests); does nothing in normal games. */
@Mixin(Minecraft.class)
public abstract class ClientSmokeMixin {
    @Inject(method = {"tick", "m_91398_"}, at = @At("TAIL"), require = 0)
    private void takumi_skins$smoke(CallbackInfo ci) {
        ClientSmoke.tick();
    }
}
