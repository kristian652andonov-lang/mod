package com.fantasyweapons.client.fx;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.effects.ChargeAuraVfx;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Spawns a {@link ChargeAuraVfx} for every player (local or remote) whose synced runtime says they are charging, and
 * plays a looping charge hum for the local player whose pitch rises with the charge.
 */
public final class ClientChargeFx {
    private static final Map<Integer, ChargeAuraVfx> AURAS = new HashMap<>();
    private static ChargeLoop loop;

    private ClientChargeFx() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            AURAS.clear();
            return;
        }
        AURAS.values().removeIf(ChargeAuraVfx::isDead);
        for (Player p : mc.level.players()) {
            AbilityRuntime rt = p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
            if (rt == null || !rt.isCharging()) continue;
            if (AURAS.containsKey(p.getId())) continue;
            if (!(p.getMainHandItem().getItem() instanceof FantasyWeaponItem item)) continue;
            Themes.Theme theme = Themes.of(p.getMainHandItem());
            float blade = switch (item.definition().weaponClass()) {
                case LONGSWORD, CHAINBLADE -> 1.6f;
                case GREATSWORD, BATTLEAXE -> 1.9f;
                case SCYTHE, WARHAMMER -> 2.0f;
                case LANCE -> 2.8f;
                case COLOSSAL -> 3.0f;
            };
            AURAS.put(p.getId(), VfxManager.add(new ChargeAuraVfx(p, theme.primary(), theme.light(), blade)));
        }
        AbilityRuntime own = mc.player == null ? null : mc.player.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
        boolean charging = own != null && own.isCharging();
        if (charging && (loop == null || loop.isStopped())) {
            loop = new ChargeLoop();
            mc.getSoundManager().play(loop);
        }
    }

    public static void clear() {
        AURAS.clear();
    }

    /** Looping hum that follows the local player and rises in pitch while charging. */
    static final class ChargeLoop extends AbstractTickableSoundInstance {
        ChargeLoop() {
            super(ModSounds.CHARGE_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05f;
            this.pitch = 0.7f;
            this.relative = true;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public void tick() {
            var mc = Minecraft.getInstance();
            AbilityRuntime rt = mc.player == null ? null : mc.player.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
            if (rt == null || !rt.isCharging() || mc.level == null) {
                volume -= 0.12f;
                if (volume <= 0) stop();
                return;
            }
            float f = rt.chargeFraction(mc.level.getGameTime());
            volume = Math.min(0.55f, volume + 0.08f);
            pitch = 0.7f + f * 0.8f;
        }
    }
}
