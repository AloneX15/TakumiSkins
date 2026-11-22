plugins {
    id("net.fabricmc.fabric-loom")
}

val modId = sc.properties.get<String>("mod.id")
val modName = sc.properties.get<String>("mod.name")
val modVersion = sc.properties.get<String>("mod.version")
val mcVersion = sc.current.version
val javaVersion = sc.properties.get<String>("deps.java").toInt()

// Pack de compatibilidad (-PcompatPack): con Iris, la Sodium que Iris admite (compat.sodium_iris) y shaders activados;
// -PcompatPack=noiris: la última Sodium sola
val compatPack = providers.gradleProperty("compatPack").orNull
val packIris = sc.properties.get<String>("compat.iris").takeIf { compatPack != null && compatPack != "noiris" && it.isNotBlank() }
val packSodium = packIris?.let { sc.properties.get<String>("compat.sodium_iris").takeIf(String::isNotBlank) }
    ?: sc.properties.get<String>("compat.sodium")

// Nombre del jar: <modid>-<versión del mod>+mc<versión>.jar
version = "$modVersion+mc$mcVersion"
group = sc.properties.get<String>("mod.group")
base.archivesName = "$modId-fabric"

repositories {
    // Cada grupo se busca solo en su repositorio
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDirectory = rootProject.file(providers.gradleProperty("skinsRunDir").getOrElse("run"))
        generateRunConfig = true
        // ./gradlew :<v>:runClient -PskinsClientSmoke: comprueba los mixins al arrancar y cierra el juego (CI)
        if (providers.gradleProperty("skinsClientSmoke").isPresent) {
            vmArg("-Dtakumi_skins.clientSmoke=true")
        }
        providers.gradleProperty("skinsSmokeExpect").orNull?.let { vmArg("-Dtakumi_skins.smokeExpect=$it") }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true          // src/gametest/java + src/gametest/resources
        modId = "$modId-test"
        enableGameTests = false         // Mod solo de cliente: no hay gametests de servidor
        enableClientGameTests = true    // runClientGameTest
        eula = true                     // imprescindible: sin esto el servidor de la CI no arranca
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    // Fabric API solo para los client gametests: el jar no depende de ella (el launcher inyecta el mod en instancias
    // ajenas y no debe forzar una versión). El job standards de la CI comprueba que src/main y src/client no la usan.
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Integraciones opcionales: compileOnly aquí + FabricLoader.isModLoaded(...) en ejecución
    // compileOnly("maven.modrinth:<slug>:${sc.properties.get<String>("compat.<slug>")}")

    // Modpack de compatibilidad (va en la CI): ./gradlew :26.3:runClientGameTest -PcompatPack
    if (compatPack != null) {
        localRuntime("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
        localRuntime("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
        // Render, shaders, modelos/texturas de entidades y otro mod de skins: lo que más toca a los jugadores
        localRuntime("maven.modrinth:sodium:$packSodium")
        packIris?.let { localRuntime("maven.modrinth:iris:$it") }
        localRuntime("maven.modrinth:entity-model-features:${sc.properties.get<String>("compat.emf")}")
        localRuntime("maven.modrinth:entitytexturefeatures:${sc.properties.get<String>("compat.etf")}")
        sc.properties.get<String>("compat.customskinloader").takeIf { it.isNotBlank() }?.let { localRuntime("maven.modrinth:customskinloader:$it") }
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Con Iris en el pack, el test de cliente juega con el shaderpack de prueba (compat/iris) y exige que esté en uso
if (packIris != null) {
    tasks.withType<JavaExec>().matching { it.name == "runClientGameTest" }.configureEach {
        val irisFiles = rootProject.file("compat/iris")
        // La carpeta del test (Loom la vacía antes con deleteGameTestRunDir)
        val runDir = layout.buildDirectory.dir("run/clientGameTest")
        systemProperty("takumi_skins.expectShaders", "true")
        doFirst { project.copy { from(irisFiles); into(runDir) } }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = javaVersion
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    test { useJUnitPlatform() }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("mod.min_loader"),
            "fabric_api" to sc.properties.get<String>("mod.min_fabric_api"),
            "java" to javaVersion,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
        manifest.attributes(
            "Implementation-Title" to modName,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "TakumiStudios",
        )
    }

    // Sin fabric.mod.json el jar de código fuente no es un mod: si alguien lo pone en mods/ por error, Fabric Loader lo
    // ignora en vez de fallar al arrancar con "Invalid mod id ${id}" (las propiedades solo se sustituyen en el jar normal)
    named<Jar>("sourcesJar") {
        exclude("fabric.mod.json")
    }
}
