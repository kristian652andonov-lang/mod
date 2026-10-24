package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorEffects;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Every armor piece bonus actually changes the game. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorEffectGameTests {
    private static final String ARENA = "arena";

    private ArmorEffectGameTests() {
    }

    private static ServerPlayer wearing(GameTestHelper h, ArmorSet set, ArmorItem.Type... types) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(4.5, 2, 4.5), 0);
        for (ArmorItem.Type t : types) {
            ItemStack stack = new ItemStack(ModItems.ARMOR.get(set).get(t).get());
            p.setItemSlot(t.getSlot(), stack);
        }
        p.doTick(); // the living tick applies the equipment's attribute modifiers
        return p;
    }

    private static void near(GameTestHelper h, String what, double got, double expected) {
        if (!(Math.abs(got - expected) <= 1e-3 * Math.max(1, Math.abs(expected)))) h.fail(what + ": " + got + ", expected " + expected);
    }

    private static Pig pig(GameTestHelper h, int x) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(x, 2, 12));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        return pig;
    }

    /** Ability damage, reach, distances, durations, projectile speed and chain jumps. */
    @GameTest(template = ARENA)
    public static void abilityBonuses(GameTestHelper h) {
        var voidfang = Weapons.get("voidfang");
        var frostrend = Weapons.get("frostrend");
        var aetherlance = Weapons.get("aetherlance");
        var stormbreaker = Weapons.get("stormbreaker");
        var gravebite = Weapons.get("gravebite");
        var monolith = Weapons.get("monolith");
        ServerPlayer p = wearing(h, ArmorSet.VOIDFANG, ArmorItem.Type.HELMET, ArmorItem.Type.BOOTS);
        near(h, "void ability damage", ArmorEffects.abilityDamage(p, voidfang, voidfang.ability("void_slash")), 1.15);
        near(h, "frost ability damage (not void)", ArmorEffects.abilityDamage(p, frostrend, frostrend.ability("frost_slash")), 1.0);
        near(h, "blink distance", ArmorEffects.param(p, voidfang, voidfang.ability("void_blink"), "distance", 10), 12.5);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.STARFORGE, ArmorItem.Type.LEGGINGS);
        near(h, "ability range", ArmorEffects.param(p, voidfang, voidfang.ability("rift_tear"), "radius", 4), 4.6);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.FROSTREND, ArmorItem.Type.LEGGINGS);
        near(h, "ice ability range", ArmorEffects.param(p, frostrend, frostrend.ability("ice_spikes"), "length", 10), 12);
        near(h, "ice range on another weapon", ArmorEffects.param(p, voidfang, voidfang.ability("rift_tear"), "radius", 4), 4);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.AETHERLANCE, ArmorItem.Type.HELMET, ArmorItem.Type.BOOTS);
        near(h, "projectile speed", ArmorEffects.param(p, aetherlance, aetherlance.ability("aether_bolt"), "speed", 2), 2.5);
        near(h, "dash distance", ArmorEffects.param(p, aetherlance, aetherlance.ability("piercing_charge"), "distance", 8), 10);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.STORMBREAKER, ArmorItem.Type.HELMET);
        near(h, "chain jumps", ArmorEffects.param(p, stormbreaker, stormbreaker.ability("chain_lightning"), "jumps", 3), 4);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.SOULREAPER, ArmorItem.Type.LEGGINGS);
        near(h, "ability duration", ArmorEffects.param(p, voidfang, voidfang.ability("void_dimension"), "duration", 10), 12);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.GRAVEBITE, ArmorItem.Type.HELMET, ArmorItem.Type.LEGGINGS);
        var legion = gravebite.ability("legion_of_the_damned");
        near(h, "summon damage", ArmorEffects.abilityDamage(p, gravebite, legion), 1.2);
        near(h, "summon duration", ArmorEffects.param(p, gravebite, legion, "duration", 10), 12.5);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.MONOLITH, ArmorItem.Type.BOOTS);
        near(h, "ground-slam damage", ArmorEffects.abilityDamage(p, monolith, monolith.ability("earthshatter")), 1.2);
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Cooldown reduction, charge speed and faster mode switching. */
    @GameTest(template = ARENA)
    public static void cooldownChargeAndModeSwitch(GameTestHelper h) {
        ServerPlayer p = wearing(h, ArmorSet.VOIDFANG, ArmorItem.Type.LEGGINGS);
        near(h, "cooldown", ArmorEffects.cooldown(p, 100), 90);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.SOLARIS, ArmorItem.Type.LEGGINGS);
        near(h, "charge ticks", ArmorEffects.chargeTicks(p, 30), 25);
        FWGameTests.cleanup(p);
        p = wearing(h, ArmorSet.ECLIPSE_REAPER, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS);
        near(h, "mode switch cooldown", ArmorEffects.formCooldown(p, 20), 14);
        ArmorEffects.onFormSwitch(p);
        if (!p.hasEffect(MobEffects.ABSORPTION)) h.fail("no mode-switch shield");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Void damage, critical and heavy hits, life steal, soul drain and life drain. */
    @GameTest(template = ARENA)
    public static void damageDealtBonuses(GameTestHelper h) {
        ServerPlayer plain = FWGameTests.player(h, new Vec3(4.5, 2, 4.5), 0);
        ServerPlayer armored = wearing(h, ArmorSet.VOIDFANG, ArmorItem.Type.CHESTPLATE);
        Pig a = pig(h, 4), b = pig(h, 8);
        float base = FWDamage.deal(plain, ItemStack.EMPTY, a, 10, FWDamage.Kind.ABILITY, Element.VOID, 0);
        float boosted = FWDamage.deal(armored, ItemStack.EMPTY, b, 10, FWDamage.Kind.ABILITY, Element.VOID, 0);
        near(h, "void damage", boosted / base, 1.1);
        FWGameTests.cleanup(plain);
        FWGameTests.cleanup(armored);

        ServerPlayer berserker = wearing(h, ArmorSet.DOOMCLEAVER, ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.BOOTS);
        near(h, "critical damage", ArmorEffects.meleeMultiplier(berserker, true, false), 1.2);
        near(h, "heavy attack damage", ArmorEffects.meleeMultiplier(berserker, false, true), 1.15);
        near(h, "life steal", ArmorEffects.lifeSteal(berserker), 0.05);
        FWGameTests.cleanup(berserker);

        ServerPlayer necro = wearing(h, ArmorSet.GRAVEBITE, ArmorItem.Type.CHESTPLATE);
        necro.setHealth(10);
        float dealt = FWDamage.deal(necro, ItemStack.EMPTY, pig(h, 12), 20, FWDamage.Kind.ABILITY, Element.NECROMANCY, 0);
        near(h, "soul drain heal", necro.getHealth() - 10, dealt * 0.05);
        FWGameTests.cleanup(necro);

        ServerPlayer reaper = wearing(h, ArmorSet.SOULREAPER, ArmorItem.Type.CHESTPLATE);
        reaper.setHealth(10);
        dealt = FWDamage.deal(reaper, ItemStack.EMPTY, pig(h, 16), 20, FWDamage.Kind.ABILITY, Element.SOUL, 0);
        near(h, "life drain heal", reaper.getHealth() - 10, dealt * 0.05);
        FWGameTests.cleanup(reaper);
        h.succeed();
    }

    /** Frost, fire and undead resistance, fall immunity and gravity resistance. */
    @GameTest(template = ARENA)
    public static void resistances(GameTestHelper h) {
        var sources = h.getLevel().damageSources();
        // the same class of chestplate with and without the resistance; burning and freezing bypass armor, so the
        // ratio of the damage taken is the resistance itself
        near(h, "fire resistance", taken(h, ArmorSet.INFERNOCHAIN, ArmorSet.DOOMCLEAVER, ArmorItem.Type.CHESTPLATE, sources.onFire()), 0.7);
        near(h, "frost resistance", taken(h, ArmorSet.FROSTREND, ArmorSet.SOLARIS, ArmorItem.Type.CHESTPLATE, sources.freeze()), 0.75);
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setNoAi(true);
        // a zombie's blow goes through armor, which blocks a larger share of a smaller hit: check the multiplier itself,
        // and that the wearer really takes less
        ServerPlayer undead = wearing(h, ArmorSet.GRAVEBITE, ArmorItem.Type.BOOTS);
        near(h, "undead resistance", ArmorEffects.incoming(undead, sources.mobAttack(zombie)), 0.8);
        FWGameTests.cleanup(undead);
        double undeadRatio = taken(h, ArmorSet.GRAVEBITE, ArmorSet.DOOMCLEAVER, ArmorItem.Type.BOOTS, sources.mobAttack(zombie));
        if (!(undeadRatio > 0 && undeadRatio < 0.85)) h.fail("undead resistance taken: " + undeadRatio);
        ServerPlayer immune = wearing(h, ArmorSet.STARFORGE, ArmorItem.Type.BOOTS);
        float before = immune.getHealth();
        immune.causeFallDamage(20, 1, sources.fall());
        if (immune.getHealth() < before) h.fail("fall damage with fall-damage immunity");
        FWGameTests.cleanup(immune);
        near(h, "gravity resistance (fall)", ArmorEffects.fallDamage(wearing(h, ArmorSet.STARFORGE, ArmorItem.Type.CHESTPLATE)), 0.7);
        h.succeed();
    }

    /** Damage taken by a wearer of {@code with} relative to one of {@code without} (same piece, same class). */
    private static double taken(GameTestHelper h, ArmorSet with, ArmorSet without, ArmorItem.Type type, net.minecraft.world.damagesource.DamageSource source) {
        ServerPlayer a = wearing(h, with, type), b = wearing(h, without, type);
        float ha = a.getHealth(), hb = b.getHealth();
        a.hurt(source, 10);
        b.hurt(source, 10);
        double ratio = (ha - a.getHealth()) / (hb - b.getHealth());
        FWGameTests.cleanup(a);
        FWGameTests.cleanup(b);
        return ratio;
    }

    /** Longer freezes and poisons applied by the wearer; shorter freezes on a frost-resistant wearer. */
    @GameTest(template = ARENA)
    public static void statusDurations(GameTestHelper h) {
        ServerPlayer frost = wearing(h, ArmorSet.FROSTREND, ArmorItem.Type.HELMET);
        Pig a = pig(h, 4);
        near(h, "freeze duration", StatusService.apply(a, StatusType.FROSTBITE, 100, 1, 0.1f, frost.getUUID()).duration, 125);
        FWGameTests.cleanup(frost);
        ServerPlayer bloom = wearing(h, ArmorSet.BLOOMFALL, ArmorItem.Type.HELMET);
        near(h, "poison duration", StatusService.apply(pig(h, 8), StatusType.NATURE_POISON, 100, 1, 0.1f, bloom.getUUID()).duration, 125);
        FWGameTests.cleanup(bloom);
        ServerPlayer resistant = wearing(h, ArmorSet.FROSTREND, ArmorItem.Type.CHESTPLATE);
        near(h, "frost resistance (status)", StatusService.apply(resistant, StatusType.FROSTBITE, 100, 1, 0.1f, null).duration, 75);
        FWGameTests.cleanup(resistant);
        h.succeed();
    }

    /** Regeneration, speed on ice, speed in dark mode and the burning dash. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void tickedBonuses(GameTestHelper h) {
        ServerPlayer grove = wearing(h, ArmorSet.BLOOMFALL, ArmorItem.Type.CHESTPLATE);
        grove.setHealth(10);
        for (int i = 0; i < 81; i++) {
            grove.tickCount++;
            ArmorEffects.tick(grove);
        }
        if (grove.getHealth() <= 10) h.fail("no regeneration in 4 seconds");
        FWGameTests.cleanup(grove);

        h.setBlock(new BlockPos(4, 1, 4), Blocks.PACKED_ICE);
        ServerPlayer frost = wearing(h, ArmorSet.FROSTREND, ArmorItem.Type.BOOTS);
        frost.teleportTo(h.getLevel(), h.absoluteVec(new Vec3(4.5, 2, 4.5)).x, h.absoluteVec(new Vec3(4.5, 2, 4.5)).y,
                h.absoluteVec(new Vec3(4.5, 2, 4.5)).z, 0, 0);
        ArmorEffects.tick(frost);
        near(h, "speed on ice", frost.getAttributeValue(Attributes.MOVEMENT_SPEED) / frost.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), 1.25);
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        ArmorEffects.tick(frost);
        near(h, "speed off the ice", frost.getAttributeValue(Attributes.MOVEMENT_SPEED) / frost.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), 1.0);
        FWGameTests.cleanup(frost);

        ServerPlayer burner = wearing(h, ArmorSet.INFERNOCHAIN, ArmorItem.Type.BOOTS);
        var aetherlance = Weapons.get("aetherlance");
        ArmorEffects.onAbilityCast(burner, aetherlance, aetherlance.ability("piercing_charge"));
        LivingEntity target = pig(h, 12);
        FWDamage.deal(burner, ItemStack.EMPTY, target, 2, FWDamage.Kind.ABILITY, Element.ENERGY, 0);
        if (target.getRemainingFireTicks() <= 0) h.fail("a burning dash did not ignite what it hit");
        FWGameTests.cleanup(burner);
        h.succeed();
    }
}
