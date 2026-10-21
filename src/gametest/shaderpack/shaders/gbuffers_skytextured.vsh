#version 120
varying vec2 uv;
varying vec4 tint;
void main() {
    gl_Position = ftransform();
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    tint = gl_Color;
}
