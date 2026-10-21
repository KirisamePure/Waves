// Built into the Mod's native terrain renderer; not an Iris shader pack.
in vec3 waves_surface;
in vec3 waves_surface_normal;
flat in float waves_is_water;
uniform float waves_is_native;
uniform float waves_amplitude;
uniform float waves_camera_y;
uniform float waves_sea_level;
uniform float waves_sea_only;
uniform float waves_foam;
uniform float waves_time;
uniform float waves_noise_x;
uniform float waves_noise_z;
uniform sampler2D waves_shore_field;
uniform float waves_shore_enabled;
uniform float waves_field_origin_x;
uniform float waves_field_origin_z;
float waves_hash(vec2 p) {
    p = mod(p, 256.0);
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float waves_noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(waves_hash(i), waves_hash(i + vec2(1, 0)), f.x),
        mix(waves_hash(i + vec2(0, 1)), waves_hash(i + vec2(1, 1)), f.x), f.y);
}
vec4 waves_shade(vec4 color) {
    if (waves_is_native < 0.5 || waves_is_water < 0.5 || waves_amplitude <= 0.0) return color;
    if (waves_sea_only > 0.5 && abs(waves_surface.y + waves_camera_y - waves_sea_level) > waves_amplitude * 1.6 + 1.25) return color;
    vec3 n = normalize(waves_surface_normal);
    vec3 eye = normalize(-waves_surface);
    float fresnel = pow(1.0 - max(dot(n, eye), 0.0), 4.0);
    float slopeLight = clamp(dot(n, normalize(vec3(0.3, 1, 0.4))), 0.3, 1.0);
    color.rgb *= 0.7 + 0.4 * slopeLight;
    color.rgb = mix(color.rgb, u_FogColor.rgb, fresnel * 0.3);
    color.a = min(0.95, color.a + fresnel * 0.2);
    vec2 pattern = waves_surface.xz + vec2(waves_noise_x, waves_noise_z);
    float noise = 0.65 * waves_noise(pattern * 2.0 + vec2(0.13, -0.09) * waves_time)
        + 0.35 * waves_noise(pattern * 4.0 - vec2(0.13, -0.09) * waves_time);
    float crest = (waves_surface.y + waves_camera_y - (waves_sea_level - 0.111111)) / max(0.1, waves_amplitude);
    float foam = smoothstep(0.35, 0.8, crest) * smoothstep(0.43, 0.8, noise) * smoothstep(0.4, 1.4, waves_amplitude);
    if (waves_shore_enabled > 0.5) {
        vec2 local = waves_surface.xz - vec2(waves_field_origin_x, waves_field_origin_z);
        float edge = min(min(local.x, local.y), min(128.0 - local.x, 128.0 - local.y));
        if (edge > 0.0) {
            float d = max(0.0, texture(waves_shore_field, local / 128.0).r - 0.5);
            float surge = 0.5 + 0.5 * sin(waves_time * 1.6 - d * 1.9 + waves_noise(pattern) * 4.0);
            foam += exp(-d * 0.7) * smoothstep(1.0, 8.0, edge) * smoothstep(0.2, 0.8, noise + surge * 0.45) * (0.5 + surge * 0.5);
        }
    }
    foam = clamp(foam * waves_foam, 0.0, 0.95);
    // Preserve local day/night/block lighting instead of emitting bright foam in darkness.
    float light = clamp(max(max(v_Color.r, v_Color.g), v_Color.b), 0.08, 1.0);
    color.rgb = mix(color.rgb, vec3(0.85, 0.94, 0.98) * light, foam);
    color.a = max(color.a, foam * 0.95);
    return color;
}
