#version 150

#moj_import <fog.glsl>

// Fantasy Weapons: animated energy. Two counter-scrolling noise layers break up the shape texture so slashes,
// trails and beams look like living energy instead of flat sprites. Bright cores are pushed towards white.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// geometry closer than ~2.5 blocks fades out so effects on/around the viewer never blind them
float nearFade() {
    return smoothstep(0.35, 2.5, vertexDistance);
}

void main() {
    float t = GameTime * 1200.0; // seconds
    vec4 shape = texture(Sampler0, texCoord0);
    float n1 = texture(Sampler1, texCoord0 * vec2(1.7, 0.9) + vec2(-t * 0.85, t * 0.11)).r;
    float n2 = texture(Sampler1, texCoord0 * vec2(3.1, 1.7) + vec2(t * 0.47, -t * 0.29)).r;
    float energy = clamp(0.35 + 1.6 * n1 * n2 + 0.25 * n2, 0.0, 1.7);
    float a = shape.a * vertexColor.a * energy;
    if (a < 0.002) discard;
    float core = smoothstep(0.9, 1.25, shape.a * energy);
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), core * 0.35);
    fragColor = vec4(rgb, a) * ColorModulator * linear_fog_fade(vertexDistance, FogStart, FogEnd) * nearFade();
}
