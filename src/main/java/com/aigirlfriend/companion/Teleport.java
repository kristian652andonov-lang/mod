package com.aigirlfriend.companion;

import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Safe teleport recovery: only onto walkable, hazard-free spots with room for her. */
public final class Teleport {
    private Teleport() {}

    public static boolean isSafe(GirlfriendEntity gf, BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(gf, pos) != PathType.WALKABLE) return false;
        if (gf.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) return false;
        if (!gf.level().getFluidState(pos).isEmpty()) return false;
        BlockPos delta = pos.subtract(gf.blockPosition());
        return gf.level().noCollision(gf, gf.getBoundingBox().move(delta));
    }

    /** Teleports near the player, preferring spots behind them so she does not pop up in their face. */
    public static boolean nearPlayer(GirlfriendEntity gf, Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double bx = Mth.sin(yaw) * 2.0, bz = -Mth.cos(yaw) * 2.0; // behind the player
        BlockPos base = player.blockPosition();
        BlockPos behind = BlockPos.containing(player.getX() + bx, player.getY(), player.getZ() + bz);
        for (int dy = 0; dy <= 1; dy++) {
            if (tryAt(gf, behind.above(dy))) return true;
            if (tryAt(gf, behind.below(dy))) return true;
        }
        for (int i = 0; i < 16; i++) {
            int x = base.getX() + gf.getRandom().nextIntBetweenInclusive(-3, 3);
            int y = base.getY() + gf.getRandom().nextIntBetweenInclusive(-1, 1);
            int z = base.getZ() + gf.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(x - base.getX()) < 2 && Math.abs(z - base.getZ()) < 2) continue;
            if (tryAt(gf, new BlockPos(x, y, z))) return true;
        }
        return false;
    }

    public static boolean toSafeSpot(GirlfriendEntity gf, BlockPos around) {
        if (tryAt(gf, around)) return true;
        for (int i = 0; i < 24; i++) {
            BlockPos p = around.offset(gf.getRandom().nextIntBetweenInclusive(-3, 3), gf.getRandom().nextIntBetweenInclusive(-2, 3), gf.getRandom().nextIntBetweenInclusive(-3, 3));
            if (tryAt(gf, p)) return true;
        }
        return false;
    }

    private static boolean tryAt(GirlfriendEntity gf, BlockPos pos) {
        if (!gf.level().isLoaded(pos) || !isSafe(gf, pos)) return false;
        if (gf.isPassenger()) gf.stopRiding();
        Vec3 v = Vec3.atBottomCenterOf(pos);
        gf.moveTo(v.x, v.y, v.z, gf.getYRot(), gf.getXRot());
        gf.getNavigation().stop();
        gf.fallDistance = 0f;
        return true;
    }
}
