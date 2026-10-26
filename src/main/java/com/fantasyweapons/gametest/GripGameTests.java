package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.weapon.TwoHandedGrip;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Two-handed weapons stay out of the off hand (see {@link TwoHandedGrip}); one-handed ones may go there. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GripGameTests {
    private static final String ARENA = "arena";

    private GripGameTests() {
    }

    /** The swap-hands key leaves a two-handed weapon in the main hand, and moves a one-handed one as ever. */
    @GameTest(template = ARENA)
    public static void swapKeyKeepsTwoHandedInMainHand(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        ItemStack axe = FWGameTests.giveWeapon(p, "doomcleaver");
        p.setItemInHand(InteractionHand.MAIN_HAND, axe);
        if (!CommonHooks.onLivingSwapHandItems(p).isCanceled()) h.fail("the swap key moved the two-handed Doomcleaver to the off hand");
        ItemStack sword = FWGameTests.giveWeapon(p, "voidfang");
        p.setItemInHand(InteractionHand.MAIN_HAND, sword);
        if (CommonHooks.onLivingSwapHandItems(p).isCanceled()) h.fail("the swap key could not move the one-handed Voidfang to the off hand");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A two-handed weapon put in the off hand goes to the free main hand, or into the inventory when the hand is full. */
    @GameTest(template = ARENA)
    public static void twoHandedLeavesTheOffHand(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        var inventory = p.getInventory();
        ItemStack scythe = FWGameTests.giveWeapon(p, "soulreaper");
        inventory.clearContent();
        inventory.offhand.set(0, scythe);
        TwoHandedGrip.tick(p);
        if (inventory.getSelected() != scythe || !inventory.offhand.get(0).isEmpty()) h.fail("the scythe did not go from the off hand to the empty main hand");
        inventory.clearContent();
        inventory.setItem(inventory.selected, new ItemStack(Items.BREAD));
        inventory.offhand.set(0, scythe);
        TwoHandedGrip.tick(p);
        if (!inventory.offhand.get(0).isEmpty() || inventory.findSlotMatchingItem(scythe) < 0) h.fail("the scythe did not go from the off hand into the inventory");
        if (!inventory.getSelected().is(Items.BREAD)) h.fail("the main hand's bread was moved");
        // a one-handed weapon may stay
        inventory.clearContent();
        ItemStack sword = FWGameTests.giveWeapon(p, "voidfang");
        inventory.clearContent();
        inventory.offhand.set(0, sword);
        TwoHandedGrip.tick(p);
        if (inventory.offhand.get(0) != sword) h.fail("the one-handed Voidfang was taken out of the off hand");
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
