package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.Locale;

/**
 * Development tool: prints the terrain model along an arbitrary line, for inspecting the walls and
 * cuts reported by {@link WallAudit}. Points are snapped to the 4-block interpolation grid.
 * Usage: {@code Transect <seed> <x0> <z0> <x1> <z1> [step=4]}
 */
public final class Transect {
    private Transect() {
    }

    public static void main(String[] args) {
        TerrainModel model = new TerrainModel(Long.parseLong(args[0]), TerrainSettings.DEFAULT);
        int x0 = Integer.parseInt(args[1]);
        int z0 = Integer.parseInt(args[2]);
        int x1 = Integer.parseInt(args[3]);
        int z1 = Integer.parseInt(args[4]);
        double step = args.length > 5 ? Double.parseDouble(args[5]) : 4.0;
        double len = Math.hypot(x1 - x0, z1 - z0);
        int count = (int) Math.ceil(len / step);
        ColumnSample s = new ColumnSample();
        for (int k = 0; k <= count; k++) {
            double t = count == 0 ? 0.0 : k / (double) count;
            int x = (int) Math.round(x0 + (x1 - x0) * t) & ~3;
            int z = (int) Math.round(z0 + (z1 - z0) * t) & ~3;
            model.sampleInto(x, z, s);
            System.out.printf(Locale.ROOT,
                "%6d %6d h=%7.1f unc=%7.1f wat=%6s mtn=%.2f wall=%.2f pk=%.2f plat=%.2f cany=%.2f cFl=%5.0f coast=%.2f fjord=%.2f trench=%.2f tFl=%5.0f volc=%.2f crat=%.2f riv=%.2f rv=%.2f rw=%5.1f lake=%.2f swamp=%.2f isl=%.2f cliff=%.2f E=%.2f C=%.2f rawC=%.2f T=%.2f H=%.2f%n",
                x, z, s.height, s.uncarvedHeight, s.waterLevel == ColumnSample.NO_WATER ? "-" : String.format(Locale.ROOT, "%.0f", s.waterLevel),
                s.mountain, s.wall, s.peakness, s.plateau, s.canyon, s.canyonFloor, s.coastCliff, s.fjord, s.trench, s.trenchFloor, s.volcano, s.crater,
                s.river, s.riverValley, s.riverWidth, s.lake, s.swamp, s.island, s.cliff, s.erosion, s.continentalness, s.rawContinentalness, s.temperature, s.humidity);
        }
    }
}
