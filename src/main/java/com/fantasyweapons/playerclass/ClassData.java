package com.fantasyweapons.playerclass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's class, when they last took one (game time, for the wait before changing again), and whether the class menu
 * has been put before them (it opens by itself the first time they join a world).
 */
public record ClassData(PlayerClass playerClass, long chosenAt, boolean offered) {
    public static final ClassData NONE = new ClassData(PlayerClass.NONE, -1, false);

    public ClassData(PlayerClass playerClass, long chosenAt) {
        this(playerClass, chosenAt, true);
    }

    public ClassData withOffered() {
        return new ClassData(playerClass, chosenAt, true);
    }

    public static final Codec<ClassData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("class").forGetter(d -> d.playerClass.id()),
            Codec.LONG.optionalFieldOf("chosen_at", -1L).forGetter(ClassData::chosenAt),
            Codec.BOOL.optionalFieldOf("offered", false).forGetter(ClassData::offered)
    ).apply(i, (id, at, offered) -> new ClassData(PlayerClass.byId(id), at, offered)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClassData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, d -> d.playerClass.id(),
            ByteBufCodecs.VAR_LONG, ClassData::chosenAt,
            ByteBufCodecs.BOOL, ClassData::offered,
            (id, at, offered) -> new ClassData(PlayerClass.byId(id), at, offered));
}
