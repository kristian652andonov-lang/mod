package com.fantasyweapons.datagen;

import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.LimitCount;

import java.util.Set;

/**
 * Ore drops: one raw material (Fortune adds up to two more), the ore block itself with Silk Touch. The experience comes
 * from the block (DropExperienceBlock) and is not given with Silk Touch.
 */
public class FWBlockLoot extends BlockLootSubProvider {
    public FWBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        var fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        for (Material m : Material.values()) {
            Block ore = ModBlocks.ORES.get(m).get();
            add(ore, createSilkTouchDispatchTable(ore, applyExplosionDecay(ore, LootItem.lootTableItem(ModItems.RAW.get(m).get())
                    .apply(ApplyBonusCount.addUniformBonusCount(fortune))
                    .apply(LimitCount.limitCount(IntRange.range(1, 3))))));
        }
        dropSelf(ModBlocks.ARCANE_REFINERY.get());
        dropSelf(ModBlocks.LEGENDARY_CRAFTING_TABLE.get());
        dropSelf(ModBlocks.UPGRADING_TABLE.get());
        dropSelf(ModBlocks.WORLDFORGE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.REGISTER.getEntries().stream().map(h -> (Block) h.get())
                .filter(b -> !(b instanceof com.fantasyweapons.machine.MachinePartBlock)).toList();
    }
}
