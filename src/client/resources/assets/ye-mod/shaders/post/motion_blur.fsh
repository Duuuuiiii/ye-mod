#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform BlurConfig {
    float BlendFactor;
};

out vec4 fragColor;

void main() {
    vec4 curr = texture(InSampler, texCoord);
    vec4 prev = texture(PrevSampler, texCoord);
    // Guard uninitialized transparent black history frame on first render
    if (prev.a < 0.01) {
        fragColor = vec4(curr.rgb, 1.0);
    } else {
        fragColor = vec4(mix(curr.rgb, prev.rgb, BlendFactor), 1.0);
    }
}
