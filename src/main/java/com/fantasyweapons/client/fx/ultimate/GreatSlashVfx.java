package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * THE GREAT SLASH. A crescent of white light leaves the blade and flies out where the player looks, bowing forward,
 * afterimages trailing it; behind it the cut hangs in the air as a fading sheet of light, and the land along it falls
 * apart, the two sides of every cut block thrown away from each other.
 */
public class GreatSlashVfx extends Vfx {
    private static final int WHITE = 0xFFFFFF, PALE = 0xFFF6D6, GOLD = 0xFFD970;
    private static final int POINTS = 33;

    private final Vec3 eye, look, blade, normal;
    private final float length, half, speed;
    private final RandomSource rng;
    private BlockDebrisVfx debris;

    public GreatSlashVfx(Vec3 eye, Vec3 look, Vec3 blade, float length, float half, float speed, long seed) {
        super((int) (length / speed) + 45);
        this.eye = eye;
        this.look = look.normalize();
        this.blade = blade.normalize();
        this.normal = this.look.cross(this.blade).normalize();
        this.length = length;
        this.half = half;
        this.speed = speed;
        this.rng = RandomSource.create(seed);
    }

    public GreatSlashVfx shattering(BlockDebrisVfx debris) {
        this.debris = debris;
        return this;
    }

    private float halfAt(float f) {
        return half * (float) Math.sqrt(Math.min(1, f / (length * 0.35f)));
    }

    /** A point of the crescent at forward distance {@code f}, {@code u} from -1 to 1 across it: it bows forward in the middle. */
    private Vec3 crescent(float f, float u) {
        float h = halfAt(f);
        float bow = h * 0.35f * (1 - u * u);
        return eye.add(look.scale(f + bow)).add(blade.scale(u * h));
    }

    @Override
    public void tick() {
        super.tick();
        if (debris == null) return;
        float f = age * speed;
        if (f > length + speed) {
            debris.close();
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // blocks along the cut are split away from it, to either side
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int n = Math.round(14 * com.fantasyweapons.config.ClientConfig.quality().density());
        for (int i = 0; i < n; i++) {
            float u = rng.nextFloat() * 2 - 1;
            Vec3 p = crescent(Math.max(2, f - rng.nextFloat() * speed), u);
            var state = level.getBlockState(m.set(p.x, p.y, p.z));
            if (state.isAir()) continue;
            float side = rng.nextBoolean() ? 1 : -1;
            Vec3 vel = normal.scale(side * (0.25 + rng.nextFloat() * 0.35)).add(0, 0.15 + rng.nextFloat() * 0.25, 0);
            debris.throwBlock(state, new Vec3(Math.floor(p.x) + 0.5, Math.floor(p.y) + 0.5, Math.floor(p.z) + 0.5), vel, 1f, 30 + rng.nextInt(20));
        }
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        float f = t * speed;
        float flying = f <= length ? 1 : 1 - clamp01((f - length) / (speed * 4));
        var slash = ctx.additive(VfxTextures.SLASH);
        var energy = ctx.energy(VfxTextures.STREAK);
        var glow = ctx.additive(VfxTextures.GLOW);
        if (flying > 0) {
            // the crescent and its afterimages
            for (int k = 0; k < 5; k++) {
                float fk = Math.min(length, f) - k * speed * 1.4f;
                if (fk < 2) continue;
                float a = flying * (k == 0 ? 1f : 0.55f / k);
                Vec3[] pts = new Vec3[POINTS];
                float[] widths = new float[POINTS];
                int[] cols = new int[POINTS];
                for (int i = 0; i < POINTS; i++) {
                    float u = i / (float) (POINTS - 1) * 2 - 1;
                    pts[i] = crescent(fk, u);
                    float edge = 1 - u * u;
                    widths[i] = (4f + halfAt(fk) * 0.25f) * (0.25f + 0.75f * edge);
                    cols[i] = Colors.alpha(a * (0.2f + 0.8f * edge), k == 0 ? WHITE : PALE);
                }
                ctx.ribbon(k == 0 ? slash : energy, pts, widths, cols, 0, 1f / (POINTS - 1));
                if (k == 0) {
                    float[] wide = widths.clone();
                    for (int i = 0; i < wide.length; i++) {
                        wide[i] *= 3;
                        cols[i] = Colors.alpha(flying * 0.35f * (1 - Math.abs(i / (float) (POINTS - 1) * 2 - 1)), GOLD);
                    }
                    ctx.ribbon(glow, pts, wide, cols, 0, 1f / (POINTS - 1));
                }
            }
        }
        // the sheet of light left hanging where it cut
        float sheet = clamp01(1 - (t - length / speed) / 40f) * clamp01(f / 6f);
        if (sheet > 0) {
            var white = ctx.additive(VfxTextures.WHITE);
            int rows = 12;
            float reached = Math.min(length, f);
            for (int r = 0; r < rows; r++) {
                float f0 = 2 + (reached - 2) * r / rows, f1 = 2 + (reached - 2) * (r + 1) / rows;
                for (int s = 0; s < 8; s++) {
                    float u0 = s / 8f * 2 - 1, u1 = (s + 1) / 8f * 2 - 1;
                    float fresh0 = clamp01(1 - (f - f0) / (length * 0.6f)), fresh1 = clamp01(1 - (f - f1) / (length * 0.6f));
                    int c0 = Colors.alpha(sheet * 0.22f * fresh0 * (1 - Math.abs(u0)), PALE), c1 = Colors.alpha(sheet * 0.22f * fresh1 * (1 - Math.abs(u0)), PALE);
                    int c2 = Colors.alpha(sheet * 0.22f * fresh1 * (1 - Math.abs(u1)), PALE), c3 = Colors.alpha(sheet * 0.22f * fresh0 * (1 - Math.abs(u1)), PALE);
                    ctx.quad(white, crescent(f0, u0), crescent(f1, u0), crescent(f1, u1), crescent(f0, u1), 0, 0, 1, 1, c0, c1, c2, c3);
                }
            }
        }
    }

    /** A creature cut in two: a blinding plane of light through it, its halves' light spilling out. */
    public static class Cut extends Vfx {
        private final Vec3 at, normal;
        private final float size;

        public Cut(Vec3 at, Vec3 normal, float size) {
            super(16);
            this.at = at;
            this.normal = normal.normalize();
            this.size = Math.max(1, size);
        }

        @Override
        public void render(VfxContext ctx) {
            float t = (age + ctx.partial) / lifetime;
            Vec3[] b = VfxContext.basis(normal);
            float r = size * (1.2f + 0.8f * easeOut(t));
            ctx.disc(ctx.additive(VfxTextures.GLOW), at, b[0], b[1], r * 1.6f, 0, Colors.alpha((1 - t) * 0.9f, WHITE));
            ctx.disc(ctx.additive(VfxTextures.FLASH), at, b[0], b[1], r, t * 2, Colors.alpha(1 - t, WHITE));
            var spark = ctx.additive(VfxTextures.SPARK);
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                ctx.stretched(spark, at.add(dir.scale(r * easeOut(t))), dir, size * 0.8f, 0.25f, Colors.alpha(1 - t, GOLD));
            }
        }
    }
}
