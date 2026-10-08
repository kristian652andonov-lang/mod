package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client → server requests. Every one of them is only a REQUEST: the server re-validates everything.
 */
public final class C2SPayloads {
    private C2SPayloads() {
    }

    /** Ability key pressed (start charging / instant cast) or released (fire). */
    public record AbilityKey(boolean pressed) implements CustomPacketPayload {
        public static final Type<AbilityKey> TYPE = new Type<>(FantasyWeapons.id("ability_key"));
        public static final StreamCodec<ByteBuf, AbilityKey> CODEC = ByteBufCodecs.BOOL.map(AbilityKey::new, AbilityKey::pressed);

        @Override
        public Type<AbilityKey> type() {
            return TYPE;
        }
    }

    /** Cycle the selected castable ability (+1 / -1). */
    public record CycleAbility(int direction) implements CustomPacketPayload {
        public static final Type<CycleAbility> TYPE = new Type<>(FantasyWeapons.id("cycle_ability"));
        public static final StreamCodec<ByteBuf, CycleAbility> CODEC = ByteBufCodecs.VAR_INT.map(CycleAbility::new, CycleAbility::direction);

        @Override
        public Type<CycleAbility> type() {
            return TYPE;
        }
    }

    /** Switch weapon form / mode (Infernochain, Eclipse Reaper). */
    public record SwitchForm() implements CustomPacketPayload {
        public static final Type<SwitchForm> TYPE = new Type<>(FantasyWeapons.id("switch_form"));
        public static final StreamCodec<ByteBuf, SwitchForm> CODEC = StreamCodec.unit(new SwitchForm());

        @Override
        public Type<SwitchForm> type() {
            return TYPE;
        }
    }

    /** Spend mastery points to upgrade an ability of the weapon in an inventory slot. */
    public record UpgradeAbility(int slot, UUID weapon, String ability, boolean toMax) implements CustomPacketPayload {
        public static final Type<UpgradeAbility> TYPE = new Type<>(FantasyWeapons.id("upgrade_ability"));
        public static final StreamCodec<ByteBuf, UpgradeAbility> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UpgradeAbility::slot,
                UUIDUtil.STREAM_CODEC, UpgradeAbility::weapon,
                ByteBufCodecs.STRING_UTF8, UpgradeAbility::ability,
                ByteBufCodecs.BOOL, UpgradeAbility::toMax,
                UpgradeAbility::new);

        @Override
        public Type<UpgradeAbility> type() {
            return TYPE;
        }
    }

    /** Make an ability the one bound to the ability key. */
    public record SelectAbility(int slot, UUID weapon, String ability) implements CustomPacketPayload {
        public static final Type<SelectAbility> TYPE = new Type<>(FantasyWeapons.id("select_ability"));
        public static final StreamCodec<ByteBuf, SelectAbility> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SelectAbility::slot,
                UUIDUtil.STREAM_CODEC, SelectAbility::weapon,
                ByteBufCodecs.STRING_UTF8, SelectAbility::ability,
                SelectAbility::new);

        @Override
        public Type<SelectAbility> type() {
            return TYPE;
        }
    }
}
