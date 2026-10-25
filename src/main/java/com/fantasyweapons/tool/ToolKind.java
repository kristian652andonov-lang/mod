package com.fantasyweapons.tool;

import com.fantasyweapons.text.Tr;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** The three legendary tools each legendary material makes: what each digs, and what it is called. */
public enum ToolKind {
    PICKAXE("pickaxe", "Pickaxe", BlockTags.MINEABLE_WITH_PICKAXE),
    SHOVEL("shovel", "Shovel", BlockTags.MINEABLE_WITH_SHOVEL),
    AXE("axe", "Axe", BlockTags.MINEABLE_WITH_AXE);

    private final String id;
    private final String displayName;
    private final TagKey<Block> mines;

    ToolKind(String id, String displayName, TagKey<Block> mines) {
        this.id = id;
        this.displayName = displayName;
        this.mines = mines;
    }

    /** Suffix of the tool's item id and asset names ({@code void_crystal_pickaxe}). */
    public String id() {
        return id;
    }

    public String displayName() {
        return Tr.s(displayName);
    }

    /** The blocks it is made for. */
    public TagKey<Block> mines() {
        return mines;
    }
}
