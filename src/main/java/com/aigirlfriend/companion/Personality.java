package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Stable character traits (0..1). They colour every system but never change wildly. */
public final class Personality {
    public float affection = 0.7f;
    public float shyness = 0.5f;
    public float playfulness = 0.6f;
    public float independence = 0.4f;
    public float confidence = 0.45f;
    public float sensitivity = 0.6f;

    public static Personality generate(RandomSource random) {
        Personality p = new Personality();
        float v = GFConfig.TRAIT_RANDOMNESS.get().floatValue();
        p.affection = vary(GFConfig.TRAIT_AFFECTION.get(), v, random);
        p.shyness = vary(GFConfig.TRAIT_SHYNESS.get(), v, random);
        p.playfulness = vary(GFConfig.TRAIT_PLAYFULNESS.get(), v, random);
        p.independence = vary(GFConfig.TRAIT_INDEPENDENCE.get(), v, random);
        p.confidence = vary(GFConfig.TRAIT_CONFIDENCE.get(), v, random);
        p.sensitivity = vary(GFConfig.TRAIT_SENSITIVITY.get(), v, random);
        return p;
    }

    private static float vary(double base, float variation, RandomSource random) {
        return Mth.clamp((float) base + (random.nextFloat() * 2f - 1f) * variation, 0.02f, 0.98f);
    }

    /** How well she copes with danger. */
    public float bravery() {
        return Mth.clamp(confidence * 0.7f + (1f - sensitivity) * 0.3f, 0f, 1f);
    }

    /** The trait that stands out the most, used for her "voice". */
    public String archetype() {
        float best = affection;
        String id = "sweet";
        if (shyness > best) { best = shyness; id = "shy"; }
        if (playfulness > best) { best = playfulness; id = "playful"; }
        if (independence > best) { best = independence; id = "independent"; }
        if (confidence > best) { best = confidence; id = "bold"; }
        if (sensitivity > best + 0.05f) { id = "gentle"; }
        return id;
    }

    public String describe() {
        return String.format("affection %.0f%%, shyness %.0f%%, playfulness %.0f%%, independence %.0f%%, confidence %.0f%%, sensitivity %.0f%% (overall %s)",
                affection * 100, shyness * 100, playfulness * 100, independence * 100, confidence * 100, sensitivity * 100, archetype());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("affection", affection);
        t.putFloat("shyness", shyness);
        t.putFloat("playfulness", playfulness);
        t.putFloat("independence", independence);
        t.putFloat("confidence", confidence);
        t.putFloat("sensitivity", sensitivity);
        return t;
    }

    public void load(CompoundTag t) {
        affection = t.getFloat("affection");
        shyness = t.getFloat("shyness");
        playfulness = t.getFloat("playfulness");
        independence = t.getFloat("independence");
        confidence = t.getFloat("confidence");
        sensitivity = t.getFloat("sensitivity");
    }
}
