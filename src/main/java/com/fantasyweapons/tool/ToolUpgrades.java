package com.fantasyweapons.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How far a legendary tool has been upgraded: {@code reach} blocks round the struck block it digs (1: 3x3, up to 6:
 * 13x13), and its Fortune and Efficiency levels. Saved with the tool.
 */
public record ToolUpgrades(int reach, int fortune, int efficiency) {
    /** A freshly made tool: 3x3, Fortune III, Efficiency V. */
    public static final ToolUpgrades BASE = new ToolUpgrades(UpgradeTrack.AREA.min(), UpgradeTrack.FORTUNE.min(), UpgradeTrack.EFFICIENCY.min());
    /** Fully upgraded: 13x13, Fortune X, Efficiency X. */
    public static final ToolUpgrades MAX = new ToolUpgrades(UpgradeTrack.AREA.max(), UpgradeTrack.FORTUNE.max(), UpgradeTrack.EFFICIENCY.max());

    public static final Codec<ToolUpgrades> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("reach").forGetter(ToolUpgrades::reach),
            Codec.INT.fieldOf("fortune").forGetter(ToolUpgrades::fortune),
            Codec.INT.fieldOf("efficiency").forGetter(ToolUpgrades::efficiency)
    ).apply(i, ToolUpgrades::new));

    public static final StreamCodec<ByteBuf, ToolUpgrades> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToolUpgrades::reach,
            ByteBufCodecs.VAR_INT, ToolUpgrades::fortune,
            ByteBufCodecs.VAR_INT, ToolUpgrades::efficiency,
            ToolUpgrades::new);

    public ToolUpgrades {
        reach = clamp(reach, UpgradeTrack.AREA);
        fortune = clamp(fortune, UpgradeTrack.FORTUNE);
        efficiency = clamp(efficiency, UpgradeTrack.EFFICIENCY);
    }

    private static int clamp(int v, UpgradeTrack t) {
        return Math.max(t.min(), Math.min(t.max(), v));
    }

    public int level(UpgradeTrack track) {
        return switch (track) {
            case AREA -> reach;
            case FORTUNE -> fortune;
            case EFFICIENCY -> efficiency;
        };
    }

    public ToolUpgrades with(UpgradeTrack track, int level) {
        return switch (track) {
            case AREA -> new ToolUpgrades(level, fortune, efficiency);
            case FORTUNE -> new ToolUpgrades(reach, level, efficiency);
            case EFFICIENCY -> new ToolUpgrades(reach, fortune, level);
        };
    }

    public boolean maxed(UpgradeTrack track) {
        return level(track) >= track.max();
    }

    public boolean maxed() {
        for (UpgradeTrack t : UpgradeTrack.values()) if (!maxed(t)) return false;
        return true;
    }

    /** Blocks along each side of the square it digs. */
    public int size() {
        return 2 * reach + 1;
    }
}
