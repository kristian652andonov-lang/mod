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
    /** Whether the ability key was down last tick while a legendary tool was held. */
    private static boolean toolKeyWasDown;

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

    /**
     * The weapon's recovery is real: until it is (nearly) recovered the attack key does nothing — no swing, no
     * attack packet. Mining blocks with a weapon is left alone.
     */
    public static void onInteractionKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof FantasyWeaponItem)) return;
        boolean ready = mc.player.getAttackStrengthScale(0f) >= 0.97f;
        // a staff or spellbook never strikes or digs: every click looses a bolt at the crosshair once it has recovered
        if (com.fantasyweapons.magic.MageBolts.isFocus(mc.player.getMainHandItem())) {
            event.setCanceled(true);
            event.setSwingHand(ready);
            if (ready) {
                PacketDistributor.sendToServer(new C2SPayloads.CastBolt());
                mc.player.resetAttackStrengthTicker();
            }
            return;
        }
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // a block can still be broken by holding attack, but the weapon only swings at its own pace: a fresh click
            // waits for the cooldown like any other attack, and every swing restarts it
            boolean mining = mc.gameMode != null && mc.gameMode.isDestroying();
            if (!ready) {
                event.setSwingHand(false);
                if (!mining) event.setCanceled(true);
            } else {
                mc.player.resetAttackStrengthTicker();
            }
            return;
        }
        if (!ready) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    public static void postTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            abilityWasDown = false;
            return;
        }
        ItemStack held = ClientState.heldWeapon(mc.player);
        // a legendary tool's power: once a press
        boolean toolDown = KeyBindings.ABILITY.isDown() && mc.screen == null && mc.player.getMainHandItem().getItem() instanceof com.fantasyweapons.tool.LegendaryTool;
        if (toolDown && !toolKeyWasDown) PacketDistributor.sendToServer(new C2SPayloads.ToolAbility());
        toolKeyWasDown = toolDown;
        boolean down = KeyBindings.ABILITY.isDown() && mc.screen == null && !held.isEmpty();
        if (down && !abilityWasDown && com.fantasyweapons.client.gui.TargetMapScreen.openFor(held)) {
            // a remote ability: it is cast on the targeting map instead (the key is not held down meanwhile)
            down = false;
        } else if (down && !abilityWasDown) {
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
        while (KeyBindings.QUESTS.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen());
        }
        while (KeyBindings.CLASSES.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new com.fantasyweapons.client.gui.ClassScreen());
        }
        while (KeyBindings.MENU.consumeClick()) {
            int slot = ProgressionScreen.findWeaponSlot(mc.player);
            if (slot >= 0) mc.setScreen(new ProgressionScreen(slot));
        }
    }
}
