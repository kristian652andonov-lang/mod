package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class FWTags {
    /** Every raw legendary material (what the Arcane Refinery accepts). */
    public static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, FantasyWeapons.id("raw_materials"));
    /** Every refined legendary material. */
    public static final TagKey<Item> REFINED_MATERIALS = TagKey.create(Registries.ITEM, FantasyWeapons.id("refined_materials"));
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, FantasyWeapons.id("ores"));
    public static final TagKey<Item> ORE_ITEMS = TagKey.create(Registries.ITEM, FantasyWeapons.id("ores"));

    private FWTags() {
    }

    public static final class Blocks extends BlockTagsProvider {
        public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, FantasyWeapons.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (Material m : Material.values()) {
                Block ore = ModBlocks.ORES.get(m).get();
                tag(ORES).add(ore);
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ore);
                tag(BlockTags.NEEDS_DIAMOND_TOOL).add(ore);
            }
            tag(Tags.Blocks.ORES).addTag(ORES);
        }
    }

    public static final class Items extends ItemTagsProvider {
        public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, CompletableFuture<TagLookup<Block>> blockTags,
                     ExistingFileHelper files) {
            super(output, lookup, blockTags, FantasyWeapons.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (Material m : Material.values()) {
                tag(RAW_MATERIALS).add(ModItems.RAW.get(m).get());
                tag(REFINED_MATERIALS).add(ModItems.REFINED.get(m).get());
                tag(ORE_ITEMS).add(ModItems.ORES.get(m).get());
            }
            tag(Tags.Items.ORES).addTag(ORE_ITEMS);
        }
    }
}
