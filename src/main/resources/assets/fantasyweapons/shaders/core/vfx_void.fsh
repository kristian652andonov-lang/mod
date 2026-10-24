#version 150

#moj_import <fog.glsl>

// Fantasy Weapons: dimensional void interior (alpha blended, NOT additive, so it can be truly dark).
// UVs are expected to span 0..1 across the rift; a swirling noise field, faint purple nebula and twinkling star
// specks fill it, with a bright rim near the shape's edge.
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
    float t = GameTime * 1200.0;
    vec2 c = texCoord0 - 0.5;
    float r = length(c) * 2.0;
    float ang = atan(c.y, c.x) / 6.2831853;
    float swirl = texture(Sampler1, vec2(ang * 2.0 + t * 0.06 + r * 0.35, r * 0.6 - t * 0.12)).r;
    float neb = texture(Sampler1, texCoord0 * 1.3 + vec2(t * 0.02, t * 0.035)).r;
    vec3 deep = vec3(0.012, 0.0, 0.03);
    vec3 tint = vertexColor.rgb;
    vec3 col = deep + tint * (0.18 * swirl * swirl + 0.22 * neb * neb * smoothstep(0.2, 1.0, r));
    // point stars: one in a few cells of a fine grid, each twinkling on its own
    vec2 grid = texCoord0 * vec2(26.0, 52.0);
    vec2 cell = floor(grid);
    float h = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
    float star = step(0.92, h) * smoothstep(0.32, 0.0, length(fract(grid) - 0.5));
    float twinkle = 0.55 + 0.45 * sin(t * 3.0 + h * 60.0);
    col += vec3(0.9, 0.82, 1.0) * star * twinkle;
    vec4 shape = texture(Sampler0, texCoord0);
    float rim = smoothstep(0.55, 1.0, r) * shape.a;
    col += tint * rim * 1.3;
    float a = shape.a * vertexColor.a;
    if (a < 0.002) discard;
    fragColor = vec4(col, a) * ColorModulator * linear_fog_fade(vertexDistance, FogStart, FogEnd) * nearFade();
}
