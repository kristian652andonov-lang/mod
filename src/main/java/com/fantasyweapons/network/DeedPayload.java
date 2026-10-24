package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server → the player who earned it: one of the mod's deeds (advancements), for its own announcement (the deed's toast
 * and fanfare) in place of vanilla's. {@code tier} is the advancement type's ordinal (task, challenge, goal).
 */
public record DeedPayload(ResourceLocation id, Component title, Component description, ItemStack icon, int tier) implements CustomPacketPayload {
    public static final Type<DeedPayload> TYPE = new Type<>(FantasyWeapons.id("deed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeedPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, DeedPayload::id,
            ComponentSerialization.TRUSTED_STREAM_CODEC, DeedPayload::title,
            ComponentSerialization.TRUSTED_STREAM_CODEC, DeedPayload::description,
            ItemStack.OPTIONAL_STREAM_CODEC, DeedPayload::icon,
            ByteBufCodecs.VAR_INT, DeedPayload::tier,
            DeedPayload::new);

    @Override
    public Type<DeedPayload> type() {
        return TYPE;
    }
}
