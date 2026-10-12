package com.fantasyweapons.client.gui;

import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.refinery.RefineryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Arcane Refinery's screen, in the mod's own style: a leather panel in a bronze frame with the crucible (raw
 * material) above the firebox (fuel), a molten channel filling towards the tray (refined material), the fire's heat
 * and the time left, and the player's inventory below.
 */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final int ARCANE = 0xB85CFF, FIRE = 0xFF6A1A, EMBER = 0xFFD27A, BLAZE = 0xFFE36A;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 182;
        inventoryLabelY = RefineryMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        int x = leftPos, y = topPos;
        UiDraw.panel(g, x, y, imageWidth, imageHeight, ARCANE, 1f);
        UiDraw.divider(g, x + imageWidth / 2f, y + RefineryMenu.INV_Y - 16, imageWidth - 30, 0.8f);
        for (Slot s : menu.slots) slotFrame(g, x + s.x, y + s.y, s.index == 2 ? 0.9f : 0.6f, s.index < 3);

        // the fire: a column of heat between the crucible and the firebox, gold on blaze rods
        float burn = menu.burnFraction();
        int fireX = x + RefineryMenu.FUEL_X + 4, fireTop = y + RefineryMenu.INPUT_Y + 19, fireBottom = y + RefineryMenu.FUEL_Y - 2;
        UiDraw.rect(g, fireX, fireTop, fireX + 8, fireBottom, Colors.alpha(0.85f, 0x0E0804));
        if (burn > 0) {
            float h = (fireBottom - fireTop) * burn;
            int top = menu.blaze() ? BLAZE : EMBER;
            UiDraw.vGradient(g, fireX + 1, fireBottom - h, fireX + 7, fireBottom, Colors.alpha(1f, top), Colors.alpha(1f, FIRE));
            UiDraw.glow(g, fireX + 4, fireBottom - h, 14 + 3 * (float) Math.sin(time * 0.4f), Colors.alpha(0.5f, top));
        }

        // the molten channel from the crucible to the tray
        float p = menu.progress();
        float bx = x + RefineryMenu.INPUT_X + 24, by = y + RefineryMenu.OUTPUT_Y + 5, bw = RefineryMenu.OUTPUT_X - RefineryMenu.INPUT_X - 30;
        UiDraw.bar(g, bx, by, bw, 6, p, FIRE, ARCANE, 1f, p > 0 ? time / 20f : -1);
        if (p > 0) UiDraw.glow(g, bx + bw * p, by + 3, 16, Colors.alpha(0.55f, ARCANE));
    }

    /** A sunken slot: dark groove with a fine bronze edge; machine slots glow faintly. */
    private static void slotFrame(GuiGraphics g, int x, int y, float glow, boolean machine) {
        UiDraw.rect(g, x - 1, y - 1, x + 17, y + 17, Colors.alpha(0.75f, 0x0B0705));
        UiDraw.border(g, x - 2, y - 2, x + 18, y + 18, 1, Colors.alpha(0.85f, 0x8A6A3A));
        if (machine) UiDraw.glowBorder(g, x - 2, y - 2, x + 18, y + 18, ARCANE, 0.35f * glow, 3);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String title = this.title.getString();
        UiDraw.text(g, title, (imageWidth - font.width(title)) / 2f, 8, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 1f, true);
        UiDraw.text(g, "Raw", RefineryMenu.INPUT_X - 22, RefineryMenu.INPUT_Y + 5, Colors.alpha(1f, UiDraw.INK_MUTED), 0.75f, false);
        UiDraw.text(g, "Fuel", RefineryMenu.FUEL_X - 24, RefineryMenu.FUEL_Y + 5, Colors.alpha(1f, UiDraw.INK_MUTED), 0.75f, false);
        UiDraw.text(g, "Refined", RefineryMenu.OUTPUT_X + 22, RefineryMenu.OUTPUT_Y + 5, Colors.alpha(1f, UiDraw.INK_MUTED), 0.75f, false);
        if (menu.progress() > 0) {
            String left = String.format("%.0fs", Math.ceil(menu.secondsLeft()));
            float bx = RefineryMenu.INPUT_X + 24, bw = RefineryMenu.OUTPUT_X - RefineryMenu.INPUT_X - 30;
            UiDraw.text(g, left, bx + (bw - font.width(left) * 0.75f) / 2f, RefineryMenu.OUTPUT_Y + 14, Colors.alpha(1f, UiDraw.INK), 0.75f, false);
        }
        if (menu.blaze() && menu.burning()) {
            UiDraw.text(g, "x2", RefineryMenu.FUEL_X + 20, RefineryMenu.FUEL_Y + 5, Colors.alpha(1f, BLAZE), 0.75f, true);
        }
        UiDraw.text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Colors.alpha(1f, UiDraw.INK_MUTED), 0.75f, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
