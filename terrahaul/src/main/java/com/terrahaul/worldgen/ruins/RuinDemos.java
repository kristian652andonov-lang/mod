package com.terrahaul.worldgen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic sites on flat ground at the origin, for the devtools: every house layout, style and
 * state, every other kind of trail ruin and its variants, every sunken layout, signpost and
 * lamp-post style can be drawn at a chosen decay and inspected without searching the world.
 */
public final class RuinDemos {
    /** Width of the stream under a demo bridge. */
    public static final int BRIDGE_SPAN = 4;

    private RuinDemos() {
    }

    /**
     * A trail ruin standing alone on flat ground with its floor at y 64, facing north (the trail
     * lies north). A bridge's stream runs along x between z = 0 and z = {@link #BRIDGE_SPAN} - 1.
     */
    public static RuinPlan ruin(int kind, int variant, int style, int state, int climate, RuinMaterials materials, double decay, double wetness,
                                long seed) {
        RuinSpec spec = kind == TrailRuin.BRIDGE ? LandmarkPlanner.bridgeSpec(BRIDGE_SPAN) : RuinSites.spec(kind, variant, style);
        Canvas.Frame f = new Canvas.Frame(0, 0, 1, 0, 0, 1);
        int path = kind == TrailRuin.BRIDGE ? BRIDGE_SPAN : spec.footpath() ? 7 : 0;
        String loot = RuinSites.lootTable(kind, variant, climate, RuinBiomes.PLAINS, materials);
        TrailRuin site = new TrailRuin(spec.w() / 2, 65, spec.d() / 2, seed, -40, -40, 50, 50, kind, variant, style, state, climate, materials, f,
            spec, kind == TrailRuin.BRIDGE ? 65 : 64, decay, wetness, RuinNames.name(seed), spec.doorU(), -7, loot, path);
        return RuinSites.planOf(site);
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

    /** A signpost of a kind and style, with placeholder text; the trail lies north of it. */
    public static RuinPlan signpost(int kind, int style, RuinMaterials materials, long seed) {
        double[] arms = switch (kind) {
            case Signpost.JUNCTION -> new double[]{0.1, 1.7, 3.6};
            case Signpost.END -> new double[]{Math.PI / 2};
            default -> new double[]{0.0, Math.PI};
        };
        Signpost s = new Signpost(8, 66, 8, seed, kind, style, 8, 2, arms, materials, 0.7, RuinCell.NORTH);
        List<SignpostPlanner.Board> boards = new ArrayList<>();
        switch (kind) {
            case Signpost.JUNCTION, Signpost.WAYPOST -> {
                String[][] names = {{"Ashford", "620 m"}, {"@lake|Lake", "350 m"}, {"@waterfall|Waterfall", "1.2 km"}};
                for (int i = 0; i < arms.length; i++) {
                    int dir = RuinCell.dirOf(Math.cos(arms[i]), Math.sin(arms[i]));
                    String[] front = {"", names[i][0], names[i][1] + " →", ""};
                    String[] back = {"", names[i][0], "← " + names[i][1], ""};
                    boards.add(new SignpostPlanner.Board(dir, i % 2 == 0 ? 3 : 2, new RuinPlan.SignSpec(front, back, materials.darkWood(), false)));
                }
            }
            case Signpost.MILESTONE -> {
                String[] front = {"", "@chapel|Chapel", "1.4 km →", ""};
                boards.add(new SignpostPlanner.Board(RuinCell.NORTH, 1, new RuinPlan.SignSpec(front, new String[]{"", "", "", ""}, false, false)));
            }
            default -> {
                String[] front = {"@path_ends|Path ends", "", "@lake|Lake", "↑ 120 m"};
                boards.add(new SignpostPlanner.Board(RuinCell.NORTH, 2, new RuinPlan.SignSpec(front, new String[]{"", "", "", ""}, false, false)));
            }
        }
        s.boards = boards.toArray(new SignpostPlanner.Board[0]);
        return SignpostPlanner.plan(null, s);
    }

    /** Lamp posts of one style in a row along x, each in its own chunk, the trail to their north; every third keeps its lantern. */
    public static List<LightStretch.Post> lampPosts(int style, double decay, double wetness, long seed, int count) {
        RuinMaterials mat = new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD,
            RuinMaterials.SPRUCE);
        List<LightStretch.Post> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new LightStretch.Post(8 + 16 * i, 8, RuinCell.NORTH, style, decay, wetness, false, mat, seed * 31L + i, i % 3 == 1));
        }
        return out;
    }

    /** Name of the condition of a post. */
    public static String lampState(LightStretch.Post p) {
        return LampPostPlanner.STATE_NAMES[LampPostPlanner.state(p)];
    }
}
