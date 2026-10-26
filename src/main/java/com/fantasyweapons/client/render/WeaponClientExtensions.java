package com.fantasyweapons.client.render;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.anim.WeaponArmPoses;
import com.fantasyweapons.compat.bettercombat.BetterCombatClient;
import com.fantasyweapons.compat.bettercombat.BetterCombatCompat;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Vector3f;

/**
 * Player presentation for fantasy weapons.
 * <ul>
 *   <li>Third person: a custom arm pose (stance, swings, heavy attacks, charge, release, transform) per weapon class,
 *       see {@link com.fantasyweapons.client.anim.WeaponPoses}.</li>
 *   <li>First person: replaces vanilla's generic arm swing with the same per-weapon timeline (own swing durations,
 *       backhands, heavy wind-ups, charge raise, release thrust).</li>
 * </ul>
 */
public class WeaponClientExtensions implements IClientItemExtensions {
    /** Where the weapon hand rests in the first-person view (x for the right hand). */
    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof FantasyWeaponItem item)) return HumanoidModel.ArmPose.ITEM;
        return com.fantasyweapons.client.anim.WeaponPoses.bothArms(item.definition().weaponClass()) ? WeaponArmPoses.TWO_HANDED.getValue()
                : WeaponArmPoses.ONE_HANDED.getValue();
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                                           float equipProcess, float swingProcess) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // the game lowers a held item while an attack recovers and raises it again: these weapons come back to rest by
        // their own swing instead (the dip sank them out of sight through the blow and had them climb back after it);
        // switching items still lowers and raises them
        if (AnimTracker.recoveringFromSwing(player)) equipProcess = 0;
        pose.translate(side * HAND_X, HAND_Y + equipProcess * -0.6F, HAND_Z);

        // the extended chainblade is far longer than the sword: keep it from filling the whole view
        WeaponForm heldForm = item.definition().form(FantasyWeaponItem.data(stack));
        if (heldForm != null && "chainblade".equals(heldForm.id())) pose.scale(0.62f, 0.62f, 0.62f);

        float time = (player.tickCount + partialTick) * 0.05f;
        pose.translate(0, Mth.sin(time) * 0.008f, 0);

        applyAbilityMotion(pose, player, item, side, partialTick);

        FirstPersonAttacks.debug(pose, side);
        applySwing(pose, player, item, side, partialTick);
        return true;
    }

    /** The first-person attack swing about the hand, if one is playing; returns whether it is. */
    public static boolean applySwing(PoseStack pose, LivingEntity player, FantasyWeaponItem item, int side, float partialTick) {
        // Better Combat's attacks play its own animation, as the player model does in third person
        if (AnimTracker.betterCombatSwing(player)) return applyBetterCombatSwing(pose, new Vector3f(side * HAND_X, HAND_Y, HAND_Z), player, partialTick);
        float s = AnimTracker.swingProgress(player, partialTick);
        if (s < 0) return false;
        float heavy = AnimTracker.heavy(player) ? 1.3f : 1f;
        FirstPersonAttacks.apply(pose, item.definition().weaponClass().swingStyle(), AnimTracker.variant(player), side, heavy, s);
        return true;
    }

    /**
     * The first-person motion of a Better Combat attack about the hand ({@code hand}: where it is in the view), if one is
     * playing; returns whether it is.
     */
    public static boolean applyBetterCombatSwing(PoseStack pose, Vector3f hand, LivingEntity player, float partialTick) {
        return BetterCombatCompat.loaded() && BetterCombatClient.firstPersonAttack(pose, hand, player, partialTick);
    }

    /**
     * The first-person motion of the weapon's abilities, about the hand: raised while charging, the release, Monolith's
     * plant, Cinder Cyclone's whirl and the form switch. Also laid over the arms of mods that draw the first-person
     * hands themselves (see {@code compat/punchy}). Returns whether any of it is playing.
     */
    public static boolean applyAbilityMotion(PoseStack pose, LivingEntity player, FantasyWeaponItem item, int side, float partialTick) {
        boolean moving = false;
        // ability charge: the weapon is raised and held steady (power shows in its glow, not in a shaking hand)
        float charge = AnimTracker.charge(player, partialTick);
        if (charge > 0) {
            moving = true;
            float w = smooth(Math.min(1, charge * 3));
            boolean big = item.definition().weaponClass().twoHanded();
            pose.translate(side * (big ? -0.04f : -0.08f) * w, 0.12f * w, -0.04f * w);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (big ? 10 : 14) * w));
            pose.mulPose(Axis.XP.rotationDegrees((big ? 4 : 6) * w));
        }
        // ability release: the weapon's own first attack, so every cast is a clean, designed motion
        float cast = AnimTracker.castProgress(player, partialTick);
        if (cast >= 0) {
            moving = true;
            FirstPersonAttacks.apply(pose, item.definition().weaponClass().swingStyle(), 0, side, 1f, Math.min(1, cast * 1.15f));
        }
        // Monolith plant: from overhead, driven point-first into the ground ahead, then pulled free
        float[] plant = AnimTracker.plantPhase(player, partialTick);
        if (plant != null) {
            moving = true;
            float w = plant[0], d = plant[1], raise = 1 - plant[1];
            float sh = plant[2] * 0.03f;
            float[] k = debugPlantFp != null ? debugPlantFp : PLANT_FP;
            pose.translate(side * k[0] * w, (0.3f * raise + k[1] * d + sh) * w, (-0.05f * raise + k[2] * d) * w);
            // roll the diagonal blade upright first (inner), then pitch it forward and down (outer)
            pose.mulPose(Axis.XP.rotationDegrees((20 * raise + k[3] * d) * w));
            pose.mulPose(Axis.ZP.rotationDegrees(side * (8 * raise + k[4] * d) * w));
        }
        // Cinder Cyclone: the chain whirls around in front of the view
        float spin = AnimTracker.spinAngle(player, partialTick);
        if (spin != 0) {
            moving = true;
            float k = (float) Math.sin(-spin);
            pose.translate(side * -0.25f * k, 0.1f, -0.15f);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 55 * k));
            pose.mulPose(Axis.YP.rotationDegrees(side * 35 * (float) Math.cos(-spin)));
        }
        // form switch: raise the weapon in front of you
        float form = AnimTracker.formProgress(player, partialTick);
        if (form >= 0) {
            moving = true;
            float k = form < 0.3f ? smooth(form / 0.3f) : smooth(1 - (form - 0.3f) / 0.7f);
            pose.translate(side * -0.2f * k, 0.15f * k, -0.1f * k);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 25 * k));
        }
        return moving;
    }

    /** First-person planted offset: x (towards centre), y, z, pitch, roll. */
    private static final float[] PLANT_FP = {-0.5f, 0.3f, -0.75f, -145f, -36f};
    /** DEVELOPMENT ONLY (screenshot director): overrides {@link #PLANT_FP}. */
    public static float[] debugPlantFp;

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
