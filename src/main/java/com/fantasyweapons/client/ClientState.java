package com.fantasyweapons.client;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.ability.AbilityState;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-side read model. Everything authoritative comes from the server (item data component + synced
 * {@link AbilityRuntime}); this class only adds a local charge prediction so the HUD reacts the instant the key is
 * pressed, which the server state then confirms or cancels.
 */
public final class ClientState {
    private static long predictedChargeStart = -1;
    private static String predictedAbility = "";
    private static int predictedTicks;

    private ClientState() {
    }

    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    @Nullable
    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static ItemStack heldWeapon(@Nullable Player p) {
        if (p == null) return ItemStack.EMPTY;
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof FantasyWeaponItem ? s : ItemStack.EMPTY;
    }

    @Nullable
    public static AbilityRuntime runtime(@Nullable Player p) {
        return p == null ? null : p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
    }

    /** Called when the local player presses the ability key. */
    public static void predictCharge(WeaponDefinition def, WeaponData data) {
        AbilityDefinition a = AbilityService.selected(def, data);
        if (a == null) return;
        int ticks = ProgressionMath.chargeTicks(a, ProgressionMath.mastery(def, data));
        if (ticks <= 0) return;
        predictedChargeStart = now();
        predictedAbility = a.id();
        predictedTicks = ticks;
    }

    public static void clearPrediction() {
        predictedChargeStart = -1;
        predictedAbility = "";
    }

    /** Charge info for the local player: {abilityId, fraction} or null if not charging. */
    @Nullable
    public static ChargeInfo localCharge() {
        Player p = player();
        AbilityRuntime rt = runtime(p);
        long now = now();
        if (rt != null && rt.isCharging()) {
            clearPrediction();
            float f = rt.chargeTicks() <= 0 ? 1 : Math.min(1f, (now - rt.chargeStart()) / (float) rt.chargeTicks());
            return new ChargeInfo(rt.chargingAbility(), f, rt.chargeTicks());
        }
        if (predictedChargeStart >= 0) {
            // the server confirms within a round trip; if it never does (cooldown, rejected), drop the prediction
            if (now - predictedChargeStart > 12) {
                clearPrediction();
                return null;
            }
            float f = Math.min(1f, (now - predictedChargeStart) / (float) Math.max(1, predictedTicks));
            return new ChargeInfo(predictedAbility, f, predictedTicks);
        }
        return null;
    }

    public record ChargeInfo(String abilityId, float fraction, int ticks) {
    }

    /** Ability state for the HUD/menu. */
    public static AbilityState state(WeaponDefinition def, WeaponData data, AbilityDefinition a) {
        if (!data.isUnlocked(a)) return AbilityState.LOCKED;
        if (a.kind() == AbilityKind.PASSIVE) return AbilityState.UNLOCKED;
        Player p = player();
        AbilityRuntime rt = runtime(p);
        long now = now();
        ChargeInfo ci = localCharge();
        if (ci != null && ci.abilityId().equals(a.id()) && data.idOrNil().equals(rt != null && rt.isCharging() ? rt.chargingWeapon() : data.idOrNil())) {
            return ci.fraction() >= 1f ? AbilityState.FULLY_CHARGED : AbilityState.CHARGING;
        }
        if (rt != null) {
            if (rt.isActive(now) && rt.activeAbility().equals(a.id())) return AbilityState.ACTIVE;
            if (rt.cooldownRemaining(data.idOrNil(), a.id(), now) > 0) return AbilityState.COOLDOWN;
        }
        return AbilityState.READY;
    }

    public static long cooldownRemaining(WeaponData data, AbilityDefinition a) {
        AbilityRuntime rt = runtime(player());
        return rt == null ? 0 : rt.cooldownRemaining(data.idOrNil(), a.id(), now());
    }

    public static int cooldownTotal(WeaponData data, AbilityDefinition a) {
        AbilityRuntime rt = runtime(player());
        return rt == null ? 0 : rt.cooldownTotal(data.idOrNil(), a.id());
    }

    /** True if any player currently has this weapon instance thrown (it must not render in hand). */
    public static boolean isThrown(UUID weaponId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || weaponId == null) return false;
        for (Player p : mc.level.players()) {
            AbilityRuntime rt = p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
            if (rt != null && rt.isThrown(weaponId)) return true;
        }
        return false;
    }

    /** Charge fraction of whoever is charging this weapon instance (0 if nobody). */
    public static float chargeOf(UUID weaponId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || weaponId == null) return 0;
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            AbilityRuntime rt = p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
            if (rt != null && rt.isCharging() && rt.chargingWeapon().equals(weaponId)) {
                return Math.min(1f, (now - rt.chargeStart()) / (float) Math.max(1, rt.chargeTicks()));
            }
        }
        if (mc.player != null && FantasyWeaponItem.data(mc.player.getMainHandItem()).idOrNil().equals(weaponId)) {
            ChargeInfo ci = localCharge();
            if (ci != null) return ci.fraction();
        }
        return 0;
    }
}
