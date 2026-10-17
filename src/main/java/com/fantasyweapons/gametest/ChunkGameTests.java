package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkMap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Queue;
import java.util.function.BooleanSupplier;

@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChunkGameTests {
    private ChunkGameTests() {
    }

    /**
     * An unload that keeps putting itself back on the queue (a chunk wanted again while it loads) runs once a pass,
     * even when the server drains the queue with no time limit as it shuts down - so shutting down cannot spin on it.
     */
    @GameTest(template = "arena", batch = "chunks")
    public static void anUnloadThatRequeuesRunsOncePerPass(GameTestHelper h) {
        ChunkMap map = h.getLevel().getChunkSource().chunkMap;
        try {
            var queueField = ChunkMap.class.getDeclaredField("unloadQueue");
            queueField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Queue<Runnable> queue = (Queue<Runnable>) queueField.get(map);
            var process = ChunkMap.class.getDeclaredMethod("processUnloads", BooleanSupplier.class);
            process.setAccessible(true);
            int[] runs = {0};
            Runnable[] self = new Runnable[1];
            self[0] = () -> {
                // gives up by itself, so the test ends even if each pass were unbounded
                if (++runs[0] < 5000) queue.add(self[0]);
            };
            queue.add(self[0]);
            process.invoke(map, (BooleanSupplier) () -> true);
            int first = runs[0];
            process.invoke(map, (BooleanSupplier) () -> true);
            int second = runs[0];
            // drain what is left so nothing of the test stays behind
            queue.remove(self[0]);
            h.assertTrue(first == 1, "The first pass ran the requeued unload " + first + " times, not once");
            h.assertTrue(second == 2, "The second pass ran it " + (second - first) + " times, not once");
        } catch (ReflectiveOperationException e) {
            h.fail("Could not reach the chunk unload queue: " + e);
        }
        h.succeed();
    }
}
