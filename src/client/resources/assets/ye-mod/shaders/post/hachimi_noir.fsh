#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 src = texture(InSampler, texCoord);
    vec3 c = src.rgb;

    // Luminance & dark-purple oppressive Hachimi Noir color grading
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 desat = mix(vec3(luma), c, 0.42);

    // Deep obsidian-violet shadow crush + moody purple midtones
    vec3 shadowTint = vec3(0.05, 0.015, 0.09);
    vec3 midTint = vec3(0.32, 0.14, 0.46);
    vec3 highTint = vec3(0.76, 0.62, 0.90);

    vec3 graded = mix(shadowTint, midTint, smoothstep(0.02, 0.48, luma));
    graded = mix(graded, highTint, smoothstep(0.48, 1.0, luma));

    // Blend with darkened desaturated original to keep block/texture clarity
    vec3 outCol = mix(desat * 0.68, graded, 0.58);
    // Subtle oppressive contrast curve
    outCol = pow(max(outCol, vec3(0.0)), vec3(1.12));

    fragColor = vec4(clamp(outCol, 0.0, 1.0), src.a);
}
