// Scales — iridescent dragon scales in the dark, a sheen gliding over them, ripples running
// out from each click. From newm's "Jadefire" look. Settings (values): speed, intensity,
// scaleSize (how big, 0.5..3). Colours: colors[0] jade, colors[1] amber, colors[2] ember,
// colors[3] the dark between them.

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec3 tone(int i, vec3 fallback) { return colorCount > i ? colors[i].rgb : fallback; }

vec4 wallpaper(vec2 at) {
    vec3 jade = tone(0, vec3(0.239, 0.863, 0.592));
    vec3 amber = tone(1, vec3(0.949, 0.702, 0.239));
    vec3 ember = tone(2, vec3(0.910, 0.333, 0.176));
    vec3 dark = tone(3, vec3(0.024, 0.035, 0.031));
    float t = time * speed;
    float aspect = size.x / max(size.y, 1.0);
    vec2 p = (at - 0.5) * vec2(aspect, 1.0);

    // Ripples from the clicks: rings running out, fading; they lift the scales they pass.
    float lift = 0.0;
    for (int i = 0; i < 4; i++) {
        if (i >= clickCount) break;
        vec2 c = (clicks[i].xy - 0.5) * vec2(aspect, 1.0);
        float age = time - clicks[i].z;
        if (age < 0.0 || age > 4.0) continue;
        float ring = length(p - c) - age * 0.45;
        lift += exp(-ring * ring * 160.0) * exp(-age * 0.9);
    }
    // And a soft swell round the pointer.
    if (pointer.x >= 0.0) {
        vec2 c = (pointer - 0.5) * vec2(aspect, 1.0);
        lift += 0.35 * exp(-dot(p - c, p - c) * 30.0);
    }

    // Overlapping scales, rows half a scale apart: each row lies over the one below, so a
    // point shows the highest scale whose round covers it (their round lower edges show).
    vec2 s = p * 9.0 / clamp(scaleSize, 0.3, 4.0);
    s.y -= t * 0.02;
    float row = floor(s.y / 0.5);
    float found = 0.0;
    vec2 bestCell = vec2(0.0);
    vec2 bestLocal = vec2(0.0);
    float bestD = 1.0;
    for (int dy = -2; dy <= 0; dy++) {
        float r = row + float(dy);
        float shift = mod(r, 2.0) * 0.5;
        float col = floor(s.x - shift);
        for (int dx = 0; dx <= 1; dx++) {
            vec2 cell = vec2(col + float(dx), r);
            vec2 local = s - vec2(cell.x + shift + 0.5 - float(dx) * 0.0, r * 0.5);
            local.x = s.x - (cell.x + shift + 0.5);
            float d = length(local * vec2(1.0, 0.95)) / 0.6;
            if (found < 0.5 && d < 1.0) {
                found = 1.0;
                bestCell = cell;
                bestLocal = local / 0.6;
                bestD = d;
            }
        }
        if (found > 0.5) break;
    }
    if (found < 0.5) return vec4(dark, 1.0);
    float d = bestD;
    float id = hash12(bestCell);
    // Iridescence: the hue turns with the angle to a light gliding over them.
    vec2 light = vec2(sin(t * 0.07) * 0.8, cos(t * 0.05) * 0.4 - 0.3);
    vec3 normal = normalize(vec3(bestLocal * vec2(0.9, 0.7), 0.7));
    float facing = clamp(dot(normal, normalize(vec3(light - p * 0.5, 0.9))), 0.0, 1.0);
    float sheen = pow(facing, 22.0);
    float hue = 0.5 + 0.5 * sin(id * 5.0 + facing * 4.0 + t * 0.15 + p.x * 1.5);
    vec3 film = mix(jade, amber, smoothstep(0.45, 0.95, hue));
    film = mix(film, ember, clamp(lift * 0.8, 0.0, 0.7));
    vec3 scale = mix(dark * 1.2, jade * 0.07, smoothstep(1.0, 0.0, d));
    scale += film * (0.025 + 0.5 * sheen) * intensity;
    scale += mix(film, amber, 0.5) * lift * 1.1 * intensity;
    // A bright lower edge; the scale above shades its top.
    float rim = smoothstep(0.78, 0.95, d) * smoothstep(1.0, 0.95, d) * smoothstep(-0.1, 0.4, bestLocal.y);
    scale += film * rim * (0.12 + 0.45 * sheen + 0.8 * lift) * intensity;
    scale *= mix(0.35, 1.0, smoothstep(-0.9, 0.3, bestLocal.y));
    scale *= 0.8 + 0.4 * id;
    // Vignette, and a little grain against banding.
    scale *= 1.0 - 1.4 * dot(at - 0.5, at - 0.5);
    scale += (hash12(at * size + fract(t) * 91.0) - 0.5) / 255.0;
    return vec4(scale, 1.0);
}
