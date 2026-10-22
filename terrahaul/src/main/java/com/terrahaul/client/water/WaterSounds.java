package com.terrahaul.client.water;

import com.terrahaul.Terrahaul;
import com.terrahaul.config.TerrahaulConfig;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * The sound of falling and running water: a deep looping roar near tall falls, a lighter rush and
 * babble near cascades, small steps and rapids.
 * <p>
 * Every fall and every patch of running water near a drop is a possible source, whose loudness
 * and reach grow with its size (width times height). Small sources close together, the steps of a
 * cascade or the cells of one stretch of rapids, are heard as one. Only the few loudest sources at
 * the listener play, each as a looping sound that sits at the point of its water nearest to the
 * listener: the nearest column of a fall (up or down a tall curtain, along a step across a wide
 * river) or the nearest cell of running water. So it is heard from the bank at either end of a
 * long step, moves with the listener and comes from the right direction. Distance is handled here
 * rather than by the sound engine, whose range is fixed when a sound starts: the volume follows a
 * smooth curve to silence at the source's reach, measured from that nearest point, changes are
 * eased in and out, and a source that drops out of the chosen few fades away instead of stopping.
 * <p>
 * The loops are mastered quietly (about -30 dBFS), in line with Minecraft's ambient loops, so even
 * a large fall sits under the game's other sounds unless the listener stands at its foot.
 * <p>
 * The sounds ({@code terrahaul:ambient.waterfall} and {@code terrahaul:ambient.rapids}) are only
 * played on this client; they need no registry entry because the sound engine finds them by name
 * in {@code sounds.json}. They belong to the Ambient/Environment volume slider.
 */
final class WaterSounds {
    static final SoundEvent WATERFALL = SoundEvent.createVariableRangeEvent(Terrahaul.id("ambient.waterfall"));
    static final SoundEvent RAPIDS = SoundEvent.createVariableRangeEvent(Terrahaul.id("ambient.rapids"));

    private static final int MAX_ROARS = 2;
    private static final int MAX_RUSHES = 3;
    /** Small sources closer than this are heard as one. */
    private static final double MERGE = 10.0;
    /** Share of full volume a source needs before a new sound starts for it. */
    private static final float START = 0.015F;
    /** A playing sound this close to a source whose key changed carries on for it. */
    private static final double ADOPT = 16.0;

    /** A place the sound of water comes from. */
    private static final class Source {
        final long key;
        final boolean roar;
        /** Centre, for joining small sources. */
        final double x;
        final double z;
        /** Where the water is: the middle of each column or cell, as x, z pairs. */
        final DoubleArrayList points;
        double minY;
        double maxY;
        double strength;
        float level;
        /** The point nearest to the listener, this tick. */
        double nearX;
        double nearZ;

        Source(long key, boolean roar, double x, double z, double minY, double maxY, double strength, DoubleArrayList points) {
            this.key = key;
            this.roar = roar;
            this.x = x;
            this.z = z;
            this.minY = minY;
            this.maxY = maxY;
            this.strength = strength;
            this.points = points;
        }

        /**
         * How far the source is heard. Even the largest fall stays well inside the area the
         * scanner watches at the lowest quality (four chunks), so a sound fades out smoothly
         * instead of cutting off where the scan ends.
         */
        double reach(double scale) {
            return scale * (roar ? Mth.clamp(12.0 + 2.2 * Math.sqrt(strength), 16.0, 48.0) : Mth.clamp(6.0 + 1.5 * Math.sqrt(strength), 8.0, 20.0));
        }

        float peak() {
            return (float) (roar ? Mth.clamp(0.25 + 0.11 * Math.log1p(strength / 20.0), 0.25, 0.8)
                : Mth.clamp(0.1 + 0.06 * Math.log1p(strength), 0.1, 0.38));
        }

        double nearestY(double y) {
            return Mth.clamp(y, minY, maxY);
        }

        /** Finds the point nearest to the listener and returns its distance. */
        double distance(Vec3 listener) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i += 2) {
                double dx = points.getDouble(i) - listener.x;
                double dz = points.getDouble(i + 1) - listener.z;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    nearX = points.getDouble(i);
                    nearZ = points.getDouble(i + 1);
                }
            }
            double dy = nearestY(listener.y) - listener.y;
            return Math.sqrt(best + dy * dy);
        }
    }

    private final List<Source> sources = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Loop> playing = new Long2ObjectOpenHashMap<>();
    private int sinceStart;

    /** Builds the sources from the current falls and running water (after each rebuild). */
    void update(List<Fall> falls, List<WaterScanner.RapidPatch> patches) {
        sources.clear();
        List<Source> small = new ArrayList<>();
        for (Fall fall : falls) {
            double strength = fall.width * (double) fall.height;
            boolean roar = fall.height >= 8 && strength >= 20.0;
            DoubleArrayList points = new DoubleArrayList(fall.columns.length * 2);
            for (WaterScanner.Column c : fall.columns) {
                points.add(c.x() + 0.5);
                points.add(c.z() + 0.5);
            }
            Source s = new Source(fall.key, roar, fall.cx, fall.cz, fall.surface, fall.topY + 1.0, roar ? strength : strength * 1.5, points);
            (roar ? sources : small).add(s);
        }
        for (WaterScanner.RapidPatch p : patches) {
            if (p.cells().length >= 3) {
                DoubleArrayList points = new DoubleArrayList(p.cells().length * 2);
                for (long cell : p.cells()) {
                    points.add(BlockPos.getX(cell) + 0.5);
                    points.add(BlockPos.getZ(cell) + 0.5);
                }
                small.add(new Source(p.key() ^ 0x5DEECE66DL, false, p.x(), p.z(), p.y(), p.y(), p.cells().length * 0.6, points));
            }
        }
        // the strongest small source takes in the weaker ones around it
        small.sort(Comparator.comparingDouble((Source s) -> s.strength).reversed());
        boolean[] taken = new boolean[small.size()];
        for (int i = 0; i < small.size(); i++) {
            if (taken[i]) {
                continue;
            }
            Source head = small.get(i);
            for (int j = i + 1; j < small.size(); j++) {
                Source other = small.get(j);
                if (!taken[j] && Math.abs(other.x - head.x) < MERGE && Math.abs(other.z - head.z) < MERGE
                    && other.minY < head.maxY + MERGE && other.maxY > head.minY - MERGE) {
                    taken[j] = true;
                    head.strength += other.strength;
                    head.points.addAll(other.points);
                    head.minY = Math.min(head.minY, other.minY);
                    head.maxY = Math.max(head.maxY, other.maxY);
                }
            }
            sources.add(head);
        }
    }

    void tick(Minecraft mc, Vec3 listener) {
        sinceStart++;
        float master = (float) TerrahaulConfig.waterSoundVolume();
        if (mc.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0.0F || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F
            || master <= 0.0F) {
            stopAll(mc);
            return;
        }
        // muffled under water
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER) {
            master *= 0.45F;
        }
        double scale = TerrahaulConfig.waterSoundRange();
        for (Source s : sources) {
            double d = s.distance(listener);
            double f = Mth.clamp(1.0 - Math.max(0.0, d - 2.0) / s.reach(scale), 0.0, 1.0);
            s.level = (float) (s.peak() * f * f) * master;
        }
        // the loudest few of each kind play; those already playing are favoured, so two sources
        // of about the same loudness do not take turns
        List<Source> chosen = new ArrayList<>();
        choose(chosen, true, MAX_ROARS);
        choose(chosen, false, MAX_RUSHES);

        for (Loop loop : playing.values()) {
            loop.target = 0.0F;
            loop.claimed = false;
        }
        for (Source s : chosen) {
            Loop loop = playing.get(s.key);
            if (loop != null && !loop.isStopped()) {
                loop.claimed = true;
            }
        }
        for (Source s : chosen) {
            Loop loop = playing.get(s.key);
            if (loop == null || loop.isStopped()) {
                loop = adopt(s);
            }
            if (loop == null || loop.isStopped()) {
                if (sinceStart < 4 || s.level < START * master) {
                    // start sounds one at a time, a few ticks apart, so they never play in step,
                    // and only once they are clearly heard: a source at the edge of its reach
                    // does not start and stop again as the listener steps back and forth
                    continue;
                }
                float pitch = s.roar ? (float) Mth.clamp(1.06 - 0.07 * Math.log1p(s.strength / 30.0), 0.8, 1.06)
                    : 0.92F + (float) Math.floorMod(s.key * 0x9E3779B97F4A7C15L >>> 40, 18) / 100.0F;
                loop = new Loop(s.roar, pitch);
                playing.put(s.key, loop);
                loop.claimed = true;
                loop.aim(s.nearX, s.nearestY(listener.y), s.nearZ, s.level);
                loop.jump();
                mc.getSoundManager().play(loop);
                sinceStart = 0;
            } else {
                loop.claimed = true;
                loop.aim(s.nearX, s.nearestY(listener.y), s.nearZ, s.level);
            }
        }
        var it = playing.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Loop loop = it.next().getValue();
            if (loop.isStopped()) {
                it.remove();
            } else if (++loop.watched > 5 && !mc.getSoundManager().isActive(loop)) {
                // the sound engine dropped it (no free channel, or no audio device at all): wait a
                // few seconds before starting sounds again instead of retrying every tick
                it.remove();
                sinceStart = -100;
            }
        }
    }

    /**
     * Hands a source a loop that is already playing nearby for the same kind of water, if no
     * other source has taken it this tick. A source's key comes from its blocks, and far from the
     * listener the scanner sees the edge columns of a fall come and go, which changes the key;
     * without this the sound would fade out and start again from the beginning each time.
     */
    private Loop adopt(Source s) {
        long found = 0L;
        Loop best = null;
        double bestDistance = ADOPT * ADOPT;
        var it = playing.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            Loop loop = e.getValue();
            if (loop.claimed || loop.isStopped() || loop.roar != s.roar) {
                continue;
            }
            double dx = loop.targetX - s.nearX;
            double dz = loop.targetZ - s.nearZ;
            double d = dx * dx + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = loop;
                found = e.getLongKey();
            }
        }
        if (best != null) {
            playing.remove(found);
            playing.put(s.key, best);
        }
        return best;
    }

    private void choose(List<Source> chosen, boolean roar, int count) {
        List<Source> kind = new ArrayList<>();
        for (Source s : sources) {
            if (s.roar == roar && s.level > 0.004F) {
                kind.add(s);
            }
        }
        kind.sort(Comparator.comparingDouble((Source s) -> playing.containsKey(s.key) ? s.level * 1.3 : s.level).reversed());
        for (int i = 0; i < Math.min(count, kind.size()); i++) {
            chosen.add(kind.get(i));
        }
    }

    int playing() {
        return playing.size();
    }

    void stopAll(Minecraft mc) {
        for (Loop loop : playing.values()) {
            mc.getSoundManager().stop(loop);
        }
        playing.clear();
    }

    /** Stops everything and forgets the sources, when the world changes. */
    void clear(Minecraft mc) {
        stopAll(mc);
        sources.clear();
    }

    /** One looping water sound, eased towards the volume and position it is given. */
    private static final class Loop extends AbstractTickableSoundInstance {
        final boolean roar;
        float target;
        int watched;
        /** Taken by a source this tick. */
        boolean claimed;
        private int sinceAim;
        private double targetX;
        private double targetY;
        private double targetZ;

        Loop(boolean roar, float pitch) {
            super(roar ? WATERFALL : RAPIDS, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.roar = roar;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0F;
            this.pitch = pitch;
        }

        void aim(double x, double y, double z, float target) {
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
            this.target = target;
            this.sinceAim = 0;
        }

        /** Puts the sound where it is aimed at once (when it starts). */
        void jump() {
            this.x = targetX;
            this.y = targetY;
            this.z = targetZ;
        }

        @Override
        public void tick() {
            // a loop nobody looks after any more (the effects were switched off, the world
            // changed) dies away by itself
            if (++sinceAim > 20) {
                target = 0.0F;
            }
            // glides to a new nearest point (from one step of a cascade to the next) instead of jumping
            x += (targetX - x) * 0.2;
            y += (targetY - y) * 0.2;
            z += (targetZ - z) * 0.2;
            // about a second to swell or die away
            float step = Math.max(0.004F, Math.abs(target - volume) * 0.07F);
            volume = target > volume ? Math.min(target, volume + step) : Math.max(target, volume - step);
            if (target <= 0.0F && volume <= 0.002F) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
