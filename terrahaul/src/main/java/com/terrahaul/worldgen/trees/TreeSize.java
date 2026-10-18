package com.terrahaul.worldgen.trees;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Age/size class of a generated tree. */
public enum TreeSize implements StringRepresentable {
    YOUNG("young"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    GIANT("giant");

    public static final Codec<TreeSize> CODEC = StringRepresentable.fromEnum(TreeSize::values);

    private final String name;

    TreeSize(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public TreeSize smaller() {
        return this == YOUNG ? YOUNG : values()[ordinal() - 1];
    }
}
