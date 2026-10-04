package com.fantasyweapons.client.input;

import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.gui.ProgressionScreen;
import com.fantasyweapons.network.C2SPayloads;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns key presses into requests. The client never decides anything: it only tells the server
 * "ability key down/up", "cycle", "switch form"; the server validates and answers through synced state.
 */
public final class InputHandler {
    private static boolean abilityWasDown;

    private InputHandler() {
    }

    /** Before vanilla handles key binds: keep F from also swapping hands while holding a form-switching weapon. */
    public static void preTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = ClientState.heldWeapon(mc.player);
        if (held.getItem() instanceof FantasyWeaponItem item && item.definition().hasForms()
                && KeyBindings.FORM.getKey().equals(mc.options.keySwapOffhand.getKey())) {
            while (mc.options.keySwapOffhand.consumeClick()) {
                // consumed: the press is used for the form switch instead
            }
        }
    }

    public static void postTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            abilityWasDown = false;
            return;
        }
        ItemStack held = ClientState.heldWeapon(mc.player);
        boolean down = KeyBindings.ABILITY.isDown() && mc.screen == null && !held.isEmpty();
        if (down && !abilityWasDown) {
            PacketDistributor.sendToServer(new C2SPayloads.AbilityKey(true));
            FantasyWeaponItem item = (FantasyWeaponItem) held.getItem();
            ClientState.predictCharge(item.definition(), FantasyWeaponItem.data(held));
        } else if (!down && abilityWasDown) {
            PacketDistributor.sendToServer(new C2SPayloads.AbilityKey(false));
            ClientState.clearPrediction();
        }
        abilityWasDown = down;

        while (KeyBindings.FORM.consumeClick()) {
            if (held.getItem() instanceof FantasyWeaponItem item && item.definition().hasForms()) {
                PacketDistributor.sendToServer(new C2SPayloads.SwitchForm());
            }
        }
        while (KeyBindings.CYCLE.consumeClick()) {
            if (!held.isEmpty()) PacketDistributor.sendToServer(new C2SPayloads.CycleAbility(mc.player.isShiftKeyDown() ? -1 : 1));
        }
        while (KeyBindings.MENU.consumeClick()) {
            int slot = ProgressionScreen.findWeaponSlot(mc.player);
            if (slot >= 0) mc.setScreen(new ProgressionScreen(slot));
        }
    }
}
