package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.weapons.ultimate.ShufflePlan;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.wreck.ChunkShaper;
import com.fantasyweapons.world.wreck.DivineShapes;
import com.fantasyweapons.world.wreck.WorldWreck;
import com.fantasyweapons.world.wreck.WreckShapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DIMENSION BREAKAGE. 4th Dimension Ascension, far greater. The figures of the fourth dimension fill the sky over the
 * target and a vast tesseract forms; then space breaks, and the land is folded into one enormous cube of chunks round the
 * tesseract: the ground under it stays as it was, its walls are pieces of the land turned on their sides, its top pieces
 * of the land hung upside down, each a 16-block cube with a cube's gap between it and the next, so the tesseract's light
 * pours out between them; the land outside, everything within the radius, is torn up to build it. The tesseract hangs
 * in the middle for half a minute, and then it explodes, and everything within the radius is deleted from the world.
 */
public final class DimensionBreakage {
    /** How many chunks the cube reaches out from its middle chunk (it is 2n + 1 chunks across). */
    public static final int HALF = 7;
    /** Radius of the ground left standing under the caster, if they are inside the radius. */
    private static final double SAFE = 12;
    /** Ticks before space breaks that the cube is built from the land (and worked out). */
    private static final int BUILD = 120;

    private DimensionBreakage() {
    }

    /**
     * The cube for a break at {@code at}: its middle chunk, how many chunks it reaches out, the section of its floor (the
     * ground) and the section of its top, counted from the bottom of the world.
     */
    public record Cube(ChunkPos middle, int half, int floor, int top) {
        public static Cube at(ServerLevel level, Vec3 at, double radius) {
            int half = Math.max(2, Math.min(HALF, (int) Math.floor(radius / 16 / 4.5)));
            int sections = level.getSectionsCount();
            int floor = Math.max(0, ((int) Math.floor(at.y) - 1 - level.getMinBuildHeight()) >> 4);
            int top = Math.min(sections - 1, floor + 2 * half + 1);
            return new Cube(new ChunkPos((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4), half, floor, top);
        }

        public boolean holds(int cx, int cz) {
            return Math.abs(cx - middle.x) <= half && Math.abs(cz - middle.z) <= half;
        }

        /** Its lowest corner and its highest (world coordinates). */
        public Vec3 low(int minY) {
            return new Vec3((middle.x - half) * 16, minY + (floor + 1) * 16, (middle.z - half) * 16);
        }

        public Vec3 high(int minY) {
            return new Vec3((middle.x + half + 1) * 16, minY + (top + 1) * 16, (middle.z + half + 1) * 16);
        }

        /** Where the tesseract hangs: the middle of the cube. */
        public Vec3 heart(int minY) {
            return low(minY).add(high(minY)).scale(0.5);
        }
    }

    public static boolean cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 at = Cataclysm.landing(ctx, 1024);
        double radius = WorldWreck.radius(ctx.param("radius"));
        int delay = Math.max(60, (int) Math.round(ctx.param("delay") * 20));
        int hold = Math.max(100, (int) Math.round(ctx.param("hold") * 20));
        long seed = Kit.seed(level);
        Cube cube = Cube.at(level, at, radius);
        int minY = level.getMinBuildHeight();
        List<ChunkPos> region = ShufflePlan.region(at, radius);
        // the region is loaded now, so every chunk is there to be read and rewritten when space breaks
        for (ChunkPos p : region) WorldWreck.hold(level, p.x, p.z);
        Cataclysm.fx(level, ctx.player(), FxPayload.of(FxIds.DIMENSION_BREAKAGE).caster(ctx.player().getId()).pos(at).scale((float) radius)
                .power(delay).level(hold).points(List.of(cube.low(minY), cube.high(minY))).entities(List.of(minY)).seed(seed).build());
        AreaEffectManager.add(new Breakage(ctx, at, radius, delay, hold, cube, region, seed));
        Kit.active(ctx, delay + hold + 60);
        return true;
    }

    private static final class Breakage extends AreaEffect {
        private final double radius;
        private final int delay, hold;
        private final Cube cube;
        private final List<ChunkPos> region;
        private final long seed;
        private final float damage;
        /** The caster's ground (spared), if they stood inside the radius. */
        private final Vec3 keep;

        Breakage(AbilityContext ctx, Vec3 at, double radius, int delay, int hold, Cube cube, List<ChunkPos> region, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, delay + hold + 40);
            this.radius = radius;
            this.delay = delay;
            this.hold = hold;
            this.cube = cube;
            this.region = region;
            this.seed = seed;
            this.damage = ctx.damage();
            Vec3 feet = ctx.feet();
            this.keep = Math.hypot(feet.x - at.x, feet.z - at.z) < radius + SAFE + 16 ? feet : null;
        }

        private ChunkShaper spare(ChunkShaper s) {
            return keep == null ? s : DivineShapes.sparing(s, keep, SAFE);
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age < delay && age % 10 == 0) {
                // everything in reach floats as the figures gather
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) {
                    e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 14, 0, false, false));
                }
            }
            if (age < delay + hold && age % 100 == 0) {
                for (ChunkPos p : region) WorldWreck.hold(level, p.x, p.z);
            }
            // the cube is built from the land as it stands a few seconds before space breaks, and worked out from then,
            // so the land is folded the moment it breaks
            if (age == Math.max(1, delay - BUILD)) build();
            if (age == delay) fold(owner);
            // the end is worked out long before it comes (once the fold is under way), so it lands all at once
            if (age == delay + Math.max(10, hold - 560)) {
                ChunkShaper end = spare(WreckShapes.shaft(pos, radius, com.fantasyweapons.world.wreck.Wall.RAINBOW));
                WorldWreck.disc(level, pos, radius + 20, seed + 9, d -> delay + hold - age - 1, end);
            }
            if (age == delay + hold) explode(owner);
        }

        /** Space breaks: the land is folded into the cube (worked out since {@link #build}), and what was on it is crushed. */
        private void fold(ServerPlayer owner) {
            int minY = level.getMinBuildHeight();
            for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.VOID);
            Cataclysm.frames(level, owner, cube.heart(minY), radius * 2, 0x9B4DFF, 0x30E8FF);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 60f, 0.3f);
        }

        /**
         * The cube: pieces of the land outside it (the loaded part) cut out and turned to make its walls and its top, set
         * going now to land as space breaks, sweeping out from the middle; the rest of the land within the radius torn up.
         */
        private void build() {
            if (!WorldWreck.active(level)) return;
            int breaks = delay - age;
            RandomSource r = RandomSource.create(seed ^ 0xD1CEL);
            // the land outside the cube, the loaded part of it, to build the cube from
            List<ChunkPos> outside = new ArrayList<>();
            for (ChunkPos p : region) if (!cube.holds(p.x, p.z) && level.getChunkSource().getChunkNow(p.x, p.z) != null) outside.add(p);
            Map<Long, List<DivineShapes.Tile>> tiles = new HashMap<>();
            int levels = cube.top() - cube.floor() - 1;
            for (int dx = -cube.half(); dx <= cube.half(); dx++) {
                for (int dz = -cube.half(); dz <= cube.half(); dz++) {
                    boolean edgeX = Math.abs(dx) == cube.half(), edgeZ = Math.abs(dz) == cube.half();
                    // a lattice: a cube of land, then a gap a cube wide, all the way round
                    boolean onLattice = (dx + cube.half()) % 2 == 0 && (dz + cube.half()) % 2 == 0;
                    ChunkPos dest = new ChunkPos(cube.middle().x + dx, cube.middle().z + dz);
                    List<DivineShapes.Tile> mine = tiles.computeIfAbsent(dest.toLong(), k -> new ArrayList<>());
                    if (onLattice) tile(mine, cube.top(), DivineShapes.Turn.FLIP, take(outside, r, dest));
                    if (!edgeX && !edgeZ) continue;
                    // the walls face in toward the middle
                    DivineShapes.Turn turn = edgeX ? (dx > 0 ? DivineShapes.Turn.FACE_NEG_X : DivineShapes.Turn.FACE_POS_X)
                            : (dz > 0 ? DivineShapes.Turn.FACE_NEG_Z : DivineShapes.Turn.FACE_POS_Z);
                    boolean along = edgeX ? (dz + cube.half()) % 2 == 0 : (dx + cube.half()) % 2 == 0;
                    if (!along) continue;
                    for (int v = 1; v <= levels; v += 2) tile(mine, cube.floor() + v, turn, take(outside, r, dest));
                }
            }
            for (var e : tiles.entrySet()) {
                int cx = ChunkPos.getX(e.getKey()), cz = ChunkPos.getZ(e.getKey());
                int lag = (int) (Math.hypot(cx * 16 + 8 - pos.x, cz * 16 + 8 - pos.z) / 24);
                WorldWreck.submit(level, cx, cz, breaks + lag, seed, spare(DivineShapes.fold(e.getValue(), cube.top() + 1)));
            }
            ChunkShaper torn = spare(DivineShapes.torn(Math.floorMod(seed, 997L)));
            for (ChunkPos p : region) {
                if (cube.holds(p.x, p.z)) continue;
                int lag = (int) (Math.hypot(p.getMiddleBlockX() - pos.x, p.getMiddleBlockZ() - pos.z) / 24);
                WorldWreck.submit(level, p.x, p.z, breaks + lag, seed + 1, torn);
            }
        }

        private static void tile(List<DivineShapes.Tile> into, int section, DivineShapes.Turn turn,
                                 net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> source) {
            if (source != null) into.add(new DivineShapes.Tile(section, turn, source));
        }

        /**
         * A section of the land outside, at its surface, for a tile (the cube's own chunk's, if none outside is loaded; null
         * if that is not loaded either).
         */
        @org.jetbrains.annotations.Nullable
        private net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> take(List<ChunkPos> outside,
                                                                                                                     RandomSource r, ChunkPos fallback) {
            ChunkPos from = outside.isEmpty() ? fallback : outside.get(r.nextInt(outside.size()));
            var chunk = level.getChunkSource().getChunkNow(from.x, from.z);
            if (chunk == null) chunk = level.getChunkSource().getChunkNow(fallback.x, fallback.z);
            if (chunk == null) return null;
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            int index = Math.max(0, Math.min(chunk.getSections().length - 1, (surface - 6 - level.getMinBuildHeight()) >> 4));
            return com.fantasyweapons.world.wreck.WreckChunk.section(chunk, index);
        }

        /** The tesseract explodes: everything within the radius is gone. */
        private void explode(ServerPlayer owner) {
            for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.VOID);
            Cataclysm.eraseColumn(owner, weapon(owner), level, pos, radius, damage, Element.VOID);
            Cataclysm.frames(level, owner, cube.heart(level.getMinBuildHeight()), radius * 3, 0xC79CFF, 0xFFFFFF);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 80f, 0.25f);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 80f, 0.3f);
        }
    }
}
