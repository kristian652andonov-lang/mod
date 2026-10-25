package com.fantasyweapons.client.gui;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.forge.WorldforgeBlockEntity;
import com.fantasyweapons.forge.WorldforgeMenu;
import com.fantasyweapons.forge.WorldforgeRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The Worldforge's screen: obsidian veined with blood-red light, iron and skulls; a horned skull over the 11x11 grid whose
 * eye follows the cursor and blinks; the grid's rim beating like a heart (faster while it forges); embers rising; a
 * summoning circle under the forging that wakes and turns while the forge works; and the forge muttering to itself
 * under the grid. Choosing one of its rites lays the pattern out on the grid as ghosts (red where something else lies);
 * Fill lays it from the inventory; Forge feeds it everything on the grid.
 */
public class WorldforgeScreen extends AbstractContainerScreen<WorldforgeMenu> {
    private static final ResourceLocation ART = FantasyWeapons.id("textures/gui/worldforge.png");
    /** The panel's veins of blood, on their own so they can beat with the heart. */
    private static final ResourceLocation VEINS = FantasyWeapons.id("textures/gui/worldforge_veins.png");
    private static final int VEINS_W = 872, VEINS_H = 536;
    /** Where blood wells up along the top of the frame: x (gui px), its period (ticks), how far it falls (gui px). */
    private static final float[][] DRIPS = {
            {34, 150, 40}, {58, 210, 22}, {79, 120, 60}, {146, 170, 30}, {171, 240, 18}, {198, 135, 52}, {226, 190, 26},
            {254, 160, 160}, {281, 220, 34}, {309, 140, 24}, {338, 200, 46}, {366, 175, 20}, {393, 130, 38}, {417, 230, 28},
            {8, 260, 250}, {428, 280, 250}};
    private static final int BLOOD = 0xFF3A30, DEEP = 0x9A1418, BONE = 0xE8D8C8, ASH = 0x9C7C74, EMBER = 0xFF8A3A;
    private static final int RITE_X = WorldforgeMenu.RIGHT_X + 10, RITE_Y = 50, FILL_W = 34, FORGE_W = 54, FORGE_H = 15;
    private static final int FORGE_X = WorldforgeMenu.FORGING_X + 8 - FORGE_W / 2, FORGE_Y = 132;
    private static final String[] WHISPERS = {
            "It remembers every legend it has eaten.",
            "Twenty-five lives. One weapon.",
            "Do not look at the heart for long.",
            "The anvil is still warm from the last world.",
            "Something on the other side of the grid is breathing.",
            "It is listening to you choose.",
            "Feed it, and it will give you the end of everything.",
            "No one has ever asked it to stop."};

    private List<RecipeHolder<WorldforgeRecipe>> recipes = List.of();
    private RecipeHolder<WorldforgeRecipe> selected;
    private int chosenSeen = -2;
    /** When the screen opened (game time), for its fade in out of the dark. */
    private float openedAt = -1;

    public WorldforgeScreen(WorldforgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WorldforgeMenu.WIDTH;
        imageHeight = WorldforgeMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft.level != null) recipes = WorldforgeRecipe.ordered(minecraft.level);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int chosen = menu.chosenRecipe();
        if (chosen == chosenSeen) return;
        chosenSeen = chosen;
        selected = chosen >= 0 && chosen < recipes.size() ? recipes.get(chosen) : null;
    }

    private float time(float partial) {
        return (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partial;
    }

    /** A heartbeat: lub-dub, then rest; faster while the forge works. */
    private float heartbeat(float t) {
        float period = menu.busy() ? 16 : 26;
        float p = (t % period) / period;
        return (float) (Math.exp(-Math.pow((p - 0.08) / 0.045, 2)) + 0.6 * Math.exp(-Math.pow((p - 0.24) / 0.045, 2)));
    }

    // ------------------------------------------------------------------------------------------------------------
    // the world behind, and the panel
    // ------------------------------------------------------------------------------------------------------------

    /** The world behind sinks into a red dark. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = time(partialTick);
        float beat = heartbeat(t);
        UiDraw.vGradient(g, 0, 0, width, height, Colors.alpha(0.82f, 0x0A0204), Colors.alpha(0.92f, 0x1A0306));
        // a vignette closing in at the edges, pulsing with the heartbeat
        int edge = (int) (width * 0.18f);
        UiDraw.hGradient(g, 0, 0, edge, height, Colors.alpha(0.55f + 0.15f * beat, 0x3A0006), 0);
        UiDraw.hGradient(g, width - edge, 0, width, height, 0, Colors.alpha(0.55f + 0.15f * beat, 0x3A0006));
        // then the panel (the container screen's own background step, which this replaces)
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float t = time(partialTick);
        int x = leftPos, y = topPos;
        g.blit(ART, x, y, imageWidth, imageHeight, 0, 0, imageWidth * 2, imageHeight * 2, 1024, 1024);
        float beat = heartbeat(t);
        mist(g, x, y, t);
        // the veins swell with each beat, and a slow tide of light moves through them
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        float tide = 0.5f + 0.5f * (float) Math.sin(t * 0.045f);
        g.setColor(0.85f, 0.75f, 0.75f, Mth.clamp(0.16f + 0.42f * beat + 0.12f * tide, 0, 1));
        g.blit(VEINS, x, y, imageWidth, imageHeight, 0, 0, VEINS_W, VEINS_H, VEINS_W, VEINS_H);
        g.setColor(1f, 1f, 1f, 1f);
        drips(g, x, y, t);
        int gx = x + WorldforgeMenu.GRID_X, gy = y + WorldforgeMenu.GRID_Y, gs = WorldforgeMenu.SIZE * 18;
        // the grid's rim beats
        UiDraw.glowBorder(g, gx - 7, gy - 7, gx + gs + 5, gy + gs + 5, BLOOD, 0.25f + 0.55f * beat, 6);
        // the grid's slots, cut into the plate
        for (Slot s : menu.slots) {
            if (s.index >= WorldforgeMenu.GRID_START && s.index < WorldforgeMenu.GRID_END) {
                int sx = x + s.x, sy = y + s.y;
                UiDraw.rect(g, sx - 1, sy - 1, sx + 17, sy + 17, Colors.alpha(1f, 0x120A0B));
                UiDraw.border(g, sx - 1, sy - 1, sx + 17, sy + 17, 1, Colors.alpha(0.55f + 0.25f * beat * (s.hasItem() ? 1 : 0.3f), DEEP));
            } else if (s.index >= WorldforgeMenu.INV_START) {
                int sx = x + s.x, sy = y + s.y;
                UiDraw.rect(g, sx - 1, sy - 1, sx + 17, sy + 17, Colors.alpha(1f, 0x0E0809));
                UiDraw.border(g, sx - 1, sy - 1, sx + 17, sy + 17, 1, Colors.alpha(0.6f, 0x5A1A1C));
            }
        }
        circle(g, x, y, t);
        for (int i : new int[]{WorldforgeMenu.RESULT, WorldforgeMenu.FORGING, WorldforgeMenu.OUTPUT}) {
            Slot s = menu.slots.get(i);
            boolean lit = i == WorldforgeMenu.RESULT ? menu.ready() : i == WorldforgeMenu.FORGING ? menu.busy() : s.hasItem();
            altarSlot(g, x + s.x, y + s.y, t, lit);
        }
        UiDraw.textCentered(g, "Rite", x + WorldforgeMenu.RESULT_X + 8, y + WorldforgeMenu.RESULT_Y + 21, Colors.alpha(1f, ASH), 0.6f, false);
        UiDraw.textCentered(g, "Forging", x + WorldforgeMenu.FORGING_X + 8, y + WorldforgeMenu.FORGING_Y - 13, Colors.alpha(1f, ASH), 0.6f, false);
        UiDraw.textCentered(g, "Forged", x + WorldforgeMenu.OUTPUT_X + 8, y + WorldforgeMenu.OUTPUT_Y + 21, Colors.alpha(1f, ASH), 0.6f, false);
        forgeButton(g, x, y, mouseX, mouseY, t);
        forgingStrip(g, x, y, t);
        rites(g, x, y, mouseX, mouseY, t);
        eye(g, x, y, mouseX, mouseY, t);
        embers(g, x, y, t);
        whisper(g, x, y, t);
    }

    /** The summoning circle under the forging: dim and still, then awake and turning while the forge works. */
    private void circle(GuiGraphics g, int x, int y, float t) {
        float cx = x + WorldforgeMenu.FORGING_X + 8, cy = y + WorldforgeMenu.FORGING_Y + 8;
        float wake = menu.busy() ? 1 : menu.ready() ? 0.55f : 0.15f;
        UiDraw.glow(g, cx, cy, 120, Colors.alpha(0.18f + 0.3f * wake * heartbeat(t), BLOOD));
        // runes walking round it
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + t * 0.02 * wake;
            float rx = cx + (float) Math.cos(a) * 46, ry = cy + (float) Math.sin(a) * 46;
            UiDraw.diamond(g, rx, ry, 1.6f, Colors.alpha(0.3f + 0.7f * wake, i % 3 == 0 ? EMBER : BLOOD));
        }
        if (menu.busy()) UiDraw.sigil(g, cx, cy, 30, BLOOD, t, 0.8f);
    }

    private void altarSlot(GuiGraphics g, int sx, int sy, float t, boolean lit) {
        if (lit) UiDraw.glow(g, sx + 8, sy + 8, 40 + 4 * (float) Math.sin(t * 0.2f), Colors.alpha(0.45f, BLOOD));
        UiDraw.rect(g, sx - 3, sy - 3, sx + 19, sy + 19, Colors.alpha(1f, 0x0A0506));
        UiDraw.border(g, sx - 4, sy - 4, sx + 20, sy + 20, 1, Colors.alpha(1f, lit ? BLOOD : 0x5A1A1C));
        if (lit) UiDraw.glowBorder(g, sx - 4, sy - 4, sx + 20, sy + 20, BLOOD, 0.5f, 3);
    }

    /** The skull over the grid: its eyes follow the cursor, glow with the heartbeat and now and then close. */
    private void eye(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        float cx = x + WorldforgeMenu.GRID_X + WorldforgeMenu.SIZE * 9, cy = y + 21.44f;
        boolean blink = (t % 140) > 133;
        float look = 1.1f;
        double dx = mouseX - cx, dy = mouseY - cy, d = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        float ox = (float) (dx / d * Math.min(look, d / 40)), oy = (float) (dy / d * Math.min(look, d / 40));
        float beat = heartbeat(t);
        for (int side = -1; side <= 1; side += 2) {
            float ex = cx + side * 3.77f, ey = cy;
            if (blink) {
                UiDraw.rect(g, ex - 2.2f, ey - 0.35f, ex + 2.2f, ey + 0.35f, Colors.alpha(1f, 0x2A0A0C));
                continue;
            }
            UiDraw.glow(g, ex + ox, ey + oy, 8 + 5 * beat, Colors.alpha(0.55f + 0.35f * beat, BLOOD));
            UiDraw.disc(g, ex + ox, ey + oy, 1.1f, Colors.alpha(1f, 0xFF5A40));
            UiDraw.rect(g, ex + ox - 0.3f, ey + oy - 1.0f, ex + ox + 0.3f, ey + oy + 1.0f, Colors.alpha(1f, 0x1A0000));
        }
    }

    /** A slow red mist drifting across the panel, thickening and thinning. */
    private void mist(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 7; i++) {
            float speed = 0.06f + 0.03f * (i % 3);
            float span = imageWidth + 160;
            float mx = x - 80 + ((t * speed + i * 131) % span), my = y + 30 + (i * 53) % (imageHeight - 60) + (float) Math.sin(t * 0.01f + i) * 12;
            float swell = 0.5f + 0.5f * (float) Math.sin(t * 0.017f + i * 2.1f);
            float edge = Mth.clamp((mx - x + 40) / 80f, 0, 1) * Mth.clamp((x + imageWidth + 40 - mx) / 80f, 0, 1);
            UiDraw.glow(g, mx, my, 120 + 50 * swell, Colors.alpha(0.1f * edge * (0.5f + 0.5f * swell), 0x6A0A10));
        }
    }

    /**
     * Blood welling up along the top of the frame: a bead swells on its thread, stretches, lets go and falls, faster and
     * faster, leaving a fading streak; where it stops it splashes and the stain fades.
     */
    private void drips(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < DRIPS.length; i++) {
            float dx = x + DRIPS[i][0], period = DRIPS[i][1], fall = DRIPS[i][2];
            float p = ((t + i * 97) % period) / period;
            float top = y + 5.5f;
            int body = 0x8A0A10, lit = 0xFF6A5A;
            // the thread it hangs from, always there, darkening as blood runs down it
            float thread = 3f + (i % 4) * 1.2f;
            UiDraw.rect(g, dx - 0.35f, top, dx + 0.35f, top + thread, Colors.alpha(0.85f, 0x5A060A));
            if (p < 0.5f) {
                // the bead swells
                float r = 0.5f + 1.6f * (p / 0.5f);
                float by = top + thread + r * 0.8f;
                UiDraw.disc(g, dx, by, r, Colors.alpha(0.95f, body));
                UiDraw.disc(g, dx - r * 0.35f, by - r * 0.35f, r * 0.3f, Colors.alpha(0.7f, lit));
            } else if (p < 0.58f) {
                // it stretches, about to let go
                float k = (p - 0.5f) / 0.08f;
                float by = top + thread + 1.4f + k * 2.2f;
                UiDraw.rect(g, dx - 0.5f, top + thread, dx + 0.5f, by, Colors.alpha(0.9f, body));
                UiDraw.disc(g, dx, by, 2.1f - 0.4f * k, Colors.alpha(0.95f, body));
                UiDraw.disc(g, dx - 0.5f, by - 0.5f, 0.45f, Colors.alpha(0.7f, lit));
            } else {
                // falling, faster and faster
                float k = (p - 0.58f) / 0.42f;
                float travel = Math.min(fall, fall * 1.6f * k * k);
                float fy = top + thread + 3.6f + travel;
                boolean landed = travel >= fall;
                if (!landed) {
                    float streak = Math.min(travel, 6 + travel * 0.25f);
                    UiDraw.vGradient(g, dx - 0.5f, fy - streak, dx + 0.5f, fy, 0, Colors.alpha(0.7f, body));
                    UiDraw.disc(g, dx, fy, 1.5f, Colors.alpha(0.95f, body));
                    UiDraw.disc(g, dx - 0.4f, fy - 0.4f, 0.35f, Colors.alpha(0.7f, lit));
                } else {
                    // the splash, fading
                    float since = (k - (float) Math.sqrt(1 / 1.6f)) / (1 - (float) Math.sqrt(1 / 1.6f));
                    float a = 1 - Mth.clamp(since, 0, 1);
                    UiDraw.disc(g, dx, fy, 1.4f + 1.2f * (1 - a), Colors.alpha(0.6f * a, body));
                    for (int s = -1; s <= 1; s += 2) UiDraw.disc(g, dx + s * (2 + 2 * (1 - a)), fy - 0.8f * a, 0.5f, Colors.alpha(0.7f * a, body));
                }
            }
        }
    }

    /** Embers rising from the bottom of the panel. */
    private void embers(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 34; i++) {
            float speed = 0.25f + (i * 37 % 10) / 20f;
            float life = imageHeight + 20;
            float p = (t * speed + i * 53) % life;
            float ex = x + 12 + ((i * 97) % (imageWidth - 24)) + (float) Math.sin(t * 0.05 + i) * 4, ey = y + imageHeight - p;
            float a = Mth.clamp(p / 30f, 0, 1) * Mth.clamp((life - p) / 60f, 0, 1);
            UiDraw.glow(g, ex, ey, 5, Colors.alpha(0.35f * a, EMBER));
            UiDraw.rect(g, ex - 0.5f, ey - 0.5f, ex + 0.5f, ey + 0.5f, Colors.alpha(0.9f * a, 0xFFC080));
        }
    }

    /** Under the grid, the forge mutters to itself. */
    private void whisper(GuiGraphics g, int x, int y, float t) {
        int i = (int) (t / 140) % WHISPERS.length;
        float p = (t % 140) / 140f;
        float a = Mth.clamp(p * 6, 0, 1) * Mth.clamp((1 - p) * 6, 0, 1);
        UiDraw.textCentered(g, WHISPERS[i], x + WorldforgeMenu.GRID_X + WorldforgeMenu.SIZE * 9, y + imageHeight - 11.5f,
                Colors.alpha(0.85f * a, 0xB0402E), 0.55f, false);
    }

    // ------------------------------------------------------------------------------------------------------------
    // forging
    // ------------------------------------------------------------------------------------------------------------

    private boolean canForge() {
        return menu.ready() && !menu.busy() && !menu.slots.get(WorldforgeMenu.OUTPUT).hasItem();
    }

    private void forgeButton(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        int bx = x + FORGE_X, by = y + FORGE_Y;
        boolean ready = canForge(), hover = ready && mouseX >= bx && mouseX < bx + FORGE_W && mouseY >= by && mouseY < by + FORGE_H;
        // a slab of iron with a glowing seam, trembling when it is ready to be pressed
        float shake = ready ? (float) Math.sin(t * 1.7) * 0.4f : 0;
        UiDraw.rect(g, bx + shake, by, bx + FORGE_W + shake, by + FORGE_H, Colors.alpha(1f, hover ? 0x3A0A0C : 0x1A0809));
        UiDraw.border(g, bx + shake, by, bx + FORGE_W + shake, by + FORGE_H, 1, Colors.alpha(1f, ready ? BLOOD : 0x4A1A1C));
        if (ready) UiDraw.glowBorder(g, bx + shake, by, bx + FORGE_W + shake, by + FORGE_H, BLOOD, hover ? 0.9f : 0.45f, 4);
        String label = menu.busy() ? "FORGING" : "FORGE";
        UiDraw.textCentered(g, label, bx + FORGE_W / 2f + shake, by + 4f, Colors.alpha(ready ? 1f : 0.5f, hover ? 0xFFFFFF : BONE), 0.8f, true);
    }

    private static final String[] STAGES = {
            "Binding the seven elements", "Melting the legends down", "The anvil drinks their memories", "Hammering the end into shape",
            "Quenching it in the dark between stars", "It is learning its name", "The world begs it not to wake", "It wakes"};

    private void forgingStrip(GuiGraphics g, int x, int y, float t) {
        int sx = x + WorldforgeMenu.RIGHT_X + 12, sy = y + 152, w = 436 - 14 - WorldforgeMenu.RIGHT_X - 24;
        if (!menu.busy()) {
            String hint = menu.slots.get(WorldforgeMenu.OUTPUT).hasItem() ? "Take what it made from the Forged slot"
                    : canForge() ? "The rite is whole. Press FORGE." : "Lay a rite on the grid";
            UiDraw.textCentered(g, hint, sx + w / 2f, sy + 3, Colors.alpha(1f, ASH), 0.62f, false);
            return;
        }
        int progress = menu.progress(), total = Math.max(1, menu.total());
        float f = Math.min(1f, progress / (float) total);
        String line = progress >= total ? "It is done" : STAGES[WorldforgeBlockEntity.stage(progress, total)];
        UiDraw.text(g, line, sx, sy - 2, Colors.alpha(1f, BONE), 0.62f, true);
        int left = Math.max(0, (total - progress) / 20);
        String time = left / 60 + ":" + String.format("%02d", left % 60);
        UiDraw.textRight(g, time, sx + w, sy - 2, Colors.alpha(1f, ASH), 0.62f, false);
        int by = sy + 8;
        UiDraw.rect(g, sx, by, sx + w, by + 4, Colors.alpha(1f, 0x0A0405));
        UiDraw.hGradient(g, sx, by, sx + w * f, by + 4, Colors.alpha(1f, DEEP), Colors.alpha(1f, BLOOD));
        UiDraw.border(g, sx - 1, by - 1, sx + w + 1, by + 5, 1, Colors.alpha(1f, 0x5A1A1C));
        if (f > 0 && f < 1) UiDraw.barTip(g, sx, by, w, 4, sx + w * f, BLOOD, t);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the rites
    // ------------------------------------------------------------------------------------------------------------

    private void rites(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        UiDraw.text(g, "RITES", x + RITE_X, y + 40, Colors.alpha(1f, BLOOD), 0.7f, true);
        for (int i = 0; i < recipes.size(); i++) {
            int ix = x + RITE_X + i * 24, iy = y + RITE_Y;
            boolean sel = recipes.get(i) == selected;
            boolean hover = mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18;
            UiDraw.rect(g, ix - 1, iy - 1, ix + 19, iy + 19, Colors.alpha(1f, 0x0A0506));
            UiDraw.border(g, ix - 2, iy - 2, ix + 20, iy + 20, 1, Colors.alpha(1f, sel ? BLOOD : hover ? 0x8A2A2C : 0x4A1A1C));
            if (sel) UiDraw.glowBorder(g, ix - 2, iy - 2, ix + 20, iy + 20, BLOOD, 0.6f, 4);
            g.renderFakeItem(recipes.get(i).value().result(), ix + 1, iy + 1);
        }
        if (selected == null) {
            UiDraw.text(g, "Choose a rite to see its pattern", x + RITE_X + Math.max(1, recipes.size()) * 24 + 2, y + RITE_Y + 6,
                    Colors.alpha(1f, ASH), 0.58f, false);
            return;
        }
        WorldforgeRecipe r = selected.value();
        int need = (int) r.pattern().ingredients().stream().filter(ing -> !ing.isEmpty()).count();
        int laid = WorldforgeMenu.laid(menu.grid(), r);
        int tx = x + RITE_X + recipes.size() * 24 + 2;
        UiDraw.text(g, r.result().getHoverName().getString(), tx, y + RITE_Y + 1, Colors.alpha(1f, BONE), 0.6f, true);
        UiDraw.text(g, Tr.f("%s / %s offerings laid", laid, need), tx, y + RITE_Y + 10, Colors.alpha(1f, laid >= need ? 0xFF8070 : ASH), 0.58f, false);
        fillButton(g, x + 436 - 14 - 8 - FILL_W, y + RITE_Y + 24, mouseX, mouseY);
    }

    private boolean canFill() {
        return selected != null && minecraft.player != null && WorldforgeMenu.fillable(minecraft.player, menu.grid(), selected.value()) > 0;
    }

    private void fillButton(GuiGraphics g, int bx, int by, int mouseX, int mouseY) {
        boolean can = canFill(), hover = can && mouseX >= bx && mouseX < bx + FILL_W && mouseY >= by && mouseY < by + 13;
        UiDraw.rect(g, bx, by, bx + FILL_W, by + 13, Colors.alpha(1f, hover ? 0x3A0A0C : 0x1A0809));
        UiDraw.border(g, bx, by, bx + FILL_W, by + 13, 1, Colors.alpha(1f, can ? 0x9A2A2C : 0x4A1A1C));
        UiDraw.textCentered(g, "Fill", bx + FILL_W / 2f, by + 3, Colors.alpha(can ? 1f : 0.45f, hover ? 0xFFFFFF : BONE), 0.72f, true);
    }

    private static ItemStack cycled(Ingredient ing, float time) {
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        return items[(int) (time / 20) % items.length];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < recipes.size(); i++) {
            int ix = leftPos + RITE_X + i * 24, iy = topPos + RITE_Y;
            if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) {
                selected = selected == recipes.get(i) ? null : recipes.get(i);
                chosenSeen = selected == null ? -1 : i;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorldforgeMenu.CHOOSE + chosenSeen);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 0.7f, 1f));
                return true;
            }
        }
        int bx = leftPos + FORGE_X, by = topPos + FORGE_Y;
        if (canForge() && mouseX >= bx && mouseX < bx + FORGE_W && mouseY >= by && mouseY < by + FORGE_H) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorldforgeMenu.FORGE_BUTTON);
            return true;
        }
        int fx = leftPos + 436 - 14 - 8 - FILL_W, fy = topPos + RITE_Y + 24;
        if (canFill() && mouseX >= fx && mouseX < fx + FILL_W && mouseY >= fy && mouseY < fy + 13) {
            int i = recipes.indexOf(selected);
            if (i >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorldforgeMenu.FILL + i);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), 0.8f, 1f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        float t = time(0);
        // the forge's name, spaced out, flickering now and then
        float flicker = (t % 97) > 94 ? 0.4f : 1f;
        UiDraw.textCentered(g, "T H E   W O R L D F O R G E", WorldforgeMenu.RIGHT_X + (436 - 14 - WorldforgeMenu.RIGHT_X) / 2f, 17,
                Colors.alpha(flicker, BLOOD), 1f, true);
        UiDraw.textCentered(g, "where legends are unmade", WorldforgeMenu.RIGHT_X + (436 - 14 - WorldforgeMenu.RIGHT_X) / 2f, 28,
                Colors.alpha(0.8f, ASH), 0.55f, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderGhost(g, mouseX, mouseY, partialTick);
        // it rises out of the dark when opened
        float t = time(partialTick);
        if (openedAt < 0) openedAt = t;
        float dark = 1 - Mth.clamp((t - openedAt) / 14f, 0, 1);
        if (dark > 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            UiDraw.rect(g, 0, 0, width, height, Colors.alpha(dark * dark, 0x050102));
            g.pose().popPose();
        }
        renderTooltip(g, mouseX, mouseY);
        for (int i = 0; i < recipes.size(); i++) {
            int ix = leftPos + RITE_X + i * 24, iy = topPos + RITE_Y;
            if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) g.renderTooltip(font, recipes.get(i).value().result(), mouseX, mouseY);
        }
    }

    /** The chosen rite's pattern on the grid: ghost offerings in the empty slots (named when hovered), red where something else lies. */
    private void renderGhost(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (selected == null) return;
        float time = time(partialTick);
        Ingredient[] at = WorldforgeMenu.layout(selected.value());
        ItemStack hovered = ItemStack.EMPTY;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (int i = 0; i < at.length; i++) {
            Slot slot = menu.slots.get(WorldforgeMenu.GRID_START + i);
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            ItemStack have = slot.getItem();
            if (at[i] == null) {
                if (!have.isEmpty()) g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x70FF2020);
                continue;
            }
            if (have.isEmpty()) {
                ItemStack ghost = cycled(at[i], time);
                g.renderFakeItem(ghost, sx, sy);
                g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0xA0200608);
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) hovered = ghost;
            } else if (!at[i].test(have)) {
                g.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x70FF2020);
            }
        }
        g.pose().popPose();
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }
}
