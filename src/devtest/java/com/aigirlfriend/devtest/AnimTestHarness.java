package com.aigirlfriend.devtest;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.companion.Expression;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.GirlfriendAnimations;
import com.aigirlfriend.interaction.Gesture;
import com.aigirlfriend.interaction.InteractionManager.StopReason;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.registry.ModEntities;
import com.aigirlfriend.seat.SeatManager;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Scripted visual test run (see {@link AnimTestHarnessMod}). Screenshots go to run/animtest/screenshots/animtest. */
public final class AnimTestHarness {
    private static final Set<String> REMOVED_GOALS = Set.of("AffectionInitiativeGoal", "IdleGestureGoal", "SeekBedGoal", "ReturnHomeGoal",
            "FollowOwnerCompanionGoal", "CompanionLookGoal", "StayPutGoal", "SitWithOwnerGoal", "RandomLookAroundGoal");

    enum Anchor { HER, PAIR, PLAYER, BED, BED2, BENCH }

    /** A camera placement: angle around the anchor's facing (0 = in front, +90 = its right side), distance and heights. */
    record View(Anchor anchor, float angle, float dist, float camH, float focusH) {}

    record Cap(int tick, String view) {}

    static final class Shot {
        final String name;
        final Consumer<AnimTestHarness> setup;
        @Nullable BiConsumer<AnimTestHarness, Integer> perTick;
        @Nullable BiConsumer<AnimTestHarness, Integer> perClientTick;
        final List<Cap> caps = new ArrayList<>();
        int clientFlags;
        boolean gui;

        Shot(String name, Consumer<AnimTestHarness> setup) {
            this.name = name;
            this.setup = setup;
        }

        Shot cap(int tick, String view) {
            caps.add(new Cap(tick, view));
            return this;
        }

        Shot caps(String view, int... ticks) {
            for (int t : ticks) caps.add(new Cap(t, view));
            return this;
        }

        Shot tick(BiConsumer<AnimTestHarness, Integer> perTick) {
            this.perTick = perTick;
            return this;
        }

        Shot flags(int flags) {
            this.clientFlags = flags;
            return this;
        }

        /** Chat / name tag / speech bubble checks: GUI and bubbles visible. */
        Shot gui() {
            this.gui = true;
            return this;
        }

        Shot clientTick(BiConsumer<AnimTestHarness, Integer> perClientTick) {
            this.perClientTick = perClientTick;
            return this;
        }
    }

    private static final Map<String, View> VIEWS = new LinkedHashMap<>();

    static {
        VIEWS.put("front", new View(Anchor.HER, 28, 2.4f, 1.25f, 0.92f));
        VIEWS.put("front0", new View(Anchor.HER, 0, 2.4f, 1.15f, 0.92f));
        VIEWS.put("side", new View(Anchor.HER, 90, 2.4f, 1.0f, 0.9f));
        VIEWS.put("lside", new View(Anchor.HER, -90, 2.4f, 1.0f, 0.9f));
        VIEWS.put("back", new View(Anchor.HER, 155, 2.4f, 1.3f, 0.92f));
        VIEWS.put("face", new View(Anchor.HER, 8, 1.45f, 1.5f, 1.42f));
        VIEWS.put("pair_side", new View(Anchor.PAIR, 90, 2.8f, 1.2f, 0.95f));
        VIEWS.put("pair_lside", new View(Anchor.PAIR, -90, 2.8f, 1.2f, 0.95f));
        VIEWS.put("pair_her", new View(Anchor.PAIR, 62, 2.6f, 1.5f, 1.1f));
        VIEWS.put("pair_him", new View(Anchor.PAIR, 118, 2.6f, 1.5f, 1.1f));
        VIEWS.put("pair_top", new View(Anchor.PAIR, 60, 1.9f, 2.8f, 0.8f));
        VIEWS.put("ply_front", new View(Anchor.PLAYER, 25, 2.7f, 1.5f, 1.05f));
        VIEWS.put("ply_side", new View(Anchor.PLAYER, 90, 2.7f, 1.2f, 1.05f));
        VIEWS.put("ply_lside", new View(Anchor.PLAYER, -90, 2.7f, 1.2f, 1.05f));
        VIEWS.put("ply_back", new View(Anchor.PLAYER, 160, 2.7f, 1.5f, 1.05f));
        VIEWS.put("bench_front", new View(Anchor.BENCH, 20, 3.0f, 1.4f, 0.75f));
        VIEWS.put("bench_side", new View(Anchor.BENCH, 90, 2.8f, 1.1f, 0.75f));
        VIEWS.put("bench_back", new View(Anchor.BENCH, 160, 2.8f, 1.7f, 0.85f));
        VIEWS.put("bed_side", new View(Anchor.BED, 90, 2.6f, 1.8f, 0.55f));
        VIEWS.put("bed_lside", new View(Anchor.BED, -90, 2.6f, 1.8f, 0.55f));
        VIEWS.put("bed_top", new View(Anchor.BED, 179, 0.5f, 3.0f, 0.5f));
        VIEWS.put("bed_foot", new View(Anchor.BED, 0, 2.8f, 2.0f, 0.55f));
        VIEWS.put("beds_top", new View(Anchor.BED2, 179, 0.5f, 3.4f, 0.5f));
        VIEWS.put("beds_foot", new View(Anchor.BED2, 0, 3.0f, 2.2f, 0.55f));
        VIEWS.put("talk", new View(Anchor.HER, 15, 3.4f, 1.7f, 1.55f));
        VIEWS.put("aim", new View(Anchor.HER, 0, 3.0f, 1.5f, 1.2f)); // crosshair on her, room above her head
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Shot> shots = new ArrayList<>();
    private final List<String> index = new ArrayList<>();
    private Path out;
    private int ticks, frames;
    private boolean worldRequested, setupQueued;
    private volatile boolean setupDone;
    private int readyAt = -1, nextShotAt;
    private int shotIdx = -1, shotStart, capIdx, camSetFrame = -1;
    @Nullable private Shot shot;
    @Nullable private String only = System.getProperty("aigirlfriend.animtest.only");

    // server-side scene (used on the server thread only, ids are read by the client)
    private ServerLevel level;
    private GirlfriendEntity gf;
    private ServerPlayer tp;
    private volatile int gfId = -1, tpId = -1;
    private BlockPos origin, bench, chair, bedHead, bed2Head;
    /** Desired body yaw of the test player (mock players do not turn their body on their own). */
    private volatile float tpBodyYaw;
    private volatile boolean tpBodyFixed = true;

    public AnimTestHarness() {
        buildShots();
    }

    // ------------------------------------------------------------------ driver

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        ticks++;
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && ticks > 40 && (mc.screen instanceof TitleScreen || ticks > 600)) {
                worldRequested = true;
                createWorld();
            }
            return;
        }
        if (mc.screen != null && readyAt >= 0 && ticks > readyAt - 40) mc.setScreen(null);
        mc.options.hideGui = shot == null || !shot.gui;
        if (readyAt < 0) {
            readyAt = ticks + 80;
            return;
        }
        if (!setupQueued) {
            if (ticks < readyAt) return;
            setupQueued = true;
            server().execute(this::setupWorld);
            nextShotAt = ticks + 60;
            return;
        }
        if (!setupDone) return;
        fixClientEntities();
        if (shot == null) {
            if (ticks >= nextShotAt) startNextShot();
            return;
        }
        int t = ticks - shotStart;
        Shot s = shot;
        if (s.perClientTick != null && t >= 0) s.perClientTick.accept(this, t);
        server().execute(() -> {
            keepStable();
            if (s.perTick != null && t >= 0) s.perTick.accept(this, t);
        });
        if (capIdx < s.caps.size() && t >= s.caps.get(capIdx).tick()) {
            placeCamera(VIEWS.get(s.caps.get(capIdx).view()));
            if (camSetFrame < 0) camSetFrame = frames;
        }
    }

    @SubscribeEvent
    public void onFrame(RenderFrameEvent.Post event) {
        frames++;
        if (shot != null && extraCapAt >= 0 && frames >= extraCapAt) {
            extraCapAt = -1;
            grab(String.format("%s__reply%d_t%d", shot.name, ++extraCaps, ticks - shotStart));
        }
        if (shot == null || camSetFrame < 0 || frames < camSetFrame + 3) return;
        Cap cap = shot.caps.get(capIdx);
        grab(String.format("%s__%02d_%s_t%d", shot.name, capIdx, cap.view(), ticks - shotStart));
        camSetFrame = -1;
        capIdx++;
        if (capIdx >= shot.caps.size()) {
            AIGirlfriend.LOGGER.info("[animtest] finished {}", shot.name);
            shot = null;
            nextShotAt = ticks + 2;
        }
    }

    private MinecraftServer server() {
        return mc.getSingleplayerServer();
    }

    private void startNextShot() {
        do {
            shotIdx++;
        } while (shotIdx < shots.size() && only != null && !shots.get(shotIdx).name.matches(only));
        if (shotIdx >= shots.size()) {
            finish();
            return;
        }
        Shot s = shots.get(shotIdx);
        AIGirlfriend.LOGGER.info("[animtest] shot {} ({}/{})", s.name, shotIdx + 1, shots.size());
        server().execute(() -> {
            try {
                reset();
                s.setup.accept(this);
            } catch (Throwable e) {
                AIGirlfriend.LOGGER.error("[animtest] setup of {} failed", s.name, e);
            }
        });
        shot = s;
        capIdx = 0;
        camSetFrame = -1;
        shotStart = ticks + 1;
        GFClientConfig.SPEECH_BUBBLES.set(s.gui);
        if (s.gui) mc.gui.getChat().clearMessages(false);
    }

    /** Logs every chat line the client receives (with the shot tick) so the reply order can be checked. */
    @SubscribeEvent
    public void onChat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent event) {
        if (shot == null) return;
        index.add(String.format("CHAT %s t=%d %s: %s", shot.name, ticks - shotStart, event.isSystem() ? "system" : "player", event.getMessage().getString()));
        if (!event.isSystem() || event.getMessage().getString().startsWith("<" + gfName + ">")) chatLines++;
        if (event.getMessage().getString().startsWith("<" + gfName + ">")) {
            herLines++;
            lastHerAt = ticks - shotStart;
            if (shot.gui) extraCapAt = frames + 20; // show the chat and her bubble right after she answers
        }
    }

    private volatile String gfName = "";
    private int chatLines, herLines, sent, lastSentAt, lastHerAt, extraCapAt = -1, extraCaps;

    /** The camera player (the real client) says something in chat, through the normal chat pipeline. */
    void clientChat(String text) {
        if (mc.player != null) mc.player.connection.sendChat(text);
        AIGirlfriend.LOGGER.info("[animtest] client says: {}", text);
    }

    /**
     * Makes the camera player (the real client) her owner so its chat reaches her. With {@code ai} the built-in brain
     * is used (it must be in run/animtest/aigirlfriend/models), otherwise scripted lines only.
     */
    private void chatSetup(boolean ai) {
        ServerPlayer viewer = null;
        for (ServerPlayer p : server().getPlayerList().getPlayers()) if (p != tp) viewer = p;
        if (viewer == null) return;
        gf.setOwner(viewer.getUUID());
        gf.setCustomName(net.minecraft.network.chat.Component.literal("Iris")); // the test lines call her Iris
        gf.data().history.clear();
        com.aigirlfriend.config.GFConfig.AI_BRAIN.set(ai ? com.aigirlfriend.config.GFConfig.Brain.BUILTIN : com.aigirlfriend.config.GFConfig.Brain.OFF);
        if (ai) com.aigirlfriend.ai.BuiltinBrain.ensureStarted(true);
        gfName = gf.data().name;
        placeHer(origin.getX() + 0.5, origin.getZ() + 0.5, 0f);
    }

    /** Sends the lines one by one, each shortly after her answer to the previous one (or after a timeout). */
    private void converse(int t, String... lines) {
        if (t < 10 || sent >= lines.length) return;
        boolean answered = herLines >= sent && t >= lastHerAt + 40;
        if (sent == 0 || answered || t >= lastSentAt + 1200) {
            clientChat(lines[sent++]);
            lastSentAt = t;
        }
    }

    private void finish() {
        try {
            Files.write(out.resolve("index.txt"), index);
        } catch (IOException e) {
            AIGirlfriend.LOGGER.error("[animtest] could not write index", e);
        }
        AIGirlfriend.LOGGER.info("[animtest] DONE: {} screenshots in {}", index.size(), out);
        mc.stop();
    }

    // ------------------------------------------------------------------ world

    private void createWorld() {
        out = mc.gameDirectory.toPath().resolve("screenshots/animtest");
        try {
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        String name = "animtest-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
        GFClientConfig.SPEECH_BUBBLES.set(false);
        GFClientConfig.SHOW_HUD.set(false);
        mc.options.bobView().set(false);
    }

    private void setupWorld() {
        MinecraftServer server = server();
        level = server.overworld();
        level.setDayTime(6000);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        origin = new BlockPos(0, y, 0);
        // neutral floor
        for (int x = -16; x <= 16; x++) for (int z = -10; z <= 14; z++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        // bench: two stairs facing north (seated people face south)
        bench = origin.offset(8, 0, 0);
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        level.setBlock(bench, stair, 2);
        level.setBlock(bench.east(), stair, 2);
        // single chair for lap sitting
        chair = origin.offset(13, 0, 0);
        level.setBlock(chair, stair, 2);
        // bed (head north) and a pair of beds
        bedHead = origin.offset(-7, 0, -1);
        placeBed(bedHead, Direction.NORTH);
        bed2Head = origin.offset(-12, 0, -1);
        placeBed(bed2Head, Direction.NORTH);
        placeBed(bed2Head.west(), Direction.NORTH);

        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.nameUUIDFromBytes("TestPlayer".getBytes()), "TestPlayer"), false);
        tp = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, tp, cookie);
        tp.setGameMode(GameType.CREATIVE);
        movePlayer(origin.getX() + 0.5, y, origin.getZ() + 2.0, 180f);

        gf = ModEntities.GIRLFRIEND.get().create(level);
        gf.moveTo(origin.getX() + 0.5, y, origin.getZ() + 0.8, 0f, 0f);
        level.addFreshEntity(gf);
        gf.ensureInitialized();
        gf.controller().claim(tp);
        gf.data().relationship.forceStage(RelationshipStage.PARTNERSHIP);
        gf.controller().checkStage();
        gf.data().personality.shyness = 0f;
        gf.goalSelector.removeAllGoals(g -> REMOVED_GOALS.contains(g.getClass().getSimpleName()));
        gfId = gf.getId();
        tpId = tp.getId();
        // the AI chat shot needs the built-in brain: start its (first-run) download / load right away
        if (only == null || "ui_chat_ai".matches(only)) com.aigirlfriend.ai.BuiltinBrain.ensureStarted(true);
        setupDone = true;
        AIGirlfriend.LOGGER.info("[animtest] scene ready at {} (gf {}, player {})", origin, gfId, tpId);
    }

    private void placeBed(BlockPos head, Direction facing) {
        BlockState s = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        level.setBlock(head.relative(facing.getOpposite()), s.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(head, s.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Puts both characters back into a neutral state between shots. */
    private void reset() {
        for (int i = 0; i < 3 && gf.interactions().isActive(); i++) gf.interactions().stop(StopReason.REPLACED);
        gf.interactions().clearCooldowns();
        if (gf.isSleeping()) gf.stopSleeping();
        SeatManager.standUp(gf);
        if (gf.isPassenger()) gf.stopRiding();
        if (tp.isSleeping()) tp.stopSleeping();
        SeatManager.standUp(tp);
        if (tp.isPassenger()) tp.stopRiding();
        tp.ejectPassengers();
        gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
        gf.setFlag(GirlfriendEntity.FLAG_FAINTED, false);
        gf.controller().debugExpression = null;
        gf.getNavigation().stop();
        gf.setDeltaMovement(Vec3.ZERO);
        gf.setOutfit(0);
        tpBodyFixed = true;
        gf.setOwner(tp.getUUID());
        clearLeaves();
        chatLines = 0;
        herLines = 0;
        sent = 0;
        lastSentAt = 0;
        lastHerAt = 0;
        // park the player out of view unless the shot needs him
        movePlayer(origin.getX() + 0.5 + 30, origin.getY(), origin.getZ() + 30.5, 180f);
        placeHer(origin.getX() + 0.5, origin.getZ() + 0.5, 0f);
    }

    private void leafWall() {
        // a band of leaves between the camera and her head / bubble, leaving her body visible
        for (int x = -3; x <= 3; x++) for (int y = 2; y <= 4; y++) {
            level.setBlock(origin.offset(x, y, 2), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
        }
    }

    private void clearLeaves() {
        for (int x = -3; x <= 3; x++) for (int y = 2; y <= 4; y++) {
            if (level.getBlockState(origin.offset(x, y, 2)).is(Blocks.OAK_LEAVES)) level.setBlock(origin.offset(x, y, 2), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void keepStable() {
        if (gf == null) return;
        gf.data().needs.energy = 90f;
        gf.data().needs.hunger = 10f;
        gf.data().needs.attention = 85f;
        if (!tp.isPassenger() && !tp.isSleeping()) tp.setOnGround(true);
    }

    void placeHer(double x, double z, float yaw) {
        gf.moveTo(x, origin.getY(), z, yaw, 0f);
        gf.setYBodyRot(yaw);
        gf.setYHeadRot(yaw);
        gf.setDeltaMovement(Vec3.ZERO);
    }

    void movePlayer(double x, double y, double z, float yaw) {
        tp.moveTo(x, y, z, yaw, 0f);
        tp.setYHeadRot(yaw);
        tp.setYBodyRot(yaw);
        tp.setOnGround(true);
        tpBodyYaw = yaw;
    }

    /** Player in front of the stage, facing her (she stands at the origin facing south). */
    void playerFacingHer() {
        placeHer(origin.getX() + 0.5, origin.getZ() + 0.5, 0f);
        movePlayer(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.6, 180f);
    }

    void start(InteractionType type, boolean self) {
        boolean ok = gf.interactions().start(type, tp, self);
        if (!ok) AIGirlfriend.LOGGER.warn("[animtest] {} did not start: {}", type, gf.interactions().check(type, tp, self));
    }

    /** Client side: mock players never turn their body, and forced animation flags. */
    private void fixClientEntities() {
        if (mc.level == null) return;
        Entity p = mc.level.getEntity(tpId);
        if (p instanceof net.minecraft.world.entity.LivingEntity le && tpBodyFixed && !p.isPassenger()) {
            le.yBodyRot = tpBodyYaw;
            le.yBodyRotO = tpBodyYaw;
            le.setYHeadRot(tpBodyYaw);
            le.yHeadRotO = tpBodyYaw;
        }
        if (gf != null && mc.level.getEntity(gfId) instanceof GirlfriendEntity cg) {
            int want = gf.getFlags() | (shot != null ? shot.clientFlags : 0);
            for (int bit = 0; bit < 16; bit++) cg.setFlag(1 << bit, (want & (1 << bit)) != 0);
        }
    }

    // ------------------------------------------------------------------ camera

    private void placeCamera(View v) {
        Entity her = mc.level.getEntity(gfId);
        Entity him = mc.level.getEntity(tpId);
        if (her == null || mc.player == null) return;
        Vec3 base;
        float yaw;
        switch (v.anchor()) {
            case PAIR -> {
                base = him == null ? her.position() : her.position().add(him.position()).scale(0.5);
                yaw = her instanceof GirlfriendEntity g ? g.yBodyRot : her.getYRot();
            }
            case PLAYER -> {
                base = him == null ? her.position() : him.position();
                yaw = him instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : 0f;
            }
            case BED -> {
                base = Vec3.atBottomCenterOf(bedHead).add(0, 0, 0.5);
                yaw = 0f; // bed head points north; "front" = looking from the foot end
            }
            case BED2 -> {
                base = Vec3.atBottomCenterOf(bed2Head).add(-0.5, 0, 0.5);
                yaw = 0f;
            }
            case BENCH -> {
                base = Vec3.atBottomCenterOf(bench).add(0.5, 0, 0);
                yaw = 0f; // seated people face south
            }
            default -> {
                base = her.position();
                yaw = her instanceof GirlfriendEntity g ? g.yBodyRot : her.getYRot();
            }
        }
        float a = (yaw + v.angle()) * Mth.DEG_TO_RAD;
        Vec3 focus = base.add(0, v.focusH(), 0);
        Vec3 cam = new Vec3(base.x - Mth.sin(a) * v.dist(), base.y + v.camH(), base.z + Mth.cos(a) * v.dist());
        Vec3 d = focus.subtract(cam);
        float camYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float camPitch = (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        double eye = mc.player.getEyeHeight();
        mc.player.moveTo(cam.x, cam.y - eye, cam.z, camYaw, camPitch);
        mc.player.setOldPosAndRot();
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYHeadRot(camYaw);
        mc.player.yHeadRotO = camYaw;
    }

    private void grab(String name) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path file = out.resolve(name + ".png");
            img.writeToFile(file);
            String info = "";
            if (mc.level.getEntity(gfId) instanceof GirlfriendEntity g) {
                info = String.format(" | pose=%s interaction=%s gesture=%s flags=%d outfit=%d expr=%s main=%s vel=%.3f",
                        g.getPose(), g.getSyncedInteraction(), g.getGesture(), g.getFlags(), g.getOutfit(), g.getExpression(),
                        currentMain(g), g.getDeltaMovement().horizontalDistance());
                if (gf != null) info += String.format(" serverVel=%.3f gap=%.2f", gf.getDeltaMovement().horizontalDistance(), gf.distanceTo(tp));
            }
            if (mc.level.getEntity(tpId) instanceof net.minecraft.world.entity.player.Player pl) {
                info += String.format(" | player pose=%s vehicle=%s body=%.0f head=%.0f pitch=%.0f",
                        pl.getPose(), pl.getVehicle() == null ? "-" : pl.getVehicle().getType().toShortString(), pl.yBodyRot, pl.getYHeadRot(), pl.getXRot());
                if (mc.getEntityRenderDispatcher().getRenderer(pl) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> lr
                        && lr.getModel() instanceof net.minecraft.client.model.HumanoidModel<?> hm) {
                    info += String.format(" model body=(%.0f,%.0f,%.0f) head=(%.0f,%.0f,%.0f)",
                            hm.body.xRot * Mth.RAD_TO_DEG, hm.body.yRot * Mth.RAD_TO_DEG, hm.body.zRot * Mth.RAD_TO_DEG,
                            hm.head.xRot * Mth.RAD_TO_DEG, hm.head.yRot * Mth.RAD_TO_DEG, hm.head.zRot * Mth.RAD_TO_DEG);
                }
            }
            index.add(name + info);
        } catch (IOException e) {
            AIGirlfriend.LOGGER.error("[animtest] screenshot failed", e);
        }
    }

    private static String currentMain(GirlfriendEntity g) {
        StringBuilder sb = new StringBuilder();
        g.getAnimatableInstanceCache().getManagerForId(g.getId()).getAnimationControllers().forEach((name, c) -> {
            if (name.equals("main")) {
                sb.append(c.getCurrentAnimation() == null ? "-" : c.getCurrentAnimation().animation().name());
            } else if (c.getCurrentAnimation() != null && c.getAnimationState() != software.bernie.geckolib.animation.AnimationController.State.STOPPED) {
                sb.append(" ").append(name).append(":").append(c.getCurrentAnimation().animation().name());
            }
        });
        return sb.toString();
    }

    // ------------------------------------------------------------------ the script

    private Shot shot(String name, Consumer<AnimTestHarness> setup) {
        Shot s = new Shot(name, setup);
        shots.add(s);
        return s;
    }

    private void buildShots() {
        // outfits
        for (int o = 0; o < 3; o++) {
            int outfit = o;
            shot("outfit" + o, h -> h.gf.setOutfit(outfit)).cap(20, "front0").cap(24, "front").cap(28, "side").cap(32, "back").cap(36, "lside").cap(40, "face");
        }
        // idle variants (flags forced on the client)
        shot("idle", h -> {}).caps("front", 10, 30, 50, 70).cap(80, "side");
        shot("idle_tired", h -> {}).flags(GirlfriendEntity.FLAG_TIRED).caps("front", 10, 40, 70).cap(80, "side");
        shot("idle_sad", h -> {}).flags(GirlfriendEntity.FLAG_SAD).caps("front", 10, 40, 70).cap(80, "side");
        shot("idle_scared", h -> {}).flags(GirlfriendEntity.FLAG_SCARED).caps("front", 10, 40, 70).cap(80, "side");
        shot("hide", h -> {}).flags(GirlfriendEntity.FLAG_HIDING).caps("front", 10, 40).cap(60, "side");
        // locomotion
        shot("walk", h -> h.placeHer(h.origin.getX() - 5.5, h.origin.getZ() + 5.5, -90f))
                .tick((h, t) -> h.walkBackAndForth(0.7))
                .caps("side", 30, 34, 38, 42, 46).cap(52, "front");
        shot("run", h -> h.placeHer(h.origin.getX() - 7.5, h.origin.getZ() + 5.5, -90f))
                .tick((h, t) -> h.walkBackAndForth(1.6))
                .caps("side", 20, 23, 26, 29, 32).cap(36, "front");
        // seated, ground sitting, fainted, sleeping
        shot("sit_bench", h -> SeatManager.sit(h.gf, h.bench, 180f)).caps("bench_front", 20, 60).cap(70, "bench_side").cap(80, "bench_back");
        shot("sit_ground", h -> h.gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, true)).caps("front", 20, 60).cap(70, "side");
        shot("fainted", h -> {}).flags(GirlfriendEntity.FLAG_FAINTED).caps("front", 20, 50).cap(60, "side");
        shot("sleep_alone", h -> {
            h.placeHer(h.bedHead.getX() + 0.5, h.bedHead.getZ() + 2.5, 180f);
            h.gf.data().needs.energy = 20f; // it is noon in the test world: a rested companion would get up again
            h.gf.startSleeping(h.bedHead);
        }).tick((h, t) -> h.gf.data().needs.energy = 20f).caps("bed_side", 2, 5, 8, 11, 14, 17, 20, 23, 26, 30, 34).cap(60, "bed_top").cap(70, "bed_foot").cap(80, "bed_lside");
        shot("wake_in_bed", h -> {
            h.placeHer(h.bedHead.getX() + 0.5, h.bedHead.getZ() + 2.5, 180f);
            h.gf.data().needs.energy = 20f;
            h.gf.startSleeping(h.bedHead);
        }).tick((h, t) -> {
            if (t < 40) h.gf.data().needs.energy = 20f;
            if (t == 40) h.gf.controller().beginWakeUp(false);
        }).cap(36, "bed_side").cap(44, "bed_side").cap(52, "bed_side").cap(60, "bed_side").cap(68, "bed_side").cap(76, "bed_side")
                .cap(84, "bed_side").cap(96, "bed_side").cap(110, "bed_side");
        // gestures
        for (Gesture g : Gesture.values()) {
            if (g == Gesture.NONE) continue;
            Shot s = shot("gesture_" + g.name().toLowerCase(), h -> h.gf.playGesture(g));
            int d = g.duration;
            s.cap(d / 5, "front").cap(2 * d / 5, "front").cap(d / 2, "side").cap(3 * d / 5, "front").cap(4 * d / 5, "front").cap(d + 6, "front");
        }
        // expressions
        for (Expression e : Expression.values()) {
            shot("expr_" + e.name().toLowerCase(), h -> h.gf.controller().debugExpression = e).caps("face", 10, 14);
        }
        // timed face-to-face interactions
        for (InteractionType type : new InteractionType[]{InteractionType.HUG, InteractionType.KISS, InteractionType.KISS_CHEEK,
                InteractionType.FOREHEAD_KISS, InteractionType.CUDDLE, InteractionType.BACK_HUG, InteractionType.LEAN_ON,
                InteractionType.PET_HAIR, InteractionType.PET_CHEEK}) {
            Shot s = shot("ix_" + type.name().toLowerCase(), h -> {
                h.playerFacingHer();
                h.start(type, false);
            });
            int d = Math.min(type.duration, 100);
            s.cap(4, "pair_side").cap(10, "pair_side").cap(d / 4, "pair_her").cap(d / 3, "pair_side").cap(d / 2, "pair_him")
                    .cap(2 * d / 3, "pair_side").cap(3 * d / 4, "pair_lside").cap(d - 6, "pair_her").cap(d + 10, "pair_side");
        }
        // hold hands standing still and walking
        shot("ix_hold_hands", h -> {
            h.movePlayer(h.origin.getX() + 0.5, h.origin.getY(), h.origin.getZ() + 0.5, 180f);
            h.placeHer(h.origin.getX() + 1.3, h.origin.getZ() + 0.5, 180f);
            h.start(InteractionType.HOLD_HANDS, false);
        }).caps("ply_front", 20, 40).cap(50, "ply_back").cap(60, "ply_side");
        shot("ix_hold_hands_walk", h -> {
            h.movePlayer(h.origin.getX() + 0.5, h.origin.getY(), h.origin.getZ() + 8.5, -90f);
            h.placeHer(h.origin.getX() + 0.5, h.origin.getZ() + 7.7, -90f);
            h.start(InteractionType.HOLD_HANDS, false);
        }).tick((h, t) -> {
            if (t > 10 && t < 70) h.movePlayer(h.tp.getX() + 0.216, h.tp.getY(), h.tp.getZ(), -90f);
            if (Boolean.getBoolean("aigirlfriend.animtest.debugwalk")) {
                StringBuilder goals = new StringBuilder();
                h.gf.goalSelector.getAvailableGoals().forEach(w -> { if (w.isRunning()) goals.append(w.getGoal().getClass().getSimpleName()).append(','); });
                AIGirlfriend.LOGGER.info("[walk] t={} gfX={} tpX={} vel={} done={} goals={}", t, String.format("%.2f", h.gf.getX()), String.format("%.2f", h.tp.getX()),
                        String.format("%.3f", h.gf.getDeltaMovement().horizontalDistance()), h.gf.getNavigation().isDone(), goals);
            }
        }).caps("ply_front", 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64).cap(72, "ply_back").cap(84, "ply_front");
        // seated interactions on the bench
        for (InteractionType type : new InteractionType[]{InteractionType.SIT_TOGETHER, InteractionType.HEAD_ON_SHOULDER, InteractionType.CUDDLE_SITTING}) {
            shot("ix_" + type.name().toLowerCase(), h -> {
                h.placeHer(h.bench.getX() + 2.5, h.bench.getZ() + 1.5, 180f);
                SeatManager.sit(h.tp, h.bench, 180f);
                h.tpBodyFixed = false;
                h.start(InteractionType.SIT_TOGETHER, false);
                if (type != InteractionType.SIT_TOGETHER) h.start(type, false);
            }).cap(20, "bench_front").cap(40, "bench_front").cap(50, "bench_side").cap(60, "bench_back").cap(70, "pair_top");
        }
        // carry and lap sitting
        shot("ix_carry", h -> {
            h.movePlayer(h.origin.getX() + 0.5, h.origin.getY(), h.origin.getZ() + 1.5, 180f);
            h.placeHer(h.origin.getX() + 0.5, h.origin.getZ() + 0.5, 0f);
            h.start(InteractionType.CARRY, false);
        }).caps("ply_front", 20, 40).cap(50, "ply_side").cap(60, "ply_lside").cap(70, "ply_back");
        shot("ix_lap_sit", h -> {
            h.placeHer(h.chair.getX() + 0.5, h.chair.getZ() + 2.0, 180f);
            SeatManager.sit(h.tp, h.chair, 180f);
            h.tpBodyFixed = false;
            h.start(InteractionType.LAP_SIT, false);
        }).cap(20, "ply_front").cap(40, "ply_front").cap(50, "ply_side").cap(60, "ply_lside").cap(70, "ply_back");
        // bed
        shot("ix_cuddle_bed", h -> {
            h.movePlayer(h.bedHead.getX() + 1.5, h.origin.getY(), h.bedHead.getZ() + 1.0, 180f);
            h.placeHer(h.bedHead.getX() + 1.5, h.bedHead.getZ() + 2.0, 180f);
            h.tpBodyFixed = false;
            h.start(InteractionType.CUDDLE_BED, false);
        }).cap(4, "bed_side").cap(14, "bed_side").cap(24, "bed_side").cap(40, "bed_side").cap(60, "bed_top").cap(70, "bed_foot").cap(80, "bed_lside");
        shot("ix_sleep_together", h -> {
            h.movePlayer(h.bedHead.getX() + 1.5, h.origin.getY(), h.bedHead.getZ() + 1.0, 180f);
            h.placeHer(h.bedHead.getX() + 1.5, h.bedHead.getZ() + 2.0, 180f);
            h.tpBodyFixed = false;
            h.tp.startSleeping(h.bedHead);
            h.gf.interactions().startSleepTogether(h.tp, h.bedHead);
        }).cap(4, "bed_side").cap(14, "bed_side").cap(30, "bed_side").cap(60, "bed_top").cap(70, "bed_foot").cap(80, "bed_lside");
        shot("ix_sleep_two_beds", h -> {
            h.movePlayer(h.bed2Head.getX() + 1.5, h.origin.getY(), h.bed2Head.getZ() + 1.0, 180f);
            h.placeHer(h.bed2Head.getX() - 1.5, h.bed2Head.getZ() + 2.0, 180f);
            h.tpBodyFixed = false;
            h.tp.startSleeping(h.bed2Head);
            h.gf.interactions().startSleepTogether(h.tp, h.bed2Head);
        }).cap(20, "beds_top").cap(40, "beds_foot");

        // chat, name tag and speech bubble checks (GUI visible)
        // after the rename line's bubble is gone: only her pink name plate may show (no white vanilla name tag)
        shot("ui_nametag", h -> h.chatSetup(false)).gui().cap(20, "aim").cap(230, "aim");
        shot("ui_bubble_leaves", h -> {
            h.chatSetup(false);
            h.leafWall();
            com.aigirlfriend.dialogue.DialogueService.deliver(h.gf, "This bubble should stay readable even with leaves in the way!", com.aigirlfriend.companion.Emotion.NONE);
        }).gui().cap(25, "talk").cap(45, "front");
        shot("ui_chat_scripted", h -> h.chatSetup(false)).gui()
                .clientTick((h, t) -> h.converse(t, "Iris, how are you?", "Iris, you look cute."))
                .caps("talk", 12, 30, 60, 120, 200, 280);
        shot("ui_request_buttons", h -> {
            h.chatSetup(false);
            com.aigirlfriend.companion.RequestSystem.issue(h.gf, com.aigirlfriend.companion.RequestType.EXPLORE_TOGETHER, h.gf.getOwner());
        }).gui()
                .clientTick((h, t) -> {
                    if (t == 60 && h.mc.player != null) h.mc.player.connection.sendCommand("aigirlfriend answer yes"); // what the [Yes] button runs
                })
                .caps("talk", 30, 110);
        shot("ui_chat_ai", h -> h.chatSetup(true)).gui()
                .clientTick((h, t) -> {
                    if (com.aigirlfriend.ai.BuiltinBrain.ready()) h.converse(t, "Hi Iris. How are you?", "You look cute.", "Iris, what's 15 times 3?",
                            "Iris, can you tell me what a creeper is?", "Iris I love you");
                })
                .caps("talk", 20, 2400, 4800);
    }

    private boolean walkToEast = true;

    /** Server side: keeps her walking between two points in front of the stage. */
    void walkBackAndForth(double speed) {
        double left = origin.getX() - 6.5, right = origin.getX() + 6.5;
        if (walkToEast && gf.getX() > right - 0.5) walkToEast = false;
        else if (!walkToEast && gf.getX() < left + 0.5) walkToEast = true;
        if (gf.getNavigation().isDone() || gf.tickCount % 20 == 0) {
            gf.getNavigation().moveTo(walkToEast ? right : left, origin.getY(), origin.getZ() + 5.5, speed);
        }
    }

    static String debugAnim(String name) {
        return GirlfriendAnimations.resolve(name);
    }
}
