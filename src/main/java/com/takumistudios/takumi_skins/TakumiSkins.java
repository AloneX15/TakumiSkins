package com.takumistudios.takumi_skins;

import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared state of Takumi Skins, filled in by each loader's entry point ({@code fabric/FabricSkins},
 * {@code forge/ForgeSkins}, {@code neoforge/NeoForgeSkins}). No loader classes here, so the same code runs on all of
 * them. Creado por TakumiStudios.
 */
public final class TakumiSkins {
    public static final String MOD_ID = "takumi_skins";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Path gameDir;
    private static String version = "desconocida";

    private TakumiSkins() {
    }

    public static void initialize(Path game, String modVersion) {
        gameDir = Objects.requireNonNull(game).toAbsolutePath().normalize();
        version = Objects.requireNonNull(modVersion);
        LOGGER.info("Takumi Skins {} cargado. Creado por TakumiStudios.", version);
    }

    /** Game folder, or the working directory when a loader did not call {@link #initialize}. */
    public static Path gameDir() {
        return gameDir != null ? gameDir : Path.of("").toAbsolutePath();
    }

    public static String version() {
        return version;
    }
}
