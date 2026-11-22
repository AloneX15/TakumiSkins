# Changelog

## 0.1.0

- Primera versión: skins, capas y modelo de brazos de Takumi en Minecraft 1.21.1, 26.1.2, 26.2 y 26.3, para Fabric,
  Quilt, Forge y NeoForge. Solo se activa con el token de Takumi Launcher. Sin dependencia de Fabric API.
- Compatible con Iris, también con shaders activados: probado en 1.21.1 (Iris 1.8.14), 26.1.2 y 26.2 (Iris 1.11.4) y
  26.3 (Iris 1.11.7). En 1.21.1 se prueba además con CustomSkinLoader en una instancia de producción.
- La caché de texturas (`.takumi/skins/`) se limpia al arrancar: se borran las imágenes sin usar en 30 días y las
  descargas a medias. La web también borra las imágenes que ya no lleva nadie.
