#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 src = texture(InSampler, texCoord);
    vec3 c = src.rgb;

    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 desat = mix(vec3(luma), c, 0.52);

    // Distance-like screen vignette gradient: keep center/foreground brighter, deepen edges & sky into Hachimi dark-purple noir
    float radial = length(texCoord - vec2(0.5, 0.55)) * 1.35;
    float grad = clamp(smoothstep(0.18, 1.05, radial) * 0.78 + 0.22, 0.0, 1.0);

    vec3 shadowTint = vec3(0.06, 0.02, 0.11);
    vec3 midTint = vec3(0.36, 0.17, 0.52);
    vec3 highTint = vec3(0.84, 0.70, 0.95);

    vec3 graded = mix(shadowTint, midTint, smoothstep(0.02, 0.45, luma));
    graded = mix(graded, highTint, smoothstep(0.45, 1.0, luma));

    vec3 outCol = mix(c * 0.88, mix(desat * 0.75, graded, 0.52), grad);
    outCol = pow(max(outCol, vec3(0.0)), vec3(1.06));

    fragColor = vec4(clamp(outCol, 0.0, 1.0), src.a);
}
