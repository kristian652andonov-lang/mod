package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorClass;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.SetAbilities;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.playerclass.ClassGear;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Mage's six regalia: robes for the Mage only, their stats, and each set's power. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RegaliaGameTests {
    private static final String ARENA = "arena";

    private RegaliaGameTests() {
    }

    /** A player at {@code (x, 2, z)} wearing the whole of {@code set} (or nothing), holding {@code focus}. */
    private static ServerPlayer mage(GameTestHelper h, double x, double z, ArmorSet set, String focus) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(x, 2, z), 0);
        if (set != null) for (ArmorItem.Type t : ArmorSet.PIECES) p.setItemSlot(t.getSlot(), new ItemStack(ModItems.ARMOR.get(set).get(t).get()));
        FWGameTests.giveWeapon(p, focus);
        p.doTick();
        return p;
    }

    private static Husk dummy(GameTestHelper h, double x, double z) {
        return FWGameTests.dummy(h, new BlockPos((int) Math.floor(x), 2, (int) Math.floor(z)), 100000f);
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** There are six regalia, one per element of the staffs and spellbooks; each is the Mage's, and each staff and book finds its own. */
    @GameTest(template = ARENA)
    public static void theRegaliaAreTheMages(GameTestHelper h) {
        int regalia = 0;
        for (ArmorSet set : ArmorSet.values()) {
            if (!set.regalia()) continue;
            regalia++;
            if (set.armorClass() != ArmorClass.MAGE) h.fail(set + " is not a Mage's set");
            ItemStack robe = new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.CHESTPLATE).get());
            if (ClassGear.of(robe) != PlayerClass.MAGE) h.fail(set + " answers to " + ClassGear.of(robe));
        }
        if (regalia != 6) h.fail(regalia + " regalia");
        for (var def : com.fantasyweapons.weapon.Weapons.focuses()) {
            ArmorSet set = ArmorSet.ofWeapon(def.id());
            if (set == null || !set.regalia()) h.fail(def.id() + " finds " + set);
        }
        if (ArmorSet.ofWeapon("cinderspire") != ArmorSet.PYROMANCER || ArmorSet.ofWeapon("umbral_codex") != ArmorSet.UMBRAMANCER) h.fail("wrong regalia");
        h.succeed();
    }

    /** Healing Power: a Tidemancer's hood makes the same heal larger. */
    @GameTest(template = ARENA)
    public static void healingPowerHealsMore(GameTestHelper h) {
        ServerPlayer plain = mage(h, 3.5, 3.5, null, "codex_of_tides");
        ServerPlayer robed = FWGameTests.player(h, new Vec3(14.5, 2, 3.5), 0);
        robed.setItemSlot(ArmorItem.Type.HELMET.getSlot(), new ItemStack(ModItems.ARMOR.get(ArmorSet.TIDEMANCER).get(ArmorItem.Type.HELMET).get()));
        FWGameTests.giveWeapon(robed, "codex_of_tides");
        robed.doTick();
        Husk a = dummy(h, 3.5, 6.5), b = dummy(h, 14.5, 6.5);
        a.setHealth(10);
        b.setHealth(10);
        float x = Heals.healFlat(plain, plain.getMainHandItem(), a, 4);
        float y = Heals.healFlat(robed, robed.getMainHandItem(), b, 4);
        if (!(y > x * 1.1f)) h.fail("healed " + x + " plain, " + y + " in the hood");
        FWGameTests.cleanup(plain);
        FWGameTests.cleanup(robed);
        h.succeed();
    }

    /** Pyromancer: the fifth spell hit bursts into a nova that burns whoever stands round the target. */
    @GameTest(template = ARENA)
    public static void pyromancerNovaEveryFifthHit(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.PYROMANCER, "cinderspire");
        Husk target = dummy(h, 4.5, 8.5), beside = dummy(h, 6.5, 8.5);
        for (int i = 0; i < SetAbilities.PYRE_HITS - 1; i++) SetAbilities.onSpellHit(p, p.getMainHandItem(), target);
        if (lost(beside) > 0) h.fail("a nova before the fifth hit");
        SetAbilities.onSpellHit(p, p.getMainHandItem(), target);
        if (!(lost(beside) > 0)) h.fail("no nova on the fifth hit");
        if (!beside.isOnFire()) h.fail("the nova should burn");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Cryomancer: three spell hits crust the target deep enough to lock it in ice; it then shatters. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void cryomancerLocksInIce(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.CRYOMANCER, "rimeheart");
        Husk target = dummy(h, 4.5, 8.5);
        for (int i = 0; i < 2; i++) SetAbilities.onSpellHit(p, p.getMainHandItem(), target);
        if (StatusService.stacks(target, StatusType.FROZEN) > 0) h.fail("frozen too soon");
        SetAbilities.onSpellHit(p, p.getMainHandItem(), target);
        if (StatusService.stacks(target, StatusType.FROZEN) == 0) h.fail("not locked in ice after three hits");
        float before = lost(target);
        h.runAfterDelay(60, () -> {
            if (!(lost(target) > before)) h.fail("the ice should shatter for damage");
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** Tidemancer: a heal sends a wave that heals the allies around the healed and washes enemies away. */
    @GameTest(template = ARENA)
    public static void tidemancerWaveHealsAround(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.TIDEMANCER, "codex_of_tides");
        ServerPlayer healed = FWGameTests.player(h, new Vec3(8.5, 2, 8.5), 0), friend = FWGameTests.player(h, new Vec3(10.5, 2, 8.5), 0);
        healed.setHealth(6);
        friend.setHealth(6);
        Husk foe = dummy(h, 8.5, 11.5);
        Heals.healFlat(p, p.getMainHandItem(), healed, 6);
        if (!(friend.getHealth() > 6)) h.fail("the wave did not heal the friend");
        if (StatusService.stacks(foe, StatusType.SOAKED) == 0) h.fail("the wave did not reach the foe");
        FWGameTests.cleanup(p);
        FWGameTests.cleanup(healed);
        FWGameTests.cleanup(friend);
        h.succeed();
    }

    /** Archdruid: spells root what they strike, and the wounded around the wearer heal. */
    @GameTest(template = ARENA)
    public static void archdruidRootsAndHeals(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.ARCHDRUID, "thornwake");
        Husk foe = dummy(h, 4.5, 8.5);
        SetAbilities.onSpellHit(p, p.getMainHandItem(), foe);
        if (StatusService.stacks(foe, StatusType.ROOTED) == 0) h.fail("the spell did not root");
        ServerPlayer friend = FWGameTests.player(h, new Vec3(7.5, 2, 3.5), 0);
        friend.setHealth(6);
        p.tickCount = 40;
        SetAbilities.tick(p);
        if (!(friend.getHealth() > 6)) h.fail("the friend did not heal");
        FWGameTests.cleanup(p);
        FWGameTests.cleanup(friend);
        h.succeed();
    }

    /** Luminarch: healing an ally calls a ray of light down on the enemy nearest them. */
    @GameTest(template = ARENA)
    public static void luminarchJudgesTheNearestFoe(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.LUMINARCH, "lumen_psalter");
        ServerPlayer ally = FWGameTests.player(h, new Vec3(8.5, 2, 8.5), 0);
        ally.setHealth(6);
        Husk near = dummy(h, 10.5, 9.5), far = dummy(h, 18.5, 18.5);
        Heals.healFlat(p, p.getMainHandItem(), ally, 4);
        if (!(lost(near) > 0)) h.fail("no ray on the nearest foe");
        if (lost(far) > 0) h.fail("the ray struck the farther foe");
        FWGameTests.cleanup(p);
        FWGameTests.cleanup(ally);
        h.succeed();
    }

    /** Umbramancer: a kill feeds the wearer and withers the enemies around the slain. */
    @GameTest(template = ARENA)
    public static void umbramancerFeedsOnKills(GameTestHelper h) {
        ServerPlayer p = mage(h, 4.5, 3.5, ArmorSet.UMBRAMANCER, "umbral_codex");
        p.setHealth(8);
        Husk slain = dummy(h, 4.5, 6.5), near = dummy(h, 6.5, 6.5);
        if (!SetAbilities.demo(p, ArmorSet.UMBRAMANCER)) h.fail("no enemy for the pact");
        if (!(p.getHealth() > 8)) h.fail("the pact did not heal");
        if (StatusService.stacks(near, StatusType.WITHERING) == 0 && StatusService.stacks(slain, StatusType.WITHERING) == 0) h.fail("no withering spread");
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
