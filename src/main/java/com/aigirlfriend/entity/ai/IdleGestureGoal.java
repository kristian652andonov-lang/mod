package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.Gesture;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Little signs of life while idle: yawning when tired, watching animals, looking around,
 * looking sad when ignored, giggling or blowing a kiss when in love.
 */
public class IdleGestureGoal extends Goal {
    private final GirlfriendEntity gf;
    @Nullable private Animal watching;
    private int watchTicks;

    public IdleGestureGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (gf.tickCount % 20 != 0 || gf.isPlayingGesture() || gf.isSleeping() || gf.isFainted() || gf.interactions().isActive()) return false;
        if (gf.controller().fear.uneasy() || !gf.getNavigation().isDone()) return false;
        if (gf.getRandom().nextInt(25) != 0) return false;
        CompanionData d = gf.data();
        Player owner = gf.getOwner();
        long now = gf.level().getGameTime();
        if (d.needs.isTired() && gf.getRandom().nextInt(3) == 0) {
            gf.playGesture(Gesture.YAWN);
            return false;
        }
        boolean ignored = owner != null && owner.distanceTo(gf) < 10 && d.needs.attention < GFConfig.LONELY_THRESHOLD.get() && now - d.lastInteractionTick > 6000;
        if (ignored && gf.getRandom().nextInt(2) == 0) {
            gf.playGesture(Gesture.SAD);
            gf.getLookControl().setLookAt(owner, 30f, 30f);
            return false;
        }
        if (gf.getRandom().nextInt(3) == 0) {
            List<Animal> animals = gf.level().getEntitiesOfClass(Animal.class, gf.getBoundingBox().inflate(8), a -> a.isAlive() && gf.hasLineOfSight(a));
            if (!animals.isEmpty()) {
                watching = animals.get(gf.getRandom().nextInt(animals.size()));
                return true;
            }
        }
        if (d.relationship.stage().isRomantic() && owner != null && owner.distanceTo(gf) < 8 && gf.getRandom().nextInt(3) == 0) {
            gf.getLookControl().setLookAt(owner, 30f, 30f);
            gf.playGesture(gf.getRandom().nextBoolean() ? Gesture.BLOW_KISS : Gesture.GIGGLE);
            return false;
        }
        gf.playGesture(Gesture.LOOK_AROUND);
        return false;
    }

    @Override
    public void start() {
        watchTicks = 80;
        gf.playGesture(Gesture.LOOK_AT_ANIMAL);
        if (watching != null && gf.getRandom().nextInt(3) == 0) {
            gf.controller().say(DialogueTrigger.LOOK_AT_ANIMAL, java.util.Map.of("animal", watching.getType().getDescription().getString().toLowerCase()));
        }
    }

    @Override
    public boolean canContinueToUse() {
        return watching != null && watching.isAlive() && watchTicks > 0 && !gf.controller().fear.uneasy();
    }

    @Override
    public void stop() {
        watching = null;
    }

    @Override
    public void tick() {
        watchTicks--;
        if (watching != null) gf.getLookControl().setLookAt(watching, 30f, 30f);
    }
}
