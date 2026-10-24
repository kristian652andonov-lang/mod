package com.fantasyweapons.client.devtest;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** DEVELOPMENT ONLY: shows the full tooltip of an item stack for screenshots. */
final class TooltipPreviewScreen extends Screen {
    private final ItemStack stack;

    TooltipPreviewScreen(ItemStack stack) {
        super(Component.literal("tooltip"));
        this.stack = stack;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.renderTooltip(font, stack, 12, 20);
    }
}
