package com.fantasyweapons.mixin.punchy;

import com.fantasyweapons.compat.punchy.PunchyAbilityMotion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays the weapons' ability motion over Punchy's first-person weapon arm: its held item, and its arm and sleeve following
 * it, are each drawn with it (see PunchyAbilityMotion). Only applied with Punchy installed (see PunchyMixinPlugin).
 */
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyAbilityMotionMixin {
    private static final String RENDER = "renderFirstPersonInternal";

    @Inject(method = RENDER, at = @At("HEAD"))
    private static void fantasyweapons$beginArms(ItemInHandRenderer handRenderer, LocalPlayer player, float partialTicks, PoseStack poseStack,
                                                 MultiBufferSource buffer, int light, CallbackInfo ci) {
        PunchyAbilityMotion.begin(poseStack);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private static void fantasyweapons$endArms(ItemInHandRenderer handRenderer, LocalPlayer player, float partialTicks, PoseStack poseStack,
                                               MultiBufferSource buffer, int light, CallbackInfo ci) {
        PunchyAbilityMotion.end();
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lpunchy/client/render/PunchyArmRenderer;renderArm(Lnet/minecraft/client/model/PlayerModel;"
            + "Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/resources/ResourceLocation;ZF)V"))
    private static void fantasyweapons$moveArm(PlayerModel<?> model, AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
                                               int light, ResourceLocation texture, boolean slim, float partialTicks, Operation<Void> original) {
        boolean pushed = PunchyAbilityMotion.push(model, player, arm, poseStack, partialTicks, false);
        try {
            original.call(model, player, arm, poseStack, buffer, light, texture, slim, partialTicks);
        } finally {
            if (pushed) poseStack.popPose();
        }
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lpunchy/client/render/PunchyArmRenderer;renderArmArmor(Lnet/minecraft/client/model/PlayerModel;"
            + "Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;IZF)V"))
    private static void fantasyweapons$moveSleeve(PlayerModel<?> model, AbstractClientPlayer player, HumanoidArm arm, PoseStack poseStack,
                                                  MultiBufferSource buffer, int light, boolean slim, float partialTicks, Operation<Void> original) {
        boolean pushed = PunchyAbilityMotion.push(model, player, arm, poseStack, partialTicks, false);
        try {
            original.call(model, player, arm, poseStack, buffer, light, slim, partialTicks);
        } finally {
            if (pushed) poseStack.popPose();
        }
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lpunchy/client/render/PunchyArmRenderer;renderItemInHand("
            + "Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/client/model/PlayerModel;Lnet/minecraft/client/player/LocalPlayer;"
            + "Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lorg/joml/Matrix4f;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;F)V"))
    private static void fantasyweapons$moveItem(ItemInHandRenderer handRenderer, PlayerModel<?> model, LocalPlayer player, HumanoidArm arm, PoseStack poseStack,
                                                MultiBufferSource buffer, int light, Matrix4f rootWorld, ModelPart geoArm, ModelPart geoItem, ModelPart geoGrip,
                                                float partialTicks, Operation<Void> original) {
        boolean pushed = PunchyAbilityMotion.push(model, player, arm, poseStack, partialTicks, true);
        try {
            original.call(handRenderer, model, player, arm, poseStack, buffer, light, rootWorld, geoArm, geoItem, geoGrip, partialTicks);
        } finally {
            if (pushed) poseStack.popPose();
        }
    }
}
