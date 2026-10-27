package com.fantasyweapons.client.render.material;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.MaterialOreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import com.fantasyweapons.client.render.LeanGeoRenderer;
import com.fantasyweapons.client.render.LeanGlowLayer;

/**
 * Draws an ore block with its GeckoLib model and glowmask, fitted to the block (see {@link OreFit}). The block is solid, so
 * its own light is that of the brightest air beside it; each ore is turned a quarter turn by its position so a vein never
 * looks copy-pasted, and its floating fragments only show with open air above.
 */
public class MaterialOreRenderer extends GeoBlockRenderer<MaterialOreBlockEntity> implements LeanGeoRenderer<MaterialOreBlockEntity> {
    public MaterialOreRenderer() {
        super(new Model());
        addRenderLayer(new LeanGlowLayer<>(this));
    }

    @Override
    public void render(MaterialOreBlockEntity ore, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       int packedOverlay) {
        Level level = ore.getLevel();
        BlockPos pos = ore.getBlockPos();
        if (level != null) packedLight = brightestNeighbour(level, pos);
        int turn = Math.floorMod((int) (pos.asLong() * 0x9E3779B97F4A7C15L >>> 40), 4);
        OreFit fit = OreFit.of(ore.material());
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(turn * 90));
        poseStack.scale(fit.scale, fit.scale, fit.scale);
        // GeckoLib moves the model half a block across before drawing it: the body's centre lands on the block's centre
        poseStack.translate(-0.5 - fit.centerX, -fit.centerY, -0.5 - fit.centerZ);
        super.render(ore, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public void preRender(PoseStack poseStack, MaterialOreBlockEntity ore, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        Level level = ore.getLevel();
        BlockPos above = ore.getBlockPos().above();
        // fragments float in the open, never inside the block above
        OreFit.fragmentsHidden(model, level != null && level.getBlockState(above).isSolidRender(level, above));
        super.preRender(poseStack, ore, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    static int brightestNeighbour(Level level, BlockPos pos) {
        int block = 0, sky = 0;
        for (Direction d : Direction.values()) {
            int light = LevelRenderer.getLightColor(level, pos.relative(d));
            block = Math.max(block, LightTexture.block(light));
            sky = Math.max(sky, LightTexture.sky(light));
        }
        return LightTexture.pack(block, sky);
    }

    @Override
    public AABB getRenderBoundingBox(MaterialOreBlockEntity ore) {
        // fragments float above the block
        return new AABB(ore.getBlockPos()).expandTowards(0, 0.8, 0);
    }

    private static final class Model extends GeoModel<MaterialOreBlockEntity> {
        @Override
        public ResourceLocation getModelResource(MaterialOreBlockEntity ore) {
            return FantasyWeapons.id("geo/block/" + ore.material().oreAsset() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(MaterialOreBlockEntity ore) {
            return FantasyWeapons.id("textures/block/" + ore.material().oreAsset() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(MaterialOreBlockEntity ore) {
            return FantasyWeapons.id("animations/block/" + ore.material().oreAsset() + ".animation.json");
        }
    }
}
