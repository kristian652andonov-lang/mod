package com.fantasyweapons.client.render.machine;

import com.fantasyweapons.FantasyWeapons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The GeckoLib renderers of the three-wide, two-high machines (the Arcane Refinery, the Legendary Crafting Table): the
 * machine in the world, all six blocks of it drawn by its core, and as an item.
 */
public final class MachineRenderers {
    private MachineRenderers() {
    }

    public static class Block<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> {
        /** {@code model} names the block's geo model, animations and texture (with its glow mask). */
        public Block(String model) {
            super(new DefaultedBlockGeoModel<>(FantasyWeapons.id(model)));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        /** Lit by the brightest of the air around its lower blocks (its own blocks are solid to light). */
        @Override
        public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
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

        /** The machine reaches a block to each side of its core and up past its second row (whichever way it faces). */
        @Override
        public AABB getRenderBoundingBox(T be) {
            BlockPos p = be.getBlockPos();
            return new AABB(p.getX() - 1.3, p.getY(), p.getZ() - 1.3, p.getX() + 2.3, p.getY() + 2.6, p.getZ() + 2.3);
        }

        @Override
        public boolean shouldRenderOffScreen(T be) {
            return true;
        }
    }

    /**
     * The Legendary Crafting Table: while it forges, the item being forged lies over the grid as on an anvil (under the
     * gyroscope), hovering, slowly turning and growing as the forging goes on, at full light.
     */
    public static final class Table extends Block<com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity> {
        public Table() {
            super("legendary_crafting_table");
        }

        @Override
        public void render(com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                           int light, int overlay) {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
            net.minecraft.world.item.ItemStack made = be.forging().getItem(0);
            Level level = be.getLevel();
            if (made.isEmpty() || level == null) return;
            float time = level.getGameTime() + partialTick;
            float done = be.total() <= 0 ? 0 : Math.min(1, be.progress() / (float) be.total());
            var at = be.forgePoint();
            BlockPos pos = be.getBlockPos();
            poseStack.pushPose();
            poseStack.translate(at.x - pos.getX(), at.y - pos.getY() + 0.025 * Math.sin(time * 0.08), at.z - pos.getZ());
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 0.8f));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            float size = 0.45f + 0.3f * done;
            poseStack.scale(size, size, size);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(made, net.minecraft.world.item.ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, buffers, level, (int) pos.asLong());
            poseStack.popPose();
        }
    }

    public static <T extends Item & GeoAnimatable> GeoItemRenderer<T> item(String model) {
        GeoItemRenderer<T> r = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(FantasyWeapons.id(model)));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }
}
