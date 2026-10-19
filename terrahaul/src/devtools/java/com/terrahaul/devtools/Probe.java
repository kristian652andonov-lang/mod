package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Locale;

/**
 * Development tool: prints terrain model fields along a west-east line.
 * Args: {@code seed x0 x1 z step}, or {@code seed z0 z1 x step z} for a north-south line.
 */
public final class Probe {
    public static void main(String[] args) {
        TerrainModel model = new TerrainModel(Long.parseLong(args[0]), TerrainSettings.DEFAULT);
        int a0 = Integer.parseInt(args[1]);
        int a1 = Integer.parseInt(args[2]);
        int fixed = Integer.parseInt(args[3]);
        int step = Integer.parseInt(args[4]);
        boolean northSouth = args.length > 5 && args[5].equals("z");
        ColumnSample s = new ColumnSample();
        for (int a = a0; a <= a1; a += step) {
            int x = northSouth ? fixed : a;
            int z = northSouth ? a : fixed;
            model.sampleInto(x, z, s);
            System.out.printf(Locale.ROOT, "x=%d z=%d h=%.1f unc=%.1f mtn=%.2f peak=%.2f alp=%.2f wall=%.2f plat=%.2f cany=%.2f riv=%.2f valley=%.2f lake=%.2f volc=%.2f coast=%.2f E=%.2f C=%.2f cliff=%.2f fjord=%.2f isl=%.2f rawC=%.2f water=%.1f%n",
                x, z, s.height, s.uncarvedHeight, s.mountain, s.peakness, s.alpine, s.wall, s.plateau, s.canyon, s.river, s.riverValley, s.lake, s.volcano, s.coastCliff, s.erosion, s.continentalness, s.cliff, s.fjord, s.island, s.rawContinentalness, s.waterLevel);
        }
    }
}
