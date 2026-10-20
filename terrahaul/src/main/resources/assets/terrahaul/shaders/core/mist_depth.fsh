#version 150

// Terrahaul mist, depth pass (Fabulous graphics only). With Fabulous graphics the water and the
// particles are drawn into separate layers that are sorted by depth when they are put together.
// Mist is drawn without depth writes so its puffs blend into one cloud, which would leave its
// layer at the depth of the rock behind it and put the water in front of all of it. This pass
// writes the depth of the mist (colour writes are off) wherever it can be seen and is in front of
// the water layer, whose depth is in Sampler3; mist behind the water keeps the depth that is
// already there, so the water is laid over it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

uniform vec4 ColorModulator;
uniform vec2 ScreenSize;

in vec2 texCoord0;
in float vertexAlpha;

out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, texCoord0).a * vertexAlpha * ColorModulator.a;
    if (alpha < 0.02) {
        discard;
    }
    if (gl_FragCoord.z > texture(Sampler3, gl_FragCoord.xy / ScreenSize).r) {
        discard;
    }
    fragColor = vec4(1.0, 1.0, 1.0, alpha);
}
