package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import com.terrahaul.blocks.BranchBlock;
import com.terrahaul.blocks.CountedDecorBlock;
import com.terrahaul.blocks.LeafLitterBlock;
import com.terrahaul.blocks.RockBlock;
import com.terrahaul.blocks.ShrubBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Decorative natural blocks: pebbles, rocks, branches, twigs, litter, pine cones and shrubs. */
public final class TerrahaulBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Terrahaul.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Terrahaul.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Terrahaul.MOD_ID);

    /** Rock materials, in the order of their texture/model definitions. */
    public static final List<String> STONES = List.of("stone", "mossy", "andesite", "granite", "diorite", "deepslate", "tuff",
        "sandstone", "red_sandstone", "blackstone");
    public static final List<String> WOODS = List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    public static final List<String> LITTERS = List.of("oak_leaf_litter", "birch_leaf_litter", "spruce_needle_litter", "jungle_leaf_litter",
        "acacia_leaf_litter", "dark_oak_leaf_litter", "mangrove_leaf_litter", "azalea_leaf_litter", "dry_leaf_litter");
    public static final List<String> SHRUBS = List.of("leafy_bush", "dense_shrub", "alpine_shrub", "dry_shrub");

    public static final Map<String, DeferredBlock<CountedDecorBlock>> PEBBLES = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<RockBlock>> ROCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<BranchBlock>> BRANCHES = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<LeafLitterBlock>> LITTER = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<ShrubBlock>> SHRUB = new LinkedHashMap<>();
    public static final DeferredBlock<BranchBlock> TWIGS;
    public static final DeferredBlock<CountedDecorBlock> PINE_CONE;
    private static final List<DeferredItem<BlockItem>> ALL_ITEMS = new java.util.ArrayList<>();

    static {
        for (String stone : STONES) {
            MapColor color = stoneColor(stone);
            PEBBLES.put(stone, block(stone + "_pebbles", p -> new CountedDecorBlock(p), BlockBehaviour.Properties.of().mapColor(color)
                .noCollission().instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY).noOcclusion()));
            ROCKS.put(stone, block(stone + "_rock", p -> new RockBlock(p), BlockBehaviour.Properties.of().mapColor(color)
                .strength(0.8f, 3.0f).sound(SoundType.STONE).pushReaction(PushReaction.DESTROY).noOcclusion()));
        }
        for (String wood : WOODS) {
            BRANCHES.put(wood, block(wood + "_branch", p -> new BranchBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                .noCollission().instabreak().sound(SoundType.WOOD).ignitedByLava().pushReaction(PushReaction.DESTROY).noOcclusion()));
        }
        TWIGS = block("twigs", p -> new BranchBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
            .noCollission().instabreak().sound(SoundType.WOOD).ignitedByLava().pushReaction(PushReaction.DESTROY).noOcclusion());
        PINE_CONE = block("pine_cone", p -> new CountedDecorBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL)
            .noCollission().instabreak().sound(SoundType.WOOD).ignitedByLava().pushReaction(PushReaction.DESTROY).noOcclusion());
        for (String litter : LITTERS) {
            LITTER.put(litter, block(litter, p -> new LeafLitterBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                .replaceable().noCollission().instabreak().sound(SoundType.AZALEA_LEAVES).ignitedByLava().pushReaction(PushReaction.DESTROY).noOcclusion()));
        }
        for (String shrub : SHRUBS) {
            SHRUB.put(shrub, block(shrub, p -> new ShrubBlock(p), BlockBehaviour.Properties.of()
                .mapColor(shrub.equals("dry_shrub") ? MapColor.WOOD : MapColor.PLANT)
                .noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava()
                .pushReaction(PushReaction.DESTROY).noOcclusion()));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("terrahaul", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.terrahaul"))
        .icon(() -> new ItemStack(ROCKS.get("mossy").get()))
        .displayItems((params, output) -> ALL_ITEMS.forEach(item -> output.accept(item.get())))
        .build());

    private TerrahaulBlocks() {
    }

    private static <B extends Block> DeferredBlock<B> block(String name, java.util.function.Function<BlockBehaviour.Properties, B> factory,
                                                            BlockBehaviour.Properties properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        ALL_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static MapColor stoneColor(String stone) {
        return switch (stone) {
            case "mossy" -> MapColor.COLOR_GREEN;
            case "granite" -> MapColor.DIRT;
            case "diorite" -> MapColor.QUARTZ;
            case "deepslate" -> MapColor.DEEPSLATE;
            case "tuff" -> MapColor.TERRACOTTA_GRAY;
            case "sandstone" -> MapColor.SAND;
            case "red_sandstone" -> MapColor.COLOR_ORANGE;
            case "blackstone" -> MapColor.COLOR_BLACK;
            default -> MapColor.STONE;
        };
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
