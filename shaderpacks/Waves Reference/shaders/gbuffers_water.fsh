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
    vec3 n = normalize(normalView);
    vec3 eye = normalize(-positionView);
    float fresnel = pow(1.0 - max(dot(n, eye), 0.0), 4.0);
    vec3 water = color.rgb * texture2D(lightmap, lm).rgb;
    vec3 reflected = fogColor + vec3(0.08, 0.12, 0.16);
    color.rgb = mix(water, reflected, 0.15 + 0.5 * fresnel);
    float highlight = pow(max(dot(reflect(-eye, n), normalize(vec3(0.3, 0.8, 0.4))), 0.0), 64.0);
    color.rgb += vec3(highlight * 0.2);
    color.a = clamp(color.a + 0.2 + fresnel * 0.25, 0.5, 0.95);
    float fog = clamp((length(positionView) - fogStart) / max(1.0, fogEnd - fogStart), 0.0, 1.0);
    color.rgb = mix(color.rgb, fogColor, fog);
    gl_FragData[0] = color;
}
