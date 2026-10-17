package com.aigirlfriend.companion;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optional requests. At most one is active, a few per day at most, never while in danger,
 * and declining or ignoring them only causes a proportionate, recoverable reaction.
 * Once the relationship is romantic she no longer asks for affection; she simply gives it.
 */
public final class RequestSystem {
    private RequestSystem() {}

    public static void tick(GirlfriendEntity gf) {
        CompanionData d = gf.data();
        Level level = gf.level();
        long now = level.getGameTime();
        CompanionRequest r = d.request;
        if (r != null) {
            checkProgress(gf, r);
            if (r.isDone()) {
                complete(gf, r);
            } else if (now >= r.expiresTick || (r.type == RequestType.HOME_BEFORE_NIGHT && WorldClock.isNight(level))) {
                fail(gf, r);
            }
            return;
        }
        if (!GFConfig.REQUESTS_ENABLED.get()) return;
        long day = WorldClock.day(level);
        if (d.requestDay != day) {
            d.requestDay = day;
            d.requestsToday = 0;
        }
        if (d.requestsToday >= GFConfig.REQUESTS_PER_DAY.get()) return;
        if (now - d.lastRequestTick < GFConfig.REQUEST_GAP_TICKS.get()) return;
        Player owner = gf.getOwner();
        if (owner == null || owner.distanceTo(gf) > 12 || gf.controller().fear.uneasy() || gf.isSleeping() || gf.interactions().isActive()) return;
        if (gf.getRandom().nextInt(300) != 0) return;
        RequestType type = choose(gf, owner);
        if (type != null) issue(gf, type, owner);
    }

    @Nullable
    private static RequestType choose(GirlfriendEntity gf, Player owner) {
        CompanionData d = gf.data();
        Level level = gf.level();
        boolean romantic = d.relationship.stage().isRomantic();
        boolean night = WorldClock.isNight(level);
        boolean outside = level.canSeeSky(gf.blockPosition());
        long tod = WorldClock.timeOfDay(level);
        CyclePhase phase = d.cycle.phase(NeedsSystem.cycleClock(level));
        List<RequestType> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        java.util.function.BiConsumer<RequestType, Float> add = (t, w) -> {
            if (w > 0) {
                pool.add(t);
                weights.add(w);
            }
        };
        add.accept(RequestType.BRING_FOOD, d.needs.hunger > 45 ? 3f : 0f);
        add.accept(RequestType.REACH_SAFETY, night && outside && d.today.get(LogKey.FRIGHTS) > 0 ? 2.5f : 0f);
        add.accept(RequestType.HOME_BEFORE_NIGHT, d.home != null && tod > 8500 && tod < 11500 && !gf.controller().isNearHome(48) && gf.controller().isHomeDimension() ? 2.5f : 0f);
        add.accept(RequestType.EXPLORE_TOGETHER, !night && d.needs.energy > 55 ? 0.6f + d.personality.playfulness : 0f);
        add.accept(RequestType.TALK_TOGETHER, d.needs.attention < 50 && d.today.get(LogKey.TALKS) < 2 ? 1.5f : 0f);
        add.accept(RequestType.PREFERRED_GIFT, d.relationship.stage().atLeast(RelationshipStage.FRIENDSHIP) ? 0.8f : 0f);
        add.accept(RequestType.PROTECT_ME, night && outside ? 0.8f * (1.2f - d.personality.bravery()) : 0f);
        float comfort = phase == CyclePhase.MENSTRUAL ? GFConfig.CYCLE_COMFORT_SEEKING.get().floatValue() : 0f;
        add.accept(RequestType.SIT_BY_FIRE, (night || comfort > 0 || isCold(gf)) ? 0.6f + comfort : 0f);
        add.accept(RequestType.COMFORT_ME, !romantic && d.relationship.stage().atLeast(RelationshipStage.FRIENDSHIP)
                && (comfort > 0 || d.mood.primary.isNegative()) ? 1.2f + comfort : 0f);
        if (pool.isEmpty()) return null;
        float total = 0;
        for (float w : weights) total += w;
        float pick = gf.getRandom().nextFloat() * total;
        for (int i = 0; i < pool.size(); i++) {
            pick -= weights.get(i);
            if (pick <= 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private static boolean isCold(GirlfriendEntity gf) {
        return gf.level().getBiome(gf.blockPosition()).value().coldEnoughToSnow(gf.blockPosition());
    }

    public static void issue(GirlfriendEntity gf, RequestType type, Player owner) {
        CompanionData d = gf.data();
        long now = gf.level().getGameTime();
        CompanionRequest r = new CompanionRequest(type, now, now + GFConfig.REQUEST_EXPIRY_TICKS.get());
        switch (type) {
            case EXPLORE_TOGETHER -> {
                r.x = gf.getX();
                r.z = gf.getZ();
            }
            case PREFERRED_GIFT -> r.detail = d.preferences.favorite.name();
            default -> {}
        }
        d.request = r;
        d.lastRequestTick = now;
        d.requestsToday++;
        String extra = type == RequestType.PREFERRED_GIFT ? d.preferences.favorite.word() : "";
        gf.controller().say(DialogueTrigger.REQUEST_ISSUED, Map.of("request", type.name(), "category", extra));
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("aigirlfriend.message.request", d.name,
                    net.minecraft.network.chat.Component.translatable(type.key(), extra)), true);
        }
    }

    public static void respond(GirlfriendEntity gf, ServerPlayer player, boolean accept) {
        CompanionData d = gf.data();
        CompanionRequest r = d.request;
        if (r == null) return;
        gf.controller().markInteraction();
        if (accept) {
            r.accepted = true;
            d.needs.addAttention(3f);
            d.mood.shiftWellbeing(2f);
            gf.controller().say(DialogueTrigger.REQUEST_ACCEPTED, Map.of("request", r.type.name()));
        } else {
            d.request = null;
            d.today.add(LogKey.DECLINED, 1);
            d.mood.shiftWellbeing(-1.5f * (0.4f + d.personality.sensitivity));
            gf.controller().say(DialogueTrigger.REQUEST_DECLINED, Map.of("request", r.type.name()));
        }
    }

    public static void progress(GirlfriendEntity gf, RequestType type, float amount, @Nullable String detail) {
        CompanionRequest r = gf.data().request;
        if (r == null || r.type != type || amount <= 0) return;
        r.progress += amount;
    }

    public static void onCommand(GirlfriendEntity gf, CommandMode mode) {
        CompanionRequest r = gf.data().request;
        if (r != null && r.type == RequestType.HOME_BEFORE_NIGHT && mode == CommandMode.HOME) r.accepted = true;
    }

    private static void checkProgress(GirlfriendEntity gf, CompanionRequest r) {
        Level level = gf.level();
        Player owner = gf.getOwner();
        switch (r.type) {
            case EXPLORE_TOGETHER -> {
                if (owner != null && owner.distanceTo(gf) < 12) {
                    double dist = Math.sqrt(Math.pow(gf.getX() - r.x, 2) + Math.pow(gf.getZ() - r.z, 2));
                    r.progress = Math.max(r.progress, (float) dist);
                }
            }
            case REACH_SAFETY -> {
                BlockPos p = gf.blockPosition();
                boolean sheltered = !level.canSeeSky(p) && level.getBrightness(LightLayer.BLOCK, p) >= 8;
                if (sheltered || gf.controller().isNearHome(10)) r.progress = r.type.target;
            }
            case HOME_BEFORE_NIGHT -> {
                if (gf.controller().isNearHome(10)) r.progress = r.type.target;
            }
            case SIT_BY_FIRE -> {
                if (gf.isSeated() && owner != null && owner.distanceTo(gf) < 4 && nearWarmth(gf)) r.progress += 1f;
            }
            default -> {}
        }
    }

    private static boolean nearWarmth(GirlfriendEntity gf) {
        BlockPos c = gf.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            BlockState s = gf.level().getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) return true;
            if (s.is(BlockTags.FIRE)) return true;
            if (s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT)) return true;
        }
        return false;
    }

    private static void complete(GirlfriendEntity gf, CompanionRequest r) {
        CompanionData d = gf.data();
        d.request = null;
        d.today.add(LogKey.REQUESTS_DONE, 1);
        float mult = r.accepted ? 1.2f : 1f;
        gf.controller().gain("request_" + r.type.name().toLowerCase(), r.type.affectionReward * mult, r.type.trustReward * mult);
        d.needs.addAttention(8f);
        d.mood.shiftWellbeing(8f);
        d.lastInteractionTick = gf.level().getGameTime();
        if (r.accepted) d.today.add(LogKey.PROMISES_KEPT, 1);
        String what = switch (r.type) {
            case BRING_FOOD -> "you brought me food when I was hungry";
            case EXPLORE_TOGETHER -> "we went exploring together";
            case REACH_SAFETY -> "you took me somewhere safe";
            case TALK_TOGETHER -> "we had a long talk";
            case PREFERRED_GIFT -> "you remembered what I like";
            case HOME_BEFORE_NIGHT -> "you brought us home before dark";
            case PROTECT_ME -> "you kept me safe all night";
            case SIT_BY_FIRE -> "we sat together by the fire";
            case COMFORT_ME -> "you comforted me when I needed it";
        };
        com.aigirlfriend.companion.MemoryType type = r.type == RequestType.EXPLORE_TOGETHER ? MemoryType.ADVENTURE
                : r.type == RequestType.SIT_BY_FIRE ? MemoryType.FAVORITE_ACTIVITY : MemoryType.FAVORITE_ACTIVITY;
        d.memory.remember(type, what, "request_" + r.type.name(), WorldClock.day(gf.level()), gf.level().getGameTime(), 0.7f);
        gf.controller().feel(Emotion.JOY, 0.85f, 600);
        gf.controller().heartParticles(6);
        gf.playGesture(com.aigirlfriend.interaction.Gesture.CELEBRATE);
        gf.controller().say(DialogueTrigger.REQUEST_DONE, Map.of("request", r.type.name()));
    }

    private static void fail(GirlfriendEntity gf, CompanionRequest r) {
        CompanionData d = gf.data();
        d.request = null;
        if (r.accepted) {
            d.today.add(LogKey.REQUESTS_FAILED, 1);
            d.today.add(LogKey.DISAPPOINTMENTS, 0.5f);
            gf.controller().lose(3f, 1.5f);
            d.mood.shiftWellbeing(-4f * (0.5f + d.personality.sensitivity));
            d.memory.remember(MemoryType.DISAPPOINTMENT, "you said you'd help but didn't", "request_failed_" + r.type.name(), WorldClock.day(gf.level()), gf.level().getGameTime(), 0.5f);
            gf.controller().say(DialogueTrigger.REQUEST_FAILED, Map.of("request", r.type.name(), "accepted", "1"));
        } else {
            d.mood.shiftWellbeing(-1f);
        }
    }
}
