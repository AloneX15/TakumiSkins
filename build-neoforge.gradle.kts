plugins {
    java
    id("net.neoforged.moddev")
}

// Shared compilation and packaging; the loader plugins supply the Minecraft classpath and remapping.
val mcVersion = sc.current.version
val modVersion = sc.properties.get<String>("mod.version")
val loader = if (project.name.endsWith("-neoforge")) "neoforge" else "forge"
val javaVersion = if (mcVersion == "1.21.1") 21 else 25
version = "$modVersion+mc$mcVersion"
group = "com.takumistudios.takumi_skins"
base.archivesName = "takumi_skins-$loader"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
    withSourcesJar()
}

sourceSets.create("client")
sourceSets.create("native")
sourceSets {
    main {
        java.srcDir(files(layout.buildDirectory.dir("generated/stonecutter/client/java")).builtBy("stonecutterGenerateClient"))
        java.srcDir(files(layout.buildDirectory.dir("generated/stonecutter/native/java")).builtBy("stonecutterGenerateNative"))
        java.exclude("**/fabric/**", "**/TakumiSkinsClient.java")
        java.exclude(if (loader == "forge") "**/neoforge/**" else "**/forge/**")
        resources.srcDir(files(layout.buildDirectory.dir("generated/stonecutter/native/resources")).builtBy("stonecutterGenerateNative"))
        resources.exclude("fabric.mod.json", "takumi_skins.mixins.json")
        resources.exclude(if (loader == "forge") "META-INF/neoforge.mods.toml" else "META-INF/mods.toml")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}
tasks.processResources {
    val packs = mapOf("1.21.1" to listOf(34, 48, 0), "26.1.2" to listOf(84, 101, 1),
        "26.2" to listOf(88, 107, 1), "26.3" to listOf(97, 121, 0)).getValue(mcVersion)
    val props = mapOf("version" to project.version, "minecraft" to mcVersion,
        "pack_resource" to packs[0], "pack_data" to packs[1], "pack_data_minor" to packs[2])
    inputs.properties(props)
    filesMatching(listOf("META-INF/*.toml", "pack.mcmeta")) { expand(props) }
}
tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_takumi_skins" } }
    manifest.attributes(
        "Implementation-Title" to "Takumi Skins",
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to "TakumiStudios",
        "MixinConfigs" to "takumi_skins.native.mixins.json",
    )
}
tasks.named<Jar>("sourcesJar") {
    exclude("META-INF/*.toml", "*.mixins.json")
}

val neoVersions = mapOf("1.21.1" to "21.1.266", "26.1.2" to "26.1.2.126", "26.2" to "26.2.0.93", "26.3" to "26.3.0.103")
neoForge {
    enable {
        version = neoVersions.getValue(mcVersion)
        isDisableRecompilation = true
    }
    runs {
        configureEach {
            if (providers.gradleProperty("skinsClientSmoke").isPresent) {
                systemProperty("takumi_skins.clientSmoke", "true")
            }
            providers.gradleProperty("skinsSmokeExpect").orNull?.let { systemProperty("takumi_skins.smokeExpect", it) }
        }
        register("server") { server(); programArgument("--nogui") }
        register("client") { client() }
    }
    mods { register("takumi_skins") { sourceSet(sourceSets.main.get()) } }
}
repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}
tasks.test { useJUnitPlatform() }

// These source sets feed Stonecutter; all their code is compiled into main with the loader classpath.
tasks.named("compileClientJava") { enabled = false }
tasks.named("compileNativeJava") { enabled = false }
