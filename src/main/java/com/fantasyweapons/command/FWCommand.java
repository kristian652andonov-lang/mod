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
                .then(Commands.literal("setfx").then(Commands.argument("set", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(com.fantasyweapons.armor.ArmorSet.values()).map(com.fantasyweapons.armor.ArmorSet::id), b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "set");
                            com.fantasyweapons.armor.ArmorSet set = java.util.Arrays.stream(com.fantasyweapons.armor.ArmorSet.values())
                                    .filter(a -> a.id().equals(id)).findFirst().orElse(null);
                            if (set == null) {
                                c.getSource().sendFailure(Component.literal("No armor set " + id));
                                return 0;
                            }
                            if (!com.fantasyweapons.armor.SetAbilities.demo(p, set)) {
                                c.getSource().sendFailure(Component.literal("No enemy nearby to set off " + set.abilityName() + " against"));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> Component.literal(set.abilityName() + " set off"), false);
                            return 1;
                        })))
                .then(Commands.literal("orescan").then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                        .executes(c -> oreScan(c.getSource(), net.minecraft.core.BlockPos.containing(c.getSource().getPosition()),
                                IntegerArgumentType.getInteger(c, "radius"), null))
                        .then(Commands.argument("biome", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .executes(c -> oreScanBiome(c.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "biome"),
                                        IntegerArgumentType.getInteger(c, "radius"))))))
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

    /** Finds the nearest place with {@code biome} and scans the chunks around it (see {@link #oreScan}). */
    private static int oreScanBiome(net.minecraft.commands.CommandSourceStack source, net.minecraft.resources.ResourceLocation biome, int radius) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, biome);
        var found = source.getLevel().findClosestBiome3d(h -> h.is(key), net.minecraft.core.BlockPos.containing(source.getPosition()), 12800, 32, 64);
        if (found == null) {
            String report = "[orescan] " + biome + ": not found";
            com.fantasyweapons.FantasyWeapons.LOGGER.info(report);
            source.sendFailure(Component.literal(report));
            return 0;
        }
        return oreScan(source, found.getFirst(), radius, key);
    }

    /**
     * Generates the chunks within {@code radius} of {@code at} and counts the legendary ores in them (world generation
     * check). With a biome, only chunks whose middle (at {@code at}'s height) is in that biome are counted.
     */
    private static int oreScan(net.minecraft.commands.CommandSourceStack source, net.minecraft.core.BlockPos at, int radius,
                               net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome) {
        var level = source.getLevel();
        var center = new net.minecraft.world.level.ChunkPos(at);
        java.util.Map<com.fantasyweapons.material.Material, Integer> counts = new java.util.EnumMap<>(com.fantasyweapons.material.Material.class);
        java.util.Map<com.fantasyweapons.material.Material, Integer> veinChunks = new java.util.EnumMap<>(com.fantasyweapons.material.Material.class);
        int chunks = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var chunk = level.getChunk(center.x + dx, center.z + dz);
                if (biome != null && !level.getBiome(new net.minecraft.core.BlockPos(chunk.getPos().getMiddleBlockX(), at.getY(),
                        chunk.getPos().getMiddleBlockZ())).is(biome)) continue;
                chunks++;
                java.util.Set<com.fantasyweapons.material.Material> here = java.util.EnumSet.noneOf(com.fantasyweapons.material.Material.class);
                for (var section : chunk.getSections()) {
                    if (!section.getStates().maybeHas(st -> st.getBlock() instanceof com.fantasyweapons.material.MaterialOreBlock)) continue;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).getBlock() instanceof com.fantasyweapons.material.MaterialOreBlock ore) {
                            counts.merge(ore.material(), 1, Integer::sum);
                            here.add(ore.material());
                        }
                    }
                }
                here.forEach(m -> veinChunks.merge(m, 1, Integer::sum));
            }
        }
        int n = chunks;
        StringBuilder sb = new StringBuilder();
        counts.forEach((m, c) -> sb.append(m.name()).append('=').append(c).append(" in ").append(veinChunks.get(m)).append(" chunks; "));
        String report = "[orescan] " + level.dimension().location() + (biome != null ? " " + biome.location() : "") + " at " + at.toShortString() + ": "
                + n + " chunks: " + (sb.length() == 0 ? "none" : sb);
        com.fantasyweapons.FantasyWeapons.LOGGER.info(report);
        source.sendSuccess(() -> Component.literal(report), false);
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
