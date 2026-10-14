package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.playerclass.ClassData;
import com.fantasyweapons.playerclass.ClassService;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The classes: what each lays on the player, the wait between changes, the Mage still sealed, and the perks in a fight. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassGameTests {
    private static final String ARENA = "arena";

    private ClassGameTests() {
    }

    /** As if the last change were long ago. */
    private static void waitOut(ServerPlayer p) {
        p.setData(ModAttachments.PLAYER_CLASS, new ClassData(ClassService.of(p), p.level().getGameTime() - ClassService.CHANGE_WAIT - 1));
    }

    /** A class changes the player's attributes, the next change waits, and changing takes the old class's changes off. */
    @GameTest(template = ARENA)
    public static void classesChangeThePlayer(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        double baseSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        ClassService.choose(p, PlayerClass.ASSASSIN);
        if (ClassService.of(p) != PlayerClass.ASSASSIN) h.fail("the first choice was refused");
        if (p.getMaxHealth() != 16) h.fail("assassin max health " + p.getMaxHealth());
        if (p.getAttributeValue(Attributes.MOVEMENT_SPEED) <= baseSpeed) h.fail("the assassin is not faster");
        ClassService.choose(p, PlayerClass.BERSERKER);
        if (ClassService.of(p) != PlayerClass.ASSASSIN) h.fail("changed class without waiting");
        if (ClassService.waitLeft(p) <= 0) h.fail("no wait after choosing");
        waitOut(p);
        ClassService.choose(p, PlayerClass.PALADIN);
        if (ClassService.of(p) != PlayerClass.PALADIN) h.fail("the change after the wait was refused");
        if (p.getMaxHealth() != 26) h.fail("paladin max health " + p.getMaxHealth());
        if (p.getAttributeValue(Attributes.ARMOR) < 4) h.fail("paladin armor " + p.getAttributeValue(Attributes.ARMOR));
        if (p.getAttributeValue(Attributes.MOVEMENT_SPEED) >= baseSpeed) h.fail("the assassin's speed stayed or the paladin's slowness is missing");
        // laid on again (a login or respawn) it stacks nothing
        ClassService.apply(p);
        ClassService.apply(p);
        if (p.getMaxHealth() != 26) h.fail("applying twice gave " + p.getMaxHealth());
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A player joining for the first time is offered the class menu, and only the first time. */
    @GameTest(template = ARENA)
    public static void theMenuIsOfferedOnJoin(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        ClassData d = p.getData(ModAttachments.PLAYER_CLASS);
        if (!d.offered()) h.fail("joining did not offer the class menu");
        if (d.playerClass() != PlayerClass.NONE) h.fail("joining chose a class");
        // saved and read back, the offer is remembered
        var saved = ClassData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, d).getOrThrow();
        if (!ClassData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow().offered()) h.fail("the offer was not saved");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A player bound by the class rules (test players may use everything unless this is taken away). */
    private static ServerPlayer bound(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        p.removeTag(com.fantasyweapons.playerclass.ClassGear.ANY_CLASS);
        return p;
    }

    private static void recovered(ServerPlayer p) {
        try {
            var f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 10000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Each weapon and armor set answers to its set's class; there are gear for all three classes. */
    @GameTest(template = ARENA)
    public static void gearHasAClass(GameTestHelper h) {
        java.util.Set<PlayerClass> seen = java.util.EnumSet.noneOf(PlayerClass.class);
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            PlayerClass c = com.fantasyweapons.playerclass.ClassGear.ofWeapon(def.id());
            if (c == PlayerClass.NONE || !c.available()) h.fail(def.id() + " answers to " + c);
            seen.add(c);
            var set = com.fantasyweapons.armor.ArmorSet.ofWeapon(def.id());
            ItemStack chest = new ItemStack(com.fantasyweapons.registry.ModItems.ARMOR.get(set).get(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE).get());
            if (com.fantasyweapons.playerclass.ClassGear.of(chest) != c) h.fail(def.id() + "'s armor answers to another class");
        }
        if (!seen.containsAll(java.util.List.of(PlayerClass.ASSASSIN, PlayerClass.BERSERKER, PlayerClass.PALADIN))) h.fail("not every class has gear: " + seen);
        h.succeed();
    }

    /** Only the weapon's class strikes with it; anyone else's blow does nothing. */
    @GameTest(template = ARENA)
    public static void onlyItsClassWieldsAWeapon(GameTestHelper h) {
        ServerPlayer p = bound(h);
        String weapon = "voidfang";
        PlayerClass need = com.fantasyweapons.playerclass.ClassGear.ofWeapon(weapon);
        PlayerClass other = need == PlayerClass.PALADIN ? PlayerClass.BERSERKER : PlayerClass.PALADIN;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.fantasyweapons.registry.ModItems.WEAPONS.get(weapon).get()));
        Mob pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 4));
        pig.setNoAi(true);
        float hp = pig.getHealth();
        recovered(p);
        com.fantasyweapons.combat.MeleeHandler.attack(p, pig);
        if (pig.getHealth() < hp) h.fail("a classless player struck with the " + weapon);
        ClassService.set(p, other, false);
        recovered(p);
        com.fantasyweapons.combat.MeleeHandler.attack(p, pig);
        if (pig.getHealth() < hp) h.fail("a " + other + " struck with the " + weapon);
        ClassService.set(p, need, false);
        recovered(p);
        pig.invulnerableTime = 0;
        com.fantasyweapons.combat.MeleeHandler.attack(p, pig);
        if (pig.getHealth() >= hp) h.fail("its own class could not strike with the " + weapon);
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Armor of another class cannot be put on, and comes off when the class changes. */
    @GameTest(template = ARENA)
    public static void onlyItsClassWearsArmor(GameTestHelper h) {
        ServerPlayer p = bound(h);
        var set = com.fantasyweapons.armor.ArmorSet.SOLARIS;
        PlayerClass need = com.fantasyweapons.playerclass.ClassGear.of(set.armorClass());
        PlayerClass other = need == PlayerClass.ASSASSIN ? PlayerClass.BERSERKER : PlayerClass.ASSASSIN;
        ItemStack helmet = new ItemStack(com.fantasyweapons.registry.ModItems.ARMOR.get(set).get(net.minecraft.world.item.ArmorItem.Type.HELMET).get());
        if (helmet.canEquip(net.minecraft.world.entity.EquipmentSlot.HEAD, p)) h.fail("a classless player can put on " + set);
        ClassService.set(p, need, false);
        if (!helmet.canEquip(net.minecraft.world.entity.EquipmentSlot.HEAD, p)) h.fail("its own class cannot put on " + set);
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet.copy());
        ClassService.set(p, other, false);
        if (!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) h.fail("the helmet stayed on after the class changed");
        if (p.getInventory().countItem(helmet.getItem()) != 1) h.fail("the helmet taken off was not given back");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** The Mage is still being made: it cannot be taken. */
    @GameTest(template = ARENA)
    public static void theMageIsSealed(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        ClassService.choose(p, PlayerClass.MAGE);
        if (ClassService.of(p) != PlayerClass.NONE) h.fail("the Mage was taken");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    private static float hit(GameTestHelper h, ServerPlayer p, EntityType<? extends Mob> type, int x, float yaw, float amount) {
        Mob mob = h.spawn(type, new BlockPos(x, 2, 6));
        mob.setNoAi(true);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yHeadRotO = yaw;
        float before = mob.getHealth();
        mob.hurt(p.damageSources().playerAttack(p), amount);
        float dealt = before - mob.getHealth();
        mob.discard();
        return dealt;
    }

    /** Backstab, rage and smite: each class hits harder in its own way. */
    @GameTest(template = ARENA)
    public static void perksChangeTheBlow(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        // the player stands at z 2.5, the creatures at z 6.5: facing south (yaw 0) is facing away from them
        float plain = hit(h, p, EntityType.PIG, 2, 0, 2);
        ClassService.choose(p, PlayerClass.ASSASSIN);
        float behind = hit(h, p, EntityType.PIG, 2, 0, 2), front = hit(h, p, EntityType.PIG, 2, 180, 2);
        if (Math.abs(behind - plain * 1.6f) > 0.05f) h.fail("backstab dealt " + behind + " of " + plain);
        if (Math.abs(front - plain) > 0.05f) h.fail("a blow from the front dealt " + front + " of " + plain);
        waitOut(p);
        ClassService.choose(p, PlayerClass.BERSERKER);
        p.setHealth(p.getMaxHealth() * 0.25f);
        float rage = hit(h, p, EntityType.PIG, 2, 180, 2);
        if (Math.abs(rage - plain * 1.5f) > 0.05f) h.fail("rage at a quarter health dealt " + rage + " of " + plain);
        p.setHealth(p.getMaxHealth());
        waitOut(p);
        ClassService.choose(p, PlayerClass.PALADIN);
        float skeletonPlain = 2, smite = hit(h, p, EntityType.SKELETON, 2, 180, 2);
        if (Math.abs(smite - skeletonPlain * 1.5f) > 0.05f) h.fail("smite dealt " + smite);
        LivingEntity skeleton = h.spawn(EntityType.SKELETON, new BlockPos(4, 2, 6));
        float hp = p.getHealth();
        p.hurt(p.damageSources().mobAttack(skeleton), 4);
        float taken = hp - p.getHealth();
        if (taken >= 4 * 0.8f) h.fail("aegis let " + taken + " of 4 through");
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
