import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.ImageIO

plugins {
    id("net.fabricmc.fabric-loom-remap")
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
// Jars del pack para prodRunClient, sin dependencias transitivas (cada mod lleva dentro lo que necesita)
val prodPack: Configuration = configurations.create("prodPack") { isTransitive = false }

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

// 1.21.1 no tiene la API de client gametests y el mod no tiene nada de servidor: sin gametests en este nodo.

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")

    // Integraciones opcionales: compileOnly aquí + FabricLoader.isModLoaded(...) en ejecución
    // compileOnly("maven.modrinth:<slug>:${sc.properties.get<String>("compat.<slug>")}")

    // Pack de compatibilidad en el entorno de desarrollo (nombres de Mojang). Iris y CustomSkinLoader no arrancan aquí
    // (sus mixins y reglas dependen de nombres intermediary, también sin Takumi Skins): van solo en prodRunClient.
    if (compatPack != null) {
        // Los mods del pack necesitan Fabric API; Takumi Skins no (solo se añade al runtime de desarrollo)
        modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
        modLocalRuntime("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
        modLocalRuntime("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
        modLocalRuntime("maven.modrinth:sodium:${sc.properties.get<String>("compat.sodium")}")
        modLocalRuntime("maven.modrinth:entity-model-features:${sc.properties.get<String>("compat.emf")}")
        modLocalRuntime("maven.modrinth:entitytexturefeatures:${sc.properties.get<String>("compat.etf")}")
    }

    // Pack completo para prodRunClient: los jars tal cual se publican, como en una instancia real
    prodPack("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
    prodPack("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
    prodPack("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
    prodPack("maven.modrinth:sodium:$packSodium")
    packIris?.let { prodPack("maven.modrinth:iris:$it") }
    prodPack("maven.modrinth:entity-model-features:${sc.properties.get<String>("compat.emf")}")
    prodPack("maven.modrinth:entitytexturefeatures:${sc.properties.get<String>("compat.etf")}")
    sc.properties.get<String>("compat.customskinloader").takeIf(String::isNotBlank)?.let { prodPack("maven.modrinth:customskinloader:$it") }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Web de Takumi simulada para prodRunClient: exige el token y a cualquier jugador le da skin roja (brazos finos) y capa
// azul, como el test de cliente de 26.x. Vive lo que dura la build.
abstract class FakeTakumiWeb : BuildService<BuildServiceParameters.None>, AutoCloseable {
    companion object {
        const val TOKEN = "sktk_TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT"
    }

    private fun png(width: Int, height: Int, argb: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, argb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val skin = png(64, 64, 0xFFD02020.toInt())
    private val cape = png(64, 32, 0xFF2050E0.toInt())
    private val textures: Map<String, ByteArray> = mapOf(sha256(skin) to skin, sha256(cape) to cape)
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val url: String

    init {
        fun send(exchange: HttpExchange, status: Int, type: String, body: ByteArray) {
            exchange.responseHeaders.set("content-type", type)
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.write(body)
            exchange.close()
        }
        fun authorized(exchange: HttpExchange) =
            exchange.requestHeaders.getFirst("authorization") == "Bearer $TOKEN"
        server.createContext("/api/skins/lookup") { exchange ->
            if (!authorized(exchange)) return@createContext send(exchange, 401, "application/json", "{}".toByteArray())
            val request = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val profiles = Regex(""""uuid"\s*:\s*"([0-9a-fA-F-]{32,36})"""").findAll(request).joinToString(",") {
                "\"${it.groupValues[1]}\":{\"skin\":\"${sha256(skin)}\",\"model\":\"slim\",\"cape\":\"${sha256(cape)}\"}"
            }
            send(exchange, 200, "application/json", "{\"profiles\":{$profiles}}".toByteArray())
        }
        server.createContext("/api/skins/textures/") { exchange ->
            val body = textures[exchange.requestURI.path.substringAfterLast('/').removeSuffix(".png")]
            when {
                !authorized(exchange) -> send(exchange, 401, "text/plain", ByteArray(0))
                body == null -> send(exchange, 404, "text/plain", ByteArray(0))
                else -> send(exchange, 200, "image/png", body)
            }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}"
    }

    override fun close() = server.stop(0)
}

val fakeTakumiWeb = gradle.sharedServices.registerIfAbsent("fakeTakumiWeb-${project.path}", FakeTakumiWeb::class) {}

// 1.21.1 como en una instancia de Takumi Launcher: Minecraft ofuscado, Fabric Loader y los jars publicados (el de Takumi
// Skins remapeado a intermediary), contra una web de Takumi simulada. El smoke (ClientSmoke) comprueba los mixins, crea
// un mundo plano, espera a que el jugador lleve la skin, la capa y los brazos de Takumi, hace una captura y cierra.
// Resultado en versions/1.21.1/build/run/prod/takumi-skins-smoke.json y screenshots/. Con -PcompatPack lleva el pack
// completo (Iris y CustomSkinLoader incluidos); con Iris, el shaderpack de prueba (compat/iris) activado y el smoke
// exige que el mundo se dibuje con shaders.
//   ./gradlew :1.21.1:prodRunClient -PcompatPack
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("prodRunClient") {
    group = "takumi"
    description = "Arranca 1.21.1 en modo producción y ejecuta el smoke de Takumi Skins"
    mods.from(tasks.named("remapJar"))
    if (compatPack != null) {
        mods.from(prodPack)
    }
    val dir = layout.buildDirectory.dir("run/prod")
    runDir = dir
    // La CI ya ejecuta Gradle dentro de xvfb-run
    useXVFB = false
    usesService(fakeTakumiWeb)
    jvmArgs.add("-Dtakumi_skins.clientSmoke=true")
    jvmArgs.add("-Dtakumi_skins.smokeWorld=true")
    jvmArgs.add("-Dtakumi_skins.token=${FakeTakumiWeb.TOKEN}")
    jvmArgs.add(fakeTakumiWeb.map { "-Dtakumi_skins.web_url=${it.url}" })
    // Cuenta no premium, como la lanza Takumi Launcher: nombre y su UUID offline
    val player = "TakumiSmoke"
    programArgs.addAll("--username", player, "--uuid",
        UUID.nameUUIDFromBytes("OfflinePlayer:$player".toByteArray()).toString())
    val irisFiles = rootProject.file("compat/iris").takeIf { packIris != null }
    if (irisFiles != null) {
        jvmArgs.add("-Dtakumi_skins.expectShaders=true")
    }
    doFirst {
        val target = dir.get().asFile
        target.resolve("takumi-skins-smoke.json").delete()
        target.resolve("mods").deleteRecursively()
        target.resolve("saves").deleteRecursively()
        target.resolve("screenshots").deleteRecursively()
        irisFiles?.let { project.copy { from(it); into(target) } }
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
