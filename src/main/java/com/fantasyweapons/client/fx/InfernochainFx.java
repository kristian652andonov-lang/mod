package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.render.ChainTracker;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import com.fantasyweapons.client.vfx.effects.ChainMeshVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.DrakeVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client visuals for Infernochain. The centrepiece is {@link ChainFire}: flames riding the real chain segments and a
 * swept sheet of fire behind them, driven by {@link ChainTracker} (positions taken from the rendered, animated model),
 * so the fire follows the artist's Transform / Chainblade attack / Retraction animations exactly.
 * Palette: black, crimson, orange, fire red.
 */
public final class InfernochainFx {
    static final int FIRE = 0xFF5A1F;
    static final int CRIMSON = 0xC0122A;
    static final int CORE = 0xFFD38A;
    static final int EMBER = 0xFF8A2A;
    static final int ASH = 0x1A0A08;
    static final Blast.Palette PALETTE = new Blast.Palette(CORE, FIRE, CRIMSON);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final Map<Integer, ChainFire> FIRES = new HashMap<>();
    private static final Map<Long, Hook> HOOKS = new HashMap<>();

    private InfernochainFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.INFERNO_LASH, InfernochainFx::lash);
        FxDispatcher.register(FxIds.INFERNO_HOOK, InfernochainFx::hook);
        FxDispatcher.register(FxIds.INFERNO_HOOK_END, InfernochainFx::hookEnd);
        FxDispatcher.register(FxIds.INFERNO_CYCLONE, InfernochainFx::cyclone);
        FxDispatcher.register(FxIds.INFERNO_MELTDOWN, InfernochainFx::meltdown);
        FxDispatcher.register(FxIds.INFERNO_DRAKE, InfernochainFx::drake);
        FxDispatcher.register(FxIds.INFERNO_DRAKE_END, InfernochainFx::drakeEnd);
    }

    static boolean isInfernochain(ItemStack stack) {
        return stack.getItem() instanceof FantasyWeaponItem item && "infernochain".equals(item.definition().id());
    }

    static boolean chainForm(ItemStack stack) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return false;
        WeaponForm form = item.definition().form(FantasyWeaponItem.data(stack));
        return form != null && "chainblade".equals(form.id());
    }

    /** Keeps a {@link ChainFire} alive for every player holding Infernochain. */
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            FIRES.clear();
            HOOKS.clear();
            return;
        }
        for (Player p : level.players()) {
            if (!isInfernochain(p.getMainHandItem())) continue;
            ChainFire f = FIRES.get(p.getId());
            if (f == null || f.isDead()) FIRES.put(p.getId(), VfxManager.add(new ChainFire(p.getId())));
        }
        FIRES.values().removeIf(Vfx::isDead);
        HOOKS.values().removeIf(h -> h.vfx.isDead());
    }

    /** A melee swing with the chainblade: the lash leaves a sheet of fire (called instead of the generic slash arc). */
    public static void onSwing(LivingEntity entity) {
        ChainFire f = FIRES.get(entity.getId());
        if (f != null) f.boost(26);
    }

    // ------------------------------------------------------------------------------------------------------------
    // fire riding the real chain
    // ------------------------------------------------------------------------------------------------------------

    static final class ChainFire extends Vfx {
        private static final long KEEP_NANOS = 220_000_000L;
        private final int entityId;
        private final ArrayDeque<Vec3[]> history = new ArrayDeque<>();
        private final ArrayDeque<Long> times = new ArrayDeque<>();
        private long lastNanos;
        private boolean historyFp;
        private int unseen;
        private int boost;

        ChainFire(int entityId) {
            super(Integer.MAX_VALUE);
            this.entityId = entityId;
        }

        void boost(int ticks) {
            boost = Math.max(boost, ticks);
        }

        @Override
        public void tick() {
            super.tick();
            if (boost > 0) boost--;
            if (++unseen > 60) kill();
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId);
            if (e == null || e.isRemoved()) {
                kill();
                return;
            }
            // embers shed from the lashing chain
            boolean spinning = e instanceof LivingEntity le && AnimTracker.spinAngle(le, 0) != 0;
            if ((boost > 0 || spinning) && !history.isEmpty() && age % 2 == 0) {
                Vec3[] p = history.peekFirst();
                Vec3 tip = historyFp ? ChainTracker.viewToWorld(p[7]) : p[7];
                VfxManager.add(new ShardBurstVfx(tip, UP, 0.8f, 0.08f, 3, 0.18f, Colors.argb(255, CORE), Colors.argb(0, CRIMSON), 16, age * 31L + entityId)
                        .texture(VfxTextures.SPARK, true).physics(0.01f, 0.92f));
            }
        }

        @Override
        public void render(VfxContext ctx) {
            ChainTracker.Sample s = ChainTracker.get(entityId, 150);
            if (s == null) return;
            unseen = 0;
            if (s.firstPerson != historyFp) {
                history.clear();
                times.clear();
                historyFp = s.firstPerson;
            }
            if (s.nanos != lastNanos) {
                lastNanos = s.nanos;
                // through the wielder's own eyes the chain is kept in view space, so turning the head does not
                // sweep a sheet of fire across the world - only the chain's own lashing does
                history.addFirst(s.firstPerson ? s.view.clone() : s.pts.clone());
                times.addFirst(s.nanos);
            }
            long now = System.nanoTime();
            while (times.size() > 1 && (now - times.peekLast() > KEEP_NANOS || times.size() > 28)) {
                times.removeLast();
                history.removeLast();
            }
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId);
            if (!(e instanceof LivingEntity le)) return;
            ItemStack stack = le.getMainHandItem();
            if (!isInfernochain(stack)) return;
            boolean chain = chainForm(stack);
            StatusEffects st = le.getExistingDataOrNull(ModAttachments.STATUS);
            int heat = st == null ? 0 : st.stacks(StatusType.INFERNO_OVERHEAT);
            boolean spinning = AnimTracker.spinAngle(le, ctx.partial) != 0;
            float active = boost > 0 || spinning ? 1f : 0f;
            float flames = Math.max(active, (chain ? 0.4f : 0f) + heat * 0.12f);
            float time = age + ctx.partial;

            // flames licking off every segment (and the tip)
            if (flames > 0.02f) {
                var flame = ctx.additive(VfxTextures.FLAME);
                for (int i = 0; i < ChainTracker.POINTS; i++) {
                    float flick = 0.7f + 0.3f * (float) Math.sin(time * 1.7 + i * 2.3);
                    float size = (0.28f + 0.22f * flames) * flick * (i == 7 ? 1.3f : 1f);
                    ctx.stretched(flame, s.pts[i].add(0, size * 0.35, 0), UP, size * 1.7f, size, Colors.alpha(Math.min(1, flames) * 0.85f * flick,
                            Colors.lerpRgb(FIRE, CORE, flick - 0.55f)));
                }
                var glow = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < ChainTracker.POINTS; i += 2) {
                    ctx.billboard(glow, s.pts[i], 0.9f + 0.5f * flames, 0, Colors.alpha(0.22f * Math.min(1, flames), FIRE));
                }
            }

            // a sheet of fire swept by the chain between its middle and its tip
            if (history.size() < 2) return;
            java.util.List<Vec3[]> world = new java.util.ArrayList<>(history.size());
            for (Vec3[] h : history) {
                if (!historyFp) {
                    world.add(h);
                    continue;
                }
                Vec3[] w = new Vec3[h.length];
                for (int i = 0; i < h.length; i++) w[i] = h[i] == null ? null : ChainTracker.viewToWorld(h[i]);
                world.add(w);
            }
            double travel = 0;
            Vec3[] prev = null;
            for (Vec3[] h : world) {
                if (prev != null) travel += h[7].distanceTo(prev[7]);
                prev = h;
            }
            float sheet = (float) Math.min(1, travel / 2.5) * Math.max(active, chain ? 0.35f : 0.15f);
            if (sheet < 0.03f) return;
            // the recorded frames are uneven and few, so the swept band is drawn through a smooth curve resampled
            // from them, with a soft wispy texture (bright along the tip's path, fraying out towards the hand)
            // instead of flat polygons
            int m = world.size();
            Vec3[][] hs = world.toArray(new Vec3[0][]);
            int n = Math.min(40, (m - 1) * 4);
            Vec3[] inner = new Vec3[n + 1], outer = new Vec3[n + 1];
            for (int j = 0; j <= n; j++) {
                float f = j * (m - 1) / (float) n;
                inner[j] = catmull(hs, 3, f);
                outer[j] = catmull(hs, 7, f);
            }
            for (int pass = 0; pass < 2; pass++) {
                var vc = pass == 0 ? ctx.energy(VfxTextures.SLASH) : ctx.additive(VfxTextures.SLASH);
                float aMul = pass == 0 ? 0.5f : 0.6f;
                for (int j = 0; j < n; j++) {
                    float k0 = j / (float) n, k1 = (j + 1) / (float) n;
                    // the band narrows towards its old end and tucks in towards the tip there
                    float in0 = 1 - k0 * 0.55f, in1 = 1 - k1 * 0.55f;
                    Vec3 a0 = outer[j].lerp(inner[j], pass == 0 ? in0 : in0 * 0.45f), a1 = outer[j + 1].lerp(inner[j + 1], pass == 0 ? in1 : in1 * 0.45f);
                    int c0 = Colors.alpha(sheet * fadeTail(k0) * aMul, Colors.lerpRgb(pass == 0 ? FIRE : EMBER, CRIMSON, k0 * 1.2f));
                    int c1 = Colors.alpha(sheet * fadeTail(k1) * aMul, Colors.lerpRgb(pass == 0 ? FIRE : EMBER, CRIMSON, k1 * 1.2f));
                    ctx.vertex(vc, a0, 1 - k0, 0, c0);
                    ctx.vertex(vc, a1, 1 - k1, 0, c1);
                    ctx.vertex(vc, outer[j + 1], 1 - k1, 0.8f, c1);
                    ctx.vertex(vc, outer[j], 1 - k0, 0.8f, c0);
                }
            }
            // white-hot edge along the tip's path
            float[] w = new float[n + 1];
            int[] cs = new int[n + 1];
            for (int j = 0; j <= n; j++) {
                float k = j / (float) n;
                w[j] = 0.2f * (1 - k) * Math.max(0.4f, sheet);
                cs[j] = Colors.alpha(sheet * fadeTail(k) * 0.85f, Colors.lerpRgb(CORE, FIRE, 0.3f + k));
            }
            ctx.ribbon(ctx.additive(VfxTextures.STREAK), outer, w, cs, 0, 0.1f);
        }
    }

    /** Fades the swept band out over its last stretch (k = 0 newest .. 1 oldest), softly instead of linearly. */
    private static float fadeTail(float k) {
        float t = 1 - k;
        return t * t * (3 - 2 * t);
    }

    /** Point {@code idx} of the recorded chain, smoothly interpolated (Catmull-Rom) at fractional frame {@code f}. */
    private static Vec3 catmull(Vec3[][] hs, int idx, float f) {
        int last = hs.length - 1;
        int i = Math.min(last - 1, (int) Math.floor(f));
        float t = f - i;
        Vec3 p0 = hs[Math.max(0, i - 1)][idx], p1 = hs[i][idx], p2 = hs[i + 1][idx], p3 = hs[Math.min(last, i + 2)][idx];
        float t2 = t * t, t3 = t2 * t;
        double a = -0.5 * t3 + t2 - 0.5 * t, b = 1.5 * t3 - 2.5 * t2 + 1, c = -1.5 * t3 + 2 * t2 + 0.5 * t, d = 0.5 * t3 - 0.5 * t2;
        return new Vec3(p0.x * a + p1.x * b + p2.x * c + p3.x * d, p0.y * a + p1.y * b + p2.y * c + p3.y * d, p0.z * a + p1.z * b + p2.z * c + p3.z * d);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Inferno Lash
    // ------------------------------------------------------------------------------------------------------------

    private static void lash(FxPayload p) {
        Vec3 c = p.pos();
        Vec3 look = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        float range = p.scale();
        float angle = (float) Math.toRadians(p.power());
        boolean chain = p.level() == 1;
        int delay = chain ? 4 : 2;
        Entity caster = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.caster());
        if (caster instanceof LivingEntity le) onSwing(le);
        FxScheduler.after(delay, () -> {
            VfxManager.add(new SlashArcVfx(c.add(0, chain ? -0.3 : 0, 0), look, side, range * 0.92f, chain ? 2.4f : 1.8f, -angle / 2, angle / 2,
                    Colors.argb(245, FIRE), Colors.argb(255, CORE), chain ? 14 : 9).sweep(0.55f, 0.7f));
            VfxManager.add(new SlashArcVfx(c.add(0, chain ? -0.1 : 0.2, 0), look, side, range * 0.7f, 0.8f, -angle / 2, angle / 2,
                    Colors.argb(190, CRIMSON), Colors.argb(230, EMBER), chain ? 13 : 10).sweep(0.6f, 0.5f));
            RandomSource r = RandomSource.create(p.seed());
            int bursts = chain ? 9 : 6;
            for (int i = 0; i < bursts; i++) {
                double a = -angle / 2 + angle * (i + 0.5) / bursts;
                Vec3 dir = look.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
                Vec3 at = c.add(dir.scale(range * (0.6 + r.nextDouble() * 0.35)));
                VfxManager.add(new ShardBurstVfx(at, UP, 0.6f, 0.12f, 5, 0.55f, Colors.argb(240, EMBER), Colors.argb(0, CRIMSON), 20, r.nextLong())
                        .texture(VfxTextures.FLAME, false).physics(-0.01f, 0.9f));
            }
            // a ring of fire rolling over the ground around the lash (none when it is cracked high in the air)
            Vec3 floor = chain ? null : FrostrendFx.groundOrNull(c.add(0, -0.6, 0), 1.5);
            if (floor != null) VfxManager.add(new ShockwaveVfx(floor.add(0, 0.3, 0), UP, 0.5f, range * 1.1f, 0.28f, Colors.argb(140, FIRE), 8).energy());
            CameraShake.add(c, chain ? 0.35f : 0.5f, range * 2);
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hellhook
    // ------------------------------------------------------------------------------------------------------------

    /** Client-side state of a thrown hook (linked to its end payload by seed). */
    private static final class Hook {
        final ChainMeshVfx vfx;
        final Vec3 start;
        final Vec3 vel;
        final double range;
        final long born;
        Vec3 end;
        long endTime = -1;
        int mode; // 0 missed, 1 hooked an enemy, 2 bit into a wall
        int victim = -1;

        Hook(ChainMeshVfx vfx, Vec3 start, Vec3 vel, double range, long born) {
            this.vfx = vfx;
            this.start = start;
            this.vel = vel;
            this.range = range;
            this.born = born;
        }
    }

    private static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    private static void hook(FxPayload p) {
        Entity caster = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.caster());
        if (!(caster instanceof LivingEntity owner)) return;
        Vec3 start = p.pos(), vel = p.dir();
        double range = p.scale();
        long born = Minecraft.getInstance().level.getGameTime();
        Hook[] holder = new Hook[1];
        ChainMeshVfx chain = new ChainMeshVfx("infernochain", () -> WeaponAnchor.hand(owner, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)),
                () -> headOf(holder[0], owner), 2.2f, FIRE, 60).head("segment_6").sag(0.15f);
        Hook h = new Hook(chain, start, vel, range, born);
        holder[0] = h;
        HOOKS.put(p.seed(), h);
        VfxManager.add(chain);
        VfxManager.add(new FollowTrailVfx(() -> chain.isDead() ? null : headOf(h, owner), 0.5f, Colors.argb(230, FIRE), Colors.argb(0, CRIMSON), 10, 60));
    }

    /** Where the hook's head is now: flying out, stuck (in a wall / an enemy), or being reeled back to the hand. */
    private static Vec3 headOf(Hook h, LivingEntity owner) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double t = now(partial);
        Vec3 hand = WeaponAnchor.hand(owner, partial);
        if (h.endTime < 0) {
            double flown = Math.min(h.range, h.vel.length() * (t - h.born));
            return h.start.add(h.vel.normalize().scale(flown));
        }
        double since = t - h.endTime;
        Vec3 stuck = h.end;
        if (h.mode == 1 && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(h.victim) instanceof LivingEntity v) {
            stuck = v.getBoundingBox().getCenter();
        }
        double hold = h.mode == 2 ? 10 : h.mode == 1 ? 6 : 0;
        if (since < hold) return stuck;
        double k = Math.min(1, (since - hold) / 6.0);
        if (k >= 1) h.vfx.kill();
        return stuck.lerp(hand, k * k);
    }

    private static void hookEnd(FxPayload p) {
        Hook h = HOOKS.get(p.seed());
        Vec3 at = p.pos();
        if (h != null) {
            h.end = at;
            h.endTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            h.mode = p.level();
            if (p.entities().length > 0) h.victim = p.entities()[0];
        }
        if (p.level() > 0) {
            VfxManager.add(new FlashVfx(at, 0.4f, 2.2f, Colors.argb(240, CORE), 6).energy());
            VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.25f, 14, 0.4f, Colors.argb(255, CORE), Colors.argb(0, CRIMSON), 18, p.seed())
                    .texture(VfxTextures.FLAME, false).physics(0.0f, 0.88f));
            CameraShake.add(at, 0.3f, 10);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Cinder Cyclone
    // ------------------------------------------------------------------------------------------------------------

    private static void cyclone(FxPayload p) {
        int duration = Math.max(1, Math.round(p.power()));
        float r = p.scale();
        AnimTracker.onSpin(p.caster(), duration);
        Entity caster = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.caster());
        if (caster instanceof LivingEntity le) onSwing(le);
        if (caster == null) return;
        FIRES.computeIfPresent(caster.getId(), (k, f) -> {
            f.boost(duration);
            return f;
        });
        VfxManager.add(new Vfx(duration + 6) {
            @Override
            public void render(VfxContext ctx) {
                float t = age + ctx.partial;
                float a = t < 4 ? t / 4 : t > duration ? clamp01((duration + 6 - t) / 6) : 1;
                Vec3 c = caster.getPosition(ctx.partial).add(0, 0.9, 0);
                float spin = -t * 1.05f;
                var flame = ctx.additive(VfxTextures.FLAME);
                int n = 28;
                for (int i = 0; i < n; i++) {
                    double ang = spin + i * Math.PI * 2 / n;
                    float flick = 0.7f + 0.3f * (float) Math.sin(t * 2.1 + i * 1.7);
                    for (int ring = 0; ring < 2; ring++) {
                        double rr = r * (ring == 0 ? 0.92 : 0.6);
                        Vec3 at = c.add(Math.cos(ang + ring) * rr, ring * 0.3 - 0.4, Math.sin(ang + ring) * rr);
                        Vec3 tangent = new Vec3(-Math.sin(ang + ring), 0.25, Math.cos(ang + ring));
                        ctx.stretched(flame, at, tangent, 1.6f * flick, 0.7f * flick, Colors.alpha(a * flick * (ring == 0 ? 0.8f : 0.55f),
                                Colors.lerpRgb(ring == 0 ? FIRE : CRIMSON, CORE, flick - 0.6f)));
                    }
                }
                ctx.ring(ctx.additive(VfxTextures.RING), c.add(0, -0.85, 0), new Vec3(Math.cos(spin), 0, Math.sin(spin)),
                        new Vec3(-Math.sin(spin), 0, Math.cos(spin)), r * 0.15f, r * 1.02f, 40, Colors.alpha(a * 0.35f, FIRE));
            }
        });
        Vec3 cg = FrostrendFx.groundOrNull(caster.position(), 2.5);
        if (cg != null) GroundShatter.cracks(cg, r, com.fantasyweapons.client.vfx.GroundMaterial.at(cg), duration + 30, FIRE, EMBER, p.seed() * 3);
        for (int t = 0; t < duration; t += 4) {
            int tt = t;
            FxScheduler.after(tt, () -> {
                if (caster.isRemoved()) return;
                VfxManager.add(new ShardBurstVfx(caster.position().add(0, 0.8, 0), UP, 1f, 0.32f, 8, 0.45f, Colors.argb(255, CORE), Colors.argb(0, CRIMSON),
                        18, p.seed() + tt).texture(VfxTextures.FLAME, false).physics(-0.005f, 0.9f));
                CameraShake.add(caster.position(), 0.15f, 8);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Overheat: Meltdown
    // ------------------------------------------------------------------------------------------------------------

    private static void meltdown(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        Blast.explode(FrostrendFx.ground(c), r, PALETTE, p.seed(), 1, VfxTextures.FLAME);
        VfxManager.add(new ShockwaveVfx(c, UP, 0.5f, r * 1.2f, 0.5f, Colors.argb(220, FIRE), 8).energy());
        VfxManager.add(new FlashVfx(c, 0.6f, r * 0.9f, Colors.argb(220, CORE), 6).energy());
        CameraShake.add(c, 0.5f, r * 3);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Drake's Wrath
    // ------------------------------------------------------------------------------------------------------------

    private static void drake(FxPayload p) {
        Entity caster = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(p.caster());
        if (!(caster instanceof LivingEntity owner)) return;
        Vec3 from = p.pos();
        Vec3 target = p.points().isEmpty() ? from.add(0, -7, 8) : p.points().get(0);
        int rise = Math.max(4, Math.round(p.power()));
        int dive = Math.max(2, p.level());
        Vec3 center = owner.position();
        Vec3 hand = WeaponAnchor.hand(owner, 1f);
        // a coiling rise around the wielder that ends where the dive begins
        double aEnd = Math.atan2(from.z - center.z, from.x - center.x);
        double rEnd = Math.hypot(from.x - center.x, from.z - center.z);
        double aStart = aEnd - Math.PI * 2.5;
        List<Vec3> rise3 = new ArrayList<>();
        rise3.add(hand);
        int steps = 60;
        for (int i = 1; i <= steps; i++) {
            double k = (double) i / steps;
            double ang = aStart + (aEnd - aStart) * k;
            double rad = 0.8 + (rEnd - 0.8) * k + Math.sin(k * Math.PI) * 2.2;
            double y = hand.y + (from.y - hand.y) * Vfx.easeInOut((float) k);
            rise3.add(new Vec3(center.x + Math.cos(ang) * rad, y, center.z + Math.sin(ang) * rad));
        }
        DrakeVfx d = VfxManager.add(new DrakeVfx(rise3, target, rise, dive, 5.2f, FIRE, CORE));
        VfxManager.add(new FollowTrailVfx(() -> d.isDead() ? null : d.head(), 3.6f, Colors.argb(220, FIRE), Colors.argb(0, CRIMSON), 22, rise + dive + 40));
        VfxManager.add(new FollowTrailVfx(() -> d.isDead() ? null : d.head(), 6.8f, Colors.argb(90, CRIMSON), Colors.argb(0, ASH), 26, rise + dive + 40)
                .texture(VfxTextures.GLOW, false));
        ScreenFx.zoneVignette(CRIMSON, 0.18f, rise + dive + 20, center, 30);
        FxScheduler.after(rise - 8, () -> CameraShake.add(center, 0.6f, 30)); // the roar
        // warning glyph where it will land
        VfxManager.add(new DecalVfx(target.add(0, 0.05, 0), UP, 5f, Colors.argb(220, FIRE), VfxTextures.RUNE_CIRCLE, rise + dive + 2)
                .spin(0.06f).energy().timing(0.5f, 0.05f));
    }

    private static void drakeEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int burn = Math.max(20, Math.round(p.power()));
        Vec3 from = p.points().isEmpty() ? c : p.points().get(0);
        Blast.explode(c, r, PALETTE, p.seed(), 2, VfxTextures.FLAME);
        Vec3 cg = FrostrendFx.ground(c);
        GroundShatter.cracks(cg, r * 1.1f, com.fantasyweapons.client.vfx.GroundMaterial.at(cg), burn + 40, FIRE, EMBER, p.seed() * 5);
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.3, 0), UP, 1f, r * 1.4f, 0.7f, Colors.argb(220, FIRE), 12).energy());
        CameraShake.add(c, 1.3f, r * 4);
        ScreenFx.flash(EMBER, 0.15f, 6);
        // the scorched path keeps burning
        RandomSource rnd = RandomSource.create(p.seed());
        Vec3 mid = from.lerp(c, 0.35);
        Vec3 pathStart = FrostrendFx.groundOrNull(new Vec3(mid.x, c.y + 2, mid.z), 12);
        Vec3 pathFrom = pathStart != null ? pathStart : c;
        for (int i = 0; i <= 6; i++) {
            Vec3 at = FrostrendFx.groundOrNull(pathFrom.lerp(c, i / 6.0), 6.5);
            if (at != null && i % 2 == 0) GroundShatter.cracks(at, 2.0f + rnd.nextFloat(), com.fantasyweapons.client.vfx.GroundMaterial.at(at), burn + 30, FIRE, EMBER,
                    rnd.nextLong());
        }
        for (int t = 0; t < burn; t += 3) {
            int tt = t;
            FxScheduler.after(tt, () -> {
                RandomSource rr = RandomSource.create(p.seed() + tt);
                for (int k = 0; k < 3; k++) {
                    Vec3 g = FrostrendFx.groundOrNull(pathFrom.lerp(c, rr.nextDouble()), 6.5);
                    if (g == null) continue;
                    Vec3 at = g.add(rr.nextGaussian() * 0.8, 0.1, rr.nextGaussian() * 0.8);
                    VfxManager.add(new ShardBurstVfx(at, UP, 0.25f, 0.08f, 3, 0.6f, Colors.argb(230, EMBER), Colors.argb(0, CRIMSON), 22, rr.nextLong())
                            .texture(VfxTextures.FLAME, false).physics(-0.012f, 0.92f));
                }
            });
        }
    }

    public static void clear() {
        for (Iterator<ChainFire> it = FIRES.values().iterator(); it.hasNext(); ) {
            it.next().kill();
            it.remove();
        }
        HOOKS.clear();
    }
}
