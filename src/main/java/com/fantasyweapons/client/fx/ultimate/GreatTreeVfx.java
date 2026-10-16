package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.fx.BlockVoxels;
import com.fantasyweapons.weapons.ultimate.RootSpline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE ROOTS OF THE GREAT TREE. The roots that hold up the world heave out of the ground - a crown of colossal ones from
 * the middle, many more all over the land - each bursting up in a spray of earth, arching high and plunging back in;
 * then they wrap around everything they seize. They are made of blocks - the very blocks the server builds into the world
 * when the spell ends, each giving way to the real one as it appears.
 */
public class GreatTreeVfx extends Vfx {

    private final Vec3 center;
    private final float radius;
    private final int duration;
    private final List<RootSpline> roots;
    private final float[] delays;
    private int[] held = new int[0];
    private int heldFrom;

    public GreatTreeVfx(Vec3 center, float radius, int duration, List<RootSpline> roots) {
        super(duration + 110);
        this.center = center;
        this.radius = radius;
        this.duration = duration;
        this.roots = roots;
        delays = new float[roots.size()];
        for (int i = 0; i < roots.size(); i++) {
            // the crown first, then the rest outward
            double d = Math.hypot(roots.get(i).p0().x - center.x, roots.get(i).p0().z - center.z);
            delays[i] = 16 + (float) (d / radius) * 26 + (i % 5) * 1.5f;
        }
    }

    /** The roots seize these creatures now. */
    public void seize(int[] ids) {
        held = ids;
        heldFrom = age;
    }

    /** Each root as the blocks the server will build it from (made when first drawn, from the world as it is then). */
    private BlockVoxels[] blocks;

    @Override
    public void tick() {
        super.tick();
        // once the server builds the roots for real, each block of these gives way to the real one
        var level = Minecraft.getInstance().level;
        if (blocks != null && level != null && age > duration - 5) for (BlockVoxels b : blocks) b.checkBuilt(level, 600);
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (blocks == null) {
            blocks = new BlockVoxels[roots.size()];
            for (int i = 0; i < roots.size(); i++) blocks[i] = voxels(level, roots.get(i));
        }
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = 0; i < roots.size(); i++) {
            float grow = easeOut(clamp01((t - delays[i]) / 30f));
            if (grow <= 0) continue;
            RootSpline r = roots.get(i);
            blocks[i].render(ctx, grow);
            // the earth bursting where it breaks out
            float burst = clamp01((t - delays[i]) / 20f);
            if (burst < 1) {
                Vec3 out = r.at(0.08);
                ctx.billboard(mist, out, r.thickness() * (3 + burst * 6), i, Colors.alpha((1 - burst) * 0.7f, 0x6A5440));
            }
        }
        drawHeld(ctx, t);
    }

    /** The blocks of one root, each appearing as the root grows past it. */
    private static BlockVoxels voxels(net.minecraft.world.level.Level level, RootSpline r) {
        BlockVoxels.Builder b = new BlockVoxels.Builder();
        com.fantasyweapons.world.wreck.WreckShapes.rootBlocks(r, (int) Math.floor(r.minX()) - 2, (int) Math.ceil(r.maxX()) + 2,
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1, (int) Math.floor(r.minZ()) - 2, (int) Math.ceil(r.maxZ()) + 2,
                (x, y, z, u, d, rad, back) -> {
                    if (d <= rad) b.add(x, y, z, com.fantasyweapons.world.wreck.WreckShapes.rootBlock(x, y, z, u, back), (float) u);
                });
        return b.build(level);
    }

    /** Roots of blocks coiled round everything seized (the nearest forty), tightening. */
    private void drawHeld(VfxContext ctx, float t) {
        if (held.length == 0) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float wrap = easeOut(clamp01((t - heldFrom) / 16f));
        java.util.List<Entity> near = new java.util.ArrayList<>();
        for (int id : held) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) near.add(e);
        }
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(ctx.cam)));
        for (int n = 0; n < Math.min(40, near.size()); n++) {
            Entity e = near.get(n);
            Vec3 base = e.position();
            float w = e.getBbWidth() * 0.75f + 0.4f, h = e.getBbHeight() + 0.4f;
            BlockVoxels.Builder b = new BlockVoxels.Builder();
            for (int k = 0; k < 3; k++) {
                double phase = k * Math.PI * 2 / 3;
                Vec3 p0 = base.add(Math.cos(phase) * w * 2.5, -2, Math.sin(phase) * w * 2.5);
                Vec3 p1 = base.add(Math.cos(phase + 1.6) * w * 1.4, h * 0.3, Math.sin(phase + 1.6) * w * 1.4);
                Vec3 p2 = base.add(Math.cos(phase + 3.4) * w, h * 0.75, Math.sin(phase + 3.4) * w);
                Vec3 p3 = base.add(Math.cos(phase + 5.0) * w * 0.6, h, Math.sin(phase + 5.0) * w * 0.6);
                RootSpline r = new RootSpline(p0, p1, p2, p3, 0.55f + w * 0.12f);
                com.fantasyweapons.world.wreck.WreckShapes.rootBlocks(r, (int) Math.floor(r.minX()) - 1, (int) Math.ceil(r.maxX()) + 1,
                        (int) Math.floor(r.minY()) - 1, (int) Math.ceil(r.maxY()) + 1, (int) Math.floor(r.minZ()) - 1, (int) Math.ceil(r.maxZ()) + 1,
                        (x, y, z, u, d, rad, back) -> {
                            if (d <= rad) b.add(x, y, z, com.fantasyweapons.world.wreck.WreckShapes.rootBlock(x, y, z, u * 0.8, back), (float) u);
                        });
            }
            b.build(level).render(ctx, wrap);
        }
    }
}
