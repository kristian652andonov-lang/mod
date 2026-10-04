package com.fantasyweapons.client.anim;

import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Third-person player animation for fantasy weapons. Every weapon class has its own holding stance, swing (with a
 * backhand variant for one-handed blades), heavy attack, charging pose, ability-release pose and form-switch pose.
 * Poses are keyframed arm/body rotations blended with smoothstep; arms follow the head's pitch and the body twists
 * into swings.
 *
 * Angles are HumanoidModel radians: arm xRot -π/2 = pointing forward, -π = straight up; right-arm yRot &lt; 0 and
 * left-arm yRot &gt; 0 move the arm across the body.
 */
public final class WeaponPoses {
    /**
     * Right arm (x, y, z), left arm (x, y, z), body yaw, body pitch, and the weapon's grip in the main hand:
     * {@code gx} tilts the weapon around the hand (positive = tip rolls back towards the shoulder), {@code gz} twists it
     * around the forearm (positive = tip swings outwards, away from the body).
     */
    public record Pose(float rx, float ry, float rz, float lx, float ly, float lz, float by, float bx, float gx, float gz) {
        static final Pose ZERO = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        Pose lerp(Pose o, float t) {
            return new Pose(rx + (o.rx - rx) * t, ry + (o.ry - ry) * t, rz + (o.rz - rz) * t, lx + (o.lx - lx) * t, ly + (o.ly - ly) * t,
                    lz + (o.lz - lz) * t, by + (o.by - by) * t, bx + (o.bx - bx) * t, gx + (o.gx - gx) * t, gz + (o.gz - gz) * t);
        }

        /** Exaggerates the difference from {@code base} (heavy attacks). */
        Pose amplify(Pose base, float k) {
            return base.lerp(this, k);
        }

        Pose addPitch(float pitch) {
            return new Pose(rx + pitch, ry, rz, lx + pitch, ly, lz, by, bx, gx, gz);
        }

        Pose withLeft(Pose o) {
            return new Pose(rx, ry, rz, o.lx, o.ly, o.lz, by, bx, gx, gz);
        }

        Pose grip(float tilt, float twist) {
            return new Pose(rx, ry, rz, lx, ly, lz, by, bx, tilt, twist);
        }
    }

    private record Key(float t, Pose pose) {
    }

    private static Pose p(float rx, float ry, float rz, float lx, float ly, float lz, float by, float bx) {
        return new Pose(rx, ry, rz, lx, ly, lz, by, bx, 0, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // stances
    // ------------------------------------------------------------------------------------------------------------

    static Pose stance(WeaponClass cls, boolean chainForm) {
        return switch (cls) {
            // one-handed ready guard, blade angled up and forward
            case LONGSWORD -> p(-0.65f, -0.2f, 0.05f, 0.05f, 0, -0.06f, 0, 0).grip(0.95f, 0);
            // sword form like a longsword; chain form lets the blade hang loose at the side
            case CHAINBLADE -> chainForm ? p(-0.25f, -0.05f, 0.12f, 0.05f, 0, -0.06f, 0, 0).grip(-0.35f, 0.1f)
                    : p(-0.65f, -0.2f, 0.05f, 0.05f, 0, -0.06f, 0, 0).grip(0.95f, 0);
            // two-handed weapons rest on the right shoulder
            case GREATSWORD -> p(-0.75f, -0.5f, 0f, -0.95f, 0.65f, 0f, -0.15f, 0).grip(2.3f, -0.15f);
            case WARHAMMER -> p(-0.7f, -0.4f, 0f, -0.9f, 0.55f, 0f, -0.1f, 0).grip(2.4f, -0.1f);
            case COLOSSAL -> p(-0.8f, -0.45f, 0f, -1.0f, 0.55f, 0f, -0.2f, 0).grip(2.3f, -0.2f);
            // battle axe held diagonally across the body
            case BATTLEAXE -> p(-0.7f, -0.45f, 0f, -0.8f, 0.55f, 0f, -0.1f, 0).grip(1.35f, 0.35f);
            // scythe stood upright, blade high
            case SCYTHE -> p(-0.4f, -0.15f, 0.15f, -1.0f, 0.75f, 0f, -0.2f, 0).grip(1.35f, 0);
            // lance couched forward
            case LANCE -> p(-0.4f, 0.05f, 0f, -0.85f, 0.45f, 0f, -0.15f, 0).grip(0.55f, 0);
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // swings: keyframes from stance → wind-up → strike → follow-through → stance
    // ------------------------------------------------------------------------------------------------------------

    private static Key[] swingKeys(WeaponClass cls, Pose stance, boolean backhand) {
        return switch (cls.swingStyle()) {
            case SLASH -> backhand
                    ? new Key[]{new Key(0, stance), new Key(0.22f, p(-0.45f, 0.85f, -0.25f, 0.1f, 0, -0.1f, 0.45f, 0).grip(0.7f, -0.5f)),
                    new Key(0.55f, p(-2.2f, -0.8f, 0.35f, 0.2f, 0, -0.25f, -0.45f, 0.05f).grip(0.9f, 0.2f)), new Key(1, stance)}
                    : new Key[]{new Key(0, stance), new Key(0.22f, p(-2.5f, -0.75f, 0.35f, 0.15f, 0, -0.2f, -0.4f, -0.05f).grip(1.5f, 0)),
                    new Key(0.55f, p(-0.55f, 0.9f, -0.2f, 0.1f, 0, -0.1f, 0.5f, 0.12f).grip(0.35f, 0.5f)), new Key(1, stance)};
            case HEAVY_SLASH -> new Key[]{new Key(0, stance),
                    new Key(0.28f, p(-1.4f, -1.15f, 0.3f, -1.3f, -0.35f, 0, -0.95f, 0).grip(1.8f, 0)),
                    new Key(0.62f, p(-1.25f, 0.95f, 0, -1.35f, 1.3f, 0, 0.9f, 0.1f).grip(0.6f, 0.3f)), new Key(1, stance)};
            case CHOP -> new Key[]{new Key(0, stance),
                    new Key(0.32f, p(-3.0f, -0.2f, 0, -2.9f, 0.3f, 0, 0, -0.12f).grip(2.0f, 0)),
                    new Key(0.58f, p(-0.45f, -0.15f, 0, -0.6f, 0.35f, 0, 0.05f, 0.35f).grip(0.45f, 0)),
                    new Key(0.75f, p(-0.5f, -0.15f, 0, -0.65f, 0.35f, 0, 0.05f, 0.3f).grip(0.5f, 0)), new Key(1, stance)};
            case REAP -> new Key[]{new Key(0, stance),
                    new Key(0.27f, p(-1.0f, -1.35f, 0.4f, -1.4f, -0.3f, 0, -1.05f, 0).grip(0.75f, 0.3f)),
                    new Key(0.66f, p(-0.9f, 1.05f, -0.2f, -1.0f, 1.4f, 0, 1.0f, 0.15f).grip(0.6f, -0.2f)), new Key(1, stance)};
            case SLAM -> new Key[]{new Key(0, stance),
                    new Key(0.38f, p(-3.1f, -0.1f, 0, -3.0f, 0.2f, 0, 0, -0.15f).grip(2.2f, 0)),
                    new Key(0.56f, p(-0.3f, -0.1f, 0, -0.4f, 0.3f, 0, 0, 0.45f).grip(0.05f, 0)),
                    new Key(0.8f, p(-0.35f, -0.1f, 0, -0.45f, 0.3f, 0, 0, 0.4f).grip(0.05f, 0)), new Key(1, stance)};
            case THRUST -> new Key[]{new Key(0, stance),
                    new Key(0.25f, p(-0.15f, 0.25f, 0, -0.6f, 0.3f, 0, -0.3f, 0).grip(0.8f, 0)),
                    new Key(0.5f, p(-1.4f, -0.05f, 0, -1.45f, 0.25f, 0, 0.2f, 0.15f).grip(-0.5f, 0)), new Key(1, stance)};
        };
    }

    private static Pose sample(Key[] keys, float t) {
        if (t <= keys[0].t()) return keys[0].pose();
        for (int i = 0; i < keys.length - 1; i++) {
            Key a = keys[i], b = keys[i + 1];
            if (t <= b.t()) {
                float k = (t - a.t()) / Math.max(1e-4f, b.t() - a.t());
                k = k * k * (3 - 2 * k);
                return a.pose().lerp(b.pose(), k);
            }
        }
        return keys[keys.length - 1].pose();
    }

    // ------------------------------------------------------------------------------------------------------------
    // charge / cast / form
    // ------------------------------------------------------------------------------------------------------------

    static Pose charge(WeaponClass cls, Pose stance) {
        return switch (cls) {
            // blade raised to the sky
            case LONGSWORD, CHAINBLADE -> p(-2.6f, -0.4f, 0.3f, -0.4f, 0.3f, -0.2f, -0.25f, -0.05f).grip(-0.15f, 0);
            case GREATSWORD, BATTLEAXE, WARHAMMER, COLOSSAL -> p(-2.9f, -0.15f, 0f, -2.85f, 0.25f, 0f, 0f, -0.1f).grip(-0.45f, 0);
            // scythe wound back for a sweep
            case SCYTHE -> p(-0.95f, -1.2f, 0.4f, -1.3f, -0.2f, 0f, -0.8f, 0f).grip(0.9f, 0.4f);
            // lance levelled at the target
            case LANCE -> p(-1.5f, 0.05f, 0f, -1.5f, 0.35f, 0f, -0.3f, 0f).grip(-0.6f, 0);
        };
    }

    static Pose cast(WeaponClass cls) {
        return switch (cls) {
            case SCYTHE -> p(-0.9f, 1.0f, -0.2f, -1.0f, 1.35f, 0, 0.9f, 0.1f).grip(0.6f, 0.3f);
            case WARHAMMER, COLOSSAL -> p(-0.35f, -0.1f, 0, -0.45f, 0.3f, 0, 0, 0.4f).grip(-0.2f, 0);
            // thrust the weapon at the target
            default -> p(-1.6f, 0.1f, 0f, -1.55f, 0.45f, 0f, 0.3f, 0.12f).grip(-0.6f, 0);
        };
    }

    static Pose form() {
        return p(-1.95f, -0.3f, 0f, -1.85f, 0.5f, 0f, 0f, -0.05f).grip(0.5f, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // evaluation & application
    // ------------------------------------------------------------------------------------------------------------

    /** Full pose for an entity holding a fantasy weapon this frame. */
    public static Pose evaluate(LivingEntity e, ItemStack stack, float headPitch) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return Pose.ZERO;
        WeaponDefinition def = item.definition();
        WeaponForm form = def.form(FantasyWeaponItem.data(stack));
        boolean chain = form != null && "chainblade".equals(form.id());
        WeaponClass cls = def.weaponClass();
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float time = (e.tickCount + partial);

        Pose stance = stance(cls, chain);
        // idle breathing
        float breathe = Mth.sin(time * 0.06f) * 0.03f;
        Pose pose = new Pose(stance.rx() + breathe, stance.ry(), stance.rz(), stance.lx() + breathe, stance.ly(), stance.lz(), stance.by(), stance.bx(),
                stance.gx(), stance.gz()).addPitch(headPitch * 0.5f);

        float charge = AnimTracker.charge(e, partial);
        if (charge > 0) {
            float w = smooth(Math.min(1, charge * 3f));
            float tremble = Mth.sin(time * 2.3f) * 0.025f * charge;
            Pose c = charge(cls, stance).addPitch(headPitch * 0.4f);
            c = new Pose(c.rx() + tremble, c.ry() + tremble, c.rz(), c.lx() - tremble, c.ly(), c.lz(), c.by(), c.bx(), c.gx(), c.gz());
            pose = pose.lerp(c, w);
        }

        float form01 = AnimTracker.formProgress(e, partial);
        if (form01 >= 0) pose = pose.lerp(form().addPitch(headPitch * 0.3f), bumpSmooth(form01, 0.3f));

        float cast = AnimTracker.castProgress(e, partial);
        if (cast >= 0) pose = pose.lerp(cast(cls).addPitch(headPitch * 0.6f), bumpSmooth(cast, 0.18f));

        float swing = AnimTracker.swingProgress(e, partial);
        if (swing >= 0) {
            boolean backhand = !cls.twoHanded() && AnimTracker.mirrored(e);
            Pose sw = sample(swingKeys(cls, stance, backhand), swing);
            if (AnimTracker.heavy(e)) sw = sw.amplify(stance, 1.2f);
            if (!cls.twoHanded()) sw = sw.withLeft(pose); // free hand keeps its own pose
            pose = sw.addPitch(headPitch * 0.35f);
        }
        if (debugGrip != null) pose = pose.grip(debugGrip[0], debugGrip[1]);
        return pose;
    }

    /** DEVELOPMENT ONLY (screenshot director): forces the grip angles. */
    public static float[] debugGrip;

    // pose most recently applied to a model; the held-item renderer reads its grip for the same entity
    private static LivingEntity lastEntity;
    private static Pose lastPose = Pose.ZERO;
    private static LivingEntity rendering;

    /** Tracks which living entity is being rendered (set around LivingEntityRenderer#render). */
    public static void setRendering(LivingEntity entity) {
        rendering = entity;
        if (entity == null) lastEntity = null;
    }

    /** Grip of the main-hand weapon of the entity currently being rendered, or null. */
    public static Pose renderingPose(ItemStack stack) {
        if (rendering == null || rendering != lastEntity || rendering.getMainHandItem() != stack) return null;
        return lastPose;
    }

    /** Applies a pose to the model and mirrors vanilla's arm-pivot handling for body twist. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm, boolean twoHanded) {
        ItemStack stack = entity.getMainHandItem();
        Pose pose = evaluate(entity, stack, model.head.xRot);
        lastEntity = entity;
        lastPose = pose;
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        float side = rightHanded ? 1 : -1;
        // vanilla would add its own swing afterwards; ours replaces it
        model.attackTime = 0;

        model.body.yRot = pose.by() * side;
        model.body.xRot = pose.bx();
        float bodyYaw = model.body.yRot;
        var main = rightHanded ? model.rightArm : model.leftArm;
        var off = rightHanded ? model.leftArm : model.rightArm;
        main.xRot = pose.rx();
        main.yRot = pose.ry() * side + bodyYaw;
        main.zRot = pose.rz() * side;
        if (twoHanded) {
            off.xRot = pose.lx();
            off.yRot = pose.ly() * side + bodyYaw;
            off.zRot = pose.lz() * side;
        }
        // arm pivots orbit the body when it twists (same as vanilla's attack animation)
        model.rightArm.z = Mth.sin(bodyYaw) * 5.0F;
        model.rightArm.x = -Mth.cos(bodyYaw) * 5.0F;
        model.leftArm.z = -Mth.sin(bodyYaw) * 5.0F;
        model.leftArm.x = Mth.cos(bodyYaw) * 5.0F;
        if (!twoHanded) off.yRot += bodyYaw;
    }

    static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** 0 → 1 (by peak) → hold → 0 over the last 35%. */
    static float bumpSmooth(float t, float peak) {
        if (t < peak) return smooth(t / peak);
        if (t > 0.65f) return smooth(1 - (t - 0.65f) / 0.35f);
        return 1;
    }
}
