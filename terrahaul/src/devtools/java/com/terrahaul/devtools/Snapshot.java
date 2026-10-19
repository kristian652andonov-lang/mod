package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Development tool: a perspective snapshot of the terrain model around a point, seen from any
 * compass direction, to judge landforms (cliffs, canyons, islands, wetlands) the way a player
 * would see them. Usage:
 * {@code Snapshot <seed> <x> <z>|find:<target> <sizeBlocks> <blocksPerCell> <cameraHeight> <headingDeg> <out.png>}
 * where the camera stands {@code sizeBlocks / 2} from the point on the side given by the heading
 * (0 = camera south of the point looking north, 90 = camera west looking east, ...), at
 * {@code cameraHeight} blocks above the ground or water under it.
 */
public final class Snapshot {
    private Snapshot() {
    }

    public static void main(String[] args) throws Exception {
        TerrainModel model = new TerrainModel(Long.parseLong(args[0]), TerrainSettings.DEFAULT);
        int px;
        int pz;
        int a = 1;
        if (args[1].startsWith("find:")) {
            TerrainFinder.Result r = TerrainFinder.find(model, TerrainFinder.targets().get(args[1].substring(5)), 0, 0, 30000, () -> false);
            if (r == null) {
                System.out.println("not found");
                return;
            }
            px = r.x();
            pz = r.z();
            a = 2;
            System.out.println("found at " + px + " " + r.y() + " " + pz);
        } else {
            px = Integer.parseInt(args[1]);
            pz = Integer.parseInt(args[2]);
            a = 3;
        }
        int size = Integer.parseInt(args[a]);
        int bpp = Integer.parseInt(args[a + 1]);
        double camHeight = Double.parseDouble(args[a + 2]);
        double heading = Math.toRadians(Double.parseDouble(args[a + 3]));
        File out = new File(args[a + 4]);
        int n = size / bpp;
        // grid rows run from far (j = 0) to the camera (j = n - 1); the camera looks along -row
        double fx = Math.sin(heading);
        double fz = -Math.cos(heading);
        double rxv = -fz;
        double rzv = fx;
        ColumnSample[][] grid = new ColumnSample[n][n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                double u = (i - n / 2.0) * bpp;
                double v = (n / 2.0 - j) * bpp;
                int x = (int) Math.round(px + rxv * u + fx * v);
                int z = (int) Math.round(pz + rzv * u + fz * v);
                ColumnSample s = new ColumnSample();
                model.sampleInto(x, z, s);
                grid[j][i] = s;
            }
        });
        System.setProperty("camHeight", Double.toString(camHeight));
        BufferedImage img = Perspective.render(grid, bpp, model.seaLevel());
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }
}
