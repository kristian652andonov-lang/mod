package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides her primary mood at the start of each Minecraft day from what actually happened yesterday,
 * her needs, her personality and environment. Each contribution carries a human readable cause so the
 * player can understand (and change) why she feels the way she does.
 */
public final class MoodEvaluator {
    private MoodEvaluator() {}

    private record Contribution(float value, String cause) {}

    private static final class Scores {
        final EnumMap<Mood, Float> total = new EnumMap<>(Mood.class);
        final EnumMap<Mood, List<Contribution>> parts = new EnumMap<>(Mood.class);

        void add(Mood mood, float value, String cause) {
            if (value == 0f) return;
            total.merge(mood, value, Float::sum);
            if (cause != null) parts.computeIfAbsent(mood, k -> new ArrayList<>()).add(new Contribution(value, cause));
        }
    }

    public static Mood evaluateDay(GirlfriendEntity gf, long day) {
        CompanionData d = gf.data();
        DailyLog log = d.yesterday;
        Personality p = d.personality;
        Needs n = d.needs;
        Level level = gf.level();
        RandomSource random = gf.getRandom();
        RelationshipStage stage = d.relationship.stage();
        float sens = GFConfig.MOOD_SENSITIVITY.get().floatValue() * (0.7f + p.sensitivity * 0.6f);
        boolean menstrual = d.cycle.phase(NeedsSystem.cycleClock(level)) == CyclePhase.MENSTRUAL;
        if (menstrual) sens *= 1f + GFConfig.CYCLE_MOOD_SENSITIVITY.get().floatValue();

        Scores s = new Scores();
        Mood prev = d.mood.primary;
        s.add(prev, prev.isNegative() ? 0.3f : 0.55f, null);

        float avgAtt = log.averageAttention();
        int variety = log.affectionVariety();
        float lonely = GFConfig.LONELY_THRESHOLD.get().floatValue();
        float affectionate = GFConfig.AFFECTIONATE_THRESHOLD.get().floatValue();

        // --- positive ---
        s.add(Mood.HAPPY, variety * 0.32f * sens, variety >= 3 ? "We did so many nice things together yesterday" : null);
        s.add(Mood.HAPPY, (avgAtt - 50f) / 50f * sens, avgAtt > 60 ? "You gave me lots of attention" : null);
        s.add(Mood.HAPPY, Math.min(1f, log.get(LogKey.GIFTS_LIKED) * 0.3f) * sens, log.get(LogKey.GIFTS_LIKED) > 0 ? "I still love the gift you gave me" : null);
        s.add(Mood.HAPPY, d.mood.wellbeing / 60f, d.mood.wellbeing > 40 ? "I've been feeling good lately" : null);
        s.add(Mood.HAPPY, Math.min(0.6f, log.get(LogKey.REQUESTS_DONE) * 0.3f), log.get(LogKey.REQUESTS_DONE) > 0 ? "You did what I asked" : null);

        float closeness = Math.min(1.2f, (log.get(LogKey.HUGS) + log.get(LogKey.KISSES) + log.get(LogKey.CUDDLES)) * 0.12f);
        s.add(Mood.AFFECTIONATE, p.affection * 0.7f, null);
        s.add(Mood.AFFECTIONATE, closeness * sens, closeness > 0.3f ? "All those hugs and cuddles" : null);
        s.add(Mood.AFFECTIONATE, stage.isRomantic() ? 0.45f : 0f, null);
        s.add(Mood.AFFECTIONATE, log.get(LogKey.SLEPT_TOGETHER) > 0 ? 0.6f : 0f, log.get(LogKey.SLEPT_TOGETHER) > 0 ? "Falling asleep next to you" : null);
        s.add(Mood.AFFECTIONATE, avgAtt > affectionate ? 0.3f : 0f, null);

        s.add(Mood.PLAYFUL, p.playfulness * 0.9f, null);
        s.add(Mood.PLAYFUL, n.energy > 70 ? 0.35f : -0.3f, n.energy > 70 ? "I'm full of energy" : null);
        s.add(Mood.PLAYFUL, Math.min(0.5f, log.get(LogKey.ADVENTURE_BLOCKS) / 2500f), log.get(LogKey.ADVENTURE_BLOCKS) > 800 ? "Our adventure yesterday was fun" : null);

        s.add(Mood.SHY, p.shyness * 0.75f, null);
        s.add(Mood.SHY, stage.ordinal() <= RelationshipStage.FRIENDSHIP.ordinal() ? 0.35f * p.shyness : 0f, null);

        s.add(Mood.RELAXED, 0.55f, null);
        s.add(Mood.RELAXED, log.get(LogKey.FRIGHTS) == 0 ? 0.3f : 0f, log.get(LogKey.FRIGHTS) == 0 ? "Yesterday was calm and safe" : null);
        s.add(Mood.RELAXED, n.energy > 40 && n.energy < 85 && n.hunger < 50 ? 0.2f : 0f, null);

        s.add(Mood.ENERGETIC, log.get(LogKey.SLEPT) > 0 && n.energy > 75 ? 0.6f : 0f, log.get(LogKey.SLEPT) > 0 && n.energy > 75 ? "I slept really well" : null);
        s.add(Mood.ENERGETIC, Math.min(0.5f, log.get(LogKey.MOBS_DEFEATED) * 0.08f + log.get(LogKey.ADVENTURE_BLOCKS) / 3000f),
                log.get(LogKey.MOBS_DEFEATED) >= 3 ? "We handled those monsters together" : null);
        s.add(Mood.ENERGETIC, p.playfulness * 0.25f + (p.confidence - 0.5f) * 0.3f, null);

        s.add(Mood.INDEPENDENT, p.independence * 0.95f, null);
        s.add(Mood.INDEPENDENT, stage.ordinal() < RelationshipStage.FRIENDSHIP.ordinal() ? 0.25f : 0f, null);
        s.add(Mood.INDEPENDENT, log.get(LogKey.PROTECTED) > 0 && p.confidence > 0.55f ? 0.2f : 0f, null);

        // --- negative ---
        if (avgAtt < lonely) s.add(Mood.LONELY, (lonely - avgAtt) / 14f * sens, "I barely saw you yesterday");
        s.add(Mood.LONELY, Math.min(1.2f, log.get(LogKey.ALONE_SECONDS) / 1800f) * sens, log.get(LogKey.ALONE_SECONDS) > 900 ? "I was alone for a long time" : null);
        s.add(Mood.LONELY, Math.min(1f, log.get(LogKey.IGNORED_SECONDS) / 1500f) * sens, log.get(LogKey.IGNORED_SECONDS) > 700 ? "You were right there but so busy" : null);

        s.add(Mood.SAD, log.get(LogKey.DISAPPOINTMENTS) * 0.35f * sens, log.get(LogKey.DISAPPOINTMENTS) > 0 ? "Something you did yesterday hurt my feelings" : null);
        s.add(Mood.SAD, log.get(LogKey.GIFTS_DISLIKED) * 0.2f * sens, null);
        s.add(Mood.SAD, log.get(LogKey.FAINTED) > 0 ? 0.8f * sens : 0f, log.get(LogKey.FAINTED) > 0 ? "I got badly hurt yesterday" : null);
        s.add(Mood.SAD, d.mood.wellbeing < -30 ? 0.7f : 0f, d.mood.wellbeing < -30 ? "I've been feeling down" : null);
        s.add(Mood.SAD, log.get(LogKey.REQUESTS_FAILED) * 0.2f * sens, null);

        if (n.hunger > 60) s.add(Mood.IRRITABLE, (n.hunger - 60f) / 22f, "I'm so hungry");
        s.add(Mood.IRRITABLE, Math.min(1f, log.get(LogKey.STARVING_SECONDS) / 600f), log.get(LogKey.STARVING_SECONDS) > 300 ? "I went hungry for ages yesterday" : null);
        if (menstrual) s.add(Mood.IRRITABLE, 0.2f * p.sensitivity, "I'm on my period and a bit sensitive");
        s.add(Mood.IRRITABLE, Math.min(0.4f, log.get(LogKey.RAIN_SECONDS) / 4000f) * (1f - p.playfulness), null);

        s.add(Mood.SLEEPY, n.energy < 35 ? (35f - n.energy) / 18f : 0f, n.energy < 35 ? "I didn't get enough rest" : null);
        s.add(Mood.SLEEPY, Math.min(0.8f, log.get(LogKey.EXHAUSTED_SECONDS) / 700f), null);
        if (menstrual) s.add(Mood.SLEEPY, 0.25f * GFConfig.CYCLE_COMFORT_SEEKING.get().floatValue(), "Cramps make me tired");

        float frights = log.get(LogKey.FRIGHTS);
        float bravery = p.bravery();
        s.add(Mood.WORRIED, frights * 0.25f * (1.2f - bravery) * sens, frights > 0 ? "Those monsters yesterday still worry me" : null);
        s.add(Mood.WORRIED, Math.min(0.6f, log.get(LogKey.NIGHT_OUT_SECONDS) / 2000f) * (1.1f - bravery), log.get(LogKey.NIGHT_OUT_SECONDS) > 900 ? "We were out in the dark a lot" : null);
        s.add(Mood.WORRIED, gf.getHealth() < gf.getMaxHealth() * 0.5f ? 0.5f : 0f, gf.getHealth() < gf.getMaxHealth() * 0.5f ? "I'm still hurt" : null);

        s.add(Mood.FRIGHTENED, log.get(LogKey.MAX_FEAR) > 0.8f ? 0.55f * (1.2f - bravery) * sens : 0f, log.get(LogKey.MAX_FEAR) > 0.8f ? "Last night was terrifying" : null);
        s.add(Mood.FRIGHTENED, frights >= 3 ? 0.3f * sens : 0f, null);
        s.add(Mood.FRIGHTENED, log.get(LogKey.PROTECTED) > 0 ? -0.3f : 0f, null);

        s.add(Mood.EMBARRASSED, log.get(LogKey.EMBARRASSING) * 0.3f * p.shyness, log.get(LogKey.EMBARRASSING) > 0 ? "I'm still flustered about yesterday" : null);

        if (stage.atLeast(RelationshipStage.GROWING_AFFECTION) && p.affection > 0.55f && avgAtt < 45f)
            s.add(Mood.NEEDY, 0.45f + (45f - avgAtt) / 60f, "I want more time with you");
        if (menstrual) s.add(Mood.NEEDY, 0.3f * GFConfig.CYCLE_COMFORT_SEEKING.get().floatValue(), "I could really use some comfort today");
        s.add(Mood.NEEDY, log.get(LogKey.HURT) > 0 ? 0.25f : 0f, null);

        // environment
        if (level.isThundering()) s.add(Mood.WORRIED, 0.35f * (1.1f - bravery), "The thunder makes me nervous");
        else if (level.isRaining()) s.add(Mood.RELAXED, p.playfulness < 0.5f ? 0.15f : -0.05f, null);

        // a little natural variety
        for (Mood m : Mood.values()) s.add(m, (random.nextFloat() - 0.5f) * 0.3f, null);

        Mood best = Mood.RELAXED;
        float bestScore = -999f;
        for (Map.Entry<Mood, Float> e : s.total.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        List<String> causes = new ArrayList<>();
        List<Contribution> parts = s.parts.getOrDefault(best, List.of());
        parts.stream().filter(c -> c.value() > 0.05f).sorted((a, b) -> Float.compare(b.value(), a.value())).limit(3).forEach(c -> causes.add(c.cause()));
        if (causes.isEmpty()) causes.add(defaultCause(best, p));
        d.mood.setPrimary(best, causes, day);
        d.mood.wellbeing = d.mood.wellbeing * 0.5f + best.valence * 30f;
        return best;
    }

    private static String defaultCause(Mood m, Personality p) {
        return switch (m) {
            case HAPPY -> "It just feels like a good day";
            case AFFECTIONATE -> "I just really like being with you";
            case PLAYFUL -> "I'm in the mood for some fun";
            case SHY -> "I feel a little shy today";
            case RELAXED -> "Nothing to worry about today";
            case SLEEPY -> "I feel a bit sleepy";
            case INDEPENDENT -> "I feel like doing my own thing";
            case ENERGETIC -> "I woke up with lots of energy";
            default -> p.sensitivity > 0.6f ? "I'm not sure why, I just feel this way" : "Just one of those days";
        };
    }

    /**
     * Mid-day: if she started the day in a negative mood but has clearly been cheered up (or the reverse after
     * something bad), let the primary mood change. Rate-limited so it never flickers.
     */
    public static boolean reevaluate(GirlfriendEntity gf, long now, long day) {
        CompanionData d = gf.data();
        MoodState m = d.mood;
        if (!GFConfig.MOOD_RECOVERY.get() || now - m.lastReevaluationTick < 2400) return false;
        if (m.primary.isNegative() && m.wellbeing > 35f) {
            Mood next = d.today.affectionVariety() >= 2 && d.personality.affection > 0.5f ? Mood.AFFECTIONATE
                    : d.needs.energy > 70 ? Mood.ENERGETIC : Mood.HAPPY;
            m.lastReevaluationTick = now;
            m.setPrimary(next, List.of("You cheered me up"), day);
            return true;
        }
        if (!m.primary.isNegative() && m.wellbeing < -55f) {
            Mood next;
            if (d.today.get(LogKey.FRIGHTS) >= 2) next = Mood.WORRIED;
            else if (d.needs.attention < GFConfig.LONELY_THRESHOLD.get()) next = Mood.LONELY;
            else if (d.needs.isHungry()) next = Mood.IRRITABLE;
            else next = Mood.SAD;
            m.lastReevaluationTick = now;
            m.setPrimary(next, List.of("Today hasn't gone well"), day);
            return true;
        }
        return false;
    }

    /** Face to show right now. */
    public static Expression expression(GirlfriendEntity gf, long now, float fear) {
        CompanionData d = gf.data();
        if (gf.isFainted()) return Expression.SLEEPY;
        if (gf.isSleeping()) return Expression.SLEEPY;
        if (fear > 0.45f) return Expression.FEAR;
        Emotion e = d.mood.activeEmotion(now);
        if (e != Emotion.NONE && d.mood.secondaryIntensity > 0.25f && e.expression != null) return e.expression;
        if (d.needs.isExhausted()) return Expression.SLEEPY;
        if (d.needs.isStarving()) return Expression.SAD;
        return d.mood.primary.expression;
    }
}
