package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aetherlance's Judgement Ray: a celestial sigil (a great rune circle ringed by four smaller ones) opens at the lance
 * tip, rune lenses line up down the line of fire, and the ray pours through them, a white-hot core inside an energy
 * body wound with twin gold and white helices, shedding sparks. Where it strikes, a burning rune mark, a starburst and
 * shock rings. It follows the caster's aim (re-traced against blocks every frame), swells in and fades out.
 */
public class JudgementRayVfx extends Vfx {
    private static final int SPARKS = 36;
    private final LivingEntity caster;
    private final double range;
    private final float radius;
    private final int color;
    private final int core;
    private final int gold;
    private final double tipReach;
    private final float[] sparkAngle = new float[SPARKS];
    private final float[] sparkPhase = new float[SPARKS];

    public JudgementRayVfx(LivingEntity caster, double range, float radius, int color, int core, int gold, double tipReach, int lifetime) {
        super(lifetime);
        this.caster = caster;
        this.range = range;
        this.radius = radius;
        this.color = color;
        this.core = core;
        this.gold = gold;
        this.tipReach = tipReach;
        for (int i = 0; i < SPARKS; i++) {
            sparkAngle[i] = (float) (i * 2.39996);
            sparkPhase[i] = (i * 0.61803f) % 1f;
        }
    }

    @Override
    public void render(VfxContext ctx) {
        if (!caster.isAlive()) {
            kill();
            return;
        }
        float time = age + ctx.partial;
        float in = easeOut(Math.min(1, time / 8f));
        float out = clamp01((lifetime - time) / 10f);
        float a = in * out;
        if (a <= 0.01f) return;
        Vec3 tip = WeaponAnchor.blade(caster, ctx.partial, tipReach);
        Vec3 look = caster.getViewVector(ctx.partial);
        Vec3 end = tip.add(look.scale(range));
        Vec3 hitNormal = null;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            HitResult hit = level.clip(new ClipContext(tip, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
                if (hit instanceof BlockHitResult bh) hitNormal = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            }
        }
        Vec3 d = end.subtract(tip);
        float len = (float) d.length();
        if (len < 0.5f) return;
        d = d.scale(1 / len);
        Vec3[] b = VfxContext.basis(d);
        // the beam swells in, breathes, and narrows to a thread as it fades
        float w = radius * 1.15f * (0.25f + 0.75f * in) * (0.35f + 0.65f * out) * (0.93f + 0.07f * (float) Math.sin(time * 1.7f));
        // seen through the caster's own eyes the sigil sits further out, smaller and fainter, so it frames the ray
        // instead of filling the screen
        var mc = Minecraft.getInstance();
        boolean ownView = caster == mc.player && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == caster;
        float near = ownView ? 0f : 1f;
        Vec3 start = tip.add(d.scale(ownView ? 3.0 : 0.6));
        Vec3 sigilAt = ownView ? tip.add(d.scale(4.5)) : start;

        // sigil and lenses: where they sit, how big, how far they have opened
        float sigilR = (1.1f + w * 1.3f) * (ownView ? 0.7f : 1f);
        float sigilOpen = easeOut(Math.min(1, time / 7f));
        float[] lensAt = {Math.min(len * 0.3f, 7f), Math.min(len * 0.55f, 14f)};
        float[] lensR = {sigilR * 0.62f, sigilR * 0.42f};
        float[] lensOpen = {easeOut(clamp01((time - 3) / 6f)), easeOut(clamp01((time - 6) / 6f))};

        // ---- soft glows ----
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.beam(glow, start, end, w * 2.6f, Colors.alpha(a * 0.2f * (ownView ? 0.6f : 1f), color), 0.5f, 0.5f);
        ctx.disc(glow, sigilAt, b[0], b[1], sigilR * 1.4f * sigilOpen, 0, Colors.alpha(a * 0.25f * near, color));
        ctx.billboard(glow, end, w * 5f, 0, Colors.alpha(a * 0.45f, color));

        // ---- the energy body of the ray ----
        float scroll = -time * 0.4f;
        ctx.beam(ctx.energy(VfxTextures.STREAK), start, end, w * 1.25f, Colors.alpha(a * 0.8f, color), scroll, scroll + len * 0.12f);

        // ---- rune circles: the sigil with its four orbiting circles, and the lenses down the line ----
        var runes = ctx.energy(VfxTextures.RUNE_CIRCLE);
        float sr = sigilR * sigilOpen;
        float sigA = ownView ? 0.55f : 1f;
        ctx.disc(runes, sigilAt, b[0], b[1], sr, time * 0.05f, Colors.alpha(a * sigA, gold));
        ctx.disc(runes, sigilAt.add(d.scale(0.05)), b[0], b[1], sr * 0.6f, -time * 0.08f, Colors.alpha(a * 0.9f * sigA, core));
        ctx.satellites(runes, sigilAt, b[0], b[1], sr * 1.18f, sr * 0.24f, 4, -time * 0.03f, time * 0.12f, Colors.alpha(a * 0.95f * sigA, color));
        for (int i = 0; i < 2; i++) {
            if (lensAt[i] < 1.5f || lensOpen[i] <= 0) continue;
            Vec3 at = tip.add(d.scale(lensAt[i]));
            float lr = lensR[i] * lensOpen[i];
            ctx.disc(runes, at, b[0], b[1], lr, (i == 0 ? -1 : 1) * time * 0.07f, Colors.alpha(a * 0.85f * lensOpen[i], i == 0 ? color : gold));
            ctx.satellites(runes, at, b[0], b[1], lr * 1.2f, lr * 0.22f, 3, (i == 0 ? 1 : -1) * time * 0.04f, time * 0.1f,
                    Colors.alpha(a * 0.8f * lensOpen[i], core));
        }
        if (hitNormal != null) {
            Vec3[] hb = VfxContext.basis(hitNormal);
            ctx.disc(runes, end.add(hitNormal.scale(0.03)), hb[0], hb[1], w * 2.4f + 0.6f, time * 0.06f, Colors.alpha(a * 0.9f, gold));
        }

        // ---- white-hot core ----
        ctx.beam(ctx.additive(VfxTextures.LIGHTNING), start, end, w * 0.42f, Colors.alpha(a * 0.95f, core), 0, 1);

        // ---- twin helices winding round the ray ----
        int n = Math.min(160, Math.max(8, (int) (len / 0.25f)));
        var helix = ctx.additive(VfxTextures.GLOW);
        for (int strand = 0; strand < 2; strand++) {
            Vec3[] pts = new Vec3[n + 1];
            float[] ws = new float[n + 1];
            int[] cs = new int[n + 1];
            for (int i = 0; i <= n; i++) {
                float s = i / (float) n;
                double ang = time * 0.35 - s * len * 1.5 + strand * Math.PI;
                float hr = w * (0.85f + 0.15f * (float) Math.sin(s * 12 + time * 0.2));
                pts[i] = start.lerp(end, s).add(b[0].scale(Math.cos(ang) * hr)).add(b[1].scale(Math.sin(ang) * hr));
                ws[i] = 0.16f + w * 0.08f;
                cs[i] = Colors.alpha(a * (0.85f - 0.4f * s), strand == 0 ? gold : core);
            }
            ctx.ribbon(helix, pts, ws, cs, 0.5f, 0f);
        }

        // ---- shock rings racing down the ray, and pulsing at the point of impact ----
        var ring = ctx.additive(VfxTextures.RING);
        for (int i = 0; i < 5; i++) {
            float cyc = (time * 0.07f + i / 5f) % 1f;
            ctx.ring(ring, start.lerp(end, cyc), b[0], b[1], w * 0.9f, w * 1.35f, 24, Colors.alpha(a * (1 - cyc) * 0.5f, core));
        }
        Vec3[] eb = hitNormal != null ? VfxContext.basis(hitNormal) : b;
        for (int i = 0; i < 3; i++) {
            float cyc = (time * 0.09f + i / 3f) % 1f;
            float rr = w * (1.5f + cyc * 6f);
            ctx.ring(ring, end, eb[0], eb[1], rr * 0.8f, rr, 28, Colors.alpha(a * (1 - cyc) * 0.7f, color));
        }

        // ---- sparks shed off the ray ----
        var spark = ctx.additive(VfxTextures.SPARK);
        for (int i = 0; i < SPARKS; i++) {
            float cyc = (sparkPhase[i] + time * 0.05f) % 1f;
            float s = (sparkPhase[i] * 7.3f + time * 0.02f) % 1f;
            double ang = sparkAngle[i] + time * 0.05;
            Vec3 radial = b[0].scale(Math.cos(ang)).add(b[1].scale(Math.sin(ang)));
            Vec3 p = start.lerp(end, s).add(radial.scale(w * 1.1f + cyc * 1.8f));
            float sa = a * (1 - cyc) * Math.min(1, cyc * 5);
            ctx.stretched(spark, p, d.add(radial.scale(0.6)), 0.5f, 0.09f, Colors.alpha(sa, i % 3 == 0 ? gold : core));
        }

        // ---- flares at the tip and at the point of impact ----
        var flash = ctx.additive(VfxTextures.FLASH);
        ctx.billboard(flash, start, w * 2.2f + 0.6f, time * 0.2f, Colors.alpha(a * 0.85f * near, core));
        ctx.billboard(flash, end, w * 3.2f, -time * 0.25f, Colors.alpha(a * 0.75f, color));
        var star = ctx.additive(VfxTextures.STAR);
        ctx.billboard(star, end, w * 2.6f, time * 0.1f, Colors.alpha(a * 0.9f, core));
        ctx.billboard(star, start, w * 1.4f + 0.5f, -time * 0.15f, Colors.alpha(a * 0.8f * near, gold));
    }
}
