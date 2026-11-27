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

float sobelEdge(vec2 uv, vec2 texel) {
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
        // 1. SIN CITY COMIC NOIR: Selective Crimson Red + High-Contrast Ink + Manga Screentone
        float edge = sobelEdge(texCoord, texel);
        float lum = luma(base);
        float contrast = smoothstep(0.16, 0.84, lum);
        // Manga halftone screentone in mid-dark shadows
        vec2 grid = fract(texCoord * InSize * 0.25) - 0.5;
        float dotMask = length(grid);
        if (lum > 0.12 && lum < 0.48 && dotMask > lum * 0.95) {
            contrast *= 0.62;
        }
        vec3 noir = vec3(contrast);
        // Detect vivid red hues (roses, lava, TNT, crimson blocks, hearts)
        float redness = base.r - max(base.g, base.b) * 1.25;
        float keepRed = smoothstep(0.08, 0.26, redness);
        vec3 vividRed = vec3(min(1.0, base.r * 1.28), base.g * 0.35, base.b * 0.35);
        outCol = mix(noir, vividRed, keepRed);
        outCol *= (1.0 - smoothstep(0.22, 0.55, edge) * 0.92);
    } else if (Mode < 2.5) {
        // 2. BORDERLANDS / FORTNITE CEL-SHADE: Bold Sobel Ink Outlines + Posterized Vibrant Bands
        float edge = sobelEdge(texCoord, texel * 1.15);
        float lum = luma(base);
        vec3 sat = mix(vec3(lum), base, 1.48);
        vec3 quantized = floor(sat * 6.0 + 0.5) / 6.0;
        vec3 cel = mix(sat, quantized, 0.58);
        float ink = smoothstep(0.20, 0.52, edge);
        outCol = mix(cel * 1.06, vec3(0.03, 0.03, 0.05), ink * 0.92);
    } else if (Mode < 3.5) {
        // 3. JOJO BIZARRE PALETTE: Morioh/Part-3 Dramatic Split-Toning + Manga Edge Ink
        float edge = sobelEdge(texCoord, texel * 1.1);
        float lum = luma(base);
        vec3 shadowTint = vec3(0.22, 0.06, 0.38);
        vec3 midTint    = vec3(0.88, 0.28, 0.68);
        vec3 highTint   = vec3(1.00, 0.88, 0.32);
        vec3 graded = lum < 0.5 ? mix(shadowTint, midTint, lum * 2.0) : mix(midTint, highTint, (lum - 0.5) * 2.0);
        vec3 boosted = mix(vec3(lum), base, 1.55);
        outCol = mix(boosted, graded, 0.52);
        float ink = smoothstep(0.22, 0.54, edge);
        outCol = mix(outCol, vec3(0.06, 0.01, 0.12), ink * 0.90);
    } else if (Mode < 4.5) {
        // 4. CYBERPUNK NEON: Chromatic Aberration + Cyan/Magenta Grading + Edge Glow
        vec2 dir = (texCoord - 0.5) * 0.0045;
        float rCh = texture(InSampler, texCoord + dir).r;
        float gCh = base.g;
        float bCh = texture(InSampler, texCoord - dir).b;
        vec3 ca = vec3(rCh, gCh, bCh);
        float lum = luma(ca);
        vec3 neon = mix(vec3(0.04, 0.10, 0.24), vec3(0.15, 0.95, 1.0), smoothstep(0.1, 0.85, ca.b));
        vec3 mag = vec3(ca.r * 1.25, ca.g * 0.88, min(1.0, ca.b * 1.32 + 0.06));
        outCol = mix(mag, neon, 0.22);
        float edge = sobelEdge(texCoord, texel);
        outCol += vec3(0.0, 0.55, 0.85) * smoothstep(0.35, 0.85, edge) * 0.35;
        outCol = mix(vec3(lum), outCol, 1.32);
    } else if (Mode < 5.5) {
        // 5. VINTAGE CINEMA FILM: Teal-Orange 35mm Film Grading + Subtle Grain + Vignette
        float lum = luma(base);
        vec3 shadows = vec3(0.04, 0.14, 0.19);
        vec3 highlights = vec3(1.04, 0.88, 0.70);
        vec3 film = mix(shadows, highlights, smoothstep(0.04, 0.96, lum));
        outCol = mix(base, film, 0.42);
        float grain = fract(sin(dot(texCoord * InSize, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        outCol += grain * 0.035;
        float dist = distance(texCoord, vec2(0.5));
        outCol *= smoothstep(0.95, 0.32, dist);
    } else {
        // 6. RETRO CRT CINEMA: Curved Screen + Phosphor Scanlines + Subpixel Warmth
        vec2 cc = texCoord - 0.5;
        vec2 warped = texCoord + cc * dot(cc, cc) * 0.10;
        if (warped.x < 0.0 || warped.x > 1.0 || warped.y < 0.0 || warped.y > 1.0) {
            outCol = vec3(0.0);
        } else {
            vec3 crt = vec3(
                texture(InSampler, warped + vec2(texel.x * 0.8, 0.0)).r,
                texture(InSampler, warped).g,
                texture(InSampler, warped - vec2(texel.x * 0.8, 0.0)).b
            );
            float scan = 0.84 + 0.16 * sin(warped.y * InSize.y * 3.14159);
            outCol = crt * scan * 1.12;
            float dist = distance(warped, vec2(0.5));
            outCol *= smoothstep(0.92, 0.38, dist);
        }
    }

    fragColor = vec4(clamp(mix(base, outCol, Strength), 0.0, 1.0), 1.0);
}
