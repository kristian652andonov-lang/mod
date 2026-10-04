package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom faceted spikes (ice crystals, rock pillars, thorns, blood crystals...) that erupt from the ground with an
 * overshoot, hold, then sink back. Each spike is an n-sided pyramid with flat-shaded facets.
 */
public class SpikeVfx extends Vfx {
    public enum Style {
        ICE(VfxTextures.ICE, true), ROCK(VfxTextures.ROCK, false), THORN(VfxTextures.BARK, false), CRYSTAL(VfxTextures.ICE, true);

        final ResourceLocation texture;
        final boolean glowEdges;

        Style(ResourceLocation texture, boolean glowEdges) {
            this.texture = texture;
            this.glowEdges = glowEdges;
        }
    }

    private record Spike(Vec3 base, Vec3 dir, float height, float radius, int delay, int sides, float twist) {
    }

    private static final Vec3 LIGHT = new Vec3(0.35, 1, 0.25).normalize();
    private final List<Spike> spikes = new ArrayList<>();
    private final Style style;
    private final int color;
    private final int edgeColor;
    private int grow = 3;
    private int sink = 6;

    public SpikeVfx(Style style, int color, int edgeColor, int lifetime) {
        super(lifetime);
        this.style = style;
        this.color = color;
        this.edgeColor = edgeColor;
    }

    public SpikeVfx add(Vec3 base, Vec3 dir, float height, float radius, int delay) {
        spikes.add(new Spike(base, dir.normalize(), height, radius, delay, style == Style.ROCK ? 5 : 4,
                (float) ((base.x * 13.7 + base.z * 7.3) % 6.283)));
        return this;
    }

    public SpikeVfx timing(int growTicks, int sinkTicks) {
        this.grow = Math.max(1, growTicks);
        this.sink = Math.max(1, sinkTicks);
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        int baseAlpha = (color >>> 24) & 255;
        List<Vec3[]> rings = new ArrayList<>();
        List<Vec3> tips = new ArrayList<>();
        List<Float> radii = new ArrayList<>();
        for (Spike s : spikes) {
            float local = time - s.delay();
            if (local <= 0) continue;
            float g = local < grow ? easeOut(local / grow) * 1.12f : (local < grow + 2 ? 1.12f - 0.12f * (local - grow) / 2f : 1f);
            float sinkT = clamp01((time - (lifetime - sink)) / sink);
            float h = s.height() * g * (1 - easeIn(sinkT));
            if (h <= 0.02f) continue;
            float r = s.radius() * (0.6f + 0.4f * Math.min(1, g));
            Vec3[] basis = VfxContext.basis(s.dir());
            Vec3 tip = s.base().add(s.dir().scale(h));
            Vec3 sunk = s.base().subtract(s.dir().scale(0.25));
            Vec3[] ring = new Vec3[s.sides()];
            for (int i = 0; i < s.sides(); i++) {
                double a = s.twist() + i * Math.PI * 2 / s.sides();
                double rr = r * (i % 2 == 0 ? 1 : 0.8);
                ring[i] = sunk.add(basis[0].scale(Math.cos(a) * rr)).add(basis[1].scale(Math.sin(a) * rr));
            }
            rings.add(ring);
            tips.add(tip);
            radii.add(r);
        }
        if (rings.isEmpty()) return;
        // pass 1: faceted bodies
        var body = ctx.translucent(style.texture);
        for (int k = 0; k < rings.size(); k++) {
            Vec3[] ring = rings.get(k);
            Vec3 tip = tips.get(k);
            for (int i = 0; i < ring.length; i++) {
                Vec3 a = ring[i], b = ring[(i + 1) % ring.length];
                Vec3 n = b.subtract(a).cross(tip.subtract(a)).normalize();
                float shade = 0.5f + 0.5f * (float) Math.max(0, n.dot(LIGHT));
                int c = Colors.argb(baseAlpha, Colors.scale(color, shade));
                int ct = Colors.argb(baseAlpha, Colors.scale(Colors.lerpRgb(color, 0xFFFFFF, style.glowEdges ? 0.35f : 0.1f), shade));
                ctx.quad(body, a, b, tip, tip, 0, 0, 1, 1, c, c, ct, ct);
            }
        }
        // pass 2: glowing edges
        if (style.glowEdges) {
            var edges = ctx.additive(VfxTextures.LIGHTNING);
            for (int k = 0; k < rings.size(); k++) {
                Vec3[] ring = rings.get(k);
                for (int i = 0; i < ring.length; i += 2) {
                    ctx.beam(edges, ring[i], tips.get(k), radii.get(k) * 0.18f, Colors.alpha(0.55f, edgeColor), 0, 1);
                }
            }
        }
    }
}
