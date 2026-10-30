// Dragon eye — a slowly breathing reptilian iris whose slit pupil follows the pointer.
// From newm's "Jadefire" look. Settings (values): speed, intensity, eyeSize (the eye's
// height on the screen, 0.2..0.9), blink (0: never). Colours: colors[0] jade, colors[1]
// amber, colors[2] ember, colors[3] the dark around it.

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm3(vec2 p) {
    float v = 0.5 * noise(p);
    v += 0.25 * noise(p * 2.03 + 1.7);
    v += 0.125 * noise(p * 4.1 + 9.2);
    return v / 0.875;
}

vec3 tone(int i, vec3 fallback) { return colorCount > i ? colors[i].rgb : fallback; }

vec4 wallpaper(vec2 at) {
    vec3 jade = tone(0, vec3(0.239, 0.863, 0.592));
    vec3 amber = tone(1, vec3(0.949, 0.702, 0.239));
    vec3 ember = tone(2, vec3(0.910, 0.333, 0.176));
    vec3 dark = tone(3, vec3(0.024, 0.035, 0.031));
    float t = time * speed;
    float aspect = size.x / max(size.y, 1.0);
    // In the eye's own units: its middle at 0, its height about 1.
    vec2 p = (at - 0.5) * vec2(aspect, 1.0) / clamp(eyeSize, 0.1, 1.5);
    float breath = 0.5 + 0.5 * sin(t * 0.45);

    // The lids: an almond, blinking now and then.
    float cycle = mod(t, 13.0);
    float lid = blink > 0.0 ? 1.0 - smoothstep(0.0, 0.2, 0.2 - abs(cycle - 0.2)) * 0.97 : 1.0;
    float open = 0.58 * lid;
    float almond = abs(p.y) - open * max(1.0 - pow(abs(p.x) / 1.02, 2.0), 0.0) + max(abs(p.x) - 1.02, 0.0);
    float inside = 1.0 - smoothstep(-0.008, 0.012, almond);

    // Around: scales in the dark, a warm halo round the eye.
    vec2 s = p * vec2(7.0, 10.0);
    float row = floor(s.y);
    s.x += mod(row, 2.0) * 0.5;
    vec2 cell = fract(s) - vec2(0.5, 0.0);
    float scale = smoothstep(0.06, 0.0, abs(length(cell) - 0.5)) * step(0.0, cell.y) * (0.5 + hash12(floor(s)));
    float halo = exp(-max(almond, 0.0) * 4.5);
    vec3 color = dark + jade * 0.06 * scale * (0.25 + halo) + ember * 0.16 * halo * (0.6 + 0.4 * breath) * intensity;
    color += amber * exp(-abs(almond) * 120.0) * 0.35 * intensity;

    if (almond < 0.02) {
        // Where the pupil looks: towards the pointer, a little; at rest it drifts.
        vec2 look = vec2(sin(t * 0.13) * 0.06, cos(t * 0.11) * 0.03);
        if (pointer.x >= 0.0) {
            vec2 toPointer = (pointer - 0.5) * vec2(aspect, 1.0);
            look = toPointer / (length(toPointer) + 0.6) * 0.16;
        }
        vec2 q = p - look;
        float r = length(q);
        vec2 dir = q / max(r, 1e-4);
        float irisR = 0.47;
        float d = r / irisR;

        // The eye's dark wet surface with fine red veins, lit by the iris.
        float veins = pow(1.0 - abs(noise(p * 5.0 + 3.0) * 2.0 - 1.0), 8.0);
        vec3 eye = dark * 2.2 + ember * (0.05 + 0.18 * veins) * smoothstep(1.0, 0.45, abs(p.x));
        eye += mix(ember, amber, 0.5) * 0.22 * exp(-max(r - irisR, 0.0) * 14.0) * (0.6 + 0.4 * noise(dir * 5.0 + t * 0.5)) * intensity;

        if (d < 1.05) {
            // The slit: a lens pointed at both ends, wider and narrower as it breathes; a quick pointer narrows it.
            float startle = clamp(length(pointerVelocity) * 1.5, 0.0, 1.0);
            float slitWidth = mix(0.022, 0.07, breath) * (1.0 - 0.55 * startle);
            float halfWidth = slitWidth * max(1.0 - pow(q.y / 0.44, 2.0), 0.0);
            float slit = abs(q.x) - halfWidth;
            float fromSlit = max(slit, 0.0) + max(abs(q.y) - 0.44, 0.0);

            // Fibres along the radius (a circle of noise: no seam), crypts, slowly turning.
            vec2 swirl = dir * (7.0 + r * 3.0);
            float fibres = fbm3(swirl + vec2(t * 0.03, 0.0));
            float fine = noise(dir * 38.0 + r * 4.0);
            float crypts = smoothstep(0.62, 0.82, noise(dir * 13.0 + r * 16.0 + 4.0));

            // Deep emerald at the rim, jade, melting into gold-amber round the slit.
            vec3 emerald = jade * vec3(0.18, 0.42, 0.40);
            vec3 iris = mix(jade * 0.95, emerald, smoothstep(0.35, 0.95, d));
            iris = mix(iris, amber * 1.15, clamp(exp(-fromSlit * 9.0) * (0.75 + 0.5 * fibres), 0.0, 1.0));
            iris *= (0.45 + 0.85 * fibres + 0.3 * fine) * (1.0 - 0.32 * crypts);
            // Light from the slit's edge, breathing with it.
            iris += mix(amber, vec3(1.0, 0.92, 0.65), 0.5) * exp(-fromSlit * 40.0) * (0.35 + 0.35 * breath) * intensity;
            // The upper lid's shadow, light from below; the rim dark, then a ring of fire.
            iris *= mix(0.55, 1.15, smoothstep(-0.45, 0.35, q.y));
            iris = mix(iris, dark, smoothstep(0.8, 1.0, d) * 0.8);
            float fire = exp(-pow((d - 0.985) * 14.0, 2.0)) * (0.45 + 0.55 * fbm3(dir * 6.0 + vec2(0.0, t * 0.35)));
            iris += mix(ember, amber, 0.25) * fire * 1.1 * intensity;
            // The pupil, black; a glint.
            float pupil = (1.0 - smoothstep(-0.004, 0.006, slit)) * (1.0 - smoothstep(0.43, 0.44, abs(q.y)));
            iris = mix(iris, vec3(0.0), pupil);
            vec2 g = q - vec2(-0.14, -0.19);
            iris += vec3(1.0, 0.97, 0.88) * (exp(-dot(g, g) * 1400.0) * 0.9 + exp(-dot(g, g) * 60.0) * 0.06);
            eye = mix(eye, iris, 1.0 - smoothstep(irisR - 0.004, irisR + 0.006, r));
        }
        // The lids' shadow along the opening.
        eye *= smoothstep(0.0, -0.2, almond) * 0.8 + 0.2;
        color = mix(color, eye, inside);
    }
    // Vignette, and a little grain against banding.
    color *= 1.0 - 1.1 * dot(at - 0.5, at - 0.5);
    color += (hash12(at * size + fract(t) * 91.0) - 0.5) / 255.0;
    return vec4(color, 1.0);
}
