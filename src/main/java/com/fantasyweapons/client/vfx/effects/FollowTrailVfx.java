package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A ribbon trail that follows a moving point (projectile, thrown weapon, entity). The head is sampled every tick;
 * when the source returns null the trail stops growing and fades out.
 */
public class FollowTrailVfx extends Vfx {
    private final Supplier<Vec3> head;
    private final float width;
    private final int colorHead;
    private final int colorTail;
    private final int maxPoints;
    private ResourceLocation texture = VfxTextures.STREAK;
    private boolean energy = true;
    private float headGlow;
    private int headGlowColor;
    private final Deque<Vec3> points = new ArrayDeque<>();
    private Vec3 prevHead;
    private Vec3 curHead;
    private int fadeTicks = -1;

    public FollowTrailVfx(Supplier<Vec3> head, float width, int colorHead, int colorTail, int maxPoints, int maxLifetime) {
        super(maxLifetime);
        this.head = head;
        this.width = width;
        this.colorHead = colorHead;
        this.colorTail = colorTail;
        this.maxPoints = Math.max(2, maxPoints);
        Vec3 h = head.get();
        if (h != null) {
            points.addFirst(h);
            prevHead = curHead = h;
        }
    }

    public FollowTrailVfx texture(ResourceLocation tex, boolean energy) {
        this.texture = tex;
        this.energy = energy;
        return this;
    }

    public FollowTrailVfx headGlow(float size, int color) {
        this.headGlow = size;
        this.headGlowColor = color;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (fadeTicks >= 0) {
            if (++fadeTicks > maxPoints || points.size() <= 1) dead = true;
            if (!points.isEmpty()) points.removeLast();
            return;
        }
        Vec3 h = head.get();
        if (h == null) {
            fadeTicks = 0;
            return;
        }
        prevHead = curHead == null ? h : curHead;
        curHead = h;
        points.addFirst(h);
        while (points.size() > maxPoints) points.removeLast();
    }

    @Override
    public void render(VfxContext ctx) {
        if (points.size() < 2) return;
        Vec3[] pts = points.toArray(new Vec3[0]);
        if (fadeTicks < 0 && prevHead != null && curHead != null) pts[0] = prevHead.lerp(curHead, ctx.partial);
        float fade = fadeTicks < 0 ? 1 : 1 - Math.min(1, (fadeTicks + ctx.partial) / (float) maxPoints);
        float[] w = new float[pts.length];
        int[] c = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            float t = i / (float) (pts.length - 1);
            w[i] = width * (1 - t * 0.9f);
            int col = Colors.lerp(colorHead, colorTail, t);
            c[i] = Colors.alpha(fade * (1 - t) * ((col >>> 24) & 255) / 255f, col);
        }
        float scroll = -(age + ctx.partial) * 0.15f;
        ctx.ribbon(energy ? ctx.energy(texture) : ctx.additive(texture), pts, w, c, scroll, 0.2f);
        if (headGlow > 0 && fadeTicks < 0) ctx.billboard(ctx.additive(VfxTextures.GLOW), pts[0], headGlow, 0, headGlowColor);
    }
}
