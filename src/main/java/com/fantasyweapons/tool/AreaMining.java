package com.fantasyweapons.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Legendary tools dig more than the block they break. A pickaxe or shovel digs a square round it, 3x3 at first and up to
 * 13x13 (see {@link ToolUpgrades#reach}), across the face that was struck. An axe fells the whole tree a log belongs to
 * (see {@link TreeFelling}), and digs the square in other wood. Only what the tool is made for goes (a pickaxe's stone and
 * ores, a shovel's earth, an axe's wood), nothing much harder than the block struck, and never a block entity (chests and
 * the like keep their contents). Everything drops together on the block that was struck (see {@link Digging}). Sneaking
 * digs a single block.
 */
public final class AreaMining {
    /** How many of the dug blocks show their break effect to the player who dug them (all of them would be thousands of particles). */
    private static final int EFFECTS = 24;

    private AreaMining() {
    }

    public static void register(IEventBus bus) {
        // last, so a protection that cancels the break stops the whole square
        bus.addListener(EventPriority.LOWEST, AreaMining::onBreak);
        bus.addListener(EventPriority.LOW, Digging::onDrops);
    }

    /** Whether {@code player} digs more than one block with {@code tool} where it would break {@code state}. */
    public static boolean digsArea(Player player, ItemStack tool, BlockState state) {
        return tool.getItem() instanceof LegendaryTool t && !player.isShiftKeyDown() && state.is(t.kind().mines());
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || Digging.active() || !(e.getPlayer() instanceof ServerPlayer player) || !(e.getLevel() instanceof ServerLevel level)) return;
        ItemStack tool = player.getMainHandItem();
        BlockPos origin = e.getPos();
        if (!digsArea(player, tool, e.getState())) return;
        LegendaryTool t = (LegendaryTool) tool.getItem();
        if (t.kind() == ToolKind.AXE && TreeFelling.isLog(e.getState())) {
            TreeFelling.Tree tree = TreeFelling.find(level, origin, null);
            if (tree != null) {
                List<BlockPos> rest = new ArrayList<>(tree.blocks());
                rest.remove(origin);
                Digging.breakAll(player, rest, Vec3.atCenterOf(origin), EFFECTS);
                return;
            }
        }
        float hardness = e.getState().getDestroySpeed(level, origin);
        List<BlockPos> dig = new ArrayList<>();
        for (BlockPos p : area(origin, face(player, origin), LegendaryTools.upgrades(tool).reach(), player.getBlockY())) {
            if (!p.equals(origin) && diggable(level, p, level.getBlockState(p), t.kind(), tool, hardness)) dig.add(p.immutable());
        }
        Digging.breakAll(player, dig, Vec3.atCenterOf(origin), EFFECTS);
    }

    /** The face of {@code pos} the player is looking at (or, failing that, the one turned most towards them). */
    static Direction face(Player player, BlockPos pos) {
        HitResult hit = player.pick(player.blockInteractionRange() + 1, 1f, false);
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(pos)) return b.getDirection();
        Vec3 look = player.getLookAngle();
        return Direction.getNearest(look.x, look.y, look.z).getOpposite();
    }

    /**
     * The square dug round {@code origin} across {@code face}, {@code reach} blocks each way. On a wall it stands on the
     * digger's feet ({@code feetY}) instead of reaching below them, as long as it still holds the block that was struck:
     * a 13x13 tunnel has its floor where the digger stands.
     */
    public static List<BlockPos> area(BlockPos origin, Direction face, int reach, int feetY) {
        List<BlockPos> out = new ArrayList<>();
        int size = 2 * reach + 1;
        if (face.getAxis() == Direction.Axis.Y) {
            for (int a = -reach; a <= reach; a++) for (int b = -reach; b <= reach; b++) out.add(origin.offset(a, 0, b));
            return out;
        }
        int bottom = Math.max(origin.getY() - (size - 1), Math.min(origin.getY(), feetY));
        Direction across = face.getClockWise();
        for (int a = -reach; a <= reach; a++) {
            for (int y = bottom; y < bottom + size; y++) {
                out.add(new BlockPos(origin.getX() + across.getStepX() * a, y, origin.getZ() + across.getStepZ() * a));
            }
        }
        return out;
    }

    /**
     * Whether a block of the square goes with the struck one: something the tool is made for and digs properly, not
     * unbreakable, not much harder than the struck block (twice as hard as it, or as stone ores, at most), and not a block
     * entity.
     */
    public static boolean diggable(BlockGetter level, BlockPos pos, BlockState state, ToolKind kind, ItemStack tool, float struckHardness) {
        if (state.isAir() || !state.is(kind.mines()) || !tool.isCorrectToolForDrops(state) || state.hasBlockEntity()) return false;
        float h = state.getDestroySpeed(level, pos);
        return h >= 0 && h <= Math.max(struckHardness, 3f) * 2f;
    }
}
