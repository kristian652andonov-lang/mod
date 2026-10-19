package com.terrahaul.devtools;

import com.terrahaul.worldgen.density.TerrainDetail;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.lang.reflect.Method;
import java.util.Locale;

/** Throwaway: prints the density terms at interpolation corners. Usage: seed x0 x1 z y0 y1 */
public final class ProbeDetail {
    public static void main(String[] a) throws Exception {
        TerrainModel m = new TerrainModel(Long.parseLong(a[0]), TerrainSettings.DEFAULT);
        boolean alongZ = a.length > 6;
        int x0 = Integer.parseInt(a[1]) & ~3;
        int x1 = Integer.parseInt(a[2]) & ~3;
        int zc = Integer.parseInt(a[3]) & ~3;
        int y0 = Integer.parseInt(a[4]) & ~7;
        int y1 = Integer.parseInt(a[5]) & ~7;
        Method ledge = TerrainDetail.class.getDeclaredMethod("ledge", TerrainModel.class, ColumnSample.class, int.class, int.class, int.class);
        Method over = TerrainDetail.class.getDeclaredMethod("overhang", TerrainModel.class, ColumnSample.class, int.class, int.class, int.class);
        Method ground = TerrainDetail.class.getDeclaredMethod("ground", TerrainModel.class, ColumnSample.class, int.class, int.class, int.class);
        ledge.setAccessible(true);
        Method plan = TerrainDetail.class.getDeclaredMethod("plan", TerrainModel.class, ColumnSample.class, int.class, int.class);
        plan.setAccessible(true);
        Method odir = TerrainDetail.class.getDeclaredMethod("overhangDir", TerrainModel.class, ColumnSample.class, double[].class, int.class, int.class, int.class, int.class);
        odir.setAccessible(true);
        over.setAccessible(true);
        ground.setAccessible(true);
        for (int v = x0; v <= x1; v += 4) {
            int x = alongZ ? zc : v;
            int z = alongZ ? v : zc;
            ColumnSample s = m.sample(x, z);
            System.out.printf(Locale.ROOT, "x=%d z=%d h=%.1f cliff=%.2f steep=%.2f alp=%.2f pk=%.2f coast=%.2f cany=%.2f plat=%.2f mtn=%.2f%n", x, z, s.height, s.cliff, s.steepness,
                s.alpine, s.peakness, s.coastCliff, s.canyon, s.plateau, s.mountain);
            for (int y = y1; y >= y0; y -= 8) {
                s = m.sample(x, z);
                double g = (double) ground.invoke(null, m, s, x, y, z);
                double l = (double) ledge.invoke(null, m, s, x, y, z);
                s = m.sample(x, z);
                double o = (double) over.invoke(null, m, s, x, y, z);
                s = m.sample(x, z);
                double d = TerrainDetail.density(m, s, x, y, z);
                System.out.printf(Locale.ROOT, "   y=%d ground=%.2f ledge=%.2f over=%.2f -> %.2f top=%.1f dir=%d%n", y, g, l, o, d, s.overhangTop, s.overhangDir);
                s = m.sample(x, z);
                double[] pl = ((double[]) plan.invoke(null, m, s, x, z)).clone();
                for (int dir = 0; dir < 4; dir++) {
                    if (pl[dir * 4] > 0) {
                        s = m.sample(x, z);
                        double od = (double) odir.invoke(null, m, s, pl, x, y, z, dir);
                        System.out.printf(Locale.ROOT, "      dir %d k=%.0f rim=%.1f r=%.1f lip=%.1f -> %.2f%n", dir, pl[dir * 4], pl[dir * 4 + 1], pl[dir * 4 + 2], pl[dir * 4 + 3], od);
                    }
                }
            }
        }
    }
}
