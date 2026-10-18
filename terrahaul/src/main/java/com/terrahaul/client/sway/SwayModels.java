package com.terrahaul.client.sway;

import com.terrahaul.Terrahaul;
import com.terrahaul.blocks.ShrubBlock;
import com.terrahaul.config.TerrahaulConfig;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Marks the models of grass, ferns, flowers, saplings, crops and bushes for vegetation sway.
 * <p>
 * Only models drawn exclusively in the cutout layer are wrapped, because the sway lives in the
 * cutout core shader. Sway is skipped entirely when it is disabled in the client config at
 * resource load time, or when a mod that replaces the chunk renderer (Sodium, Embeddium, Iris,
 * Oculus) is installed: those renderers do not use the vanilla core shaders.
 */
public final class SwayModels {
    private static final List<String> RENDERER_MODS = List.of("sodium", "embeddium", "iris", "oculus", "rubidium");

    private SwayModels() {
    }

    /** True when sway can work with the active renderer. */
    public static boolean supported() {
        for (String mod : RENDERER_MODS) {
            if (ModList.get().isLoaded(mod)) {
                return false;
            }
        }
        return true;
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        if (!TerrahaulConfig.vegetationSway() || TerrahaulConfig.animationQuality() == TerrahaulConfig.AnimationQuality.OFF) {
            return;
        }
        if (!supported()) {
            Terrahaul.LOGGER.info("Vegetation sway disabled: a chunk renderer replacement is installed");
            return;
        }
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        RandomSource random = RandomSource.create(42L);
        int wrapped = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            float flexibility = flexibility(block);
            if (flexibility <= 0.0F) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model == null || model instanceof SwayBakedModel || !cutoutOnly(model, state, random)) {
                    continue;
                }
                float base = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1.0F : 0.0F;
                models.put(key, new SwayBakedModel(model, base, flexibility));
                wrapped++;
            }
        }
        Terrahaul.LOGGER.debug("Vegetation sway enabled for {} block state models", wrapped);
    }

    private static boolean cutoutOnly(BakedModel model, BlockState state, RandomSource random) {
        ChunkRenderTypeSet types = model.getRenderTypes(state, random, ModelData.EMPTY);
        if (types.isEmpty()) {
            return false;
        }
        for (RenderType type : types) {
            if (type != RenderType.cutout()) {
                return false;
            }
        }
        return true;
    }

    /** How far a plant bends; 0 for blocks that never sway. */
    private static float flexibility(Block block) {
        if (block instanceof SmallDripleafBlock) {
            return 0.0F;
        }
        if (block instanceof TallGrassBlock || block instanceof DoublePlantBlock) {
            return 1.0F;
        }
        if (block instanceof FlowerBlock) {
            return 0.85F;
        }
        if (block instanceof CropBlock || block instanceof StemBlock) {
            return 0.65F;
        }
        if (block instanceof SaplingBlock) {
            return 0.55F;
        }
        if (block instanceof DeadBushBlock || block instanceof SweetBerryBushBlock || block instanceof ShrubBlock) {
            return 0.45F;
        }
        if (block instanceof AzaleaBlock) {
            return 0.25F;
        }
        return 0.0F;
    }
}
