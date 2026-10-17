package com.fantasyweapons.tool;

import com.fantasyweapons.material.MaterialOreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The legendary tools' powers, on the ability key while one is held:
 * <ul>
 *   <li>a pickaxe's Ore Sight shows every legendary ore within {@link #SIGHT_RADIUS} blocks through the stone for ten
 *   seconds;</li>
 *   <li>an axe's Timberfall fells every tree within {@link #TIMBER_RADIUS} blocks, nearest first;</li>
 *   <li>a shovel's Earthshaper levels the earth round its holder to the ground they stand on, in a wave outwards (wider
 *   as the shovel's mining area grows).</li>
 * </ul>
 * Each then rests for a while (shown on the tool in the hotbar).
 */
public final class ToolAbilities {
    public static final int SIGHT_RADIUS = 32, SIGHT_TICKS = 200, SIGHT_COOLDOWN = 600;
    public static final int TIMBER_RADIUS = 16, TIMBER_COOLDOWN = 900;
    public static final int EARTH_HEIGHT = 12, EARTH_COOLDOWN = 600;
    /** How many trees Timberfall fells a tick, and how many blocks Earthshaper digs a tick at most. */
    private static final int TREES_PER_TICK = 2, EARTH_PER_TICK = 900;

    private ToolAbilities() {
    }

    /** A power under way, a little each tick. */
    private interface Task {
        /** Whether it is done. */
        boolean tick(ServerPlayer player);
    }

    private record Running(WeakReference<ServerPlayer> player, ServerLevel level, Task task) {
    }

    private static final List<Running> RUNNING = new ArrayList<>();

    public static String name(ToolKind kind) {
        return switch (kind) {
            case PICKAXE -> "Ore Sight";
            case AXE -> "Timberfall";
            case SHOVEL -> "Earthshaper";
        };
    }

    public static String describe(ToolKind kind, ToolUpgrades upgrades) {
        return switch (kind) {
            case PICKAXE -> "Shows every legendary ore within " + SIGHT_RADIUS + " blocks, through the stone, for " + SIGHT_TICKS / 20 + " seconds";
            case AXE -> "Fells every tree within " + TIMBER_RADIUS + " blocks";
            case SHOVEL -> "Levels the earth within " + earthRadius(upgrades) + " blocks to the ground you stand on";
        };
    }

    public static int cooldown(ToolKind kind) {
        return switch (kind) {
            case PICKAXE -> SIGHT_COOLDOWN;
            case AXE -> TIMBER_COOLDOWN;
            case SHOVEL -> EARTH_COOLDOWN;
        };
    }

    /** Earthshaper's reach: 6 blocks for a new shovel, 2 more for each step of its mining area, 16 at the most. */
    public static int earthRadius(ToolUpgrades upgrades) {
        return 4 + 2 * upgrades.reach();
    }

    /** The ability key was pressed by {@code player}: the held legendary tool's power, if it is ready. */
    public static void use(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof LegendaryTool tool) || player.isSpectator()) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        ServerLevel level = player.serverLevel();
        boolean used = switch (tool.kind()) {
            case PICKAXE -> oreSight(player, level);
            case AXE -> timberfall(player, level);
            case SHOVEL -> earthshaper(player, level, LegendaryTools.upgrades(stack));
        };
        if (used) player.getCooldowns().addCooldown(stack.getItem(), cooldown(tool.kind()));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Ore Sight
    // ------------------------------------------------------------------------------------------------------------

    private static boolean oreSight(ServerPlayer player, ServerLevel level) {
        List<Long> positions = new ArrayList<>();
        List<Byte> materials = new ArrayList<>();
        BlockPos center = player.blockPosition();
        scanOres(level, center, SIGHT_RADIUS, (pos, block) -> {
            positions.add(pos.asLong());
            materials.add((byte) block.material().ordinal());
        });
        long[] p = new long[positions.size()];
        byte[] m = new byte[materials.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = positions.get(i);
            m[i] = materials.get(i);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new OreSightPayload(center.asLong(), p, m, SIGHT_TICKS));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.literal(p.length == 0 ? "Ore Sight: no legendary ore within " + SIGHT_RADIUS + " blocks"
                : "Ore Sight: " + p.length + " legendary ore" + (p.length == 1 ? "" : "s") + " nearby").withStyle(s -> s.withColor(0xD8C2FF)), true);
        return true;
    }

    @FunctionalInterface
    public interface OreVisitor {
        void found(BlockPos pos, MaterialOreBlock block);
    }

    /** Every legendary ore within {@code radius} blocks of {@code center} (sections without one are passed over whole). */
    public static void scanOres(ServerLevel level, BlockPos center, int radius, OreVisitor visitor) {
        int r2 = radius * radius;
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - radius); cx <= SectionPos.blockToSectionCoord(center.getX() + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - radius); cz <= SectionPos.blockToSectionCoord(center.getZ() + radius); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = SectionPos.blockToSectionCoord(Math.max(level.getMinBuildHeight(), center.getY() - radius));
                     sy <= SectionPos.blockToSectionCoord(Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius)); sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> s.getBlock() instanceof MaterialOreBlock)) continue;
                    for (int i = 0; i < 4096; i++) {
                        int x = i & 15, y = i >> 8, z = (i >> 4) & 15;
                        BlockState s = section.getBlockState(x, y, z);
                        if (!(s.getBlock() instanceof MaterialOreBlock ore)) continue;
                        BlockPos pos = new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                        if (pos.distSqr(center) <= r2) visitor.found(pos, ore);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Timberfall
    // ------------------------------------------------------------------------------------------------------------

    /** Every tree with a log within {@code radius} blocks (sideways) of {@code center}, nearest first. */
    public static List<TreeFelling.Tree> treesAround(ServerLevel level, BlockPos center, int radius) {
        List<TreeFelling.Tree> trees = new ArrayList<>();
        Set<BlockPos> taken = new HashSet<>();
        int r2 = radius * radius;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                for (int dy = -8; dy <= 32; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (taken.contains(p) || !TreeFelling.isLog(level.getBlockState(p))) continue;
                    TreeFelling.Tree tree = TreeFelling.find(level, p.immutable(), taken);
                    if (tree != null) trees.add(tree);
                }
            }
        }
        trees.sort(Comparator.comparingDouble(t -> t.base().distSqr(center)));
        return trees;
    }

    private static boolean timberfall(ServerPlayer player, ServerLevel level) {
        List<TreeFelling.Tree> trees = treesAround(level, player.blockPosition(), TIMBER_RADIUS);
        if (trees.isEmpty()) {
            player.displayClientMessage(Component.literal("Timberfall: no trees within " + TIMBER_RADIUS + " blocks").withStyle(s -> s.withColor(0xC9E8A0)), true);
            return false;
        }
        player.displayClientMessage(Component.literal("Timberfall: " + trees.size() + " tree" + (trees.size() == 1 ? "" : "s"))
                .withStyle(s -> s.withColor(0xC9E8A0)), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.6f);
        Iterator<TreeFelling.Tree> left = trees.iterator();
        start(player, level, p -> {
            for (int i = 0; i < TREES_PER_TICK && left.hasNext(); i++) {
                TreeFelling.Tree tree = left.next();
                BlockPos base = tree.base();
                level.playSound(null, base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7f, 0.7f + level.random.nextFloat() * 0.2f);
                Digging.breakAll(p, tree.blocks(), p.position().add(0, 0.5, 0), 12);
            }
            return !left.hasNext();
        });
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Earthshaper
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The earth Earthshaper would take round {@code center}: every block a shovel digs (no block entity, nothing
     * unbreakable) from the ground {@code center} stands on up to {@link #EARTH_HEIGHT} blocks above it, within
     * {@code radius}, grouped by distance (index i holds the ring between i and i + 1 blocks out).
     */
    public static List<List<BlockPos>> earthAround(ServerLevel level, BlockPos center, int radius, ItemStack shovel) {
        List<List<BlockPos>> rings = new ArrayList<>();
        for (int i = 0; i <= radius; i++) rings.add(new ArrayList<>());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.5) continue;
                for (int dy = 0; dy <= EARTH_HEIGHT; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (!AreaMining.diggable(level, p, s, ToolKind.SHOVEL, shovel, 1f)) continue;
                    rings.get(Math.min(radius, (int) d)).add(p);
                }
            }
        }
        return rings;
    }

    private static boolean earthshaper(ServerPlayer player, ServerLevel level, ToolUpgrades upgrades) {
        int radius = earthRadius(upgrades);
        List<List<BlockPos>> rings = earthAround(level, player.blockPosition(), radius, player.getMainHandItem());
        int total = rings.stream().mapToInt(List::size).sum();
        if (total == 0) {
            player.displayClientMessage(Component.literal("Earthshaper: the ground here is already level").withStyle(s -> s.withColor(0xE8D2A0)), true);
            return false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 0.6f, 1.3f);
        Vec3 at = player.position().add(0, 0.5, 0);
        int[] ring = {0};
        start(player, level, p -> {
            int dug = 0;
            while (ring[0] < rings.size() && dug < EARTH_PER_TICK) {
                List<BlockPos> blocks = rings.get(ring[0]++);
                dug += blocks.size();
                Digging.breakAll(p, blocks, at, 6);
            }
            return ring[0] >= rings.size();
        });
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // running
    // ------------------------------------------------------------------------------------------------------------

    private static void start(ServerPlayer player, ServerLevel level, Task task) {
        Running r = new Running(new WeakReference<>(player), level, task);
        // its first part straight away
        if (!task.tick(player)) RUNNING.add(r);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            ServerPlayer p = r.player().get();
            if (p == null || p.isRemoved() || p.level() != r.level() || r.task().tick(p)) it.remove();
        }
    }

    public static void clear() {
        RUNNING.clear();
    }
}
