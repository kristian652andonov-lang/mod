package com.aigirlfriend.gametest;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.CycleState;
import com.aigirlfriend.companion.LogKey;
import com.aigirlfriend.companion.MemoryType;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.MoodEvaluator;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.dialogue.DialogueContext;
import com.aigirlfriend.dialogue.DialogueService;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.dialogue.IntentClassifier;
import com.aigirlfriend.dialogue.LocalDialogueGenerator;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.registry.ModEntities;
import com.aigirlfriend.registry.ModItems;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceptance tests that run on a real headless server: {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(AIGirlfriend.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    private CompanionGameTests() {}

    /** Lays out a known test platform: a solid floor at relative y = 0 and free space above it. */
    private static void floor(GameTestHelper h) {
        // tests use the scripted dialogue: never download or load the built-in AI model
        com.aigirlfriend.config.GFConfig.AI_BRAIN.set(com.aigirlfriend.config.GFConfig.Brain.OFF);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE);
                for (int y = 1; y < 5; y++) h.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
    }

    private static GirlfriendEntity spawnCompanion(GameTestHelper h, BlockPos pos) {
        GirlfriendEntity gf = h.spawn(ModEntities.GIRLFRIEND.get(), pos);
        gf.ensureInitialized();
        gf.setNoAi(false);
        return gf;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, BlockPos pos) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(pos));
        p.moveTo(v.x, v.y, v.z, 0f, 0f);
        p.setOnGround(true);
        return p;
    }

    private static String solidAround(GameTestHelper h, BlockPos center) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1))) {
            if (!h.getLevel().getBlockState(p).getCollisionShape(h.getLevel(), p).isEmpty()) sb.append(p.subtract(center)).append(h.getLevel().getBlockState(p).getBlock()).append(';');
        }
        return sb.toString();
    }

    private static void removePlayer(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    // 1. identity and relationship survive save/load
    @GameTest(template = "platform")
    public static void persistenceRoundTrip(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        gf.controller().claim(owner);
        CompanionData d = gf.data();
        d.relationship.forceStage(RelationshipStage.ROMANTIC);
        d.memory.remember(MemoryType.GIFT, "you gave me a poppy", "minecraft:poppy", 3, 100);
        d.needs.hunger = 42f;
        d.mood.setPrimary(Mood.PLAYFUL, java.util.List.of("test cause"), 3);
        d.cycle.setPhase(1234L, CyclePhase.LUTEAL);
        gf.controller().setHome(new BlockPos(10, 64, 10));

        CompoundTag tag = gf.saveWithoutId(new CompoundTag());
        GirlfriendEntity copy = ModEntities.GIRLFRIEND.get().create(h.getLevel());
        h.assertTrue(copy != null, "could not create copy");
        copy.load(tag);
        CompanionData c = copy.data();
        h.assertTrue(c.companionId.equals(d.companionId), "companion id lost");
        h.assertTrue(c.name.equals(d.name), "name lost");
        h.assertTrue(owner.getUUID().equals(copy.getOwnerUUID()), "owner lost");
        h.assertTrue(c.relationship.stage() == RelationshipStage.ROMANTIC, "stage lost: " + c.relationship.stage());
        h.assertTrue(Math.abs(c.relationship.affection - d.relationship.affection) < 0.01f, "affection lost");
        h.assertTrue(c.memory.all().size() == d.memory.all().size(), "memories lost");
        h.assertTrue(c.mood.primary == Mood.PLAYFUL && c.mood.causes.contains("test cause"), "mood lost");
        h.assertTrue(Math.abs(c.needs.hunger - 42f) < 0.01f, "needs lost");
        h.assertTrue(c.cycle.anchor == d.cycle.anchor, "cycle lost");
        h.assertTrue(c.personality.affection == d.personality.affection, "personality lost");
        h.assertTrue(c.home != null && c.home.equals(d.home), "home lost");
        removePlayer(h, owner);
        h.succeed();
    }

    // 8. default cycle: 3 days long, first full day menstrual
    @GameTest(template = "platform")
    public static void cycleDefaults(GameTestHelper h) {
        floor(h);
        h.assertTrue(CycleState.lengthTicks() == 72000L, "cycle length should be 3 days, was " + CycleState.lengthTicks());
        h.assertTrue(CycleState.phaseAt(0) == CyclePhase.MENSTRUAL, "day 1 start must be menstrual");
        h.assertTrue(CycleState.phaseAt(23999) == CyclePhase.MENSTRUAL, "day 1 end must be menstrual");
        h.assertTrue(CycleState.phaseAt(24000) != CyclePhase.MENSTRUAL, "day 2 must not be menstrual");
        h.assertTrue(CycleState.phaseAt(71999) == CyclePhase.LUTEAL, "end of cycle must be luteal");
        CycleState s = new CycleState();
        s.setPhase(500_000L, CyclePhase.MENSTRUAL);
        h.assertTrue(s.phase(500_000L + 72_000L) == CyclePhase.MENSTRUAL, "cycle must repeat every 3 days");
        h.assertTrue(s.dayOfCycle(500_000L + 24_000L * 2 + 5) == 3, "day-of-cycle counter wrong");
        h.succeed();
    }

    // 10. local dialogue works for every trigger and intent without AI
    @GameTest(template = "platform")
    public static void localDialogueCoversEverything(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        Map<String, String> vars = new HashMap<>();
        vars.put("item", "Poppy");
        vars.put("mob", "zombie");
        vars.put("request", "BRING_FOOD");
        for (DialogueTrigger t : DialogueTrigger.values()) {
            DialogueContext ctx = new DialogueContext(gf, null, t, vars);
            String line = LocalDialogueGenerator.line(ctx, gf.data().companionId, gf.getRandom());
            h.assertTrue(line != null && !line.isBlank() && !line.contains("{"), "bad line for " + t + ": " + line);
        }
        for (LocalDialogueGenerator.Intent i : LocalDialogueGenerator.Intent.values()) {
            DialogueContext ctx = new DialogueContext(gf, null, DialogueTrigger.REPLY, vars);
            String line = LocalDialogueGenerator.reply(ctx, i, gf.data().companionId, gf.getRandom());
            h.assertTrue(line != null && !line.isBlank() && !line.contains("{"), "bad reply for " + i + ": " + line);
        }
        h.assertTrue(IntentClassifier.classify("I love you") == LocalDialogueGenerator.Intent.LOVE, "love intent");
        h.assertTrue(IntentClassifier.classify("how are you feeling?") == LocalDialogueGenerator.Intent.HOW_ARE_YOU, "how-are-you intent");
        h.assertTrue(IntentClassifier.classify("wait here") == LocalDialogueGenerator.Intent.COMMAND, "command intent");
        h.assertTrue(IntentClassifier.affection("can I have a hug?") == IntentClassifier.AffectionAsk.HUG, "hug ask");
        h.succeed();
    }

    // 10. talking without an AI provider still produces a reply, and it comes after the player's own line
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void talkingFallsBackToLocalDialogue(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        gf.controller().claim(owner);
        int before = gf.data().history.recent(64).size();
        DialogueService.playerSays(gf, owner, "Hey, how are you?");
        int now = gf.data().history.recent(64).size();
        h.assertTrue(now == before + 1, "her reply must wait for the player's chat line, got " + (now - before) + " new lines at once");
        h.assertTrue(gf.data().today.get(LogKey.TALKS) >= 1, "talk not counted");
        h.succeedWhen(() -> {
            var lines = gf.data().history.recent(64);
            h.assertTrue(lines.size() >= before + 2, "no reply yet");
            h.assertTrue(lines.get(before).fromPlayer() && !lines.get(before + 1).fromPlayer(), "reply order wrong");
            removePlayer(h, owner);
        });
    }

    // her requests can be answered with the chat buttons (/gf answer yes|no)
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void requestAnsweredFromChatButton(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        gf.controller().claim(owner);
        com.aigirlfriend.companion.RequestSystem.issue(gf, com.aigirlfriend.companion.RequestType.EXPLORE_TOGETHER, owner);
        h.assertTrue(gf.data().request != null && !gf.data().request.accepted, "request not issued");
        int before = gf.data().history.recent(64).size();
        owner.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "aigirlfriend answer yes");
        h.assertTrue(gf.data().request != null && gf.data().request.accepted, "yes button did not accept the request");
        h.assertTrue(gf.data().history.recent(64).get(before).fromPlayer(), "the player's answer is not in the conversation");
        h.succeedWhen(() -> {
            h.assertTrue(gf.data().history.recent(64).size() >= before + 2, "she did not react to the answer");
            removePlayer(h, owner);
        });
    }

    // 3. she notices hostile mobs and becomes afraid
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void detectsHostileMobs(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(2, 1, 2));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 5));
        // keep it from burning in daylight
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        h.succeedWhen(() -> h.assertTrue(gf.isScared() && gf.controller().fear.mainThreat == z, "not scared yet, fear=" + gf.getFear()));
    }

    // 5 + 6. needs and day evaluation give understandable causes
    @GameTest(template = "platform")
    public static void dailyMoodHasCauses(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        CompanionData d = gf.data();
        d.yesterday.reset(1);
        d.yesterday.add(LogKey.FRIGHTS, 4);
        d.yesterday.max(LogKey.MAX_FEAR, 0.95f);
        d.yesterday.add(LogKey.ALONE_SECONDS, 3000);
        d.yesterday.add(LogKey.ATTENTION_SUM, 10 * 100);
        d.yesterday.add(LogKey.ATTENTION_SAMPLES, 100);
        d.personality.confidence = 0.1f;
        d.personality.sensitivity = 0.9f;
        Mood bad = MoodEvaluator.evaluateDay(gf, 2);
        h.assertTrue(bad.isNegative(), "a lonely, scary day should give a negative mood, got " + bad);
        h.assertTrue(!d.mood.causes.isEmpty(), "mood must have causes");
        d.yesterday.reset(2);
        for (LogKey k : new LogKey[]{LogKey.TALKS, LogKey.HUGS, LogKey.KISSES, LogKey.CUDDLES, LogKey.GIFTS_LIKED, LogKey.FEEDINGS})
            d.yesterday.add(k, 3);
        d.yesterday.add(LogKey.ATTENTION_SUM, 85 * 100);
        d.yesterday.add(LogKey.ATTENTION_SAMPLES, 100);
        d.mood.wellbeing = 60;
        Mood good = MoodEvaluator.evaluateDay(gf, 3);
        h.assertTrue(!good.isNegative(), "a lovely day should give a positive mood, got " + good + " " + d.mood.causes);
        h.succeed();
    }

    // relationship progression needs variety and time
    @GameTest(template = "platform")
    public static void relationshipNeedsTimeAndVariety(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        var r = gf.data().relationship;
        float first = r.gain("hug", 10f, 1f, 5);
        float tenth = 0;
        for (int i = 0; i < 9; i++) tenth = r.gain("hug", 10f, 1f, 5);
        h.assertTrue(tenth < first * 0.3f, "repeated interactions must diminish");
        r.affection = 900f;
        r.trust = 90f;
        r.daysTogether = 0;
        r.refreshStage();
        h.assertTrue(!r.stage().isRomantic(), "romance must not be possible on day one");
        r.daysTogether = 3;
        r.refreshStage();
        h.assertTrue(r.stage().isRomantic(), "should be romantic now, was " + r.stage());
        h.succeed();
    }

    // sitting on a stair works and the seat cleans itself up
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void sitsOnStairs(GameTestHelper h) {
        floor(h);
        BlockPos stair = new BlockPos(4, 1, 4);
        h.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 6));
        h.assertTrue(SeatManager.sit(gf, h.absolutePos(stair), 0f) != null, "could not sit");
        h.assertTrue(gf.isSeated(), "not seated");
        SeatManager.standUp(gf);
        h.assertTrue(!gf.isPassenger(), "still riding");
        h.succeedWhen(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(com.aigirlfriend.entity.SeatEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(stair)).inflate(2)).isEmpty(), "seat not removed"));
    }

    // 4 + 5. a synchronized hug starts, positions her, completes and rewards
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void hugCompletes(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 5));
        ServerPlayer owner = player(h, new BlockPos(4, 1, 3));
        gf.controller().claim(owner);
        gf.data().relationship.forceStage(RelationshipStage.FRIENDSHIP);
        gf.controller().checkStage();
        boolean started = gf.interactions().start(InteractionType.HUG, owner, false);
        h.assertTrue(started, "hug did not start: " + gf.interactions().check(InteractionType.HUG, owner, false)
                + " player=" + owner.position() + " gf=" + gf.position() + " below=" + h.getLevel().getBlockState(owner.blockPosition().below())
                + " onGround=" + owner.onGround() + " level=" + (owner.level() == gf.level()) + " around=" + solidAround(h, owner.blockPosition())
                + " free=" + h.getLevel().noCollision(gf, gf.getBoundingBox().move(0, 0, -1.5)));
        h.assertTrue(gf.getSyncedInteraction() == InteractionType.HUG && gf.getSyncedPartnerId() == owner.getId(), "hug not synced");
        h.runAfterDelay(InteractionType.HUG.duration + 5, () -> {
            h.assertTrue(!gf.interactions().isActive(), "hug did not end");
            h.assertTrue(gf.getSyncedInteraction() == null, "sync not cleared");
            h.assertTrue(gf.data().today.get(LogKey.HUGS) >= 1, "hug not rewarded");
            h.assertTrue(gf.position().distanceTo(owner.position()) < 1.0, "not positioned for the hug");
            removePlayer(h, owner);
            h.succeed();
        });
    }

    // 4. carry and lap sitting mount and dismount cleanly; interrupting never leaves her stuck
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void carryAndLapSit(GameTestHelper h) {
        floor(h);
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 5));
        ServerPlayer owner = player(h, new BlockPos(4, 1, 3));
        gf.controller().claim(owner);
        gf.data().relationship.forceStage(RelationshipStage.ROMANTIC);
        gf.controller().checkStage();
        h.assertTrue(gf.interactions().start(InteractionType.CARRY, owner, false), "carry failed");
        h.assertTrue(gf.getVehicle() == owner && gf.isCarried(), "not carried");
        gf.interactions().stop(InteractionManager.StopReason.CANCELLED_PLAYER);
        h.assertTrue(!gf.isPassenger(), "still carried after stop");

        BlockPos stair = new BlockPos(2, 1, 2);
        h.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState());
        h.assertTrue(SeatManager.sit(owner, h.absolutePos(stair), 0f) != null, "owner could not sit");
        Vec3 near = h.absoluteVec(new Vec3(2.5, 1, 3.6));
        gf.moveTo(near.x, near.y, near.z);
        h.assertTrue(gf.interactions().start(InteractionType.LAP_SIT, owner, false), "lap sit failed: " + gf.interactions().check(InteractionType.LAP_SIT, owner, false));
        h.assertTrue(gf.isOnLap(), "not on lap");
        SeatManager.standUp(owner);
        h.runAfterDelay(3, () -> {
            h.assertTrue(!gf.interactions().isActive() && !gf.isPassenger(), "lap sit did not end when the owner stood up");
            removePlayer(h, owner);
            h.succeed();
        });
    }

    /** An owned companion at the given stage next to a survival-mode owner holding the given item. */
    private static GirlfriendEntity romanceSetup(GameTestHelper h, ServerPlayer owner, RelationshipStage stage, ItemStack held) {
        GirlfriendEntity gf = spawnCompanion(h, new BlockPos(4, 1, 4));
        gf.controller().claim(owner);
        gf.data().relationship.forceStage(stage);
        gf.controller().checkStage();
        owner.getAbilities().instabuild = false;
        owner.setItemInHand(InteractionHand.MAIN_HAND, held);
        return gf;
    }

    // proposing at DEEP_TRUST: she says yes, keeps the ring, and the marriage survives save/load
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void proposalMarriesAtDeepTrust(GameTestHelper h) {
        floor(h);
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        GirlfriendEntity gf = romanceSetup(h, owner, RelationshipStage.DEEP_TRUST, new ItemStack(ModItems.WEDDING_RING.get()));
        int linesBefore = gf.data().history.recent(64).size();
        gf.interact(owner, InteractionHand.MAIN_HAND);
        var r = gf.data().relationship;
        h.assertTrue(r.married && r.marriedDay >= 0, "she should have said yes");
        h.assertTrue(owner.getMainHandItem().isEmpty(), "the ring should now be hers");
        h.assertTrue(r.milestones.contains("married"), "marriage milestone missing");
        h.assertTrue(gf.data().memory.find(e -> e.type == MemoryType.MILESTONE && "married".equals(e.detail)) != null, "proposal memory missing");

        GirlfriendEntity copy = ModEntities.GIRLFRIEND.get().create(h.getLevel());
        h.assertTrue(copy != null, "could not create copy");
        copy.load(gf.saveWithoutId(new CompoundTag()));
        h.assertTrue(copy.data().relationship.married && copy.data().relationship.marriedDay == r.marriedDay, "marriage lost on save/load");
        h.assertTrue(new DialogueContext(copy, owner, DialogueTrigger.AFFECTIONATE, new HashMap<>()).married, "dialogue does not know she is married");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WEDDING_RING.get()));
        h.runAfterDelay(40, () -> {
            h.assertTrue(gf.data().history.recent(64).size() > linesBefore, "she never answered the proposal");
            gf.interact(owner, InteractionHand.MAIN_HAND);
            h.assertTrue(owner.getMainHandItem().getCount() == 1, "proposing again must keep the second ring");
            removePlayer(h, owner);
            h.succeed();
        });
    }

    // proposing while only ROMANTIC: a sweet "not yet", the ring stays with the player
    @GameTest(template = "platform")
    public static void proposalDeclinedWhenRomantic(GameTestHelper h) {
        floor(h);
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        GirlfriendEntity gf = romanceSetup(h, owner, RelationshipStage.ROMANTIC, new ItemStack(ModItems.WEDDING_RING.get()));
        gf.interact(owner, InteractionHand.MAIN_HAND);
        var r = gf.data().relationship;
        h.assertTrue(!r.married && !r.milestones.contains("married"), "she must not marry at ROMANTIC");
        h.assertTrue(owner.getMainHandItem().is(ModItems.WEDDING_RING.get()) && owner.getMainHandItem().getCount() == 1, "declined ring must be kept");
        h.assertTrue(gf.data().memory.find(e -> "proposal_early".equals(e.detail)) != null, "she should remember being asked");
        removePlayer(h, owner);
        h.succeed();
    }

    // /gf maxtier reaches PARTNERSHIP with real stats, keeps it through refreshes and losses, milestone once
    @GameTest(template = "platform")
    public static void maxTierCommandReachesPartnership(GameTestHelper h) {
        floor(h);
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        GirlfriendEntity gf = romanceSetup(h, owner, RelationshipStage.ACQUAINTANCE, ItemStack.EMPTY);
        var r = gf.data().relationship;
        h.getLevel().getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2).withSuppressedOutput(), "gf maxtier");
        h.assertTrue(r.stage() == RelationshipStage.PARTNERSHIP && gf.getSyncedStage() == RelationshipStage.PARTNERSHIP, "not at the top stage: " + r.stage());
        var milestone = gf.data().memory.find(e -> "stage_PARTNERSHIP".equals(e.detail));
        h.assertTrue(milestone != null && milestone.count == 1, "milestone should play exactly once");
        r.refreshStage();
        gf.controller().lose(10f, 6f);
        h.assertTrue(r.stage() == RelationshipStage.PARTNERSHIP, "stage did not stick: " + r.stage());
        h.assertTrue(!gf.controller().reachMaxStage(), "a second maxtier should report she was already there");
        h.assertTrue(milestone.count == 1, "milestone repeated");
        removePlayer(h, owner);
        h.succeed();
    }

    // hand-made gifts are loved and consumed; chocolates count as a present (not plain feeding) but still fill her up
    @GameTest(template = "platform")
    public static void romanticGiftsAreLoved(GameTestHelper h) {
        floor(h);
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        GirlfriendEntity gf = romanceSetup(h, owner, RelationshipStage.FRIENDSHIP, new ItemStack(ModItems.LOVE_LETTER.get()));
        CompanionData d = gf.data();
        float affection = d.relationship.affection;
        gf.interact(owner, InteractionHand.MAIN_HAND);
        h.assertTrue(owner.getMainHandItem().isEmpty(), "love letter not accepted");
        h.assertTrue(d.today.get(LogKey.GIFTS_LIKED) >= 1 && d.relationship.affection > affection + 8f, "love letter should count like a favourite gift");
        h.assertTrue(d.relationship.milestones.contains("love_letter"), "love letter keepsake memory missing");

        d.needs.hunger = 60f;
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BOX_OF_CHOCOLATES.get()));
        gf.interact(owner, InteractionHand.MAIN_HAND);
        h.assertTrue(owner.getMainHandItem().isEmpty(), "chocolates not accepted");
        h.assertTrue(d.today.get(LogKey.GIFTS_LIKED) >= 2 && d.today.get(LogKey.FEEDINGS) == 0, "chocolates should be a gift, not plain food");
        h.assertTrue(d.needs.hunger < 60f, "she should have eaten some chocolates");
        removePlayer(h, owner);
        h.succeed();
    }

    // every romance item can be crafted in survival; the ring recipe matches gold + diamond
    @GameTest(template = "platform")
    public static void romanceItemsAreCraftable(GameTestHelper h) {
        floor(h);
        var recipes = h.getLevel().getRecipeManager();
        for (var item : ModItems.romanceItems()) {
            h.assertTrue(recipes.byKey(item.getId()).isPresent(), "no recipe for " + item.getId());
        }
        ItemStack gold = new ItemStack(Items.GOLD_INGOT), diamond = new ItemStack(Items.DIAMOND);
        CraftingInput grid = CraftingInput.of(3, 3, java.util.List.of(
                ItemStack.EMPTY, diamond, ItemStack.EMPTY,
                gold, ItemStack.EMPTY, gold,
                ItemStack.EMPTY, gold, ItemStack.EMPTY));
        var ring = recipes.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(ring.isPresent() && ring.get().value().getResultItem(h.getLevel().registryAccess()).is(ModItems.WEDDING_RING.get()), "ring recipe does not match");
        h.succeed();
    }

    // duplicate prevention: one companion per player by default
    @GameTest(template = "platform")
    public static void spawnEggLimit(GameTestHelper h) {
        floor(h);
        ServerPlayer owner = player(h, new BlockPos(2, 1, 2));
        var egg = ModItems.GIRLFRIEND_SPAWN_EGG.get();
        boolean first = egg.spawnFor(h.getLevel(), owner, h.absolutePos(new BlockPos(4, 1, 4)), new ItemStack(egg));
        boolean second = egg.spawnFor(h.getLevel(), owner, h.absolutePos(new BlockPos(5, 1, 5)), new ItemStack(egg));
        h.assertTrue(first, "first spawn failed");
        h.assertTrue(!second, "second companion should be refused");
        removePlayer(h, owner);
        h.succeed();
    }
}
