#version 150

// Impact frames: the held frame redrawn as a stark black-and-white still - a negative, black ink on white paper, or
// white light on black - with speed lines bursting from where the blow lands; then the picture snaps back, the colour
// flooding back into it.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;

uniform float Mode;      // 0 none (snapping back), 1 negative, 2 ink on paper, 3 white light on black,
                         // 4 ink in the power's colours, 5 the power's light on black
uniform float Strength;  // how much of the still is shown
uniform float Fade;      // the snap back: grey with a white wash, 1 -> 0, as the colour returns
uniform float Lines;     // speed line strength
uniform float CenterX;
uniform float CenterY;
uniform float Seed;
uniform float Zoom;      // the still pushed in about the burst (1 = as captured)
uniform float ShiftX;    // and nudged, a jolt between stills
uniform float ShiftY;
uniform float TintR;     // the power's colour, and the colour of its light (modes 4 and 5)
uniform float TintG;
uniform float TintB;
uniform float GlowR;
uniform float GlowG;
uniform float GlowB;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(float n) {
    return fract(sin(n * 12.9898 + 78.233) * 43758.5453);
}

float lumaAt(vec2 uv) {
    return luma(texture(DiffuseSampler, uv).rgb);
}

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(CenterX, CenterY);
    vec3 src = texture(DiffuseSampler, uv).rgb;

    // the snap back: the live picture still grey under a white wash, its colour flooding back in
    if (Mode < 0.5) {
        vec3 grey = vec3(luma(src));
        vec3 washed = mix(grey, vec3(1.0), 0.18 * Fade * Fade);
        fragColor = vec4(mix(src, washed, Strength), 1.0);
        return;
    }

    // the still itself: the held frame pushed in about the burst and nudged a little, as each new still lands
    vec2 suv = clamp(center + (uv - center) / Zoom + vec2(ShiftX, ShiftY), oneTexel, 1.0 - oneTexel);
    src = texture(DiffuseSampler, suv).rgb;

    // radial speed lines: thin wedges at scattered angles, each starting at its own distance from the centre
    vec2 d = uv - center;
    d.x *= InSize.x / InSize.y;
    float r = length(d);
    float a = (atan(d.y, d.x) / 6.2831853 + 0.5) * 180.0;
    float id = floor(a);
    float f = fract(a);
    float keep = step(0.5, hash(id + Seed * 7.13));
    float width = 0.12 + 0.3 * hash(id * 1.31 + Seed);
    float start = 0.1 + 0.32 * hash(id * 2.71 + Seed * 3.1);
    float line = keep * (1.0 - smoothstep(width * 0.5, width * 0.5 + 0.08, abs(f - 0.5)))
            * smoothstep(start, start + 0.15, r) * Lines;

    // ink outlines from the edges of the picture
    float gx = lumaAt(suv + vec2(oneTexel.x, 0.0)) - lumaAt(suv - vec2(oneTexel.x, 0.0));
    float gy = lumaAt(suv + vec2(0.0, oneTexel.y)) - lumaAt(suv - vec2(0.0, oneTexel.y));
    float edge = smoothstep(0.08, 0.3, length(vec2(gx, gy)));
    float l = luma(src);
    float lit = smoothstep(0.26, 0.36, l);
    // the frame's edges darken towards the corners
    float vig = smoothstep(0.35, 1.1, r);

    vec3 still;
    if (Mode < 1.5) {
        // negative: the picture in grey turned inside out, its contrast pushed hard
        float inv = clamp((1.0 - l - 0.5) * 1.9 + 0.5, 0.0, 1.0);
        still = vec3(inv);
        still = mix(still, vec3(0.0), line);
        still *= 1.0 - 0.5 * vig;
    } else if (Mode < 2.5) {
        // ink on paper: the lit world white, the dark black, edges inked, black speed lines
        still = mix(vec3(0.03), vec3(1.0), lit);
        still = mix(still, vec3(0.0), edge);
        still = mix(still, vec3(0.0), line);
        still = mix(still, vec3(0.0), vig * 0.6);
    } else if (Mode < 3.5) {
        // light on black: the shadows burning white against black, white edges and speed lines of white light
        still = mix(vec3(0.94), vec3(0.0), lit);
        still = mix(still, vec3(1.0), edge);
        still = mix(still, vec3(1.0), line);
        still *= 1.0 - 0.6 * vig;
    } else if (Mode < 4.5) {
        // ink in the power's colours: the lit world in its light, the dark in deep shades of its colour, inked edges
        vec3 tint = vec3(TintR, TintG, TintB), glow = vec3(GlowR, GlowG, GlowB);
        vec3 deep = tint * 0.12;
        still = mix(deep, mix(tint, glow, smoothstep(0.45, 0.85, l)), lit);
        still = mix(still, deep * 0.5, edge);
        still = mix(still, deep * 0.4, line);
        still = mix(still, deep * 0.3, vig * 0.6);
    } else {
        // the power's light on black: the shadows burning in its colour, edges and speed lines of its light
        vec3 tint = vec3(TintR, TintG, TintB), glow = vec3(GlowR, GlowG, GlowB);
        still = mix(tint * 1.15, tint * 0.04, lit);
        still = mix(still, glow, edge);
        still = mix(still, glow, line);
        still *= 1.0 - 0.6 * vig;
    }
    fragColor = vec4(mix(texture(DiffuseSampler, uv).rgb, clamp(still, 0.0, 1.0), Strength), 1.0);
}
