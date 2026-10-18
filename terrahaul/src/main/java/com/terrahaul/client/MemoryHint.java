package com.terrahaul.client;

import com.terrahaul.Terrahaul;
import com.terrahaul.client.render.TerrahaulSkyEffects;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Warns once per game session, in chat, when Java's maximum heap is probably too small for the
 * render distance in a Terrahaul world.
 * <p>
 * A Terrahaul world is 159 sections tall instead of 24, so every chunk within render distance
 * costs several times the memory of a vanilla chunk: on the client (block data, light, render
 * sections) and, in singleplayer, again on the integrated server, which shares the heap and also
 * keeps a ring of partly generated chunks around the loaded area. When the heap runs out the game
 * stalls in garbage collection and then crashes, often without a useful log, and the remedy, a
 * lower render distance or a larger {@code -Xmx}, is not obvious.
 * <p>
 * The estimate is linear in the number of chunks: {@link #BASE_MB} for the game itself, a client
 * and a server cost per chunk within render distance, and a cost per chunk of the generation ring.
 * The per-chunk costs follow from the sizes of the client and server data structures of a
 * 159-section column and reproduce the live heap measured after a full collection at render
 * distance 16 in singleplayer (1.37 GB before {@code SectionBufferMap} saved another 87 MB; 0.56 GB
 * in a vanilla world). They scale with the world height. The recommendation
 * adds 50% headroom for garbage and chunk-generation bursts. Without an integrated server only the
 * client cost counts.
 */
public final class MemoryHint {
    /** Heap used by the game itself, independent of the render distance. */
    static final double BASE_MB = 350.0;
    /** Client chunk data, light and render sections per chunk within render distance (159 sections). */
    private static final double CLIENT_MB_PER_CHUNK = 0.27;
    /** Integrated server chunk data, light and tracking per chunk within render distance (159 sections). */
    private static final double SERVER_MB_PER_CHUNK = 0.25;
    /** Integrated server chunks still generating around the loaded area, per chunk of the ring. */
    private static final double RING_MB_PER_CHUNK = 0.28;
    /** Width of that ring in chunks. */
    private static final int RING_WIDTH = 9;
    private static final int TERRAHAUL_SECTIONS = 159;
    private static final double HEADROOM = 1.5;
    /** Waits until joining has settled before judging, so the hint is not lost among join messages. */
    private static final int SETTLE_TICKS = 100;
    private static final int MAX_RENDER_DISTANCE = 32;

    private static boolean shown;
    /** The current level, only to notice a change; never kept once the client has left it. */
    @Nullable
    private static ClientLevel level;
    private static int ticksInLevel;
    private static int checkedDistance = -1;

    private MemoryHint() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (shown) {
            return;
        }
        if (mc.level != level) {
            level = mc.level;
            ticksInLevel = 0;
            checkedDistance = -1;
        }
        if (level == null || mc.player == null || !(level.effects() instanceof TerrahaulSkyEffects) || ++ticksInLevel < SETTLE_TICKS) {
            return;
        }
        int distance = mc.options.getEffectiveRenderDistance();
        if (distance == checkedDistance) {
            return;
        }
        checkedDistance = distance;
        long max = Runtime.getRuntime().maxMemory();
        if (max == Long.MAX_VALUE) {
            return;
        }
        double heapMb = max / 1048576.0;
        boolean singleplayer = mc.hasSingleplayerServer();
        int sections = level.getSectionsCount();
        double recommendedMb = recommendedHeapMb(distance, sections, singleplayer);
        if (heapMb >= recommendedMb) {
            return;
        }
        shown = true;
        level = null;
        int safeDistance = maxDistance(heapMb, sections, singleplayer);
        int xmx = (int) Math.ceil(recommendedMb / 1024.0);
        String need = gigabytes(recommendedMb);
        String have = gigabytes(heapMb);
        Terrahaul.LOGGER.warn("Render distance {} in this {}-section world needs about {} GB of heap but the maximum is {} GB; "
            + "suggested render distance {} or -Xmx{}G", distance, sections, need, have, safeDistance, xmx);
        Component message = safeDistance >= 2
            ? Component.translatableWithFallback("message.terrahaul.memory_hint",
                "Terrahaul worlds are much taller than vanilla ones: at render distance %1$s Minecraft needs about %2$s GB of memory, "
                    + "but Java may only use %3$s GB, so the game can freeze or crash. Lower the render distance to %4$s or less, "
                    + "or allocate more memory in your launcher (Java argument -Xmx%5$sG).",
                distance, need, have, safeDistance, xmx)
            : Component.translatableWithFallback("message.terrahaul.memory_hint.heap",
                "Terrahaul worlds are much taller than vanilla ones and need more memory than the %1$s GB Java may use, "
                    + "so the game can freeze or crash. Allocate more memory in your launcher (Java argument -Xmx%2$sG).",
                have, xmx);
        mc.gui.getChat().addMessage(message.copy().withStyle(ChatFormatting.YELLOW));
    }

    /** Heap in MB that should be available at {@code distance} in a world {@code sections} sections tall. */
    public static double recommendedHeapMb(int distance, int sections, boolean singleplayer) {
        return estimatedLiveHeapMb(distance, sections, singleplayer) * HEADROOM;
    }

    /** Estimated live heap in MB (what remains after a full collection) once everything within {@code distance} has loaded. */
    public static double estimatedLiveHeapMb(int distance, int sections, boolean singleplayer) {
        double scale = sections / (double) TERRAHAUL_SECTIONS;
        double side = 2.0 * distance + 1.0;
        double chunks = side * side;
        double mb = BASE_MB + chunks * CLIENT_MB_PER_CHUNK * scale;
        if (singleplayer) {
            double outer = side + 2.0 * RING_WIDTH;
            mb += chunks * SERVER_MB_PER_CHUNK * scale + (outer * outer - chunks) * RING_MB_PER_CHUNK * scale;
        }
        return mb;
    }

    /** The highest render distance the heap is recommended for, or 0 when even the minimum is too much. */
    static int maxDistance(double heapMb, int sections, boolean singleplayer) {
        for (int distance = MAX_RENDER_DISTANCE; distance >= 2; distance--) {
            if (recommendedHeapMb(distance, sections, singleplayer) <= heapMb) {
                return distance;
            }
        }
        return 0;
    }

    private static String gigabytes(double mb) {
        return String.format(Locale.ROOT, "%.1f", mb / 1024.0);
    }
}
