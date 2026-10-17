package com.fantasyweapons.guide;

import com.fantasyweapons.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Every player is given a Wayfarer's Codex the first time they join a world (once: it is remembered through death). */
public final class CodexGift {
    private static final String GIVEN = "fantasyweapons:codex_given";

    private CodexGift() {
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) return;
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack codex = new ItemStack(ModItems.CODEX.get());
        if (!player.getInventory().add(codex)) player.drop(codex, false);
        player.sendSystemMessage(Component.literal("A worn book is in your pack: the Wayfarer's Codex. Use it to read.")
                .withStyle(s -> s.withColor(0xF2C46B).withItalic(true)));
    }
}
