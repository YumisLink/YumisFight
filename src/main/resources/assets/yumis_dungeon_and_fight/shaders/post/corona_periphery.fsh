#version 330

uniform sampler2D OriginalSampler;
uniform sampler2D BlurredSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 OriginalSize;
    vec2 BlurredSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Pixel distance keeps the protected center circular at every aspect ratio.
    float radius = length((texCoord - vec2(0.5)) * OutSize) / min(OutSize.x, OutSize.y);
    float blurAmount = smoothstep(0.15, 0.30, radius);
    fragColor = mix(texture(OriginalSampler, texCoord), texture(BlurredSampler, texCoord), blurAmount);
}
