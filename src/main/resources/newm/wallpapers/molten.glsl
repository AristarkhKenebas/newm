// Molten — slow lava-lamp blobs of amber and ember that melt together and part again, edged
// with a jade glow; the pointer is a warm blob of its own. From newm's "Jadefire" look.
// Settings (values): speed, intensity, blobs (how many, 3..10). Colours: colors[0] jade,
// colors[1] amber, colors[2] ember, colors[3] the dark.

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
    float t = time * speed * 0.12;
    float aspect = size.x / max(size.y, 1.0);
    vec2 p = (at - 0.5) * vec2(aspect, 1.0);

    // The field: each blob adds r² / d²; where it passes 1 is inside.
    float field = 0.0;
    int count = int(clamp(blobs, 3.0, 10.0));
    for (int i = 0; i < 10; i++) {
        if (i >= count) break;
        float fi = float(i);
        vec2 c = vec2(sin(t * (0.7 + 0.13 * fi) + fi * 2.4) * 0.45 * aspect,
                      sin(t * (0.5 + 0.11 * fi) + fi * 1.3) * 0.38);
        float r = 0.075 + 0.04 * sin(fi * 3.7) + 0.015 * sin(t * 2.0 + fi);
        vec2 d = p - c;
        field += r * r / (dot(d, d) + 1e-4);
    }
    if (pointer.x >= 0.0) {
        vec2 d = p - (pointer - 0.5) * vec2(aspect, 1.0);
        field += 0.0049 / (dot(d, d) + 1e-4);
    }
    // Background: dark, warmer near the blobs.
    vec3 color = dark * (1.0 + 0.6 * (1.0 - at.y));
    color += ember * 0.10 * smoothstep(0.2, 1.0, field) * intensity;
    // Outside, a jade halo hugging the skin.
    color += jade * 0.30 * exp(-max(1.0 - field, 0.0) * 7.0) * step(field, 1.0) * intensity;
    // Inside: a soft surface lit from above, ember at the skin, amber deeper in.
    float h = clamp(1.0 - 1.0 / max(field, 1e-3), 0.0, 1.0);
    vec2 slope = vec2(dFdx(h), dFdy(h)) * size.y * 0.02;
    vec3 n = normalize(vec3(-slope, 1.0));
    float lit = clamp(dot(n, normalize(vec3(-0.3, -0.6, 0.75))), 0.0, 1.0);
    vec3 hot = mix(ember * 0.65, amber, smoothstep(0.0, 0.75, h));
    hot *= 0.55 + 0.6 * lit;
    hot += vec3(1.0, 0.92, 0.75) * 0.35 * pow(lit, 24.0) * smoothstep(0.1, 0.4, h);
    // Its rim, glowing jade where it meets the dark.
    hot = mix(jade * 0.9, hot, smoothstep(0.0, 0.12, h));
    float inside = smoothstep(0.97, 1.03, field);
    color = mix(color, hot * intensity, inside);
    // Vignette, and a little grain against banding.
    color *= 1.0 - 1.0 * dot(at - 0.5, at - 0.5);
    color += (hash12(at * size + fract(time) * 91.0) - 0.5) / 255.0;
    return vec4(color, 1.0);
}
