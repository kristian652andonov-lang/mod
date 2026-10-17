package com.aigirlfriend.command;

import com.aigirlfriend.ai.AIService;
import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.MemoryEntry;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.MoodEvaluator;
import com.aigirlfriend.companion.NeedsSystem;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.companion.RequestSystem;
import com.aigirlfriend.companion.RequestType;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.network.ServerPayloadHandler;
import com.aigirlfriend.registry.ModItems;
import com.aigirlfriend.util.WorldClock;
import com.aigirlfriend.world.ActiveCompanions;
import com.aigirlfriend.world.CompanionRegistry;
import com.aigirlfriend.world.CompanionTransfer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /aigirlfriend (alias /gf): everyday helpers for owners (info, recall, restore, rename), the op-only /gf maxtier cheat
 * and op-only debug tools to test every relationship stage, marriage, mood, need, cycle phase and request.
 */
public final class GirlfriendCommand {
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.translatable("aigirlfriend.fail.none_nearby"));

    private GirlfriendCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("aigirlfriend")
                .then(Commands.literal("info").executes(GirlfriendCommand::info))
                .then(Commands.literal("recall").executes(ctx -> {
                    CompanionTransfer.recall(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("restore").executes(GirlfriendCommand::restore))
                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                    GirlfriendEntity gf = nearest(ctx);
                    String name = StringArgumentType.getString(ctx, "name").trim();
                    if (name.isEmpty() || name.length() > 24) return 0;
                    gf.setCustomName(Component.literal(name));
                    return 1;
                })))
                .then(Commands.literal("memories").executes(GirlfriendCommand::memories))
                .then(Commands.literal("ai").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("AI: " + AIService.status()), false);
                    return 1;
                }).then(Commands.literal("download").requires(GirlfriendCommand::opOrWorldOwner).executes(ctx -> {
                    if (!AIService.builtin()) {
                        ctx.getSource().sendFailure(Component.literal("The built-in brain is not selected (config: ai.brain = BUILTIN)."));
                        return 0;
                    }
                    com.aigirlfriend.ai.BuiltinBrain.ensureStarted(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("AI: " + AIService.status()), false);
                    return 1;
                })))
                .then(Commands.literal("maxtier").requires(GirlfriendCommand::opOrWorldOwner).executes(GirlfriendCommand::maxTier))
                .then(Commands.literal("answer")
                        .then(Commands.literal("yes").executes(ctx -> answer(ctx, true, -1))
                                .then(Commands.argument("request", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                        .executes(ctx -> answer(ctx, true, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "request")))))
                        .then(Commands.literal("no").executes(ctx -> answer(ctx, false, -1))
                                .then(Commands.argument("request", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                        .executes(ctx -> answer(ctx, false, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "request"))))))
                .then(debug());
        d.register(root);
        d.register(Commands.literal("gf").redirect(d.getRoot().getChild("aigirlfriend")));
    }

    /** Operators, or the player whose single-player world this is (works without "allow cheats"). */
    private static boolean opOrWorldOwner(CommandSourceStack s) {
        return s.hasPermission(2) || s.getPlayer() != null && s.getServer().isSingleplayerOwner(s.getPlayer().getGameProfile());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debug() {
        LiteralArgumentBuilder<CommandSourceStack> dbg = Commands.literal("debug").requires(s -> s.hasPermission(2));
        LiteralArgumentBuilder<CommandSourceStack> stage = Commands.literal("stage");
        for (RelationshipStage s : RelationshipStage.values()) {
            stage.then(Commands.literal(s.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                GirlfriendEntity gf = nearest(ctx);
                gf.data().relationship.forceStage(s);
                gf.controller().checkStage();
                gf.setSyncedStage(s);
                feedback(ctx, "Relationship set to " + s.name());
                return 1;
            }));
        }
        dbg.then(stage);
        LiteralArgumentBuilder<CommandSourceStack> mood = Commands.literal("mood");
        for (Mood m : Mood.values()) {
            mood.then(Commands.literal(m.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                GirlfriendEntity gf = nearest(ctx);
                gf.data().mood.setPrimary(m, java.util.List.of("Debug command"), WorldClock.day(gf.level()));
                gf.setSyncedMood(m);
                feedback(ctx, "Mood set to " + m.name());
                return 1;
            }));
        }
        dbg.then(mood);
        dbg.then(Commands.literal("evaluate_mood").executes(ctx -> {
            GirlfriendEntity gf = nearest(ctx);
            Mood m = MoodEvaluator.evaluateDay(gf, WorldClock.day(gf.level()));
            gf.setSyncedMood(m);
            feedback(ctx, "Evaluated mood: " + m.name() + " because " + String.join("; ", gf.data().mood.causes));
            return 1;
        }));
        dbg.then(Commands.literal("needs")
                .then(Commands.argument("hunger", FloatArgumentType.floatArg(0, 100))
                        .then(Commands.argument("energy", FloatArgumentType.floatArg(0, 100))
                                .then(Commands.argument("attention", FloatArgumentType.floatArg(0, 100)).executes(ctx -> {
                                    GirlfriendEntity gf = nearest(ctx);
                                    var n = gf.data().needs;
                                    n.hunger = FloatArgumentType.getFloat(ctx, "hunger");
                                    n.energy = FloatArgumentType.getFloat(ctx, "energy");
                                    n.attention = FloatArgumentType.getFloat(ctx, "attention");
                                    feedback(ctx, "Needs updated");
                                    return 1;
                                })))));
        LiteralArgumentBuilder<CommandSourceStack> cycle = Commands.literal("cycle");
        for (CyclePhase p : CyclePhase.values()) {
            if (p == CyclePhase.DISABLED) continue;
            cycle.then(Commands.literal(p.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                GirlfriendEntity gf = nearest(ctx);
                gf.data().cycle.setPhase(NeedsSystem.cycleClock(gf.level()), p);
                feedback(ctx, "Cycle phase set to " + p.name());
                return 1;
            }));
        }
        dbg.then(cycle);
        LiteralArgumentBuilder<CommandSourceStack> req = Commands.literal("request");
        for (RequestType t : RequestType.values()) {
            req.then(Commands.literal(t.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                GirlfriendEntity gf = nearest(ctx);
                gf.data().request = null;
                RequestSystem.issue(gf, t, ctx.getSource().getPlayerOrException());
                return 1;
            }));
        }
        dbg.then(req);
        dbg.then(Commands.literal("faint").executes(ctx -> {
            GirlfriendEntity gf = nearest(ctx);
            gf.controller().faint(gf.damageSources().generic());
            return 1;
        }));
        dbg.then(Commands.literal("married").then(Commands.argument("married", BoolArgumentType.bool()).executes(ctx -> {
            GirlfriendEntity gf = nearest(ctx);
            boolean married = BoolArgumentType.getBool(ctx, "married");
            gf.data().relationship.setMarried(married, WorldClock.day(gf.level()));
            feedback(ctx, gf.data().name + (married ? " is now married to you" : " is no longer married"));
            return 1;
        })));
        dbg.then(Commands.literal("reset_cooldowns").executes(ctx -> {
            GirlfriendEntity gf = nearest(ctx);
            gf.controller().lastInitiativeTick = 0;
            gf.interactions().clearCooldowns();
            feedback(ctx, "Initiative and interaction cooldowns reset");
            return 1;
        }));
        return dbg;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static GirlfriendEntity nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GirlfriendEntity gf = ServerPayloadHandler.findNearestOwned(player, 32);
        if (gf == null) throw NONE.create();
        return gf;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GirlfriendEntity gf = nearest(ctx);
        CompanionData d = gf.data();
        String s = String.format(Locale.ROOT,
                "%s | %s%s (%s) | mood %s: %s | hunger %.0f energy %.0f attention %.0f | affection %.0f trust %.0f | days %d | %s | cycle %s",
                d.name, d.relationship.stage(), d.relationship.married ? ", married" : "", d.personality.archetype(), d.mood.primary, String.join("; ", d.mood.causes),
                d.needs.hunger, d.needs.energy, d.needs.attention, d.relationship.affection, d.relationship.trust,
                d.relationship.daysTogether, gf.getCommand(), d.cycle.phase(NeedsSystem.cycleClock(gf.level())));
        feedback(ctx, s);
        return 1;
    }

    /** /gf maxtier: the highest relationship stage with the stats to keep it; the milestone plays only when she moves up. */
    /** The [Yes] / [No] buttons under her requests in chat. */
    private static int answer(CommandContext<CommandSourceStack> ctx, boolean yes, long requestId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GirlfriendEntity gf = null;
        for (GirlfriendEntity g : com.aigirlfriend.world.ActiveCompanions.ownedBy(player.getUUID())) {
            var r = g.data().request;
            if (g.level() != player.level() || r == null || r.accepted) continue;
            if (requestId >= 0 && r.issuedTick == requestId) {
                gf = g; // the companion whose request this button belongs to
                break;
            }
            if (gf == null || g.distanceTo(player) < gf.distanceTo(player)) gf = g;
        }
        if (gf == null) gf = nearest(ctx);
        com.aigirlfriend.dialogue.DialogueService.answerRequest(gf, player, yes, requestId);
        return 1;
    }

    private static int maxTier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GirlfriendEntity gf = nearest(ctx);
        CompanionData d = gf.data();
        boolean moved = gf.controller().reachMaxStage();
        Component stage = Component.translatable(d.relationship.stage().key());
        ctx.getSource().sendSuccess(() -> Component.translatable(moved ? "aigirlfriend.cmd.maxtier.done" : "aigirlfriend.cmd.maxtier.already", d.name, stage), false);
        if (!d.relationship.married) ctx.getSource().sendSuccess(() -> Component.translatable("aigirlfriend.cmd.maxtier.hint", d.name), false);
        return 1;
    }

    private static int memories(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GirlfriendEntity gf = nearest(ctx);
        long day = WorldClock.day(gf.level());
        for (MemoryEntry e : gf.data().memory.all()) {
            String line = String.format(Locale.ROOT, "[%s, day %d, x%d%s] %s", e.type, e.day, e.count, e.resolved ? ", resolved" : "", e.summary);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        feedback(ctx, gf.data().memory.all().size() + " memories (today is day " + day + ")");
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CompanionRegistry reg = CompanionRegistry.get(player.server);
        CompanionRegistry.Entry e = reg.restorable(player.getUUID());
        if (e == null || ActiveCompanions.get(e.companionId) != null) {
            ctx.getSource().sendFailure(Component.translatable("aigirlfriend.message.nothing_to_restore"));
            return 0;
        }
        var egg = ModItems.GIRLFRIEND_SPAWN_EGG.get();
        boolean ok = egg.spawnFor(player.serverLevel(), player, player.blockPosition(), new net.minecraft.world.item.ItemStack(egg));
        return ok ? 1 : 0;
    }
}
