package com.terrahaul.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * {@code /terrahaul} command.
 * <ul>
 *   <li>{@code /terrahaul find <landform> [radius]}: nearest river, lake, ocean, canyon, waterfall,
 *       volcano, peak, ... Searches query the terrain model on a background thread and never
 *       generate chunks.</li>
 *   <li>{@code /terrahaul find biome <biome|#tag> [radius]}: nearest biome (works in any world).</li>
 *   <li>{@code /terrahaul info}: what the terrain model knows about the column you stand in.</li>
 * </ul>
 * Requires permission level 2, like vanilla {@code /locate}.
 */
public final class TerrahaulCommand {
    private static final SimpleCommandExceptionType NOT_TERRAHAUL = new SimpleCommandExceptionType(
        Component.translatable("commands.terrahaul.not_terrahaul"));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
        name -> Component.translatable("commands.terrahaul.find.unknown", name));
    private static final DynamicCommandExceptionType BIOME_NOT_FOUND = new DynamicCommandExceptionType(
        name -> Component.translatable("commands.terrahaul.find.biome_not_found", name));
    private static final int DEFAULT_RADIUS = 8192;
    private static final int MAX_RADIUS = 30000;

    private TerrahaulCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("terrahaul")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("find")
                .then(Commands.literal("biome")
                    .then(Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(buildContext, Registries.BIOME))
                        .executes(ctx -> findBiome(ctx, 6400))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, MAX_RADIUS))
                            .executes(ctx -> findBiome(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.argument("landform", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TerrainFinder.targets().keySet(), builder))
                    .executes(ctx -> findLandform(ctx, DEFAULT_RADIUS))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(64, MAX_RADIUS))
                        .executes(ctx -> findLandform(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
            .then(Commands.literal("info").executes(TerrahaulCommand::info)));
    }

    private static int findLandform(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "landform").toLowerCase(Locale.ROOT);
        TerrainFinder.Target target = TerrainFinder.targets().get(name);
        if (target == null) {
            throw UNKNOWN.create(name);
        }
        ServerLevel level = source.getLevel();
        TerrainModel model = TerrahaulGeneration.model(level);
        if (model == null) {
            throw NOT_TERRAHAUL.create();
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable("commands.terrahaul.find.searching", target.description(), radius)
            .withStyle(ChatFormatting.GRAY), false);
        long started = Util.getMillis();
        CompletableFuture.supplyAsync(() -> TerrainFinder.find(model, target, origin.getX(), origin.getZ(), radius, server::isStopped),
                Util.backgroundExecutor())
            .whenComplete((result, error) -> server.execute(() -> {
                if (error != null) {
                    source.sendFailure(Component.literal("Terrahaul search failed: " + error.getMessage()));
                } else if (result == null) {
                    source.sendFailure(Component.translatable("commands.terrahaul.find.not_found", target.description(), radius));
                } else {
                    sendResult(source, origin, target.description(), result.x(), result.y(), result.z(), Util.getMillis() - started);
                }
            }));
        return 1;
    }

    private static int findBiome(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceOrTagArgument.Result<Biome> biome = ResourceOrTagArgument.getResourceOrTag(ctx, "biome", Registries.BIOME);
        BlockPos origin = BlockPos.containing(source.getPosition());
        long started = Util.getMillis();
        Pair<BlockPos, Holder<Biome>> found = source.getLevel().findClosestBiome3d(biome, origin, radius, 32, 64);
        if (found == null) {
            throw BIOME_NOT_FOUND.create(biome.asPrintable());
        }
        BlockPos pos = found.getFirst();
        int y = source.getLevel().getChunkSource().getGenerator().getBaseHeight(pos.getX(), pos.getZ(),
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, source.getLevel(), source.getLevel().getChunkSource().randomState());
        sendResult(source, origin, biome.asPrintable(), pos.getX(), Math.max(y + 1, pos.getY()), pos.getZ(), Util.getMillis() - started);
        return 1;
    }

    private static void sendResult(CommandSourceStack source, BlockPos origin, String what, int x, int y, int z, long millis) {
        int distance = Mth.floor(Math.sqrt((double) (x - origin.getX()) * (x - origin.getX()) + (double) (z - origin.getZ()) * (z - origin.getZ())));
        MutableComponent coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", x, y, z))
            .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + x + " " + y + " " + z))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.translatable("commands.terrahaul.find.success", what, coords, distance, millis), false);
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        TerrainModel model = TerrahaulGeneration.model(source.getLevel());
        if (model == null) {
            throw NOT_TERRAHAUL.create();
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        ColumnSample s = new ColumnSample();
        model.sampleInto(pos.getX(), pos.getZ(), s);
        StringBuilder landforms = new StringBuilder();
        appendIf(landforms, s.mountain > 0.4, String.format(Locale.ROOT, "mountain(%.0f%% %s)", s.mountain * 100, s.alpine > s.ancient ? "alpine" : "ancient"));
        appendIf(landforms, s.wall > 0.3, "mountain wall");
        appendIf(landforms, s.meadow > 0.4, "meadow");
        appendIf(landforms, s.plateau > 0.5, "plateau");
        appendIf(landforms, s.canyon > 0.2, "canyon");
        appendIf(landforms, s.windswept > 0.5, "windswept");
        appendIf(landforms, s.swamp > 0.5, "wetland");
        appendIf(landforms, s.volcano > 0.2, "volcano");
        appendIf(landforms, s.river > 0.0, String.format(Locale.ROOT, "river(%.0f wide%s)", s.riverWidth * 2, s.dryRiver ? ", dry" : ""));
        appendIf(landforms, s.riverValley > 0.3 && s.river <= 0.0, "river valley");
        appendIf(landforms, s.stream > 0.3, "stream");
        appendIf(landforms, s.lake > 0.0, LakeType.name(s.lakeType).replace('_', ' '));
        appendIf(landforms, s.island > 0.3, "island");
        appendIf(landforms, s.fjord > 0.4, "fjord");
        appendIf(landforms, s.coastCliff > 0.4, "sea cliffs");
        appendIf(landforms, s.beach > 0.5, "beach");
        if (landforms.isEmpty()) {
            landforms.append(s.height < model.seaLevel() ? "sea" : "lowland");
        }
        source.sendSuccess(() -> Component.translatable("commands.terrahaul.info",
            String.format(Locale.ROOT, "%.1f", s.height),
            s.waterLevel == ColumnSample.NO_WATER ? "-" : String.format(Locale.ROOT, "%.0f", s.waterLevel),
            landforms.toString(), Geology.name(s.geology),
            String.format(Locale.ROOT, "C=%.2f E=%.2f W=%.2f T=%.2f H=%.2f", s.continentalness, s.erosion, s.weirdness, s.temperature, s.humidity)), false);
        return 1;
    }

    private static void appendIf(StringBuilder sb, boolean condition, String text) {
        if (condition) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(text);
        }
    }
}
