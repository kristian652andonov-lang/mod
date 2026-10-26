package com.fantasyweapons.compat.punchy;

import com.fantasyweapons.client.render.WeaponClientExtensions;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Punchy draws the first-person arms and the held weapon itself, with its own idle and attack animations, so the
 * weapons' ability motion (raised while charging, the release, Monolith's plant, Cinder Cyclone, the form switch) never
 * reached the screen, nor, with Better Combat, any attack (Punchy does not animate Better Combat's attacks unless its own
 * Better Combat setting is on). While one plays it is laid over Punchy's pose instead: the weapon moves about Punchy's hand just as
 * it moves about the hand without Punchy, and the arm (with its sleeve) follows it there, still reaching from the
 * shoulder, so a wide swing never folds the arm into the view.
 */
public final class PunchyAbilityMotion {
    /** The view Punchy starts its arms from this frame (the game's first-person hand space), or null outside of it. */
    private static Matrix4f view;

    private PunchyAbilityMotion() {
    }

    public static void begin(PoseStack poseStack) {
        view = new Matrix4f(poseStack.last().pose());
    }

    public static void end() {
        view = null;
    }

    /**
     * Pushes the ability motion onto {@code poseStack} for Punchy's {@code arm} (posed in {@code model}) if it is the
     * weapon arm and an ability is playing: for the held weapon ({@code item}) or for the arm and its sleeve. Returns
     * whether it pushed, for the caller to pop.
     */
    public static boolean push(PlayerModel<?> model, Player player, HumanoidArm arm, PoseStack poseStack, float partialTick, boolean item) {
        if (view == null || arm != player.getMainArm() || !(player.getMainHandItem().getItem() instanceof FantasyWeaponItem weapon)) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack motion = new PoseStack();
        boolean moving = WeaponClientExtensions.applyAbilityMotion(motion, player, weapon, side, partialTick);
        // a Better Combat attack: Punchy leaves it be (unless it animates Better Combat's attacks itself), so the weapon's
        // own swing is laid over its arm, as it is in the first-person hand without Punchy
        if (com.fantasyweapons.client.anim.AnimTracker.betterCombatSwing(player, partialTick) && !PunchyCompat.animatesBetterCombatAttacks()) {
            moving |= WeaponClientExtensions.applySwing(motion, player, weapon, side, partialTick);
        }
        if (!moving) return false;
        // from Punchy's own space (turned about the view) into the view's, where the motion is made
        Matrix4f toView = new Matrix4f(view).invert().mul(poseStack.last().pose());
        // the shoulder and the fist of the arm as Punchy has posed it
        PoseStack armPose = new PoseStack();
        (arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm).translateAndRotate(armPose);
        Vector3f shoulder = toView.transformPosition(armPose.last().pose().transformPosition(new Vector3f()));
        Vector3f hand = toView.transformPosition(armPose.last().pose().transformPosition(new Vector3f(-side / 16f, 10 / 16f, 0)));
        // the weapon: moved about the fist
        Matrix4f moved = new Matrix4f().translation(hand).mul(motion.last().pose()).translate(-hand.x, -hand.y, -hand.z);
        if (!item) {
            // the arm: its fist carried to where the weapon's grip went, turned to reach there from the shoulder
            Vector3f to = moved.transformPosition(new Vector3f(hand));
            Vector3f before = new Vector3f(hand).sub(shoulder), after = new Vector3f(to).sub(shoulder);
            Quaternionf aim = new Quaternionf().rotationTo(before.normalize(), after.normalize());
            moved = new Matrix4f().translation(to).rotate(aim).translate(-hand.x, -hand.y, -hand.z);
        }
        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f(toView).invert().mul(moved).mul(toView));
        return true;
    }
}
