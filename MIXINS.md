# Mixins de Takumi Skins

Takumi Skins no usa Fabric API (Takumi Launcher lo instala en instancias que no gestiona y no debe forzar una versión),
así que todo se engancha con mixins. Reglas:

- Solo `@Inject` en `HEAD`, `RETURN` o `TAIL`. Sin overwrites, redirects ni MixinExtras.
- Solo se cambia el **valor devuelto** (qué skin lleva un jugador), nunca el renderizado: Sodium, Iris, OptiFine,
  EMF/ETF y similares dibujan lo que les damos.
- `"required": false` y `defaultRequire: 0`: si otro mod cambia un método objetivo, el juego arranca igual y solo se
  pierde la skin de Takumi. El smoke de arranque de la CI (`-PskinsClientSmoke`) falla si los hooks no se aplican.
- Las envolturas son idempotentes (`SkinOverlay.TakumiSkinLookup`): si dos hooks coinciden no se aplica dos veces.

| Mixin | Destino | Motivo y alcance |
|---|---|---|
| `PlayerInfoMixin` | `PlayerInfo.createSkinLookup` (todas) | Skin de cada jugador del mundo y de la lista de tab, el propio incluido. Envuelve el `Supplier` que `PlayerInfo` consulta en cada frame. |
| `SkinManagerMixin` | 26.x: `SkinManager.createLookup`, `get` · 1.21.1: `lookupInsecure`, `getInsecureSkin` | Skins pedidas fuera de `PlayerInfo`: cabezas de jugador, widgets de skin y otros mods. `get` (cabezas en 26.x) espera hasta 3 s a la skin de Takumi. Añade la interfaz marcadora `TakumiSkinsHooked`. |
| `ClientPacketListenerMixin` | `ClientPacketListener.sendCommand` | Intercepta solo `takumiskins recargar` / `reload`; el resto de comandos va al servidor. |
| `ClientSmokeMixin` | `Minecraft.tick` (TAIL) | Comprobación de arranque de la CI; no hace nada si no se activa con `-Dtakumi_skins.clientSmoke=true`. |

En 1.21.1 la skin de Takumi se devuelve con `secure = true`: `PlayerInfo` cambia por la skin por defecto las skins no
seguras de otros jugadores.

Posibles conflictos: un mod que sustituya por completo `PlayerInfo` o `SkinManager` (en vez de envolverlos) dejaría de
mostrar la skin de Takumi, sin errores. Los mods de skins que envuelven el mismo retorno (CustomSkinLoader) quedan por
debajo: manda Takumi.
