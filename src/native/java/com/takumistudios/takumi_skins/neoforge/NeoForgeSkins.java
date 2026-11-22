package com.takumistudios.takumi_skins.neoforge;

import com.takumistudios.takumi_skins.TakumiSkins;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/** Entry point; the skin hooks are client mixins, so on a dedicated server this mod does nothing. */
@Mod(TakumiSkins.MOD_ID)
public final class NeoForgeSkins {
    public NeoForgeSkins() {
        String version = NeoForgeSkins.class.getPackage().getImplementationVersion();
        TakumiSkins.initialize(FMLPaths.GAMEDIR.get(), version == null ? "dev" : version);
    }
}
