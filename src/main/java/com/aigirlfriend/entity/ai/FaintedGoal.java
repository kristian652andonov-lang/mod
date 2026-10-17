package com.aigirlfriend.entity.ai;

import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** While knocked out she cannot move or look around. */
public class FaintedGoal extends Goal {
    private final GirlfriendEntity gf;

    public FaintedGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return gf.isFainted();
    }

    @Override
    public void tick() {
        gf.getNavigation().stop();
    }
}
