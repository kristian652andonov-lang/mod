#version 150

// Vanilla rendertype_cutout vertex shader with Terrahaul vegetation sway.
//
// Plant models marked by Terrahaul carry a sway height in the alpha of their vertex colour
// (255 - height * 48, see SwayBakedModel); every other block keeps alpha 255 and renders exactly
// as in vanilla. Marked vertices bend with the wind and get their alpha restored to 1.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

// xy: wind direction, z: strength (0 disables sway), w: time in seconds (wraps every 3600 s)
uniform vec4 TerrahaulWind;
// camera position with x and z wrapped to [0, 1024)
uniform vec3 TerrahaulCamera;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const float TAU = 6.2831853;
// whole cycles per 1024 blocks and per 3600 seconds, so both wraps are seamless
const float K = TAU / 1024.0;
const float W = TAU / 3600.0;

vec3 terrahaulSway(vec3 world, float height) {
    float strength = TerrahaulWind.z;
    vec2 dir = TerrahaulWind.xy;
    float t = TerrahaulWind.w * W;
    float wave = sin(world.x * K * 37.0 + world.z * K * 23.0 + t * 1150.0) * 0.55
               + sin(world.x * K * 89.0 - world.z * K * 61.0 + t * 1900.0) * 0.30
               + sin(world.x * K * 151.0 + world.z * K * 173.0 + t * 3300.0) * 0.15;
    float gust = 0.55 + 0.45 * sin(world.x * K * 7.0 + world.z * K * 11.0 + t * 260.0);
    // tips move most; a two-block plant bends further than a one-block one
    float bend = height * sqrt(height);
    float lean = strength * (0.05 + 0.05 * gust);
    float flutter = (0.025 + strength * 0.04) * wave;
    vec2 along = dir * (lean + flutter) * bend;
    vec2 across = vec2(-dir.y, dir.x) * (0.015 + 0.02 * strength) * bend
                * sin(world.x * K * 131.0 - world.z * K * 97.0 + t * 2700.0);
    vec2 offset = along + across;
    // dip the tip slightly as it bends so stems keep roughly their length
    float drop = -dot(offset, offset) * 0.5 / max(height, 0.25);
    return vec3(offset.x, drop, offset.y);
}

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 color = Color;
    float marker = floor((1.0 - Color.a) * 255.0 + 0.5);
    if (marker > 0.5) {
        color.a = 1.0;
        // sky light 7..13 -> 0..1: plants in caves and under overhangs stay still
        float sky = clamp((float(UV2.y) / 16.0 - 7.0) / 6.0, 0.0, 1.0);
        float fade = 1.0 - smoothstep(40.0, 64.0, length(pos));
        if (TerrahaulWind.z > 0.0 && sky * fade > 0.0) {
            pos += terrahaulSway(pos + TerrahaulCamera, marker / 48.0) * (sky * fade);
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
