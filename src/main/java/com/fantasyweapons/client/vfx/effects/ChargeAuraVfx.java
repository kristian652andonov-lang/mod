package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import com.fantasyweapons.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Energy gathering around a charging weapon, visible to everyone: motes spiral into the blade, the blade glow swells,
 * a rune circle forms under the holder's feet and, at full charge, the whole aura pulses at maximum intensity.
 * Driven only by the synced charge state; ends itself when charging stops.
 */
public class ChargeAuraVfx extends Vfx {
    private static final int MOTES = 28;
    private final int playerId;
    private final int color;
    private final int colorLight;
    private final float bladeLength;
    private final float[] moteAngle = new float[MOTES];
    private final float[] moteHeight = new float[MOTES];
    private final float[] motePhase = new float[MOTES];
    private float charge;
    private float prevCharge;
    private int fullTicks;
    private boolean ending;
    private int endTicks;

    public ChargeAuraVfx(Player player, int color, int colorLight, float bladeLength) {
        super(20 * 60);
        this.playerId = player.getId();
        this.color = color;
        this.colorLight = colorLight;
        this.bladeLength = bladeLength;
        RandomSource r = RandomSource.create(player.getId());
        for (int i = 0; i < MOTES; i++) {
            moteAngle[i] = r.nextFloat() * 6.283f;
            moteHeight[i] = r.nextFloat() * 1.8f - 0.2f;
            motePhase[i] = r.nextFloat();
        }
    }

    public int playerId() {
        return playerId;
    }

    @Override
    public void tick() {
        super.tick();
        prevCharge = charge;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.level != null && mc.level.getEntity(playerId) instanceof Player pl ? pl : null;
        AbilityRuntime rt = p == null ? null : p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
        if (p == null || rt == null || !rt.isCharging()) {
            ending = true;
        } else {
            long now = mc.level.getGameTime();
            charge = Math.min(1f, Math.max(0f, (now - rt.chargeStart()) / (float) Math.max(1, rt.chargeTicks())));
            if (charge >= 1f) fullTicks++;
        }
        if (ending && ++endTicks > 6) dead = true;
    }

    @Override
    public void render(VfxContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(playerId) instanceof Player p)) return;
        float c = prevCharge + (charge - prevCharge) * ctx.partial;
        float fade = ending ? 1 - Math.min(1, (endTicks + ctx.partial) / 6f) : Math.min(1, (age + ctx.partial) / 4f);
        if (fade <= 0) return;
        boolean full = fullTicks > 0;
        float pulse = full ? 0.75f + 0.25f * (float) Math.sin((fullTicks + ctx.partial) * 0.9f) : 1f;
        boolean fp = WeaponAnchor.isFirstPersonLocal(p);

        Vec3 hand = WeaponAnchor.hand(p, ctx.partial);
        Vec3 tip = WeaponAnchor.blade(p, ctx.partial, bladeLength);
        Vec3 mid = hand.add(tip).scale(0.5);

        // blade glow column: brighter and wider as charge grows
        var glow = ctx.additive(VfxTextures.GLOW);
        int steps = fp ? 3 : 5;
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = hand.lerp(tip, i / (float) steps);
            float s = (fp ? 0.35f : 0.7f) * (0.4f + c * 1.1f) * pulse;
            ctx.billboard(glow, pt, s, 0, Colors.alpha(fade * (0.18f + 0.45f * c), color));
        }
        // white-hot core at full charge
        if (full) ctx.billboard(ctx.additive(VfxTextures.FLASH), mid, (fp ? 0.5f : 1.1f) * pulse, 0, Colors.alpha(fade * 0.55f, colorLight));

        // converging motes (spiral inwards to the blade): small element-tinted glows with a faint bright core
        // (two passes: one buffer per render type, never interleaved)
        if (!fp || c > 0.05f) {
            int n = Math.round(MOTES * Math.min(1f, ctx.density) * (0.3f + 0.7f * c));
            float time = (age + ctx.partial) / 20f;
            Vec3 feet = p.getPosition(ctx.partial);
            Vec3[] pos = new Vec3[n];
            float[] alpha = new float[n], cyc = new float[n], ang = new float[n];
            for (int i = 0; i < n; i++) {
                cyc[i] = (time * (0.6f + c * 0.9f) + motePhase[i]) % 1f;
                float radius = (fp ? 1.1f : 1.8f) * (1 - cyc[i]);
                ang[i] = moteAngle[i] + cyc[i] * 5f;
                Vec3 origin = feet.add(Math.cos(ang[i]) * radius, moteHeight[i] + 0.3, Math.sin(ang[i]) * radius);
                pos[i] = origin.lerp(mid, cyc[i] * cyc[i]);
                alpha[i] = fade * Math.min(1, cyc[i] * 4) * (1 - cyc[i] * 0.3f) * (0.4f + 0.6f * c);
            }
            float size = (fp ? 0.09f : 0.13f) + 0.07f * c;
            var motes = ctx.additive(VfxTextures.GLOW);
            for (int i = 0; i < n; i++) ctx.billboard(motes, pos[i], size, 0, Colors.alpha(alpha[i] * 0.85f, color));
            var cores = ctx.additive(VfxTextures.SPARK);
            for (int i = 0; i < n; i++) {
                ctx.billboard(cores, pos[i], size * 0.35f, ang[i], Colors.alpha(alpha[i] * (0.3f + 0.5f * cyc[i]), Colors.lerpRgb(color, colorLight, cyc[i])));
            }
        }

        // rune circle under the feet (third person or when looking down)
        if (c > 0.15f) {
            Vec3 feet = p.getPosition(ctx.partial).add(0, 0.03, 0);
            float r = 1.1f + c * 0.9f;
            float a = fade * Math.min(1, (c - 0.15f) * 2f) * (full ? pulse : 0.75f);
            ctx.disc(ctx.energy(VfxTextures.RUNE_CIRCLE), feet, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, (age + ctx.partial) * 0.04f,
                    Colors.alpha(a, color));
            ctx.disc(ctx.additive(VfxTextures.GLOW), feet, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 1.2f, 0, Colors.alpha(a * 0.35f, color));
        }

        // full charge: expanding pulse rings around the body
        if (full) {
            float cyc = ((fullTicks + ctx.partial) % 14) / 14f;
            Vec3 center = p.getPosition(ctx.partial).add(0, p.getBbHeight() * 0.5, 0);
            ctx.disc(ctx.additive(VfxTextures.RING), center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.9f + cyc * 1.8f, 0,
                    Colors.alpha(fade * (1 - cyc) * 0.6f, colorLight));
        }
    }
}
