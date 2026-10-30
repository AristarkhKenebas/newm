// Jadefire border: jade flowing into amber along the border, a hot spark travelling round it;
// an urgent window's border pulses ember. colors[0]: the jade (focus.color), colors[1]: urgent.
vec4 paint(vec2 at) {
    vec3 jade = vec3(0.239, 0.863, 0.592);
    vec3 emerald = vec3(0.075, 0.600, 0.373);
    vec3 amber = vec3(0.949, 0.702, 0.239);
    vec3 ember = vec3(0.910, 0.333, 0.176);
    float flow = fract(progress - time * 0.08);
    // Jade → amber → jade → emerald, twice round the window.
    float w = 0.5 + 0.5 * sin(flow * 6.2831853 * 2.0);
    vec3 c = mix(emerald, jade, smoothstep(0.0, 0.5, w));
    c = mix(c, amber, smoothstep(0.62, 1.0, w));
    // The spark: a short bright run going round.
    float d = abs(fract(progress - time * 0.15 + 0.5) - 0.5);
    c += vec3(1.0, 0.85, 0.55) * exp(-d * d * 4000.0) * 0.9;
    float a = 0.95;
    if (urgent > 0.5 && focused < 0.5) {
        float pulse = 0.5 + 0.5 * sin(time * 5.0);
        c = mix(ember, amber, pulse * 0.6);
        a = 0.7 + 0.3 * pulse;
    }
    return vec4(c, a);
}
