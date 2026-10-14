package com.fantasyweapons.quest;

import com.fantasyweapons.network.QuestPayloads;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps the players' quests: tallies what they do into the counted objectives (by key: "mine:...", "kill:...", "visit:...",
 * "wear:...", "codex", "forge", "ability", "ultimate", "infuse"), tells them when a quest is done, and pays out its rewards
 * when they claim it from the journal.
 */
public final class QuestService {
    private QuestService() {
    }

    public static QuestData data(Player player) {
        return player.getData(ModAttachments.QUESTS);
    }

    /** Whether a quest is open: its required quest has been claimed. */
    public static boolean unlocked(Player player, Quest quest) {
        return quest.requires() == null || data(player).claimed(quest.requires());
    }

    /** How far the player is with objective {@code index} of a quest (capped at its target). */
    public static int progress(Player player, Quest quest, int index) {
        QuestObjective o = quest.objectives().get(index);
        int n = o.counted() ? data(player).count(quest.id(), index) : o.have(player);
        return Math.min(n, o.target());
    }

    public static boolean complete(Player player, Quest quest) {
        for (int i = 0; i < quest.objectives().size(); i++) if (progress(player, quest, i) < quest.objectives().get(i).target()) return false;
        return true;
    }

    /** Something the player did, {@code amount} times: every open quest counting it moves on. */
    public static void event(ServerPlayer player, String key, int amount) {
        QuestData d = data(player);
        boolean changed = false;
        for (Quest q : Quests.all()) {
            if (d.claimed(q.id()) || !unlocked(player, q)) continue;
            for (int i = 0; i < q.objectives().size(); i++) {
                if (q.objectives().get(i) instanceof QuestObjective.Count c && c.key().equals(key)) {
                    int now = Math.min(c.target(), d.count(q.id(), i) + amount);
                    changed |= d.setCount(q.id(), i, now);
                }
            }
        }
        if (changed) {
            announceDone(player);
            player.syncData(ModAttachments.QUESTS);
        }
    }

    public static void event(ServerPlayer player, String key) {
        event(player, key, 1);
    }

    /** Tells the player of every open quest newly done (once each); whether there was any. */
    private static boolean announceDone(ServerPlayer player) {
        QuestData d = data(player);
        boolean any = false;
        for (Quest q : Quests.all()) {
            if (d.claimed(q.id()) || d.announced(q.id()) || !unlocked(player, q) || !complete(player, q)) continue;
            d.announce(q.id());
            PacketDistributor.sendToPlayer(player, new QuestPayloads.Completed(q.id()));
            any = true;
        }
        return any;
    }

    /** Pays out a quest the player claims from the journal, if it is open, done and not yet claimed. */
    public static void claim(ServerPlayer player, String id) {
        Quest q = Quests.get(id);
        if (q == null || !pay(player, q)) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        // what it opened may be done already
        announceDone(player);
        player.syncData(ModAttachments.QUESTS);
    }

    /**
     * Pays out every quest the player has done, and the quests those open that are done too; how many. (Toasts are not
     * shown for quests claimed at once.)
     */
    public static int claimAll(ServerPlayer player) {
        int paid = 0;
        boolean more = true;
        while (more) {
            more = false;
            for (Quest q : Quests.all()) {
                if (pay(player, q)) {
                    paid++;
                    more = true;
                }
            }
        }
        if (paid == 0) return 0;
        // the quests just paid need no announcing
        QuestData d = data(player);
        for (Quest q : Quests.all()) if (d.claimed(q.id())) d.announce(q.id());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Claimed " + paid + (paid == 1 ? " quest" : " quests")).withColor(0xF2C46B), true);
        announceDone(player);
        player.syncData(ModAttachments.QUESTS);
        return paid;
    }

    /** Gives a quest's rewards, if it is open, done and not yet claimed; whether it did. */
    private static boolean pay(ServerPlayer player, Quest q) {
        QuestData d = data(player);
        if (d.claimed(q.id()) || !unlocked(player, q) || !complete(player, q)) return false;
        d.claim(q.id());
        for (var reward : q.rewards()) {
            ItemStack stack = reward.get();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (q.xp() > 0) player.giveExperiencePoints(q.xp());
        return true;
    }

    /** Operator tool: a quest's counted objectives filled (what it asks to be held still has to be held). */
    public static void force(ServerPlayer player, Quest quest) {
        QuestData d = data(player);
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i) instanceof QuestObjective.Count c) d.setCount(quest.id(), i, c.target());
        }
        announceDone(player);
        player.syncData(ModAttachments.QUESTS);
    }

    /** Operator tool: every quest forgotten. */
    public static void reset(ServerPlayer player) {
        player.setData(ModAttachments.QUESTS, new QuestData());
        player.syncData(ModAttachments.QUESTS);
    }

    // ---- what the world reports ----

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative()) {
            event(player, "mine:" + BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()));
        }
    }

    /** A creature slain with a legendary weapon in hand. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !(player.getMainHandItem().getItem() instanceof FantasyWeaponItem)) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof Enemy) event(player, "kill:hostile");
        event(player, "kill:" + BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()));
    }

    /** Every second: where the player is, and whether a quest that asks for what they hold is done. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        event(player, "visit:" + player.level().dimension().location());
        if (player.level().getBiome(player.blockPosition()).is(Biomes.DEEP_DARK)) event(player, "visit:minecraft:deep_dark");
        if (player.getY() > 160) event(player, "visit:height");
        if (player.tickCount % 40 == 0 && announceDone(player)) player.syncData(ModAttachments.QUESTS);
    }
}
