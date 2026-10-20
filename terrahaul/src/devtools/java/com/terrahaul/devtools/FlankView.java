package com.terrahaul.devtools;

import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool behind {@code FadeStudy <seed> <outDir> flanks}: renders mountain flanks block
 * by block, coloured by the block the surface rule places there, seen obliquely from the downhill
 * side so that steep faces, ledges, snow patches and the snow line can be judged as in the game.
 * <p>
 * The terrain is the model's interpolated height field (no 3D detail or caves). For every visible
 * block the tool repeats what the rule computes: depth below the open air above and sideways
 * (the same ring of neighbouring tops), faces, the block slope and the smoothed slope, and the
 * column fields interpolated and jittered like the rule does. The biome, which the rule reads from
 * the game, is approximated from the climate the model hands to the biome source (snowy peaks
 * below a temperature of 0.2 on high ground, stony peaks above it, deserts in hot and dry
 * lowlands). Two pipelines can be drawn: {@code before}, a copy of the rule as it was with level
 * strata on every face, and {@code now}, which calls the same {@link Fades} functions as the rule.
 * <p>
 * Besides the picture, every panel reports speckle measures (as the other FadeStudy panels) and
 * patch statistics over the top view and the face view: the number of patches (4-connected
 * regions of one material), their mean and 90th-percentile size and the share of blocks in
 * patches of one or two blocks. A synthetic flank with straight V gullies and sharp spurs at
 * slopes of 1-3 blocks per block checks the rules on steeper ground than the current mountains
 * have.
 */
final class FlankView {
    // block ids of the picture (colours below)
    static final int AIR = -1;
    static final int STONE = 0;
    static final int ANDESITE = 1;
    static final int TUFF = 2;
    static final int DIORITE = 3;
    static final int CALCITE = 4;
    static final int GRANITE = 5;
    static final int DEEPSLATE = 6;
    static final int SMOOTH_BASALT = 7;
    static final int COBBLED_DEEPSLATE = 8;
    static final int COBBLESTONE = 9;
    static final int MOSSY_COBBLESTONE = 10;
    static final int GRAVEL = 11;
    static final int DIRT = 12;
    static final int COARSE_DIRT = 13;
    static final int GRASS = 14;
    static final int MOSS = 15;
    static final int SNOW = 16;
    static final int PACKED_ICE = 17;
    static final int SAND = 18;
    static final int SANDSTONE = 19;
    static final int SMOOTH_SANDSTONE = 20;
    static final int TERRACOTTA = 21;
    static final int BADLANDS = 22;
    static final int SNOW_LAYER = 23;
    /** Rough average colours of the block textures (grass tinted like a cool mountain biome). */
    private static final int[] RGB = {
        0x7D7D7D, 0x888889, 0x6C6D66, 0xBCBCBC, 0xDFE0DC, 0x956755, 0x505052, 0x48484E,
        0x4D4D50, 0x7A7A7A, 0x6A7660, 0x837F7E, 0x86603F, 0x77553B, 0x6E9A4E, 0x596D2D,
        0xF6FAFA, 0x8DB4FA, 0xDBCFA3, 0xD8CB9B, 0xDFD6AA, 0x985E43, 0xB0653A, 0xFFFFFF};
    static final String[] NAMES = {
        "stone", "andesite", "tuff", "diorite", "calcite", "granite", "deepslate", "smooth basalt",
        "cobbled deepslate", "cobblestone", "mossy cobblestone", "gravel", "dirt", "coarse dirt", "grass", "moss",
        "snow", "packed ice", "sand", "sandstone", "smooth sandstone", "terracotta", "badlands", "snow layer"};

    private static final int[] GREY = {TUFF, ANDESITE, STONE};
    private static final int[] DARK = {TUFF, SMOOTH_BASALT, DEEPSLATE};
    private static final int[] PALE = {CALCITE, DIORITE, STONE};
    private static final int[] SANDY = {SMOOTH_SANDSTONE, SANDSTONE, SANDSTONE};
    private static final int[] UNDER_SNOW = {CALCITE, DIORITE, STONE};
    private static final int[] UNDER_SAND = {SANDSTONE, SMOOTH_SANDSTONE, SANDSTONE};

    /** Biome classes of the approximation. */
    static final int SOIL = Fades.GROUND_SOIL;
    static final int ROCKY = Fades.GROUND_ROCK;
    static final int SNOWY = Fades.GROUND_SNOW;
    static final int SANDY_GROUND = Fades.GROUND_SAND;
    static final int HOT_PEAKS = 9;

    private static final int[] RING = {1, 2, 3, 4, 6, 8, 11, 15};

    /** Height field of a scene: the model's or a synthetic one. */
    interface Heights {
        double at(int x, int z);
    }

    /** One scene to draw: an area of {@code size} blocks around a centre, seen from {@code rotation}. */
    record Site(String name, int x, int z, int rotation, int size, Heights heights, ColumnSample climate) {
    }

    /** Everything the rule knows about one column. */
    static final class Column {
        int x;
        int z;
        int top;
        int slope;
        double smooth;
        boolean rugged;
        final int[] ringTop = new int[RING.length];
        final ColumnSample col = new ColumnSample();
        int ground;
        boolean frozen;
        double soilDepth;
        double outcrop;
        double sandOutcrop;
        double warp;
        int[] family;
        final Fades.Mountain m = new Fades.Mountain();
    }

    private final TerrainModel model;
    private final double[] blendScratch = new double[Fades.BLEND_FIELDS];

    FlankView(TerrainModel model) {
        this.model = model;
    }

    // =========================================================================================
    // finding sites
    // =========================================================================================

    /**
     * Picks one flank per category on a coarse grid: steep mountain ground of the granite, dark,
     * limestone and temperate provinces below the snow, a snowy peak, the snow line and a desert
     * mountain. The camera looks uphill.
     */
    List<Site> findSites(int size) {
        String[] names = {"granite", "dark", "limestone", "temperate", "snowy", "snowline", "desert", "foothill"};
        double[] best = new double[names.length];
        int[][] where = new int[names.length][];
        Arrays.fill(best, -1.0);
        ColumnSample s = new ColumnSample();
        for (int gx = -16000; gx <= 16000; gx += 112) {
            for (int gz = -16000; gz <= 16000; gz += 112) {
                model.sampleInto(gx, gz, s);
                if (s.volcano > 0.1 || s.canyon > 0.05 || s.mountain < 0.15) {
                    continue;
                }
                double peak = s.mountain * s.peakness;
                double score = s.steepness + 0.5 * s.mountain + 0.3 * s.cliff;
                for (int c = 0; c < names.length; c++) {
                    if (s.mountain < 0.45 && !names[c].equals("foothill")) {
                        continue;
                    }
                    boolean ok = switch (names[c]) {
                        case "granite" -> s.geology == Geology.GRANITE && rockZone(s);
                        case "dark" -> s.geology == Geology.DARK && rockZone(s);
                        case "limestone" -> s.geology == Geology.LIMESTONE && rockZone(s);
                        case "temperate" -> s.geology == Geology.TEMPERATE && rockZone(s);
                        case "snowy" -> s.temperature < -0.15 && peak > 0.4;
                        case "snowline" -> s.temperature > 0.12 && s.temperature < 0.24 && peak > 0.4;
                        case "desert" -> s.baseTemperature > 0.6 && s.humidity < 0.0 && s.temperature > 0.56 && peak < 0.3 && s.mountain > 0.5;
                        // where the mosaic gives way to the strata of the lowlands
                        case "foothill" -> Fades.mountainShare(s) > 0.3 && Fades.mountainShare(s) < 0.7 && s.temperature > 0.2 && s.temperature < 0.5
                            && Fades.smoothSlope(model, gx, gz) > 1.5;
                        default -> false;
                    };
                    if (ok && score > best[c]) {
                        best[c] = score;
                        where[c] = new int[]{gx, gz};
                    }
                }
            }
        }
        List<Site> sites = new ArrayList<>();
        for (int c = 0; c < names.length; c++) {
            if (where[c] == null) {
                System.out.println("no site for " + names[c]);
                continue;
            }
            int x = where[c][0];
            int z = where[c][1];
            sites.add(new Site(names[c], x, z, uphillRotation(x, z), size, model::interpolatedHeight, null));
        }
        return sites;
    }

    private static boolean rockZone(ColumnSample s) {
        return s.temperature > 0.24 && s.temperature < 0.5 && s.mountain * s.peakness > 0.4;
    }

    /** The rotation whose camera stands on the downhill side of the point. */
    int uphillRotation(int x, int z) {
        double hx = model.interpolatedHeight(x + 40, z) - model.interpolatedHeight(x - 40, z);
        double hz = model.interpolatedHeight(x, z + 40) - model.interpolatedHeight(x, z - 40);
        // camera directions of rotations 0..3: south, east, north, west of the scene
        double[][] cam = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int best = 0;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int r = 0; r < 4; r++) {
            double dot = -(hx * cam[r][0] + hz * cam[r][1]);
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    /**
     * A synthetic mountain flank: a cone falling at {@code slope} blocks per block from a summit at
     * the back of the scene, cut by straight V gullies between sharp spurs, with the climate and
     * province of a real column.
     */
    Site synthetic(String name, double slope, ColumnSample climate, int size, double summit) {
        int cx = climate.x;
        int cz = climate.z;
        int sx = cx;
        int sz = cz - (int) Math.max(67.0, size * 0.38);
        Heights h = (x, z) -> {
            double dx = x - sx;
            double dz = z - sz;
            double r = Math.sqrt(dx * dx + dz * dz);
            double a = Math.atan2(dz, dx);
            // triangle wave: 0 on gully lines, 1 on spur crests, linear in between (V gullies)
            double w = a * 9.0 / (2.0 * Math.PI) + 0.15 * Math.sin(r / 23.0);
            double tri = Math.abs(w - Math.floor(w) - 0.5) * 2.0;
            double depth = Math.min(r, 110.0) * 0.3 * Math.min(1.0, slope / 1.5);
            return summit - slope * r + depth * (tri - 0.5);
        };
        return new Site(name, cx, cz, 0, size, h, climate);
    }

    // =========================================================================================
    // columns
    // =========================================================================================

    /** Columns of a scene in view coordinates (u across, v towards the camera), for one pipeline. */
    Column[][] columns(Site site, boolean now) {
        Column[][] cols = new Column[site.size][site.size];
        for (int v = 0; v < site.size; v++) {
            for (int u = 0; u < site.size; u++) {
                Column c = new Column();
                int[] w = world(site, u, v);
                c.x = w[0];
                c.z = w[1];
                fill(site, c, now);
                cols[v][u] = c;
            }
        }
        return cols;
    }

    /** World position of view cell {@code (u, v)}: rotation 0 looks north, 1 west, 2 south, 3 east. */
    static int[] world(Site site, int u, int v) {
        int du = u - site.size / 2;
        int dv = v - site.size / 2;
        return switch (site.rotation) {
            case 1 -> new int[]{site.x + dv, site.z - du};
            case 2 -> new int[]{site.x - du, site.z - dv};
            case 3 -> new int[]{site.x - dv, site.z + du};
            default -> new int[]{site.x + du, site.z + dv};
        };
    }

    /** The rule's {@code updateColumn}, on the scene's height field ({@code now}: with the mosaic). */
    private void fill(Site site, Column c, boolean now) {
        int x = c.x;
        int z = c.z;
        Heights h = site.heights;
        if (site.climate == null) {
            c.col.copyFrom(model.sample((x + 2) & ~3, (z + 2) & ~3));
            Fades.blend(model, x, z, c.col, blendScratch);
            int gx = x + (int) Math.round(3.0 * Fades.patch(model, x, z, 13.0, 2));
            int gz = z + (int) Math.round(3.0 * Fades.patch(model, x, z, 13.0, 5));
            c.col.geology = model.sample((gx + 2) & ~3, (gz + 2) & ~3).geology;
        } else {
            // synthetic flank: the climate of a real column, cooled with the synthetic altitude
            c.col.copyFrom(site.climate);
            double height = h.at(x, z);
            c.col.height = height;
            c.col.mountain = 1.0;
            c.col.alpine = 0.8;
            c.col.peakness = 0.9;
            c.col.steepness = 0.8;
            c.col.cliff = 0.3;
            c.col.canyon = 0.0;
            c.col.volcano = 0.0;
            c.col.temperature = site.climate.baseTemperature - Math.max(0.0, height - 110.0) / 560.0;
        }
        c.top = (int) Math.floor(h.at(x, z));
        int max = 0;
        for (int i = -2; i <= 2; i += 4) {
            max = Math.max(max, Math.abs((int) Math.floor(h.at(x + i, z)) - c.top));
            max = Math.max(max, Math.abs((int) Math.floor(h.at(x, z + i)) - c.top));
        }
        c.slope = max;
        // Fades.smoothSlope on the scene's heights
        double gx = h.at(x + 4, z) - h.at(x - 4, z);
        double gz = h.at(x, z + 4) - h.at(x, z - 4);
        c.smooth = Math.sqrt(gx * gx + gz * gz) / 8.0;
        ColumnSample s = c.col;
        c.rugged = site.climate != null || c.slope >= 3 || s.steepness > 0.3 || s.cliff > 0.1 || s.trench > 0.15 || s.canyon > 0.1 || s.coastCliff > 0.2;
        for (int i = 0; i < RING.length; i++) {
            int k = RING[i];
            c.ringTop[i] = Math.min(Math.min(top(h, x + k, z), top(h, x - k, z)), Math.min(top(h, x, z + k), top(h, x, z - k)));
        }
        double thin = 1.0 - 0.4 * Fades.smoothstep(0.25, 0.55, s.mountain);
        c.soilDepth = Math.max(1.5, (3.0 + 4.0 * Math.max(0.0, 1.0 - c.smooth / 2.5) + 1.5 * Fades.patch(model, x, z, 23.0, 5)) * thin);
        c.ground = ground(s);
        c.frozen = c.ground == SNOWY && s.weirdness >= 0.0;
        c.warp = Fades.strataWarp(model, x, z);
        boolean mountainous = s.mountain > 0.3 || s.alpine > 0.3;
        c.family = switch (s.geology) {
            case Geology.DARK -> DARK;
            case Geology.LIMESTONE -> PALE;
            default -> !mountainous && c.ground == SANDY_GROUND ? SANDY : GREY;
        };
        c.outcrop = Fades.rockiness(model, x, z, c.smooth, 0.95);
        c.sandOutcrop = Fades.rockiness(model, x, z, c.smooth, 1.1);
        c.m.share = 0.0;
        if (!now || c.family == SANDY || Fades.mountainShare(s) <= 0.0) {
            return;
        }
        // Fades.hollow on the scene's heights
        double hollow = (h.at(x + 6, z) + h.at(x - 6, z) + h.at(x, z + 6) + h.at(x, z - 6)) * 0.125
            + (h.at(x + 14, z) + h.at(x - 14, z) + h.at(x, z + 14) + h.at(x, z - 14)) * 0.0625 - 0.75 * h.at(x, z);
        int ground = c.ground == HOT_PEAKS ? SOIL : c.ground;
        Fades.mountain(model, x, z, s, ground, c.smooth, hollow, c.m);
        if (ground == SOIL || ground == SANDY_GROUND) {
            // the rule takes the outcrops of mountain columns from the mosaic
            c.outcrop = c.m.rock;
            c.sandOutcrop = c.m.rock;
        }
    }

    private static int top(Heights h, int x, int z) {
        return (int) Math.floor(h.at(x, z));
    }

    /** Approximate biome class from the climate parameters (see the class comment). */
    static int ground(ColumnSample s) {
        double peak = s.mountain * s.peakness;
        if (s.temperature < -0.45 || s.temperature < 0.2 && peak > 0.35) {
            return SNOWY;
        }
        if (peak > 0.35) {
            return s.temperature < 0.55 ? ROCKY : HOT_PEAKS;
        }
        if (s.baseTemperature > 0.55 && s.humidity < 0.1) {
            return SANDY_GROUND;
        }
        return SOIL;
    }

    // =========================================================================================
    // materials
    // =========================================================================================

    /** Block at {@code (x, y, z)} of column {@code c}, by the rule's exposure arithmetic. */
    int block(Column c, int x, int y, int z, boolean now) {
        int vertical = c.top - y;
        int lateral = Integer.MAX_VALUE;
        if (c.rugged) {
            for (int i = 0; i < RING.length; i++) {
                if (c.ringTop[i] < y) {
                    lateral = RING[i] - 1;
                    break;
                }
            }
        }
        int d = Math.min(vertical, lateral);
        if (d > 15) {
            return STONE;
        }
        boolean face = lateral < vertical - 1 || c.slope >= 6;
        if (c.ground == HOT_PEAKS) {
            return BADLANDS;
        }
        if (now && Fades.inMosaic(model, c.m, x, y, z)) {
            int code = Fades.mountainBlock(model, c.m, x, y, z, d, d == 0 && vertical == 0, face);
            if (code != Fades.SOIL) {
                return material(c, code, d, x, z);
            }
            return soil(c, x, y, z, d, vertical, Fades.wobble(model, x, y, z), true);
        }
        return legacy(c, x, y, z, d, vertical, face, Fades.wobble(model, x, y, z));
    }

    /** The rule's block for a mosaic code. */
    private int material(Column c, int code, int d, int x, int z) {
        return switch (code) {
            case Fades.ROCK_PALE, Fades.ROCK_MID, Fades.ROCK_FRESH -> c.family[code];
            case Fades.ROCK_PROVINCE -> switch (c.col.geology) {
                case Geology.GRANITE -> GRANITE;
                case Geology.DARK -> ANDESITE;
                case Geology.LIMESTONE -> ANDESITE;
                case Geology.SEDIMENTARY -> TERRACOTTA;
                default -> COBBLESTONE;
            };
            case Fades.COBBLE -> c.family == DARK ? COBBLED_DEEPSLATE : COBBLESTONE;
            case Fades.MOSSY_COBBLE -> MOSSY_COBBLESTONE;
            case Fades.GRAVEL -> GRAVEL;
            case Fades.EARTH -> c.col.geology == Geology.GRANITE ? GRANITE : TERRACOTTA;
            case Fades.COARSE_DIRT -> COARSE_DIRT;
            case Fades.DIRT -> DIRT;
            case Fades.MOSS -> MOSS;
            case Fades.GRASS -> GRASS;
            case Fades.SAND -> SAND;
            case Fades.SANDSTONE -> SANDSTONE;
            case Fades.SNOW -> d == 0 && c.frozen && c.smooth < 1.05 && Fades.patch(model, x, z, 14.0, 7) > 0.55 ? PACKED_ICE : SNOW;
            default -> throw new IllegalArgumentException("code " + code);
        };
    }

    /** The rule as it was: level strata on every face, snow over calcite, bare-rock patches. */
    private int legacy(Column c, int x, int y, int z, int d, int vertical, boolean face, double n) {
        if (face) {
            return strata(c, x, y, z, n);
        }
        if (c.ground == SNOWY) {
            if (Fades.rockiness(model, x, z, c.smooth, 1.05) > 0.0) {
                return d <= 3 ? bareRock(c, x, z, d, n) : strata(c, x, y, z, n);
            }
            double t = d / Math.max(1.5, c.soilDepth * 0.6);
            if (t < 1.0) {
                return d == 0 && c.frozen && Fades.patch(model, x, z, 14.0, 7) > 0.55 ? PACKED_ICE : SNOW;
            }
            if (t < 3.0) {
                return UNDER_SNOW[Fades.stop(3, (t - 1.0) / 2.0, n)];
            }
            return strata(c, x, y, z, n);
        }
        if (c.ground == ROCKY) {
            return d <= 3 ? bareRock(c, x, z, d, n) : strata(c, x, y, z, n);
        }
        return soil(c, x, y, z, d, vertical, n, false);
    }

    /** The rule's paths for soil and sand ground (scree, top blocks and the soil profile). */
    private int soil(Column c, int x, int y, int z, int d, int vertical, double n, boolean now) {
        ColumnSample s = c.col;
        if (s.mountain > 0.3 && c.smooth > 0.75 && d <= 1 && s.peakness > 0.35 && Fades.patch(model, x, z, 9.0, 2) > 0.35) {
            return GRAVEL;
        }
        if (d == 0 && vertical == 0) {
            if (c.ground == SANDY_GROUND) {
                return c.sandOutcrop > 0.0 ? bareRock(c, x, z, 0, n) : SAND;
            }
            double t = s.temperature;
            if (!now && t < -0.3 && c.smooth < 1.0) {
                double cover = Math.min(0.9, (-0.3 - t) / 0.17 * 0.8);
                if (0.5 + 0.5 * Fades.patch(model, x, z, 16.0, 6) < cover) {
                    return SNOW;
                }
            }
            if (c.outcrop > 0.0) {
                return bareRock(c, x, z, 0, n);
            }
            return c.outcrop > -0.12 ? COARSE_DIRT : GRASS;
        }
        int dd = Math.max(d, 1);
        if (c.ground == SANDY_GROUND) {
            if (c.sandOutcrop > 0.0) {
                return dd <= 3 ? bareRock(c, x, z, dd, n) : strata(c, x, y, z, n);
            }
            double t = dd / Math.max(2.0, c.soilDepth * 0.8);
            if (t < 0.8 + 0.25 * n) {
                return SAND;
            }
            if (t < 2.6) {
                return UNDER_SAND[Fades.stop(3, (t - 0.8) / 1.8, n)];
            }
            return strata(c, x, y, z, n);
        }
        if (c.outcrop > 0.0) {
            return dd <= 3 ? bareRock(c, x, z, dd, n) : strata(c, x, y, z, n);
        }
        double t = dd / c.soilDepth + 0.2 * n;
        if (t < 0.75) {
            return DIRT;
        }
        if (t < 1.1) {
            return COARSE_DIRT;
        }
        if (t < 2.2) {
            return c.family[Fades.stop(3, (t - 1.1) / 1.1, n)];
        }
        return strata(c, x, y, z, n);
    }

    private int bareRock(Column c, int x, int z, int d, double n) {
        return c.family[Fades.bareRockStop(model, 3, x, z, d, n)];
    }

    private int strata(Column c, int x, int y, int z, double n) {
        double period = c.col.geology == Geology.SEDIMENTARY ? Fades.SEDIMENTARY_PERIOD : Fades.ROCK_PERIOD;
        return c.family[Fades.strataStop(model, 3, x, y, z, c.warp, period, n)];
    }

    // =========================================================================================
    // pictures and measures
    // =========================================================================================

    /** A rendered scene: the picture and the material grids the measures are taken on. */
    record Panel(BufferedImage image, int[][] topView, int[][] faceView) {
    }

    /**
     * Draws the scene obliquely: every column shows its top face and the blocks of its front face
     * down to the top of the column in front of it, painted from the back to the front. Top faces
     * are lit, front faces shaded, and every block gets a slight brightness jitter like a texture.
     * With {@code snowLayers}, top faces in air cold enough for vanilla's snow layer are white.
     */
    Panel render(Site site, Column[][] cols, boolean now, int scale, boolean snowLayers) {
        int size = site.size;
        double k = 0.5;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Column[] row : cols) {
            for (Column c : row) {
                minY = Math.min(minY, c.top);
                maxY = Math.max(maxY, c.top);
            }
        }
        int width = size * scale;
        int height = (int) Math.ceil((size + 1) * scale * k) + (maxY - minY + 2) * scale;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xC8D7E6));
        g.fillRect(0, 0, width, height);
        g.dispose();
        int[][] topView = new int[size][size];
        for (int v = 0; v < size; v++) {
            for (int u = 0; u < size; u++) {
                Column c = cols[v][u];
                int frontTop = v + 1 < size ? cols[v + 1][u].top : minY - 1;
                int lowest = Math.max(frontTop + 1, c.top - 80);
                for (int y = lowest; y <= c.top; y++) {
                    int b = block(c, c.x, y, c.z, now);
                    int sy = (int) Math.round((v + 1) * scale * k) + (maxY - y) * scale;
                    // the front row is cut by the edge of the scene: drawn darker, as a section
                    fill(img, u * scale, sy, scale, scale, shade(b, v + 1 < size ? 0.78 : 0.5, c.x, y, c.z));
                }
                int b = block(c, c.x, c.top, c.z, now);
                topView[v][u] = b;
                if (snowLayers && snowLayer(c)) {
                    b = SNOW_LAYER;
                }
                int sy0 = (int) Math.round(v * scale * k) + (maxY - c.top) * scale;
                int sy1 = (int) Math.round((v + 1) * scale * k) + (maxY - c.top) * scale;
                fill(img, u * scale, sy0, scale, Math.max(1, sy1 - sy0), shade(b, 1.0, c.x, c.top + 1, c.z));
            }
        }
        return new Panel(img, topView, faceView(cols, now, minY, maxY));
    }

    /**
     * What the camera sees looking straight along the view direction, one cell per block: for every
     * column of the screen and every height, the nearest exposed block.
     */
    private int[][] faceView(Column[][] cols, boolean now, int minY, int maxY) {
        int size = cols.length;
        int h = maxY - minY + 1;
        int[][] m = new int[h][size];
        for (int[] row : m) {
            Arrays.fill(row, AIR);
        }
        for (int u = 0; u < size; u++) {
            for (int y = minY; y <= maxY; y++) {
                for (int v = size - 1; v >= 0; v--) {
                    Column c = cols[v][u];
                    if (c.top >= y) {
                        // faces of the front row are the cut edge of the scene, not terrain
                        boolean exposed = v + 1 < size && cols[v + 1][u].top < y;
                        m[maxY - y][u] = exposed ? block(c, c.x, y, c.z, now) : AIR;
                        break;
                    }
                }
            }
        }
        return m;
    }

    /**
     * Whether vanilla's freeze feature lays a snow layer on the column: the biome's temperature,
     * lowered with altitude above Y 80, below 0.15 (noise of the vanilla adjustment left out).
     */
    private static boolean snowLayer(Column c) {
        double biome = switch (c.ground) {
            case SNOWY -> -0.7;
            case ROCKY -> 1.0;
            case SANDY_GROUND, HOT_PEAKS -> 2.0;
            default -> 0.5;
        };
        return biome - Math.max(0, c.top + 1 - 80) * 0.05 / 40.0 < 0.15 && biome < 1.5;
    }

    private static int shade(int b, double light, int x, int y, int z) {
        int rgb = RGB[b];
        long hsh = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        hsh ^= hsh >>> 13;
        hsh *= 0x9E3779B97F4A7C15L;
        double jitter = 1.0 + 0.05 * (((hsh >>> 40) & 0xFF) / 255.0 - 0.5) * 2.0;
        double f = light * jitter;
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f);
        int gr = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f);
        int bl = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (gr << 8) | bl;
    }

    private static void fill(BufferedImage img, int x0, int y0, int w, int h, int rgb) {
        for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y0 + h); y++) {
            for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x0 + w); x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    /**
     * Speckle and patch statistics, as {@link #measure}, on the exposed surface itself and
     * independent of the view: every
     * block open to the air above or sideways (down to the lowest neighbouring top) is a node,
     * joined to its face neighbours that are surface blocks too and to the blocks one up or down
     * in the next column (so the tops of a slope's steps join). A block is speckle if it differs
     * from at least three quarters of its neighbours (and they are at least three); patches are
     * connected regions of one material. Blocks of the scene's border columns are left out.
     */
    String surfaceMeasure(Column[][] cols, boolean now) {
        int size = cols.length;
        // surface blocks per column: y from lowest[v][u] to top
        int[][] lowest = new int[size][size];
        int[][] offset = new int[size][size];
        int total = 0;
        for (int v = 0; v < size; v++) {
            for (int u = 0; u < size; u++) {
                int low = cols[v][u].top;
                if (u > 0 && v > 0 && u + 1 < size && v + 1 < size) {
                    low = Math.min(low, Math.min(Math.min(cols[v][u - 1].top, cols[v][u + 1].top), Math.min(cols[v - 1][u].top, cols[v + 1][u].top)) + 1);
                }
                lowest[v][u] = Math.min(low, cols[v][u].top);
                offset[v][u] = total;
                total += cols[v][u].top - lowest[v][u] + 1;
            }
        }
        int[] mat = new int[total];
        for (int v = 0; v < size; v++) {
            for (int u = 0; u < size; u++) {
                Column c = cols[v][u];
                for (int y = lowest[v][u]; y <= c.top; y++) {
                    mat[offset[v][u] + y - lowest[v][u]] = block(c, c.x, y, c.z, now);
                }
            }
        }
        long blocks = 0;
        long speckle = 0;
        long[] speckleBy = new long[RGB.length];
        int[] parent = new int[total];
        for (int i = 0; i < total; i++) {
            parent[i] = i;
        }
        int[] nb = new int[DIRECTIONS.length];
        for (int v = 1; v + 1 < size; v++) {
            for (int u = 1; u + 1 < size; u++) {
                for (int y = lowest[v][u]; y <= cols[v][u].top; y++) {
                    int i = offset[v][u] + y - lowest[v][u];
                    int count = 0;
                    for (int[] dd : DIRECTIONS) {
                        int uu = u + dd[0];
                        int vv = v + dd[1];
                        int yy = y + dd[2];
                        if (yy >= lowest[vv][uu] && yy <= cols[vv][uu].top) {
                            nb[count++] = offset[vv][uu] + yy - lowest[vv][uu];
                        }
                    }
                    blocks++;
                    int differ = 0;
                    for (int k = 0; k < count; k++) {
                        if (mat[nb[k]] != mat[i]) {
                            differ++;
                        } else {
                            union(parent, i, nb[k]);
                        }
                    }
                    if (count >= 3 && differ * 4 >= count * 3) {
                        speckle++;
                        speckleBy[mat[i]]++;
                    }
                }
            }
        }
        java.util.Map<Integer, Integer> sizes = new java.util.HashMap<>();
        long[] counts = new long[RGB.length];
        for (int v = 1; v + 1 < size; v++) {
            for (int u = 1; u + 1 < size; u++) {
                for (int y = lowest[v][u]; y <= cols[v][u].top; y++) {
                    int i = offset[v][u] + y - lowest[v][u];
                    sizes.merge(find(parent, i), 1, Integer::sum);
                    counts[mat[i]]++;
                }
            }
        }
        int[] sorted = sizes.values().stream().mapToInt(Integer::intValue).sorted().toArray();
        long tinyBlocks = 0;
        int tiny = 0;
        for (int s : sorted) {
            if (s <= 2) {
                tiny++;
                tinyBlocks += s;
            }
        }
        long acc = 0;
        int areaMedian = 0;
        for (int s : sorted) {
            acc += s;
            if (acc * 2 >= blocks) {
                areaMedian = s;
                break;
            }
        }
        StringBuilder mix = new StringBuilder();
        for (int b = 0; b < counts.length; b++) {
            if (counts[b] * 200 >= blocks) {
                mix.append(String.format(Locale.ROOT, " %s %.0f%%", NAMES[b], 100.0 * counts[b] / blocks));
            }
        }
        mix.append(" | speckle by material:");
        for (int b = 0; b < speckleBy.length; b++) {
            if (speckleBy[b] * 10 >= speckle && speckle > 0) {
                mix.append(String.format(Locale.ROOT, " %s %.0f%%", NAMES[b], 100.0 * speckleBy[b] / speckle));
            }
        }
        return String.format(Locale.ROOT,
            "%-34s blocks %6d  speckle %.2f%%  patches<=2 per 10k %.1f (%.2f%% of blocks)  patches %d: median %d, mean %.1f, block-weighted median %d |%s",
            "surface (3D)", blocks, 100.0 * speckle / Math.max(1, blocks), 1.0e4 * tiny / Math.max(1, blocks), 100.0 * tinyBlocks / Math.max(1, blocks),
            sorted.length, sorted.length == 0 ? 0 : sorted[sorted.length / 2], blocks / (double) Math.max(1, sorted.length), areaMedian, mix);
    }

    /** Surface neighbours: the six faces, and the next column one block up or down (steps of a slope). */
    private static final int[][] DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 0, 1}, {-1, 0, 1}, {0, 1, 1}, {0, -1, 1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, -1}, {0, -1, -1}};

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    /**
     * Speckle and patch statistics of a material grid: the share of blocks that differ from at
     * least three of their four neighbours, islands of one or two blocks per 10 000 blocks (and
     * the share of blocks in them), and the patches (4-connected regions of one material): count,
     * median and mean size, and the size of the patch the median block lies in; plus the
     * material shares.
     */
    static String measure(String name, int[][] m) {
        int h = m.length;
        int w = m[0].length;
        long blocks = 0;
        long isolated = 0;
        long[] counts = new long[RGB.length];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = m[j][i];
                if (c == AIR) {
                    continue;
                }
                blocks++;
                counts[c]++;
                int differ = 0;
                int neighbours = 0;
                int[][] nb = {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}};
                for (int[] p : nb) {
                    if (p[0] < 0 || p[1] < 0 || p[0] >= w || p[1] >= h || m[p[1]][p[0]] == AIR) {
                        continue;
                    }
                    neighbours++;
                    if (m[p[1]][p[0]] != c) {
                        differ++;
                    }
                }
                if (neighbours >= 3 && differ >= 3) {
                    isolated++;
                }
            }
        }
        List<Integer> sizes = patches(m);
        long tiny = 0;
        long tinyBlocks = 0;
        for (int s : sizes) {
            if (s <= 2) {
                tiny++;
                tinyBlocks += s;
            }
        }
        int[] sorted = sizes.stream().mapToInt(Integer::intValue).sorted().toArray();
        double mean = blocks / (double) Math.max(1, sorted.length);
        int p50 = sorted.length == 0 ? 0 : sorted[sorted.length / 2];
        // size of the patch that the median block lies in (patch sizes weighted by area)
        long acc = 0;
        int areaMedian = 0;
        for (int s : sorted) {
            acc += s;
            if (acc * 2 >= blocks) {
                areaMedian = s;
                break;
            }
        }
        StringBuilder mix = new StringBuilder();
        for (int b = 0; b < counts.length; b++) {
            if (counts[b] * 200 >= blocks) {
                mix.append(String.format(Locale.ROOT, " %s %.0f%%", NAMES[b], 100.0 * counts[b] / blocks));
            }
        }
        return String.format(Locale.ROOT,
            "%-34s blocks %6d  speckle %.2f%%  islands<=2 per 10k %.1f (%.2f%% of blocks)  patches %d: median %d, mean %.1f, block-weighted median %d |%s",
            name, blocks, 100.0 * isolated / Math.max(1, blocks), 1.0e4 * tiny / Math.max(1, blocks), 100.0 * tinyBlocks / Math.max(1, blocks),
            sorted.length, p50, mean, areaMedian, mix);
    }

    /** Sizes of the 4-connected regions of one material. */
    private static List<Integer> patches(int[][] m) {
        int h = m.length;
        int w = m[0].length;
        boolean[][] seen = new boolean[h][w];
        int[] stack = new int[w * h];
        List<Integer> sizes = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (seen[j][i] || m[j][i] == AIR) {
                    continue;
                }
                int c = m[j][i];
                int size = 0;
                int sp = 0;
                stack[sp++] = j * w + i;
                seen[j][i] = true;
                while (sp > 0) {
                    int p = stack[--sp];
                    size++;
                    int pi = p % w;
                    int pj = p / w;
                    if (pi > 0 && !seen[pj][pi - 1] && m[pj][pi - 1] == c) {
                        seen[pj][pi - 1] = true;
                        stack[sp++] = p - 1;
                    }
                    if (pi + 1 < w && !seen[pj][pi + 1] && m[pj][pi + 1] == c) {
                        seen[pj][pi + 1] = true;
                        stack[sp++] = p + 1;
                    }
                    if (pj > 0 && !seen[pj - 1][pi] && m[pj - 1][pi] == c) {
                        seen[pj - 1][pi] = true;
                        stack[sp++] = p - w;
                    }
                    if (pj + 1 < h && !seen[pj + 1][pi] && m[pj + 1][pi] == c) {
                        seen[pj + 1][pi] = true;
                        stack[sp++] = p + w;
                    }
                }
                sizes.add(size);
            }
        }
        return sizes;
    }

    /**
     * Cost of the surface rule's work, both pipelines, on 96 x 96 columns of the scene, as the
     * rule spends it: once per column the column values ({@link #fill}, which repeats the rule's
     * {@code updateColumn}, and for the mosaic {@link Fades#hollow} and {@link Fades#mountain}),
     * then the material of the 16 blocks below the top, where the rule does its work (deeper
     * blocks return at once). The two pipelines alternate over eleven rounds; the median of the
     * last seven is printed, per column and per block. Times are the thread's CPU time, so other
     * processes on a busy machine disturb them less.
     */
    void bench(Site site) {
        int n = 96;
        int runs = 11;
        long sink = 0;
        double[][] perColumn = new double[2][runs];
        double[][] perBlock = new double[2][runs];
        Column[][] cols = new Column[n][n];
        for (int rep = 0; rep < runs; rep++) {
            for (int p = 0; p < 2; p++) {
                boolean now = p == 1;
                long t0 = CPU.getCurrentThreadCpuTime();
                for (int v = 0; v < n; v++) {
                    for (int u = 0; u < n; u++) {
                        Column c = new Column();
                        int[] w = world(site, u - n / 2 + site.size / 2, v - n / 2 + site.size / 2);
                        c.x = w[0];
                        c.z = w[1];
                        fill(site, c, now);
                        cols[v][u] = c;
                    }
                }
                long t1 = CPU.getCurrentThreadCpuTime();
                for (Column[] row : cols) {
                    for (Column c : row) {
                        for (int y = c.top; y > c.top - 16; y--) {
                            sink += block(c, c.x, y, c.z, now);
                        }
                    }
                }
                long t2 = CPU.getCurrentThreadCpuTime();
                perColumn[p][rep] = (t1 - t0) / (double) (n * n);
                perBlock[p][rep] = (t2 - t1) / (double) (n * n * 16);
            }
        }
        double[] column = {median(perColumn[0], 4), median(perColumn[1], 4)};
        double[] block = {median(perBlock[0], 4), median(perBlock[1], 4)};
        double[] total = {column[0] + 16 * block[0], column[1] + 16 * block[1]};
        System.out.printf(Locale.ROOT, "  cost %s: per column (values + 16 blocks) before %.0f ns, now %.0f ns (%+.0f%%); column values %.0f -> %.0f ns, per block %.0f -> %.0f ns%n",
            site.name, total[0], total[1], 100.0 * (total[1] - total[0]) / total[0], column[0], column[1], block[0], block[1]);
        if (sink == 42) {
            System.out.println();
        }
    }

    private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();

    /** Median of the values after the first {@code skip} (warm-up). */
    private static double median(double[] values, int skip) {
        double[] v = Arrays.copyOfRange(values, skip, values.length);
        Arrays.sort(v);
        return v[v.length / 2];
    }

    /** Writes a sheet of panels side by side with captions. */
    static void write(File file, List<String> captions, List<BufferedImage> images) throws Exception {
        int w = 0;
        int h = 0;
        for (BufferedImage i : images) {
            w += i.getWidth() + 10;
            h = Math.max(h, i.getHeight());
        }
        BufferedImage sheet = new BufferedImage(Math.max(1, w - 10), h + 18, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(225, 230, 235));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int x = 0;
        g.setColor(Color.BLACK);
        for (int i = 0; i < images.size(); i++) {
            g.drawImage(images.get(i), x, 18, null);
            g.drawString(captions.get(i), x + 4, 13);
            x += images.get(i).getWidth() + 10;
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }
}
