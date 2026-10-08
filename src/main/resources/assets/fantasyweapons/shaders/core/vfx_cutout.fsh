#version 150

#moj_import <fog.glsl>

// Fantasy Weapons: modelled props cut out by their texture's alpha (petals, leaves): crisp natural outlines, the
// texture's grey shading multiplied by the vertex colour, which also carries the light.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.5) discard;
    vec4 color = vec4(vertexColor.rgb * tex.rgb, vertexColor.a) * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
