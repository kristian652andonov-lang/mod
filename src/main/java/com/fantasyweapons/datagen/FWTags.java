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
    /** Every legendary pickaxe, shovel and axe. */
    public static final TagKey<Item> LEGENDARY_TOOLS = TagKey.create(Registries.ITEM, FantasyWeapons.id("legendary_tools"));

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
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.ARCANE_REFINERY.get(), ModBlocks.REFINERY_PART.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.ARCANE_REFINERY.get(), ModBlocks.REFINERY_PART.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.LEGENDARY_CRAFTING_TABLE.get(), ModBlocks.LEGENDARY_CRAFTING_TABLE_PART.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.LEGENDARY_CRAFTING_TABLE.get(), ModBlocks.LEGENDARY_CRAFTING_TABLE_PART.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.WORLDFORGE.get(), ModBlocks.WORLDFORGE_PART.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.UPGRADING_TABLE.get(), ModBlocks.UPGRADING_TABLE_PART.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.UPGRADING_TABLE.get(), ModBlocks.UPGRADING_TABLE_PART.get());
            tag(BlockTags.NEEDS_DIAMOND_TOOL).add(ModBlocks.WORLDFORGE.get(), ModBlocks.WORLDFORGE_PART.get());
            tag(BlockTags.WITHER_IMMUNE).add(ModBlocks.WORLDFORGE.get(), ModBlocks.WORLDFORGE_PART.get());
            tag(BlockTags.DRAGON_IMMUNE).add(ModBlocks.WORLDFORGE.get(), ModBlocks.WORLDFORGE_PART.get());
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
            // legendary tools: tools like any other of their kind (for what keys on pickaxes, axes and shovels), and take
            // books of the enchantments a mining tool takes (those they already carry are turned away by the items)
            for (Material m : Material.values()) {
                for (var kind : com.fantasyweapons.tool.ToolKind.values()) {
                    Item tool = ModItems.tool(m, kind);
                    tag(switch (kind) {
                        case PICKAXE -> net.minecraft.tags.ItemTags.PICKAXES;
                        case SHOVEL -> net.minecraft.tags.ItemTags.SHOVELS;
                        case AXE -> net.minecraft.tags.ItemTags.AXES;
                    }).add(tool);
                    tag(net.minecraft.tags.ItemTags.MINING_ENCHANTABLE).add(tool);
                    tag(net.minecraft.tags.ItemTags.MINING_LOOT_ENCHANTABLE).add(tool);
                    tag(Tags.Items.MINING_TOOL_TOOLS).add(tool);
                    tag(LEGENDARY_TOOLS).add(tool);
                }
            }
            // armor: enchantable like any armor
            for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
                for (var e : ModItems.ARMOR.get(set).entrySet()) {
                    Item item = e.getValue().get();
                    var slotTags = switch (e.getKey()) {
                        case HELMET -> java.util.List.of(net.minecraft.tags.ItemTags.HEAD_ARMOR, net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE);
                        case CHESTPLATE -> java.util.List.of(net.minecraft.tags.ItemTags.CHEST_ARMOR, net.minecraft.tags.ItemTags.CHEST_ARMOR_ENCHANTABLE);
                        case LEGGINGS -> java.util.List.of(net.minecraft.tags.ItemTags.LEG_ARMOR, net.minecraft.tags.ItemTags.LEG_ARMOR_ENCHANTABLE);
                        default -> java.util.List.of(net.minecraft.tags.ItemTags.FOOT_ARMOR, net.minecraft.tags.ItemTags.FOOT_ARMOR_ENCHANTABLE);
                    };
                    for (var t : slotTags) tag(t).add(item);
                    tag(net.minecraft.tags.ItemTags.ARMOR_ENCHANTABLE).add(item);
                    tag(net.minecraft.tags.ItemTags.EQUIPPABLE_ENCHANTABLE).add(item);
                }
            }
        }
    }
}
