# Compatibilidad de Takumi Skins

Validación local del 22 de noviembre de 2026. Las versiones de los mods del pack están fijadas en
`stonecutter.properties.toml`; esta matriz no promete compatibilidad con cualquier versión futura o modpack.

## Cargadores y versiones

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 1.21.1 | Compilación, unitarios, arranque, skin real de la web, producción con mundo | Compilación, unitarios, arranque, skin real de la web | Compilación, unitarios |
| 26.1.2 | Compilación, unitarios | Compilación, unitarios, skin real de la web | Compilación, unitarios |
| 26.2 | Compilación, unitarios | Compilación, unitarios | Compilación, unitarios |
| 26.3 | Compilación, unitarios, test de cliente de punta a punta | Compilación, unitarios | Compilación, unitarios, arranque, skin real de la web |

- *Arranque:* `runClient -PskinsClientSmoke`. Mixins aplicados, el hook se ejecuta y la skin vanilla sigue intacta sin
  token (el mod queda inactivo y no hace ninguna petición).
- *Skin real de la web:* el mismo arranque con un token de juego contra la web local. El perfil recibe skin, capa y
  modelo de Takumi (`takumi_skins:skins/<sha>`, con `secure=true` en 1.21.1).
- *Test de cliente de punta a punta:* `runClientGameTest`, con una web simulada que exige el token. El jugador lleva la
  skin, la capa y los brazos finos; captura en `versions/26.3/build/run/clientGameTest/screenshots/`.

- *Producción con mundo:* `prodRunClient`. Jars publicados en Minecraft ofuscado, web simulada, mundo plano, el jugador
  lleva skin, capa y brazos de Takumi; captura en `versions/1.21.1/build/run/prod/screenshots/`.

La CI repite compilación, unitarios y arranque en las doce combinaciones, el test de cliente de 26.x con y sin el pack y
el arranque en producción de 1.21.1 con y sin el pack.

## Pack de compatibilidad (Fabric)

| Mod | 1.21.1 (producción) | 26.1.2 / 26.2 | 26.3 |
|---|---|---|---|
| Sodium | 0.8.13 ✅ | 0.9.2 ✅ | 0.9.2 con Iris ✅ · 0.9.3 sin Iris ✅ |
| Iris, con shaders activados | 1.8.14-beta.1 ✅ | 1.11.4 ✅ | 1.11.7 ✅ |
| Lithium | 0.15.3 ✅ | 0.24.7 / 0.25.3 ✅ | 0.26.2 ✅ |
| FerriteCore | 7.0.3 ✅ | 9.0.0 ✅ | 9.0.0 ✅ |
| Entity Model Features | 3.3.12 ✅ | 3.3.12 ✅ | 3.3.12 ✅ |
| Entity Texture Features | 7.2.7 ✅ | 7.2.7 ✅ | 7.2.7 ✅ |
| CustomSkinLoader | 15.1 ✅ | 15.1 ✅ | 15.1 ✅ (la skin de Takumi se impone) |

Cómo se prueba:

- **26.x:** `runClientGameTest -PcompatPack`. Con Iris, el build copia a la carpeta del test el shaderpack de prueba
  [`compat/iris/shaderpacks/TakumiSkinsTest`](../compat/iris/shaderpacks/TakumiSkinsTest) y una `iris.properties` que lo
  activa. El test exige que Iris tenga el shaderpack en uso (`IrisApi.isShaderPackInUse()`) y que el jugador lleve la
  skin, la capa y los brazos de Takumi; la captura `takumi_skins_player_iris.png` muestra al jugador dibujado por los
  programas del shaderpack.
- **1.21.1:** `prodRunClient -PcompatPack`. Minecraft ofuscado con Fabric Loader y los jars tal cual se publican (el de
  Takumi Skins remapeado a intermediary), como una instancia de Takumi Launcher con una cuenta no premium, contra una web
  de Takumi simulada desde Gradle. El smoke crea un mundo plano, espera a que el jugador lleve la skin de Takumi,
  comprueba que Iris dibuja el mundo con shaders y guarda `versions/1.21.1/build/run/prod/screenshots/`. Iris y
  CustomSkinLoader no arrancan en el entorno de desarrollo de 1.21.1 (dependen de nombres intermediary, también sin
  Takumi Skins), por eso esta versión se prueba en producción.

Notas:

1. Sodium 0.9.3 para 26.3 declara `breaks: iris <=1.12.0`. Quien usa Iris en 26.3 tiene la Sodium que Iris pide
   (0.9.2), así que el pack con Iris usa esa (`compat.sodium_iris`) y la CI prueba aparte la 0.9.3 sin Iris
   (`-PcompatPack=noiris`). Cuando salga un Iris que admita Sodium 0.9.3, basta con vaciar `compat.sodium_iris`.
2. FerriteCore 7.0.2 para 1.21.1 falla en producción (mixin `BlockStateCacheMixin`, también sin Takumi Skins); el
   entorno de desarrollo no lo mostraba. El pack usa 7.0.3.
3. Takumi Skins no tiene código para Iris: solo cambia qué textura lleva cada jugador y Iris la dibuja con su pipeline
   como cualquier otra. Las texturas son `DynamicTexture` normales del `TextureManager`
   (`takumi_skins:skins/<sha256>`), sin mapas PBR (`_n`/`_s`): los shaderpacks con PBR usan sus valores por defecto,
   igual que con las skins de Mojang.
4. La CI ejecuta los tests de cliente con renderizado por software (Mesa). Los shaders del pack de prueba son mínimos
   (GLSL 120, solo programas `gbuffers`) para que funcionen ahí; los shaderpacks reales (BSL, Complementary…) solo se
   pueden probar a mano con GPU.

## OptiFine

OptiFine solo existe para Forge en 1.21.1 (no hay para NeoForge ni OptiFabric para 1.21+). Su licencia no permite
descargarlo en la CI: queda como prueba manual. OptiFine pinta su capa propia (de su servicio de capas); si en la
prueba se impone a la de Takumi, se añadirá un mixin opcional (`@Pseudo`, `require = 0`) o se documentará como límite.

## Pendiente: 1.20.1 a 1.21.11

Siguiente fase del plan, con nodos 1.20.1 (Fabric, Forge), 1.20.4, 1.20.6, 1.21.4, 1.21.8 y 1.21.10/11 (Fabric, Forge,
NeoForge). 1.20.1 usa la API antigua de skins (`ResourceLocation` sueltos en `PlayerInfo`) y necesita su propio tramo
en `SkinOverlay`.

## Límites

- Solo ven las skins de Takumi quienes juegan desde Takumi Launcher con el mod. Los demás ven la skin normal del
  jugador: no se firma nada en el servidor.
- En servidores no premium cualquiera puede entrar con cualquier nombre y recibe la skin de Takumi de ese nombre, como
  con cualquier sistema de skins no premium.
- Un mod que sustituya por completo `PlayerInfo` o `SkinManager` deja de mostrar las skins de Takumi (sin errores).
