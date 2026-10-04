package com.fantasyweapons.command;

import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /fantasyweapons} (alias {@code /fw}) — operator tools for testing and server administration.
 */
public final class FWCommand {
    private static final SimpleCommandExceptionType NOT_HOLDING =
            new SimpleCommandExceptionType(Component.literal("Hold a fantasy weapon in your main hand"));

    private FWCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("fantasyweapons").requires(s -> s.hasPermission(2))
                .then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = held(p);
                            ExpService.setLevel(p, s, IntegerArgumentType.getInteger(c, "level"));
                            return info(c, s);
                        })))
                .then(Commands.literal("exp").then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = held(p);
                            ExpService.addExp(p, s, IntegerArgumentType.getInteger(c, "amount"), false, false);
                            return info(c, s);
                        })))
                .then(Commands.literal("points").then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = held(p);
                            WeaponData d = FantasyWeaponItem.data(s);
                            s.set(ModComponents.WEAPON_DATA.get(), d.withProgress(d.level(), d.exp(), d.totalExp(), IntegerArgumentType.getInteger(c, "amount")));
                            return info(c, s);
                        })))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack s = held(p);
                    WeaponData d = FantasyWeaponItem.data(s);
                    s.set(ModComponents.WEAPON_DATA.get(), new WeaponData(d.weaponId(), 1, 0, 0, Map.of(), 0, 0, "", 0, 0));
                    return info(c, s);
                }))
                .then(Commands.literal("cooldowns").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getData(ModAttachments.ABILITY_RUNTIME).clearCooldowns();
                    p.syncData(ModAttachments.ABILITY_RUNTIME);
                    c.getSource().sendSuccess(() -> Component.literal("Ability cooldowns cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("info").executes(c -> info(c, held(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("give").then(Commands.argument("weapon", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ModItems.WEAPONS.keySet(), b))
                        .executes(c -> give(c, 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10000))
                                .executes(c -> give(c, IntegerArgumentType.getInteger(c, "level"))))));
        dispatcher.register(root);
        dispatcher.register(Commands.literal("fw").requires(s -> s.hasPermission(2)).redirect(dispatcher.getRoot().getChild("fantasyweapons")));
    }

    private static ItemStack held(ServerPlayer p) throws CommandSyntaxException {
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof FantasyWeaponItem)) throw NOT_HOLDING.create();
        return s;
    }

    private static int give(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var item = ModItems.WEAPONS.get(StringArgumentType.getString(c, "weapon"));
        if (item == null) throw new SimpleCommandExceptionType(Component.literal("Unknown weapon")).create();
        ItemStack stack = new ItemStack(item.get());
        stack.set(ModComponents.WEAPON_DATA.get(), WeaponData.EMPTY.withId(java.util.UUID.randomUUID()));
        if (level > 1) ExpService.setLevel(p, stack, level);
        ItemStack shown = stack.copy();
        // Inventory#add moves the stack (leaving the original empty), so report from a copy
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        return info(c, shown);
    }

    private static int info(CommandContext<CommandSourceStack> c, ItemStack s) {
        FantasyWeaponItem item = (FantasyWeaponItem) s.getItem();
        WeaponDefinition def = item.definition();
        WeaponData d = FantasyWeaponItem.data(s);
        String msg = String.format("%s  Lv %d  EXP %d/%d  DMG %,.0f  Mastery %d%%  Points %d  Id %s", def.displayName(), d.level(), d.exp(),
                ProgressionMath.expToNext(d.level()), ProgressionMath.weaponDamage(def, d), Math.round(ProgressionMath.mastery(def, d) * 100),
                d.masteryPoints(), Optional.ofNullable(d.weaponId().orElse(null)).map(Object::toString).orElse("unassigned"));
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
