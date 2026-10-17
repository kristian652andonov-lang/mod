package com.fantasyweapons.client.anim;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Custom {@link HumanoidModel.ArmPose} constants added through NeoForge enum extensions
 * (META-INF/enumextensions.json). Their transformer runs inside HumanoidModel#setupAnim, right before vanilla's
 * attack animation, so it can pose arms/body and replace the swing.
 */
public final class WeaponArmPoses {
    public static final EnumProxy<HumanoidModel.ArmPose> ONE_HANDED = new EnumProxy<>(HumanoidModel.ArmPose.class, false,
            (IArmPoseTransformer) (model, entity, arm) -> WeaponPoses.apply(model, entity, arm, false));

    public static final EnumProxy<HumanoidModel.ArmPose> TWO_HANDED = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) (model, entity, arm) -> WeaponPoses.apply(model, entity, arm, true));

    private WeaponArmPoses() {
    }
}
