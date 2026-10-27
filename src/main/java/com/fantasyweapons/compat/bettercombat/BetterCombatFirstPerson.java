package com.fantasyweapons.compat.bettercombat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.Helper;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.IPlayerModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The weapons' first-person attacks with Better Combat: exactly the attack the player model plays in third person, seen
 * from its eyes. That animation runs on the local player in first person as well, only unseen (the weapons keep to the
 * first-person hand, see FirstPersonModeMixin). Each frame the model is posed by it and the frame its hand holds the
 * weapon in is taken, from the eye; the first-person weapon is drawn there, with its third-person look (see
 * WeaponRenderer), so it sweeps, chops and thrusts through the view just as it does on the model.
 * <p>
 * It goes over to the model's hold as the model's swing leaves rest and back to its own first-person rest as the swing
 * returns, so nothing jumps where the two meet. The model is posed looking straight ahead and the swing laid on the
 * view, so it follows where the player looks (as Better Combat's own first-person attacks do), from a model of its own,
 * so the player's renderer is left alone. Only loaded with Better Combat (and so playerAnimator) installed.
 */
public final class BetterCombatFirstPerson {
    /** How far (degrees) the model's hand turns from rest before the first-person weapon is wholly in the model's hold. */
    private static final float INTO = 30;
    /** How far behind the eye (blocks) the attack is seen from (the screenshot director may change it). */
    public static float eyeBack = 0.6f;

    private static PlayerModel<AbstractClientPlayer> wide, slim;
    /** The last attack worked out, and for which moment (the weapon and the arm both ask for it every frame). */
    private static FirstPersonAttack last;
    private static int lastTick = Integer.MIN_VALUE;
    private static float lastPartial = Float.NaN;

    private BetterCombatFirstPerson() {
    }

    /** The attack as the first person sees it, or null when none is playing. */
    static FirstPersonAttack attack(AbstractClientPlayer player, float partialTick) {
        if (player.tickCount == lastTick && partialTick == lastPartial) return last;
        lastTick = player.tickCount;
        lastPartial = partialTick;
        return last = work(player, partialTick);
    }

    private static FirstPersonAttack work(AbstractClientPlayer player, float partialTick) {
        AnimationApplier animation = ((IAnimatedPlayer) player).playerAnimator_getAnimation();
        if (animation == null || !animation.isActive()) return null;
        HumanoidArm arm = player.getMainArm();
        Matrix4f swung, rest;
        float pitch = player.getXRot();
        player.setXRot(0);
        try {
            animation.setTickDelta(partialTick);
            swung = hand(player, arm, animation);
            rest = hand(player, arm, null);
        } finally {
            player.setXRot(pitch);
        }
        // how far the swing has left rest: the turn of the model's hand from rest
        float away = new Matrix4f(swung).mul(new Matrix4f(rest).invert()).getNormalizedRotation(new Quaternionf()).angle();
        float into = Math.min(1, away / (float) Math.toRadians(INTO));
        float weight = into * into * (3 - 2 * into);
        if (weight <= 0) return null;
        // from the eye: the model stands with its feet the eye's height below the camera, facing where it looks; seen
        // from a little behind the eye, as the first-person view is narrower than the world's, so what the model swings
        // out to its sides stays in view
        Matrix4f frame = new Matrix4f().translation(0, -player.getEyeHeight(Pose.STANDING), -eyeBack).mul(swung);
        return new FirstPersonAttack(frame, weight);
    }

    /**
     * Where the model holds its weapon: the frame its held item is drawn in, as the player renderer, its held item layer
     * and playerAnimator's additions to them place it, in the space the model is turned to the body's facing in (x to
     * its right, y up, z behind: the view's axes when looking straight ahead), from its feet. Posed by
     * {@code animation}, or at rest.
     */
    private static Matrix4f hand(AbstractClientPlayer player, HumanoidArm arm, AnimationApplier animation) {
        PlayerModel<AbstractClientPlayer> model = model(player);
        model.rightArmPose = arm == HumanoidArm.RIGHT ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = arm == HumanoidArm.LEFT ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
        model.attackTime = 0;
        model.crouching = false;
        model.riding = false;
        model.swimAmount = 0;
        // playerAnimator poses the model's parts in setupAnim unless told the next pose is a first-person one
        if (animation == null) ((IPlayerModel) model).playerAnimator_prepForFirstPersonRender();
        model.setupAnim(player, 0, 0, 0, 0, 0);

        PoseStack pose = new PoseStack();
        if (animation != null) {
            // the whole body (PlayerRenderer#setupRotations)
            Vec3f scale = animation.get3DTransform("body", TransformType.SCALE, new Vec3f(1, 1, 1));
            pose.scale(scale.getX(), scale.getY(), scale.getZ());
            Vec3f pos = animation.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
            pose.translate(pos.getX(), pos.getY() + 0.7f, pos.getZ());
            Vec3f rot = animation.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
            pose.mulPose(Axis.ZP.rotation(rot.getZ()));
            pose.mulPose(Axis.YP.rotation(rot.getY()));
            pose.mulPose(Axis.XP.rotation(rot.getX()));
            pose.translate(0, -0.7f, 0);
        }
        // LivingEntityRenderer#render and PlayerRenderer#scale
        pose.scale(-1, -1, 1);
        pose.scale(0.9375f, 0.9375f, 0.9375f);
        pose.translate(0, -1.501f, 0);
        // ItemInHandLayer#renderArmWithItem
        model.translateToHand(arm, pose);
        String side = arm == HumanoidArm.LEFT ? "left" : "right";
        if (animation != null && Helper.isBendEnabled()) {
            Vec3f bend = animation.get3DTransform(side + "Arm", TransformType.BEND, new Vec3f(0, 0, 0));
            float axis = -bend.getX();
            pose.translate(0, 0.25f, 0);
            pose.mulPose(new Quaternionf().rotateAxis(bend.getY(), new Vector3f((float) Math.cos(axis), 0, (float) Math.sin(axis))));
            pose.translate(0, -0.25f, 0);
        }
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16f, 0.125f, -0.625f);
        if (animation != null) {
            Vec3f scale = animation.get3DTransform(side + "Item", TransformType.SCALE, new Vec3f(1, 1, 1));
            Vec3f rot = animation.get3DTransform(side + "Item", TransformType.ROTATION, Vec3f.ZERO);
            Vec3f pos = animation.get3DTransform(side + "Item", TransformType.POSITION, Vec3f.ZERO).scale(1 / 16f);
            pose.scale(scale.getX(), scale.getY(), scale.getZ());
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            pose.mulPose(Axis.ZP.rotation(rot.getZ()));
            pose.mulPose(Axis.YP.rotation(rot.getY()));
            pose.mulPose(Axis.XP.rotation(rot.getX()));
        }
        return new Matrix4f(pose.last().pose());
    }

    private static PlayerModel<AbstractClientPlayer> model(AbstractClientPlayer player) {
        var models = Minecraft.getInstance().getEntityModels();
        if (player.getSkin().model() == PlayerSkin.Model.SLIM) {
            if (slim == null) slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }
}
