package com.aigirlfriend.mixin.client;

import com.aigirlfriend.client.anim.PlayerPoseController;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies interaction animations to the player model after vanilla posing. PlayerModel#setupAnim calls this method
 * first and copies the limbs into sleeves/jacket afterwards, so the outer layers follow automatically.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void aigirlfriend$applyInteractionPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player) {
            PlayerPoseController.applyToModel((HumanoidModel<?>) (Object) this, player);
        }
    }
}
