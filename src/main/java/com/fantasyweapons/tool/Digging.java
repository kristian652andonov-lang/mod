package com.fantasyweapons.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaks many blocks at once for a legendary tool (its square, a felled tree, its abilities): each as the player would
 * break it, so protections, quests, stats and the tool's Fortune all apply, with what they drop gathered up and dropped in
 * one place as a few stacks, their experience as one orb.
 */
public final class Digging {
    /** The digging going on on this thread, if any: where its drops are gathered. */
    private static final ThreadLocal<Gather> DIGGING = new ThreadLocal<>();

    private static final class Gather {
        final ServerLevel level;
        final List<ItemStack> drops = new ArrayList<>();
        int experience;

        Gather(ServerLevel level) {
            this.level = level;
        }
    }

    private Digging() {
    }

    /** Whether blocks are being broken for a legendary tool right now (their breaks don't dig squares of their own). */
    public static boolean active() {
        return DIGGING.get() != null;
    }

    /**
     * Breaks {@code blocks} as {@code player}, drops everything they drop at {@code at} and shows the player the break
     * effects of about {@code effects} of them (each player's own client only breaks the block they struck; the others
     * watching see them all). Returns how many were broken.
     */
    public static int breakAll(ServerPlayer player, List<BlockPos> blocks, Vec3 at, int effects) {
        if (blocks.isEmpty() || !(player.level() instanceof ServerLevel level)) return 0;
        Gather gather = new Gather(level);
        Gather outer = DIGGING.get();
        DIGGING.set(gather);
        int broken = 0;
        try {
            float show = Math.min(1f, effects / (float) blocks.size());
            for (BlockPos p : blocks) {
                BlockState state = level.getBlockState(p);
                if (state.isAir() || !player.gameMode.destroyBlock(p)) continue;
                broken++;
                if (player.getRandom().nextFloat() < show) player.connection.send(new ClientboundLevelEventPacket(2001, p, Block.getId(state), false));
            }
        } finally {
            if (outer == null) DIGGING.remove();
            else DIGGING.set(outer);
        }
        drop(gather, at);
        if (broken > 0 && !player.isCreative()) com.fantasyweapons.quest.QuestService.event(player, "dig_area", broken);
        return broken;
    }

    /** The drops of every block being dug, gathered up instead of dropped where each block was. */
    static void onDrops(BlockDropsEvent e) {
        Gather gather = DIGGING.get();
        if (gather == null || e.isCanceled() || e.getLevel() != gather.level) return;
        for (ItemEntity item : e.getDrops()) merge(gather.drops, item.getItem());
        e.getDrops().clear();
        gather.experience += e.getDroppedExperience();
        e.setDroppedExperience(0);
    }

    private static void merge(List<ItemStack> into, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack s : into) {
            if (rest.isEmpty()) return;
            if (!ItemStack.isSameItemSameComponents(s, rest) || s.getCount() >= s.getMaxStackSize()) continue;
            int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
            s.grow(move);
            rest.shrink(move);
        }
        if (!rest.isEmpty()) into.add(rest);
    }

    private static void drop(Gather gather, Vec3 at) {
        for (ItemStack stack : gather.drops) {
            ItemEntity item = new ItemEntity(gather.level, at.x, at.y, at.z, stack, 0, 0.1, 0);
            item.setDefaultPickUpDelay();
            gather.level.addFreshEntity(item);
        }
        if (gather.experience > 0) ExperienceOrb.award(gather.level, at, gather.experience);
    }
}
