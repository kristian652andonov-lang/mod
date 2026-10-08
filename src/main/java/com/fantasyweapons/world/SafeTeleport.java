package com.fantasyweapons.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side teleport validation. Destinations are searched by marching along the path, so a player can never pass
 * through walls, leave the world border, end inside blocks, enter unloaded chunks or teleport into lava.
 */
public final class SafeTeleport {
    private static final double STEP = 0.25;
    private static final double[] VERTICAL_TRIES = {0, 0.55, 1.05, -0.55, -1.05};

    private SafeTeleport() {
    }

    /**
     * Furthest safe position along {@code dir} from the entity's position, up to {@code maxDistance}.
     *
     * @return the destination, or null if not even a short hop is possible
     */
    @Nullable
    public static Vec3 along(ServerPlayer player, Vec3 dir, double maxDistance) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 eye = new Vec3(0, player.getEyeHeight() * 0.8, 0);
        Vec3 n = dir.normalize();
        Vec3 best = null;
        Vec3 lastGood = start;
        for (double d = STEP; d <= maxDistance + 1e-6; d += STEP) {
            Vec3 cand = start.add(n.scale(d));
            Vec3 ok = null;
            for (double dy : VERTICAL_TRIES) {
                Vec3 c = cand.add(0, dy, 0);
                if (isSafe(level, player, c)) {
                    ok = c;
                    break;
                }
            }
            if (ok == null) {
                // stop if a solid wall is in the way, otherwise keep marching (e.g. a thin pillar we can blink past)
                if (blocked(level, player, lastGood.add(eye), cand.add(eye))) break;
                continue;
            }
            if (blocked(level, player, lastGood.add(eye), ok.add(eye))) break;
            best = ok;
            lastGood = ok;
        }
        return best != null && best.distanceToSqr(start) >= 1.0 ? best : null;
    }

    /** Validates a specific point (used for "behind the target" teleports), trying small vertical offsets. */
    @Nullable
    public static Vec3 near(ServerPlayer player, Vec3 target) {
        for (double dy : VERTICAL_TRIES) {
            Vec3 c = target.add(0, dy, 0);
            if (isSafe(player.serverLevel(), player, c) && !blocked(player.serverLevel(), player, player.getEyePosition(), c.add(0, 1, 0))) {
                return c;
            }
        }
        return null;
    }

    public static boolean isSafe(ServerLevel level, Entity entity, Vec3 pos) {
        WorldBorder border = level.getWorldBorder();
        if (!border.isWithinBounds(pos.x, pos.z)) return false;
        if (pos.y < level.getMinBuildHeight() + 1 || pos.y > level.getMaxBuildHeight() + 32) return false;
        if (!level.isLoaded(BlockPos.containing(pos))) return false;
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position())).deflate(1.0E-3);
        if (!level.noCollision(entity, box)) return false;
        return level.getBlockStates(box).noneMatch(s -> s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.FIRE)
                || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH));
    }

    private static boolean blocked(ServerLevel level, Entity entity, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() != HitResult.Type.MISS;
    }

    /** Performs the teleport and clears fall damage. */
    public static void teleport(ServerPlayer player, Vec3 dest) {
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();
        player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
        player.hurtMarked = true;
    }
}
