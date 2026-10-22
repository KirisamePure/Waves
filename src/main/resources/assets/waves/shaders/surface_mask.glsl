uniform sampler2D waves_surface_mask;
uniform float waves_surface_replace;
uniform float waves_surface_origin_x;
uniform float waves_surface_origin_z;
float waves_cell_covered(vec2 p) {
    vec2 local = p - vec2(waves_surface_origin_x, waves_surface_origin_z);
    if (local.x < 0.003 || local.y < 0.003 || local.x > 127.997 || local.y > 127.997) return 0.0;
    // Include either neighboring cell at an exact voxel face, so old vertical side walls disappear too.
    vec2 uv = local / 128.0;
    vec2 e = vec2(0.002 / 128.0);
    return max(max(texture2D(waves_surface_mask, uv - e).r, texture2D(waves_surface_mask, uv + e).r),
               max(texture2D(waves_surface_mask, uv + vec2(e.x, -e.y)).r,
                   texture2D(waves_surface_mask, uv + vec2(-e.x, e.y)).r));
}
