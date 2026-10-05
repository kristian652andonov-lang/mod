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
        ICE(VfxTextures.ICE, true), ROCK(VfxTextures.ROCK, false), THORN(VfxTextures.THORN, false), CRYSTAL(VfxTextures.ICE, true);

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
    @org.jetbrains.annotations.Nullable
    private com.fantasyweapons.client.vfx.GroundMaterial material;
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

    /** Texture the spikes with the real ground block's texture (tiled up their height), lit like the terrain. */
    public SpikeVfx material(com.fantasyweapons.client.vfx.GroundMaterial m) {
        this.material = m;
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
        boolean curved = style == Style.THORN;
        // ICE/CRYSTAL stay see-through; rock and thorns are modelled solids that hide their own far side
        var body = material != null ? ctx.solid(com.fantasyweapons.client.vfx.GroundMaterial.ATLAS)
                : style.glowEdges ? ctx.translucent(style.texture) : ctx.solid(style.texture);
        List<Vec3[]> bases = new ArrayList<>();
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
            // ground-textured spikes are split into one band per block of height so the texture tiles instead of stretching
            int levels = curved ? 4 : material != null ? Math.max(1, Math.min(6, (int) Math.ceil(h))) : 1;
            // thorns hook over: their axis bends sideways towards the tip
            Vec3 bend = curved ? basis[0].scale(Math.cos(s.twist() * 2)).add(basis[1].scale(Math.sin(s.twist() * 2))) : Vec3.ZERO;
            Vec3[][] rings = new Vec3[levels][];
            Vec3 tip = null;
            for (int k = 0; k <= levels; k++) {
                float f = k / (float) levels;
                Vec3 axis = s.base().subtract(s.dir().scale(0.25 * (1 - f))).add(s.dir().scale(h * f)).add(bend.scale(h * 0.35 * f * f));
                if (k == levels) {
                    tip = axis;
                    break;
                }
                float rr = r * (curved ? (float) Math.pow(1 - f, 0.85) : levels > 1 ? 1 - 0.8f * f : 1f);
                Vec3[] ring = new Vec3[s.sides()];
                for (int i = 0; i < s.sides(); i++) {
                    double a = s.twist() + i * Math.PI * 2 / s.sides();
                    double rad = rr * (i % 2 == 0 ? 1 : 0.8);
                    ring[i] = axis.add(basis[0].scale(Math.cos(a) * rad)).add(basis[1].scale(Math.sin(a) * rad));
                }
                rings[k] = ring;
            }
            // faceted body: bands between rings, the last one closing to the tip
            for (int k = 0; k < levels; k++) {
                Vec3[] lo = rings[k];
                Vec3[] hi = k + 1 < levels ? rings[k + 1] : null;
                float f0 = k / (float) levels, f1 = (k + 1) / (float) levels;
                for (int i = 0; i < lo.length; i++) {
                    int j = (i + 1) % lo.length;
                    Vec3 a = lo[i], b = lo[j];
                    Vec3 c = hi == null ? tip : hi[j], d = hi == null ? tip : hi[i];
                    Vec3 n = b.subtract(a).cross(d.subtract(a));
                    n = n.lengthSqr() < 1e-12 ? s.dir() : n.normalize();
                    float shade = 0.5f + 0.5f * (float) Math.max(0, n.dot(LIGHT));
                    if (material != null) {
                        var sp = material.particle();
                        int tint = Colors.lerpRgb(0xFFFFFF, color & 0xFFFFFF, 0.15f);
                        int cm = Colors.argb(baseAlpha, material.lit(tint, shade));
                        float fu = Math.min(1f, (float) a.distanceTo(b));
                        ctx.quad(body, a, b, c, d, sp.getU(0), sp.getV(0), sp.getU(fu), sp.getV(1), cm, cm, cm, cm);
                        continue;
                    }
                    int c0 = Colors.argb(baseAlpha, Colors.scale(shadeAt(f0), shade));
                    int c1 = Colors.argb(baseAlpha, Colors.scale(shadeAt(f1), shade));
                    ctx.quad(body, a, b, c, d, 0, 1 - f1, 1, 1 - f0, c0, c0, c1, c1);
                }
            }
            bases.add(rings[0]);
            tips.add(tip);
            radii.add(r);
        }
        if (bases.isEmpty() || !style.glowEdges) return;
        // glowing crystal edges
        var edges = ctx.additive(VfxTextures.LIGHTNING);
        for (int k = 0; k < bases.size(); k++) {
            Vec3[] ring = bases.get(k);
            for (int i = 0; i < ring.length; i += 2) {
                ctx.beam(edges, ring[i], tips.get(k), radii.get(k) * 0.18f, Colors.alpha(0.55f, edgeColor), 0, 1);
            }
        }
    }

    /** Body colour at fraction {@code f} of the height: crystals brighten towards the tip, thorns ripen to a pale point. */
    private int shadeAt(float f) {
        int rgb = color & 0xFFFFFF;
        return switch (style) {
            case ICE, CRYSTAL -> Colors.lerpRgb(rgb, 0xFFFFFF, 0.35f * f);
            case THORN -> Colors.lerpRgb(rgb, Colors.lerpRgb(rgb, edgeColor & 0xFFFFFF, 0.55f), f * f);
            case ROCK -> Colors.lerpRgb(rgb, Colors.brighten(rgb, 0.1f), f);
        };
    }
}
