package com.fantasyweapons.client.gui;

import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.forge.ForgeRecipe;
import com.fantasyweapons.forge.ForgeStages;
import com.fantasyweapons.forge.LegendaryCraftingMenu;
import com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Legendary Crafting Table's screen: the 9x9 grid, the forged result, and beside them the table's recipes. Choosing one lays
 * its pattern out on the grid as ghost items (red where something else lies in the way) and lists what it takes.
 */
public class LegendaryCraftingScreen extends AbstractContainerScreen<LegendaryCraftingMenu> {
    /** The table's colours: gold on netherite. */
    private static final int ARCANE = 0xF0B848, GOLD = 0xFFD978, LINE = 0xB08A3E;
    /** The recipe browser: its icon grid (columns x rows) and where it sits. */
    private static final int COLS = 9, ROWS = 3, LIST_X = LegendaryCraftingMenu.INV_X, LIST_Y = 37;
    /** The Forge button (under the result) and the Fill button's width. */
    private static final int FORGE_W = 40, FORGE_H = 14, FORGE_X = LegendaryCraftingMenu.RESULT_X + 8 - FORGE_W / 2, FORGE_Y = LegendaryCraftingMenu.RESULT_Y + 28,
            FILL_W = 30;

    private final List<RecipeHolder<ForgeRecipe>> recipes = new ArrayList<>();
    private int page;
    private RecipeHolder<ForgeRecipe> selected;

    public LegendaryCraftingScreen(LegendaryCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = LegendaryCraftingMenu.WIDTH;
        imageHeight = LegendaryCraftingMenu.HEIGHT;
        inventoryLabelX = LegendaryCraftingMenu.INV_X;
        inventoryLabelY = LegendaryCraftingMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        recipes.clear();
        if (minecraft.level != null) recipes.addAll(minecraft.level.getRecipeManager().getAllRecipesFor(ModRecipes.FORGING.get()));
        // weapons first, then the armor set by set, as in the creative tab
        Map<Item, Integer> order = new HashMap<>();
        for (var def : Weapons.all()) order.put(ModItems.WEAPONS.get(def.id()).get(), order.size());
        for (ArmorSet set : ArmorSet.values()) for (var type : ArmorSet.PIECES) order.put(ModItems.ARMOR.get(set).get(type).get(), order.size());
        recipes.sort(Comparator.comparingInt(r -> order.getOrDefault(r.value().result().getItem(), Integer.MAX_VALUE)));
        page = Math.min(page, pages() - 1);
    }

    private int pages() {
        return Math.max(1, (recipes.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        int x = leftPos, y = topPos;
        UiDraw.panel(g, x, y, imageWidth, imageHeight, ARCANE, 1f);
        // the grid's anvil plate
        int gx = x + LegendaryCraftingMenu.GRID_X, gy = y + LegendaryCraftingMenu.GRID_Y;
        UiDraw.rect(g, gx - 5, gy - 5, gx + 9 * 18 + 3, gy + 9 * 18 + 3, Colors.alpha(0.45f, 0x000000));
        UiDraw.border(g, gx - 6, gy - 6, gx + 9 * 18 + 4, gy + 9 * 18 + 4, 1, Colors.alpha(0.8f, LINE));
        for (Slot s : menu.slots) {
            if (s.index == LegendaryCraftingMenu.RESULT || s.index == LegendaryCraftingMenu.OUTPUT) resultFrame(g, x + s.x, y + s.y, time);
            else slotFrame(g, x + s.x, y + s.y);
        }
        UiDraw.textCentered(g, "Forged", x + LegendaryCraftingMenu.OUTPUT_X + 8, y + LegendaryCraftingMenu.OUTPUT_Y - 15, Colors.alpha(1f, UiDraw.INK_MUTED), 0.65f, false);
        forgeButton(g, x, y, mouseX, mouseY);
        forgingStrip(g, x, y, time);
        // an inked arrow from the grid to the result
        int ay = y + LegendaryCraftingMenu.RESULT_Y + 8, ax = gx + 9 * 18 + 6;
        UiDraw.arrow(g, ax, ay - 5, x + LegendaryCraftingMenu.RESULT_X - 7 - ax, 10, Colors.alpha(1f, GOLD));
        renderBrowser(g, x, y, mouseX, mouseY, time);
    }

    /** The Forge button, under the result: lit when the grid holds a legendary pattern and the table is free. */
    private void forgeButton(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int bx = x + FORGE_X, by = y + FORGE_Y;
        boolean ready = canForge(), hover = ready && mouseX >= bx && mouseX < bx + FORGE_W && mouseY >= by && mouseY < by + FORGE_H;
        UiDraw.button(g, bx, by, FORGE_W, FORGE_H, hover ? 1 : 0, ready ? 1f : 0.45f);
        String label = menu.busy() ? "Forging" : "Forge";
        UiDraw.textCentered(g, label, bx + FORGE_W / 2f, by + 3.5f, Colors.alpha(ready ? 1f : 0.55f, hover ? 0xFFFFFF : GOLD), 0.8f, true);
    }

    private boolean canForge() {
        return menu.resultIsLegendary() && !menu.busy() && !menu.slots.get(LegendaryCraftingMenu.RESULT).getItem().isEmpty();
    }

    /** Under the grid: what the table is forging, the stage it is at, how far along, and how long is left. */
    private void forgingStrip(GuiGraphics g, int x, int y, float time) {
        int sx = x + LegendaryCraftingMenu.FORGING_X + 22, sy = y + LegendaryCraftingMenu.FORGING_Y, w = 9 * 18 - 26;
        if (!menu.busy()) {
            ItemStack next = menu.slots.get(LegendaryCraftingMenu.RESULT).getItem();
            String hint = menu.resultIsLegendary() && !next.isEmpty()
                    ? "Ready: press Forge (" + ForgeStages.remaining(0, forgeTime(next)) + ")"
                    : "Lay a legendary pattern on the grid, then Forge";
            UiDraw.text(g, hint, sx, sy + 4, Colors.alpha(1f, UiDraw.INK_MUTED), 0.62f, false);
            return;
        }
        ItemStack made = menu.slots.get(LegendaryCraftingMenu.FORGING).getItem();
        int progress = menu.progress(), total = Math.max(1, menu.total());
        ForgeRecipe recipe = forgingRecipe();
        boolean done = progress >= total;
        String line = done ? "Forged! Take it from the Forged slot" : ForgeStages.line(made, recipe, LegendaryCraftingTableBlockEntity.stage(progress, total,
                ForgeStages.count(made)));
        UiDraw.text(g, line, sx, sy - 1, Colors.alpha(1f, GOLD), 0.66f, true);
        // the bar, gilded, with a spark running along its edge
        int by = sy + 9;
        float f = Math.min(1f, progress / (float) total);
        UiDraw.rect(g, sx, by, sx + w, by + 5, Colors.alpha(0.7f, 0x0B0705));
        UiDraw.rect(g, sx, by, sx + (int) (w * f), by + 5, Colors.alpha(0.95f, 0xC8902E));
        UiDraw.rect(g, sx, by, sx + (int) (w * f), by + 2, Colors.alpha(0.6f, 0xFFE7A0));
        UiDraw.border(g, sx - 1, by - 1, sx + w + 1, by + 6, 1, Colors.alpha(0.9f, LINE));
        if (!done) UiDraw.glow(g, sx + w * f, by + 2.5f, 10 + 2 * (float) Math.sin(time * 0.6f), Colors.alpha(0.7f, GOLD));
        String left = done ? "" : ForgeStages.remaining(progress, total);
        UiDraw.text(g, left, sx + w - font.width(left) * 0.6f, sy - 1, Colors.alpha(1f, UiDraw.INK_MUTED), 0.6f, false);
    }

    private int forgeTime(ItemStack next) {
        for (RecipeHolder<ForgeRecipe> r : recipes) if (ItemStack.isSameItem(r.value().result(), next)) return r.value().time();
        return ForgeRecipe.DEFAULT_TIME;
    }

    private ForgeRecipe forgingRecipe() {
        int i = menu.forgingRecipe();
        var all = minecraft.level == null ? List.<RecipeHolder<ForgeRecipe>>of() : ForgeRecipe.ordered(minecraft.level);
        return i >= 0 && i < all.size() ? all.get(i).value() : null;
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        UiDraw.rect(g, x - 1, y - 1, x + 17, y + 17, Colors.alpha(0.75f, 0x0B0705));
        UiDraw.border(g, x - 2, y - 2, x + 18, y + 18, 1, Colors.alpha(0.85f, LINE));
    }

    private static void resultFrame(GuiGraphics g, int x, int y, float time) {
        UiDraw.glow(g, x + 8, y + 8, 46 + 4 * (float) Math.sin(time * 0.15f), Colors.alpha(0.35f, ARCANE));
        UiDraw.rect(g, x - 5, y - 5, x + 21, y + 21, Colors.alpha(0.8f, 0x0B0705));
        UiDraw.border(g, x - 6, y - 6, x + 22, y + 22, 1, Colors.alpha(0.9f, LINE));
        UiDraw.glowBorder(g, x - 6, y - 6, x + 22, y + 22, ARCANE, 0.45f, 4);
    }

    // ------------------------------------------------------------------------------------------------------------
    // recipes
    // ------------------------------------------------------------------------------------------------------------

    private void renderBrowser(GuiGraphics g, int x, int y, int mouseX, int mouseY, float time) {
        int lx = x + LIST_X, ly = y + LIST_Y;
        UiDraw.text(g, "Legendary Recipes", lx, y + 25, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 0.8f, true);
        String pageText = (page + 1) + " / " + pages();
        UiDraw.textCentered(g, pageText, lx + 162 - 22, y + 26, Colors.alpha(1f, UiDraw.INK_MUTED), 0.7f, false);
        arrowButton(g, lx + 162 - 44, y + 23, false, mouseX, mouseY);
        arrowButton(g, lx + 162 - 10, y + 23, true, mouseX, mouseY);
        int first = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS; i++) {
            int ix = lx + (i % COLS) * 18, iy = ly + (i / COLS) * 18;
            UiDraw.rect(g, ix, iy, ix + 17, iy + 17, Colors.alpha(0.5f, 0x0B0705));
            if (first + i >= recipes.size()) continue;
            RecipeHolder<ForgeRecipe> r = recipes.get(first + i);
            if (r == selected) {
                UiDraw.glow(g, ix + 8, iy + 8, 26, Colors.alpha(0.6f, GOLD));
                UiDraw.border(g, ix - 1, iy - 1, ix + 18, iy + 18, 1, Colors.alpha(1f, GOLD));
            }
            g.renderFakeItem(r.value().result(), ix, iy);
        }
        // what the chosen recipe takes
        int sy = ly + ROWS * 18 + 2;
        if (selected == null) {
            UiDraw.wrap(g, "Choose a recipe to lay out its pattern on the grid.", lx, sy + 2, 162, Colors.alpha(1f, UiDraw.INK_MUTED), 0.65f, 8);
            return;
        }
        Map<Item, Integer> counts = new LinkedHashMap<>();
        Map<Item, ItemStack> shown = new LinkedHashMap<>();
        for (Ingredient ing : selected.value().pattern().ingredients()) {
            ItemStack s = cycled(ing, time);
            if (s.isEmpty()) continue;
            counts.merge(s.getItem(), 1, Integer::sum);
            shown.putIfAbsent(s.getItem(), s);
        }
        fillButton(g, lx + 162 - FILL_W, sy - 1, mouseX, mouseY);
        int cx = lx;
        for (var e : shown.entrySet()) {
            if (cx > lx + 162 - FILL_W - 22) break;
            g.renderFakeItem(e.getValue(), cx, sy);
            String n = "x" + counts.get(e.getKey());
            UiDraw.text(g, n, cx + 17, sy + 6, Colors.alpha(1f, UiDraw.INK), 0.65f, true);
            cx += 19 + (int) (font.width(n) * 0.65f) + 3;
        }
    }

    /** Lays the chosen recipe out on the grid from the inventory; lit only when everything it takes is to hand. */
    private void fillButton(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        boolean can = canFill(), hover = can && mouseX >= x && mouseX < x + FILL_W && mouseY >= y && mouseY < y + 14;
        UiDraw.button(g, x, y, FILL_W, 14, hover ? 1 : 0, can ? 1f : 0.45f);
        UiDraw.textCentered(g, "Fill", x + FILL_W / 2f, y + 3.5f, Colors.alpha(can ? 1f : 0.55f, hover ? 0xFFFFFF : GOLD), 0.8f, true);
    }

    private boolean canFill() {
        return selected != null && minecraft.player != null && LegendaryCraftingMenu.canFill(minecraft.player, menu.grid(), selected.value());
    }

    private static void arrowButton(GuiGraphics g, int x, int y, boolean right, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10;
        UiDraw.button(g, x, y, 10, 10, hover ? 1 : 0, 1f);
        int c = Colors.alpha(1f, hover ? 0xFFFFFF : UiDraw.GOLD_LIGHT);
        float mx = x + 5, my = y + 5, d = right ? 1.5f : -1.5f;
        UiDraw.line(g, mx - d, my - 2.5f, mx + d, my, 1.2f, c, c);
        UiDraw.line(g, mx - d, my + 2.5f, mx + d, my, 1.2f, c, c);
    }

    private void pageSound() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                com.fantasyweapons.sound.ModSounds.TABLE_PAGE.get(), 0.95f + (float) Math.random() * 0.1f, 0.7f));
    }

    /** One of an ingredient's items, turning over every second when it accepts several. */
    private static ItemStack cycled(Ingredient ing, float time) {
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        return items[(int) (time / 20) % items.length];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (mouseX >= lx && mouseX < lx + COLS * 18 && mouseY >= ly && mouseY < ly + ROWS * 18) {
            int i = page * COLS * ROWS + (int) ((mouseY - ly) / 18) * COLS + (int) ((mouseX - lx) / 18);
            if (i < recipes.size()) {
                RecipeHolder<ForgeRecipe> r = recipes.get(i);
                selected = selected == r ? null : r;
                return true;
            }
        }
        int ay = topPos + 23;
        if (mouseY >= ay && mouseY < ay + 10) {
            if (mouseX >= lx + 162 - 44 && mouseX < lx + 162 - 34) {
                page = (page + pages() - 1) % pages();
                pageSound();
                return true;
            }
            if (mouseX >= lx + 162 - 10 && mouseX < lx + 162) {
                page = (page + 1) % pages();
                pageSound();
                return true;
            }
        }
        int bx = leftPos + FORGE_X, by = topPos + FORGE_Y;
        if (canForge() && mouseX >= bx && mouseX < bx + FORGE_W && mouseY >= by && mouseY < by + FORGE_H) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LegendaryCraftingMenu.FORGE_BUTTON);
            return true;
        }
        int fx = lx + 162 - FILL_W, fy = ly + ROWS * 18 + 1;
        if (canFill() && mouseX >= fx && mouseX < fx + FILL_W && mouseY >= fy && mouseY < fy + 14) {
            var all = ForgeRecipe.ordered(minecraft.level);
            int i = all.indexOf(selected);
            if (i >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LegendaryCraftingMenu.FILL + i);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.2f, 0.6f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String t = title.getString();
        UiDraw.text(g, t, LegendaryCraftingMenu.GRID_X + (9 * 18 - font.width(t)) / 2f, 9, Colors.alpha(1f, UiDraw.GOLD_LIGHT), 1f, true);
        UiDraw.text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Colors.alpha(1f, UiDraw.INK_MUTED), 0.75f, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // gold and netherite, like the table itself
        UiDraw.style(UiDraw.NETHERITE, -1, 0xF2C46B, 0xFFE7A8, 0xB9AC92);
        try {
            renderThemed(g, mouseX, mouseY, partialTick);
        } finally {
            UiDraw.resetTheme();
        }
    }

    private void renderThemed(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderGhost(g, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // the recipe icons' and ingredients' tooltips
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (mouseX >= lx && mouseX < lx + COLS * 18 && mouseY >= ly && mouseY < ly + ROWS * 18) {
            int i = page * COLS * ROWS + (mouseY - ly) / 18 * COLS + (mouseX - lx) / 18;
            if (i < recipes.size()) g.renderTooltip(font, recipes.get(i).value().result(), mouseX, mouseY);
        }
    }

    /** The chosen recipe's pattern on the grid: ghost items in the empty slots, red where something else lies. */
    private void renderGhost(GuiGraphics g, float partialTick) {
        if (selected == null) return;
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        var pattern = selected.value().pattern();
        int w = pattern.width(), h = pattern.height();
        int left = (ForgeRecipe.SIZE - w) / 2, top = (ForgeRecipe.SIZE - h) / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                Ingredient ing = pattern.ingredients().get(c + r * w);
                Slot slot = menu.slots.get(LegendaryCraftingMenu.GRID_START + (c + left) + (r + top) * ForgeRecipe.SIZE);
                int sx = leftPos + slot.x, sy = topPos + slot.y;
                ItemStack have = slot.getItem();
                if (ing.isEmpty()) {
                    if (!have.isEmpty()) g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x60FF2020);
                    continue;
                }
                if (have.isEmpty()) {
                    g.renderFakeItem(cycled(ing, time), sx, sy);
                    g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x90241A14);
                } else if (!ing.test(have)) {
                    g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x60FF2020);
                }
            }
        }
        g.pose().popPose();
    }
}
