package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.material.MaterialOreBlock;
import com.fantasyweapons.forge.LegendaryCraftingTableBlock;
import com.fantasyweapons.machine.MachinePartBlock;
import com.fantasyweapons.refinery.ArcaneRefineryBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(FantasyWeapons.MOD_ID);

    public static final Map<Material, DeferredBlock<MaterialOreBlock>> ORES = new EnumMap<>(Material.class);

    static {
        for (Material m : Material.values()) {
            // as hard as deepslate diamond ore; a faint glow of their own lights the caves around them. Their stone sits just
            // inside the block (the crystals reach its edges), so the faces of the blocks around them are still drawn
            ORES.put(m, REGISTER.register(m.oreId(), () -> new MaterialOreBlock(m, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 6).noOcclusion())));
        }
    }

    /** An iron furnace: as tough as a blast furnace, mined with a pickaxe; glows brightly while it refines. */
    public static final DeferredBlock<ArcaneRefineryBlock> ARCANE_REFINERY = REGISTER.register("arcane_refinery",
            () -> new ArcaneRefineryBlock(refineryProperties().lightLevel(s -> s.getValue(ArcaneRefineryBlock.LIT) ? 13 : 5)));
    public static final DeferredBlock<MachinePartBlock> REFINERY_PART = REGISTER.register("arcane_refinery_part",
            () -> new MachinePartBlock(refineryProperties().noLootTable().lightLevel(s -> 3), ModBlocks.ARCANE_REFINERY));

    /**
     * The 9x9 crafting table the legendary weapons and armor are forged on, three blocks wide and two high: as tough as an
     * anvil's stone, and lit by its own starlight (brighter while it crafts).
     */
    public static final DeferredBlock<LegendaryCraftingTableBlock> LEGENDARY_CRAFTING_TABLE = REGISTER.register("legendary_crafting_table",
            () -> new LegendaryCraftingTableBlock(tableProperties().lightLevel(s -> s.getValue(LegendaryCraftingTableBlock.CRAFTING) ? 12 : 7)));
    public static final DeferredBlock<MachinePartBlock> LEGENDARY_CRAFTING_TABLE_PART = REGISTER.register("legendary_crafting_table_part",
            () -> new MachinePartBlock(tableProperties().noLootTable().lightLevel(s -> 3), ModBlocks.LEGENDARY_CRAFTING_TABLE));

    /**
     * The Worldforge, seven blocks every way, where the ultimate weapons are forged: as hard to break as obsidian and nearly
     * as blast-proof, lit by its own pylons (blazing while it forges).
     */
    public static final DeferredBlock<com.fantasyweapons.forge.WorldforgeBlock> WORLDFORGE = REGISTER.register("worldforge",
            () -> new com.fantasyweapons.forge.WorldforgeBlock(worldforgeProperties().lightLevel(s -> s.getValue(com.fantasyweapons.forge.WorldforgeBlock.CRAFTING) ? 15 : 9)));
    public static final DeferredBlock<com.fantasyweapons.forge.WorldforgePartBlock> WORLDFORGE_PART = REGISTER.register("worldforge_part",
            () -> new com.fantasyweapons.forge.WorldforgePartBlock(worldforgeProperties().noLootTable().lightLevel(s -> 4)));

    static {
        // it was a plain block called the Legendary Forge before it had its model
        REGISTER.addAlias(FantasyWeapons.id("legendary_forge"), FantasyWeapons.id("legendary_crafting_table"));
    }

    private static BlockBehaviour.Properties refineryProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties tableProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.ANVIL)
                .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties worldforgeProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(40f, 1200f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)
                .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    private ModBlocks() {
    }
}
