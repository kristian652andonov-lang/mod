package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.MageBolts;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The mage's magic: healing that restores and rewards the weapon, shields of absorption, shards that turn blows aside,
 * curses that forbid healing or slowly kill, Spring Eternal's reprieve, valor's harder blows, and the bolts of a focus.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MageGameTests {
    private static final String ARENA = "arena";

    private MageGameTests() {
    }

    /** Mock players are never ticked, so their focus never "recovers" by itself: mark it fully recovered. */
    private static void ready(ServerPlayer p) {
        try {
            var f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 10000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void look(GameTestHelper h, ServerPlayer p, Vec3 at) {
        Vec3 to = at.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
        p.teleportTo(h.getLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }

    /** Healing restores a share of the target's health, never more than it lacks, and the healer's weapon grows by it. */
    @GameTest(template = ARENA)
    public static void healingRestoresAndRewards(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        ItemStack staff = FWGameTests.giveWeapon(p, "thornwake");
        p.setHealth(8);
        float healed = Heals.heal(p, staff, p, 0.25f, 1f);
        if (Math.abs(healed - 5) > 0.01 || Math.abs(p.getHealth() - 13) > 0.01) h.fail("healed " + healed + " to " + p.getHealth() + ", expected 5 to 13");
        if (FantasyWeaponItem.data(staff).totalExp() <= 0) h.fail("the healer's weapon gained nothing");
        float over = Heals.heal(p, staff, p, 5f, 1f);
        if (p.getHealth() > p.getMaxHealth() || Math.abs(over - 7) > 0.01) h.fail("over-healed: " + over + " to " + p.getHealth());
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A barrier fills the target's absorption, soaks blows first, and is gone when it fades. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void barriersShieldThenFade(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Husk husk = FWGameTests.dummy(h, new BlockPos(5, 2, 5), 40);
        Heals.barrierFlat(p, husk, 10, 30);
        if (Math.abs(husk.getAbsorptionAmount() - 10) > 0.01) h.fail("absorption " + husk.getAbsorptionAmount() + ", expected 10");
        husk.hurt(husk.damageSources().generic(), 6);
        if (husk.getHealth() < 40) h.fail("the blow went through the shield: health " + husk.getHealth());
        h.runAfterDelay(45, () -> {
            if (husk.getAbsorptionAmount() > 0.01) h.fail("the shield outlived its barrier: " + husk.getAbsorptionAmount());
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** Each shatterguard shard turns one blow aside entirely and freezes the attacker. */
    @GameTest(template = ARENA)
    public static void shatterguardTurnsBlowsAside(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Husk guarded = FWGameTests.dummy(h, new BlockPos(5, 2, 5), 40);
        Husk attacker = FWGameTests.dummy(h, new BlockPos(7, 2, 5), 40);
        StatusService.apply(guarded, StatusType.SHATTERGUARD, 200, 2, 0, p.getUUID());
        guarded.hurt(guarded.damageSources().mobAttack(attacker), 6);
        if (guarded.getHealth() < 40) h.fail("a shard did not turn the blow: health " + guarded.getHealth());
        if (StatusService.stacks(guarded, StatusType.SHATTERGUARD) != 1) h.fail("shards left: " + StatusService.stacks(guarded, StatusType.SHATTERGUARD));
        if (StatusService.stacks(attacker, StatusType.FROZEN) == 0) h.fail("the attacker was not frozen");
        // the frozen attacker cannot strike at all: thaw it to go on
        StatusService.remove(attacker, StatusType.FROZEN);
        guarded.invulnerableTime = 0;
        guarded.hurt(guarded.damageSources().mobAttack(attacker), 6);
        StatusService.remove(attacker, StatusType.FROZEN);
        guarded.invulnerableTime = 0;
        guarded.hurt(guarded.damageSources().mobAttack(attacker), 6);
        if (guarded.getHealth() >= 40) h.fail("blows past the last shard did nothing");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Under Ruin nothing heals; under Decay healing is halved and blows land harder. */
    @GameTest(template = ARENA)
    public static void cursesSpoilHealing(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Husk ruined = FWGameTests.dummy(h, new BlockPos(5, 2, 5), 40);
        Husk decayed = FWGameTests.dummy(h, new BlockPos(7, 2, 5), 40);
        ruined.setHealth(20);
        decayed.setHealth(20);
        StatusService.apply(ruined, StatusType.RUIN, 200, 1, 0, p.getUUID());
        StatusService.apply(decayed, StatusType.DECAY, 200, 1, 0.5f, p.getUUID());
        ruined.heal(10);
        decayed.heal(10);
        if (ruined.getHealth() != 20) h.fail("Ruin let healing through: " + ruined.getHealth());
        if (Math.abs(decayed.getHealth() - 25) > 0.01) h.fail("Decay healed to " + decayed.getHealth() + ", expected 25");
        Husk plain = FWGameTests.dummy(h, new BlockPos(9, 2, 5), 40);
        decayed.hurt(decayed.damageSources().generic(), 4);
        plain.hurt(plain.damageSources().generic(), 4);
        float d = 25 - decayed.getHealth(), n = 40 - plain.getHealth();
        if (d < n * 1.4f) h.fail("Decay's victim took " + d + " against " + n);
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Spring Eternal turns its bearer's first death away, leaving them standing; the second is not turned. */
    @GameTest(template = ARENA)
    public static void springTurnsDeathAway(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Husk husk = FWGameTests.dummy(h, new BlockPos(5, 2, 5), 40);
        var spring = StatusService.apply(husk, StatusType.SPRING, 200, 1, 1, p.getUUID());
        spring.extra = 0.4f;
        husk.hurt(husk.damageSources().genericKill(), 1000);
        if (!husk.isAlive()) h.fail("spring did not save its bearer");
        if (Math.abs(husk.getHealth() - 16) > 0.01) h.fail("saved at " + husk.getHealth() + ", expected 16");
        if (StatusService.stacks(husk, StatusType.SPRING) != 0) h.fail("spring saved without being spent");
        husk.invulnerableTime = 0;
        husk.hurt(husk.damageSources().genericKill(), 1000);
        if (husk.isAlive()) h.fail("spring saved twice");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Valor makes its bearer's blows land harder. */
    @GameTest(template = ARENA)
    public static void valorStrengthensBlows(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        Husk blessed = FWGameTests.dummy(h, new BlockPos(3, 2, 7), 40), plain = FWGameTests.dummy(h, new BlockPos(9, 2, 7), 40);
        Husk a = FWGameTests.dummy(h, new BlockPos(5, 2, 5), 1000), b = FWGameTests.dummy(h, new BlockPos(11, 2, 5), 1000);
        StatusService.apply(blessed, StatusType.VALOR, 200, 1, 0.5f, p.getUUID());
        a.hurt(a.damageSources().mobAttack(blessed), 8);
        b.hurt(b.damageSources().mobAttack(plain), 8);
        float withValor = 1000 - a.getHealth(), without = 1000 - b.getHealth();
        if (withValor < without * 1.45f) h.fail("valor's blow " + withValor + " against " + without);
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A staff's click looses a bolt that flies to the foe in its sights and wounds it. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void boltsStrikeFromAfar(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 2.5), 0);
        FWGameTests.giveWeapon(p, "cinderspire");
        Husk far = FWGameTests.dummy(h, new BlockPos(12, 2, 14), 1000);
        look(h, p, far.getBoundingBox().getCenter());
        ready(p);
        MageBolts.cast(p);
        h.succeedWhen(() -> {
            h.assertTrue(far.getHealth() < 1000, "the bolt never reached the foe 12 blocks away");
            h.assertTrue(StatusService.stacks(far, StatusType.SEARED) > 0, "a fire bolt did not sear");
            FWGameTests.cleanup(p);
        });
    }

    /** Lifebloom laid at an ally's feet heals them, and the healer's weapon grows by it. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void lifebloomHealsAnAlly(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 2.5), 0);
        ServerPlayer friend = FWGameTests.player(h, new Vec3(12.5, 2, 10.5), 180);
        ItemStack staff = FWGameTests.giveWeapon(p, "thornwake");
        ExpService.setLevel(p, staff, 5);
        staff.set(ModComponents.WEAPON_DATA.get(), FantasyWeaponItem.data(staff).withSelected("lifebloom"));
        long expBefore = FantasyWeaponItem.data(staff).totalExp();
        friend.setHealth(6);
        look(h, p, friend.getBoundingBox().getCenter());
        AbilityService.handleAbilityKey(p, true);
        var rt = AbilityService.runtime(p);
        int charge = rt.isCharging() ? rt.chargeTicks() : 0;
        h.runAfterDelay(charge + 1, () -> {
            if (rt.isCharging()) AbilityService.handleAbilityKey(p, false);
        });
        h.succeedWhen(() -> {
            h.assertTrue(friend.getHealth() > 6, "the ally was not healed: " + friend.getHealth());
            h.assertTrue(FantasyWeaponItem.data(p.getInventory().getItem(0)).totalExp() > expBefore, "the healer's weapon gained nothing");
            FWGameTests.cleanup(friend);
            FWGameTests.cleanup(p);
        });
    }

    /** The Withering curse eats at its victim and grows hungrier as it goes. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void witheringGrows(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 2.5), 0);
        ItemStack staff = FWGameTests.giveWeapon(p, "gloamreach");
        ExpService.setLevel(p, staff, 5);
        staff.set(ModComponents.WEAPON_DATA.get(), FantasyWeaponItem.data(staff).withSelected("withering_curse"));
        Husk victim = FWGameTests.dummy(h, new BlockPos(12, 2, 9), 100000);
        look(h, p, victim.getBoundingBox().getCenter());
        AbilityService.handleAbilityKey(p, true);
        var rt = AbilityService.runtime(p);
        int charge = rt.isCharging() ? rt.chargeTicks() : 0;
        h.runAfterDelay(charge + 1, () -> {
            if (rt.isCharging()) AbilityService.handleAbilityKey(p, false);
        });
        float[] first = {0};
        h.runAfterDelay(charge + 14, () -> {
            var curse = StatusService.get(victim, StatusType.WITHERING);
            if (curse == null) h.fail("no curse took hold");
            else first[0] = curse.potency;
        });
        h.runAfterDelay(charge + 60, () -> {
            var curse = StatusService.get(victim, StatusType.WITHERING);
            if (curse == null) h.fail("the curse ended early");
            else if (curse.potency <= first[0]) h.fail("the curse did not grow: " + first[0] + " -> " + curse.potency);
            if (victim.getHealth() >= 100000) h.fail("the curse did no harm");
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }
}
