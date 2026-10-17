package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.quest.Chapter;
import com.fantasyweapons.quest.Quest;
import com.fantasyweapons.quest.QuestService;
import com.fantasyweapons.quest.Quests;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** The Quest Journal: a big book of quests over every ore, core, weapon and armor set, counted as they are done and paid when claimed. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private static final String ARENA = "arena";

    private QuestGameTests() {
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Over a hundred quests in every chapter: one to find, mine and refine each ore, forge and raise each weapon, wear each set. */
    @GameTest(template = ARENA)
    public static void theJournalCoversEverything(GameTestHelper h) {
        if (Quests.all().size() < 100) h.fail("only " + Quests.all().size() + " quests");
        Set<String> ids = new HashSet<>();
        for (Quest q : Quests.all()) {
            if (!ids.add(q.id())) h.fail("two quests named " + q.id());
            if (q.objectives().isEmpty()) h.fail(q.id() + " asks nothing");
            if (q.rewards().isEmpty()) h.fail(q.id() + " gives nothing");
            for (var r : q.rewards()) if (r.get().isEmpty()) h.fail(q.id() + " has an empty reward");
            if (q.requires() != null && Quests.get(q.requires()) == null) h.fail(q.id() + " needs a quest that is not there: " + q.requires());
        }
        for (Chapter c : Chapter.values()) if (Quests.of(c).isEmpty()) h.fail("chapter " + c + " is empty");
        for (Material m : Material.values()) {
            for (String id : new String[] {"prospect_" + m.rawId(), "vein_" + m.rawId(), "refine_" + m.refinedId()}) {
                if (Quests.get(id) == null) h.fail("no quest " + id);
            }
        }
        for (WeaponDefinition def : Weapons.legendaries()) {
            for (String id : new String[] {"forge_" + def.id(), "blooded_" + def.id(), "champion_" + def.id()}) {
                if (Quests.get(id) == null) h.fail("no quest " + id);
            }
        }
        for (ArmorSet set : ArmorSet.values()) if (Quests.get("wear_" + set.id()) == null) h.fail("no quest wear_" + set.id());
        // the Worldforge's road: raise it, begin a rite, then each ultimate weapon and its powers
        for (String id : new String[] {"raise_worldforge", "first_rite", "world_ender", "dominion_sight", "end_of_days"}) {
            if (Quests.get(id) == null) h.fail("no quest " + id);
        }
        for (WeaponDefinition def : Weapons.calamities()) {
            Quest q = Quests.get("forge_" + def.id());
            if (q == null) h.fail("no quest forge_" + def.id());
            else if (!"first_rite".equals(q.requires())) h.fail("forge_" + def.id() + " opens after " + q.requires());
        }
        // the Weapon of God: hold both calamities, forge it, then summon its powers
        Quest god = Quests.get("forge_" + com.fantasyweapons.weapons.god.WeaponOfGod.ID);
        if (god == null) h.fail("no quest for the Weapon of God");
        else if (!"both_calamities".equals(god.requires())) h.fail("the Weapon of God's quest opens after " + god.requires());
        for (String id : new String[] {"both_calamities", "divine_summoning", "every_power_of_god"}) if (Quests.get(id) == null) h.fail("no quest " + id);
        h.succeed();
    }

    /** Opening the Codex finishes the first quest; claiming it pays once and opens the next, which counts what is held. */
    @GameTest(template = ARENA)
    public static void questsAreCountedAndPaid(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Quest first = Quests.get("first_page"), pick = Quests.get("diamond_pick");
        if (QuestService.complete(p, first)) h.fail("the first quest starts done");
        if (QuestService.unlocked(p, pick)) h.fail("the second quest starts open");
        QuestService.event(p, "codex");
        if (!QuestService.complete(p, first)) h.fail("opening the Codex did not finish The First Page");
        QuestService.claim(p, "diamond_pick");
        if (QuestService.data(p).claimed("diamond_pick")) h.fail("a locked quest was claimed");
        QuestService.claim(p, "first_page");
        if (!QuestService.data(p).claimed("first_page")) h.fail("The First Page was not claimed");
        int bread = count(p, Items.BREAD), iron = count(p, Items.IRON_INGOT);
        if (bread != 12 || iron != 6) h.fail("rewards: " + bread + " bread, " + iron + " iron");
        QuestService.claim(p, "first_page");
        if (count(p, Items.BREAD) != 12) h.fail("a quest paid twice");
        if (!QuestService.unlocked(p, pick)) h.fail("claiming did not open the next quest");
        if (QuestService.complete(p, pick)) h.fail("the pick quest is done with no pick");
        p.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
        if (!QuestService.complete(p, pick)) h.fail("holding a diamond pickaxe did not finish its quest");
        QuestService.claim(p, "diamond_pick");
        if (count(p, Items.DIAMOND) != 2) h.fail("the pick quest paid " + count(p, Items.DIAMOND) + " diamonds");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Claim All pays every quest that is done, and the quests that opens which are done too, each once. */
    @GameTest(template = ARENA)
    public static void claimAllPaysEverythingDone(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        QuestService.event(p, "codex");
        p.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
        QuestService.event(p, "kill:hostile", 50);
        int paid = QuestService.claimAll(p);
        for (String id : new String[]{"first_page", "diamond_pick", "hunter_50"}) {
            if (!QuestService.data(p).claimed(id)) h.fail(id + " was not claimed");
        }
        if (paid != 3) h.fail("claimed " + paid + " quests, not 3");
        if (count(p, Items.BREAD) != 12) h.fail("first page paid " + count(p, Items.BREAD) + " bread");
        if (QuestService.claimAll(p) != 0) h.fail("claimed again with nothing new done");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Mining counts up to the target and no further; a quest still locked counts nothing until it opens. */
    @GameTest(template = ARENA)
    public static void lockedQuestsWaitTheirTurn(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Material m = Material.values()[0];
        Quest prospect = Quests.get("prospect_" + m.rawId()), vein = Quests.get("vein_" + m.rawId());
        String key = ((com.fantasyweapons.quest.QuestObjective.Count) prospect.objectives().get(0)).key();
        QuestService.event(p, key, 20);
        if (QuestService.progress(p, prospect, 0) != 8) h.fail("prospecting counted " + QuestService.progress(p, prospect, 0));
        if (QuestService.progress(p, vein, 0) != 0) h.fail("the locked vein quest counted " + QuestService.progress(p, vein, 0));
        QuestService.claim(p, prospect.id());
        if (count(p, ModItems.RAW.get(m).get()) != 8) h.fail("prospecting paid " + count(p, ModItems.RAW.get(m).get()) + " raw");
        QuestService.event(p, key, 5);
        if (QuestService.progress(p, vein, 0) != 5) h.fail("the opened vein quest counted " + QuestService.progress(p, vein, 0));
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A hostile creature slain with a legendary weapon counts for the hunts. */
    @GameTest(template = ARENA)
    public static void huntsCountLegendaryKills(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Quest hunter = Quests.get("hunter_50");
        Zombie bare = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        bare.hurt(p.damageSources().playerAttack(p), 1000);
        if (QuestService.progress(p, hunter, 0) != 0) h.fail("a kill without a legendary weapon counted");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.WEAPONS.get("voidfang").get()));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        zombie.hurt(p.damageSources().playerAttack(p), 1000);
        if (QuestService.progress(p, hunter, 0) != 1) h.fail("a legendary kill counted " + QuestService.progress(p, hunter, 0));
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
