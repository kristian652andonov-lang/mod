package com.fantasyweapons.advancement;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.network.DeedPayload;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The mod's advancements are its deeds, in a tab of their own. They keep quiet in vanilla's way (no vanilla toast, no
 * vanilla chat line) and are announced in the mod's: the earner gets the deed's own toast and fanfare, and everyone reads
 * a line in the deed's colours - a deed in parchment gold, a feat in arcane blue, a legend in royal violet.
 */
public final class Deeds {
    /** A deed's (task's), a feat's (goal's) and a legend's (challenge's) colour. */
    public static final int DEED = 0xE8C46A, FEAT = 0x6FD8F0, LEGEND = 0xC98CFF;
    private static final int PLAIN = 0xCFC3A6;

    private Deeds() {
    }

    public static boolean ours(AdvancementHolder holder) {
        return holder.id().getNamespace().equals(FantasyWeapons.MOD_ID) && !holder.id().getPath().startsWith("recipes/");
    }

    /** The font of the ranks' emblems: a gilded four-pointed star, a blue compass rose and a violet star with a golden heart. */
    public static final net.minecraft.resources.ResourceLocation MARKS = FantasyWeapons.id("deeds");

    /** The emblem of {@code type}'s rank, a glyph in its own colours. */
    public static MutableComponent mark(AdvancementType type) {
        char glyph = switch (type) {
            case TASK -> '\uE000';
            case GOAL -> '\uE001';
            case CHALLENGE -> '\uE002';
        };
        return Component.literal(String.valueOf(glyph)).withStyle(s -> s.withFont(MARKS).withColor(0xFFFFFF));
    }

    public static int color(AdvancementType type) {
        return switch (type) {
            case TASK -> DEED;
            case GOAL -> FEAT;
            case CHALLENGE -> LEGEND;
        };
    }

    /** What a deed of {@code type} is called: Deed, Feat or Legend. */
    public static String rank(AdvancementType type) {
        return switch (type) {
            case TASK -> "Deed";
            case GOAL -> "Feat";
            case CHALLENGE -> "Legend";
        };
    }

    private static String verb(AdvancementType type) {
        return switch (type) {
            case TASK -> " has earned the deed ";
            case GOAL -> " has accomplished the feat ";
            case CHALLENGE -> " has become a legend: ";
        };
    }

    public static void onEarned(AdvancementEvent.AdvancementEarnEvent event) {
        AdvancementHolder holder = event.getAdvancement();
        if (!ours(holder) || holder.value().parent().isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) return;
        DisplayInfo display = holder.value().display().orElse(null);
        if (display == null) return;
        PacketDistributor.sendToPlayer(player, new DeedPayload(holder.id(), display.getTitle(), display.getDescription(), display.getIcon(),
                display.getType().ordinal()));
        if (player.serverLevel().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
            player.server.getPlayerList().broadcastSystemMessage(announcement(player, display), false);
        }
    }

    /** "(emblem) Steve has accomplished the feat [Legend Reborn]", the title in its colour and its description on hover. */
    public static MutableComponent announcement(ServerPlayer player, DisplayInfo display) {
        int color = color(display.getType());
        Component hover = Component.empty().append(display.getTitle()).append("\n").append(mark(display.getType())).append(" ")
                .append(Component.literal(rank(display.getType())).withColor(color)).append("\n").append(display.getDescription());
        MutableComponent title = ComponentUtils.wrapInSquareBrackets(display.getTitle().copy())
                .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        return Component.empty()
                .append(mark(display.getType())).append(" ")
                .append(player.getDisplayName())
                .append(Component.literal(verb(display.getType())).withColor(PLAIN))
                .append(title);
    }
}
