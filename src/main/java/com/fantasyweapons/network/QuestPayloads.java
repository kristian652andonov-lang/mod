package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Quest Journal's and class menu's messages: a quest claimed and a class chosen (client to server), and a quest done
 * (server to its player, for the toast).
 */
public final class QuestPayloads {
    private QuestPayloads() {
    }

    public record Claim(String quest) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(FantasyWeapons.id("quest_claim"));
        public static final StreamCodec<ByteBuf, Claim> CODEC = ByteBufCodecs.STRING_UTF8.map(Claim::new, Claim::quest);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    /** A class chosen in the class menu (client to server). */
    public record ChooseClass(String playerClass) implements CustomPacketPayload {
        public static final Type<ChooseClass> TYPE = new Type<>(FantasyWeapons.id("choose_class"));
        public static final StreamCodec<ByteBuf, ChooseClass> CODEC = ByteBufCodecs.STRING_UTF8.map(ChooseClass::new, ChooseClass::playerClass);

        @Override
        public Type<ChooseClass> type() {
            return TYPE;
        }
    }

    public record Completed(String quest) implements CustomPacketPayload {
        public static final Type<Completed> TYPE = new Type<>(FantasyWeapons.id("quest_completed"));
        public static final StreamCodec<ByteBuf, Completed> CODEC = ByteBufCodecs.STRING_UTF8.map(Completed::new, Completed::quest);

        @Override
        public Type<Completed> type() {
            return TYPE;
        }
    }
}
