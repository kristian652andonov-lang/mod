package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The figures of the fourth dimension, drawn the way 4th Dimension Ascension draws its tesseract: turned through planes
 * that reach into a direction that does not exist, seen in perspective from it, their edges split into red and cyan
 * round a white-hot line, in a violet glow.
 */
public final class Polytopes {
    static final int VOID = 0x9B4DFF, PALE = 0xE2C8FF, RED = 0xFF3050, CYAN = 0x30E8FF, WHITE = 0xFFFFFF;

    /** A figure: its corners in four dimensions (about unit size) and its edges. */
    public record Figure(double[][] verts, int[][] edges) {
    }

    /** The tesseract (8-cell): 16 corners, 32 edges. */
    public static final Figure TESSERACT;
    /** The 16-cell: the corners of the four axes both ways, every pair but the opposite ones joined. */
    public static final Figure SIXTEEN_CELL;
    /** The 24-cell: every arrangement of two ±1s and two 0s. */
    public static final Figure TWENTY_FOUR_CELL;
    /** The 5-cell: the simplest figure of four dimensions, five corners all joined. */
    public static final Figure FIVE_CELL;

    static {
        double[][] v = new double[16][4];
        List<int[]> e = new ArrayList<>();
        for (int i = 0; i < 16; i++) for (int k = 0; k < 4; k++) v[i][k] = ((i >> k) & 1) == 1 ? 1 : -1;
        for (int i = 0; i < 16; i++) for (int k = 0; k < 4; k++) if (((i >> k) & 1) == 0) e.add(new int[]{i, i | (1 << k)});
        TESSERACT = new Figure(v, e.toArray(int[][]::new));

        double[][] v16 = new double[8][4];
        for (int k = 0; k < 4; k++) {
            v16[k * 2][k] = 1.4;
            v16[k * 2 + 1][k] = -1.4;
        }
        List<int[]> e16 = new ArrayList<>();
        for (int i = 0; i < 8; i++) for (int j = i + 1; j < 8; j++) if (i / 2 != j / 2) e16.add(new int[]{i, j});
        SIXTEEN_CELL = new Figure(v16, e16.toArray(int[][]::new));

        List<double[]> v24 = new ArrayList<>();
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                for (int sa = -1; sa <= 1; sa += 2) {
                    for (int sb = -1; sb <= 1; sb += 2) {
                        double[] p = new double[4];
                        p[a] = sa * 0.9;
                        p[b] = sb * 0.9;
                        v24.add(p);
                    }
                }
            }
        }
        List<int[]> e24 = new ArrayList<>();
        for (int i = 0; i < v24.size(); i++) {
            for (int j = i + 1; j < v24.size(); j++) {
                double d = 0;
                for (int k = 0; k < 4; k++) d += (v24.get(i)[k] - v24.get(j)[k]) * (v24.get(i)[k] - v24.get(j)[k]);
                if (Math.abs(d - 0.81 * 2) < 1e-6) e24.add(new int[]{i, j});
            }
        }
        TWENTY_FOUR_CELL = new Figure(v24.toArray(double[][]::new), e24.toArray(int[][]::new));

        double s = 1 / Math.sqrt(5);
        double[][] v5 = {{1, 1, 1, -s}, {1, -1, -1, -s}, {-1, 1, -1, -s}, {-1, -1, 1, -s}, {0, 0, 0, Math.sqrt(5) - s}};
        List<int[]> e5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++) e5.add(new int[]{i, j});
        FIVE_CELL = new Figure(v5, e5.toArray(int[][]::new));
    }

    private Polytopes() {
    }

    /**
     * Where the figure's corners are, turned by {@code a} (in the x-w plane), {@code b} (z-w) and {@code c} (x-z, an
     * ordinary turn), seen from the fourth direction and drawn {@code size} across about {@code center}.
     */
    public static Vec3[] corners(Figure f, Vec3 center, double size, double a, double b, double c) {
        Vec3[] p = new Vec3[f.verts().length];
        double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b), cc = Math.cos(c), sc = Math.sin(c);
        for (int i = 0; i < p.length; i++) {
            double[] q = f.verts()[i];
            double x = q[0], y = q[1], z = q[2], w = q[3];
            double x1 = x * ca - w * sa, w1 = x * sa + w * ca;
            double z1 = z * cb - w1 * sb, w2 = z * sb + w1 * cb;
            double x2 = x1 * cc - z1 * sc, z2 = x1 * sc + z1 * cc;
            double k = 2.6 / (2.6 - w2);
            p[i] = center.add(x2 * k * size * 0.5, y * k * size * 0.5, z2 * k * size * 0.5);
        }
        return p;
    }

    /** Draws a figure, its edges split red and cyan round white in a violet glow, at {@code alpha}. */
    public static void draw(VfxContext ctx, Figure f, Vec3 center, double size, double a, double b, double c, float alpha, float t) {
        if (alpha <= 0.01f) return;
        Vec3[] p = corners(f, center, size, a, b, c);
        var white = ctx.additive(VfxTextures.WHITE);
        var glow = ctx.additive(VfxTextures.GLOW);
        Vec3 split = ctx.camLeft.scale(Math.max(0.6, size * 0.02));
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 2.3 + size);
        float wd = (float) Math.max(0.5, size * 0.012);
        for (int[] e : f.edges()) {
            Vec3 u = p[e[0]], v = p[e[1]];
            ctx.beam(white, u.add(split), v.add(split), wd, Colors.alpha(alpha * 0.55f, RED), 0, 1);
            ctx.beam(white, u.subtract(split), v.subtract(split), wd, Colors.alpha(alpha * 0.55f, CYAN), 0, 1);
            ctx.beam(white, u, v, wd * 0.8f, Colors.alpha(alpha * flick, WHITE), 0, 1);
            ctx.beam(glow, u, v, wd * 6, Colors.alpha(alpha * 0.22f, VOID), 0, 1);
        }
        for (Vec3 v : p) ctx.billboard(glow, v, (float) (size * 0.06 + 1.5), 0, Colors.alpha(alpha, PALE));
    }
}
