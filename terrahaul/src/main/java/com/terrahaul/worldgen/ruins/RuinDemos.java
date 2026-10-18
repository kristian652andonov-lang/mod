package com.terrahaul.worldgen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic sites on flat ground at the origin, for the devtools: every house type, sunken
 * layout, signpost and lamp-post style can be drawn at a chosen decay and inspected without
 * searching the world for one.
 */
public final class RuinDemos {
    private RuinDemos() {
    }

    /** A house standing alone on flat ground with its floor at y 64, facing north (the trail lies north). */
    public static RuinPlan house(int template, int climate, RuinMaterials materials, double decay, double wetness, long seed) {
        HousePlanner.Spec spec = HousePlanner.spec(template);
        Canvas.Frame f = new Canvas.Frame(0, 0, 1, 0, 0, 1);
        TrailRuin site = new TrailRuin(spec.w() / 2, 65, spec.d() / 2, seed, -30, -30, 40, 40, template, climate, materials, f, 64,
            decay, wetness, RuinNames.name(seed), spec.doorU(), -7, "minecraft:chests/village/village_plains_house", 7);
        return HousePlanner.plan(site);
    }

    /** A sunken ruin on a flat sea floor at y 30 under 33 blocks of water. */
    public static RuinPlan sunken(int kind, boolean warm, int stone, double decay, long seed) {
        int[] size = SunkenPlanner.size(kind, seed);
        Canvas.Frame f = new Canvas.Frame(0, 0, 1, 0, 0, 1);
        RuinMaterials mat = new RuinMaterials(stone, RuinMaterials.OAK, RuinMaterials.OAK, RuinMaterials.ROOF_STONE, RuinMaterials.OAK);
        SunkenRuin site = new SunkenRuin(size[0] / 2, 32, size[1] / 2, seed, -10, -10, size[0] + 10, size[1] + 10, kind, f, 30, decay, warm,
            false, mat, size[0], size[1], 33);
        return SunkenPlanner.plan(site);
    }

    /** A fingerpost with three arms, or a trail-end board, with placeholder text. */
    public static RuinPlan signpost(boolean junction, RuinMaterials materials, long seed) {
        double[] arms = junction ? new double[]{0.1, 1.7, 3.6} : new double[]{Math.PI / 2};
        Signpost s = new Signpost(8, 66, 8, seed, junction ? Signpost.JUNCTION : Signpost.END, 8, 2, arms, materials, 0.7);
        List<SignpostPlanner.Board> boards = new ArrayList<>();
        if (junction) {
            String[][] names = {{"Ashford", "620 m"}, {"@lake|Lake", "350 m"}, {"@waterfall|Waterfall", "1.2 km"}};
            for (int i = 0; i < 3; i++) {
                int dir = RuinCell.dirOf(Math.cos(arms[i]), Math.sin(arms[i]));
                String[] front = {"", names[i][0], names[i][1] + " →", ""};
                String[] back = {"", names[i][0], "← " + names[i][1], ""};
                boards.add(new SignpostPlanner.Board(dir, i % 2 == 0 ? 3 : 2, new RuinPlan.SignSpec(front, back, materials.darkWood(), false)));
            }
        } else {
            String[] front = {"@path_ends|Path ends", "", "@lake|Lake", "↑ 120 m"};
            boards.add(new SignpostPlanner.Board(RuinCell.NORTH, 2, new RuinPlan.SignSpec(front, new String[]{"", "", "", ""}, false, false)));
        }
        s.boards = boards.toArray(new SignpostPlanner.Board[0]);
        return SignpostPlanner.plan(null, s);
    }

    /** Name of the condition of post {@code i} of a demo row made by {@link #lampPosts}. */
    public static String lampState(int style, double decay, long seed, int count, int i) {
        int[][] posts = new int[count][];
        for (int k = 0; k < count; k++) {
            posts[k] = new int[]{8 + 16 * k, 8, RuinCell.NORTH};
        }
        LightStretch s = new LightStretch(8, 66, 8, seed, 0, 0, 16 * count, 16, posts, style, decay, 0.7, false, null);
        return LampPostPlanner.STATE_NAMES[LampPostPlanner.state(s, i)];
    }

    /** Lamp posts of one style in a row along x, each in its own chunk, the trail to their north. */
    public static List<RuinPlan> lampPosts(int style, double decay, double wetness, long seed, int count) {
        int[][] posts = new int[count][];
        for (int i = 0; i < count; i++) {
            posts[i] = new int[]{8 + 16 * i, 8, RuinCell.NORTH};
        }
        RuinMaterials mat = new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD,
            RuinMaterials.SPRUCE);
        LightStretch s = new LightStretch(8, 66, 8, seed, 0, 0, 16 * count, 16, posts, style, decay, wetness, false, mat);
        List<RuinPlan> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(LampPostPlanner.plan(s, i));
        }
        return out;
    }
}
