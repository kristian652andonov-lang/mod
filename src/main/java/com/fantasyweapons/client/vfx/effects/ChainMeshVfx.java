package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.List;
import java.util.function.Supplier;

/**
 * A chain made of the weapon's own chain-link meshes (alternating link orientations, exactly as modelled) stretched
 * between two moving points, with an optional head piece (a blade segment) at the far end. The links glow with heat.
 * Used for Infernochain's thrown hook.
 */
public class ChainMeshVfx extends Vfx {
    private final String geo;
    private final Supplier<Vec3> from;
    private final Supplier<Vec3> to;
    private final float scale;
    private final int heat;
    @Nullable
    private String[] headBones;
    private float sag;
    private int fadeTicks = 4;

    public ChainMeshVfx(String weaponGeo, Supplier<Vec3> from, Supplier<Vec3> to, float scale, int heat, int lifetime) {
        super(lifetime);
        this.geo = weaponGeo;
        this.from = from;
        this.to = to;
        this.scale = scale;
        this.heat = heat;
    }

    public ChainMeshVfx head(String... bones) {
        this.headBones = bones;
        return this;
    }

    /** Downward sag at the middle of the chain (blocks). */
    public ChainMeshVfx sag(float blocks) {
        this.sag = blocks;
        return this;
    }

    public ChainMeshVfx fade(int ticks) {
        this.fadeTicks = Math.max(1, ticks);
        return this;
    }

    static Quaternionf along(Vec3 dir, float roll) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (d.lengthSquared() < 1e-8f) d.set(0, 1, 0);
        d.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d).rotateY(roll);
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 a = from.get(), b = to.get();
        if (a == null || b == null) return;
        BakedGeoModel model = ModelParts.model(geo);
        if (model == null) return;
        List<GeoBone> linkA = ModelParts.bones(model, "chain_link_0");
        List<GeoBone> linkB = ModelParts.bones(model, "chain_link_1");
        if (linkA.isEmpty() || linkB.isEmpty()) return;
        float left = lifetime - age - ctx.partial;
        float alpha = clamp01(left / fadeTicks);
        if (alpha <= 0.01f) return;
        Vec3 pivotA = ModelParts.center(linkA), pivotB = ModelParts.center(linkB);
        double linkLen = 2.4 / 16.0 * scale;
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) (len / linkLen));
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n;
            pts[i] = a.lerp(b, k).subtract(0, sag * 4 * k * (1 - k), 0);
        }
        int body = Colors.alpha(alpha, Colors.lerpRgb(0xFFFFFF, heat, 0.35f));
        int glow = Colors.alpha(alpha * 0.55f, heat);
        var tex = ModelParts.texture(geo);
        for (int pass = 0; pass < 2; pass++) {
            var vc = pass == 0 ? ctx.translucent(tex) : ctx.additive(tex);
            int col = pass == 0 ? body : glow;
            for (int i = 0; i < n; i++) {
                Vec3 mid = pts[i].lerp(pts[i + 1], 0.5);
                Quaternionf q = along(pts[i + 1].subtract(pts[i]), 0);
                boolean odd = (i & 1) == 1;
                ModelParts.draw(ctx, vc, odd ? linkB : linkA, mid, q, scale, odd ? pivotB : pivotA, col);
            }
            if (headBones != null) {
                List<GeoBone> head = ModelParts.bones(model, headBones);
                if (!head.isEmpty()) {
                    Vec3 dir = b.subtract(pts[Math.max(0, n - 1)]);
                    Vec3 pivot = ModelParts.center(head);
                    // the head piece sits on the chain end, pointing onward
                    ModelParts.draw(ctx, vc, head, b.add(dir.normalize().scale(0.25 * scale)), along(dir, 0), scale, pivot, col);
                }
            }
        }
        // heat glow along the chain
        var gl = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i <= n; i += 3) ctx.billboard(gl, pts[i], 0.5f * scale * 0.4f + 0.3f, 0, Colors.alpha(alpha * 0.35f, heat));
    }
}
