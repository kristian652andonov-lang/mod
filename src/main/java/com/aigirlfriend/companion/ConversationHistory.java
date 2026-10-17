package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The last few exchanged lines, verbatim, for conversational continuity. */
public final class ConversationHistory {
    public record Line(boolean fromPlayer, String text, long gameTime) {}

    private final Deque<Line> lines = new ArrayDeque<>();

    public void add(boolean fromPlayer, String text, long gameTime) {
        lines.addLast(new Line(fromPlayer, text, gameTime));
        int max = GFConfig.HISTORY_LENGTH.get();
        while (lines.size() > max) lines.removeFirst();
    }

    public List<Line> recent(int n) {
        List<Line> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    /** True if she said (almost) exactly this recently; used to avoid repeating herself. */
    public boolean saidRecently(String text) {
        String norm = normalize(text);
        for (Line l : lines) if (!l.fromPlayer() && normalize(l.text()).equals(norm)) return true;
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z ]", "").trim();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Line l : lines) {
            CompoundTag t = new CompoundTag();
            t.putBoolean("p", l.fromPlayer());
            t.putString("t", l.text());
            t.putLong("g", l.gameTime());
            list.add(t);
        }
        return list;
    }

    public void load(ListTag list) {
        lines.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            lines.addLast(new Line(t.getBoolean("p"), t.getString("t"), t.getLong("g")));
        }
    }

}
