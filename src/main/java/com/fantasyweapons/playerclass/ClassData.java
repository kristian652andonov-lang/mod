package com.fantasyweapons.playerclass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A player's class and when they last took one (game time), for the wait before changing again. */
public record ClassData(PlayerClass playerClass, long chosenAt) {
    public static final ClassData NONE = new ClassData(PlayerClass.NONE, -1);

    public static final Codec<ClassData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("class").forGetter(d -> d.playerClass.id()),
            Codec.LONG.optionalFieldOf("chosen_at", -1L).forGetter(ClassData::chosenAt)
    ).apply(i, (id, at) -> new ClassData(PlayerClass.byId(id), at)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClassData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, d -> d.playerClass.id(),
            ByteBufCodecs.VAR_LONG, ClassData::chosenAt,
            (id, at) -> new ClassData(PlayerClass.byId(id), at));
}
