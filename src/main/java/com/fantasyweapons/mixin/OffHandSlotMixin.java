package com.fantasyweapons.mixin;

import com.fantasyweapons.weapon.TwoHandedGrip;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The off-hand slot of a player's inventory takes no two-handed weapon (see TwoHandedGrip): not clicked or dropped in,
 * not shift-clicked, not swapped in with a hotbar key while over the slot, in the inventory or the creative menu alike.
 */
@Mixin(Slot.class)
public abstract class OffHandSlotMixin {
    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void fantasyweapons$noTwoHandedInOffHand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof Inventory && getContainerSlot() == Inventory.SLOT_OFFHAND && TwoHandedGrip.isTwoHanded(stack)) cir.setReturnValue(false);
    }
}
