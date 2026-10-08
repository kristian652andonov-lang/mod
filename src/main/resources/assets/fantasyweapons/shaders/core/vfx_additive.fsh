#version 150

#moj_import <fog.glsl>

// Fantasy Weapons: plain additive glow. Texture alpha shapes the glow, vertex colour tints it.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// geometry closer than ~2.5 blocks fades out so effects on/around the viewer never blind them
float nearFade() {
    return smoothstep(0.35, 2.5, vertexDistance);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    float a = tex.a * vertexColor.a;
    if (a < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * tex.rgb, a) * ColorModulator * linear_fog_fade(vertexDistance, FogStart, FogEnd) * nearFade();
}
