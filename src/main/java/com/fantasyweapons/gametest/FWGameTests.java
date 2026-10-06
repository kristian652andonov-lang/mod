package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.api.GroundSplitAbility;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import com.fantasyweapons.weapons.monolith.Monolith;
import com.fantasyweapons.weapons.voidfang.Voidfang;
import com.fantasyweapons.world.DeathDissolve;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side GameTests for progression and the Voidfang abilities. Run with {@code ./gradlew runGameTestServer}.
 * Every test drives the real server code paths (EXP service, ability state machine, melee pipeline) through a mock
 * player standing in the {@code fantasyweapons:arena} structure (24x8x24, stone floor). GameTest places structures one block
 * above the test origin, so the floor is at relative y=1 and everything stands at y=2.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FWGameTests {
    private static final String ARENA = "arena";
    private static final AtomicInteger PLAYERS = new AtomicInteger();

    private FWGameTests() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // pure progression maths
    // ------------------------------------------------------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void expCurveMatchesDesign(GameTestHelper h) {
        long[] expected = {100, 150, 225, 325};
        for (int i = 0; i < expected.length; i++) {
            long actual = ProgressionMath.expToNext(i + 1);
            h.assertTrue(actual == expected[i], "EXP to level " + (i + 2) + " was " + actual + ", expected " + expected[i]);
        }
        h.assertTrue(ProgressionMath.expToNext(ProgressionMath.maxLevel()) == 0, "max level must need no more EXP");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void damageScalesWithLevel(GameTestHelper h) {
        WeaponDefinition def = Weapons.get("voidfang");
        float l1 = ProgressionMath.weaponDamage(def, 1, 0);
        double expectedL1 = ServerConfig.baseDamage(def) * def.rarity().damageFactor() * ServerConfig.GLOBAL_DAMAGE_MULTIPLIER.getOrDefault();
        h.assertTrue(Math.abs(l1 - expectedL1) < 1e-3, "level 1 damage " + l1 + " != " + expectedL1);
        float prev = l1;
        for (int level = 2; level <= ProgressionMath.maxLevel(); level++) {
            float d = ProgressionMath.weaponDamage(def, level, 0);
            h.assertTrue(d > prev, "damage must grow every level (level " + level + ")");
            prev = d;
        }
        float ratio = prev / l1;
        h.assertTrue(ratio > 60 && ratio < 300, "level-100/level-1 damage ratio out of range: " + ratio);
        h.assertTrue(l1 < 15, "level-1 damage should be small: " + l1);
        // exponential: every level multiplies damage by the same factor
        float r1 = ProgressionMath.weaponDamage(def, 11, 0) / ProgressionMath.weaponDamage(def, 10, 0);
        float r2 = ProgressionMath.weaponDamage(def, 81, 0) / ProgressionMath.weaponDamage(def, 80, 0);
        h.assertTrue(Math.abs(r1 - r2) < 1e-3, "growth per level should be constant: " + r1 + " vs " + r2);
        h.assertTrue(ProgressionMath.weaponDamage(def, 50, 1f) > ProgressionMath.weaponDamage(def, 50, 0f), "mastery must add damage");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void weaponDataCodecsRoundTrip(GameTestHelper h) {
        WeaponData data = new WeaponData(Optional.of(UUID.randomUUID()), 37, 1234, 98765, Map.of("void_slash", 3, "rift_tear", 1),
                5, 1, "void_blink", 412, 3);
        Tag tag = WeaponData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        WeaponData fromNbt = WeaponData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        h.assertTrue(data.equals(fromNbt), "NBT round trip changed the data: " + fromNbt);
        var buf = Unpooled.buffer();
        WeaponData.STREAM_CODEC.encode(buf, data);
        WeaponData fromNet = WeaponData.STREAM_CODEC.decode(buf);
        h.assertTrue(data.equals(fromNet), "network round trip changed the data: " + fromNet);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------
    // EXP, levels, unlocks, upgrades
    // ------------------------------------------------------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void levelUpGrantsPointsAndUnlocks(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        WeaponDefinition def = Weapons.get("voidfang");
        AbilityDefinition slash = def.ability(Voidfang.VOID_SLASH);
        h.assertFalse(FantasyWeaponItem.data(stack).isUnlocked(slash), "Void Slash must be locked at level 1");

        ExpService.addExp(p, stack, 100 + 150 + 225 + 325 + 7, false, true);
        WeaponData d = FantasyWeaponItem.data(stack);
        int expectedPoints = 0;
        for (int l = 2; l <= 5; l++) expectedPoints += ProgressionMath.masteryPointsForLevel(l);
        h.assertTrue(d.level() == 5, "level should be 5, was " + d.level());
        h.assertTrue(d.exp() == 7, "leftover EXP should be 7, was " + d.exp());
        h.assertTrue(d.totalExp() == 807, "total EXP should be 807, was " + d.totalExp());
        h.assertTrue(d.masteryPoints() == expectedPoints, "mastery points should be " + expectedPoints + ", was " + d.masteryPoints());
        h.assertTrue(d.kills() == 1, "kill should be counted");
        h.assertTrue(d.isUnlocked(slash), "Void Slash unlocks at level 5");
        h.assertFalse(d.isUnlocked(def.ability(Voidfang.VOID_BLINK)), "Void Blink stays locked until level 10");
        double expectedAttr = ProgressionMath.weaponDamage(def, d) - 1.0;
        h.assertTrue(p.getMainHandItem().getAttributeModifiers().modifiers().stream()
                        .anyMatch(m -> Math.abs(m.modifier().amount() - expectedAttr) < 1e-3 && expectedAttr > ProgressionMath.weaponDamage(def, 1, 0) - 1.0),
                "attack damage attribute should follow the weapon level");
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void upgradesAreServerValidated(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        WeaponDefinition def = Weapons.get("voidfang");
        AbilityDefinition slash = def.ability(Voidfang.VOID_SLASH);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();

        h.assertTrue(AbilityService.upgradeDenial(FantasyWeaponItem.data(stack), slash) != null, "locked ability cannot be upgraded");
        int needed = slash.weaponLevelFor(2);
        ExpService.setLevel(p, stack, needed);
        WeaponData before = FantasyWeaponItem.data(stack);
        h.assertTrue(AbilityService.upgradeDenial(before, slash) == null, "upgrade should be allowed at level " + needed);

        // a request naming another weapon instance or slot is ignored
        AbilityService.handleUpgrade(p, 0, UUID.randomUUID(), slash.id());
        AbilityService.handleUpgrade(p, 5, id, slash.id());
        h.assertTrue(FantasyWeaponItem.data(stack).equals(before), "mismatched upgrade requests must be ignored");

        AbilityService.handleUpgrade(p, 0, id, slash.id());
        WeaponData after = FantasyWeaponItem.data(stack);
        h.assertTrue(after.abilityLevel(slash) == 2, "Void Slash should be level 2, was " + after.abilityLevel(slash));
        h.assertTrue(after.masteryPoints() == before.masteryPoints() - slash.costFor(2), "upgrade cost not deducted");

        // spending every point then trying again is refused
        stack.set(ModComponents.WEAPON_DATA.get(), after.withUpgrade(slash.id(), 1, 0));
        WeaponData broke = FantasyWeaponItem.data(stack);
        AbilityService.handleUpgrade(p, 0, id, slash.id());
        h.assertTrue(FantasyWeaponItem.data(stack).equals(broke), "upgrade without points must be refused");
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void upgradeToMaxSpendsOnlyWhatIsAllowed(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        AbilityDefinition slash = Weapons.get("voidfang").ability(Voidfang.VOID_SLASH);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        ExpService.setLevel(p, stack, 100);
        // only enough points for two levels: upgrade-to-max must stop there
        WeaponData d = FantasyWeaponItem.data(stack);
        int two = slash.costFor(2) + slash.costFor(3);
        stack.set(ModComponents.WEAPON_DATA.get(), d.withProgress(d.level(), d.exp(), d.totalExp(), two));
        h.assertTrue(AbilityService.maxUpgrade(FantasyWeaponItem.data(stack), slash)[0] == 2, "two levels should be affordable");
        AbilityService.handleUpgrade(p, 0, id, slash.id(), true);
        WeaponData after = FantasyWeaponItem.data(stack);
        h.assertTrue(after.abilityLevel(slash) == 3, "upgrade-to-max with points for two levels should reach level 3, got " + after.abilityLevel(slash));
        h.assertTrue(after.masteryPoints() == 0, "every affordable point should be spent, " + after.masteryPoints() + " left");
        // plenty of points: it goes all the way to the ability's max level and keeps the change
        stack.set(ModComponents.WEAPON_DATA.get(), after.withProgress(after.level(), after.exp(), after.totalExp(), 999));
        int[] plan = AbilityService.maxUpgrade(FantasyWeaponItem.data(stack), slash);
        AbilityService.handleUpgrade(p, 0, id, slash.id(), true);
        WeaponData maxed = FantasyWeaponItem.data(stack);
        h.assertTrue(maxed.abilityLevel(slash) == slash.maxLevel(), "should reach max level " + slash.maxLevel() + ", got " + maxed.abilityLevel(slash));
        h.assertTrue(maxed.masteryPoints() == 999 - plan[1], "cost should match the plan: " + (999 - plan[1]) + " vs " + maxed.masteryPoints());
        cleanup(p);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------
    // ability state machine
    // ------------------------------------------------------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void lockedWeaponCannotCharge(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        giveWeapon(p, "voidfang");
        AbilityService.handleAbilityKey(p, true);
        h.assertFalse(AbilityService.runtime(p).isCharging(), "a level-1 weapon has nothing to charge");
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void abilityOnCooldownCannotCharge(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        ExpService.setLevel(p, stack, 5);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        AbilityRuntime rt = AbilityService.runtime(p);
        rt.setCooldown(id, Voidfang.VOID_SLASH, AbilityService.now(p), 200);
        AbilityService.handleAbilityKey(p, true);
        h.assertFalse(rt.isCharging(), "an ability on cooldown must not start charging at all");
        AbilityService.handleAbilityKey(p, false);
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void heavierWeaponsSwingSlower(GameTestHelper h) {
        // swing speed follows the weight class: the colossal Monolith is slowest, the lance fastest
        float[] order = {WeaponClass.COLOSSAL.attackSpeed(), WeaponClass.WARHAMMER.attackSpeed(), WeaponClass.GREATSWORD.attackSpeed(),
                WeaponClass.LONGSWORD.attackSpeed(), WeaponClass.LANCE.attackSpeed()};
        for (int i = 1; i < order.length; i++) h.assertTrue(order[i - 1] < order[i], "attack speeds should rise from colossal to lance");
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        giveWeapon(p, "monolith");
        double monolith = speedOf(p);
        p.getInventory().clearContent();
        giveWeapon(p, "aetherlance");
        double lance = speedOf(p);
        h.assertTrue(monolith < 1.0 && lance > monolith * 2, "Monolith swings at " + monolith + "/s, Aetherlance at " + lance + "/s");
        cleanup(p);
        h.succeed();
    }

    private static double speedOf(ServerPlayer p) {
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        double base = attr.getBaseValue();
        double[] add = {0};
        p.getMainHandItem().forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (a, m) -> {
            if (a.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) add[0] += m.amount();
        });
        return base + add[0];
    }

    @GameTest(template = ARENA)
    public static void releasingTooEarlyFizzles(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 12.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        ExpService.setLevel(p, stack, 5);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        AbilityService.handleAbilityKey(p, true);
        AbilityRuntime rt = AbilityService.runtime(p);
        h.assertTrue(rt.isCharging() && Voidfang.VOID_SLASH.equals(rt.chargingAbility()), "Void Slash should start charging");
        AbilityService.handleAbilityKey(p, false);
        long cd = rt.cooldownRemaining(id, Voidfang.VOID_SLASH, AbilityService.now(p));
        h.assertFalse(rt.isCharging(), "charge should end on release");
        h.assertTrue(cd > 0 && cd <= ServerConfig.FIZZLE_COOLDOWN_TICKS.getOrDefault(), "fizzle should apply the short fizzle cooldown, got " + cd);
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void voidSlashKillsAndGrantsExp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 6.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        ExpService.setLevel(p, stack, 5);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        long expBefore = FantasyWeaponItem.data(stack).totalExp();
        Husk target = dummy(h, new BlockPos(12, 2, 11), 1f);

        AbilityService.handleAbilityKey(p, true);
        int charge = AbilityService.runtime(p).chargeTicks();
        h.runAfterDelay(charge + 1, () -> {
            AbilityService.handleAbilityKey(p, false);
            AbilityRuntime rt = AbilityService.runtime(p);
            h.assertTrue(rt.cooldownRemaining(id, Voidfang.VOID_SLASH, AbilityService.now(p)) > ServerConfig.FIZZLE_COOLDOWN_TICKS.getOrDefault(),
                    "a full charge should put Void Slash on its real cooldown");
        });
        h.succeedWhen(() -> {
            h.assertFalse(target.isAlive(), "target should be killed by the slash");
            WeaponData d = FantasyWeaponItem.data(stack);
            h.assertTrue(d.kills() == 1, "kill should be credited to the weapon");
            h.assertTrue(d.totalExp() > expBefore, "kill should grant weapon EXP");
            cleanup(p);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void meleeKillGrantsExp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 10.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        Husk target = dummy(h, new BlockPos(12, 2, 12), 1f);
        h.runAfterDelay(2, () -> {
            ready(p);
            p.attack(target);
        });
        h.succeedWhen(() -> {
            h.assertFalse(target.isAlive(), "melee hit should kill the target");
            WeaponData d = FantasyWeaponItem.data(stack);
            h.assertTrue(d.kills() == 1 && d.totalExp() > 0, "melee kill should grant weapon EXP");
            cleanup(p);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void weaponKillsDissolveInsteadOfVanillaPuff(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 10.5), 0);
        giveWeapon(p, "voidfang");
        Husk target = dummy(h, new BlockPos(12, 2, 12), 1f);
        h.runAfterDelay(2, () -> {
            ready(p);
            p.attack(target);
        });
        h.succeedWhen(() -> {
            h.assertTrue(target.isRemoved(), "dead mob should be removed");
            // vanilla removes at deathTime 20 with the puff event; the dissolve removes it earlier
            h.assertTrue(target.deathTime == DeathDissolve.REMOVE_AT, "removed at death tick " + target.deathTime);
            cleanup(p);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void otherFormAbilityTransformsOnlyWhenCast(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 4.5), 0);
        ItemStack stack = giveWeapon(p, "eclipse_reaper");
        ExpService.setLevel(p, stack, 20);
        var def = ((FantasyWeaponItem) stack.getItem()).definition();
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        h.assertTrue("light".equals(def.form(FantasyWeaponItem.data(stack)).id()), "starts in light form");
        AbilityService.handleSelect(p, 0, id, com.fantasyweapons.weapons.eclipse.EclipseReaper.UMBRAL_VORTEX);
        var data = FantasyWeaponItem.data(stack);
        h.assertTrue("light".equals(def.form(data).id()), "selecting Umbral Vortex must not transform the weapon");
        h.assertTrue(AbilityService.selected(def, data) == def.ability(com.fantasyweapons.weapons.eclipse.EclipseReaper.UMBRAL_VORTEX),
                "Umbral Vortex should be the selected ability");
        AbilityService.handleAbilityKey(p, true);
        h.assertTrue("dark".equals(def.form(FantasyWeaponItem.data(stack)).id()), "casting Umbral Vortex transforms the weapon to dark form");
        h.assertTrue(AbilityService.runtime(p).isCharging(), "and starts charging it");
        AbilityService.handleAbilityKey(p, false);
        cleanup(p);
        h.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void voidBlinkStopsAtWalls(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 4.5), 0);
        ItemStack stack = giveWeapon(p, "voidfang");
        ExpService.setLevel(p, stack, 10);
        UUID id = FantasyWeaponItem.data(stack).idOrNil();
        AbilityService.handleSelect(p, 0, id, Voidfang.VOID_BLINK);
        h.assertTrue(Voidfang.VOID_BLINK.equals(FantasyWeaponItem.data(stack).selected()), "Void Blink should be selected");
        // a wall 5 blocks ahead, well inside the blink distance
        for (int x = 8; x <= 16; x++) for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
        double startZ = p.getZ();
        double wallZ = h.absoluteVec(new Vec3(0, 0, 9)).z;

        AbilityService.handleAbilityKey(p, true);
        int charge = AbilityService.runtime(p).chargeTicks();
        h.runAfterDelay(charge + 1, () -> AbilityService.handleAbilityKey(p, false));
        h.runAfterDelay(charge + 4, () -> {
            h.assertTrue(p.getZ() > startZ + 2, "player should have blinked forward (moved " + (p.getZ() - startZ) + ")");
            h.assertTrue(p.getZ() + p.getBbWidth() / 2 <= wallZ + 1e-3, "player must stop before the wall, ended at z=" + p.getZ());
            h.assertFalse(h.getLevel().getBlockState(p.blockPosition()).isSolidRender(h.getLevel(), p.blockPosition()), "player inside a block");
            cleanup(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void monolithReportsGroundSplitsWithoutTouchingTerrain(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 6.5), 0);
        ItemStack stack = giveWeapon(p, "monolith");
        ExpService.setLevel(p, stack, 5);
        java.util.List<GroundSplitAbility.Context> splits = new java.util.concurrent.CopyOnWriteArrayList<>();
        GroundSplitAbility.setHandler(c -> {
            if (c.player() == p) splits.add(c);
        });
        // snapshot the terrain around the impact
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
        for (BlockPos bp : BlockPos.betweenClosed(new BlockPos(2, 0, 0), new BlockPos(22, 3, 18))) before.put(bp.immutable(), h.getBlockState(bp));

        AbilityService.handleAbilityKey(p, true);
        int charge = AbilityService.runtime(p).chargeTicks();
        h.runAfterDelay(charge + 1, () -> AbilityService.handleAbilityKey(p, false));
        h.runAfterDelay(charge + 3, () -> {
            var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            h.assertTrue(speed != null && speed.getValue() < speed.getBaseValue() * 0.5, "wielder should be anchored while the blade is planted");
        });
        h.runAfterDelay(charge + 45, () -> {
            GroundSplitAbility.setHandler(GroundSplitAbility.NONE);
            h.assertTrue(splits.size() == 7, "Earthshatter should report its 7 cracks, got " + splits.size());
            for (GroundSplitAbility.Context c : splits) {
                h.assertTrue(Monolith.EARTHSHATTER.equals(c.abilityId()) && c.abilityLevel() >= 1, "wrong ability in " + c);
                h.assertTrue(c.length() > 1 && Math.abs(c.direction().y) < 1e-6 && Math.abs(c.direction().length() - 1) < 1e-6, "bad crack " + c);
            }
            before.forEach((bp, state) -> h.assertTrue(h.getBlockState(bp) == state, "terrain changed at " + bp));
            var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            h.assertTrue(speed != null && Math.abs(speed.getValue() - speed.getBaseValue()) < 1e-6, "anchor should be released");
            cleanup(p);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void chainbladeHitsHarderSearsAndHeatsUp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 6.5), 0);
        ItemStack stack = giveWeapon(p, "infernochain");
        ExpService.setLevel(p, stack, 15);
        Husk swordTarget = dummy(h, new BlockPos(10, 2, 9), 100000f);
        Husk chainTarget = dummy(h, new BlockPos(15, 2, 9), 100000f);
        // sword form (form 0); random crits would make the comparison flaky
        com.fantasyweapons.combat.MeleeHandler.suppressRandomCrits = true;
        ready(p);
        com.fantasyweapons.combat.MeleeHandler.attack(p, swordTarget);
        float swordDamage = swordTarget.getMaxHealth() - swordTarget.getHealth();
        // chainblade form (form 1)
        WeaponData d = FantasyWeaponItem.data(stack);
        stack.set(ModComponents.WEAPON_DATA.get(), d.withForm(1));
        ready(p);
        com.fantasyweapons.combat.MeleeHandler.attack(p, chainTarget);
        float chainDamage = chainTarget.getMaxHealth() - chainTarget.getHealth();
        com.fantasyweapons.combat.MeleeHandler.suppressRandomCrits = false;
        h.assertTrue(swordDamage > 0 && chainDamage > swordDamage * 1.3f, "chainblade should hit far harder: sword " + swordDamage + " chain " + chainDamage);
        h.assertTrue(StatusService.stacks(chainTarget, StatusType.SEARED) > 0, "chainblade hits should sear");
        h.assertTrue(StatusService.stacks(swordTarget, StatusType.SEARED) == 0, "sword form hits should not sear");
        h.assertTrue(StatusService.stacks(p, StatusType.INFERNO_OVERHEAT) == 2, "each hit should add a heat stack");
        cleanup(p);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------
    // every castable ability of every weapon: charges, executes (not a fizzle/failure) and hurts the dummies
    // ------------------------------------------------------------------------------------------------------------

    @GameTestGenerator
    public static java.util.Collection<TestFunction> everyAbility() {
        java.util.List<TestFunction> tests = new java.util.ArrayList<>();
        for (WeaponDefinition def : Weapons.all()) {
            for (AbilityDefinition a : def.castables()) {
                String name = "ability_" + def.id() + "_" + a.id();
                tests.add(new TestFunction("abilities", name, FantasyWeapons.MOD_ID + ":" + ARENA, 240, 0, true, h -> abilityTest(h, def, a)));
            }
        }
        return tests;
    }

    /** Ability ids whose effect is a self buff / utility and deals no damage by itself. */
    private static final java.util.Set<String> NON_DAMAGING = java.util.Set.of("doomcleaver/blood_rage");

    private static void abilityTest(GameTestHelper h, WeaponDefinition def, AbilityDefinition a) {
        ServerPlayer p = player(h, new Vec3(12.5, 2, 5.5), 0);
        ItemStack stack = giveWeapon(p, def.id());
        ExpService.setLevel(p, stack, Math.max(1, a.unlockLevel()));
        WeaponData d = FantasyWeaponItem.data(stack);
        if (a.requiredForm() != null) {
            for (int i = 0; i < def.forms().size(); i++) if (def.forms().get(i).id().equals(a.requiredForm())) d = d.withForm(i);
        }
        stack.set(ModComponents.WEAPON_DATA.get(), d.withSelected(a.id()));
        UUID id = d.idOrNil();
        java.util.List<Husk> dummies = java.util.List.of(dummy(h, new BlockPos(12, 2, 9), 2000f), dummy(h, new BlockPos(13, 2, 11), 2000f),
                dummy(h, new BlockPos(10, 2, 7), 2000f));
        // look at the first dummy's chest
        Vec3 eye = p.getEyePosition();
        Vec3 to = dummies.get(0).getBoundingBox().getCenter().subtract(eye);
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
        p.teleportTo(h.getLevel(), p.getX(), p.getY(), p.getZ(), 0, pitch);

        AbilityService.handleAbilityKey(p, true);
        AbilityRuntime rt = AbilityService.runtime(p);
        int charge = rt.isCharging() ? rt.chargeTicks() : 0;
        h.assertTrue(charge > 0 || a.chargeTicks() == 0, a.key() + " did not start charging");
        h.runAfterDelay(charge + 1, () -> {
            if (rt.isCharging()) AbilityService.handleAbilityKey(p, false);
            long cd = rt.cooldownRemaining(id, a.id(), AbilityService.now(p));
            h.assertTrue(cd > ServerConfig.FIZZLE_COOLDOWN_TICKS.getOrDefault(), a.key() + " failed or fizzled (cooldown " + cd + ")");
        });
        boolean damaging = !NON_DAMAGING.contains(def.id() + "/" + a.id());
        h.runAfterDelay(charge + 2, () -> h.succeedWhen(() -> {
            if (damaging) {
                h.assertTrue(dummies.stream().anyMatch(du -> du.getHealth() < du.getMaxHealth() || !du.isAlive()), a.key() + " hurt no dummy "
                        + dummies.stream().map(du -> du.getHealth() + "/" + du.getMaxHealth() + "@" + du.position()).toList() + " player " + p.position()
                        + " pitch " + p.getXRot());
            }
            cleanup(p);
        }));
    }

    // ------------------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------------------

    /**
     * A survival mock player with a NeoForge-configured mock connection, so payloads (HUD sync, FX, notifications)
     * can be sent to it like to a real client.
     */
    private static ServerPlayer player(GameTestHelper h, Vec3 relative, float yaw) {
        ServerLevel level = h.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "fwtest" + PLAYERS.incrementAndGet());
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 abs = h.absoluteVec(relative);
        player.teleportTo(level, abs.x, abs.y, abs.z, yaw, 0);
        return player;
    }

    /** Mock players are never ticked, so their weapon never "recovers" by itself: mark it fully recovered. */
    private static void ready(ServerPlayer p) {
        try {
            var f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 10000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void cleanup(ServerPlayer p) {
        p.server.getPlayerList().remove(p);
    }

    private static ItemStack giveWeapon(ServerPlayer p, String weapon) {
        ItemStack stack = new ItemStack(ModItems.WEAPONS.get(weapon).get());
        stack.set(ModComponents.WEAPON_DATA.get(), WeaponData.EMPTY.withId(UUID.randomUUID()));
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, stack);
        return p.getInventory().getItem(0);
    }

    /** A motionless husk (husks don't burn in daylight) with the given health. */
    private static Husk dummy(GameTestHelper h, BlockPos relative, float health) {
        Husk husk = h.spawn(EntityType.HUSK, relative);
        husk.setNoAi(true);
        var max = husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (max != null && health > max.getBaseValue()) max.setBaseValue(health);
        husk.setHealth(health);
        return husk;
    }
}
