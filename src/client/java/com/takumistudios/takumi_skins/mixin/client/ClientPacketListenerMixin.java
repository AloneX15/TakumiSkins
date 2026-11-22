package com.takumistudios.takumi_skins.mixin.client;

import com.takumistudios.takumi_skins.client.SkinCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /takumiskins recargar}, handled on the client without Fabric API or loader command events: only that exact
 * command is taken; every other command goes to the server as usual.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void takumi_skins$command(String command, CallbackInfo ci) {
        if (SkinCommands.handle(command)) {
            ci.cancel();
        }
    }
}
