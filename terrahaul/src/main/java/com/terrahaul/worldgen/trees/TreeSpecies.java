package com.terrahaul.worldgen.trees;

/**
 * Tree species handled by the procedural tree generator. Pine, fir and swamp oak are shape variants
 * built from the spruce and oak block families; the blocks of every species are mapped in
 * {@link TreeMaterials}. Pale oak does not exist in Minecraft 1.21.1 (it was added in 1.21.4) and is
 * therefore absent.
 * <p>
 * This enum, {@link TreeSize}, {@link TreeBlueprint} and {@link TreeShapes} are plain Java on
 * purpose: the development tool {@code TreeStudy} builds and renders trees with them without
 * starting Minecraft.
 */
public enum TreeSpecies {
    OAK("oak"),
    SWAMP_OAK("swamp_oak"),
    BIRCH("birch"),
    SPRUCE("spruce"),
    PINE("pine"),
    FIR("fir"),
    JUNGLE("jungle"),
    ACACIA("acacia"),
    DARK_OAK("dark_oak"),
    MANGROVE("mangrove"),
    CHERRY("cherry"),
    AZALEA("azalea"),
    DEAD("dead"),
    DEAD_SPRUCE("dead_spruce"),
    /** Fire-killed snags on volcano slopes: charred, leafless trunks with broken tops. */
    BURNED("burned");

    private final String name;

    TreeSpecies(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    /** The species with the given serialized name, or {@code null}. */
    public static TreeSpecies byName(String name) {
        for (TreeSpecies s : values()) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

    public boolean isConifer() {
        return this == SPRUCE || this == PINE || this == FIR || this == DEAD_SPRUCE;
    }
}
