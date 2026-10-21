// Inserted before the shader pack's declarations by Waves, before Iris transforms attributes.
uniform float waves_amplitude;
uniform float waves_wavelength;
uniform float waves_direction;
uniform float waves_sharpness;
uniform float waves_water_id;
uniform float waves_camera_y;
uniform float waves_sea_level;
uniform float waves_sea_only;
uniform float waves_phase_0;
uniform float waves_phase_1;
uniform float waves_phase_2;

vec4 waves_displace(vec4 vertex, vec3 normal, float material, out vec3 displacedNormal) {
    displacedNormal = normal;
    if (waves_amplitude <= 0.0 || waves_water_id < 0.0 || abs(material - waves_water_id) > 0.25) return vertex;
    float surface = 1.0;
    if (waves_sea_only > 0.5) {
        float difference = abs(vertex.y + waves_camera_y - (waves_sea_level - 0.111111));
        surface = 1.0 - smoothstep(0.35, 1.25, difference);
    }
    if (surface <= 0.0) return vertex;

    float height = 0.0;
    vec2 slope = vec2(0.0);
    for (int i = 0; i < 3; i++) {
        float lengthScale = i == 0 ? 1.0 : (i == 1 ? 0.57 : 0.29);
        float angleOffset = i == 0 ? 0.0 : (i == 1 ? 0.85 : -1.15);
        float weight = i == 0 ? 0.62 : (i == 1 ? 0.26 : 0.12);
        float phase = i == 0 ? waves_phase_0 : (i == 1 ? waves_phase_1 : waves_phase_2);
        float angle = waves_direction + angleOffset;
        vec2 direction = vec2(cos(angle), sin(angle));
        float k = 6.28318530718 / (waves_wavelength * lengthScale);
        float p = k * dot(vertex.xz, direction) + phase;
        height += weight * (sin(p) + 0.25 * waves_sharpness * sin(2.0 * p));
        slope += weight * k * direction * (cos(p) + 0.5 * waves_sharpness * cos(2.0 * p));
    }
    float strength = waves_amplitude * surface;
    vertex.y += height * strength;
    if (normal.y > 0.5) displacedNormal = normalize(vec3(-slope.x * strength, 1.0, -slope.y * strength));
    return vertex;
}
