package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.armor.ArmorEffects;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.SetAbilities;
import com.fantasyweapons.armor.SetState;
import com.fantasyweapons.combat.MeleeHandler;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.world.SafeTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The 13 full-set abilities. Swings are made with the Aetherlance (a lance: no cleave, no melee passive) unless the
 * ability needs a weapon of its own; where it helps, the same swing by a player without the set is the control.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SetAbilityGameTests {
    private static final String ARENA = "arena";

    private SetAbilityGameTests() {
    }

    /** A player at {@code (x, 2, z)} facing +Z, wearing the whole of {@code set} (or nothing, for null). */
    private static ServerPlayer player(GameTestHelper h, double x, double z, ArmorSet set) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(x, 2, z), 0);
        if (set != null) {
            for (ArmorItem.Type t : ArmorSet.PIECES) p.setItemSlot(t.getSlot(), new ItemStack(ModItems.ARMOR.get(set).get(t).get()));
        }
        p.doTick();
        p.setOnGround(true);
        return p;
    }

    /** Mock players are never ticked: mark the weapon fully recovered. */
    private static void ready(ServerPlayer p) {
        try {
            var f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 10000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void swing(ServerPlayer p, LivingEntity target) {
        ready(p);
        target.invulnerableTime = 0;
        MeleeHandler.attack(p, target);
    }

    private static Husk dummy(GameTestHelper h, double x, double z) {
        return FWGameTests.dummy(h, new BlockPos((int) Math.floor(x), 2, (int) Math.floor(z)), 100000f);
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** Voidfang: after a teleport the next strike echoes, once. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void voidfangRiftEchoesAfterATeleport(GameTestHelper h) {
        MeleeHandler.suppressRandomCrits = true;
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.VOIDFANG);
        FWGameTests.giveWeapon(p, "aetherlance");
        Husk plain = dummy(h, 4.5, 6.5);
        swing(p, plain);
        float plainHit = lost(plain);
        Vec3 dest = h.absoluteVec(new Vec3(14.5, 2, 3.5));
        SafeTeleport.teleport(p, dest);
        SetState s = SetAbilities.state(p);
        h.assertTrue(s.riftUntil > AbilityService.now(p), "a teleport should prime the rift");
        Husk echoed = dummy(h, 14.5, 6.5);
        swing(p, echoed);
        h.assertTrue(s.riftUntil == 0, "the strike should spend the primed rift");
        MeleeHandler.suppressRandomCrits = false;
        h.runAfterDelay(14, () -> {
            float first = plainHit, second = lost(echoed);
            h.assertTrue(first > 0 && second > first * 1.5f, "the rift should echo the hit: plain " + first + ", echoed " + second);
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** Solaris: a fully charged strike bursts and burns the enemies around its target. */
    @GameTest(template = ARENA)
    public static void solarisFullStrikesBurst(GameTestHelper h) {
        MeleeHandler.suppressRandomCrits = true;
        ServerPlayer control = player(h, 4.5, 3.5, null), wearer = player(h, 14.5, 3.5, ArmorSet.SOLARIS);
        FWGameTests.giveWeapon(control, "aetherlance");
        FWGameTests.giveWeapon(wearer, "aetherlance");
        Husk ct = dummy(h, 4.5, 6), cb = dummy(h, 7, 6.5), wt = dummy(h, 14.5, 6), wb = dummy(h, 17, 6.5);
        swing(control, ct);
        swing(wearer, wt);
        MeleeHandler.suppressRandomCrits = false;
        h.assertTrue(lost(wb) > lost(cb), "the dawn burst should hurt enemies near the target: " + lost(wb) + " vs " + lost(cb));
        h.assertTrue(StatusService.stacks(wb, StatusType.SOLAR_BURN) > 0 && wb.getRemainingFireTicks() > 0, "the burst should burn");
        h.assertTrue(StatusService.stacks(cb, StatusType.SOLAR_BURN) == 0, "no burst without the set");
        FWGameTests.cleanup(control);
        FWGameTests.cleanup(wearer);
        h.succeed();
    }

    /** Frostrend: whoever hits the wearer is chilled and slowed. */
    @GameTest(template = ARENA)
    public static void frostrendChillsAttackers(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.FROSTREND);
        Husk attacker = dummy(h, 4.5, 5);
        double speed = attacker.getAttributeValue(Attributes.MOVEMENT_SPEED);
        p.hurt(h.getLevel().damageSources().mobAttack(attacker), 2);
        h.assertTrue(StatusService.stacks(attacker, StatusType.FROSTBITE) > 0, "the attacker should be chilled");
        h.assertTrue(attacker.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.95, "the attacker should be slowed");
        FWGameTests.cleanup(p);
        ServerPlayer bare = player(h, 14.5, 3.5, null);
        Husk other = dummy(h, 14.5, 5);
        bare.hurt(h.getLevel().damageSources().mobAttack(other), 2);
        h.assertTrue(StatusService.stacks(other, StatusType.FROSTBITE) == 0, "no chill without the set");
        FWGameTests.cleanup(bare);
        h.succeed();
    }

    /** Doomcleaver: below half health, attack speed and life steal rise. */
    @GameTest(template = ARENA)
    public static void doomcleaverFrenzyBelowHalfHealth(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.DOOMCLEAVER);
        ArmorEffects.tick(p);
        double speed = p.getAttributeValue(Attributes.ATTACK_SPEED);
        float steal = ArmorEffects.lifeSteal(p);
        h.assertTrue(!SetAbilities.state(p).frenzy, "no frenzy at full health");
        p.setHealth(p.getMaxHealth() * 0.4f);
        ArmorEffects.tick(p);
        h.assertTrue(SetAbilities.state(p).frenzy, "frenzy below half health");
        h.assertTrue(p.getAttributeValue(Attributes.ATTACK_SPEED) > speed * 1.2, "attack speed should rise: " + p.getAttributeValue(Attributes.ATTACK_SPEED));
        h.assertTrue(Math.abs(ArmorEffects.lifeSteal(p) - steal - 0.2f) < 1e-4, "life steal should rise by 20%");
        p.setHealth(p.getMaxHealth());
        ArmorEffects.tick(p);
        h.assertTrue(!SetAbilities.state(p).frenzy && Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - speed) < 1e-6, "frenzy ends when healed");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Stormbreaker: every fourth hit calls down a lightning bolt on the target. */
    @GameTest(template = ARENA)
    public static void stormbreakerEveryFourthHitStrikes(GameTestHelper h) {
        MeleeHandler.suppressRandomCrits = true;
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.STORMBREAKER);
        FWGameTests.giveWeapon(p, "aetherlance");
        Husk target = dummy(h, 4.5, 6);
        float[] hits = new float[4];
        for (int i = 0; i < 4; i++) {
            float before = lost(target);
            swing(p, target);
            hits[i] = lost(target) - before;
        }
        MeleeHandler.suppressRandomCrits = false;
        h.assertTrue(hits[0] > 0 && Math.abs(hits[1] - hits[0]) < hits[0] * 0.1f && Math.abs(hits[2] - hits[0]) < hits[0] * 0.1f,
                "the first three hits are plain: " + java.util.Arrays.toString(hits));
        h.assertTrue(hits[3] > hits[0] * 1.6f, "the fourth hit should call lightning: " + java.util.Arrays.toString(hits));
        h.assertTrue(StatusService.stacks(target, StatusType.STAGGERED) > 0, "the bolt should stagger");
        h.assertTrue(SetAbilities.state(p).stormHits == 0, "the storm counter should start over");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Gravebite: slain enemies rise as allies (never more than three) that fight for the wearer and fade. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void gravebiteSlainRiseAsAllies(GameTestHelper h) {
        ServerPlayer p = player(h, 11.5, 6.5, ArmorSet.GRAVEBITE);
        // in the middle of the arena, so the risen find this test's enemy before anything outside it
        for (int i = 0; i < 20; i++) {
            Husk victim = FWGameTests.dummy(h, new BlockPos(9 + i % 5, 2, 9 + i / 5), 1f);
            victim.hurt(h.getLevel().damageSources().playerAttack(p), 1000);
        }
        int allies = SetAbilities.state(p).allies;
        h.assertTrue(allies >= 1 && allies <= 3, "some of twenty slain should rise, at most three: " + allies);
        Husk enemy = dummy(h, 11.5, 11.5);
        h.runAfterDelay(60, () -> h.assertTrue(lost(enemy) > 0, "the risen should attack nearby enemies"));
        h.runAfterDelay(200, () -> {
            h.assertTrue(SetAbilities.state(p).allies == 0, "the risen should fade: " + SetAbilities.state(p).allies);
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** Soulreaper: kills store up to five souls, spent on (and refunded by a failed) next ability. */
    @GameTest(template = ARENA)
    public static void soulreaperStoresSoulsForTheNextAbility(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.SOULREAPER);
        for (int i = 0; i < 7; i++) {
            Husk victim = FWGameTests.dummy(h, new BlockPos(2 + i, 2, 8), 1f);
            victim.hurt(h.getLevel().damageSources().playerAttack(p), 1000);
        }
        SetState s = SetAbilities.state(p);
        h.assertTrue(s.souls == SetAbilities.MAX_SOULS, "souls stop at five: " + s.souls);
        var soulreaper = com.fantasyweapons.weapon.Weapons.get("soulreaper");
        var ability = soulreaper.castables().get(0);
        float plain = 1f;
        SetAbilities.beforeCast(p);
        float empowered = ArmorEffects.abilityDamage(p, soulreaper, ability);
        h.assertTrue(s.souls == 0 && Math.abs(empowered - plain * 1.75f) < 1e-4, "five souls empower the cast by 75%: " + empowered);
        SetAbilities.afterCast(p, false, false);
        h.assertTrue(s.souls == SetAbilities.MAX_SOULS, "a failed cast keeps its souls");
        SetAbilities.beforeCast(p);
        SetAbilities.afterCast(p, true, false);
        h.assertTrue(s.souls == 0, "a cast spends the souls");
        SetAbilities.beforeCast(p);
        h.assertTrue(Math.abs(ArmorEffects.abilityDamage(p, soulreaper, ability) - plain) < 1e-4, "the next cast has no souls left");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Bloomfall: standing still heals the wearer and roots the enemies around them; moving ends it. */
    @GameTest(template = ARENA)
    public static void bloomfallGroveWhenStandingStill(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.BLOOMFALL);
        Husk near = dummy(h, 6.5, 5.5);
        p.setHealth(10);
        for (int i = 0; i < SetAbilities.GROVE_STILL + 25; i++) SetAbilities.tick(p);
        h.assertTrue(SetAbilities.state(p).grove, "the grove should take root");
        h.assertTrue(p.getHealth() > 10, "the grove should heal");
        h.assertTrue(StatusService.stacks(near, StatusType.ROOTED) > 0, "nearby enemies should be rooted");
        Vec3 moved = h.absoluteVec(new Vec3(6.5, 2, 3.5));
        p.teleportTo(moved.x, moved.y, moved.z);
        SetAbilities.tick(p);
        h.assertTrue(!SetAbilities.state(p).grove, "moving uproots the grove");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Eclipse Reaper: switching mode releases an eclipse wave, then both modes' bonuses apply for a while. */
    @GameTest(template = ARENA)
    public static void eclipseSwitchReleasesTotality(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.ECLIPSE_REAPER);
        ItemStack stack = FWGameTests.giveWeapon(p, "eclipse_reaper");
        Husk near = dummy(h, 6.5, 5.5);
        AbilityService.handleSwitchForm(p);
        h.assertTrue(lost(near) > 0, "the eclipse wave should hurt nearby enemies");
        h.assertTrue(StatusService.stacks(near, StatusType.ECLIPSE_LIGHT) > 0 && StatusService.stacks(near, StatusType.ECLIPSE_DARKNESS) > 0,
                "the wave should brand with light and darkness");
        h.assertTrue(SetAbilities.totality(p), "totality after the switch");
        // back in light mode, the dark mode's speed still applies during totality
        stack.set(ModComponents.WEAPON_DATA.get(), FantasyWeaponItem.data(stack).withForm(0));
        ArmorEffects.tick(p);
        double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 1.1, "dark-mode speed in light mode during totality");
        float health = near.getHealth();
        ArmorEffects.onFormSwitch(p);
        h.assertTrue(near.getHealth() == health, "the wave has a cooldown");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Starforge: heavy attacks pull the enemies around the impact towards it. */
    @GameTest(template = ARENA)
    public static void starforgeHeavyAttacksPull(GameTestHelper h) {
        // the control stands well outside the reach of the wearer's well
        ServerPlayer control = player(h, 2.5, 3.5, null), wearer = player(h, 16.5, 3.5, ArmorSet.STARFORGE);
        FWGameTests.giveWeapon(control, "aetherlance");
        FWGameTests.giveWeapon(wearer, "aetherlance");
        control.setShiftKeyDown(true);
        wearer.setShiftKeyDown(true);
        Husk ct = dummy(h, 2.5, 6), cb = dummy(h, 5.5, 6.5), wt = dummy(h, 16.5, 6), wb = dummy(h, 20.5, 6.5);
        swing(control, ct);
        swing(wearer, wt);
        h.assertTrue(StatusService.stacks(wb, StatusType.GRAVITY_BOUND) > 0, "the gravity well should hold enemies near the impact");
        h.assertTrue(StatusService.stacks(cb, StatusType.GRAVITY_BOUND) == 0, "no well without the set");
        wearer.setShiftKeyDown(false);
        Husk lt = dummy(h, 20.5, 12), lb = dummy(h, 21.5, 15.5);
        wearer.teleportTo(h.absoluteVec(new Vec3(20.5, 2, 9.5)).x, h.absoluteVec(new Vec3(20.5, 2, 9.5)).y, h.absoluteVec(new Vec3(20.5, 2, 9.5)).z);
        swing(wearer, lt);
        h.assertTrue(StatusService.stacks(lb, StatusType.GRAVITY_BOUND) == 0, "only heavy attacks pull");
        FWGameTests.cleanup(control);
        FWGameTests.cleanup(wearer);
        h.succeed();
    }

    /** Aetherlance: critical hits fire a piercing bolt along the strike; plain hits don't. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void aetherlanceCritsFireABolt(GameTestHelper h) {
        MeleeHandler.suppressRandomCrits = true;
        ServerPlayer crit = player(h, 4.5, 3.5, ArmorSet.AETHERLANCE), plain = player(h, 14.5, 3.5, ArmorSet.AETHERLANCE);
        FWGameTests.giveWeapon(crit, "aetherlance");
        FWGameTests.giveWeapon(plain, "aetherlance");
        Husk ct = dummy(h, 4.5, 6), cb = dummy(h, 4.5, 14), pt = dummy(h, 14.5, 6), pb = dummy(h, 14.5, 14);
        // a falling strike is a critical hit
        crit.setOnGround(false);
        crit.fallDistance = 1;
        swing(crit, ct);
        swing(plain, pt);
        MeleeHandler.suppressRandomCrits = false;
        h.runAfterDelay(15, () -> {
            h.assertTrue(lost(cb) > 0, "the bolt should fly on to the enemy behind the target");
            h.assertTrue(lost(pb) == 0, "a plain hit fires no bolt");
            FWGameTests.cleanup(crit);
            FWGameTests.cleanup(plain);
            h.succeed();
        });
    }

    /** Monolith: no knockback, and heavy attacks send out a shockwave. */
    @GameTest(template = ARENA)
    public static void monolithIsImmovableAndHeavyHitsShake(GameTestHelper h) {
        ServerPlayer p = player(h, 14.5, 3.5, ArmorSet.MONOLITH);
        ArmorEffects.tick(p);
        h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "the wearer cannot be knocked back");
        Vec3 v = p.getDeltaMovement();
        p.knockback(2, 1, 0);
        h.assertTrue(p.getDeltaMovement().distanceTo(v) < 1e-6, "knockback should do nothing");
        ServerPlayer control = player(h, 4.5, 3.5, null);
        FWGameTests.giveWeapon(control, "aetherlance");
        FWGameTests.giveWeapon(p, "aetherlance");
        control.setShiftKeyDown(true);
        p.setShiftKeyDown(true);
        MeleeHandler.suppressRandomCrits = true;
        Husk ct = dummy(h, 4.5, 6), cb = dummy(h, 7, 6.5), wt = dummy(h, 14.5, 6), wb = dummy(h, 17, 6.5);
        swing(control, ct);
        swing(p, wt);
        MeleeHandler.suppressRandomCrits = false;
        h.assertTrue(lost(wb) > lost(cb) && StatusService.stacks(wb, StatusType.STAGGERED) > 0, "the shockwave should hit and stagger: " + lost(wb));
        FWGameTests.cleanup(control);
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Infernochain: chain-form attacks leave a burning trail; sword-form ones don't. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void infernochainChainAttacksLeaveFire(GameTestHelper h) {
        ServerPlayer sword = player(h, 4.5, 3.5, ArmorSet.INFERNOCHAIN), chain = player(h, 14.5, 3.5, ArmorSet.INFERNOCHAIN);
        FWGameTests.giveWeapon(sword, "infernochain");
        ItemStack stack = FWGameTests.giveWeapon(chain, "infernochain");
        stack.set(ModComponents.WEAPON_DATA.get(), FantasyWeaponItem.data(stack).withForm(1));
        swing(sword, dummy(h, 4.5, 9.5));
        swing(chain, dummy(h, 14.5, 9.5));
        // whoever steps onto the line between the wearer and the target afterwards
        Husk swordWalker = dummy(h, 4.5, 6), chainWalker = dummy(h, 14.5, 6);
        h.runAfterDelay(14, () -> {
            h.assertTrue(chainWalker.getRemainingFireTicks() > 0 && StatusService.stacks(chainWalker, StatusType.SEARED) > 0,
                    "a chain-form hit should leave a burning trail");
            h.assertTrue(swordWalker.getRemainingFireTicks() <= 0, "a sword-form hit leaves no trail");
            FWGameTests.cleanup(sword);
            FWGameTests.cleanup(chain);
            h.succeed();
        });
    }

    /** Taking a set off loses what its ability was holding. */
    @GameTest(template = ARENA)
    public static void takingTheSetOffClearsItsState(GameTestHelper h) {
        ServerPlayer p = player(h, 4.5, 3.5, ArmorSet.SOULREAPER);
        Husk victim = FWGameTests.dummy(h, new BlockPos(4, 2, 8), 1f);
        victim.hurt(h.getLevel().damageSources().playerAttack(p), 1000);
        h.assertTrue(SetAbilities.state(p).souls == 1, "a kill stores a soul");
        p.setItemSlot(ArmorItem.Type.HELMET.getSlot(), ItemStack.EMPTY);
        SetAbilities.tick(p);
        h.assertTrue(SetAbilities.state(p).souls == 0, "souls are lost with the set");
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
