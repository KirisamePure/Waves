#version 120
uniform sampler2D texture;
varying vec2 uv;
varying vec4 tint;
void main() {
    vec4 color = texture2D(texture, uv) * tint;
    if (color.a < 0.01) discard;
    gl_FragData[0] = color;
}
