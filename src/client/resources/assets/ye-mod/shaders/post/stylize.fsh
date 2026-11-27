#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FilterConfig {
    float Mode;
    float Strength;
};

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float smoothEdge(vec2 uv, vec2 texel) {
    float tl = luma(texture(InSampler, uv + vec2(-texel.x,  texel.y)).rgb);
    float  l = luma(texture(InSampler, uv + vec2(-texel.x,  0.0)).rgb);
    float bl = luma(texture(InSampler, uv + vec2(-texel.x, -texel.y)).rgb);
    float  t = luma(texture(InSampler, uv + vec2( 0.0,      texel.y)).rgb);
    float  b = luma(texture(InSampler, uv + vec2( 0.0,     -texel.y)).rgb);
    float tr = luma(texture(InSampler, uv + vec2( texel.x,  texel.y)).rgb);
    float  r = luma(texture(InSampler, uv + vec2( texel.x,  0.0)).rgb);
    float br = luma(texture(InSampler, uv + vec2( texel.x, -texel.y)).rgb);
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -bl - 2.0 * b - br + tl + 2.0 * t + tr;
    return length(vec2(gx, gy));
}

void main() {
    vec2 texel = 1.0 / InSize;
    vec3 base = texture(InSampler, texCoord).rgb;
    vec3 outCol = base;

    if (Mode < 1.5) {
        // Mode 1: SIN_CITY (黑白留红 — ZERO moiré halftone dots! Pure cinema silver-noir + vivid scarlet)
        float lum = luma(base);
        float noirCurve = smoothstep(0.08, 0.92, lum);
        vec3 mono = vec3(noirCurve * 0.96, noirCurve * 0.97, min(1.0, noirCurve * 1.02));
        float redness = base.r - max(base.g, base.b) * 1.22;
        float redMask = smoothstep(0.07, 0.24, redness);
        vec3 scarlet = vec3(min(1.0, base.r * 1.25), base.g * 0.28, base.b * 0.28);
        outCol = mix(mono, scarlet, redMask);
    } else if (Mode < 2.5) {
        // Mode 2: NATURAL CEL-SHADE (原色漫描 — preserves 100% original world colors + soft anti-aliased ink!)
        float edge = smoothEdge(texCoord, texel);
        vec3 blur = (
            texture(InSampler, texCoord + vec2( texel.x,  texel.y)).rgb +
            texture(InSampler, texCoord + vec2(-texel.x,  texel.y)).rgb +
            texture(InSampler, texCoord + vec2( texel.x, -texel.y)).rgb +
            texture(InSampler, texCoord + vec2(-texel.x, -texel.y)).rgb
        ) * 0.25;
        vec3 smoothCol = mix(base, blur, 0.35);
        float ink = smoothstep(0.28, 0.72, edge);
        outCol = mix(smoothCol * 1.04, smoothCol * 0.14, ink * 0.82);
    } else if (Mode < 3.5) {
        // Mode 3: DREAMY HDR BLOOM (梦幻柔光 — multi-ring bright-pass glow without altering world hues!)
        vec3 glow = vec3(0.0);
        float totalW = 0.0;
        for (int i = 0; i < 12; i++) {
            float ang = float(i) * 2.3999632; // Golden angle
            float rad = (1.5 + float(i) * 1.35);
            vec2 off = vec2(cos(ang), sin(ang)) * texel * rad;
            vec3 s = texture(InSampler, texCoord + off).rgb;
            float bright = smoothstep(0.45, 0.95, max(s.r, max(s.g, s.b)));
            float w = 1.0 - float(i) * 0.05;
            glow += s * (0.35 + bright * 1.35) * w;
            totalW += w;
        }
        glow /= totalW;
        outCol = base + glow * 0.34;
        outCol = outCol / (1.0 + outCol * 0.24); // Smooth ACES-like highlight rolloff
    } else if (Mode < 4.5) {
        // Mode 4: SPEED RADIAL DISPERSION (极速动态视觉 — crystal sharp center + peripheral radial motion & prism)
        vec2 delta = texCoord - vec2(0.5);
        float dist = length(delta);
        float edgeFactor = smoothstep(0.16, 0.72, dist);
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float scale = 1.0 - float(i) * 0.0085 * edgeFactor;
            vec2 uvR = vec2(0.5) + delta * (scale + 0.004 * edgeFactor);
            vec2 uvG = vec2(0.5) + delta * scale;
            vec2 uvB = vec2(0.5) + delta * (scale - 0.004 * edgeFactor);
            acc.r += texture(InSampler, uvR).r;
            acc.g += texture(InSampler, uvG).g;
            acc.b += texture(InSampler, uvB).b;
        }
        outCol = acc / 6.0;
    } else if (Mode < 5.5) {
        // Mode 5: CINEMA BOKEH DOF (光学景深虚化 — razor sharp center focus + creamy peripheral disc bokeh)
        float dist = distance(texCoord, vec2(0.5));
        float coc = smoothstep(0.22, 0.78, dist) * 4.5;
        if (coc < 0.05) {
            outCol = base;
        } else {
            vec3 bokeh = base;
            for (int i = 1; i <= 10; i++) {
                float ang = float(i) * 2.3999632;
                float r = sqrt(float(i) / 10.0) * coc;
                bokeh += texture(InSampler, texCoord + vec2(cos(ang), sin(ang)) * texel * r).rgb;
            }
            outCol = bokeh / 11.0;
        }
        outCol *= mix(1.0, 0.82, smoothstep(0.45, 0.92, dist));
    } else {
        // Mode 6: ULTRA CAS SHARPEN & HDR CLARITY (超清锐化 — zero moiré scanlines! Crisp texture detail boost)
        vec3 n = texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb;
        vec3 s = texture(InSampler, texCoord - vec2(0.0, texel.y)).rgb;
        vec3 e = texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb;
        vec3 w = texture(InSampler, texCoord - vec2(texel.x, 0.0)).rgb;
        vec3 avg = (n + s + e + w) * 0.25;
        vec3 detail = base - avg;
        vec3 sharpened = base + clamp(detail * 0.85, -0.18, 0.18);
        float lum = luma(sharpened);
        outCol = mix(vec3(lum), sharpened, 1.18);
    }

    fragColor = vec4(clamp(mix(base, outCol, Strength), 0.0, 1.0), 1.0);
}
