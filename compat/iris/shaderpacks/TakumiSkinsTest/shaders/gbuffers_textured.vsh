#version 120
// Shaderpack mínimo de prueba de Takumi Skins: dibuja cada superficie con su textura y su color, sin efectos.
// Iris lo usa para jugadores, entidades, partículas y, por herencia, para el resto de programas gbuffers.
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
}
