package com.fantasyweapons.client.gui;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.refinery.RefineryMenu;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.fantasyweapons.refinery.ArcaneRefineryBlockEntity;
import com.fantasyweapons.refinery.RefiningRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * The Arcane Refinery's screen, in the mod's own style: a leather panel in a bronze frame with the crucible (raw
 * material) above the firebox (fuel), a molten channel filling towards the tray (refined material) under a large view of
 * what is being made, what the machine needs next, and the player's inventory below. Beside it, a guide to refining: what
 * goes in, which fuels are fast, how hoppers feed it and what the refined materials are for.
 */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    /** The refinery's colours: diamond and crystal on slate (its fire stays fire). */
    private static final int ARCANE = 0x5FD8E8, FIRE = 0xFF6A1A, EMBER = 0xFFD27A, BLAZE = 0xFFE36A, LINE = 0x6F8E96, CRYSTAL = 0xD8FAFF;
    private static final int GUIDE_W = 152, GAP = 6;

    /** Item icons drawn in the guide this frame, for their tooltips. */
    private final List<Icon> icons = new ArrayList<>();
    private boolean guide;

    private record Icon(ItemStack stack, int x, int y) {
    }

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RefineryMenu.WIDTH;
        imageHeight = RefineryMenu.HEIGHT;
        inventoryLabelX = RefineryMenu.INV_X;
        inventoryLabelY = RefineryMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        // the guide sits to the left when there is room; the two are centred together
        guide = width >= imageWidth + GUIDE_W + GAP + 8;
        if (guide) leftPos += (GUIDE_W + GAP) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        int x = leftPos, y = topPos;
        UiDraw.panel(g, x, y, imageWidth, imageHeight, ARCANE, 1f);
        // the machine's bay
        UiDraw.rect(g, x + 12, y + 24, x + imageWidth - 12, y + 126, Colors.alpha(0.35f, 0x000000));
        UiDraw.border(g, x + 12, y + 24, x + imageWidth - 12, y + 126, 1, Colors.alpha(0.5f, LINE));
        UiDraw.divider(g, x + imageWidth / 2f, y + RefineryMenu.INV_Y - 18, imageWidth - 30, 0.8f);
        boolean working = menu.burning() && menu.progress() > 0;
        for (Slot s : menu.slots) {
            if (s.index < RefineryMenu.MACHINE) {
                boolean tray = s.index == 2;
                UiDraw.sigil(g, x + s.x + 8, y + s.y + 8, tray ? 18 : 13, ARCANE, time, working || (tray && s.hasItem()) ? 1f : s.hasItem() ? 0.6f : 0.35f);
                machineSlot(g, x + s.x, y + s.y, tray);
            } else {
                slotFrame(g, x + s.x, y + s.y);
            }
        }

        // the fire: a column of heat between the crucible and the firebox, gold on a fast fire
        float burn = menu.burnFraction();
        int fireX = x + RefineryMenu.FUEL_X + 3, fireTop = y + RefineryMenu.INPUT_Y + 22, fireBottom = y + RefineryMenu.FUEL_Y - 5;
        UiDraw.rect(g, fireX, fireTop, fireX + 10, fireBottom, Colors.alpha(0.85f, 0x0E0804));
        UiDraw.border(g, fireX - 1, fireTop - 1, fireX + 11, fireBottom + 1, 1, Colors.alpha(0.7f, LINE));
        if (burn > 0) {
            float h = (fireBottom - fireTop) * burn;
            int top = menu.fastFire() ? BLAZE : EMBER;
            UiDraw.vGradient(g, fireX + 1, fireBottom - h, fireX + 9, fireBottom, Colors.alpha(1f, top), Colors.alpha(1f, FIRE));
            UiDraw.glow(g, fireX + 5, fireBottom - h, 18 + 4 * (float) Math.sin(time * 0.4f), Colors.alpha(0.5f, top));
        }

        // the molten channel from the crucible to the tray, and what it is making, large, above it
        float p = menu.progress();
        float bx = x + RefineryMenu.INPUT_X + 28, by = y + RefineryMenu.OUTPUT_Y + 5, bw = RefineryMenu.OUTPUT_X - RefineryMenu.INPUT_X - 40;
        UiDraw.bar(g, bx, by, bw, 7, p, 0x3FA8C0, CRYSTAL, 1f, p > 0 ? time / 20f : -1);
        if (p > 0 && p < 1) UiDraw.barTip(g, bx, by, bw, 7, bx + bw * p, CRYSTAL, time);
        ItemStack making = result();
        if (!making.isEmpty()) {
            float cx = bx + bw * 0.76f, cy = y + 36;
            boolean active = menu.burning() && p > 0;
            UiDraw.glow(g, cx, cy, 44 + (active ? 6 * (float) Math.sin(time * 0.25f) : 0), Colors.alpha(active ? 0.55f : 0.25f, ARCANE));
            g.pose().pushPose();
            g.pose().translate(cx - 16, cy - 16, 0);
            g.pose().scale(2, 2, 1);
            g.renderFakeItem(making, 0, 0);
            g.pose().popPose();
        }

        if (guide) renderGuide(g, x - GUIDE_W - GAP, y, time);
    }

    /** What the crucible's contents will become (empty if nothing), however much of it is there yet. */
    private ItemStack result() {
        RefiningRecipe recipe = recipe();
        return recipe == null ? ItemStack.EMPTY : recipe.result();
    }

    /** The recipe for the raw material in the crucible, if it is one. */
    private RefiningRecipe recipe() {
        ItemStack input = menu.slots.get(0).getItem();
        if (input.isEmpty() || minecraft.level == null) return null;
        for (var r : minecraft.level.getRecipeManager().getAllRecipesFor(ModRecipes.REFINING.get())) if (r.value().ingredient().test(input)) return r.value();
        return null;
    }

    /** A machine slot: a larger sunken frame with an arcane glow; the tray is larger still. */
    private static void machineSlot(GuiGraphics g, int x, int y, boolean tray) {
        int m = tray ? 5 : 3;
        UiDraw.rect(g, x - m, y - m, x + 16 + m, y + 16 + m, Colors.alpha(0.8f, 0x0B0705));
        UiDraw.border(g, x - m - 1, y - m - 1, x + 17 + m, y + 17 + m, 1, Colors.alpha(0.9f, LINE));
        UiDraw.glowBorder(g, x - m - 1, y - m - 1, x + 17 + m, y + 17 + m, ARCANE, tray ? 0.45f : 0.3f, 4);
    }

    /** A sunken slot: dark groove with a fine bronze edge. */
    private static void slotFrame(GuiGraphics g, int x, int y) {
        UiDraw.rect(g, x - 1, y - 1, x + 17, y + 17, Colors.alpha(0.75f, 0x0B0705));
        UiDraw.border(g, x - 2, y - 2, x + 18, y + 18, 1, Colors.alpha(0.85f, LINE));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the guide
    // ------------------------------------------------------------------------------------------------------------

    private void renderGuide(GuiGraphics g, int x, int y, float time) {
        icons.clear();
        UiDraw.panel(g, x, y, GUIDE_W, imageHeight, ARCANE, 1f);
        UiDraw.title(g, "Refining Guide", x + GUIDE_W / 2f, y + 8, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 0.9f);
        int ink = Colors.alpha(1f, UiDraw.INK), muted = Colors.alpha(1f, UiDraw.INK_MUTED), gold = Colors.alpha(1f, BLAZE);
        int tx = x + 10, w = GUIDE_W - 20;
        // each legendary material in turn
        Material m = Material.values()[(int) (time / 40) % Material.values().length];
        ItemStack raw = new ItemStack(ModItems.RAW.get(m).get()), refined = new ItemStack(ModItems.REFINED.get(m).get());

        int yy = y + 24;
        heading(g, "1  Refine", tx, yy);
        yy += 11;
        ItemStack additive = new ItemStack(m.additive());
        icon(g, raw.copyWithCount(Material.REFINE_COUNT), tx + 2, yy);
        UiDraw.text(g, "+", tx + 21, yy + 4, muted, 0.8f, false);
        icon(g, additive.copyWithCount(Material.REFINE_COUNT), tx + 28, yy);
        UiDraw.text(g, "+", tx + 47, yy + 4, muted, 0.8f, false);
        icon(g, new ItemStack(Items.COAL), tx + 54, yy);
        arrow(g, tx + 74, yy + 8);
        icon(g, refined, tx + 96, yy);
        yy += 19;
        UiDraw.wrap(g, Tr.f("%s ×%s and %s ×%s melt into one %s in 20 seconds.", m.rawName(), Material.REFINE_COUNT,
                additive.getHoverName().getString(), Material.REFINE_COUNT, m.refinedName()), tx, yy, w, ink, 0.6f, 7);
        yy += 18;

        heading(g, "2  Fuel", tx, yy);
        yy += 11;
        icon(g, new ItemStack(Items.COAL), tx + 2, yy);
        icon(g, new ItemStack(Items.CHARCOAL), tx + 20, yy);
        icon(g, new ItemStack(Items.OAK_LOG), tx + 38, yy);
        UiDraw.wrap(g, "Any furnace fuel", tx + 60, yy + 1, w - 60, ink, 0.6f, 7);
        UiDraw.text(g, "20 s", tx + 60, yy + 9, muted, 0.6f, false);
        yy += 19;
        icon(g, new ItemStack(Items.BLAZE_ROD), tx + 2, yy);
        icon(g, new ItemStack(Items.LAVA_BUCKET), tx + 20, yy);
        UiDraw.wrap(g, "Twice as fast", tx + 42, yy + 1, w - 42, gold, 0.6f, 7);
        UiDraw.text(g, "10 s", tx + 42, yy + 9, gold, 0.6f, false);
        yy += 22;

        heading(g, "3  Hoppers", tx, yy);
        yy += 11;
        icon(g, new ItemStack(Items.HOPPER), tx + 2, yy + 2);
        UiDraw.wrap(g, "Top: raw material. Sides: fuel and additive. Bottom: takes the refined out.", tx + 24, yy, w - 24, ink, 0.6f, 7);
        yy += 26;

        heading(g, "4  Then", tx, yy);
        yy += 11;
        icon(g, refined, tx + 2, yy);
        arrow(g, tx + 22, yy + 8);
        icon(g, new ItemStack(ModItems.LEGENDARY_CRAFTING_TABLE.get()), tx + 42, yy);
        UiDraw.wrap(g, "Its armor and weapon, at the Legendary Crafting Table", tx + 62, yy - 2, w - 62, ink, 0.6f, 7);
        yy += 22;
        ItemStack weapon = new ItemStack(ModItems.WEAPONS.get(m.weapon()).get());
        icon(g, weapon, tx + 2, yy);
        UiDraw.text(g, "+", tx + 22, yy + 4, muted, 0.8f, false);
        icon(g, refined, tx + 30, yy);
        UiDraw.wrap(g, "+1 weapon level per core", tx + 52, yy + 1, w - 52, gold, 0.6f, 7);
    }

    private boolean enough(RefiningRecipe r) {
        ItemStack input = menu.slots.get(0).getItem(), add = menu.slots.get(ArcaneRefineryBlockEntity.ADDITIVE).getItem();
        return input.getCount() >= r.count() && (r.additive().isEmpty() || r.additive().test(add) && add.getCount() >= r.additiveCount());
    }

    /** Text ending at {@code right}, made smaller when it is wider than {@code room}. */
    private static void rightFit(GuiGraphics g, String text, float right, float y, int argb, float scale, float room) {
        float s = Math.min(scale, room / Math.max(1f, UiDraw.width(text)));
        UiDraw.textRight(g, text, right, y + (scale - s) * 4, argb, s, false);
    }

    /** Centred text, made smaller when it is wider than {@code room}. */
    private static void centeredFit(GuiGraphics g, String text, float cx, float y, int argb, float scale, boolean shadow, float room) {
        float s = Math.min(scale, room / Math.max(1f, UiDraw.width(text)));
        UiDraw.textCentered(g, text, cx, y + (scale - s) * 4, argb, s, shadow);
    }

    private static void heading(GuiGraphics g, String s, int x, int y) {
        UiDraw.text(g, s, x, y, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 0.7f, true);
    }

    private static void arrow(GuiGraphics g, int x, int cy) {
        UiDraw.arrow(g, x - 1, cy - 4, 17, 7, Colors.alpha(1f, CRYSTAL));
    }

    private void icon(GuiGraphics g, ItemStack stack, int x, int y) {
        UiDraw.rect(g, x - 1, y - 1, x + 17, y + 17, Colors.alpha(0.5f, 0x0B0705));
        g.renderFakeItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        icons.add(new Icon(stack, x, y));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String title = this.title.getString();
        UiDraw.text(g, title, (imageWidth - font.width(title)) / 2f, 9, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 1f, true);
        int muted = Colors.alpha(1f, UiDraw.INK_MUTED);
        rightFit(g, "Raw", RefineryMenu.INPUT_X - 4, RefineryMenu.INPUT_Y + 5, muted, 0.75f, RefineryMenu.INPUT_X - 10);
        UiDraw.textCentered(g, "+", (RefineryMenu.INPUT_X + RefineryMenu.ADDITIVE_X + 16) / 2f, RefineryMenu.INPUT_Y + 4, muted, 0.9f, false);
        UiDraw.textCentered(g, "Additive", RefineryMenu.ADDITIVE_X + 8, RefineryMenu.ADDITIVE_Y - 11, muted, 0.6f, false);
        rightFit(g, "Fuel", RefineryMenu.FUEL_X - 4, RefineryMenu.FUEL_Y + 5, muted, 0.75f, RefineryMenu.FUEL_X - 10);
        UiDraw.textCentered(g, "Refined", RefineryMenu.OUTPUT_X + 8, RefineryMenu.OUTPUT_Y + 26, muted, 0.75f, false);
        // what the machine is doing, or what it needs
        float cx = (RefineryMenu.INPUT_X + 28 + RefineryMenu.OUTPUT_X - 12) / 2f;
        int ly = RefineryMenu.OUTPUT_Y + 16;
        // the lines stay clear of the Refined slot and its label, shrinking when a language's words run long
        float refinedHalf = UiDraw.width("Refined") * 0.75f / 2;
        float room = 2 * (Math.min(RefineryMenu.OUTPUT_X - 4, RefineryMenu.OUTPUT_X + 8 - refinedHalf - 3) - cx);
        ItemStack input = menu.slots.get(0).getItem(), fuel = menu.slots.get(1).getItem(), out = menu.slots.get(2).getItem();
        if (menu.progress() > 0 && menu.burning()) {
            centeredFit(g, Tr.f("Refining  %ss", String.format("%.0f", Math.ceil(menu.secondsLeft()))), cx, ly, Colors.alpha(1f, UiDraw.INK), 0.75f, false, room);
            if (menu.fastFire()) centeredFit(g, "Fast fire  x2", cx, ly + 10, Colors.alpha(1f, BLAZE), 0.7f, true, room);
        } else if (input.isEmpty()) {
            centeredFit(g, "Put a raw material in", cx, ly, muted, 0.7f, false, room);
        } else if (recipe() != null && !enough(recipe())) {
            // what it still needs: 8 of the raw material and 8 of its additive, each green once there is enough
            RefiningRecipe r = recipe();
            ItemStack add = menu.slots.get(ArcaneRefineryBlockEntity.ADDITIVE).getItem();
            int haveRaw = input.getCount(), haveAdd = r.additive().test(add) ? add.getCount() : 0;
            String addName = r.additive().getItems().length > 0 ? r.additive().getItems()[0].getHoverName().getString() : Tr.s("Additive");
            centeredFit(g, Tr.s("Raw") + "  " + Math.min(haveRaw, r.count()) + "/" + r.count(), cx, ly, Colors.alpha(1f, haveRaw >= r.count() ? UiDraw.GOOD : BLAZE), 0.62f, true, room);
            centeredFit(g, addName + "  " + Math.min(haveAdd, r.additiveCount()) + "/" + r.additiveCount(), cx, ly + 9,
                    Colors.alpha(1f, haveAdd >= r.additiveCount() ? UiDraw.GOOD : BLAZE), 0.62f, true, room);
        } else if (!out.isEmpty() && out.getCount() >= out.getMaxStackSize()) {
            centeredFit(g, "Take the refined out", cx, ly, muted, 0.7f, false, room);
        } else if (!menu.burning() && (fuel.isEmpty() || fuel.is(Items.BUCKET))) {
            centeredFit(g, "Add fuel to light the fire", cx, ly, muted, 0.7f, false, room);
        }
        UiDraw.text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, muted, 0.75f, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // stone and diamond, like the refinery itself
        UiDraw.style(UiDraw.SLATE, 0xBDEFF5, 0x8FE6F0, 0xE2FBFF, 0xA3B3B8);
        try {
            renderThemed(g, mouseX, mouseY, partialTick);
        } finally {
            UiDraw.resetTheme();
        }
    }

    private void renderThemed(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        for (Icon i : icons) {
            if (mouseX >= i.x() && mouseX < i.x() + 16 && mouseY >= i.y() && mouseY < i.y() + 16) {
                g.renderTooltip(font, i.stack(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // clicks on the guide are not "outside" (they would throw the carried stack away)
        if (guide && mouseX >= left - GUIDE_W - GAP && mouseX < left && mouseY >= top && mouseY < top + imageHeight) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
}
