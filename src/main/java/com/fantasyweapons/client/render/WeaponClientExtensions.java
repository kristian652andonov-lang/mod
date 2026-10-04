package com.fantasyweapons.client.render;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.anim.WeaponArmPoses;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
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
        // ability release: forward thrust
        float cast = AnimTracker.castProgress(player, partialTick);
        if (cast >= 0) {
            float k = cast < 0.2f ? smooth(cast / 0.2f) : smooth(1 - (cast - 0.2f) / 0.8f);
            pose.translate(side * -0.15f * k, 0.05f * k, -0.4f * k);
            pose.mulPose(Axis.XP.rotationDegrees(-25 * k));
        }
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
        // form switch: raise the weapon in front of you
        float form = AnimTracker.formProgress(player, partialTick);
        if (form >= 0) {
            float k = form < 0.3f ? smooth(form / 0.3f) : smooth(1 - (form - 0.3f) / 0.7f);
            pose.translate(side * -0.2f * k, 0.15f * k, -0.1f * k);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 25 * k));
        }

        float s = AnimTracker.swingProgress(player, partialTick);
        if (s < 0) return true;
        float k = Mth.sin(s * Mth.PI);
        float sweep = smooth(s);
        float heavy = AnimTracker.heavy(player) ? 1.3f : 1f;
        float dir = AnimTracker.mirrored(player) && !item.definition().weaponClass().twoHanded() ? -1 : 1;
        WeaponClass.SwingStyle style = item.definition().weaponClass().swingStyle();
        switch (style) {
            case SLASH -> {
                float wind = s < 0.25f ? smooth(s / 0.25f) : 1 - smooth((s - 0.25f) / 0.75f);
                pose.translate(side * dir * (0.25f * wind - 0.5f * sweep * k), 0.15f * wind - 0.1f * k, -0.25f * k);
                pose.mulPose(Axis.YP.rotationDegrees(side * dir * (45 * wind - 70 * sweep * k) * heavy));
                pose.mulPose(Axis.ZP.rotationDegrees(side * dir * (-35 * wind + 65 * sweep * k)));
                pose.mulPose(Axis.XP.rotationDegrees((20 * wind - 45 * k) * heavy));
            }
            case HEAVY_SLASH -> {
                float wind = s < 0.3f ? smooth(s / 0.3f) : 1 - smooth((s - 0.3f) / 0.7f);
                pose.translate(side * (0.35f * wind - 0.75f * sweep * k), -0.05f * k, -0.3f * k);
                pose.mulPose(Axis.YP.rotationDegrees(side * (70 * wind - 120 * sweep * k) * heavy));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (30 * wind - 20 * k)));
                pose.mulPose(Axis.XP.rotationDegrees(-25 * k));
            }
            case REAP -> {
                float wind = s < 0.3f ? smooth(s / 0.3f) : 1 - smooth((s - 0.3f) / 0.7f);
                pose.translate(side * (0.45f * wind - 0.95f * sweep * k), -0.1f * k, -0.2f * k);
                pose.mulPose(Axis.YP.rotationDegrees(side * (95 * wind - 170 * sweep * k) * heavy));
                pose.mulPose(Axis.XP.rotationDegrees(-15 * k));
            }
            case CHOP -> {
                float wind = s < 0.35f ? smooth(s / 0.35f) : 1 - smooth((s - 0.35f) / 0.65f);
                float strike = s < 0.35f ? 0 : Mth.sin((s - 0.35f) / 0.65f * Mth.PI);
                // wind-up lifts the axe to the upper right (out of the way), then it chops down through the centre
                pose.translate(side * (0.3f * wind - 0.15f * strike), 0.5f * wind - 0.25f * strike, -0.3f * strike);
                pose.mulPose(Axis.ZP.rotationDegrees(side * (-25 * wind + 15 * strike)));
                pose.mulPose(Axis.XP.rotationDegrees((12 * wind - 85 * strike) * heavy));
            }
            case SLAM -> {
                float wind = s < 0.4f ? smooth(s / 0.4f) : 1 - smooth((s - 0.4f) / 0.6f);
                float strike = s < 0.4f ? 0 : Mth.sin((s - 0.4f) / 0.6f * Mth.PI);
                pose.translate(side * (0.35f * wind - 0.15f * strike), 0.6f * wind - 0.4f * strike, -0.35f * strike);
                pose.mulPose(Axis.ZP.rotationDegrees(side * (-30 * wind + 10 * strike)));
                pose.mulPose(Axis.XP.rotationDegrees((12 * wind - 105 * strike) * heavy));
            }
            case THRUST -> {
                float back = s < 0.25f ? smooth(s / 0.25f) : 1 - smooth((s - 0.25f) / 0.35f);
                float thrust = s < 0.25f ? 0 : Mth.sin(Math.min(1, (s - 0.25f) / 0.6f) * Mth.PI);
                pose.translate(side * -0.08f * thrust, 0.04f * thrust, 0.2f * back - 0.85f * thrust * heavy);
                pose.mulPose(Axis.XP.rotationDegrees(-10 * thrust));
            }
        }
        return true;
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
