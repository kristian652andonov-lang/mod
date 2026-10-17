package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.Gesture;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Her own affection. Once the relationship is romantic she does not ask for hugs, kisses, hand-holding or
 * cuddles: she walks up and does it, picking what fits the moment (you sitting: she snuggles up and rests
 * her head on your shoulder; you walking: she takes your hand; you standing still: hugs, kisses, back hugs).
 * Below that stage she only reaches out shyly, so the player stays in charge early on.
 * <p>
 * It never interrupts fights, item use or travel, and is rate limited by config so it stays sweet, not annoying.
 */
public class AffectionInitiativeGoal extends Goal {
    private final GirlfriendEntity gf;
    @Nullable private ServerPlayer owner;
    @Nullable private InteractionType chosen;
    private int timer;
    private int repath;

    public AffectionInitiativeGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private long interval() {
        CompanionData d = gf.data();
        double base = GFConfig.INITIATIVE_MIN_SECONDS.get() * 20.0;
        Mood m = d.mood.primary;
        if (m == Mood.AFFECTIONATE || m == Mood.NEEDY) base *= 0.6;
        else if (m == Mood.INDEPENDENT || m == Mood.IRRITABLE) base *= 1.8;
        else if (m == Mood.SHY) base *= 1.3;
        base *= 1.25 - d.personality.affection * 0.5;
        if (d.needs.attention < GFConfig.LONELY_THRESHOLD.get()) base *= 0.7;
        return (long) base;
    }

    @Override
    public boolean canUse() {
        if (gf.tickCount % 20 != 0 || !GFConfig.ROMANTIC_INITIATIVE.get()) return false;
        CompanionData d = gf.data();
        RelationshipStage stage = d.relationship.stage();
        if (!stage.atLeast(RelationshipStage.GROWING_AFFECTION)) return false;
        if (gf.isSleeping() || gf.isFainted() || gf.isPassenger() && !gf.isSeated() || gf.controller().fear.uneasy()) return false;
        if (gf.getCommand() == com.aigirlfriend.companion.CommandMode.WAIT || gf.getCommand() == com.aigirlfriend.companion.CommandMode.HOME) return false;
        InteractionType cur = gf.interactions().current();
        if (cur != null && cur != InteractionType.SIT_TOGETHER && cur != InteractionType.HOLD_HANDS) return false;
        long now = gf.level().getGameTime();
        if (now - gf.controller().lastInitiativeTick < interval()) return false;
        if (!(gf.getOwner() instanceof ServerPlayer sp) || sp.distanceTo(gf) > 10 || !ownerAvailable(sp)) return false;
        if (gf.getRandom().nextFloat() > 0.25f) return false;
        owner = sp;
        if (!stage.isRomantic()) {
            // not yet together: shy, asking gestures only
            gf.controller().lastInitiativeTick = now;
            gf.getLookControl().setLookAt(sp, 30f, 30f);
            if (d.needs.attention < 55f && !gf.interactions().onCooldown(InteractionType.HOLD_HANDS)) {
                gf.playGesture(Gesture.REACH_HAND);
                gf.controller().reachHandUntil = now + 200;
            } else {
                gf.playGesture(gf.getRandom().nextBoolean() ? Gesture.WAVE : Gesture.GIGGLE);
            }
            return false;
        }
        chosen = choose(sp, cur, gf.getRandom());
        return chosen != null;
    }

    /** Avoid interrupting gameplay: no fights, no item use, not while flying, swimming, riding or mining. */
    private boolean ownerAvailable(ServerPlayer sp) {
        if (sp.isSleeping() || sp.isFallFlying() || sp.isSwimming() || sp.isUsingItem() || sp.swinging) return false;
        if (sp.isPassenger() && !SeatManager.isSeated(sp)) return false;
        if (gf.level().getGameTime() - sp.getLastHurtByMobTimestamp() < 200) return false;
        return gf.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(12), m -> m instanceof Enemy && m.isAlive()).isEmpty();
    }

    @Nullable
    private InteractionType choose(ServerPlayer sp, @Nullable InteractionType cur, RandomSource r) {
        CompanionData d = gf.data();
        boolean ownerSeated = SeatManager.isSeated(sp);
        boolean ownerStill = gf.level().getGameTime() - gf.controller().ownerStillSince > 50;
        List<InteractionType> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        if (ownerSeated) {
            if (cur == InteractionType.SIT_TOGETHER) {
                add(pool, weights, InteractionType.HEAD_ON_SHOULDER, 3f);
                add(pool, weights, InteractionType.CUDDLE_SITTING, 2.5f + d.personality.affection);
                add(pool, weights, InteractionType.LAP_SIT, 1.5f + d.personality.playfulness + (d.mood.primary == Mood.NEEDY ? 1.5f : 0f));
            } else if (cur == null) {
                add(pool, weights, InteractionType.SIT_TOGETHER, 3f);
                add(pool, weights, InteractionType.LAP_SIT, 1.2f + d.personality.playfulness);
            }
        } else if (ownerStill && sp.onGround()) {
            if (cur == InteractionType.HOLD_HANDS) return null;
            add(pool, weights, InteractionType.HUG, 3f);
            add(pool, weights, InteractionType.BACK_HUG, 1.5f + d.personality.playfulness);
            add(pool, weights, InteractionType.KISS_CHEEK, 2f + d.personality.playfulness);
            add(pool, weights, InteractionType.KISS, 1.5f + d.personality.affection * 1.5f);
            add(pool, weights, InteractionType.CUDDLE, 1.5f + (d.mood.primary == Mood.NEEDY || d.mood.primary == Mood.AFFECTIONATE ? 2f : 0f));
            add(pool, weights, InteractionType.LEAN_ON, d.needs.isTired() ? 3f : 1f);
            add(pool, weights, InteractionType.PET_HAIR, 0.8f + d.personality.shyness);
        } else if (cur == null && sp.getDeltaMovement().horizontalDistanceSqr() < 0.06 && !sp.isSprinting()) {
            add(pool, weights, InteractionType.HOLD_HANDS, 4f);
        }
        pool.removeIf(t -> gf.interactions().onCooldown(t) || !d.relationship.stage().atLeast(t.stage));
        if (pool.isEmpty()) return null;
        if (pool.size() != weights.size()) {
            weights.clear();
            for (int i = 0; i < pool.size(); i++) weights.add(1f);
        }
        float total = 0;
        for (float w : weights) total += w;
        float pick = r.nextFloat() * total;
        for (int i = 0; i < pool.size(); i++) {
            pick -= weights.get(i);
            if (pick <= 0) return pool.get(i);
        }
        return pool.get(0);
    }

    private static void add(List<InteractionType> pool, List<Float> weights, InteractionType t, float w) {
        pool.add(t);
        weights.add(w);
    }

    @Override
    public void start() {
        timer = 0;
        repath = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && chosen != null && timer < 160 && owner.isAlive() && !gf.controller().fear.uneasy();
    }

    @Override
    public void stop() {
        owner = null;
        chosen = null;
    }

    @Override
    public void tick() {
        timer++;
        if (owner == null || chosen == null) return;
        gf.getLookControl().setLookAt(owner, 30f, 30f);
        double need = chosen.isSeated() || chosen == InteractionType.HOLD_HANDS || chosen == InteractionType.LAP_SIT ? 2.5 : 1.6;
        if (chosen == InteractionType.SIT_TOGETHER) need = 2.0;
        if (gf.distanceTo(owner) > need && !(gf.isSeated() && chosen.isSeated())) {
            if (--repath <= 0) {
                repath = 8;
                gf.getNavigation().moveTo(owner, 1.05);
            }
            return;
        }
        gf.getNavigation().stop();
        gf.controller().lastInitiativeTick = gf.level().getGameTime();
        InteractionType cur = gf.interactions().current();
        if (cur != null && cur != InteractionType.SIT_TOGETHER) gf.interactions().stop(com.aigirlfriend.interaction.InteractionManager.StopReason.REPLACED);
        if (!gf.interactions().start(chosen, owner, true)) {
            // couldn't (no room etc.): a little gesture instead
            gf.playGesture(Gesture.BLOW_KISS);
            gf.controller().say(DialogueTrigger.AFFECTIONATE);
        }
        chosen = null;
    }
}
