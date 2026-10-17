package com.aigirlfriend.entity.ai;

import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Eye contact: she mostly looks at her owner when they are close, and glances around otherwise. */
public class CompanionLookGoal extends Goal {
    private final GirlfriendEntity gf;
    private int lookTime;
    private double relX, relZ;
    private boolean atOwner;

    public CompanionLookGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !gf.isSleeping() && !gf.isFainted() && gf.getRandom().nextFloat() < 0.05f;
    }

    @Override
    public void start() {
        Player owner = gf.getOwner();
        atOwner = owner != null && owner.distanceTo(gf) < 9 && gf.getRandom().nextFloat() < 0.75f;
        lookTime = 40 + gf.getRandom().nextInt(60);
        double a = Math.PI * 2 * gf.getRandom().nextDouble();
        relX = Math.cos(a);
        relZ = Math.sin(a);
    }

    @Override
    public boolean canContinueToUse() {
        return lookTime > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        lookTime--;
        Player owner = gf.getOwner();
        if (atOwner && owner != null) gf.getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ(), 15f, 30f);
        else gf.getLookControl().setLookAt(gf.getX() + relX, gf.getEyeY(), gf.getZ() + relZ);
    }
}
