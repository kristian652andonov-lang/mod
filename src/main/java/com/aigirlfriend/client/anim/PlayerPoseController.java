package com.aigirlfriend.client.anim;

import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Drives the vanilla player model during shared interactions, using animations from {@link PlayerAnimationLibrary}.
 * Every client derives the pose from the companion's synced interaction state, so all players see the same thing.
 * Animations blend in and out over a few ticks so nothing snaps.
 * <p>
 * Conversion from Bedrock (Blockbench) space to the Java player model: xRot = +x, yRot = -y, zRot = -z (degrees),
 * position offset x = +x, y = -y, z = -z (pixels). The optional "root" bone moves/rotates the whole player
 * (used for lying on a bed), around the animation's "root_pivot".
 */
public final class PlayerPoseController {
    private static final float BLEND_TICKS = 5f;

    private static final class State {
        @Nullable String animation;
        float startTime;
        float weight;
        @Nullable GirlfriendEntity partner;
        @Nullable InteractionType type;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, InteractionType> ACTIVE = new HashMap<>();
    private static final Map<Integer, GirlfriendEntity> PARTNERS = new HashMap<>();

    private PlayerPoseController() {}

    /** Rebuilds the player -> interaction map from synced companion state. Called once per client tick. */
    public static void tick() {
        ACTIVE.clear();
        PARTNERS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GirlfriendEntity gf) {
                InteractionType t = gf.getSyncedInteraction();
                int pid = gf.getSyncedPartnerId();
                if (t != null && pid >= 0) {
                    ACTIVE.put(pid, t);
                    PARTNERS.put(pid, gf);
                }
            }
        }
        float now = mc.level.getGameTime();
        STATES.entrySet().removeIf(en -> {
            State s = en.getValue();
            boolean active = ACTIVE.containsKey(en.getKey());
            return !active && s.weight <= 0f && now - s.startTime > 40;
        });
        for (Map.Entry<Integer, InteractionType> en : ACTIVE.entrySet()) {
            InteractionType t = en.getValue();
            String anim = sided(t.playerAnimation, mc.level.getEntity(en.getKey()), PARTNERS.get(en.getKey()));
            State s = STATES.computeIfAbsent(en.getKey(), k -> new State());
            if (anim != null && !anim.equals(s.animation)) {
                s.animation = anim;
                s.startTime = now;
            }
            s.type = t;
            s.partner = PARTNERS.get(en.getKey());
        }
        for (Map.Entry<Integer, State> en : STATES.entrySet()) {
            State s = en.getValue();
            boolean active = ACTIVE.containsKey(en.getKey()) && s.type != null && s.type.playerAnimation != null;
            s.weight = Mth.clamp(s.weight + (active ? 1f : -1f) / BLEND_TICKS, 0f, 1f);
            if (!active && s.weight <= 0f) {
                s.type = null;
                s.partner = null;
            }
        }
    }

    /** Picks the "_left"/"_right" variant of a player clip by which side of the player she is on, if it has one. */
    @Nullable
    private static String sided(@Nullable String anim, @Nullable Entity player, @Nullable GirlfriendEntity gf) {
        if (anim == null || player == null || gf == null || PlayerAnimationLibrary.get(anim + "_right") == null) return anim;
        float yaw = (player instanceof Player p ? p.yBodyRot : player.getYRot()) * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        boolean right = (gf.getX() - player.getX()) * rx + (gf.getZ() - player.getZ()) * rz >= 0;
        String variant = anim + (right ? "_right" : "_left");
        return PlayerAnimationLibrary.get(variant) != null ? variant : anim;
    }

    @Nullable
    public static InteractionType interactionOf(Player player) {
        return ACTIVE.get(player.getId());
    }

    @Nullable
    public static GirlfriendEntity partnerOf(Player player) {
        return PARTNERS.get(player.getId());
    }

    private static float elapsedSeconds(State s, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float now = mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
        float speed;
        try {
            speed = GFClientConfig.ANIMATION_SPEED.get().floatValue();
        } catch (Exception e) {
            speed = 1f;
        }
        return (now - s.startTime) / 20f * speed;
    }

    private static boolean enabled() {
        try {
            return GFClientConfig.PLAYER_ANIMATIONS.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** Applied at the end of HumanoidModel#setupAnim (before the player model copies limbs into its outer layers). */
    public static void applyToModel(HumanoidModel<?> model, Player player) {
        // vanilla setupAnim never resets these, and the model is shared by every player: always start from neutral
        // so nothing accumulates frame to frame or leaks onto other players (a head roll used to keep adding up)
        model.head.zRot = 0f;
        model.body.zRot = 0f;
        if (!enabled()) return;
        State s = STATES.get(player.getId());
        if (s == null || s.weight <= 0f || s.animation == null) return;
        BedrockAnimation anim = PlayerAnimationLibrary.get(s.animation);
        if (anim == null) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float w = smooth(Mth.clamp(s.weight + (ACTIVE.containsKey(player.getId()) ? partial : -partial) / BLEND_TICKS, 0f, 1f));
        float t = anim.timeAt(elapsedSeconds(s, partial));
        apply(anim, "head", model.head, t, w, true);
        apply(anim, "body", model.body, t, w, false);
        apply(anim, "rightarm", model.rightArm, t, w, false);
        apply(anim, "leftarm", model.leftArm, t, w, false);
        apply(anim, "rightleg", model.rightLeg, t, w, false);
        apply(anim, "leftleg", model.leftLeg, t, w, false);
        model.hat.copyFrom(model.head);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static void apply(BedrockAnimation anim, String bone, ModelPart part, float t, float w, boolean additive) {
        BedrockAnimation.Bone b = anim.bones.get(bone);
        if (b == null) return;
        if (b.rotation != null) {
            Vector3f r = b.rotation.sample(t);
            float x = r.x * Mth.DEG_TO_RAD, y = -r.y * Mth.DEG_TO_RAD, z = -r.z * Mth.DEG_TO_RAD;
            if (additive) {
                part.xRot += x * w;
                part.yRot += y * w;
                part.zRot += z * w;
            } else {
                part.xRot = Mth.lerp(w, part.xRot, x);
                part.yRot = Mth.lerp(w, part.yRot, y);
                part.zRot = Mth.lerp(w, part.zRot, z);
            }
        }
        if (b.position != null) {
            // x/z from the rest pose (vanilla does not reset them for every part); y on top of vanilla's reset y
            Vector3f p = b.position.sample(t);
            part.x = part.getInitialPose().x + p.x * w;
            part.y -= p.y * w;
            part.z = part.getInitialPose().z - p.z * w;
        }
    }

    /**
     * Whole-body ("root" bone) transform, applied to the pose stack before the player renderer runs.
     * Returns true if a transform was pushed (the caller must pop it after rendering).
     */
    public static boolean pushRootTransform(Player player, PoseStack poseStack, float partialTick) {
        if (!enabled()) return false;
        State s = STATES.get(player.getId());
        if (s == null || s.weight <= 0f || s.animation == null) return false;
        BedrockAnimation anim = PlayerAnimationLibrary.get(s.animation);
        if (anim == null) return false;
        BedrockAnimation.Bone root = anim.bones.get("root");
        if (root == null) return false;
        float w = smooth(s.weight);
        float t = anim.timeAt(elapsedSeconds(s, partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        if (root.position != null) {
            Vector3f p = root.position.sample(t);
            poseStack.translate(p.x * w / 16f, p.y * w / 16f, p.z * w / 16f);
        }
        if (root.rotation != null) {
            Vector3f r = root.rotation.sample(t);
            Vector3f pivot = anim.rootPivot;
            poseStack.translate(pivot.x / 16f, pivot.y / 16f, pivot.z / 16f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(r.z * w));
            poseStack.mulPose(Axis.YP.rotationDegrees(r.y * w));
            poseStack.mulPose(Axis.XP.rotationDegrees(r.x * w));
            poseStack.translate(-pivot.x / 16f, -pivot.y / 16f, -pivot.z / 16f);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        return true;
    }

    /** Keeps the player's body facing the bed/partner during lying interactions so the root pose lines up. */
    public static void alignBody(Player player) {
        InteractionType t = ACTIVE.get(player.getId());
        GirlfriendEntity gf = PARTNERS.get(player.getId());
        if (t == null || gf == null) return;
        if (t == InteractionType.CUDDLE_BED && player.getVehicle() != null) {
            float yaw = player.getVehicle().getYRot();
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;
        }
    }

    public static void clear() {
        STATES.clear();
        ACTIVE.clear();
        PARTNERS.clear();
    }
}
