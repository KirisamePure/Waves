// Waves vertex library, inserted before Iris transforms compatibility attributes.
uniform float waves_amplitude;
uniform float waves_wavelength;
uniform float waves_direction;
uniform float waves_sharpness;
uniform float waves_irregularity;
uniform float waves_water_id;
uniform float waves_camera_y;
uniform float waves_sea_level;
uniform float waves_sea_only;
uniform float waves_time;
uniform float waves_phase_0;
uniform float waves_phase_1;
uniform float waves_phase_2;
uniform float waves_phase_3;
uniform float waves_phase_4;
uniform float waves_phase_5;
uniform float waves_phase_6;
uniform float waves_phase_7;
uniform sampler2D waves_shore_field;
uniform float waves_shore_enabled;
uniform float waves_field_origin_x;
uniform float waves_field_origin_z;
uniform float waves_shore_fade;
uniform float waves_reflection;

float waves_length(int i) {
    if (i == 0) return 1.0;
    if (i == 1) return 0.743;
    if (i == 2) return 0.527;
    if (i == 3) return 0.389;
    if (i == 4) return 0.281;
    if (i == 5) return 0.197;
    if (i == 6) return 0.143;
    return 0.103;
}
float waves_angle(int i) {
    if (i == 0) return 0.0;
    if (i == 1) return 0.41;
    if (i == 2) return -0.72;
    if (i == 3) return 1.17;
    if (i == 4) return -1.63;
    if (i == 5) return 0.83;
    if (i == 6) return 2.35;
    return -2.71;
}
float waves_weight(int i) {
    if (i == 0) return 0.28;
    if (i == 1) return 0.22;
    if (i == 2) return 0.17;
    if (i == 3) return 0.12;
    if (i == 4) return 0.085;
    if (i == 5) return 0.06;
    if (i == 6) return 0.04;
    return 0.025;
}
float waves_phase(int i) {
    if (i == 0) return waves_phase_0;
    if (i == 1) return waves_phase_1;
    if (i == 2) return waves_phase_2;
    if (i == 3) return waves_phase_3;
    if (i == 4) return waves_phase_4;
    if (i == 5) return waves_phase_5;
    if (i == 6) return waves_phase_6;
    return waves_phase_7;
}
float waves_distance(vec2 relativePosition) {
    vec2 local = relativePosition - vec2(waves_field_origin_x, waves_field_origin_z);
    float edge = min(min(local.x, local.y), min(128.0 - local.x, 128.0 - local.y));
    if (edge <= 0.0) return 12.0;
    float distance = max(0.0, texture2D(waves_shore_field, local / 128.0).r - 0.5);
    return mix(12.0, distance, smoothstep(1.0, 8.0, edge));
}
vec4 waves_displace(vec4 vertex, vec3 normal, float material, out vec3 displacedNormal) {
    displacedNormal = normal;
    if (waves_amplitude <= 0.0 || waves_water_id < 0.0 || abs(material - waves_water_id) > 0.25) return vertex;
    float surface = 1.0;
    if (waves_sea_only > 0.5) {
        float difference = abs(vertex.y + waves_camera_y - (waves_sea_level - 0.111111));
        surface = 1.0 - smoothstep(0.35, 1.25, difference);
    }
    if (surface <= 0.0) return vertex;

    float phases[8];
    vec2 gradients[8];
    for (int i = 0; i < 8; i++) {
        float angle = waves_direction + waves_angle(i) * (0.3 + waves_irregularity * 1.3);
        vec2 direction = vec2(cos(angle), sin(angle));
        float k = 6.28318530718 / max(3.5, waves_wavelength * waves_length(i));
        gradients[i] = k * direction;
        phases[i] = dot(vertex.xz, gradients[i]) + waves_phase(i);
    }
    float height = 0.0;
    vec2 slope = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        int j = i + 3;
        int l = i + 5;
        if (j >= 8) j -= 8;
        if (l >= 8) l -= 8;
        float warp = phases[j] + phases[l];
        float p = phases[i] + 0.45 * waves_irregularity * sin(warp);
        vec2 dp = gradients[i] + 0.45 * waves_irregularity * cos(warp) * (gradients[j] + gradients[l]);
        height += waves_weight(i) * (sin(p) + 0.2 * waves_sharpness * sin(2.0 * p));
        slope += waves_weight(i) * dp * (cos(p) + 0.4 * waves_sharpness * cos(2.0 * p));
    }
    float groupPhase = phases[6] - phases[7];
    float group = 1.0 + 0.25 * waves_irregularity * sin(groupPhase);
    slope = slope * group + height * 0.25 * waves_irregularity * cos(groupPhase) * (gradients[6] - gradients[7]);
    height *= group;

    if (waves_shore_enabled > 0.5) {
        float d = waves_distance(vertex.xz);
        vec2 gradient = vec2(waves_distance(vertex.xz + vec2(1, 0)) - waves_distance(vertex.xz - vec2(1, 0)),
                             waves_distance(vertex.xz + vec2(0, 1)) - waves_distance(vertex.xz - vec2(0, 1))) * 0.5;
        float shoreRange = max(0.5, waves_shore_fade * 0.35);
        float t = clamp(d / shoreRange, 0.0, 1.0);
        float attenuation = 0.72 + 0.28 * t * t * (3.0 - 2.0 * t);
        slope = slope * attenuation + height * gradient * (0.28 * 6.0 * t * (1.0 - t) / shoreRange);
        height *= attenuation;
        float reflectedPhase = d * 1.7 - waves_time * 1.9 + 0.7 * sin(phases[2] + phases[4]);
        float reflectedStrength = 0.22 * waves_reflection * exp(-d * 0.45) * smoothstep(0.0, 0.9, d);
        height += reflectedStrength * sin(reflectedPhase);
        slope += gradient * reflectedStrength * 1.7 * cos(reflectedPhase);
    }
    float strength = waves_amplitude * surface;
    vertex.y += height * strength;
    if (normal.y > 0.5) displacedNormal = normalize(vec3(-slope.x * strength, 1.0, -slope.y * strength));
    return vertex;
}
