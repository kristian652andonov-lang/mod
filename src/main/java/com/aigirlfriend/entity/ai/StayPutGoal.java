package com.aigirlfriend.entity.ai;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** "Wait here" / "Sit": stays at the chosen spot, walking back to it after being scared away. */
public class StayPutGoal extends Goal {
    private final GirlfriendEntity gf;
    private int repath;

    public StayPutGoal(GirlfriendEntity gf) {
        this.gf = gf;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        CommandMode c = gf.getCommand();
        return (c == CommandMode.WAIT || c == CommandMode.SIT) && !gf.isPassenger() && !gf.isSleeping() && !gf.interactions().isActive();
    }

    @Override
    public void tick() {
        BlockPos wait = gf.getWaitPos();
        if (wait == null) {
            gf.setWaitPos(gf.blockPosition());
            wait = gf.blockPosition();
        }
        double d = gf.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(wait));
        if (d > 2.0) {
            gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
            if (--repath <= 0) {
                repath = 20;
                gf.getNavigation().moveTo(wait.getX() + 0.5, wait.getY(), wait.getZ() + 0.5, 1.0);
            }
            return;
        }
        gf.getNavigation().stop();
        if (gf.getCommand() == CommandMode.SIT && !gf.isSeated() && !gf.controller().fear.uneasy()) {
            if (SeatManager.sitHere(gf) == null) gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, true);
        }
    }
}
