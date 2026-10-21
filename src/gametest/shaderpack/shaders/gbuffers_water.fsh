#version 120
uniform sampler2D texture;
uniform sampler2D lightmap;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform sampler2D waves_shore_field;
uniform float waves_shore_enabled;
uniform float waves_field_origin_x;
uniform float waves_field_origin_z;
uniform float waves_camera_y;
uniform float waves_sea_level;
uniform float waves_amplitude;
uniform float waves_water_id;
uniform float waves_foam;
uniform float waves_time;
uniform float waves_noise_x;
uniform float waves_noise_z;
varying vec3 waves_surface;
varying float waves_material;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
varying vec3 normalView;
varying vec3 positionView;
float foam_hash(vec2 p) {
    p = mod(p, 256.0);
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float foam_noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(foam_hash(i), foam_hash(i + vec2(1, 0)), f.x),
               mix(foam_hash(i + vec2(0, 1)), foam_hash(i + vec2(1, 1)), f.x), f.y);
}
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
    if (waves_foam > 0.0 && waves_amplitude > 0.0 && abs(waves_material - waves_water_id) < 0.25) {
        vec2 worldPattern = waves_surface.xz + vec2(waves_noise_x, waves_noise_z);
        vec2 drift = vec2(0.13, -0.09) * waves_time;
        float noise = 0.65 * foam_noise(worldPattern * 2.0 + drift) + 0.35 * foam_noise(worldPattern * 4.0 - drift);
        float crest = (waves_surface.y + waves_camera_y - (waves_sea_level - 0.111111)) / max(0.1, waves_amplitude);
        float foam = smoothstep(0.35, 0.8, crest) * smoothstep(0.43, 0.8, noise) * smoothstep(0.4, 1.4, waves_amplitude);
        if (waves_shore_enabled > 0.5) {
            vec2 local = waves_surface.xz - vec2(waves_field_origin_x, waves_field_origin_z);
            float edge = min(min(local.x, local.y), min(128.0 - local.x, 128.0 - local.y));
            if (edge > 0.0) {
                float d = max(0.0, texture2D(waves_shore_field, local / 128.0).r - 0.5);
                float band = exp(-d * 1.1) * smoothstep(1.0, 8.0, edge);
                float surge = 0.5 + 0.5 * sin(waves_time * 1.6 - d * 1.9 + foam_noise(worldPattern) * 4.0);
                foam += band * smoothstep(0.2, 0.8, noise + surge * 0.45) * smoothstep(0.3, 0.85, surge);
            }
        }
        foam = clamp(foam * waves_foam, 0.0, 0.95);
        vec3 whitewater = vec3(0.85, 0.94, 0.98) * (0.45 + 0.55 * texture2D(lightmap, lm).rgb);
        color.rgb = mix(color.rgb, whitewater, foam);
        color.a = max(color.a, foam * 0.95);
    }
    float fog = clamp((length(positionView) - fogStart) / max(1.0, fogEnd - fogStart), 0.0, 1.0);
    color.rgb = mix(color.rgb, fogColor, fog);
    gl_FragData[0] = color;
}
