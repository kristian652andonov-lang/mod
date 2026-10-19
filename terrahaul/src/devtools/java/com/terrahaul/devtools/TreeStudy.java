package com.terrahaul.devtools;

import com.terrahaul.worldgen.trees.TreeBlueprint;
import com.terrahaul.worldgen.trees.TreeShapes;
import com.terrahaul.worldgen.trees.TreeSize;
import com.terrahaul.worldgen.trees.TreeSpecies;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import javax.imageio.ImageIO;

/**
 * Development tool: builds procedural trees exactly as the game does (the tree geometry is plain
 * Java), renders them as isometric voxel pictures standing on flat and sloped ground, and prints
 * statistics per species and size: height, crown width, leaf and wood counts, leaves dropped for
 * being more than six blocks from wood, cells clipped by the reach limit and build time.
 * <p>
 * Run with {@code ./gradlew treeStudy -Pargs="<outDir> [species,...|all] [samples] [pxPerBlock] [seed] [sizes,...|all]"}.
 * Log end faces (sawn rings) are drawn in a light tan so exposed rings stand out; ground-following
 * roots and rooted dirt are laid on the simulated ground the way the feature lays them.
 */
public final class TreeStudy {
    private TreeStudy() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/trees");
        String speciesArg = args.length > 1 ? args[1] : "all";
        int samples = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int px = args.length > 3 ? Integer.parseInt(args[3]) : 4;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 12345L;
        String sizesArg = args.length > 5 ? args[5] : "all";
        Files.createDirectories(out);
        List<TreeSpecies> species = new ArrayList<>();
        for (TreeSpecies s : TreeSpecies.values()) {
            if (speciesArg.equals("all") || List.of(speciesArg.split(",")).contains(s.serializedName())) {
                species.add(s);
            }
        }
        List<TreeSize> sizes = new ArrayList<>();
        for (TreeSize s : TreeSize.values()) {
            if (sizesArg.equals("all") || List.of(sizesArg.split(",")).contains(s.serializedName())) {
                sizes.add(s);
            }
        }
        List<String> report = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-12s %-8s %6s %6s %7s %7s %6s %7s %6s %7s", "species", "size", "height", "width",
            "leaves", "wood", "orph%", "clipped", "roots", "ms"));
        for (TreeSpecies sp : species) {
            BufferedImage[][] tiles = new BufferedImage[samples][sizes.size()];
            for (int si = 0; si < sizes.size(); si++) {
                TreeSize size = sizes.get(si);
                double h = 0;
                int hMax = 0;
                double w = 0;
                double leaves = 0;
                double wood = 0;
                double orph = 0;
                double clipped = 0;
                double roots = 0;
                double ms = 0;
                int floats = 0;
                int pruned = 0;
                int n = Math.max(samples, 12);
                for (int i = 0; i < n; i++) {
                    long t0 = System.nanoTime();
                    TreeBlueprint bp = TreeShapes.create(sp, size, new SplittableRandom(seed * 1_000_003L + sp.ordinal() * 7919L + size.ordinal() * 104_729L + i));
                    bp.finish();
                    ms += (System.nanoTime() - t0) / 1e6;
                    int[] c = new int[3];
                    bp.forEachCell((x, y, z, code) -> {
                        if (TreeBlueprint.isLeaf(code)) {
                            c[0]++;
                        } else if (TreeBlueprint.isWoody(code)) {
                            c[1]++;
                        }
                    });
                    bp.forEachGroundRoot((x, z, rise) -> c[2]++);
                    pruned += bp.detached() > 0 ? 1 : 0;
                    int floating = floatingWood(bp);
                    if (floating > 0) {
                        floats++;
                        if (floats <= 3) {
                            System.out.println("  floating wood: " + sp.serializedName() + " " + size.serializedName() + " sample " + i + ": " + floating + " blocks");
                        }
                    }
                    int height = bp.maxY() + 1;
                    h += height;
                    hMax = Math.max(hMax, height);
                    w += Math.max(bp.maxX() - bp.minX() + 1, bp.maxZ() - bp.minZ() + 1);
                    leaves += c[0];
                    wood += c[1];
                    orph += c[0] + bp.orphans() == 0 ? 0 : 100.0 * bp.orphans() / (c[0] + bp.orphans());
                    clipped += bp.clipped();
                    roots += c[2];
                    if (i < samples) {
                        tiles[i][si] = render(bp, px, i % 2 == 1 ? 0.45 : 0.0, sp.serializedName() + " " + size.serializedName());
                    }
                }
                report.add(String.format(Locale.ROOT, "%-12s %-8s %6.1f %6.1f %7.0f %7.0f %6.1f %7.1f %6.0f %7.2f   (max height %d, floating wood in %d/%d, pruned in %d)",
                    sp.serializedName(), size.serializedName(), h / n, w / n, leaves / n, wood / n, orph / n, clipped / n, roots / n, ms / n, hMax,
                    floats, n, pruned));
            }
            ImageIO.write(sheet(tiles), "png", out.resolve(sp.serializedName() + ".png").toFile());
        }
        Files.write(out.resolve("stats.txt"), report);
        report.forEach(System.out::println);
    }

    /**
     * Wood and prop-root blocks not connected (through faces) to the tree's footing: the trunk
     * base, or for mangroves the roots at or below the base.
     */
    private static int floatingWood(TreeBlueprint bp) {
        Map<Long, Boolean> solid = new HashMap<>();
        List<long[]> seeds = new ArrayList<>();
        bp.forEachCell((x, y, z, code) -> {
            if (TreeBlueprint.isWoody(code) || code == TreeBlueprint.PROP_ROOT) {
                solid.put(key(x, y, z), Boolean.FALSE);
                if (y <= 0) {
                    seeds.add(new long[]{x, y, z});
                }
            }
        });
        java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>(seeds);
        for (long[] s : seeds) {
            solid.put(key((int) s[0], (int) s[1], (int) s[2]), Boolean.TRUE);
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long[] p = queue.poll();
            for (int[] d : dirs) {
                long k = key((int) p[0] + d[0], (int) p[1] + d[1], (int) p[2] + d[2]);
                if (Boolean.FALSE.equals(solid.get(k))) {
                    solid.put(k, Boolean.TRUE);
                    queue.add(new long[]{p[0] + d[0], p[1] + d[1], p[2] + d[2]});
                }
            }
        }
        int floating = 0;
        for (Boolean b : solid.values()) {
            if (!b) {
                floating++;
            }
        }
        return floating;
    }

    // ------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------

    private static final int GRASS = 1;
    private static final int SOIL = 2;
    private static final int ROOTED = 3;
    private static final int GROUND_WOOD = 4;
    private static final int OFFSET = 16;

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    /**
     * Renders a blueprint standing on ground that rises by {@code slope} blocks per block towards
     * +x (0 = flat), with the trunk anchored and the ground roots laid as the feature does.
     */
    private static BufferedImage render(TreeBlueprint bp, int s, double slope, String label) {
        Map<Long, Integer> vox = new HashMap<>();
        int reach = bp.reach();
        final int gx0 = Math.max(Math.min(bp.minX(), -6) - 1, -reach - 1);
        final int gx1 = Math.min(Math.max(bp.maxX(), 6) + 1, reach + 1);
        final int gz0 = Math.max(Math.min(bp.minZ(), -6) - 1, -reach - 1);
        final int gz1 = Math.min(Math.max(bp.maxZ(), 6) + 1, reach + 1);
        int[][] ground = new int[gx1 - gx0 + 1][gz1 - gz0 + 1];
        for (int x = gx0; x <= gx1; x++) {
            for (int z = gz0; z <= gz1; z++) {
                // the trunk stands on the block below its base, as placed by the heightmap
                int g = (int) Math.floor(slope * x + 0.5) - 1;
                ground[x - gx0][z - gz0] = g;
                vox.put(key(x, g, z), GRASS);
                vox.put(key(x, g - 1, z), SOIL);
            }
        }
        // the tree's blocks; blocks inside the ground only where the feature may bury them
        bp.forEachCell((x, y, z, code) -> {
            boolean inGround = x >= gx0 && x <= gx1 && z >= gz0 && z <= gz1 && y <= ground[x - gx0][z - gz0];
            if (TreeBlueprint.isLeaf(code) && inGround) {
                return;
            }
            if (code == TreeBlueprint.ROOTED_DIRT) {
                return;
            }
            vox.put(key(x, y, z), OFFSET + code);
        });
        // trunk anchors down to the ground
        bp.forEachTrunkBase((x, z, u) -> {
            if (x < gx0 || x > gx1 || z < gz0 || z > gz1) {
                return;
            }
            for (int y = -1; y > ground[x - gx0][z - gz0]; y--) {
                vox.put(key(x, y, z), OFFSET + TreeBlueprint.LOG_Y);
            }
        });
        // ground-following roots
        bp.forEachGroundRoot((x, z, rise) -> {
            if (x < gx0 || x > gx1 || z < gz0 || z > gz1) {
                return;
            }
            int g = ground[x - gx0][z - gz0];
            if (Math.abs(g + 1) > 7) {
                return;
            }
            if (rise < 0) {
                if (rise > -1.2) {
                    vox.put(key(x, g, z), ROOTED);
                }
                return;
            }
            int top = g + (int) Math.floor(rise);
            for (int y = g; y <= top; y++) {
                Integer e = vox.get(key(x, y, z));
                if (y == g || e == null) {
                    vox.put(key(x, y, z), GROUND_WOOD);
                }
            }
        });
        // painter's algorithm, back to front
        List<long[]> list = new ArrayList<>(vox.size());
        int minSx = Integer.MAX_VALUE;
        int maxSx = Integer.MIN_VALUE;
        int minSy = Integer.MAX_VALUE;
        int maxSy = Integer.MIN_VALUE;
        for (Map.Entry<Long, Integer> e : vox.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 40) - 512;
            int y = (int) ((k >> 20) & 0xFFFFF) - 512;
            int z = (int) (k & 0xFFFFF) - 512;
            list.add(new long[]{x + y + z, x, y, z, e.getValue()});
            double sx = (x - z) * 0.866 * s;
            double sy = (x + z) * 0.5 * s - y * s;
            minSx = Math.min(minSx, (int) sx - s);
            maxSx = Math.max(maxSx, (int) sx + s);
            minSy = Math.min(minSy, (int) sy - s);
            maxSy = Math.max(maxSy, (int) sy + s);
        }
        list.sort((a, b) -> Long.compare(a[0], b[0]));
        int pad = 6;
        int top = 16;
        int width = maxSx - minSx + 2 * pad;
        int height = maxSy - minSy + 2 * pad + top;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 222, 240));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double ox = pad - minSx;
        double oy = pad + top - minSy;
        TreeSpecies sp = bp.species();
        for (long[] v : list) {
            int x = (int) v[1];
            int y = (int) v[2];
            int z = (int) v[3];
            int kind = (int) v[4];
            int[] top3 = color(sp, kind, 0, x, y, z);
            int[] east = color(sp, kind, 1, x, y, z);
            int[] south = color(sp, kind, 2, x, y, z);
            if (!vox.containsKey(key(x, y + 1, z))) {
                face(g, ox, oy, s, top3, 1.0, new double[][]{{x - 0.5, y + 0.5, z - 0.5}, {x + 0.5, y + 0.5, z - 0.5}, {x + 0.5, y + 0.5, z + 0.5}, {x - 0.5, y + 0.5, z + 0.5}});
            }
            if (!vox.containsKey(key(x + 1, y, z))) {
                face(g, ox, oy, s, east, 0.8, new double[][]{{x + 0.5, y + 0.5, z - 0.5}, {x + 0.5, y + 0.5, z + 0.5}, {x + 0.5, y - 0.5, z + 0.5}, {x + 0.5, y - 0.5, z - 0.5}});
            }
            if (!vox.containsKey(key(x, y, z + 1))) {
                face(g, ox, oy, s, south, 0.62, new double[][]{{x - 0.5, y + 0.5, z + 0.5}, {x + 0.5, y + 0.5, z + 0.5}, {x + 0.5, y - 0.5, z + 0.5}, {x - 0.5, y - 0.5, z + 0.5}});
            }
        }
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(label + (slope > 0 ? " (slope)" : ""), 4, 12);
        g.dispose();
        return img;
    }

    private static void face(Graphics2D g, double ox, double oy, int s, int[] rgb, double shade, double[][] corners) {
        Polygon p = new Polygon();
        for (double[] c : corners) {
            p.addPoint((int) Math.round(ox + (c[0] - c[2]) * 0.866 * s), (int) Math.round(oy + (c[0] + c[2]) * 0.5 * s - c[1] * s));
        }
        g.setColor(new Color(clamp(rgb[0] * shade), clamp(rgb[1] * shade), clamp(rgb[2] * shade)));
        g.fillPolygon(p);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    /** Face colour of a voxel; {@code face} 0 = top, 1 = +x, 2 = +z. */
    private static int[] color(TreeSpecies sp, int kind, int face, int x, int y, int z) {
        int hash = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        double jitter = 1.0 + ((hash >>> 8) % 17 - 8) / 100.0;
        int[] c;
        switch (kind) {
            case GRASS -> c = face == 0 ? new int[]{118, 160, 84} : new int[]{125, 96, 66};
            case SOIL -> c = new int[]{125, 96, 66};
            case ROOTED -> c = new int[]{150, 112, 78};
            case GROUND_WOOD -> c = bark(sp);
            default -> {
                int code = kind - OFFSET;
                if (TreeBlueprint.isLeaf(code)) {
                    c = code == TreeBlueprint.ALT_LEAF ? new int[]{205, 120, 200} : leaf(sp);
                    c = new int[]{(int) (c[0] * jitter), (int) (c[1] * jitter), (int) (c[2] * jitter)};
                } else if (code == TreeBlueprint.PROP_ROOT) {
                    c = new int[]{88, 66, 46};
                } else if (code == TreeBlueprint.LOG_Y && face == 0 || code == TreeBlueprint.LOG_X && face == 1
                    || code == TreeBlueprint.LOG_Z && face == 2) {
                    c = new int[]{214, 182, 120}; // sawn rings
                } else {
                    c = bark(sp);
                }
            }
        }
        return c;
    }

    private static int[] leaf(TreeSpecies sp) {
        return switch (sp) {
            case BIRCH -> new int[]{118, 160, 72};
            case SPRUCE, PINE, FIR, DEAD_SPRUCE -> new int[]{52, 92, 62};
            case JUNGLE -> new int[]{56, 150, 36};
            case ACACIA -> new int[]{112, 142, 40};
            case DARK_OAK, BURNED -> new int[]{44, 102, 34};
            case MANGROVE -> new int[]{82, 132, 40};
            case CHERRY -> new int[]{238, 172, 204};
            case AZALEA -> new int[]{94, 140, 58};
            default -> new int[]{62, 136, 46};
        };
    }

    private static int[] bark(TreeSpecies sp) {
        return switch (sp) {
            case BIRCH -> new int[]{222, 220, 210};
            case SPRUCE, PINE, FIR, DEAD_SPRUCE -> new int[]{74, 52, 30};
            case JUNGLE -> new int[]{110, 88, 46};
            case ACACIA -> new int[]{104, 98, 88};
            case DARK_OAK -> new int[]{62, 46, 28};
            case BURNED -> new int[]{40, 32, 26};
            case MANGROVE -> new int[]{92, 52, 40};
            case CHERRY -> new int[]{62, 32, 42};
            default -> new int[]{108, 84, 50};
        };
    }

    private static BufferedImage sheet(BufferedImage[][] tiles) {
        int rows = tiles.length;
        int cols = tiles[0].length;
        int[] colW = new int[cols];
        int[] rowH = new int[rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    colW[c] = Math.max(colW[c], tiles[r][c].getWidth());
                    rowH[r] = Math.max(rowH[r], tiles[r][c].getHeight());
                }
            }
        }
        int w = 0;
        for (int v : colW) {
            w += v + 4;
        }
        int h = 0;
        for (int v : rowH) {
            h += v + 4;
        }
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int y = 0;
        for (int r = 0; r < rows; r++) {
            int x = 0;
            for (int c = 0; c < cols; c++) {
                if (tiles[r][c] != null) {
                    // bottom-align so trees of a row stand on one line
                    g.drawImage(tiles[r][c], x, y + rowH[r] - tiles[r][c].getHeight(), null);
                }
                x += colW[c] + 4;
            }
            y += rowH[r] + 4;
        }
        g.dispose();
        return img;
    }

    /** Writes a single tree picture (used when tuning one species/size at a larger scale). */
    static void writeOne(TreeBlueprint bp, int px, File file) throws IOException {
        ImageIO.write(render(bp, px, 0.0, bp.species().serializedName()), "png", file);
    }
}
