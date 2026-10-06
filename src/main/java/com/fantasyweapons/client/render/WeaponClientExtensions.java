package com.fantasyweapons.client.render;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.anim.WeaponArmPoses;
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

/**
 * Player presentation for fantasy weapons.
 * <ul>
 *   <li>Third person: a custom arm pose (stance, swings, heavy attacks, charge, release, transform) per weapon class,
 *       see {@link com.fantasyweapons.client.anim.WeaponPoses}.</li>
 *   <li>First person: replaces vanilla's generic arm swing with the same per-weapon timeline (own swing durations,
 *       backhands, heavy wind-ups, charge raise with tremble, release thrust).</li>
 * </ul>
 */
public class WeaponClientExtensions implements IClientItemExtensions {
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof FantasyWeaponItem item)) return HumanoidModel.ArmPose.ITEM;
        return item.definition().weaponClass().twoHanded() ? WeaponArmPoses.TWO_HANDED.getValue() : WeaponArmPoses.ONE_HANDED.getValue();
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                                           float equipProcess, float swingProcess) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);

        // the extended chainblade is far longer than the sword: keep it from filling the whole view
        WeaponForm heldForm = item.definition().form(FantasyWeaponItem.data(stack));
        if (heldForm != null && "chainblade".equals(heldForm.id())) pose.scale(0.62f, 0.62f, 0.62f);

        float time = (player.tickCount + partialTick) * 0.05f;
        pose.translate(0, Mth.sin(time) * 0.008f, 0);

        // ability charge: weapon rises and trembles with power
        float charge = AnimTracker.charge(player, partialTick);
        if (charge > 0) {
            float w = smooth(Math.min(1, charge * 3));
            float tremble = Mth.sin((player.tickCount + partialTick) * 2.3f) * 0.012f * charge;
            boolean big = item.definition().weaponClass().twoHanded();
            pose.translate(side * ((big ? -0.04f : -0.08f) * w + tremble), 0.12f * w + tremble, -0.04f * w);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (big ? 10 : 14) * w));
            pose.mulPose(Axis.XP.rotationDegrees((big ? 4 : 6) * w));
        }
        // ability release: the weapon's own first attack, so every cast is a clean, designed motion
        float cast = AnimTracker.castProgress(player, partialTick);
        if (cast >= 0) attack(pose, item.definition().weaponClass().swingStyle(), 0, side, 1f, Math.min(1, cast * 1.15f));
        // Monolith plant: from overhead, driven point-first into the ground ahead, then pulled free
        float[] plant = AnimTracker.plantPhase(player, partialTick);
        if (plant != null) {
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
            float k = (float) Math.sin(-spin);
            pose.translate(side * -0.25f * k, 0.1f, -0.15f);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 55 * k));
            pose.mulPose(Axis.YP.rotationDegrees(side * 35 * (float) Math.cos(-spin)));
        }
        // form switch: raise the weapon in front of you
        float form = AnimTracker.formProgress(player, partialTick);
        if (form >= 0) {
            float k = form < 0.3f ? smooth(form / 0.3f) : smooth(1 - (form - 0.3f) / 0.7f);
            pose.translate(side * -0.2f * k, 0.15f * k, -0.1f * k);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 25 * k));
        }

        float s = AnimTracker.swingProgress(player, partialTick);
        if (s < 0) return true;
        float heavy = AnimTracker.heavy(player) ? 1.3f : 1f;
        attack(pose, item.definition().weaponClass().swingStyle(), AnimTracker.variant(player), side, heavy, s);
        return true;
    }

    /**
     * Attack {@code variant} (0-2) of a swing style in first person at progress {@code s}: each class has three, the
     * same as in third person and matching the slash it leaves.
     */
    private static void attack(PoseStack pose, WeaponClass.SwingStyle style, int variant, int side, float heavy, float s) {
        float k = Mth.sin(s * Mth.PI);
        float sweep = smooth(s);
        switch (style) {
            case SLASH, HEAVY_SLASH -> {
                float big = style == WeaponClass.SwingStyle.HEAVY_SLASH ? 1.2f : 1f;
                if (variant == 2) {
                    // longsword: rising slash low right to high left; greatsword: cleave high right to low left
                    rolled(pose, style == WeaponClass.SwingStyle.SLASH ? -38 : 42, () -> levelSweep(pose, side, 1, big, heavy, s));
                } else {
                    levelSweep(pose, side, variant == 1 ? -1 : 1, big, heavy, s);
                }
            }
            case REAP -> {
                if (variant == 2) {
                    overhead(pose, side, heavy, s, 0.5f, 95);
                } else {
                    float dir = variant == 1 ? -1 : 1;
                    float wind = s < 0.3f ? smooth(s / 0.3f) : 1 - smooth((s - 0.3f) / 0.7f);
                    pose.translate(side * dir * (0.45f * wind - 0.95f * sweep * k), -0.1f * k, -0.2f * k);
                    pose.mulPose(Axis.YP.rotationDegrees(side * dir * (95 * wind - 170 * sweep * k) * heavy));
                    pose.mulPose(Axis.XP.rotationDegrees(-15 * k));
                }
            }
            case CHOP -> {
                switch (variant) {
                    case 1 -> levelSweep(pose, side, 1, 1.15f, heavy, s);
                    case 2 -> rolled(pose, -45, () -> overhead(pose, side, heavy, s, 0.5f, 85));
                    default -> overhead(pose, side, heavy, s, 0.5f, 85);
                }
            }
            case SLAM -> {
                switch (variant) {
                    case 1 -> levelSweep(pose, side, 1, 1.25f, heavy, s);
                    case 2 -> {
                        // uppercut: drawn down and back, then smashed up through the centre
                        float wind = s < 0.35f ? smooth(s / 0.35f) : 1 - smooth((s - 0.35f) / 0.65f);
                        float strike = s < 0.35f ? 0 : Mth.sin((s - 0.35f) / 0.65f * Mth.PI);
                        pose.translate(side * (0.3f * wind - 0.1f * strike), -0.35f * wind + 0.45f * strike, -0.3f * strike);
                        pose.mulPose(Axis.ZP.rotationDegrees(side * (20 * wind - 8 * strike)));
                        pose.mulPose(Axis.XP.rotationDegrees((-30 * wind + 60 * strike) * heavy));
                    }
                    default -> overhead(pose, side, heavy, s, 0.6f, 105);
                }
            }
            case THRUST -> {
                if (variant == 1) {
                    levelSweep(pose, side, 1, 1f, heavy, s);
                } else {
                    float back = s < 0.25f ? smooth(s / 0.25f) : 1 - smooth((s - 0.25f) / 0.35f);
                    float thrust = s < 0.25f ? 0 : Mth.sin(Math.min(1, (s - 0.25f) / 0.6f) * Mth.PI);
                    float high = variant == 2 ? 1 : 0;
                    pose.translate(side * -0.08f * thrust, (0.04f + 0.3f * high) * thrust, 0.2f * back - 0.85f * thrust * heavy);
                    pose.mulPose(Axis.XP.rotationDegrees((-10 + 22 * high) * thrust));
                }
            }
        }
    }

    /** An overhead strike: lifted to the upper right, then brought down through the centre by {@code down} degrees. */
    private static void overhead(PoseStack pose, int side, float heavy, float s, float lift, float down) {
        float wind = s < 0.35f ? smooth(s / 0.35f) : 1 - smooth((s - 0.35f) / 0.65f);
        float strike = s < 0.35f ? 0 : Mth.sin((s - 0.35f) / 0.65f * Mth.PI);
        pose.translate(side * (0.3f * wind - 0.15f * strike), lift * wind - 0.3f * strike, -0.3f * strike);
        pose.mulPose(Axis.ZP.rotationDegrees(side * (-25 * wind + 15 * strike)));
        pose.mulPose(Axis.XP.rotationDegrees((12 * wind - down * strike) * heavy));
    }

    /** Runs {@code swing} turned by {@code deg} about the view axis, through a point in front of the eyes: diagonals. */
    private static void rolled(PoseStack pose, float deg, Runnable swing) {
        pose.translate(-0.2f, 0.1f, -0.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        pose.translate(0.2f, -0.1f, 0.5f);
        swing.run();
    }

    /**
     * A level sweep from right to left (left to right when {@code dir} is -1), like the slash it leaves: the blade is
     * laid forward, drawn out to one side, carried across at chest height and eased back. {@code s} is 0..1.
     */
    private static void levelSweep(PoseStack pose, int side, float dir, float big, float heavy, float s) {
        float in = smooth(Math.min(1, s / 0.1f));
        float out = s > 0.6f ? smooth((s - 0.6f) / 0.4f) : 0;
        float w = in * (1 - out);
        float t = smooth(Mth.clamp((s - 0.06f) / 0.36f, 0, 1));
        float arc = Mth.sin(t * Mth.PI);
        pose.translate(side * dir * (0.3f - 0.62f * t) * w, (0.2f + 0.04f * arc) * w, (-0.32f - 0.22f * arc) * w * big);
        pose.mulPose(Axis.YP.rotationDegrees(side * dir * (-55 + 125 * t) * big * heavy * w));
        pose.mulPose(Axis.XP.rotationDegrees(-36 * w));
        pose.mulPose(Axis.ZP.rotationDegrees(side * dir * 10 * w));
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
