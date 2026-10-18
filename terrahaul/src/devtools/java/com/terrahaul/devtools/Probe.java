package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Locale;

/** Development tool: prints terrain model fields along a west-east line. Args: seed x0 x1 z step */
public final class Probe {
    public static void main(String[] args) {
        TerrainModel model = new TerrainModel(Long.parseLong(args[0]), TerrainSettings.DEFAULT);
        int x0 = Integer.parseInt(args[1]);
        int x1 = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);
        int step = Integer.parseInt(args[4]);
        ColumnSample s = new ColumnSample();
        for (int x = x0; x <= x1; x += step) {
            model.sampleInto(x, z, s);
            System.out.printf(Locale.ROOT, "x=%d h=%.1f unc=%.1f mtn=%.2f peak=%.2f alp=%.2f wall=%.2f plat=%.2f cany=%.2f riv=%.2f valley=%.2f lake=%.2f volc=%.2f coast=%.2f E=%.2f C=%.2f cliff=%.2f fjord=%.2f isl=%.2f rawC=%.2f water=%.1f%n",
                x, s.height, s.uncarvedHeight, s.mountain, s.peakness, s.alpine, s.wall, s.plateau, s.canyon, s.river, s.riverValley, s.lake, s.volcano, s.coastCliff, s.erosion, s.continentalness, s.cliff, s.fjord, s.island, s.rawContinentalness, s.waterLevel);
        }
    }
}
