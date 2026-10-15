#version 150

// Holds the frame an impact landed on: copies the live frame while capturing, otherwise keeps the one held.
uniform sampler2D DiffuseSampler;
uniform sampler2D HeldSampler;
uniform float Capture;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = Capture > 0.5 ? texture(DiffuseSampler, texCoord) : texture(HeldSampler, texCoord);
}
