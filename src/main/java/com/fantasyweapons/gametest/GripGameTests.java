package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.weapon.TwoHandedGrip;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
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

    /**
     * No trick of the inventory screen puts a two-handed weapon in the off hand: the swap-hands key over the weapon, a
     * hotbar key over the off-hand slot, the weapon clicked into the slot. A one-handed weapon still goes there.
     */
    @GameTest(template = ARENA)
    public static void inventoryTricksKeepTwoHandedOut(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        var inventory = p.getInventory();
        var menu = p.inventoryMenu;
        // the inventory menu: hotbar slot 0 is menu slot 36, the off hand menu slot 45
        int hotbar0 = 36, offHand = 45;
        ItemStack axe = FWGameTests.giveWeapon(p, "doomcleaver");
        menu.clicked(hotbar0, Inventory.SLOT_OFFHAND, ClickType.SWAP, p);
        if (!inventory.offhand.get(0).isEmpty()) h.fail("the swap-hands key over the Doomcleaver put it in the off hand");
        menu.clicked(offHand, 0, ClickType.SWAP, p);
        if (!inventory.offhand.get(0).isEmpty()) h.fail("hotbar key 1 over the off-hand slot put the Doomcleaver in the off hand");
        inventory.setItem(0, ItemStack.EMPTY);
        menu.setCarried(axe);
        menu.clicked(offHand, 0, ClickType.PICKUP, p);
        if (!inventory.offhand.get(0).isEmpty()) h.fail("the Doomcleaver was clicked into the off-hand slot");
        if (menu.getCarried() != axe) h.fail("the refused Doomcleaver left the cursor");
        menu.setCarried(ItemStack.EMPTY);
        ItemStack sword = FWGameTests.giveWeapon(p, "voidfang");
        menu.clicked(hotbar0, Inventory.SLOT_OFFHAND, ClickType.SWAP, p);
        if (inventory.offhand.get(0) != sword) h.fail("the one-handed Voidfang could not be swapped into the off hand");
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
