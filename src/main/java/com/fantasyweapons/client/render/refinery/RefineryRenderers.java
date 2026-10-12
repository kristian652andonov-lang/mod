package com.fantasyweapons.client.render.refinery;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.refinery.ArcaneRefineryBlockEntity;
import com.fantasyweapons.refinery.ArcaneRefineryItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Arcane Refinery's GeckoLib renderers: the machine in the world (all six blocks of it) and as an item. */
public final class RefineryRenderers {
    private RefineryRenderers() {
    }

    public static final class Block extends GeoBlockRenderer<ArcaneRefineryBlockEntity> {
        public Block() {
            super(new DefaultedBlockGeoModel<>(FantasyWeapons.id("arcane_refinery")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        /** Lit by the brightest of the air around its lower blocks (its own blocks are solid to light). */
        @Override
        public void render(ArcaneRefineryBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            Level level = be.getLevel();
            if (level != null) {
                BlockPos pos = be.getBlockPos();
                int block = LightTexture.block(light), sky = LightTexture.sky(light);
                for (Direction d : Direction.values()) {
                    for (int up = 0; up <= 1; up++) {
                        int l = LevelRenderer.getLightColor(level, pos.above(up).relative(d));
                        block = Math.max(block, LightTexture.block(l));
                        sky = Math.max(sky, LightTexture.sky(l));
                    }
                }
                light = LightTexture.pack(block, sky);
            }
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        }

        @Override
        public AABB getRenderBoundingBox(ArcaneRefineryBlockEntity be) {
            BlockPos p = be.getBlockPos();
            return new AABB(p.getX() - 1.3, p.getY(), p.getZ() - 1.3, p.getX() + 2.3, p.getY() + 2.1, p.getZ() + 2.3);
        }

        @Override
        public boolean shouldRenderOffScreen(ArcaneRefineryBlockEntity be) {
            return true;
        }
    }

    public static GeoItemRenderer<ArcaneRefineryItem> item() {
        GeoItemRenderer<ArcaneRefineryItem> r = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(FantasyWeapons.id("arcane_refinery")));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }
}
