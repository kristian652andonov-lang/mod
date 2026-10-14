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
