package com.fantasyweapons.mixin.punchy;

import com.fantasyweapons.client.render.armor.FirstPersonArmor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Punchy dresses its first-person arms in the chestplate's sleeve by painting the armor's vanilla texture on a plain arm.
 * The legendary armor is a GeckoLib model with no such texture, so the arm came out black: for it, the arm of the
 * armor's own model is drawn on Punchy's arm instead (after Punchy's own checks, so its "armor arms" setting still
 * applies). Only applied with Punchy installed (see PunchyMixinPlugin).
 */
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    @Shadow
    private static void applyArmMeshOffsets(PoseStack poseStack, boolean leftArm) {
        throw new AssertionError();
    }

    @Shadow
    private static void applyFreezeShake(PoseStack poseStack, AbstractClientPlayer player, float partialTicks) {
        throw new AssertionError();
    }

    @Inject(method = "renderArmArmor", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static void fantasyweapons$legendaryArmorSleeve(PlayerModel<?> playerModel, AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack,
                                                            MultiBufferSource buffer, int light, boolean slim, float partialTicks, CallbackInfo ci) {
        if (!FirstPersonArmor.wearsLegendaryChest(player)) return;
        ci.cancel();
        boolean left = arm == HumanoidArm.LEFT;
        // the same nudges Punchy gives its own sleeve, so the armor sits on the arm the way it would
        poseStack.pushPose();
        if (slim) poseStack.translate((left ? 1f : -1f) * 0.5f / 16f, 0, 0);
        applyArmMeshOffsets(poseStack, left);
        applyFreezeShake(poseStack, player, partialTicks);
        FirstPersonArmor.renderSleeve(left ? playerModel.leftArm : playerModel.rightArm, player, arm, poseStack, buffer, light, partialTicks);
        poseStack.popPose();
    }
}
