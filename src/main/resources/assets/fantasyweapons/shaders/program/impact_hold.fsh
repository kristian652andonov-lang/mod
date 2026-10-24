#version 150

// Holds the frame an impact landed on: copies the live frame while capturing, otherwise keeps the one held.
uniform sampler2D DiffuseSampler;
uniform sampler2D HeldSampler;
uniform float Capture;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // opaque: where only the clear colour was drawn (the sky below the horizon) the frame's alpha is 0, and the
    // alpha-blended blit that keeps the held copy would turn it black
    vec3 c = Capture > 0.5 ? texture(DiffuseSampler, texCoord).rgb : texture(HeldSampler, texCoord).rgb;
    fragColor = vec4(c, 1.0);
}
