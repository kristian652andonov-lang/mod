package com.fantasyweapons.mixin;

import com.fantasyweapons.client.wreck.WreckSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the destruction a low effect quality lands a little at a time (see {@link WreckSync}) in step with the
 * game's own news of the same chunks: before a block of a chunk changes, the land still waiting for that chunk goes in
 * first, so the newer block is never covered over by the older picture; a chunk sent whole, or let go, drops what
 * waited for it. (Each handler runs first on the network thread, only to be passed to the game's; this acts on the
 * game's.)
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), require = 0)
    private void fantasyweapons$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) WreckSync.flush(packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), require = 0)
    private void fantasyweapons$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread() || !WreckSync.waiting()) return;
        // every block of the packet is in one section, so in one chunk
        long[] chunk = {Long.MIN_VALUE};
        packet.runUpdates((pos, state) -> {
            if (chunk[0] == Long.MIN_VALUE) chunk[0] = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        });
        if (chunk[0] != Long.MIN_VALUE) WreckSync.flush(net.minecraft.world.level.ChunkPos.getX(chunk[0]), net.minecraft.world.level.ChunkPos.getZ(chunk[0]));
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"), require = 0)
    private void fantasyweapons$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) WreckSync.drop(packet.getX(), packet.getZ());
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), require = 0)
    private void fantasyweapons$forget(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) WreckSync.drop(packet.pos().x, packet.pos().z);
    }
}
