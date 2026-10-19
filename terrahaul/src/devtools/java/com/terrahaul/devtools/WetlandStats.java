package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Development tool: how wet the wetlands are. Samples a square of the terrain model on the 4-block
 * grid and reports, for marsh columns (swamp landform above 0.5 and above 0.8) that are not frozen,
 * the share standing in water, the depth of that water, the height of the dry ground between the
 * pools and the share of marsh columns in a frozen climate.
 * Usage: {@code WetlandStats <sizeBlocks> <seed> [seed ...]}
 */
public final class WetlandStats {
    private WetlandStats() {
    }

    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);
        for (int a = 1; a < args.length; a++) {
            long seed = Long.parseLong(args[a]);
            TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
            int sl = model.seaLevel();
            int n = size / 4;
            // per band (swamp > 0.5, swamp > 0.8): columns, wet, depth sum, dry height sum, frozen
            double[][] acc = new double[2][5];
            IntStream.range(0, n).parallel().forEach(j -> {
                ColumnSample s = new ColumnSample();
                double[][] local = new double[2][5];
                for (int i = 0; i < n; i++) {
                    model.sampleInto(-size / 2 + i * 4, -size / 2 + j * 4, s);
                    for (int b = 0; b < 2; b++) {
                        if (s.swamp <= (b == 0 ? 0.5 : 0.8)) {
                            continue;
                        }
                        if (s.temperature < -0.45) {
                            local[b][4]++;
                            continue;
                        }
                        local[b][0]++;
                        boolean wet = s.height < sl - 0.5 || s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > s.height;
                        if (wet) {
                            local[b][1]++;
                            local[b][2] += Math.max(s.waterLevel == ColumnSample.NO_WATER ? sl : s.waterLevel, sl) - s.height;
                        } else {
                            local[b][3] += s.height - sl;
                        }
                    }
                }
                synchronized (acc) {
                    for (int b = 0; b < 2; b++) {
                        for (int k = 0; k < 5; k++) {
                            acc[b][k] += local[b][k];
                        }
                    }
                }
            });
            for (int b = 0; b < 2; b++) {
                double[] v = acc[b];
                System.out.printf(Locale.ROOT, "seed %d swamp>%.1f: %.0f columns, %.1f%% water (mean depth %.2f), dry ground mean %.2f above sea level, %.1f%% frozen%n",
                    seed, b == 0 ? 0.5 : 0.8, v[0], 100.0 * v[1] / Math.max(1, v[0]), v[2] / Math.max(1, v[1]), v[3] / Math.max(1, v[0] - v[1]),
                    100.0 * v[4] / Math.max(1, v[0] + v[4]));
            }
        }
    }
}
