package com.fantasyweapons.compat.bettercombat;

import com.fantasyweapons.client.render.WeaponRenderer;
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
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The weapons' first-person attacks with Better Combat, played from the very animation its attack plays on the player
 * model in third person. That animation runs on the local player in first person as well, only unseen (the weapons
 * keep to the first-person hand, see FirstPersonModeMixin). Each frame the model's weapon hand is posed twice, by the
 * animation and at rest: the first-person hand travels as the model's does, and the first-person weapon points the
 * way the model's points, so it sweeps, chops and thrusts through the view as it does on the model, the body's twist
 * and lean included, and starts and ends at its first-person rest exactly when the model's swing leaves and returns to
 * rest. What would not read from the eye is kept in view: a thrust points at the crosshair rather than straight away
 * from the eye, what the model swings behind its back is seen in front, and the hand stays on the screen.
 * <p>
 * The model is posed looking straight ahead (Better Combat leans the swing to the player's pitch, which the view
 * already has in first person), from a model of its own so the player's renderer is left alone. Only loaded with
 * Better Combat (and so playerAnimator) installed.
 */
public final class BetterCombatFirstPerson {
    /** How far the first-person hand travels for each block the model's hand does: less, as it is nearer the eye. */
    private static final float TRAVEL = 0.6f;
    /** How much of the hand's travel ahead is kept: ahead is away from the eye, and shrinks the weapon into the distance. */
    private static final float REACH = 0.4f;
    /** How far ahead of the eye (blocks) the crosshair point is that the model's ahead aims at in the view. */
    private static final float AIM = 1.2f;
    /** How far (degrees) the model's hand turns from rest before the first-person blade is wholly on the model's line. */
    private static final float INTO = 35;
    /** How far from the middle of the view (blocks, to either side, below and above) the first-person hand may go. */
    private static final float REACH_X = 0.75f, LOW = -0.62f, HIGH = 0.2f;
    /** DEVELOPMENT ONLY (screenshot director): overrides the travel. */
    public static float debugTravel = -1;

    private static PlayerModel<AbstractClientPlayer> wide, slim;

    private BetterCombatFirstPerson() {
    }

    /**
     * Lays the attack's motion on {@code pose} (the first-person hand, at {@code hand} in the view) if an animation is
     * playing; returns whether one is.
     */
    static boolean apply(PoseStack pose, Vector3f hand, AbstractClientPlayer player, float partialTick) {
        AnimationApplier animation = ((IAnimatedPlayer) player).playerAnimator_getAnimation();
        if (animation == null || !animation.isActive()) return false;
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
        ItemStack stack = player.getMainHandItem();
        boolean left = arm == HumanoidArm.LEFT;
        // how far the swing has left rest: the model's hand's turn from rest, and its travel
        float away = new Matrix4f(swung).mul(new Matrix4f(rest).invert()).getNormalizedRotation(new Quaternionf()).angle();
        Vector3f travel = swung.getTranslation(new Vector3f()).sub(rest.getTranslation(new Vector3f())).mul(debugTravel >= 0 ? debugTravel : TRAVEL);
        // the first-person blade points where the model's does, seen from the eye: up, across, down; turned there the
        // shortest way from its rest, so it keeps its first-person face to the eye (the model's blade, flat to the
        // swing, would be seen edge on). It turns into the model's line as the swing leaves rest and back as it returns
        Vector3f target = swung.getNormalizedRotation(new Quaternionf())
                .transform(display(stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left)
                        .transform(new Vector3f(0, 1, 0)));
        // what points ahead on the model would be seen end on from the eye, a speck: ahead is the line from the hand
        // to the crosshair, so a thrust points the blade there. What the model swings behind its back would point
        // past the eye, out of the view or into the camera: it is seen as far ahead instead, low and to the side for a
        // follow-through, raised for a wind-up overhead
        float ahead = Math.abs(target.z);
        Vector3f aim = new Vector3f(0, 0, -AIM).sub(hand).normalize();
        target.set(target.x, target.y, 0).fma(ahead, aim).normalize();
        Vector3f blade = display(stack, left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, left)
                .transform(new Vector3f(0, 1, 0));
        float into = Math.min(1, away / (float) Math.toRadians(INTO));
        Quaternionf turn = new Quaternionf().slerp(new Quaternionf().rotationTo(blade, target), into * into * (3 - 2 * into));
        // a thrust's reach ahead would shrink the weapon into the distance, and drawing back would bring it into the
        // camera: the hand goes only part of the way ahead, and never back; nor out of the view
        travel.z = Math.min(travel.z * REACH, 0);
        travel.x = Math.clamp(hand.x + travel.x, -REACH_X, REACH_X) - hand.x;
        travel.y = Math.clamp(hand.y + travel.y, LOW, HIGH) - hand.y;
        pose.translate(travel.x, travel.y, travel.z);
        pose.mulPose(turn);
        return true;
    }

    /** How the weapon's model is turned in the hand for {@code ctx}: the item's display transform and the turn about its handle. */
    private static Quaternionf display(ItemStack stack, ItemDisplayContext ctx, boolean left) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack);
        PoseStack display = new PoseStack();
        model.applyTransform(ctx, display, left);
        display.mulPose(Axis.YP.rotationDegrees(WeaponRenderer.handleYaw(stack, ctx)));
        return display.last().pose().getNormalizedRotation(new Quaternionf());
    }

    /**
     * Where the model holds its weapon: the frame its held item is drawn in, as the player renderer, its held item layer
     * and playerAnimator's additions to them place it, in the space the model is turned to the body's facing in (x to
     * its right, y up, z behind: the view's axes when looking straight ahead). Posed by {@code animation}, or at rest.
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
