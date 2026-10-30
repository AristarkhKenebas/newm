// Ember sky — jade and amber curtains of light over a night sky, embers drifting up that
// gather round the pointer. From newm's "Jadefire" look. Settings (values): speed,
// intensity, embers (how many, 0..2). Colours: colors[0] jade, colors[1] amber,
// colors[2] ember, colors[3] the night.

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

// One curtain of light: a wavy band, bright at its lower edge, fading upwards in rays.
float curtain(vec2 p, float t, float seed, float height) {
    float x = p.x * 1.3 + seed;
    float wave = 0.10 * sin(x * 1.7 + t * 0.21 + seed) + 0.05 * sin(x * 3.9 - t * 0.33) + 0.06 * (noise(vec2(x * 1.4, t * 0.08 + seed)) - 0.5);
    float base = height + wave;
    float below = p.y - base;
    float rays = 0.55 + 0.45 * noise(vec2(x * 18.0, t * 0.25 + seed * 3.0));
    float glow = below < 0.0 ? exp(below * 14.0) * 0.8 : exp(-below * 5.0) * rays;
    float folds = 0.35 + 0.65 * smoothstep(0.25, 0.75, noise(vec2(x * 2.2 - t * 0.05, seed)));
    return glow * folds;
}

vec4 wallpaper(vec2 at) {
    vec3 jade = tone(0, vec3(0.239, 0.863, 0.592));
    vec3 amber = tone(1, vec3(0.949, 0.702, 0.239));
    vec3 ember = tone(2, vec3(0.910, 0.333, 0.176));
    vec3 night = tone(3, vec3(0.024, 0.035, 0.031));
    float t = time * speed;
    float aspect = size.x / max(size.y, 1.0);
    // y up from the bottom, in heights of the screen.
    vec2 p = vec2((at.x - 0.5) * aspect, 1.0 - at.y);

    // The sky: night above, a warm haze at the horizon.
    vec3 color = mix(night * 0.7, night * 1.6 + ember * 0.07, smoothstep(0.9, 0.0, p.y));
    color += amber * 0.05 * exp(-p.y * 6.0);
    // Stars, few and faint.
    vec2 sc = floor(at * size / 3.0);
    float star = step(0.9985, hash12(sc)) * (0.5 + 0.5 * sin(t * 1.3 + hash12(sc + 7.0) * 40.0));
    color += vec3(0.8, 0.95, 0.9) * star * 0.35 * smoothstep(0.2, 0.8, p.y);

    // Curtains: jade, a second farther and fainter, amber at their feet.
    float c1 = curtain(p, t, 0.0, 0.42);
    float c2 = curtain(p * vec2(0.8, 1.0) + vec2(0.7, 0.0), t * 0.8, 2.3, 0.55);
    vec3 light = jade * c1 * 0.5 + mix(jade, vec3(0.3, 0.7, 0.9), 0.3) * c2 * 0.28;
    light += amber * 0.35 * pow(c1, 3.0);
    color += light * intensity * smoothstep(0.0, 0.25, p.y);

    // Embers rising: one per cell, swaying, gathering round the pointer.
    vec2 toward = pointer.x >= 0.0 ? vec2((pointer.x - 0.5) * aspect, 1.0 - pointer.y) : vec2(-10.0);
    float sparks = 0.0;
    for (int layer = 0; layer < 2; layer++) {
        float fl = float(layer);
        float cellSize = 0.11 + 0.07 * fl;
        vec2 q = p;
        q.y -= t * (0.035 + 0.02 * fl);
        // Near the pointer they are drawn in towards it.
        vec2 pull = toward - p;
        q += pull * 0.35 * exp(-dot(pull, pull) * 18.0);
        vec2 cell = floor(q / cellSize);
        vec2 local = q / cellSize - cell;
        vec2 rnd = hash22(cell + fl * 17.0);
        if (rnd.x > 0.28 * clamp(embers, 0.0, 3.0)) continue;
        vec2 center = 0.35 + 0.3 * hash22(cell + 3.1);
        center.x += 0.12 * sin(t * (0.6 + rnd.y) + rnd.x * 30.0);
        float d = length((local - center) * cellSize);
        float flicker = 0.6 + 0.4 * sin(t * (3.0 + 4.0 * rnd.y) + rnd.x * 50.0);
        sparks += (exp(-d * d * 9000.0 / (1.0 + fl)) * 1.3 + exp(-d * 90.0 / cellSize * 0.11) * 0.10) * flicker * (1.0 - fl * 0.35);
    }
    color += mix(ember, amber, 0.55) * sparks * intensity * smoothstep(1.1, 0.2, p.y);

    // A dark ridge of hills along the bottom.
    float ridge = 0.08 + 0.04 * noise(vec2(p.x * 2.5, 1.0)) + 0.02 * noise(vec2(p.x * 9.0, 2.0));
    color = mix(color, night * 0.5, smoothstep(0.004, -0.004, p.y - ridge));
    color += (hash12(at * size + fract(t) * 91.0) - 0.5) / 255.0;
    return vec4(color, 1.0);
}
