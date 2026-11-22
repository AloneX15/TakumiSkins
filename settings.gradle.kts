pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForge" }
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.19.4"
        id("net.fabricmc.fabric-loom-remap") version "1.19.4"
        id("net.minecraftforge.gradle") version "7.0.40"
        id("net.neoforged.moddev") version "2.0.148"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Una build por versión de Minecraft soportada (26.x en adelante)
        versions("26.1.2", "26.2", "26.3")
        version("1.21.1").buildscript("build-fabric-legacy.gradle.kts")
        for (mc in listOf("1.21.1", "26.1.2", "26.2", "26.3")) {
            version("$mc-forge", mc).buildscript("build-forge.gradle.kts")
            version("$mc-neoforge", mc).buildscript("build-neoforge.gradle.kts")
        }
        vcsVersion = "26.3"
    }
}

rootProject.name = "takumi_skins"
