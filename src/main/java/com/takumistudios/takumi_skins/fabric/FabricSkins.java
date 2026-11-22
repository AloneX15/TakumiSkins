package com.takumistudios.takumi_skins.fabric;

import com.takumistudios.takumi_skins.TakumiSkins;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric and Quilt entry point. Only Fabric Loader is used, not Fabric API: Takumi Launcher adds this mod to instances
 * it does not manage and must not force a Fabric API version on them. Everything else is done with mixins.
 */
public final class FabricSkins implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        TakumiSkins.initialize(loader.getGameDir(), loader.getModContainer(TakumiSkins.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("desconocida"));
    }
}
