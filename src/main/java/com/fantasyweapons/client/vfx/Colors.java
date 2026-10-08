package com.fantasyweapons.client.vfx;

/** ARGB colour helpers. */
public final class Colors {
    private Colors() {
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(float alpha, int rgb) {
        return argb(Math.round(alpha * 255), rgb);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24) & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24) & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Lerp RGB only, result fully opaque. */
    public static int lerpRgb(int a, int b, float t) {
        return lerp(a | 0xFF000000, b | 0xFF000000, t) & 0xFFFFFF;
    }

    public static int brighten(int rgb, float amount) {
        return lerpRgb(rgb, 0xFFFFFF, amount);
    }

    public static int darken(int rgb, float amount) {
        return lerpRgb(rgb, 0x000000, amount);
    }

    /** Multiplies the RGB channels by {@code k} (flat shading). */
    public static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    public static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    public static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    public static float b(int c) {
        return (c & 255) / 255f;
    }
}
