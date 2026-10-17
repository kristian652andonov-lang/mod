package com.aigirlfriend.entity;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.companion.Expression;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.interaction.Gesture;
import com.aigirlfriend.interaction.InteractionType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.HashMap;
import java.util.Map;

/**
 * Animation selection for the companion, layered over four GeckoLib controllers:
 * <ol>
 *   <li><b>main</b> - locomotion and body pose (idle, walk, run, sit, sleep, carried...)</li>
 *   <li><b>action</b> - one-shot gestures (wave, yawn, eat, startled, celebrate...)</li>
 *   <li><b>interaction</b> - coordinated interactions with the player (hug, kiss, cuddle...)</li>
 *   <li><b>face</b> - facial expressions</li>
 * </ol>
 * Every controller has a transition time so changes blend instead of snapping. Animation names are resolved
 * against the loaded animation file with fallbacks, so a partially finished animation set never errors:
 * missing names fall back to related animations (e.g. "interaction.cuddle_sitting" -> "interaction.sit_together" -> "pose.sit").
 */
public final class GirlfriendAnimations {
    public static final ResourceLocation ANIMATION_FILE = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "animations/entity/girlfriend.animation.json");

    private static final Map<String, RawAnimation> LOOPS = new HashMap<>();
    private static final Map<String, RawAnimation> ONCE = new HashMap<>();
    private static final Map<String, String> FALLBACK = new HashMap<>();
    private static BakedAnimations cachedFile;
    private static final Map<String, String> RESOLVED = new HashMap<>();

    static {
        fb("move.idle_tired", "move.idle");
        fb("move.idle_scared", "move.idle");
        fb("move.idle_sad", "move.idle");
        fb("move.hide", "move.idle_scared");
        fb("move.walk_tired", "move.walk");
        fb("move.walk_scared", "move.walk");
        fb("move.run", "move.walk");
        fb("move.crouch", "move.idle");
        fb("move.crouch_walk", "move.walk");
        fb("move.swim", "move.walk");
        fb("pose.sit_ground", "pose.sit");
        fb("pose.sit", "move.idle");
        fb("pose.sleep", "move.idle");
        fb("pose.fainted", "pose.sleep");
        fb("pose.carried", "interaction.carried");
        fb("interaction.carried", "move.idle");
        fb("interaction.hug", "move.idle");
        fb("interaction.back_hug", "interaction.hug");
        fb("interaction.cuddle", "interaction.hug");
        fb("interaction.kiss", "interaction.hug");
        fb("interaction.kiss_cheek", "interaction.kiss");
        fb("interaction.forehead_kiss", "interaction.hug");
        fb("interaction.lean", "interaction.hug");
        fb("interaction.hold_hands", "move.idle");
        fb("interaction.lead_by_hand", "interaction.hold_hands");
        fb("interaction.sit_together", "pose.sit");
        fb("interaction.head_on_shoulder", "interaction.sit_together");
        fb("interaction.cuddle_sitting", "interaction.head_on_shoulder");
        fb("action.wake_up", "action.stretch");
        fb("pose.lie_down", "pose.sleep");
        fb("interaction.cuddle_bed", "pose.sleep");
        fb("interaction.sleep_together", "pose.sleep");
        fb("interaction.pet_hair", "move.idle");
        fb("interaction.pet_cheek", "interaction.pet_hair");
        fb("interaction.lap_sit", "pose.lap_sit");
        fb("pose.lap_sit", "pose.sit");
        fb("action.wave_goodbye", "action.wave");
        fb("action.blow_kiss", "action.wave");
        fb("action.giggle", "action.gift_happy");
        fb("action.wake_greeting", "action.wave");
        fb("action.relieved", "action.celebrate");
        fb("action.ask_help", "action.reach_hand");
        fb("action.look_at_animal", "action.look_around");
        fb("action.pout", "action.sad");
        fb("action.defend", "action.startled");
        fb("action.hurt", "action.startled");
    }

    private GirlfriendAnimations() {}

    private static void fb(String name, String fallback) {
        FALLBACK.put(name, fallback);
    }

    /** Resolves an animation name to one that exists in the loaded file (or null if nothing fits). */
    public static String resolve(String name) {
        BakedAnimations file = GeckoLibCache.getBakedAnimations().get(ANIMATION_FILE);
        if (file != cachedFile) {
            cachedFile = file;
            RESOLVED.clear();
        }
        if (file == null) return null;
        return RESOLVED.computeIfAbsent(name, n -> {
            String cur = n;
            for (int i = 0; i < 8 && cur != null; i++) {
                if (file.getAnimation(cur) != null) return cur;
                cur = FALLBACK.get(cur);
            }
            return file.getAnimation("move.idle") != null ? "move.idle" : null;
        });
    }

    public static boolean exists(String name) {
        BakedAnimations file = GeckoLibCache.getBakedAnimations().get(ANIMATION_FILE);
        return file != null && file.getAnimation(name) != null;
    }

    private static RawAnimation loop(String name) {
        return LOOPS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n));
    }

    private static final Map<String, RawAnimation> CHAINS = new HashMap<>();

    private static RawAnimation lieThenSleep(String lie, String sleep) {
        return CHAINS.computeIfAbsent(lie + ">" + sleep, k -> RawAnimation.begin().thenPlay(lie).thenLoop(sleep));
    }

    private static RawAnimation once(String name) {
        return ONCE.computeIfAbsent(name, n -> RawAnimation.begin().thenPlay(n));
    }

    /** Client-side bookkeeping for gestures. */
    public static final class ClientState {
        int lastGestureSeq = Integer.MIN_VALUE;
        int gestureEndTick;
        /** Anti-snap filter state (see GirlfriendModel). */
        public double lastPoseTick;
        public java.util.Map<String, float[]> lastPose;
    }

    private static double speed() {
        try {
            return GFClientConfig.ANIMATION_SPEED.get();
        } catch (Exception e) {
            return 1.0;
        }
    }

    public static void register(GirlfriendEntity gf, AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(gf, "main", 5, state -> main(gf, state)));
        controllers.add(new AnimationController<>(gf, "action", 4, state -> action(gf, state)));
        controllers.add(new AnimationController<>(gf, "interaction", 6, state -> interaction(gf, state)));
        controllers.add(new AnimationController<>(gf, "face", 3, state -> face(gf, state)));
    }

    private static PlayState play(AnimationState<GirlfriendEntity> state, String name, boolean looping) {
        String resolved = resolve(name);
        if (resolved == null) return PlayState.STOP;
        state.getController().setAnimationSpeed(speed());
        return state.setAndContinue(looping ? loop(resolved) : once(resolved));
    }

    private static PlayState main(GirlfriendEntity gf, AnimationState<GirlfriendEntity> state) {
        String anim;
        if (gf.isFainted()) anim = "pose.fainted";
        else if (gf.isSleeping()) {
            // lie down first, then breathe in her sleep
            String lie = resolve("pose.lie_down"), sleep = resolve("pose.sleep");
            if (sleep == null) return PlayState.STOP;
            state.getController().setAnimationSpeed(speed());
            return state.setAndContinue(lie != null && !lie.equals(sleep) ? lieThenSleep(lie, sleep) : loop(sleep));
        }
        else if (gf.isCarried()) anim = "pose.carried";
        else if (gf.isOnLap()) anim = "pose.lap_sit";
        else if (gf.getVehicle() instanceof SeatEntity) anim = "pose.sit";
        else if (gf.hasFlag(GirlfriendEntity.FLAG_GROUND_SIT)) anim = "pose.sit_ground";
        else if (gf.isInWater() && !gf.onGround()) anim = "move.swim";
        else {
            double speed = gf.getDeltaMovement().horizontalDistance();
            boolean moving = state.isMoving() || speed > 0.03;
            boolean tired = gf.hasFlag(GirlfriendEntity.FLAG_EXHAUSTED) || gf.hasFlag(GirlfriendEntity.FLAG_TIRED);
            if (moving) {
                if (gf.hasFlag(GirlfriendEntity.FLAG_RUNNING) || speed > 0.2) anim = "move.run";
                else if (gf.isCrouching()) anim = "move.crouch_walk";
                else if (gf.isScared()) anim = "move.walk_scared";
                else if (gf.hasFlag(GirlfriendEntity.FLAG_EXHAUSTED)) anim = "move.walk_tired";
                else anim = "move.walk";
            } else if (gf.isCrouching()) anim = "move.crouch";
            else if (gf.hasFlag(GirlfriendEntity.FLAG_HIDING)) anim = "move.hide";
            else if (gf.isScared()) anim = "move.idle_scared";
            else if (tired) anim = "move.idle_tired";
            else if (gf.hasFlag(GirlfriendEntity.FLAG_SAD)) anim = "move.idle_sad";
            else anim = "move.idle";
        }
        return play(state, anim, true);
    }

    private static PlayState action(GirlfriendEntity gf, AnimationState<GirlfriendEntity> state) {
        ClientState cs = gf.animState;
        int seq = gf.getGestureSequence();
        Gesture g = gf.getGesture();
        if (cs.lastGestureSeq == Integer.MIN_VALUE) cs.lastGestureSeq = seq; // do not replay old gestures when first seen
        if (seq != cs.lastGestureSeq && g != Gesture.NONE) {
            cs.lastGestureSeq = seq;
            cs.gestureEndTick = gf.tickCount + g.duration;
            String resolved = resolve(g.animation());
            if (resolved == null) return PlayState.STOP;
            state.getController().forceAnimationReset();
            state.getController().setAnimationSpeed(speed());
            return state.setAndContinue(once(resolved));
        }
        if (gf.tickCount < cs.gestureEndTick && !state.getController().hasAnimationFinished()) return PlayState.CONTINUE;
        return PlayState.STOP;
    }

    private static PlayState interaction(GirlfriendEntity gf, AnimationState<GirlfriendEntity> state) {
        InteractionType type = gf.getSyncedInteraction();
        if (type == null) return PlayState.STOP;
        String name = type.animation;
        if (gf.isSyncedInteractionSelfInitiated() && exists(name + "_initiate")) name = name + "_initiate";
        if (exists(name + "_left") || exists(name + "_right")) name = name + (partnerOnLeft(gf) ? "_left" : "_right");
        if (gf.hasFlag(GirlfriendEntity.FLAG_SHY) && exists(name + "_shy")) name = name + "_shy";
        return play(state, name, true);
    }

    /** True if the interaction partner is on her left-hand side. */
    private static boolean partnerOnLeft(GirlfriendEntity gf) {
        net.minecraft.world.entity.Entity p = gf.level().getEntity(gf.getSyncedPartnerId());
        if (p == null) return true;
        double yaw = Math.toRadians(gf.yBodyRot);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        return (p.getX() - gf.getX()) * lx + (p.getZ() - gf.getZ()) * lz > 0;
    }

    private static PlayState face(GirlfriendEntity gf, AnimationState<GirlfriendEntity> state) {
        Expression e = gf.getExpression();
        String name = "expression." + e.id();
        if (!exists(name)) return PlayState.STOP;
        return play(state, name, true);
    }
}
