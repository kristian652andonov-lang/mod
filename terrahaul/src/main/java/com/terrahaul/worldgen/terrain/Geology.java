package com.terrahaul.worldgen.terrain;

/**
 * Geological provinces. Each province fixes which rock is exposed on cliffs, peaks and canyon
 * walls, so neighbouring mountains share a coherent appearance instead of randomly alternating
 * stone types. Ordinary ore-bearing stone stays the bulk material in every province.
 */
public final class Geology {
    /** Plain stone and andesite: the most common province. */
    public static final int TEMPERATE = 0;
    /** Granite-dominated ranges with pinkish exposed faces. */
    public static final int GRANITE = 1;
    /** Dark peaks: deepslate, tuff and basalt faces. */
    public static final int DARK = 2;
    /** Pale limestone-like cliffs: calcite and diorite. */
    public static final int LIMESTONE = 3;
    /** Layered sedimentary rock: sandstone, terracotta and stone bands. */
    public static final int SEDIMENTARY = 4;
    /** Volcanic: basalt, blackstone, tuff and magma near vents. */
    public static final int VOLCANIC = 5;

    public static final int COUNT = 6;

    private static final String[] NAMES = {"temperate", "granite", "dark", "limestone", "sedimentary", "volcanic"};

    private Geology() {
    }

    public static String name(int id) {
        return id >= 0 && id < COUNT ? NAMES[id] : "unknown";
    }
}
