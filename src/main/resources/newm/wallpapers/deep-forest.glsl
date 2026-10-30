// Deep forest — a calm, misty dark green where glowing spores drift along slow currents and
// step aside for the pointer. From newm's "Jadefire" look. Settings (values): speed,
// intensity, spores (how many, 0..2). Colours: colors[0] jade, colors[1] amber, colors[3] the dark.

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec2 hash22(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

vec3 tone(int i, vec3 fallback) { return colorCount > i ? colors[i].rgb : fallback; }

// The slow current at p: a field of turning sines (no noise: cheap and smooth).
vec2 current(vec2 p, float t) {
    return vec2(sin(p.y * 2.1 + t * 0.10) + 0.5 * sin(p.y * 4.3 - p.x * 1.3 + t * 0.07),
                cos(p.x * 1.7 - t * 0.08) + 0.5 * cos(p.x * 3.7 + p.y * 1.1 - t * 0.05)) * 0.12;
}

vec4 wallpaper(vec2 at) {
    vec3 jade = tone(0, vec3(0.239, 0.863, 0.592));
    vec3 amber = tone(1, vec3(0.949, 0.702, 0.239));
    vec3 dark = tone(3, vec3(0.024, 0.035, 0.031));
    float t = time * speed;
    float aspect = size.x / max(size.y, 1.0);
    vec2 p = (at - 0.5) * vec2(aspect, 1.0);

    // Depth: darker above, a glow low in the middle, mist drifting in two layers.
    vec3 color = mix(dark * 2.4 + jade * 0.03, dark * 0.8, smoothstep(-0.2, 0.6, -p.y));
    color += jade * 0.07 * exp(-dot(p - vec2(0.0, 0.35), p - vec2(0.0, 0.35)) * 2.0);
    float mist = noise(p * 2.2 + vec2(t * 0.025, 0.0)) * 0.6 + noise(p * 4.7 - vec2(t * 0.04, t * 0.01)) * 0.4;
    color += jade * 0.05 * smoothstep(0.35, 0.9, mist) * smoothstep(-0.5, 0.4, p.y);
    // Tall dark trunks far away, swaying a hair.
    float trunks = noise(vec2(p.x * 6.0 + 0.3 * sin(t * 0.05), 0.5));
    color *= 1.0 - 0.6 * smoothstep(0.6, 0.72, trunks) * smoothstep(0.6, -0.2, p.y);
    // Light falling slantwise through the leaves, slowly shifting.
    float shafts = noise(vec2((p.x + p.y * 0.35) * 5.0 + t * 0.02, t * 0.03));
    color += mix(jade, amber, 0.35) * 0.05 * smoothstep(0.55, 0.95, shafts) * smoothstep(0.6, -0.5, p.y) * intensity;

    // Spores: a few per cell, carried along the current, pushed aside by the pointer.
    vec2 away = pointer.x >= 0.0 ? (pointer - 0.5) * vec2(aspect, 1.0) : vec2(-10.0);
    float glow = 0.0;
    float warm = 0.0;
    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        float cellSize = 0.09 + 0.05 * fl;
        vec2 q = p + current(p * (1.0 + fl * 0.3), t + fl * 10.0) + vec2(t * 0.006 * (1.0 + fl), -t * 0.004);
        vec2 push = p - away;
        q += push * 0.08 * exp(-dot(push, push) * 25.0) / (length(push) + 0.05);
        vec2 cell = floor(q / cellSize);
        vec2 local = q / cellSize - cell;
        vec2 rnd = hash22(cell + fl * 31.0);
        if (rnd.x > 0.32 * clamp(spores, 0.0, 3.0)) continue;
        vec2 center = 0.3 + 0.4 * hash22(cell + 5.3);
        float d = length((local - center) * cellSize);
        float pulse = 0.55 + 0.45 * sin(t * (0.5 + rnd.y) + rnd.x * 60.0);
        float size_ = 0.0042 * (1.0 - fl * 0.25);
        float g = (exp(-d * d / (size_ * size_)) + 0.22 * exp(-d / (size_ * 2.5))) * pulse * (1.0 - fl * 0.3);
        glow += g;
        warm += g * step(0.85, rnd.y);
    }
    color += jade * glow * 0.9 * intensity + amber * warm * 0.6 * intensity;
    // Vignette, and a little grain against banding.
    color *= 1.0 - 1.1 * dot(at - 0.5, at - 0.5);
    color += (hash12(at * size + fract(t) * 91.0) - 0.5) / 255.0;
    return vec4(color, 1.0);
}
