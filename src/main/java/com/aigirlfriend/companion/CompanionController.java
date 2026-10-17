package com.aigirlfriend.companion;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueService;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.fear.FearState;
import com.aigirlfriend.fear.ThreatScanner;
import com.aigirlfriend.interaction.Gesture;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionMenu;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.network.StatusSnapshot;
import com.aigirlfriend.network.payload.StatusPayload;
import com.aigirlfriend.registry.ModItems;
import com.aigirlfriend.seat.SeatManager;
import com.aigirlfriend.util.WorldClock;
import com.aigirlfriend.world.CompanionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side brain of one companion. Runs scheduled updates (needs every second, threats every
 * half second, mood once per day) instead of recalculating everything every tick, and turns gameplay
 * events (gifts, protection, neglect, sleep...) into consistent changes of relationship, mood, memory and dialogue.
 */
public final class CompanionController {
    private static final ResourceLocation TIRED_SPEED = ResourceLocation.fromNamespaceAndPath("aigirlfriend", "tired_slowdown");
    /** Preference weight of hand-made romantic gifts (same as her favourite category). */
    private static final float SPECIAL_GIFT_WEIGHT = 1.8f;
    private static final int PROPOSAL_SURPRISE_TICKS = 30;

    private final GirlfriendEntity gf;
    public final FearState fear = new FearState();
    public boolean sleepingShared;
    private final EnumMap<DialogueTrigger, Long> lastSaid = new EnumMap<>(DialogueTrigger.class);
    public long lastSpokeTick = -100000L;
    public long lastInitiativeTick;
    public long reachHandUntil;
    public long ownerStillSince;
    public long lastBedtimeLeadDay = -1;
    public long lastHurtTick = -100000L;
    private long lastDefenseTick = -100000L;
    private long lastRescueTick = -100000L;
    private int ticks;
    private int faintTicks;
    private boolean firstTick = true;
    private boolean ownerNear;
    private boolean wasCritical;
    @Nullable private Vec3 lastOwnerPos;
    private float activityLevel = 0.2f;
    private long pendingMorningLine = -1;
    private long pendingWakeAffection = -1;
    /** Countdown from her surprised reaction to the celebration after she accepted a proposal. */
    private int proposalTicks;
    /** Set when the owner picks "sleep together"; the lead-to-bed goal picks it up. */
    public boolean bedtimeRequested;
    /** A bed she is lying on, waiting for the owner to join. */
    @Nullable public BlockPos waitingBed;
    public long waitingBedSince;

    public CompanionController(GirlfriendEntity gf) {
        this.gf = gf;
    }

    private long now() {
        return gf.level().getGameTime();
    }

    private long day() {
        return WorldClock.day(gf.level());
    }

    public CompanionData data() {
        return gf.data();
    }

    // ================================================================== ticking

    public void tick() {
        ticks++;
        if (firstTick) {
            firstTick = false;
            onFirstTick();
        }
        if (faintTicks > 0) {
            tickFaint();
            return;
        }
        if (wakeTicks > 0 && --wakeTicks == 0) finishWakeUp();
        if (proposalTicks > 0 && --proposalTicks == 0) celebrateProposal();
        if (ticks % 10 == 0) threatTick();
        if (ticks % 5 == 0) gf.setExpression(currentExpression());
        if (ticks % 20 == 0) secondTick();
        if (ticks % 400 == 0 && gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, ticks % 2400 == 0);
        if (pendingMorningLine > 0 && now() >= pendingMorningLine) {
            pendingMorningLine = -1;
            if (ownerNear) say(DialogueTrigger.MORNING);
        }
        if (pendingWakeAffection > 0 && now() >= pendingWakeAffection) {
            pendingWakeAffection = -1;
            Player owner = gf.getOwner();
            if (owner instanceof ServerPlayer sp && data().relationship.stage().isRomantic() && owner.distanceTo(gf) < 4) {
                gf.interactions().start(gf.getRandom().nextBoolean() ? InteractionType.KISS_CHEEK : InteractionType.HUG, sp, true);
            } else {
                gf.playGesture(Gesture.WAKE_GREETING);
            }
        }
    }

    private void onFirstTick() {
        long day = day();
        CompanionData d = data();
        if (d.today.day < 0) d.today.reset(day);
        if (d.mood.lastEvaluatedDay == Long.MIN_VALUE) d.mood.lastEvaluatedDay = day;
        d.cycle.ensure(NeedsSystem.cycleClock(gf.level()), GFConfig.CYCLE_CLOCK.get().ordinal(), gf.getRandom());
        d.relationship.refreshStage();
        gf.setSyncedStage(d.relationship.stage());
        gf.setSyncedMood(d.mood.primary);
        gf.applyConfigAttributes();
        if (gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, true);
    }

    private void secondTick() {
        CompanionData d = data();
        long now = now();
        long day = day();

        // --- day rollover: decide the new primary mood ---
        if (d.today.day != day) {
            d.yesterday.copyFrom(d.today);
            d.today.reset(day);
            Mood mood = MoodEvaluator.evaluateDay(gf, day);
            d.mood.lastEvaluatedDay = day;
            d.preferences.decayHistory();
            d.requestsToday = 0;
            gf.setSyncedMood(mood);
            pendingMorningLine = now + 100 + gf.getRandom().nextInt(400);
            expireOldPromises(day);
        } else if (MoodEvaluator.reevaluate(gf, now, day)) {
            gf.setSyncedMood(d.mood.primary);
            if (ownerNear) say(d.mood.primary.isNegative() ? DialogueTrigger.LONELY : DialogueTrigger.HAPPY);
        }

        // --- needs ---
        activityLevel = computeActivityLevel();
        NeedsSystem.update(gf, gf.isSleeping(), activityLevel);
        d.cycle.ensure(NeedsSystem.cycleClock(gf.level()), GFConfig.CYCLE_CLOCK.get().ordinal(), gf.getRandom());
        Needs n = d.needs;
        if (n.isStarving()) d.today.add(LogKey.STARVING_SECONDS, 1);
        if (n.isExhausted()) d.today.add(LogKey.EXHAUSTED_SECONDS, 1);
        d.today.add(LogKey.ATTENTION_SUM, n.attention);
        d.today.add(LogKey.ATTENTION_SAMPLES, 1);
        if (gf.level().isRaining() && gf.level().canSeeSky(gf.blockPosition())) d.today.add(LogKey.RAIN_SECONDS, 1);
        if (WorldClock.isNight(gf.level()) && gf.level().canSeeSky(gf.blockPosition())) d.today.add(LogKey.NIGHT_OUT_SECONDS, 1);

        updateOwnerPresence(now);
        tickOwnSleep();
        updateHealth(now);
        updateFlags();
        updateActivity();
        RequestSystem.tick(gf);
        if (!gf.isSleeping() && !gf.interactions().isActive()) ambientDialogue(now);

        // HUD for the owner
        if (ticks % 40 == 0) {
            ServerPlayer owner = gf.getOwnerAnywhere();
            if (owner != null && owner.level() == gf.level() && owner.distanceToSqr(gf) < 48 * 48) {
                com.aigirlfriend.network.Net.send(owner, new StatusPayload(gf.getId(), StatusSnapshot.of(gf, owner, false)));
            }
        }
    }

    private float computeActivityLevel() {
        if (gf.isSleeping() || gf.isFainted()) return 0f;
        if (gf.isSeated() || gf.isCarried() || gf.isOnLap()) return 0.05f;
        double speed = gf.getDeltaMovement().horizontalDistance();
        if (fear.afraid() || speed > 0.2) return 1f;
        if (speed > 0.05) return 0.6f;
        return 0.2f;
    }

    private void updateOwnerPresence(long now) {
        CompanionData d = data();
        Player owner = gf.getOwner();
        boolean near = owner != null && owner.distanceToSqr(gf) < 12 * 12;
        if (near) {
            long away = now - d.lastOwnerNearTick;
            if (!ownerNear && d.lastOwnerNearTick > 0 && away > 6000) onOwnerReturned(owner, away);
            d.lastOwnerNearTick = now;
            if (d.needs.attention < GFConfig.PROXIMITY_ATTENTION_CAP.get()) d.needs.addAttention(0.035f);
            d.today.add(LogKey.NEAR_OWNER_SECONDS, 1);
            if (now - d.lastInteractionTick > 6000) d.today.add(LogKey.IGNORED_SECONDS, 1);
            if (lastOwnerPos != null && gf.getCommand() == CommandMode.FOLLOW) {
                double moved = Math.sqrt(Math.pow(owner.getX() - lastOwnerPos.x, 2) + Math.pow(owner.getZ() - lastOwnerPos.z, 2));
                if (moved > 0.3 && moved < 14) d.today.add(LogKey.ADVENTURE_BLOCKS, (float) moved);
            }
            if (lastOwnerPos != null && owner.position().distanceToSqr(lastOwnerPos) > 0.04) ownerStillSince = now;
            lastOwnerPos = owner.position();
        } else {
            d.today.add(LogKey.ALONE_SECONDS, 1);
            if (ownerNear && owner != null && gf.getCommand() != CommandMode.FOLLOW && gf.getCommand() != CommandMode.STAY_SAFE) {
                gf.playGesture(Gesture.WAVE_GOODBYE);
                say(DialogueTrigger.WAVE_GOODBYE);
            }
            lastOwnerPos = null;
            ownerStillSince = now;
        }
        ownerNear = near;
    }

    public boolean isOwnerNear() {
        return ownerNear;
    }

    private void onOwnerReturned(Player owner, long awayTicks) {
        CompanionData d = data();
        d.needs.addAttention(8f);
        d.mood.shiftWellbeing(5f);
        feel(Emotion.JOY, 0.8f, 600);
        gf.playGesture(Gesture.WAVE);
        String away = awayTicks > 48000 ? "days" : awayTicks > 24000 ? "a whole day" : awayTicks > 12000 ? "ages" : "a while";
        say(DialogueTrigger.RETURN_AFTER_AWAY, Map.of("away", away));
        if (d.relationship.stage().isRomantic() && owner instanceof ServerPlayer sp && GFConfig.ROMANTIC_INITIATIVE.get()) {
            lastInitiativeTick = now() - (long) GFConfig.INITIATIVE_MIN_SECONDS.get() * 20L + 60;
            gf.controller().reachHandUntil = 0;
            if (owner.distanceTo(gf) < 4) gf.interactions().start(InteractionType.HUG, sp, true);
        }
    }

    private void updateHealth(long now) {
        float max = gf.getMaxHealth();
        boolean critical = gf.getHealth() < max * GFConfig.CRITICAL_HEALTH.get();
        if (critical && !wasCritical) {
            gf.playGesture(Gesture.ASK_HELP);
            say(DialogueTrigger.CRITICAL);
            feel(Emotion.FEAR, 0.9f, 400);
        }
        wasCritical = critical;
        int regenEvery = GFConfig.REGEN_SECONDS.get();
        if (gf.getHealth() < max && now - lastHurtTick > 200 && !data().needs.isStarving() && (ticks / 20) % regenEvery == 0) {
            gf.heal(gf.isSleeping() ? 2f : 1f);
        }
        AttributeInstance speed = gf.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean slow = data().needs.isExhausted() || critical;
            if (slow && !speed.hasModifier(TIRED_SPEED)) {
                speed.addTransientModifier(new AttributeModifier(TIRED_SPEED, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!slow && speed.hasModifier(TIRED_SPEED)) {
                speed.removeModifier(TIRED_SPEED);
            }
        }
    }

    private void updateFlags() {
        Needs n = data().needs;
        gf.setFlag(GirlfriendEntity.FLAG_TIRED, n.isTired());
        gf.setFlag(GirlfriendEntity.FLAG_EXHAUSTED, n.isExhausted());
        gf.setFlag(GirlfriendEntity.FLAG_HUNGRY, n.isHungry());
        gf.setFlag(GirlfriendEntity.FLAG_CRITICAL, wasCritical);
        boolean sad = data().mood.primary == Mood.SAD || data().mood.primary == Mood.LONELY
                || (n.attention < GFConfig.LONELY_THRESHOLD.get() && ownerNear && now() - data().lastInteractionTick > 6000);
        gf.setFlag(GirlfriendEntity.FLAG_SAD, sad);
        gf.setFlag(GirlfriendEntity.FLAG_RUNNING, fear.afraid() && gf.getDeltaMovement().horizontalDistance() > 0.12);
    }

    private void updateActivity() {
        Activity a;
        if (gf.isFainted()) a = Activity.FAINTED;
        else if (gf.isCarried()) a = Activity.CARRIED;
        else if (gf.isSleeping()) a = Activity.SLEEPING;
        else if (gf.interactions().isActive()) a = gf.interactions().current() == InteractionType.LEAD_TO_BED ? Activity.LEADING_TO_BED : Activity.INTERACTING;
        else if (fear.panic() || (fear.afraid() && gf.hasFlag(GirlfriendEntity.FLAG_RUNNING))) a = Activity.FLEEING;
        else if (gf.hasFlag(GirlfriendEntity.FLAG_HIDING)) a = Activity.HIDING;
        else if (gf.isPlayingGesture() && (gf.getGesture() == Gesture.EAT || gf.getGesture() == Gesture.DRINK)) a = Activity.EATING;
        else if (gf.isSeated()) a = Activity.SITTING;
        else a = switch (gf.getCommand()) {
            case FOLLOW -> gf.getOwner() != null ? Activity.FOLLOWING : Activity.IDLE;
            case WAIT -> Activity.WAITING;
            case SIT -> Activity.SITTING;
            case STAY_SAFE -> Activity.STAYING_SAFE;
            case HOME -> isNearHome(8) ? Activity.AT_HOME : Activity.GOING_HOME;
        };
        gf.setActivity(a);
    }

    public boolean isNearHome(double dist) {
        BlockPos home = data().home;
        return home != null && gf.level().dimension().location().toString().equals(data().homeDimension) && home.closerToCenterThan(gf.position(), dist);
    }

    private void expireOldPromises(long day) {
        for (MemoryEntry e : data().memory.openPromises()) {
            if (day - e.day > 4) {
                e.resolved = true;
                data().today.add(LogKey.DISAPPOINTMENTS, 0.5f);
                data().relationship.lose(2f, 1f);
            }
        }
    }

    // ================================================================== fear

    private void threatTick() {
        if (gf.isSleeping() && !fear.inDanger) {
            ThreatScanner.scan(gf, fear);
            if (fear.level > 0.6f) gf.stopSleeping();
        } else {
            ThreatScanner.scan(gf, fear);
        }
        gf.setFear(fear.level);
        CompanionData d = data();
        d.today.max(LogKey.MAX_FEAR, fear.level);
        boolean afraid = fear.afraid();
        gf.setFlag(GirlfriendEntity.FLAG_SCARED, afraid);
        long now = now();
        if (afraid && !fear.inDanger) {
            fear.inDanger = true;
            fear.dangerStartTick = now;
            fear.peak = fear.level;
            fear.protectedDuringEncounter = false;
            if (now - d.lastFrightTick > 600) {
                d.today.add(LogKey.FRIGHTS, 1);
                d.lastFrightTick = now;
                gf.playGesture(Gesture.STARTLED);
                feel(Emotion.FEAR, Math.min(1f, fear.level + 0.2f), 300);
                d.mood.shiftWellbeing(-3f * GFConfig.MOOD_SENSITIVITY.get().floatValue());
                say(DialogueTrigger.SCARED, Map.of("mob", mobName(fear.mainThreat)));
            }
            InteractionType cur = gf.interactions().current();
            if (cur != null && cur != InteractionType.CARRY && cur != InteractionType.HOLD_HANDS) gf.interactions().stop(InteractionManager.StopReason.DANGER);
            if (gf.isSeated() && gf.getCommand() != CommandMode.SIT && !gf.interactions().isActive()) SeatManager.standUp(gf);
        } else if (fear.inDanger && fear.level > 0.5f && ownerNear && ticks % 160 == 0) {
            say(DialogueTrigger.ASK_PROTECTION, Map.of("mob", mobName(fear.mainThreat)));
        } else if (fear.inDanger && fear.level < 0.08f && now - fear.dangerStartTick > 60) {
            fear.inDanger = false;
            if (fear.protectedDuringEncounter) {
                gf.playGesture(Gesture.CELEBRATE);
                feel(Emotion.RELIEF, 0.9f, 600);
                say(DialogueTrigger.CELEBRATE);
            } else {
                gf.playGesture(Gesture.RELIEVED);
                feel(Emotion.RELIEF, 0.6f, 400);
                say(DialogueTrigger.DANGER_OVER);
            }
            if (fear.peak > 0.7f) {
                d.memory.remember(MemoryType.DANGER, "we ran into scary monsters near " + biomeName(), "danger", day(), now);
            }
            fear.peak = 0f;
        }
    }

    public static String mobName(@Nullable Entity e) {
        return e == null ? "monster" : e.getType().getDescription().getString().toLowerCase();
    }

    public String biomeName() {
        return gf.level().getBiome(gf.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath().replace('_', ' ')).orElse("here");
    }

    // ================================================================== player input

    public InteractionResult onRightClick(ServerPlayer player, ItemStack stack) {
        if (!gf.hasOwner()) {
            claim(player);
            return InteractionResult.SUCCESS;
        }
        if (!gf.isOwnedBy(player)) {
            gf.getLookControl().setLookAt(player, 30f, 30f);
            gf.playGesture(Gesture.WAVE);
            say(DialogueTrigger.GREETING, Map.of("player", player.getGameProfile().getName()));
            return InteractionResult.SUCCESS;
        }
        if (gf.isFainted()) {
            faintTicks = Math.max(20, faintTicks - 100);
            gf.level().playSound(null, gf.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6f, 1.3f);
            return InteractionResult.SUCCESS;
        }
        if (gf.isCarried()) {
            gf.interactions().stop(InteractionManager.StopReason.CANCELLED_PLAYER);
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() || player.isShiftKeyDown()) {
            InteractionMenu.open(gf, player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.WEDDING_RING.get())) {
            onPropose(player, stack);
            return InteractionResult.SUCCESS;
        }
        if (FoodHelper.isDrink(stack)) {
            onDrink(player, stack);
            return InteractionResult.SUCCESS;
        }
        // edible romantic gifts (chocolates) are presents first; she still eats some in onGift
        if (FoodHelper.isFood(stack, gf) && !stack.is(GiftCategory.SPECIAL)) {
            onFeed(player, stack);
            return InteractionResult.SUCCESS;
        }
        if (GiftCategory.of(stack) != null) {
            onGift(player, stack);
            return InteractionResult.SUCCESS;
        }
        InteractionMenu.open(gf, player);
        return InteractionResult.SUCCESS;
    }

    public void claim(ServerPlayer player) {
        gf.setOwner(player.getUUID());
        CompanionData d = data();
        d.relationship.markTogether(day());
        d.lastOwnerNearTick = now();
        d.lastInteractionTick = now();
        d.memory.remember(MemoryType.FIRST_MEETING, "we first met in the " + biomeName(), "first_meeting", day(), now(), 1.2f);
        d.relationship.milestones.add("met");
        gf.playGesture(Gesture.WAVE);
        say(DialogueTrigger.FIRST_MEETING);
        if (gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, true);
    }

    public void onRenamed(String oldName) {
        CompanionData d = data();
        if (!gf.hasOwner()) return;
        d.memory.remember(MemoryType.MILESTONE, "you gave me the name " + d.name, "renamed", day(), now(), 0.9f);
        feel(Emotion.JOY, 0.7f, 400);
        say(DialogueTrigger.RENAMED, Map.of("old", oldName));
        if (gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, true);
    }

    public void markInteraction() {
        data().lastInteractionTick = now();
    }

    public void onTalk(ServerPlayer player) {
        CompanionData d = data();
        d.today.add(LogKey.TALKS, 1);
        d.needs.addAttention(4f);
        d.mood.shiftWellbeing(1.5f);
        gain("talk", 1.2f, 0.4f);
        markInteraction();
        RequestSystem.progress(gf, RequestType.TALK_TOGETHER, 1f, null);
    }

    public void onFeed(ServerPlayer player, ItemStack stack) {
        CompanionData d = data();
        Needs n = d.needs;
        String foodName = stack.getHoverName().getString();
        if (FoodHelper.isBlacklisted(stack)) {
            gf.playGesture(Gesture.POUT);
            feel(Emotion.ANNOYANCE, 0.3f, 200);
            say(DialogueTrigger.GIFT_DISLIKED, Map.of("item", foodName));
            return;
        }
        if (n.hunger < 6f) {
            say(DialogueTrigger.FULL, Map.of("item", foodName));
            return;
        }
        boolean wasHungry = n.isHungry();
        int nutrition = Math.max(1, FoodHelper.nutrition(stack, gf));
        ItemStack leftover = FoodHelper.leftover(stack, gf);
        boolean sweet = stack.is(GiftCategory.SWEETS.tag);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!leftover.isEmpty() && !player.getInventory().add(leftover)) player.drop(leftover, false);
        }
        n.addHunger(-nutrition * GFConfig.FOOD_MULTIPLIER.get().floatValue());
        gf.heal(nutrition * 0.5f);
        gf.playGesture(Gesture.EAT);
        gf.level().playSound(null, gf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.2f);
        d.today.add(LogKey.FEEDINGS, 1);
        d.lastFedTick = now();
        n.addAttention(5f);
        d.mood.shiftWellbeing(wasHungry ? 7f : 3f);
        gain("feed", wasHungry ? 4f : 2f, wasHungry ? 1.2f : 0.5f);
        markInteraction();
        feel(Emotion.JOY, wasHungry ? 0.7f : 0.4f, 300);
        if (sweet && d.preferences.favorite == GiftCategory.SWEETS && !d.preferences.isRevealed(GiftCategory.SWEETS)) {
            d.preferences.reveal(GiftCategory.SWEETS);
            d.memory.remember(MemoryType.PREFERENCE, "I told you I have a sweet tooth", "pref_sweets", day(), now());
        }
        RequestSystem.progress(gf, RequestType.BRING_FOOD, 1f, null);
        say(DialogueTrigger.FED, Map.of("item", foodName, "was_hungry", wasHungry ? "1" : "0"));
    }

    public void onDrink(ServerPlayer player, ItemStack stack) {
        CompanionData d = data();
        String name = stack.getHoverName().getString();
        ItemStack remainder = ItemStack.EMPTY;
        if (stack.is(Items.MILK_BUCKET)) {
            gf.removeAllEffects();
            remainder = new ItemStack(Items.BUCKET);
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            gf.removeEffect(MobEffects.POISON);
            d.needs.addHunger(-6f);
            remainder = new ItemStack(Items.GLASS_BOTTLE);
        } else if (stack.is(Items.POTION)) {
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (contents.is(Potions.WATER)) d.needs.addEnergy(3f);
            contents.forEachEffect(effect -> gf.addEffect(effect));
            remainder = new ItemStack(Items.GLASS_BOTTLE);
        } else {
            d.needs.addEnergy(2f);
            remainder = stack.getCraftingRemainingItem();
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!remainder.isEmpty() && !player.getInventory().add(remainder)) player.drop(remainder, false);
        }
        gf.playGesture(Gesture.DRINK);
        gf.level().playSound(null, gf.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6f, 1.2f);
        d.needs.addAttention(3f);
        markInteraction();
        say(DialogueTrigger.DRINK, Map.of("item", name));
    }

    public void onGift(ServerPlayer player, ItemStack stack) {
        CompanionData d = data();
        GiftCategory cat = GiftCategory.of(stack);
        if (cat == null) {
            say(DialogueTrigger.GIFT_CONFUSED, Map.of("item", stack.getHoverName().getString()));
            return;
        }
        String itemId = FoodHelper.id(stack);
        String itemName = stack.getHoverName().getString();
        Preferences prefs = d.preferences;
        boolean special = stack.is(GiftCategory.SPECIAL);
        int nutrition = FoodHelper.isFood(stack, gf) ? FoodHelper.nutrition(stack, gf) : 0;
        float moodFactor = 1f + d.mood.primary.valence * 0.15f;
        float rarity = switch (stack.getRarity()) {
            case COMMON -> 0f;
            case UNCOMMON -> 0.2f;
            case RARE -> 0.4f;
            case EPIC -> 0.6f;
        };
        // hand-made romantic gifts weigh like her favourite category and are never handed back; repeats still wear off
        float weight = special ? Math.max(prefs.weight(cat), SPECIAL_GIFT_WEIGHT) : prefs.weight(cat);
        float score = weight * prefs.novelty(itemId) * moodFactor * (1f + rarity);
        if (special) score = Math.max(score, 0.6f);
        else if (cat == prefs.disliked) score *= 0.4f;
        long now = now();
        prefs.recordGift(itemId);
        d.lastGiftTick = now;
        markInteraction();
        RequestSystem.progress(gf, RequestType.PREFERRED_GIFT, cat.name().equals(d.request != null ? d.request.detail : "") ? 1f : 0f, cat.name());
        boolean shy = d.personality.shyness > 0.6f && d.relationship.stage().ordinal() <= RelationshipStage.FRIENDSHIP.ordinal();

        if (!special && (score < 0.5f || cat == prefs.disliked)) {
            // she gently gives it back: disliked gifts are not consumed
            gf.playGesture(d.personality.playfulness > 0.5f ? Gesture.POUT : Gesture.SHY);
            d.today.add(LogKey.GIFTS_DISLIKED, 1);
            d.needs.addAttention(3f);
            d.mood.shiftWellbeing(-1f);
            if (!prefs.isRevealed(cat)) {
                prefs.reveal(cat);
                d.memory.remember(MemoryType.PREFERENCE, "I told you I'm not into " + cat.word(), "dislike_" + cat.id, day(), now);
            }
            say(DialogueTrigger.GIFT_DISLIKED, Map.of("item", itemName, "category", cat.word()));
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        heartParticles(5);
        if (nutrition > 0) {
            // a box of chocolates: she eats a few and keeps the rest
            d.needs.addHunger(-nutrition * GFConfig.FOOD_MULTIPLIER.get().floatValue() * 0.5f);
            RequestSystem.progress(gf, RequestType.BRING_FOOD, 1f, null);
        }
        if (score >= 1.4f) {
            gain("gift", 8f + score * 3f, 2.5f);
            d.needs.addAttention(14f);
            d.mood.shiftWellbeing(12f);
            feel(special ? Emotion.LOVE : Emotion.JOY, 0.95f, 800);
            gf.playGesture(shy ? Gesture.SHY : Gesture.GIFT_HAPPY);
            d.today.add(LogKey.GIFTS_LIKED, 1);
            d.memory.remember(MemoryType.GIFT, "you gave me " + itemName + " and I loved it", itemId, day(), now, special ? 0.9f : 0.75f);
            if (cat == prefs.favorite && !prefs.isRevealed(cat)) {
                prefs.reveal(cat);
                d.memory.remember(MemoryType.PREFERENCE, "you found out I love " + cat.word(), "fav_" + cat.id, day(), now, 0.8f);
            }
            if (special) onRomanticGift(itemId, itemName, cat);
            else say(DialogueTrigger.GIFT_LOVED, Map.of("item", itemName, "category", cat.word()));
        } else if (score >= 0.85f) {
            gain("gift", 5f + score * 2f, 1.5f);
            d.needs.addAttention(10f);
            d.mood.shiftWellbeing(7f);
            feel(Emotion.JOY, 0.6f, 500);
            gf.playGesture(shy ? Gesture.SHY : Gesture.GIFT_HAPPY);
            d.today.add(LogKey.GIFTS_LIKED, 1);
            d.memory.remember(MemoryType.GIFT, "you gave me " + itemName, itemId, day(), now, 0.5f);
            say(DialogueTrigger.GIFT_LIKED, Map.of("item", itemName, "category", cat.word()));
        } else {
            gain("gift", 2f, 0.5f);
            d.needs.addAttention(6f);
            d.mood.shiftWellbeing(2f);
            gf.playGesture(Gesture.TALK);
            say(DialogueTrigger.GIFT_OKAY, Map.of("item", itemName, "category", cat.word()));
        }
    }

    /** Extra warmth for a loved hand-made gift: its own line, more hearts, and a keepsake memory for a love letter. */
    private void onRomanticGift(String itemId, String itemName, GiftCategory cat) {
        CompanionData d = data();
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        String gift = id != null && id.getNamespace().equals(AIGirlfriend.MOD_ID) ? id.getPath() : "";
        heartParticles(8);
        d.mood.shiftWellbeing(4f);
        if (gift.equals("love_letter")) {
            gf.playGesture(Gesture.SHY);
            if (d.relationship.milestones.add("love_letter")) {
                d.memory.remember(MemoryType.MILESTONE, "you wrote me a love letter and I read it again and again", "love_letter", day(), now(), 1.1f);
            }
        }
        say(DialogueTrigger.GIFT_ROMANTIC, Map.of("item", itemName, "category", cat.word(), "gift", gift));
    }

    // ================================================================== proposal & stage helpers

    /**
     * Right-click with a wedding ring. From DEEP_TRUST on she says yes: the ring becomes hers and she is married
     * (surprise first, the celebration follows in {@link #tick}). Earlier she sweetly declines and the ring stays with the player.
     */
    public void onPropose(ServerPlayer player, ItemStack ring) {
        CompanionData d = data();
        Relationship r = d.relationship;
        if (proposalTicks > 0) return; // she is still taking in the last one
        markInteraction();
        gf.getLookControl().setLookAt(player, 30f, 30f);
        if (r.married) {
            if (!canSay(DialogueTrigger.PROPOSAL_ALREADY_MARRIED)) return;
            gf.playGesture(Gesture.GIGGLE);
            feel(Emotion.LOVE, 0.8f, 400);
            heartParticles(4);
            say(DialogueTrigger.PROPOSAL_ALREADY_MARRIED);
            return;
        }
        if (gf.isSleeping() || fear.afraid()) {
            player.displayClientMessage(Component.translatable("aigirlfriend.message.propose_later", d.name), true);
            return;
        }
        if (!r.stage().atLeast(RelationshipStage.DEEP_TRUST)) {
            if (!canSay(DialogueTrigger.PROPOSAL_DECLINED)) return;
            boolean romantic = r.stage().isRomantic();
            gf.playGesture(romantic ? Gesture.SHY : Gesture.STARTLED);
            feel(romantic ? Emotion.LOVE : Emotion.EMBARRASSMENT, 0.8f, 600);
            if (romantic) {
                gain("proposal_early", 3f, 1f);
                d.memory.remember(MemoryType.AFFECTION, "you asked me to marry you and I asked you to wait a little", "proposal_early", day(), now(), 0.8f);
            }
            say(DialogueTrigger.PROPOSAL_DECLINED);
            return;
        }
        if (!player.getAbilities().instabuild) ring.shrink(1);
        r.setMarried(true, day());
        d.memory.remember(MemoryType.MILESTONE, "you proposed to me in the " + biomeName() + " and I said yes", "married", day(), now(), 1.5f);
        gain("proposal", 30f, 10f);
        d.needs.addAttention(30f);
        d.mood.shiftWellbeing(25f);
        gf.playGesture(Gesture.STARTLED);
        feel(Emotion.SURPRISE, 1f, PROPOSAL_SURPRISE_TICKS);
        gf.level().playSound(null, gf.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 1.5f);
        proposalTicks = PROPOSAL_SURPRISE_TICKS;
        player.displayClientMessage(Component.translatable("aigirlfriend.message.married", d.name), false);
        if (gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, true);
    }

    private void celebrateProposal() {
        gf.playGesture(Gesture.CELEBRATE);
        feel(Emotion.LOVE, 1f, 2400);
        heartParticles(20);
        if (gf.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, gf.getX(), gf.getY() + 1.2, gf.getZ(), 40, 0.5, 0.6, 0.5, 0.3);
        }
        gf.level().playSound(null, gf.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.8f, 1f);
        say(DialogueTrigger.PROPOSAL_ACCEPTED);
    }

    /** Behind /gf maxtier: real stats for the top stage (so it sticks), with the usual milestone once. False if she was already there. */
    public boolean reachMaxStage() {
        Relationship r = data().relationship;
        boolean already = r.stage() == RelationshipStage.PARTNERSHIP;
        r.maxOut();
        checkStage();
        return !already;
    }

    // ================================================================== interactions

    /** Called by the interaction manager when a coordinated interaction finishes properly. */
    public void onInteractionCompleted(InteractionType type, ServerPlayer partner, boolean selfInitiated, boolean shy) {
        CompanionData d = data();
        float gained = gain(type.name().toLowerCase(), type.affection, type.trust);
        d.needs.addAttention(type.attention);
        d.mood.shiftWellbeing(3f + type.affection * 0.5f);
        if (type.logKey != LogKey.HAND_HOLD_SECONDS && type.logKey != LogKey.NEAR_OWNER_SECONDS) d.today.add(type.logKey, 1);
        if (selfInitiated) d.today.add(LogKey.INITIATED_AFFECTION, 1);
        if (shy) d.today.add(LogKey.EMBARRASSING, 1);
        d.today.add(LogKey.AFFECTION_GAINED, gained);
        feel(shy ? Emotion.EMBARRASSMENT : type.emotion, 0.7f, 500);
        markInteraction();
        String key = "first_" + type.name().toLowerCase();
        if (!d.relationship.milestones.contains(key) && type.isRomantic() || !d.relationship.milestones.contains(key) && type == InteractionType.HUG) {
            d.relationship.milestones.add(key);
            String what = switch (type) {
                case KISS -> "our first kiss";
                case HUG -> "our first hug";
                case CUDDLE, CUDDLE_SITTING, CUDDLE_BED -> "the first time we cuddled";
                case SLEEP_TOGETHER -> "the first night we fell asleep together";
                case BACK_HUG -> "the first time I hugged you from behind";
                case FOREHEAD_KISS -> "the first time you kissed my forehead";
                case HEAD_ON_SHOULDER -> "the first time I rested my head on your shoulder";
                case LAP_SIT -> "the first time I curled up on your lap";
                case PET_CHEEK -> "the first time you caressed my cheek";
                default -> "a sweet moment together";
            };
            d.memory.remember(MemoryType.MILESTONE, what + " in the " + biomeName(), key, day(), now(), 1.0f);
        }
        if (type == InteractionType.HUG || type == InteractionType.CUDDLE || type == InteractionType.CUDDLE_SITTING
                || type == InteractionType.HEAD_ON_SHOULDER || type == InteractionType.BACK_HUG || type == InteractionType.CUDDLE_BED
                || type == InteractionType.PET_HAIR || type == InteractionType.PET_CHEEK || type == InteractionType.LAP_SIT) {
            RequestSystem.progress(gf, RequestType.COMFORT_ME, 1f, null);
        }
        if (shy) gf.playGesture(Gesture.SHY);
    }

    /** Ongoing (toggle) interactions give small rewards over time. */
    public void onInteractionSecond(InteractionType type, int seconds) {
        CompanionData d = data();
        if (type == InteractionType.HOLD_HANDS || type == InteractionType.LEAD_TO_BED) d.today.add(LogKey.HAND_HOLD_SECONDS, 1);
        if (seconds % 30 == 0) {
            gain(type.name().toLowerCase() + "_time", type.affection * 0.6f, type.trust * 0.5f);
            d.needs.addAttention(type.attention * 0.35f);
            d.mood.shiftWellbeing(1.5f);
            markInteraction();
        }
    }

    public void onInteractionDeclined(InteractionType type) {
        CompanionData d = data();
        d.today.add(LogKey.DECLINED, 1);
        d.mood.shiftWellbeing(-1.5f * (0.5f + d.personality.sensitivity));
        say(DialogueTrigger.INTERACTION_REFUSED, Map.of("action", type.name().toLowerCase().replace('_', ' ')));
        if (d.personality.playfulness > 0.6f) gf.playGesture(Gesture.POUT);
    }

    /** Applies a relationship gain and handles stage milestones. */
    public float gain(String key, float affection, float trust) {
        CompanionData d = data();
        float g = d.relationship.gain(key, affection, trust, day());
        checkStage();
        return g;
    }

    public void lose(float affection, float trust) {
        data().relationship.lose(affection, trust);
        checkStage();
    }

    public void checkStage() {
        CompanionData d = data();
        RelationshipStage before = gf.getSyncedStage();
        RelationshipStage up = d.relationship.refreshStage();
        RelationshipStage now = d.relationship.stage();
        if (now != before) gf.setSyncedStage(now);
        if (up != null) {
            d.memory.remember(MemoryType.MILESTONE, "our relationship grew into " + up.name().toLowerCase().replace('_', ' '), "stage_" + up.name(), day(), now(), 1.2f);
            gf.playGesture(d.personality.shyness > 0.55f ? Gesture.SHY : Gesture.CELEBRATE);
            feel(Emotion.LOVE, 1f, 1200);
            heartParticles(10);
            say(DialogueTrigger.MILESTONE, Map.of("stage", up.name().toLowerCase().replace('_', ' ')));
            ServerPlayer owner = gf.getOwnerAnywhere();
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("aigirlfriend.message.milestone", d.name, Component.translatable(up.key())), false);
            }
        }
    }

    public void heartParticles(int count) {
        if (gf.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, gf.getX(), gf.getY() + gf.getBbHeight() + 0.3, gf.getZ(), count, 0.35, 0.25, 0.35, 0.02);
        }
    }

    // ================================================================== commands

    public void command(CommandMode mode, ServerPlayer by) {
        CompanionData d = data();
        markInteraction();
        if (gf.interactions().isActive() && gf.interactions().current() != InteractionType.HOLD_HANDS) {
            gf.interactions().stop(InteractionManager.StopReason.CANCELLED_PLAYER);
        }
        switch (mode) {
            case FOLLOW -> {
                if (gf.isSeated() && !SeatManager.isSeated(by)) SeatManager.standUp(gf);
                gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
                gf.setWaitPos(null);
                say(DialogueTrigger.COMMAND_FOLLOW);
            }
            case WAIT -> {
                gf.setWaitPos(gf.blockPosition());
                say(DialogueTrigger.COMMAND_WAIT);
            }
            case SIT -> {
                gf.setWaitPos(gf.blockPosition());
                if (!gf.isSeated() && SeatManager.sitHere(gf) == null) gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, true);
                say(DialogueTrigger.COMMAND_SIT);
            }
            case STAY_SAFE -> say(DialogueTrigger.COMMAND_STAY_SAFE);
            case HOME -> {
                if (d.home == null) {
                    say(DialogueTrigger.COMMAND_HOME, Map.of("nohome", "1"));
                    return;
                }
                if (gf.isSeated()) SeatManager.standUp(gf);
                say(DialogueTrigger.COMMAND_HOME);
            }
        }
        if (mode != CommandMode.SIT) gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
        gf.setCommand(mode);
        RequestSystem.onCommand(gf, mode);
    }

    public void setHome(BlockPos pos) {
        CompanionData d = data();
        d.home = pos.immutable();
        d.homeDimension = gf.level().dimension().location().toString();
        d.memory.remember(MemoryType.FAVORITE_PLACE, "we made our home near " + pos.getX() + ", " + pos.getZ() + " in the " + biomeName(), "home", day(), now(), 1.0f);
        say(DialogueTrigger.HOME_SET);
        feel(Emotion.JOY, 0.6f, 400);
    }

    // ================================================================== damage, rescue, fainting

    /** Returns false to cancel the damage entirely. */
    public boolean beforeHurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && gf.isOwnedBy(p) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            onHurtByOwner(p);
            return false;
        }
        if (GFConfig.ENVIRONMENT_RESCUE.get() && now() - lastRescueTick > 100) {
            boolean unfair = source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.IN_WALL)
                    || (source.is(DamageTypeTags.IS_FIRE) && gf.isInLava())
                    || (source.is(DamageTypes.DROWN) && gf.getHealth() <= amount * 3f)
                    || (source.is(DamageTypes.FREEZE) && gf.getHealth() <= amount * 3f);
            if (unfair && rescue()) {
                lastRescueTick = now();
                return false;
            }
        }
        return true;
    }

    public void afterHurt(DamageSource source, float damage) {
        if (damage <= 0f) return;
        CompanionData d = data();
        long now = now();
        lastHurtTick = now;
        d.today.add(LogKey.HURT, 1);
        d.mood.shiftWellbeing(-2f);
        gf.playGesture(Gesture.HURT);
        feel(Emotion.FEAR, 0.7f, 200);
        if (gf.isSleeping()) gf.stopSleeping();
        if (gf.isSeated() && gf.getCommand() != CommandMode.SIT) SeatManager.standUp(gf);
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && !(attacker instanceof Player)) {
            fear.level = Math.max(fear.level, 0.65f);
            fear.mainThreat = living;
            if (now - lastDefenseTick > GFConfig.DEFENSE_COOLDOWN.get() * 20L && gf.distanceTo(living) < 3f) {
                lastDefenseTick = now;
                gf.playGesture(Gesture.DEFEND);
                gf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                living.knockback(0.9, gf.getX() - living.getX(), gf.getZ() - living.getZ());
                if (GFConfig.DEFENSE_DAMAGE.get() > 0) living.hurt(gf.damageSources().mobAttack(gf), GFConfig.DEFENSE_DAMAGE.get().floatValue());
                say(DialogueTrigger.DEFENDED, Map.of("mob", mobName(living)));
            } else {
                say(DialogueTrigger.HURT, Map.of("mob", mobName(living)));
            }
        }
    }

    private void onHurtByOwner(Player owner) {
        CompanionData d = data();
        long now = now();
        if (now - lastHurtTick < 10) return;
        lastHurtTick = now;
        lose(10f, 6f);
        d.today.add(LogKey.DISAPPOINTMENTS, 1);
        d.mood.shiftWellbeing(-12f * GFConfig.MOOD_SENSITIVITY.get().floatValue());
        feel(d.personality.confidence > 0.6f ? Emotion.ANNOYANCE : Emotion.SADNESS, 0.9f, 1200);
        gf.playGesture(Gesture.HURT);
        d.memory.remember(MemoryType.DISAPPOINTMENT, "you hit me", "hit_by_owner", day(), now, 0.8f);
        say(DialogueTrigger.HURT_BY_OWNER);
    }

    /** Moves her out of an unfair hazard: to the owner if possible, else home, else straight up to air. */
    public boolean rescue() {
        Player owner = gf.getOwner();
        if (owner != null && Teleport.nearPlayer(gf, owner)) {
            gf.clearFire();
            return true;
        }
        BlockPos home = data().home;
        if (home != null && isHomeDimension() && Teleport.toSafeSpot(gf, home)) {
            gf.clearFire();
            return true;
        }
        BlockPos up = gf.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gf.blockPosition());
        if (Teleport.toSafeSpot(gf, up)) {
            gf.clearFire();
            return true;
        }
        return false;
    }

    public boolean isHomeDimension() {
        return gf.level().dimension().location().toString().equals(data().homeDimension);
    }

    public void faint(DamageSource source) {
        CompanionData d = data();
        faintTicks = GFConfig.FAINT_SECONDS.get() * 20;
        gf.setFlag(GirlfriendEntity.FLAG_FAINTED, true);
        gf.setActivity(Activity.FAINTED);
        gf.interactions().stop(InteractionManager.StopReason.FAINTED);
        if (gf.isPassenger()) gf.stopRiding();
        if (gf.isSleeping()) gf.stopSleeping();
        gf.getNavigation().stop();
        gf.clearFire();
        d.today.add(LogKey.FAINTED, 1);
        d.mood.shiftWellbeing(-25f);
        Entity attacker = source.getEntity();
        String cause = attacker != null ? mobName(attacker) : source.getMsgId();
        d.memory.remember(MemoryType.HURT, "I fainted after being hurt by " + cause, "fainted", day(), now(), 0.7f);
        clearTargets();
        ServerPlayer owner = gf.getOwnerAnywhere();
        if (owner != null) owner.displayClientMessage(Component.translatable("aigirlfriend.message.fainted", d.name), false);
    }

    private void clearTargets() {
        for (Mob m : gf.level().getEntitiesOfClass(Mob.class, gf.getBoundingBox().inflate(24), m -> m.getTarget() == gf)) m.setTarget(null);
    }

    private void tickFaint() {
        gf.getNavigation().stop();
        gf.setFear(0f);
        if (ticks % 20 == 0) {
            clearTargets();
            Player owner = gf.getOwner();
            if (owner != null && owner.distanceTo(gf) < 4f) faintTicks -= 20;
            NeedsSystem.update(gf, false, 0f);
        }
        if (--faintTicks <= 0) {
            faintTicks = 0;
            gf.setFlag(GirlfriendEntity.FLAG_FAINTED, false);
            gf.setHealth(Math.max(gf.getHealth(), gf.getMaxHealth() * 0.3f));
            fear.level = 0.3f;
            Player owner = gf.getOwner();
            boolean ownerHere = owner != null && owner.distanceTo(gf) < 6f;
            if (ownerHere) {
                gain("stayed_by_me", 3f, 3f);
                data().memory.remember(MemoryType.PROTECTED, "you stayed by my side when I fainted", "stayed_fainted", day(), now(), 0.9f);
            }
            feel(ownerHere ? Emotion.RELIEF : Emotion.SADNESS, 0.8f, 1200);
            say(DialogueTrigger.FAINT_RECOVERED, Map.of("owner_here", ownerHere ? "1" : "0"));
        }
    }

    public boolean isFaintedNow() {
        return faintTicks > 0;
    }

    public void onDeath(DamageSource source) {
        CompanionData d = data();
        d.memory.remember(MemoryType.HURT, "I was lost to " + source.getMsgId(), "died", day(), now(), 0.6f);
        if (gf.level() instanceof ServerLevel sl) {
            CompanionRegistry reg = CompanionRegistry.get(sl.getServer());
            reg.update(gf, true);
            reg.markStatus(d.companionId, CompanionRegistry.Status.DEAD);
        }
        ServerPlayer owner = gf.getOwnerAnywhere();
        if (owner != null) owner.displayClientMessage(Component.translatable("aigirlfriend.message.died", d.name), false);
    }

    // ================================================================== protection

    public void onOwnerDefeatedThreat(LivingEntity mob) {
        CompanionData d = data();
        boolean targetedHer = mob instanceof Mob m && m.getTarget() == gf;
        if (!targetedHer && fear.level < 0.15f && mob.distanceTo(gf) > 10f) return;
        d.today.add(LogKey.MOBS_DEFEATED, 1);
        if (!fear.inDanger && !targetedHer && fear.level < 0.3f) return;
        long now = now();
        fear.protectedDuringEncounter = true;
        d.today.add(LogKey.PROTECTED, 1);
        gain("protect", 6f, 3f);
        d.lastProtectedTick = now;
        d.needs.addAttention(6f);
        d.mood.shiftWellbeing(6f);
        d.memory.remember(MemoryType.PROTECTED, "you protected me from a " + mobName(mob), "protected_" + mob.getType().toShortString(), day(), now, 0.85f);
        RequestSystem.progress(gf, RequestType.PROTECT_ME, 1f, null);
        if (fear.level > 0.3f || targetedHer) say(DialogueTrigger.PROTECTED, Map.of("mob", mobName(mob)));
    }

    // ================================================================== sleep

    /** Applies elapsed-time needs right now, e.g. before the night is skipped. */
    public void flushNeeds(boolean sleeping) {
        NeedsSystem.update(gf, sleeping, sleeping ? 0f : activityLevel);
    }

    /** Ticks of sitting up in bed before she gets out (matches the in-bed part of the wake-up clip). */
    private static final int WAKE_SIT_UP_TICKS = 34;
    private int wakeTicks;
    private boolean wakeShared;

    /** Wakes her up: she first sits up in bed (still lying in it), then gets out, stretches and greets. */
    public void beginWakeUp(boolean sharedWithOwner) {
        if (!gf.isSleeping()) {
            onWokeUp(sharedWithOwner);
            return;
        }
        if (wakeTicks > 0) return;
        wakeTicks = WAKE_SIT_UP_TICKS;
        wakeShared = sharedWithOwner;
        gf.setFlag(GirlfriendEntity.FLAG_WAKING, true);
    }

    public boolean isWakingUp() {
        return wakeTicks > 0;
    }

    private void finishWakeUp() {
        gf.setFlag(GirlfriendEntity.FLAG_WAKING, false);
        if (gf.isSleeping()) gf.stopSleeping();
        onWokeUp(wakeShared);
    }

    public void onWokeUp(boolean sharedWithOwner) {
        CompanionData d = data();
        d.today.add(LogKey.SLEPT, 1);
        if (sharedWithOwner) d.today.add(LogKey.SLEPT_TOGETHER, 1);
        d.mood.shiftWellbeing(sharedWithOwner ? 6f : 2f);
        gf.playGesture(Gesture.WAKE_UP);
        say(DialogueTrigger.GOOD_MORNING, Map.of("together", sharedWithOwner ? "1" : "0"));
        pendingWakeAffection = now() + 80;
    }

    public void requestBedtime(ServerPlayer player) {
        bedtimeRequested = true;
        lastBedtimeLeadDay = -1;
        markInteraction();
        say(DialogueTrigger.BEDTIME);
    }

    /** Wakes her up from her own (non shared) sleep in the morning, or when danger appears. */
    private void tickOwnSleep() {
        InteractionType cur = gf.interactions().current();
        if (!gf.isSleeping() || cur == InteractionType.SLEEP_TOGETHER || cur == InteractionType.CUDDLE_BED) return;
        long tod = WorldClock.timeOfDay(gf.level());
        boolean day = tod >= 0 && tod < 12000;
        if (waitingBed != null) {
            if (now() - waitingBedSince > 2400) {
                waitingBed = null;
                say(DialogueTrigger.SLEEP_DECLINED);
            }
        }
        if (day && data().needs.energy > 45f && wakeTicks == 0) {
            flushNeeds(true);
            waitingBed = null;
            beginWakeUp(false);
        }
    }

    // ================================================================== emotions & dialogue

    public void feel(Emotion emotion, float intensity, int durationTicks) {
        data().mood.feel(emotion, intensity, now(), durationTicks);
        gf.setExpression(currentExpression());
    }

    /** Test/debug hook: forces a facial expression (null = normal behaviour). */
    @Nullable public Expression debugExpression;

    public Expression currentExpression() {
        if (debugExpression != null) return debugExpression;
        Expression override = gf.interactions().expressionOverride();
        if (override != null) return override;
        return MoodEvaluator.expression(gf, now(), fear.level);
    }

    public boolean canSay(DialogueTrigger trigger) {
        Long last = lastSaid.get(trigger);
        return last == null || now() - last >= trigger.cooldown;
    }

    public boolean say(DialogueTrigger trigger) {
        return say(trigger, Map.of());
    }

    public boolean say(DialogueTrigger trigger, Map<String, String> vars) {
        if (!canSay(trigger)) return false;
        lastSaid.put(trigger, now());
        lastSpokeTick = now();
        DialogueService.speak(gf, trigger, new HashMap<>(vars));
        return true;
    }

    private void ambientDialogue(long now) {
        double freq = GFConfig.DIALOGUE_FREQUENCY.get();
        if (freq <= 0 || !ownerNear || fear.afraid()) return;
        long minGap = (long) (GFConfig.DIALOGUE_MIN_SECONDS.get() * 20L / Math.max(0.25, freq));
        if (now - lastSpokeTick < minGap) return;
        if (gf.getRandom().nextFloat() > 0.02f * freq) return;
        CompanionData d = data();
        Needs n = d.needs;
        var level = gf.level();
        CyclePhase phase = d.cycle.phase(NeedsSystem.cycleClock(level));
        DialogueTrigger t;
        if (n.isStarving()) t = DialogueTrigger.STARVING;
        else if (n.isExhausted()) t = DialogueTrigger.EXHAUSTED;
        else if (n.isHungry() && canSay(DialogueTrigger.HUNGRY)) t = DialogueTrigger.HUNGRY;
        else if (n.isTired() && WorldClock.isBedtime(level) && canSay(DialogueTrigger.BEDTIME)) t = DialogueTrigger.BEDTIME;
        else if (n.isTired() && canSay(DialogueTrigger.TIRED)) t = DialogueTrigger.TIRED;
        else if (n.attention < GFConfig.LONELY_THRESHOLD.get() && now - d.lastInteractionTick > 6000 && canSay(DialogueTrigger.IGNORED)) t = DialogueTrigger.IGNORED;
        else if (phase == CyclePhase.MENSTRUAL && GFConfig.CYCLE_COMFORT_SEEKING.get() > 0 && gf.getRandom().nextFloat() < 0.3f && canSay(DialogueTrigger.CYCLE_DISCOMFORT)) t = DialogueTrigger.CYCLE_DISCOMFORT;
        else if (level.isThundering() && canSay(DialogueTrigger.WEATHER_THUNDER)) t = DialogueTrigger.WEATHER_THUNDER;
        else if (level.isRaining() && level.canSeeSky(gf.blockPosition()) && canSay(DialogueTrigger.WEATHER_RAIN)) t = DialogueTrigger.WEATHER_RAIN;
        else if (WorldClock.isNight(level) && canSay(DialogueTrigger.NIGHT_FALLS)) t = DialogueTrigger.NIGHT_FALLS;
        else if (gf.getRandom().nextFloat() < 0.25f && canSay(DialogueTrigger.REMINISCE) && d.memory.pickReminiscence(day(), gf.getRandom()) != null) t = DialogueTrigger.REMINISCE;
        else if (!d.mood.primary.isNegative() && gf.getRandom().nextFloat() < 0.4f)
            t = d.mood.primary == Mood.AFFECTIONATE || d.mood.primary == Mood.NEEDY ? DialogueTrigger.AFFECTIONATE : DialogueTrigger.HAPPY;
        else if (d.mood.primary == Mood.LONELY && canSay(DialogueTrigger.LONELY)) t = DialogueTrigger.LONELY;
        else t = DialogueTrigger.SMALL_TALK;
        if (!say(t)) say(DialogueTrigger.SMALL_TALK);
    }

    @Nullable
    public Biome currentBiome() {
        return gf.level().getBiome(gf.blockPosition()).value();
    }
}
