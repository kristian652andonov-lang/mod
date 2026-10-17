package com.example.companion.network;

import com.example.companion.entity.GirlfriendEntity;
import com.example.companion.outfit.Outfit;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the player picked an outfit for her (wardrobe screen, radial menu, keybind…).
 * Send with {@code PacketDistributor.sendToServer(new SetOutfitPayload(entity.getId(), Outfit.BEACH.id))}.
 */
public record SetOutfitPayload(int entityId, String outfitId) implements CustomPacketPayload {
    public static final Type<SetOutfitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Outfit.MODID, "set_outfit"));
    public static final StreamCodec<ByteBuf, SetOutfitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetOutfitPayload::entityId,
            ByteBufCodecs.STRING_UTF8, SetOutfitPayload::outfitId,
            SetOutfitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetOutfitPayload msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level().getEntity(msg.entityId()) instanceof GirlfriendEntity her
                    && her.distanceToSqr(player) < 64 && her.canBeDressedBy(player)) {
                Outfit.byId(msg.outfitId()).ifPresent(her::chooseOutfit);
            }
        });
    }
}
