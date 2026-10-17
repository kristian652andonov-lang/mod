package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Her tastes: how much she likes each gift category, one favourite, one dislike, and what she has received. */
public final class Preferences {
    private final EnumMap<GiftCategory, Float> weights = new EnumMap<>(GiftCategory.class);
    public GiftCategory favorite = GiftCategory.FLOWERS;
    public GiftCategory disliked = GiftCategory.ADVENTURE;
    /** Items given recently -> times, bounded. Used for novelty. */
    private final LinkedHashMap<String, Integer> giftHistory = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 48;
        }
    };
    public int revealedMask = 0;
    public String favoriteActivity = "walks";

    public static Preferences generate(RandomSource random, Personality p) {
        Preferences prefs = new Preferences();
        for (GiftCategory c : GiftCategory.values()) prefs.weights.put(c, 0.6f + random.nextFloat() * 0.6f);
        // personality nudges taste
        prefs.weights.merge(GiftCategory.CUTE, p.shyness * 0.3f, Float::sum);
        prefs.weights.merge(GiftCategory.ADVENTURE, p.confidence * 0.4f, Float::sum);
        prefs.weights.merge(GiftCategory.SWEETS, p.playfulness * 0.3f, Float::sum);
        prefs.weights.merge(GiftCategory.FLOWERS, p.affection * 0.3f, Float::sum);
        GiftCategory[] all = GiftCategory.values();
        prefs.favorite = all[random.nextInt(all.length)];
        if (prefs.favorite == GiftCategory.FOOD) prefs.favorite = GiftCategory.SWEETS;
        do {
            prefs.disliked = all[random.nextInt(all.length)];
        } while (prefs.disliked == prefs.favorite || prefs.disliked == GiftCategory.FOOD || prefs.disliked == GiftCategory.FLOWERS);
        prefs.weights.put(prefs.favorite, 1.8f);
        prefs.weights.put(prefs.disliked, 0.3f);
        String[] activities = {"walks", "stargazing", "exploring caves", "watching the sunset", "sitting by the water", "picking flowers", "cuddling by the fire", "building things"};
        prefs.favoriteActivity = activities[random.nextInt(activities.length)];
        return prefs;
    }

    public float weight(GiftCategory c) {
        return weights.getOrDefault(c, 1f);
    }

    /** 0..1+ novelty: the same item over and over is less exciting. */
    public float novelty(String itemId) {
        int n = giftHistory.getOrDefault(itemId, 0);
        return Mth.clamp(1.2f - n * 0.25f, 0.3f, 1.2f);
    }

    public void recordGift(String itemId) {
        giftHistory.merge(itemId, 1, Integer::sum);
    }

    /** Called daily: old gifts become exciting again. */
    public void decayHistory() {
        giftHistory.replaceAll((k, v) -> Math.max(0, v - 1));
        giftHistory.values().removeIf(v -> v <= 0);
    }

    public boolean isRevealed(GiftCategory c) {
        return (revealedMask & (1 << c.ordinal())) != 0;
    }

    public void reveal(GiftCategory c) {
        revealedMask |= 1 << c.ordinal();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        CompoundTag w = new CompoundTag();
        weights.forEach((k, v) -> w.putFloat(k.name(), v));
        t.put("weights", w);
        t.putString("favorite", favorite.name());
        t.putString("disliked", disliked.name());
        CompoundTag h = new CompoundTag();
        giftHistory.forEach(h::putInt);
        t.put("history", h);
        t.putInt("revealed", revealedMask);
        t.putString("favoriteActivity", favoriteActivity);
        return t;
    }

    public void load(CompoundTag t) {
        weights.clear();
        CompoundTag w = t.getCompound("weights");
        for (GiftCategory c : GiftCategory.values()) weights.put(c, w.contains(c.name()) ? w.getFloat(c.name()) : 1f);
        try { favorite = GiftCategory.valueOf(t.getString("favorite")); } catch (Exception ignored) {}
        try { disliked = GiftCategory.valueOf(t.getString("disliked")); } catch (Exception ignored) {}
        giftHistory.clear();
        CompoundTag h = t.getCompound("history");
        for (String k : h.getAllKeys()) giftHistory.put(k, h.getInt(k));
        revealedMask = t.getInt("revealed");
        if (t.contains("favoriteActivity")) favoriteActivity = t.getString("favoriteActivity");
    }
}
