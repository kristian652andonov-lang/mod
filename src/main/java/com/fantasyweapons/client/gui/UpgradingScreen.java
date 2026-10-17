package com.fantasyweapons.client.gui;

import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.tool.LegendaryTool;
import com.fantasyweapons.tool.LegendaryTools;
import com.fantasyweapons.tool.ToolUpgrades;
import com.fantasyweapons.tool.UpgradeTrack;
import com.fantasyweapons.tool.UpgradingMenu;
import com.fantasyweapons.tool.UpgradingTableBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Legendary Upgrading Table's screen, copper and experience-green on dark metal like the table: the vise (the tool,
 * large) and the cup (its refined material) on the left; on the right the three things the table raises - mining area,
 * Fortune, Efficiency - each with where it stands, what the next step makes of it, what that costs (cores and levels,
 * green when there is enough) and a button to raise it; while the table works, the step under way fills its row.
 */
public class UpgradingScreen extends AbstractContainerScreen<UpgradingMenu> {
    private static final int COPPER = 0xE58A55, COPPER_LIGHT = 0xFFC79E, XP = 0x9CF06A, XP_DARK = 0x3E8A2A, LINE = 0x8C6A55, BAD = 0xE8746A;
    private static final int BUTTON_W = 42, BUTTON_H = 14;

    public UpgradingScreen(UpgradingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = UpgradingMenu.WIDTH;
        imageHeight = UpgradingMenu.HEIGHT;
        inventoryLabelX = UpgradingMenu.INV_X;
        inventoryLabelY = UpgradingMenu.INV_Y - 11;
    }

    private float time(float partialTick) {
        return (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float time = time(partialTick);
        int x = leftPos, y = topPos;
        UiDraw.panel(g, x, y, imageWidth, imageHeight, COPPER, 1f);
        // the vise and the cup
        UiDraw.rect(g, x + 10, y + 24, x + 60, y + 132, Colors.alpha(0.35f, 0x000000));
        UiDraw.border(g, x + 10, y + 24, x + 60, y + 132, 1, Colors.alpha(0.5f, LINE));
        UiDraw.divider(g, x + imageWidth / 2f, y + UpgradingMenu.INV_Y - 18, imageWidth - 30, 0.8f);
        boolean working = menu.working();
        for (Slot s : menu.slots) {
            if (s.index < UpgradingTableBlockEntity.SLOTS) {
                boolean vise = s.index == UpgradingTableBlockEntity.TOOL;
                UiDraw.sigil(g, x + s.x + 8, y + s.y + 8, vise ? 20 : 13, working ? XP : COPPER, time, working ? 1f : s.hasItem() ? 0.6f : 0.35f);
                machineSlot(g, x + s.x, y + s.y, vise);
            } else {
                slotFrame(g, x + s.x, y + s.y);
            }
        }
        ItemStack tool = menu.tool();
        for (UpgradeTrack t : UpgradeTrack.values()) row(g, t, tool, mouseX, mouseY, time);
    }

    private int rowY(UpgradeTrack t) {
        return topPos + UpgradingMenu.ROWS_Y + t.ordinal() * UpgradingMenu.ROW_H;
    }

    private int buttonX() {
        return leftPos + UpgradingMenu.ROWS_X + UpgradingMenu.ROW_W - BUTTON_W - 4;
    }

    /** One thing the table raises: where it stands, the next step, its cost and its button (or the step under way). */
    private void row(GuiGraphics g, UpgradeTrack t, ItemStack tool, int mouseX, int mouseY, float time) {
        int rx = leftPos + UpgradingMenu.ROWS_X, ry = rowY(t), rw = UpgradingMenu.ROW_W, rh = UpgradingMenu.ROW_H - 4;
        boolean has = tool.getItem() instanceof LegendaryTool;
        ToolUpgrades u = has ? LegendaryTools.upgrades(tool) : ToolUpgrades.BASE;
        boolean underWay = menu.track() == t;
        UiDraw.rect(g, rx, ry, rx + rw, ry + rh, Colors.alpha(underWay ? 0.5f : 0.32f, underWay ? 0x0E1E08 : 0x000000));
        UiDraw.border(g, rx, ry, rx + rw, ry + rh, 1, Colors.alpha(0.55f, underWay ? XP_DARK : LINE));
        if (underWay) UiDraw.glowBorder(g, rx, ry, rx + rw, ry + rh, XP, 0.35f + 0.15f * (float) Math.sin(time * 0.3f), 4);
        int level = u.level(t);
        int ink = Colors.alpha(has ? 1f : 0.5f, UiDraw.INK), muted = Colors.alpha(has ? 1f : 0.5f, UiDraw.INK_MUTED);
        UiDraw.text(g, t.displayName(), rx + 5, ry + 3, Colors.alpha(has ? 1f : 0.55f, COPPER_LIGHT), 0.7f, true);
        // where it stands, and its pips
        String now = t.describe(level), next = u.maxed(t) ? null : t.describe(level + 1);
        UiDraw.text(g, now, rx + 5, ry + 12, ink, 0.7f, false);
        if (next != null) {
            float w = minecraft.font.width(now) * 0.7f;
            UiDraw.text(g, " → " + next, rx + 5 + w, ry + 12, Colors.alpha(has ? 1f : 0.5f, XP), 0.7f, false);
        }
        int steps = t.max() - t.min() + 1, filled = level - t.min() + 1;
        for (int i = 0; i < steps; i++) {
            float px = rx + 8 + i * 6, py = ry + 22;
            UiDraw.diamond(g, px, py, 2.1f, Colors.alpha(has ? 1f : 0.4f, i < filled ? (u.maxed(t) ? UiDraw.GOLD_LIGHT : COPPER) : 0x3A2A22));
        }
        int bx = buttonX(), by = ry + (rh - BUTTON_H) / 2;
        if (underWay) {
            float p = menu.progress();
            UiDraw.bar(g, bx - 4, by + 3, BUTTON_W + 4, 7, p, XP_DARK, XP, 1f, time / 20f);
            if (p > 0 && p < 1) UiDraw.barTip(g, bx - 4, by + 3, BUTTON_W + 4, 7, bx - 4 + (BUTTON_W + 4) * p, XP, time);
            return;
        }
        if (!has) return;
        if (u.maxed(t)) {
            UiDraw.textCentered(g, "★ Highest", bx + BUTTON_W / 2f, by + 4, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 0.66f, true);
            return;
        }
        // its cost: cores of the tool's material and experience levels, green where there is enough
        LegendaryTool lt = (LegendaryTool) tool.getItem();
        ItemStack core = new ItemStack(ModItems.REFINED.get(lt.material()).get());
        int cores = t.cores(level), levels = t.levels(level);
        ItemStack cup = menu.catalyst();
        boolean enoughCores = cup.is(core.getItem()) && cup.getCount() >= cores;
        boolean enoughLevels = minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= levels;
        float cx = bx - 34;
        g.pose().pushPose();
        g.pose().translate(cx, ry + 3, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        g.renderFakeItem(core, 0, 0);
        g.pose().popPose();
        UiDraw.text(g, "×" + cores, cx + 11, ry + 5, Colors.alpha(1f, enoughCores ? UiDraw.GOOD : BAD), 0.62f, true);
        UiDraw.text(g, levels + " lv", cx + 1, ry + 15, Colors.alpha(1f, enoughLevels ? XP : BAD), 0.62f, true);
        boolean ready = !menu.working() && UpgradingTableBlockEntity.check(tool, cup, t, minecraft.player) == UpgradingTableBlockEntity.Check.READY;
        boolean hover = mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= by && mouseY < by + BUTTON_H;
        UiDraw.button(g, bx, by, BUTTON_W, BUTTON_H, hover && ready ? 1 : 0, ready ? 1f : 0.45f);
        UiDraw.textCentered(g, "Raise", bx + BUTTON_W / 2f, by + 4, Colors.alpha(ready ? 1f : 0.6f, ready ? UiDraw.GOLD_LIGHT : UiDraw.INK_MUTED), 0.7f, true);
    }

    /** A machine slot: a larger sunken frame with a copper glow; the vise is larger still. */
    private static void machineSlot(GuiGraphics g, int x, int y, boolean vise) {
        int m = vise ? 6 : 3;
        UiDraw.rect(g, x - m, y - m, x + 16 + m, y + 16 + m, Colors.alpha(0.8f, 0x0B0705));
        UiDraw.border(g, x - m - 1, y - m - 1, x + 17 + m, y + 17 + m, 1, Colors.alpha(0.9f, LINE));
        UiDraw.glowBorder(g, x - m - 1, y - m - 1, x + 17 + m, y + 17 + m, COPPER, vise ? 0.45f : 0.3f, 4);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        UiDraw.rect(g, x - 1, y - 1, x + 17, y + 17, Colors.alpha(0.75f, 0x0B0705));
        UiDraw.border(g, x - 2, y - 2, x + 18, y + 18, 1, Colors.alpha(0.85f, LINE));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String heading = title.getString();
        UiDraw.text(g, heading, (imageWidth - font.width(heading)) / 2f, 9, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 1f, true);
        int muted = Colors.alpha(1f, UiDraw.INK_MUTED);
        UiDraw.textCentered(g, "Vise", UpgradingMenu.TOOL_X + 8, UpgradingMenu.TOOL_Y - 17, muted, 0.65f, false);
        UiDraw.textCentered(g, "Cup", UpgradingMenu.CATALYST_X + 8, UpgradingMenu.CATALYST_Y - 13, muted, 0.65f, false);
        ItemStack tool = menu.tool();
        if (tool.getItem() instanceof LegendaryTool lt) {
            UiDraw.textCentered(g, lt.material().refinedName(), UpgradingMenu.CATALYST_X + 8, UpgradingMenu.CATALYST_Y + 22,
                    Colors.alpha(1f, lt.material().color()), 0.55f, true);
        }
        // what the table is doing, or what it needs
        float cx = UpgradingMenu.ROWS_X + UpgradingMenu.ROW_W / 2f;
        int ly = UpgradingMenu.ROWS_Y + 3 * UpgradingMenu.ROW_H + 2;
        UpgradeTrack under = menu.track();
        if (under != null) {
            float left = (1 - menu.progress()) * UpgradingTableBlockEntity.DURATION / 20f;
            UiDraw.textCentered(g, String.format("Raising %s   %.0fs", under.displayName(), Math.ceil(left)), cx, ly, Colors.alpha(1f, XP), 0.72f, true);
        } else if (!(tool.getItem() instanceof LegendaryTool)) {
            UiDraw.textCentered(g, "Lay a legendary tool in the vise", cx, ly, muted, 0.68f, false);
        } else {
            UpgradingTableBlockEntity.Check best = null;
            for (UpgradeTrack t : UpgradeTrack.values()) {
                var c = UpgradingTableBlockEntity.check(tool, menu.catalyst(), t, minecraft.player);
                if (c == UpgradingTableBlockEntity.Check.READY) {
                    best = c;
                    break;
                }
                if (c != UpgradingTableBlockEntity.Check.MAXED && best == null) best = c;
            }
            String line = best == null ? "Fully upgraded" : best == UpgradingTableBlockEntity.Check.READY ? "Choose what to raise" : best.message;
            UiDraw.textCentered(g, line, cx, ly, best == UpgradingTableBlockEntity.Check.READY || best == null ? Colors.alpha(1f, UiDraw.GOLD_LIGHT) : muted, 0.68f, false);
        }
        UiDraw.text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, muted, 0.75f, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ItemStack tool = menu.tool();
        if (button == 0 && !menu.working() && tool.getItem() instanceof LegendaryTool) {
            int bx = buttonX();
            for (UpgradeTrack t : UpgradeTrack.values()) {
                int by = rowY(t) + (UpgradingMenu.ROW_H - 4 - BUTTON_H) / 2;
                if (mouseX < bx || mouseX >= bx + BUTTON_W || mouseY < by || mouseY >= by + BUTTON_H) continue;
                if (UpgradingTableBlockEntity.check(tool, menu.catalyst(), t, minecraft.player) != UpgradingTableBlockEntity.Check.READY) return true;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, t.ordinal());
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.8f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // copper on dark metal, like the table itself
        UiDraw.style(UiDraw.NETHERITE, 0xE7955F, 0xF0A878, 0xFFD9BC, 0xBFA48E);
        try {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
        } finally {
            UiDraw.resetTheme();
        }
    }
}
