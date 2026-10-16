package com.fantasyweapons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.function.BooleanSupplier;

/**
 * Keeps the game from hanging on "Saving worlds". A chunk that is let go and then wanted again while it loads keeps
 * putting its unload back on the queue until the load is done; while the server shuts down it drains that queue with
 * no time limit, so it spins on that one chunk forever, and the load it waits for (which needs this same thread) never
 * finishes. Big world destruction makes this likely, as it loads and lets go of thousands of chunks. Here each pass
 * runs only what was queued when it began; whatever puts itself back waits for the next pass, after the server has
 * had its turn to run the loading work.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private Queue<Runnable> unloadQueue;

    @Unique
    private int fantasyweapons$unloadsLeft = -1;

    @Inject(method = "processUnloads", at = @At("HEAD"), require = 0)
    private void fantasyweapons$startUnloads(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        fantasyweapons$unloadsLeft = -1;
    }

    @WrapOperation(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"), require = 0)
    private Object fantasyweapons$nextUnload(Queue<Runnable> queue, Operation<Object> poll) {
        if (queue != unloadQueue) return poll.call(queue);
        // counted at the first poll, after this pass has queued the chunks it is letting go of
        if (fantasyweapons$unloadsLeft < 0) fantasyweapons$unloadsLeft = queue.size();
        if (fantasyweapons$unloadsLeft == 0) return null;
        fantasyweapons$unloadsLeft--;
        return poll.call(queue);
    }
}
