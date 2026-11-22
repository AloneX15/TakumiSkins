package com.takumistudios.takumi_skins.forge;

import com.takumistudios.takumi_skins.TakumiSkins;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Entry point; the skin hooks are client mixins, so on a dedicated server this mod does nothing. */
@Mod(TakumiSkins.MOD_ID)
public final class ForgeSkins {
    public ForgeSkins() {
        String version = ForgeSkins.class.getPackage().getImplementationVersion();
        TakumiSkins.initialize(FMLPaths.GAMEDIR.get(), version == null ? "dev" : version);
    }
}
