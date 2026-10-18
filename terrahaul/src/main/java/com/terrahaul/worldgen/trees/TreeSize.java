package com.terrahaul.worldgen.trees;

/** Age/size class of a generated tree. Plain Java, see {@link TreeSpecies}. */
public enum TreeSize {
    YOUNG("young"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    GIANT("giant"),
    /**
     * Rare old trees far larger than vanilla ones, reaching up to {@link TreeBlueprint#ANCIENT_REACH}
     * blocks from the trunk; species without an ancient form grow as giants.
     */
    ANCIENT("ancient");

    private final String name;

    TreeSize(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    /** The size class with the given serialized name, or {@code null}. */
    public static TreeSize byName(String name) {
        for (TreeSize s : values()) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

    public TreeSize smaller() {
        return this == YOUNG ? YOUNG : values()[ordinal() - 1];
    }

    public boolean atLeast(TreeSize other) {
        return ordinal() >= other.ordinal();
    }
}
