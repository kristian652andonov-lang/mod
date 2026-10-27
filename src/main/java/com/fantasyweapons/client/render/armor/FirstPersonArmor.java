package com.fantasyweapons.client.render.armor;

import com.fantasyweapons.armor.FantasyArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** The legendary chestplate's sleeve on a first-person arm (mods that draw armored arms in first person, like Punchy). */
public final class FirstPersonArmor {
    /** A body in its rest pose: GeckoLib lays the armor's bones over it. */
    private static HumanoidModel<LivingEntity> rest;

    private FirstPersonArmor() {
    }

    public static boolean wearsLegendaryChest(LivingEntity wearer) {
        return wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FantasyArmorItem;
    }

    /**
     * Draws the arm of the worn legendary chestplate on {@code armPart}, a first-person arm posed (by whoever animates it)
     * in the model space the pose stack is in.
     */
    public static void renderSleeve(ModelPart armPart, LivingEntity wearer, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int light,
                                    float partialTick) {
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (rest == null) {
            rest = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
            // a fresh model counts as a baby until an entity renderer says otherwise, and GeckoLib would halve the armor
            rest.young = false;
        }
        if (!(GeoRenderProvider.of(chest).getGeoArmorRenderer(wearer, chest, EquipmentSlot.CHEST, rest) instanceof ArmorRenderers.Worn worn)) return;
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        // into the arm as it is posed; GeckoLib then puts the armor's arm where an arm rests (5 px out, 2 down), so take
        // that rest offset back off
        armPart.translateAndRotate(poseStack);
        poseStack.translate((left ? -5f : 5f) / 16f, -2f / 16f, 0);
        worn.prepForRender(wearer, chest, EquipmentSlot.CHEST, rest, buffer, partialTick, 0, 0, 0, 0);
        worn.renderArm(arm, poseStack, buffer, partialTick, light);
        poseStack.popPose();
    }
}
