package com.fantasyweapons.mixin;

import com.fantasyweapons.weapon.TwoHandedGrip;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The swap-hands key pressed over a slot in any menu puts that slot's item straight into the off hand, without asking the
 * off-hand slot: for a two-handed weapon (see TwoHandedGrip) it does nothing.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class OffHandSwapMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void fantasyweapons$noTwoHandedSwappedToOffHand(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (clickType != ClickType.SWAP || button != Inventory.SLOT_OFFHAND || slotId < 0 || slotId >= slots.size()) return;
        if (TwoHandedGrip.isTwoHanded(slots.get(slotId).getItem())) ci.cancel();
    }
}
