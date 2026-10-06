package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DamageNumberVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.LevelUpVfx;
import com.fantasyweapons.client.vfx.effects.RibbonTrailVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Effects shared by every weapon: melee impacts, swing trails, damage numbers, level-ups, form switches. */
public final class GenericFx {
    private static final Map<Integer, Integer> SWING_COUNT = new HashMap<>();
    private static final Map<Integer, Long> LAST_SWING = new HashMap<>();

    private GenericFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.MELEE_HIT, GenericFx::meleeHit);
        FxDispatcher.register(FxIds.DAMAGE_NUMBER, GenericFx::damageNumber);
        FxDispatcher.register(FxIds.LEVEL_UP, GenericFx::levelUp);
        FxDispatcher.register(FxIds.FORM_SWITCH, GenericFx::formSwitch);
        FxDispatcher.register(FxIds.ABILITY_FIZZLE, GenericFx::fizzle);
        FxDispatcher.register(FxIds.DEATH_DISSOLVE, GenericFx::deathDissolve);
    }

    // ------------------------------------------------------------------------------------------------------------
    // swing trails (client-side, from every observed swing)
    // ------------------------------------------------------------------------------------------------------------

    public static void onSwing(LivingEntity entity, ItemStack stack) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Long last = LAST_SWING.get(entity.getId());
        if (last != null && now - last < 4) return;
        LAST_SWING.put(entity.getId(), now);
        int count = SWING_COUNT.merge(entity.getId(), 1, Integer::sum);
        boolean mirror = count % 2 == 0;

        WeaponDefinition def = item.definition();
        WeaponForm form = def.form(FantasyWeaponItem.data(stack));
        WeaponClass cls = def.weaponClass();
        boolean heavy = entity.isCrouching();
        AnimTracker.onSwing(entity, def, stack, heavy);
        if (InfernochainFx.isInfernochain(stack) && InfernochainFx.chainForm(stack)) {
            // the chainblade's lash is drawn from the real chain (fire sheet + flames), not a generic arc
            InfernochainFx.onSwing(entity);
            return;
        }
        Themes.Theme theme = Themes.of(stack);
        boolean fp = WeaponAnchor.isFirstPersonLocal(entity);

        Vec3 look = entity.getViewVector(1f);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        up = right.cross(look).normalize();
        Vec3 eye = entity.getEyePosition(1f);
        Vec3 center = fp ? eye.add(look.scale(0.55)).subtract(up.scale(0.2)) : eye.subtract(up.scale(0.25)).add(look.scale(0.35));

        float scale = (fp ? 0.75f : 1f) * (heavy ? 1.25f : 1f);
        float reach = cls.cleaveRange() * 0.55f + (form != null ? form.reachBonus() * 0.6f : 0);
        int col = Colors.argb(heavy ? 230 : 190, theme.primary());
        int edge = Colors.argb(255, theme.light());
        int life = heavy ? 14 : 10;

        switch (cls.swingStyle()) {
            case SLASH, HEAVY_SLASH -> {
                double tilt = Math.toRadians(cls.swingStyle() == WeaponClass.SwingStyle.HEAVY_SLASH ? 12 : 32) * (mirror ? -1 : 1);
                Vec3 ax = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
                float a0 = (float) Math.toRadians(-20), a1 = (float) Math.toRadians(200);
                if (mirror) {
                    float tmp = a0;
                    a0 = a1;
                    a1 = tmp;
                }
                VfxManager.add(new SlashArcVfx(center, ax, look, reach * scale, 0.55f * scale, a0, a1, col, edge, life));
            }
            case REAP -> {
                Vec3 ax = right.scale(Math.cos(-0.15)).add(up.scale(Math.sin(-0.15)));
                float a0 = (float) Math.toRadians(-50), a1 = (float) Math.toRadians(230);
                if (mirror) {
                    float tmp = a0;
                    a0 = a1;
                    a1 = tmp;
                }
                VfxManager.add(new SlashArcVfx(center.subtract(up.scale(0.2)), ax, look, reach * 1.1f * scale, 0.7f * scale, a0, a1, col, edge, life + 2)
                        .sweep(0.5f, 0.85f));
            }
            case CHOP, SLAM -> {
                Vec3 c2 = center.add(right.scale(fp ? 0.15 : 0.25));
                float a0 = (float) Math.toRadians(-25), a1 = (float) Math.toRadians(165);
                VfxManager.add(new SlashArcVfx(c2, up, look, reach * scale, 0.65f * scale, a0, a1, col, edge, life + 2).sweep(0.4f, 0.7f));
            }
            case THRUST -> {
                Vec3 from = fp ? eye.add(look.scale(0.6)).subtract(up.scale(0.15)) : center;
                VfxManager.add(new BeamVfx(from, from.add(look.scale(reach * 1.6f)), 0.35f * scale, col, 7));
                VfxManager.add(new FlashVfx(from.add(look.scale(reach * 1.6f)), 0.3f, 1.0f * scale, Colors.argb(200, theme.light()), 6, VfxTextures.SPARK));
            }
        }
        if (form != null && "chainblade".equals(form.id())) {
            // chainblade lash: a fiery ribbon flicked forward along the extended chain
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 10; i++) {
                double t = i / 10.0;
                double sway = Math.sin(t * Math.PI) * 0.9 * (mirror ? -1 : 1);
                path.add(center.add(look.scale(t * reach * 1.4)).add(right.scale(sway)).subtract(up.scale(t * 0.3)));
            }
            VfxManager.add(new RibbonTrailVfx(path, 0.45f, Colors.argb(230, theme.light()), Colors.argb(160, theme.primary()), 9));
        }
        float pitch = 0.9f + entity.getRandom().nextFloat() * 0.2f;
        mc.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                heavy ? ModSounds.WEAPON_SWING_HEAVY.get() : ModSounds.WEAPON_SWING.get(), SoundSource.PLAYERS, 0.7f, pitch, false);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void meleeHit(FxPayload p) {
        Themes.Theme theme = Themes.ofEntity(p.caster());
        boolean crit = (p.level() & FWDamage.FLAG_CRIT) != 0;
        boolean heavy = (p.level() & FWDamage.FLAG_HEAVY) != 0;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int id : p.entities()) {
            Entity e = level.getEntity(id);
            Vec3 c = e != null ? e.getBoundingBox().getCenter() : p.pos();
            float s = heavy ? 1.6f : 1f;
            VfxManager.add(new FlashVfx(c, 0.3f, 1.6f * s, Colors.argb(230, theme.primary()), 7).energy());
            VfxManager.add(new FlashVfx(c, 0.2f, 0.9f * s, Colors.argb(255, theme.light()), 5, VfxTextures.SPARK).rotation(p.seed() % 7));
            VfxManager.add(new ShardBurstVfx(c, p.dir(), 0.7f, 0.35f * s, heavy ? 16 : 9, 0.22f, Colors.argb(255, theme.light()),
                    Colors.argb(0, theme.primary()), 12, p.seed() + id).physics(0.01f, 0.86f));
        }
        if (crit) {
            VfxManager.add(new FlashVfx(p.pos(), 0.5f, 2.6f, Colors.argb(255, 0xFFE27A), 8, VfxTextures.STAR).spin(0.15f));
        }
        if (heavy) {
            Vec3 ground = new Vec3(p.pos().x, Math.floor(p.pos().y - 0.5) + 0.05, p.pos().z);
            VfxManager.add(new ShockwaveVfx(ground, new Vec3(0, 1, 0), 0.3f, 3.5f, 0.6f, Colors.argb(200, theme.primary()), 14));
            GroundShatter.cracks(ground, 1.6f, com.fantasyweapons.client.vfx.GroundMaterial.at(ground), 40);
            CameraShake.add(p.pos(), 0.6f, 10);
        }
    }

    private static void damageNumber(FxPayload p) {
        if (!ClientConfig.get(ClientConfig.DAMAGE_NUMBERS, true)) return;
        int flags = p.level();
        float amount = p.power();
        String text = format(amount);
        int color = 0xFFF1E6;
        float scale = 1f;
        if ((flags & FWDamage.FLAG_DOT) != 0) {
            color = 0xC9B8FF;
            scale = 0.75f;
        } else if ((flags & FWDamage.FLAG_ABILITY) != 0) {
            color = Themes.ofEntity(p.caster()).light();
            scale = 1.15f;
        }
        if ((flags & FWDamage.FLAG_HEAVY) != 0) {
            color = 0xFF9A3C;
            scale *= 1.25f;
        }
        if ((flags & FWDamage.FLAG_CRIT) != 0) {
            color = 0xFFD84A;
            scale *= 1.35f;
            text = text + "!";
        }
        if ((flags & FWDamage.FLAG_EXECUTE) != 0) {
            color = 0xFF3B6B;
            scale = 1.9f;
            text = "EXECUTE " + text;
        }
        VfxManager.add(new DamageNumberVfx(p.pos().add(0, 0.25, 0), text, color, scale, (long) (amount * 31) ^ p.entities().length));
    }

    public static String format(float v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000f);
        if (v >= 100_000) return String.format("%.0fK", v / 1000f);
        return String.format("%,d", Math.round(v));
    }

    private static void levelUp(FxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(p.caster());
        Themes.Theme theme = Themes.ofEntity(p.caster());
        Vec3 base = groundBelow(level, e != null ? e.position() : p.pos());
        boolean unlock = p.power() > 0;
        boolean max = p.level() >= com.fantasyweapons.progression.ProgressionMath.maxLevel();
        VfxManager.add(new LevelUpVfx(base, p.level(), max, unlock, theme.primary(), theme.light(), p.seed()));
        float r = max ? 6.5f : 1.6f + 2.6f * Math.min(1f, p.level() / 100f);
        VfxManager.add(new ShockwaveVfx(base.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5f, r * 2.2f, 0.5f, Colors.argb(200, theme.primary()), max ? 26 : 18));
        VfxManager.add(new ShardBurstVfx(base.add(0, 0.2, 0), new Vec3(0, 1, 0), 0.35f, 0.35f, max ? 60 : 30, 0.25f, Colors.argb(255, theme.light()),
                Colors.argb(0, theme.primary()), max ? 45 : 30, p.seed()).texture(VfxTextures.SPARK, false).physics(-0.004f, 0.94f));
        if (e != null) VfxManager.add(new FlashVfx(Vec3.ZERO, 1f, max ? 6f : 3.2f, Colors.argb(160, theme.light()), max ? 24 : 14).follow(e, new Vec3(0, 1, 0)));
        if (max) {
            ScreenFx.flash(theme.light(), 0.35f, 20);
            CameraShake.add(base, 0.35f, 24);
        }
    }

    /** The top of the first solid block below {@code pos} (within 32 blocks), so effects land on the ground. */
    static Vec3 groundBelow(net.minecraft.world.level.Level level, Vec3 pos) {
        var m = new net.minecraft.core.BlockPos.MutableBlockPos(Math.floor(pos.x), Math.floor(pos.y + 0.2), Math.floor(pos.z));
        for (int i = 0; i < 32; i++, m.move(0, -1, 0)) {
            var st = level.getBlockState(m);
            var shape = st.getCollisionShape(level, m);
            if (!shape.isEmpty()) return new Vec3(pos.x, m.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), pos.z);
        }
        return pos;
    }

    private static void formSwitch(FxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        AnimTracker.onFormSwitch(p.caster());
        Entity e = level.getEntity(p.caster());
        Themes.Theme theme = Themes.ofEntity(p.caster());
        if (!(e instanceof LivingEntity le)) return;
        Vec3 hand = WeaponAnchor.hand(le, 1f);
        Vec3 tip = WeaponAnchor.blade(le, 1f, 2.0);
        for (int i = 0; i <= 4; i++) {
            Vec3 pt = hand.lerp(tip, i / 4.0);
            VfxManager.add(new FlashVfx(pt, 0.3f, 1.3f, Colors.argb(220, theme.primary()), 12 + i * 2).energy());
        }
        VfxManager.add(new ShockwaveVfx(le.position().add(0, 1, 0), new Vec3(0, 1, 0), 0.4f, 3.2f, 0.5f, Colors.argb(220, theme.light()), 14));
        VfxManager.add(new ShardBurstVfx(hand.lerp(tip, 0.5), Vec3.ZERO, 1f, 0.25f, 20, 0.22f, Colors.argb(255, theme.light()),
                Colors.argb(0, theme.secondary()), 18, p.seed() + le.getId()).energy());
    }

    private static void fizzle(FxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(p.caster());
        if (!(e instanceof LivingEntity le)) return;
        Vec3 hand = WeaponAnchor.blade(le, 1f, 1.0);
        VfxManager.add(new ShardBurstVfx(hand, new Vec3(0, 1, 0), 0.8f, 0.08f, 10, 0.5f, Colors.argb(120, 0x8A80A0), Colors.argb(0, 0x403850),
                16, le.getId()).texture(VfxTextures.MIST, false).physics(-0.003f, 0.92f));
    }

    /** A mob killed by a fantasy weapon breaks apart into rising elemental embers (replaces the vanilla puff). */
    private static void deathDissolve(FxPayload p) {
        Element[] elements = Element.values();
        Element el = elements[Math.floorMod(p.level(), elements.length)];
        Vec3 c = p.pos();
        float w = Math.max(0.3f, p.scale()), h = Math.max(0.3f, p.power());
        Vec3 feet = c.add(0, -h / 2 + 0.05, 0);
        int layers = Math.max(2, Math.min(6, Math.round(h / 0.4f)));
        int perLayer = Math.max(6, Math.round(8 + w * 10));
        for (int i = 0; i < layers; i++) {
            Vec3 o = c.add(0, -h / 2 + h * (i + 0.5f) / layers, 0);
            // bright embers drifting up and out
            VfxManager.add(new ShardBurstVfx(o, new Vec3(0, 1, 0), 1.6f, 0.06f + 0.02f * w, perLayer, 0.16f + 0.06f * w,
                    Colors.argb(255, el.light()), Colors.argb(0, el.primary()), 24 + i * 3, p.seed() + i)
                    .texture(VfxTextures.SPARK, false).physics(-0.007f, 0.9f).energy());
            // the body itself turning to coloured smoke
            VfxManager.add(new ShardBurstVfx(o, new Vec3(0, 1, 0), 1.2f, 0.03f, Math.max(3, perLayer / 2), 0.55f + 0.25f * w,
                    Colors.argb(190, el.primary()), Colors.argb(0, el.dark()), 28 + i * 2, p.seed() * 31 + i)
                    .texture(VfxTextures.MIST, false).physics(-0.004f, 0.92f));
        }
        // a short column of light where the body stood
        VfxManager.add(new BeamVfx(feet, feet.add(0, h + 0.8, 0), 0.5f * w + 0.3f, Colors.argb(200, el.primary()), 12));
        VfxManager.add(new FlashVfx(c, w * 0.8f, w * 2.2f + 0.8f, Colors.argb(190, el.primary()), 12).energy());
        VfxManager.add(new ShockwaveVfx(feet, new Vec3(0, 1, 0), 0.2f, w * 1.5f + 1.2f, 0.35f, Colors.argb(220, el.primary()), 16));
    }

    static Vec3[] basis(Vec3 n) {
        return VfxContext.basis(n);
    }
}
