package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.MaterialOreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FantasyWeapons.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaterialOreBlockEntity>> MATERIAL_ORE = REGISTER.register("material_ore",
            () -> BlockEntityType.Builder.of(MaterialOreBlockEntity::new, ModBlocks.ORES.values().stream().map(h -> (Block) h.get()).toArray(Block[]::new))
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fantasyweapons.refinery.ArcaneRefineryBlockEntity>> ARCANE_REFINERY =
            REGISTER.register("arcane_refinery", () -> BlockEntityType.Builder.of(com.fantasyweapons.refinery.ArcaneRefineryBlockEntity::new,
                    ModBlocks.ARCANE_REFINERY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity>> LEGENDARY_CRAFTING_TABLE =
            REGISTER.register("legendary_crafting_table", () -> BlockEntityType.Builder.of(com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity::new,
                    ModBlocks.LEGENDARY_CRAFTING_TABLE.get()).build(null));

    private ModBlockEntities() {
    }
}
