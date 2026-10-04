package com.fantasyweapons.client.vfx;

/**
 * A client-side visual effect object. Never an entity: lives only in {@link VfxManager}, is ticked at 20 Hz and
 * rendered every frame with interpolation. Gameplay never depends on it.
 */
public abstract class Vfx {
    protected int age;
    protected int lifetime;
    protected boolean dead;

    protected Vfx(int lifetime) {
        this.lifetime = Math.max(1, lifetime);
    }

    public void tick() {
        if (++age >= lifetime) dead = true;
    }

    public abstract void render(VfxContext ctx);

    /** Life progress in [0, 1] interpolated with the partial tick. */
    public float progress(float partial) {
        return Math.min(1f, (age + partial) / lifetime);
    }

    public boolean isDead() {
        return dead;
    }

    public void kill() {
        dead = true;
    }

    // ---- easing helpers used by effects ----

    public static float easeOut(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public static float easeIn(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** 0 → 1 → 0 bump, peaking at {@code peak}. */
    public static float bump(float t, float peak) {
        t = clamp01(t);
        return t < peak ? t / peak : 1 - (t - peak) / (1 - peak);
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }
}
