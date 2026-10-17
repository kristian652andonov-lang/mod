package com.fantasyweapons.client.tool;

import com.fantasyweapons.tool.AreaMining;
import com.fantasyweapons.tool.LegendaryTool;
import com.fantasyweapons.tool.LegendaryTools;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the square a legendary tool will dig: the outline of every block of it that will go (in the material's colour)
 * round the block looked at, and while the player digs, the cracks spread over all of them together.
 */
public final class AreaMiningClient {
    /** Breaker ids for the cracks drawn on the square's other blocks (negative, so no entity has them). */
    private static final int CRACK_ID = -0x7A000000;
    /** The blocks given cracks last tick, to take them away again. */
    private static List<BlockPos> cracked = List.of();

    private AreaMiningClient() {
    }

    /** The square's other blocks the player would dig now at {@code hit}, or an empty list. */
    private static List<BlockPos> square(Minecraft mc, BlockHitResult hit) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return List.of();
        ItemStack tool = mc.player.getMainHandItem();
        BlockPos origin = hit.getBlockPos();
        BlockState struck = level.getBlockState(origin);
        if (!AreaMining.digsArea(mc.player, tool, struck)) return List.of();
        LegendaryTool t = (LegendaryTool) tool.getItem();
        if (t.kind() == com.fantasyweapons.tool.ToolKind.AXE && com.fantasyweapons.tool.TreeFelling.isLog(struck)) {
            List<BlockPos> logs = treeLogs(level, origin);
            if (logs != null) return logs;
        }
        float hardness = struck.getDestroySpeed(level, origin);
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : AreaMining.area(origin, hit.getDirection(), LegendaryTools.upgrades(tool).reach(), mc.player.getBlockY())) {
            if (!p.equals(origin) && AreaMining.diggable(level, p, level.getBlockState(p), t.kind(), tool, hardness)) out.add(p);
        }
        return out;
    }

    private static BlockPos treeAt;
    private static long treeTime;
    private static List<BlockPos> tree;

    /** The other logs of the tree the axe is aimed at (null if it is not a tree's), looked for again twice a second. */
    private static List<BlockPos> treeLogs(ClientLevel level, BlockPos origin) {
        long now = level.getGameTime();
        if (!origin.equals(treeAt) || now - treeTime > 10 || now < treeTime) {
            treeAt = origin.immutable();
            treeTime = now;
            var found = com.fantasyweapons.tool.TreeFelling.find(level, origin, null);
            tree = found == null ? null : found.logs().stream().filter(p -> !p.equals(origin)).toList();
        }
        return tree;
    }

    public static void onHighlight(RenderHighlightEvent.Block e) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> square = square(mc, e.getTarget());
        if (square.isEmpty()) return;
        int c = ((LegendaryTool) mc.player.getMainHandItem().getItem()).material().color();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        Vec3 cam = e.getCamera().getPosition();
        VertexConsumer lines = e.getMultiBufferSource().getBuffer(RenderType.lines());
        for (BlockPos p : square) {
            var shape = mc.level.getBlockState(p).getShape(mc.level, p);
            AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
            LevelRenderer.renderLineBox(e.getPoseStack(), lines, box.move(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z).inflate(0.002), r, g, b, 0.45f);
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> now = List.of();
        int stage = -1;
        if (mc.level != null && mc.gameMode != null && mc.gameMode.isDestroying() && mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            stage = mc.gameMode.getDestroyStage();
            if (stage >= 0) now = square(mc, hit);
        }
        LevelRenderer renderer = mc.levelRenderer;
        for (int i = now.size(); i < cracked.size(); i++) renderer.destroyBlockProgress(CRACK_ID + i, cracked.get(i), -1);
        for (int i = 0; i < now.size(); i++) renderer.destroyBlockProgress(CRACK_ID + i, now.get(i), stage);
        cracked = now;
    }
}
