package com.fantasyweapons.ability;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.network.ProgressionEventPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponAnimations;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative ability state machine.
 * <pre>
 *   READY --press--> CHARGING --(full)--> FULLY CHARGED --release--> executor --> COOLDOWN --> READY
 *                       \--release below min charge--> fizzle (short cooldown)
 *                       \--weapon switched / died--> cancelled
 * </pre>
 * Charge time is measured in server ticks, so a client can never fake a charge. Cooldowns are stored per weapon
 * instance in {@link AbilityRuntime} and enforced here.
 */
public final class AbilityService {
    private static final String FORM_COOLDOWN = "#form";
    private static final int MAX_KEY_PACKETS_PER_SECOND = 20;

    private AbilityService() {
    }

    public static long now(ServerPlayer p) {
        return p.serverLevel().getServer().overworld().getGameTime();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Requests from the client
    // ------------------------------------------------------------------------------------------------------------

    public static void handleAbilityKey(ServerPlayer player, boolean pressed) {
        if (!rateLimit(player)) return;
        if (pressed) startCharge(player);
        else release(player, false);
    }

    public static void handleCycle(ServerPlayer player, int direction) {
        if (!rateLimit(player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        AbilityRuntime rt = runtime(player);
        if (rt.isCharging()) return;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        List<AbilityDefinition> usable = def.castables().stream().filter(a -> data.isUnlocked(a) && formAllows(def, data, a)).toList();
        if (usable.isEmpty()) return;
        AbilityDefinition current = selected(def, data);
        int idx = current == null ? 0 : usable.indexOf(current);
        AbilityDefinition next = usable.get(Math.floorMod(idx + (direction >= 0 ? 1 : -1), usable.size()));
        stack.set(ModComponents.WEAPON_DATA.get(), data.withSelected(next.id()));
    }

    public static void handleSelect(ServerPlayer player, int slot, UUID weaponId, String abilityId) {
        if (!rateLimit(player)) return;
        ItemStack stack = stackInSlot(player, slot, weaponId);
        if (stack == null) return;
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityDefinition a = item.definition().ability(abilityId);
        if (a == null || !a.kind().castable() || !data.isUnlocked(a)) return;
        stack.set(ModComponents.WEAPON_DATA.get(), data.withSelected(a.id()));
    }

    public static void handleUpgrade(ServerPlayer player, int slot, UUID weaponId, String abilityId) {
        if (!rateLimit(player)) return;
        ItemStack stack = stackInSlot(player, slot, weaponId);
        if (stack == null) return;
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityDefinition a = def.ability(abilityId);
        if (a == null) return;
        String denied = upgradeDenial(data, a);
        if (denied != null) {
            deny(player, def, denied);
            return;
        }
        int cur = data.abilityLevel(a);
        int cost = a.costFor(cur + 1);
        int extra = data.upgrades().getOrDefault(a.id(), 0) + 1;
        WeaponData nd = data.withUpgrade(a.id(), extra, data.masteryPoints() - cost);
        stack.set(ModComponents.WEAPON_DATA.get(), nd);
        float dmg = ProgressionMath.weaponDamage(def, nd);
        PacketDistributor.sendToPlayer(player, new ProgressionEventPayload(ProgressionEventPayload.Kind.UPGRADE, def.id(),
                cur, cur + 1, dmg, dmg, cost, List.of(a.id()), ""));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.UPGRADE.get(), SoundSource.PLAYERS, 1f, 1f);
    }

    /** Null if the upgrade is allowed, otherwise a short reason shown to the player. Shared with the client UI. */
    @Nullable
    public static String upgradeDenial(WeaponData data, AbilityDefinition a) {
        int cur = data.abilityLevel(a);
        if (cur <= 0) return "Locked until weapon level " + a.unlockLevel();
        if (cur >= a.maxLevel()) return "Already at max level";
        int needLevel = a.weaponLevelFor(cur + 1);
        if (data.level() < needLevel) return "Requires weapon level " + needLevel;
        if (data.masteryPoints() < a.costFor(cur + 1)) return "Insufficient mastery points";
        return null;
    }

    public static void handleSwitchForm(ServerPlayer player) {
        if (!rateLimit(player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        WeaponDefinition def = item.definition();
        if (!def.hasForms()) return;
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityRuntime rt = runtime(player);
        long now = now(player);
        if (rt.isCharging() || rt.isThrown(data.idOrNil())) return;
        if (rt.cooldownRemaining(data.idOrNil(), FORM_COOLDOWN, now) > 0) return;
        int next = Math.floorMod(data.form() + 1, def.forms().size());
        WeaponForm form = def.forms().get(next);
        WeaponData nd = data.withForm(next);
        AbilityDefinition sel = selected(def, nd);
        if (sel != null && !formAllows(def, nd, sel)) nd = nd.withSelected("");
        stack.set(ModComponents.WEAPON_DATA.get(), nd);
        rt.setCooldown(data.idOrNil(), FORM_COOLDOWN, now, 20);
        player.syncData(ModAttachments.ABILITY_RUNTIME);
        WeaponAnimations.trigger(player, stack, "form:" + form.id());
        Fx.tracking(player, FxPayload.of(FxIds.FORM_SWITCH).caster(player.getId()).pos(player.position()).level(next).build());
        SoundEvent sound = def.id().equals("infernochain")
                ? (next == 1 ? ModSounds.CHAIN_TRANSFORM.get() : ModSounds.CHAIN_RETRACT.get())
                : ModSounds.MODE_TRANSFORM.get();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1f, 1f);
        PacketDistributor.sendToPlayer(player, new ProgressionEventPayload(ProgressionEventPayload.Kind.FORM, def.id(),
                data.form(), next, 0, 0, 0, List.of(), form.displayName()));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Charge lifecycle
    // ------------------------------------------------------------------------------------------------------------

    private static void startCharge(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        AbilityRuntime rt = runtime(player);
        if (rt.isCharging() || !player.isAlive() || player.isSpectator()) return;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        if (data.weaponId().isEmpty()) return;
        AbilityDefinition ability = selected(def, data);
        if (ability == null) {
            deny(player, def, def.castables().isEmpty() ? "This weapon has no abilities yet" : "No ability unlocked yet");
            return;
        }
        if (ability.executor() == null) {
            deny(player, def, ability.name() + " is not implemented");
            return;
        }
        if (rt.isThrown(data.idOrNil())) {
            deny(player, def, "Weapon is in flight");
            return;
        }
        long now = now(player);
        if (rt.cooldownRemaining(data.idOrNil(), ability.id(), now) > 0) {
            deny(player, def, ability.name() + " is on cooldown");
            player.syncData(ModAttachments.ABILITY_RUNTIME);
            return;
        }
        float mastery = ProgressionMath.mastery(def, data);
        int chargeTicks = ProgressionMath.chargeTicks(ability, mastery);
        if (chargeTicks <= 0) {
            execute(player, stack, def, data, ability, 1f);
            return;
        }
        rt.startCharge(ability.id(), data.idOrNil(), player.getInventory().selected, now, chargeTicks);
        fullChargeAnnounced.remove(player.getUUID());
        player.syncData(ModAttachments.ABILITY_RUNTIME);
        WeaponAnimations.trigger(player, stack, FantasyWeaponItem.chargeTrigger(ability));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CHARGE_START.get(), SoundSource.PLAYERS, 0.8f, 1f);
    }

    private static final java.util.Set<UUID> fullChargeAnnounced = new java.util.HashSet<>();

    private static void release(ServerPlayer player, boolean auto) {
        AbilityRuntime rt = runtime(player);
        if (!rt.isCharging()) return;
        ItemStack stack = player.getMainHandItem();
        if (!validCharge(player, rt, stack)) {
            cancel(player, rt, stack);
            return;
        }
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityDefinition ability = def.ability(rt.chargingAbility());
        float fraction = rt.chargeFraction(now(player));
        if (ability == null || fraction + 1e-4f < ability.minCharge()) {
            fizzle(player, rt, stack, data);
            return;
        }
        execute(player, stack, def, data, ability, fraction);
    }

    private static void execute(ServerPlayer player, ItemStack stack, WeaponDefinition def, WeaponData data, AbilityDefinition ability, float charge) {
        AbilityRuntime rt = runtime(player);
        rt.clearCharge();
        int level = data.abilityLevel(ability);
        float mastery = ProgressionMath.mastery(def, data);
        AbilityContext ctx = new AbilityContext(player, player.serverLevel(), stack, (FantasyWeaponItem) stack.getItem(), def, data,
                ability, level, charge, ProgressionMath.weaponDamage(def, data), mastery);
        boolean ok;
        String failReason = "No valid target";
        try {
            ok = ability.executor().execute(ctx);
        } catch (AbilityFailedException e) {
            ok = false;
            failReason = e.getMessage();
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("Ability {} failed", ability.key(), e);
            ok = false;
        }
        long now = now(player);
        if (ok) {
            rt.setCooldown(data.idOrNil(), ability.id(), now, ProgressionMath.cooldownTicks(ability, level, mastery));
            WeaponAnimations.trigger(player, stack, "cast:" + ability.id());
        } else {
            rt.setCooldown(data.idOrNil(), ability.id(), now, ServerConfig.FIZZLE_COOLDOWN_TICKS.getOrDefault());
            WeaponAnimations.stop(player, stack, FantasyWeaponItem.chargeTrigger(ability));
            deny(player, def, failReason);
        }
        player.syncData(ModAttachments.ABILITY_RUNTIME);
    }

    private static void fizzle(ServerPlayer player, AbilityRuntime rt, ItemStack stack, WeaponData data) {
        String abilityId = rt.chargingAbility();
        rt.clearCharge();
        rt.setCooldown(data.idOrNil(), abilityId, now(player), ServerConfig.FIZZLE_COOLDOWN_TICKS.getOrDefault());
        player.syncData(ModAttachments.ABILITY_RUNTIME);
        if (stack.getItem() instanceof FantasyWeaponItem item) {
            AbilityDefinition a = item.definition().ability(abilityId);
            if (a != null) WeaponAnimations.stop(player, stack, FantasyWeaponItem.chargeTrigger(a));
        }
        Fx.tracking(player, FxPayload.of(FxIds.ABILITY_FIZZLE).caster(player.getId()).pos(player.position()).build());
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ABILITY_FIZZLE.get(), SoundSource.PLAYERS, 0.7f, 1f);
    }

    private static void cancel(ServerPlayer player, AbilityRuntime rt, ItemStack stack) {
        String abilityId = rt.chargingAbility();
        UUID weapon = rt.chargingWeapon();
        rt.clearCharge();
        player.syncData(ModAttachments.ABILITY_RUNTIME);
        ItemStack chargedStack = findById(player, weapon);
        if (chargedStack != null && chargedStack.getItem() instanceof FantasyWeaponItem item) {
            AbilityDefinition a = item.definition().ability(abilityId);
            if (a != null) WeaponAnimations.stop(player, chargedStack, FantasyWeaponItem.chargeTrigger(a));
        }
    }

    /** Server tick for every player: charge validation, full-charge cue, auto-release, cooldown pruning. */
    public static void tick(ServerPlayer player) {
        AbilityRuntime rt = player.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
        if (rt == null) return;
        long now = now(player);
        if (rt.isCharging()) {
            ItemStack stack = player.getMainHandItem();
            if (!validCharge(player, rt, stack)) {
                cancel(player, rt, stack);
            } else {
                float f = rt.chargeFraction(now);
                if (f >= 1f && fullChargeAnnounced.add(player.getUUID())) {
                    player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FULL_CHARGE.get(), SoundSource.PLAYERS, 1f, 1f);
                }
                int maxHold = ServerConfig.MAX_HOLD_AT_FULL_CHARGE_TICKS.getOrDefault();
                if (maxHold > 0 && now - rt.chargeStart() >= rt.chargeTicks() + maxHold) release(player, true);
            }
        }
        if (player.tickCount % 100 == 0 && rt.pruneCooldowns(now)) player.syncData(ModAttachments.ABILITY_RUNTIME);
    }

    public static void onLogout(ServerPlayer player) {
        AbilityRuntime rt = player.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
        if (rt != null) {
            rt.clearCharge();
            rt.setThrown(null);
        }
        fullChargeAnnounced.remove(player.getUUID());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    public static AbilityRuntime runtime(ServerPlayer player) {
        return player.getData(ModAttachments.ABILITY_RUNTIME);
    }

    /** The ability bound to the ability key: the stored selection if usable, else the first usable one. */
    @Nullable
    public static AbilityDefinition selected(WeaponDefinition def, WeaponData data) {
        AbilityDefinition sel = def.ability(data.selected());
        if (sel != null && sel.kind().castable() && data.isUnlocked(sel) && formAllows(def, data, sel)) return sel;
        for (AbilityDefinition a : def.castables()) {
            if (data.isUnlocked(a) && formAllows(def, data, a)) return a;
        }
        return null;
    }

    public static boolean formAllows(WeaponDefinition def, WeaponData data, AbilityDefinition a) {
        if (a.requiredForm() == null) return true;
        WeaponForm f = def.form(data);
        return f != null && f.id().equals(a.requiredForm());
    }

    private static boolean validCharge(ServerPlayer player, AbilityRuntime rt, ItemStack stack) {
        if (!player.isAlive() || player.isSpectator()) return false;
        if (!(stack.getItem() instanceof FantasyWeaponItem)) return false;
        if (player.getInventory().selected != rt.chargingSlot) return false;
        return FantasyWeaponItem.data(stack).idOrNil().equals(rt.chargingWeapon());
    }

    @Nullable
    private static ItemStack stackInSlot(ServerPlayer player, int slot, UUID weaponId) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return null;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof FantasyWeaponItem)) return null;
        if (!FantasyWeaponItem.data(stack).idOrNil().equals(weaponId)) return null;
        return stack;
    }

    @Nullable
    private static ItemStack findById(ServerPlayer player, UUID weaponId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof FantasyWeaponItem && FantasyWeaponItem.data(s).idOrNil().equals(weaponId)) return s;
        }
        return null;
    }

    private static void deny(ServerPlayer player, WeaponDefinition def, String message) {
        PacketDistributor.sendToPlayer(player, ProgressionEventPayload.denied(def.id(), message));
    }

    private static boolean rateLimit(ServerPlayer player) {
        AbilityRuntime rt = runtime(player);
        long now = now(player);
        if (now - rt.rateWindowStart >= 20) {
            rt.rateWindowStart = now;
            rt.rateWindowCount = 0;
        }
        return ++rt.rateWindowCount <= MAX_KEY_PACKETS_PER_SECOND;
    }
}
