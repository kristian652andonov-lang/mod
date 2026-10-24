package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → owning client: progression feedback for notifications (EXP popups, level-up banners, unlock banners,
 * upgrade confirmations, denied requests). The authoritative values themselves arrive through the item's data component.
 */
public record ProgressionEventPayload(Kind kind, String weapon, int oldLevel, int newLevel, float oldDamage, float newDamage,
                                      long amount, List<String> abilities, String message) implements CustomPacketPayload {
    public enum Kind { EXP, LEVEL_UP, UPGRADE, POINTS, FORM, DENIED }

    public static final Type<ProgressionEventPayload> TYPE = new Type<>(FantasyWeapons.id("progression_event"));

    public static final StreamCodec<ByteBuf, ProgressionEventPayload> CODEC = new StreamCodec<>() {
        @Override
        public ProgressionEventPayload decode(ByteBuf buf) {
            Kind kind = Kind.values()[ByteBufCodecs.VAR_INT.decode(buf)];
            String weapon = ByteBufCodecs.STRING_UTF8.decode(buf);
            int oldLevel = ByteBufCodecs.VAR_INT.decode(buf);
            int newLevel = ByteBufCodecs.VAR_INT.decode(buf);
            float oldDamage = ByteBufCodecs.FLOAT.decode(buf);
            float newDamage = ByteBufCodecs.FLOAT.decode(buf);
            long amount = ByteBufCodecs.VAR_LONG.decode(buf);
            int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 64);
            List<String> abilities = new ArrayList<>(n);
            for (int i = 0; i < n; i++) abilities.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            String message = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new ProgressionEventPayload(kind, weapon, oldLevel, newLevel, oldDamage, newDamage, amount, abilities, message);
        }

        @Override
        public void encode(ByteBuf buf, ProgressionEventPayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.kind().ordinal());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.weapon());
            ByteBufCodecs.VAR_INT.encode(buf, p.oldLevel());
            ByteBufCodecs.VAR_INT.encode(buf, p.newLevel());
            ByteBufCodecs.FLOAT.encode(buf, p.oldDamage());
            ByteBufCodecs.FLOAT.encode(buf, p.newDamage());
            ByteBufCodecs.VAR_LONG.encode(buf, p.amount());
            ByteBufCodecs.VAR_INT.encode(buf, p.abilities().size());
            for (String a : p.abilities()) ByteBufCodecs.STRING_UTF8.encode(buf, a);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.message());
        }
    };

    public static ProgressionEventPayload denied(String weapon, String message) {
        return new ProgressionEventPayload(Kind.DENIED, weapon, 0, 0, 0, 0, 0, List.of(), message);
    }

    @Override
    public Type<ProgressionEventPayload> type() {
        return TYPE;
    }
}
