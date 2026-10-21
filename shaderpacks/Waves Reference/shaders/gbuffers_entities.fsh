#version 120
uniform sampler2D texture;
uniform sampler2D lightmap;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
varying vec3 normalView;
varying vec3 positionView;
void main() {
    vec4 color = texture2D(texture, uv) * tint;
    if (color.a < 0.1) discard;
    color.rgb *= texture2D(lightmap, lm).rgb;
    float fog = clamp((length(positionView) - fogStart) / max(1.0, fogEnd - fogStart), 0.0, 1.0);
    color.rgb = mix(color.rgb, fogColor, fog);
    gl_FragData[0] = color;
}
