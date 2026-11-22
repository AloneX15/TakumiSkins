# Takumi Skins

[![Build](https://github.com/AloneX15/TakumiSkins/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/TakumiSkins/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/TakumiSkins?sort=semver)](https://github.com/AloneX15/TakumiSkins/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%C2%B7%2026.x-62b47a)
![Loader](https://img.shields.io/badge/loaders-Fabric%20%7C%20Quilt%20%7C%20Forge%20%7C%20NeoForge-dbd0b4)

Shows the skins and capes Takumi Launcher players choose on the Takumi website or launcher, in any world or server.

Mod de cliente para [Takumi Launcher](https://github.com/AloneX15/TakumiLauncher), al estilo de TL Skins/Capes: cada
jugador sube su skin y su capa en la web de Takumi (*Mi cuenta → Skin y capa*) o en el launcher (*Skins*), y todos los
que juegan desde Takumi Launcher las ven en cualquier mundo o servidor, premium o no premium.

## Características

- **Skin, capa y brazos (clásicos o finos)** de cada jugador de Takumi, también en la lista de jugadores, las cabezas
  de jugador y la élitra.
- **Solo con Takumi Launcher:** el launcher instala el mod y le pasa un token temporal de tu sesión. Sin él el mod no
  hace nada y la web no responde.
- **No rompe el juego:** si algo falla (web caída, otro mod cambia el código de skins, imagen dañada) se ven las skins
  normales. No toca el renderizado, solo qué textura lleva cada jugador.
- **Ligero:** consultas en lotes de hasta 64 jugadores, respuestas guardadas 5 minutos, texturas descargadas una vez,
  comprobadas por SHA-256 y guardadas en `.takumi/skins/`. Las que no se usan en 30 días se borran solas (el mod al
  arrancar y Takumi Launcher antes de lanzar).
- **Al momento:** al cambiar la skin en el launcher se recarga sola en el juego; `/takumiskins recargar` vuelve a pedir
  las de todos.
- No necesita Fabric API ni nada en el servidor.

## Instalación

Nada: Takumi Launcher lo instala solo en las instancias con Fabric, Quilt, Forge o NeoForge de las versiones
soportadas. Si lo pones a mano en otro launcher, no hace nada (falta el token de Takumi Launcher).

Jars en [Releases](https://github.com/AloneX15/TakumiSkins/releases), uno por cargador y versión:
`takumi_skins-fabric-…` (Fabric y Quilt), `takumi_skins-forge-…`, `takumi_skins-neoforge-…`. Cada release incluye
`takumi-skins.json`, el índice que usa el launcher para elegir el jar.

## Compatibilidad

| Minecraft | Java | Fabric / Quilt | Forge | NeoForge |
|---|---|---|---|---|
| 1.21 – 1.21.1 | 21 | ✅ | ✅ | ✅ |
| 26.1.x | 25 | ✅ | ✅ | ✅ |
| 26.2.x | 25 | ✅ | ✅ | ✅ |
| 26.3.x | 25 | ✅ | ✅ | ✅ |

De 1.20.1 a 1.21.11 se añade en la siguiente fase (ver [docs/compatibilidad.md](docs/compatibilidad.md)).

Se prueba junto a Sodium, **Iris con shaders activados**, Lithium, FerriteCore, Entity Model Features, Entity Texture
Features y CustomSkinLoader (otro mod de skins: la de Takumi se impone), en las cuatro versiones de Minecraft. Detalle y límites en [docs/compatibilidad.md](docs/compatibilidad.md). No
se puede garantizar un «100 %» con cualquier combinación de mods: si encuentras un conflicto, abre un
[issue](https://github.com/AloneX15/TakumiSkins/issues) con versión, cargador, lista de mods y log.

## Cómo funciona

1. Takumi Launcher pide un token de juego (`sktk_…`, 7 días, muere con tu sesión) y lanza el juego con
   `TAKUMI_SKINS_TOKEN` y `TAKUMI_WEB_URL`.
2. El mod pregunta a la web (`POST /api/skins/lookup`) por los jugadores que ve: los no premium por su nombre (solo si
   su UUID es el offline de ese nombre) y los premium por la cuenta de Microsoft vinculada en el launcher.
3. Descarga las texturas (`GET /api/skins/textures/<sha256>.png`) y las pone encima de la skin que resolvió Minecraft.

Contrato completo: `takumi-webpage/docs/launcher-integration.md` §8. Mixins: [MIXINS.md](MIXINS.md).

## Desarrollo

```bash
./gradlew :26.3:build                                   # compila y ejecuta los tests unitarios
./gradlew :26.3:runClientGameTest                       # test de cliente de punta a punta (web simulada) con captura
./gradlew :26.3:runClientGameTest -PcompatPack          # lo mismo con el pack de compatibilidad (Iris con shaders)
./gradlew :26.3:runClientGameTest -PcompatPack=noiris   # el pack sin Iris, con la última Sodium
./gradlew :1.21.1:prodRunClient -PcompatPack            # 1.21.1 como una instancia real (jars publicados), con el pack
./gradlew :1.21.1-forge:build :26.3-neoforge:build      # Forge / NeoForge
./gradlew :1.21.1:runClient -PskinsClientSmoke          # arranque: mixins aplicados, skin vanilla intacta (sin token)
# Camino activo contra una web real (TAKUMI_SKINS_TOKEN=sktk_… TAKUMI_WEB_URL=http://127.0.0.1:5173):
./gradlew :1.21.1-forge:runClient -PskinsClientSmoke -PskinsSmokeExpect=NombreConSkin
```

Todas las versiones y cargadores salen del mismo código con Stonecutter. Fabric 1.21.1 se remapea a intermediary;
Forge/NeoForge usan nombres de Mojang; 26.x no está ofuscado. Las diferencias de API están solo en
`client/SkinOverlay.java` y `client/SkinTextures.java`. El código de `src/main` y `src/client` no puede usar Fabric
API (la CI lo comprueba): solo está en el entorno de desarrollo para los tests.

Para desarrollar con la web en Vite, arráncala en IPv4 (`pnpm dev --host 127.0.0.1`) y usa `http://127.0.0.1:5173`:
algunos entornos de Java resuelven `localhost` solo por IPv4 y Vite escucha en `::1`.

## Licencia

[MIT](LICENSE).

---

Creado por **TakumiStudios**.
