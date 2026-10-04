package com.fantasyweapons.progression;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.combat.HitTracker;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.network.ProgressionEventPayload;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only weapon EXP. The client can never grant EXP: it is decided here from server-side death events and the
 * server-side hit tracker, and each victim can only ever be rewarded once.
 */
public final class ExpService {
    /** Victims rewarded recently: guards against duplicate death events for the same entity. */
    private static final Set<UUID> RECENT = Collections.newSetFromMap(new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 8192;
        }
    });

    private ExpService() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        boolean isPlayer = victim instanceof Player;
        if (!(victim instanceof Mob) && !(isPlayer && ServerConfig.PLAYER_KILLS_GIVE_EXP.getOrDefault())) return;

        long now = level.getServer().overworld().getGameTime();
        HitTracker tracker = victim.getExistingDataOrNull(ModAttachments.HIT_TRACKER);
        if (tracker != null && tracker.rewarded) return;

        ServerPlayer player = null;
        ItemStack weapon = ItemStack.EMPTY;
        if (tracker != null && tracker.player != null && now - tracker.gameTime <= ServerConfig.KILL_CREDIT_WINDOW_TICKS.getOrDefault()) {
            player = level.getServer().getPlayerList().getPlayer(tracker.player);
            if (player != null) weapon = findWeapon(player, tracker.weapon);
        }
        if (weapon.isEmpty()) {
            Entity src = event.getSource().getEntity();
            if (src instanceof ServerPlayer sp && sp.getMainHandItem().getItem() instanceof FantasyWeaponItem) {
                player = sp;
                weapon = sp.getMainHandItem();
            }
        }
        if (player == null || weapon.isEmpty() || player == victim) return;
        victim.getData(ModAttachments.HIT_TRACKER).rewarded = true;
        if (!RECENT.add(victim.getUUID())) return;

        ExpTier tier = isPlayer ? ExpTier.ELITE : ExpTier.classify(victim);
        double exp = tier.exp() * ServerConfig.EXP_MULTIPLIER.getOrDefault();
        if (victim instanceof Mob mob && mob.getSpawnType() == MobSpawnType.SPAWNER) {
            exp *= ServerConfig.SPAWNER_EXP_MULTIPLIER.getOrDefault();
        }
        exp = Math.min(exp, ServerConfig.MAX_EXP_PER_KILL.getOrDefault());
        addExp(player, weapon, Math.round(exp), tier == ExpTier.BOSS, true);
    }

    /** Finds a weapon stack by its instance id anywhere in the player's inventory. */
    public static ItemStack findWeapon(ServerPlayer player, UUID weaponId) {
        if (weaponId == null) return ItemStack.EMPTY;
        for (ItemStack s : player.getInventory().items) if (matches(s, weaponId)) return s;
        for (ItemStack s : player.getInventory().offhand) if (matches(s, weaponId)) return s;
        return ItemStack.EMPTY;
    }

    private static boolean matches(ItemStack s, UUID id) {
        return s.getItem() instanceof FantasyWeaponItem && FantasyWeaponItem.data(s).weaponId().map(id::equals).orElse(false);
    }

    /**
     * Adds EXP to a weapon, processing any number of level-ups, mastery point grants and ability unlocks, then
     * notifies the owner.
     */
    public static void addExp(ServerPlayer player, ItemStack stack, long amount, boolean bossKill, boolean countKill) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item) || amount < 0) return;
        WeaponDefinition def = item.definition();
        WeaponData d = FantasyWeaponItem.data(stack);
        int max = ProgressionMath.maxLevel();
        int oldLevel = d.level();
        float oldDamage = ProgressionMath.weaponDamage(def, d);

        int level = oldLevel;
        long exp = d.exp() + amount;
        long total = d.totalExp() + amount;
        int points = d.masteryPoints();
        while (level < max) {
            long need = ProgressionMath.expToNext(level);
            if (exp < need) break;
            exp -= need;
            level++;
            points += ProgressionMath.masteryPointsForLevel(level);
        }
        if (level >= max) exp = 0;
        int kills = d.kills() + (countKill ? 1 : 0);
        int bossKills = d.bossKills() + (bossKill ? 1 : 0);
        if (bossKill) points += ServerConfig.MASTERY_POINTS_PER_BOSS.getOrDefault();

        WeaponData nd = d.withProgress(level, exp, total, points).withKills(kills, bossKills, points);
        stack.set(ModComponents.WEAPON_DATA.get(), nd);

        if (amount > 0) {
            PacketDistributor.sendToPlayer(player, new ProgressionEventPayload(ProgressionEventPayload.Kind.EXP, def.id(),
                    oldLevel, level, oldDamage, oldDamage, amount, List.of(), ""));
        }
        if (level > oldLevel) {
            float newDamage = ProgressionMath.weaponDamage(def, nd);
            List<String> unlocked = new ArrayList<>();
            for (AbilityDefinition a : def.abilities()) {
                if (a.unlockLevel() > oldLevel && a.unlockLevel() <= level) unlocked.add(a.id());
            }
            PacketDistributor.sendToPlayer(player, new ProgressionEventPayload(ProgressionEventPayload.Kind.LEVEL_UP, def.id(),
                    oldLevel, level, oldDamage, newDamage, nd.masteryPoints() - d.masteryPoints(), unlocked, ""));
            Fx.tracking(player, FxPayload.of(FxIds.LEVEL_UP).caster(player.getId()).pos(player.position())
                    .level(level).power(unlocked.isEmpty() ? 0 : 1).build());
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    unlocked.isEmpty() ? ModSounds.LEVEL_UP.get() : ModSounds.ABILITY_UNLOCK.get(), SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /** Sets a weapon to an exact level (commands / testing). Grants the mastery points those levels would have. */
    public static void setLevel(ServerPlayer player, ItemStack stack, int level) {
        if (!(stack.getItem() instanceof FantasyWeaponItem)) return;
        WeaponData d = FantasyWeaponItem.data(stack);
        int target = Math.max(1, Math.min(level, ProgressionMath.maxLevel()));
        long need = 0;
        for (int l = d.level(); l < target; l++) need += ProgressionMath.expToNext(l);
        if (target > d.level()) {
            addExp(player, stack, need - d.exp(), false, false);
        } else {
            int points = d.masteryPoints();
            stack.set(ModComponents.WEAPON_DATA.get(), d.withProgress(target, 0, d.totalExp(), points));
        }
    }
}
