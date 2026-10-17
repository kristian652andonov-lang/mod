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

    /**
     * The Worldforge: 21 blocks wide and deep, 22 high, seen from far off; while it forges, the weapon lies over the anvil under the
     * heart's beam, turning, rising and growing as the forging goes on, at full light.
     */
    public static final class Worldforge extends Block<com.fantasyweapons.forge.WorldforgeBlockEntity> {
        public Worldforge() {
            super("worldforge");
        }

        @Override
        public void render(com.fantasyweapons.forge.WorldforgeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                           int light, int overlay) {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
            net.minecraft.world.item.ItemStack made = be.forging().getItem(0);
            if (made.isEmpty()) made = be.output().getItem(0);
            Level level = be.getLevel();
            if (made.isEmpty() || level == null) return;
            float time = level.getGameTime() + partialTick;
            float done = be.total() <= 0 ? 1 : Math.min(1, be.progress() / (float) be.total());
            var at = be.forgePoint();
            BlockPos pos = be.getBlockPos();
            poseStack.pushPose();
            poseStack.translate(at.x - pos.getX(), at.y - pos.getY() + 0.1 * done + 0.05 * Math.sin(time * 0.06), at.z - pos.getZ());
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 0.6f));
            float size = 0.7f + 0.6f * done;
            poseStack.scale(size, size, size);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(made, net.minecraft.world.item.ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, buffers, level, (int) pos.asLong());
            poseStack.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(com.fantasyweapons.forge.WorldforgeBlockEntity be) {
            return be.renderBox();
        }

        @Override
        public int getViewDistance() {
            return 160;
        }
    }

    /**
     * The Legendary Upgrading Table: the tool in its vise lies flat on the rune plate at the model's tool bone, head
     * towards the grindstone, its grip where the vise closes (and jolts with the bone at each pulse); its refined material
     * stands in the copper cup at the catalyst bone.
     */
    public static final class UpgradingTable extends Block<com.fantasyweapons.tool.UpgradingTableBlockEntity> {
        private static final String TOOL = "legendary_upgrading_table_tool_slot", CUP = "legendary_upgrading_table_catalyst_slot";
        /**
         * The tool laid down: its handle (up its model) towards the grindstone, the face of its head up. In the model's
         * render space (x mirrored) the grindstone is towards -x.
         */
        private static final org.joml.Quaternionf LAID = new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(0, 0, -1, -1, 0, 0, 0, 1, 0));

        public UpgradingTable() {
            super("legendary_upgrading_table");
            addRenderLayer(new software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<>(this) {
                @Override
                protected net.minecraft.world.item.ItemStack getStackForBone(software.bernie.geckolib.cache.object.GeoBone bone,
                                                                              com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    return switch (bone.getName()) {
                        case TOOL -> be.items().getStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.TOOL);
                        case CUP -> be.items().getStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.CATALYST);
                        default -> null;
                    };
                }

                @Override
                protected void renderStackForBone(PoseStack pose, software.bernie.geckolib.cache.object.GeoBone bone, net.minecraft.world.item.ItemStack stack,
                                                  com.fantasyweapons.tool.UpgradingTableBlockEntity be, MultiBufferSource buffers, float partialTick,
                                                  int light, int overlay) {
                    if (stack.isEmpty()) return;
                    pose.pushPose();
                    net.minecraft.world.item.ItemDisplayContext ctx;
                    if (bone.getName().equals(TOOL)) {
                        // held by its grip, as in the hand, at about 0.6 of its size
                        pose.mulPose(LAID);
                        pose.scale(0.6f, 0.6f, 0.6f);
                        ctx = net.minecraft.world.item.ItemDisplayContext.NONE;
                    } else {
                        pose.translate(0, 0.1, 0);
                        pose.scale(0.38f, 0.38f, 0.38f);
                        ctx = net.minecraft.world.item.ItemDisplayContext.FIXED;
                    }
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack, ctx, light, overlay, pose, buffers, be.getLevel(),
                            (int) be.getBlockPos().asLong());
                    pose.popPose();
                }
            });
        }
    }

    public static <T extends Item & GeoAnimatable> GeoItemRenderer<T> item(String model) {
        GeoItemRenderer<T> r = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(FantasyWeapons.id(model)));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }
}
