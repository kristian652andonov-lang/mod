package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.material.MaterialOreBlock;
import com.fantasyweapons.refinery.ArcaneRefineryBlock;
import com.fantasyweapons.refinery.RefineryPartBlock;
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
            // as hard as deepslate diamond ore; a faint glow of their own lights the caves around them
            ORES.put(m, REGISTER.register(m.oreId(), () -> new MaterialOreBlock(m, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 6))));
        }
    }

    /** An iron furnace: as tough as a blast furnace, mined with a pickaxe; glows brightly while it refines. */
    public static final DeferredBlock<ArcaneRefineryBlock> ARCANE_REFINERY = REGISTER.register("arcane_refinery",
            () -> new ArcaneRefineryBlock(refineryProperties().lightLevel(s -> s.getValue(ArcaneRefineryBlock.LIT) ? 13 : 5)));
    public static final DeferredBlock<RefineryPartBlock> REFINERY_PART = REGISTER.register("arcane_refinery_part",
            () -> new RefineryPartBlock(refineryProperties().noLootTable().lightLevel(s -> 3)));

    private static BlockBehaviour.Properties refineryProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    private ModBlocks() {
    }
}
