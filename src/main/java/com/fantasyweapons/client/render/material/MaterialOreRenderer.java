package com.fantasyweapons.client.render.material;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.MaterialOreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws an ore block with its GeckoLib model and glowmask. The block is solid, so its own light is that of the brightest
 * air beside it; each ore is turned a quarter turn by its position so a vein never looks copy-pasted.
 */
public class MaterialOreRenderer extends GeoBlockRenderer<MaterialOreBlockEntity> {
    public MaterialOreRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(MaterialOreBlockEntity ore, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       int packedOverlay) {
        Level level = ore.getLevel();
        BlockPos pos = ore.getBlockPos();
        if (level != null) packedLight = brightestNeighbour(level, pos);
        int turn = Math.floorMod((int) (pos.asLong() * 0x9E3779B97F4A7C15L >>> 40), 4);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(turn * 90));
        poseStack.translate(-0.5, 0, -0.5);
        super.render(ore, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
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
        // the crystals break out of the stone and fragments float above it
        return new AABB(ore.getBlockPos()).inflate(0.3).expandTowards(0, 0.8, 0);
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
