package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;

/**
 * An effect drawn by a callback: the mage's spells each have a look of their own (a wall of flame, a whirlpool, a
 * curse sigil...), composed from the same drawing primitives, without a class for every one.
 */
public class SpellVfx extends Vfx {
    /** Draws one frame: {@code t} is life progress 0..1, {@code ticks} the age in ticks with the partial tick. */
    @FunctionalInterface
    public interface Draw {
        void draw(VfxContext ctx, float t, float ticks);
    }

    private final Draw draw;

    public SpellVfx(int lifetime, Draw draw) {
        super(lifetime);
        this.draw = draw;
    }

    @Override
    public void render(VfxContext ctx) {
        draw.draw(ctx, progress(ctx.partial), age + ctx.partial);
    }

    /** 0 → 1 over the first {@code in} of the life and 1 → 0 over the last {@code out} (fractions of life). */
    public static float envelope(float t, float in, float out) {
        float a = in <= 0 ? 1 : Math.min(1, t / in);
        float b = out <= 0 ? 1 : Math.min(1, (1 - t) / out);
        return Math.max(0, Math.min(a, b));
    }
}
