package com.terrahaul.devtools;

import com.terrahaul.worldgen.noise.Fractal;
import java.util.Arrays;

public final class TmpTest {
    public static void main(String[] a) {
        int[][] cfg = {{4, 200, 50}, {2, 200, 40}, {3, 200, 45}};
        for (int[] c : cfg) {
            Fractal f = new Fractal(99L + c[0], c[0], c[1] / 100.0, c[2] / 100.0);
            int n = 200000;
            double[] g = new double[n];
            java.util.Random r = new java.util.Random(1);
            double e = 0.001;
            for (int i = 0; i < n; i++) {
                double x = r.nextDouble() * 1e3;
                double z = r.nextDouble() * 1e3;
                double v = f.fbm(x, z);
                double vx = f.fbm(x + e, z);
                double vz = f.fbm(x, z + e);
                g[i] = Math.sqrt((vx - v) * (vx - v) + (vz - v) * (vz - v)) / e;
            }
            Arrays.sort(g);
            StringBuilder sb = new StringBuilder("octaves " + c[0] + " gain " + c[2] + ": ");
            for (double q : new double[]{0.5, 0.9, 0.95, 0.99, 1.0}) {
                sb.append(String.format("q%.2f=%.2f ", q, g[Math.min(n - 1, (int) (q * n))]));
            }
            System.out.println(sb);
        }
    }
}
