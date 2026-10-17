package com.aigirlfriend.entity.ai;

import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.interaction.Positioning;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Blocks all other movement while a positioned interaction (hug, kiss, carry, sitting, bed) runs. */
public class InteractionLockGoal extends Goal {
    private final GirlfriendEntity gf;

    public InteractionLockGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        InteractionType t = gf.interactions().current();
        return t != null && t.positioning != Positioning.FREE;
    }

    @Override
    public void start() {
        gf.getNavigation().stop();
    }

    @Override
    public void tick() {
        gf.getNavigation().stop();
        Player p = gf.interactions().partner();
        InteractionType t = gf.interactions().current();
        if (p != null && t != null && t.positioning != Positioning.BEHIND && t != InteractionType.SLEEP_TOGETHER && t != InteractionType.CUDDLE_BED) {
            gf.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 40f, 40f);
        }
    }
}
