package com.terrahaul.devtools;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Development tool: finds mountain summits with the terrain finder and renders, for each, a
 * perspective view from the south and an east-west height profile through the summit, side by
 * side, so the shape of peaks can be judged and tuned without launching Minecraft.
 * Usage: {@code PeakStudy <seed> <outDir> [target=alpine_peaks] [count=4]}
 */
public final class PeakStudy {
    private PeakStudy() {
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        String target = args.length > 2 ? args[2] : "alpine_peaks";
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 4;
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        TerrainFinder.Target t = TerrainFinder.targets().get(target);
        int ox = 0;
        int oz = 0;
        for (int i = 0; i < count; i++) {
            TerrainFinder.Result r = TerrainFinder.find(model, t, ox, oz, 30000, () -> false);
            if (r == null) {
                System.out.println("no " + target + " found from " + ox + "," + oz);
                break;
            }
            // climb to the local summit
            int px = r.x();
            int pz = r.z();
            ColumnSample s = new ColumnSample();
            for (int step = 64; step >= 2; step /= 2) {
                boolean moved = true;
                while (moved) {
                    moved = false;
                    model.sampleInto(px, pz, s);
                    double best = s.height;
                    int bx = px;
                    int bz = pz;
                    for (int[] d : new int[][]{{step, 0}, {-step, 0}, {0, step}, {0, -step}}) {
                        model.sampleInto(px + d[0], pz + d[1], s);
                        if (s.height > best) {
                            best = s.height;
                            bx = px + d[0];
                            bz = pz + d[1];
                        }
                    }
                    if (bx != px || bz != pz) {
                        px = bx;
                        pz = bz;
                        moved = true;
                    }
                }
            }
            model.sampleInto(px, pz, s);
            System.out.printf("%s %d: summit %d %.0f %d (alpine %.2f ancient %.2f wall %.2f)%n", target, i, px, s.height, pz, s.alpine, s.ancient, s.wall);
            BufferedImage view = view(model, px, pz, s.height);
            BufferedImage profile = profile(model, px, pz);
            BufferedImage out = new BufferedImage(view.getWidth() + profile.getWidth(), Math.max(view.getHeight(), profile.getHeight()), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(view, 0, 0, null);
            g.drawImage(profile, view.getWidth(), 0, null);
            g.dispose();
            ImageIO.write(out, "png", new File(dir, target + "_" + i + ".png"));
            // continue the search beyond this summit
            ox = px + 2500;
            oz = pz + 1800;
        }
    }

    /** Perspective from 1800 blocks south of the summit, camera at 55% of the summit height. */
    private static BufferedImage view(TerrainModel model, int px, int pz, double summit) {
        int bpp = 4;
        int n = 900;
        int minX = px - n * bpp / 2;
        int minZ = pz + 1800 - (n - 1) * bpp;
        ColumnSample[][] grid = new ColumnSample[n][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                ColumnSample c = new ColumnSample();
                model.sampleInto(minX + i * bpp, minZ + j * bpp, c);
                grid[j][i] = c;
            }
        }
        ColumnSample under = grid[n - 1][n / 2];
        System.setProperty("camHeight", Double.toString(Math.max(60.0, summit * 0.55 - Math.max(under.height, model.seaLevel()))));
        return Perspective.render(grid, bpp, model.seaLevel());
    }

    /** East-west profile through the summit, 1:1 scale, 2 blocks per pixel. */
    private static BufferedImage profile(TerrainModel model, int px, int pz) {
        int w = 700;
        int h = 900;
        int bpp = 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 220, 245));
        g.fillRect(0, 0, w, h);
        ColumnSample s = new ColumnSample();
        double base = 1e9;
        double[] hs = new double[w];
        for (int i = 0; i < w; i++) {
            model.sampleInto(px + (i - w / 2) * bpp, pz, s);
            hs[i] = s.height;
            base = Math.min(base, s.height);
        }
        for (int i = 0; i < w; i++) {
            int top = h - 20 - (int) ((hs[i] - base) / bpp);
            g.setColor(hs[i] > 600 ? new Color(235, 238, 242) : new Color(110, 110, 115));
            g.drawLine(i, Math.max(0, top), i, h);
        }
        g.dispose();
        return img;
    }
}
