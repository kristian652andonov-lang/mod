package com.fantasyweapons.client.render;

import com.fantasyweapons.entity.ThrownWeaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the actual weapon model of a {@link ThrownWeaponEntity}, lying flat and spinning like a disc. */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeaponEntity> {
    /** Degrees per tick of spin; shared with the trail so the blade tips line up. */
    public static final float SPIN = 24f;
    public static final float SCALE = 2.4f;

    public ThrownWeaponRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ThrownWeaponEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = displayStack(e);
        if (stack.isEmpty()) return;
        pose.pushPose();
        float t = e.tickCount + partial;
        pose.translate(0, 0.25, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-t * SPIN));
        pose.mulPose(Axis.ZP.rotationDegrees(12));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    /**
     * A copy of the weapon with its own GeckoLib animatable id, so the flying model only plays its idle loop and never
     * the cast / transform animations still being triggered on the stack in the player's hand (which made it flip).
     */
    private static ItemStack displayStack(ThrownWeaponEntity e) {
        ItemStack src = e.stack();
        if (src.isEmpty()) return src;
        if (e.displayStack == null || e.displayStack.getItem() != src.getItem()) {
            ItemStack copy = src.copy();
            copy.set(software.bernie.geckolib.GeckoLibConstants.STACK_ANIMATABLE_ID_COMPONENT.get(), Long.MIN_VALUE + e.getId());
            e.displayStack = copy;
        }
        return e.displayStack;
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownWeaponEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
