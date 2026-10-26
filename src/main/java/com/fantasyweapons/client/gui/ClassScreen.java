package com.fantasyweapons.client.gui;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.network.QuestPayloads;
import com.fantasyweapons.playerclass.ClassGear;
import com.fantasyweapons.playerclass.ClassService;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The class menu, a class to a page: on the left a banner bearing the class's emblem inside a turning sigil and a
 * diamond of its colour; on the right its attributes as rows of pips, what it gives and costs, the weapons and armor
 * that answer to it, its signature gift and a word of its lore. Arrows (or the arrow keys) turn the pages; the seal
 * beneath takes the class, asking once more first. The class already taken is crowned, the Mage is sealed while it is
 * being made, and after a change the menu shows how long until the next.
 */
public final class ClassScreen extends Screen {
    public static final ResourceLocation EMBLEMS = FantasyWeapons.id("textures/gui/classes.png");
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");
    private static final List<PlayerClass> PAGES = List.of(PlayerClass.ASSASSIN, PlayerClass.BERSERKER, PlayerClass.PALADIN, PlayerClass.MAGE);
    private static final int W = 416, H = 236;
    private static final int GOLD = 0xF2C46B, GOLD_LIGHT = 0xFFE7A8, MUTED = 0xB9AC92, GOOD = 0x8FE08A, BAD = 0xF07A62, LINE = 0xB08A3E, LOCKED = 0x6E6A78;
    /** How long a first click on "Choose" waits for the second, confirming one (ticks). */
    private static final int CONFIRM_TICKS = 80;
    private static final String[] STAT_NAMES = {"Defense", "Attack", "Agility", "Sustain", "Control"};

    /** What the page shows of a class beyond the class itself: its attributes (0 to 5) and its signature gift. */
    private record Page(int[] stats, String gift, String giftText, Holder<MobEffect> giftIcon) {
    }

    private static Page page(PlayerClass c) {
        return switch (c) {
            case ASSASSIN -> new Page(new int[]{1, 4, 5, 2, 3}, "Vanish",
                    "A kill cloaks you: unseen and swift for three seconds. Blows from behind strike 60% harder, and the wounded fall faster.",
                    MobEffects.INVISIBILITY);
            case BERSERKER -> new Page(new int[]{2, 5, 3, 4, 2}, "Bloodrage",
                    "The nearer death, the harder you hit: up to half again as hard. Every kill drinks back a heart.",
                    MobEffects.DAMAGE_BOOST);
            case PALADIN -> new Page(new int[]{5, 3, 2, 5, 3}, "Holy Aegis",
                    "The undead take half again as much from you and deal a quarter less. Out of combat, the light mends your wounds.",
                    MobEffects.DAMAGE_RESISTANCE);
            default -> new Page(new int[]{1, 4, 3, 2, 5}, "Arcane Ward",
                    "Every spell strikes, heals and shields 15% stronger. Out of combat a ward of arcane light gathers around you, a shield of two hearts.",
                    MobEffects.ABSORPTION);
        };
    }

    /** Ticks until the class menu opens for a newcomer (once the world is showing), or -1. */
    private static int pendingOpen = -1;

    private final GuiZoom zoom = new GuiZoom(W, H);
    private int left, top;
    private int index;
    /** The page turn: which way (+1 forward, -1 back), and when it began (ms). */
    private int turnDir;
    private long turnAt;
    private PlayerClass confirming;
    private float confirmUntil;
    private float time;
    private ItemStack hoveredGear = ItemStack.EMPTY;
    /** The width of an item slot and the space after it. */
    private static final int SLOT = 19;
    /** How fast a gear strip slides (menu units a tick) and how long it rests at either end (in the same units). */
    private static final float STRIP_SPEED = 0.6f, STRIP_PAUSE = 24;
    /** The gear strips that slide on the page shown, where they are and how far they go. */
    private record Strip(String label, int x, int y, int w, float max) {
    }
    private final List<Strip> strips = new ArrayList<>();
    /** Each strip's place (menu units; past either end it rests) and the way it is sliding, by label. */
    private final java.util.Map<String, float[]> stripState = new java.util.HashMap<>();
    /** Ticks since the last frame, for the strips' slide. */
    private float frameTicks;
    private long lastFrame;

    public ClassScreen() {
        super(Component.literal(Tr.s("Choose Your Class")));
    }

    /** Opens the menu for a player new to the world, a moment after the world appears. */
    public static void openWhenReady() {
        pendingOpen = 40;
    }

    public static void tickPending(net.minecraft.client.Minecraft mc) {
        if (pendingOpen < 0 || mc.player == null || mc.getOverlay() != null || mc.screen != null) return;
        if (--pendingOpen <= 0) {
            pendingOpen = -1;
            mc.setScreen(new ClassScreen());
        }
    }

    @Override
    protected void init() {
        zoom.fit(width, height);
        left = zoom.left(width);
        top = zoom.top(height);
        if (turnAt == 0) {
            // open on the class already taken
            int at = PAGES.indexOf(ClassService.of(minecraft.player));
            index = Math.max(0, at);
            turnAt = 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A class's emblem from the sheet. */
    public static void emblem(GuiGraphics g, PlayerClass c, float x, float y, float size, int argb) {
        int n = PlayerClass.values().length;
        UiDraw.textureUv(g, EMBLEMS, x, y, size, size, c.ordinal() / (float) n, 0, (c.ordinal() + 1) / (float) n, 1, argb);
    }

    private PlayerClass shown() {
        return PAGES.get(index);
    }

    private void turn(int dir) {
        index = Math.floorMod(index + dir, PAGES.size());
        turnDir = dir;
        turnAt = Util.getMillis();
        confirming = null;
        stripState.clear();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.1f + 0.1f * dir, 0.6f));
    }

    private static List<ItemStack> weapons(PlayerClass c) {
        List<ItemStack> out = new ArrayList<>();
        for (WeaponDefinition def : Weapons.all()) {
            if (ClassGear.answersTo(def.id(), c)) out.add(new ItemStack(ModItems.WEAPONS.get(def.id()).get()));
        }
        return out;
    }

    private static List<ItemStack> armor(PlayerClass c) {
        List<ItemStack> out = new ArrayList<>();
        for (ArmorSet set : ArmorSet.values()) {
            if (ClassGear.of(set.armorClass()) == c) out.add(new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.CHESTPLATE).get()));
        }
        return out;
    }

    // ---- layout (in the menu's own units, see GuiZoom) ----

    private int scrollX() {
        return left + 12;
    }

    private int rightX() {
        return left + 152;
    }

    private int controlsY() {
        return top + 212;
    }

    private int chooseX() {
        return left + W / 2 - 52;
    }

    private int prevX() {
        return left + W / 2 - 78;
    }

    private int nextX() {
        return left + W / 2 + 60;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (devMouse != null) {
            mouseX = (int) zoom.out(devMouse[0]);
            mouseY = (int) zoom.out(devMouse[1]);
        }
        super.render(g, mouseX, mouseY, partialTick);
        int realX = mouseX, realY = mouseY;
        mouseX = (int) zoom.in(mouseX);
        mouseY = (int) zoom.in(mouseY);
        time = Util.getMillis() / 50f;
        long now = Util.getMillis();
        frameTicks = lastFrame == 0 ? 0 : Math.min(5, (now - lastFrame) / 50f);
        lastFrame = now;
        if (confirming != null && time > confirmUntil) confirming = null;
        PlayerClass c = shown(), current = ClassService.of(minecraft.player);
        boolean locked = !c.available();
        int color = locked ? LOCKED : c.color();
        hoveredGear = ItemStack.EMPTY;
        UiDraw.style(UiDraw.NETHERITE, -1, GOLD, GOLD_LIGHT, MUTED);
        zoom.push(g);
        try {
            UiDraw.panel(g, left, top, W, H, color, 1f);
            header(g, c, current, color);
            // the page slides in from the side it was turned to
            float e = ease(Math.min(1, (Util.getMillis() - turnAt) / 260f));
            g.pose().pushPose();
            g.pose().translate(turnDir * (1 - e) * 22, 0, 0);
            banner(g, c, current, color, locked);
            details(g, c, color, locked, mouseX, mouseY);
            g.pose().popPose();
            if (e < 1) UiDraw.rect(g, left + 8, top + 26, left + W - 8, top + 208, Colors.alpha(0.55f * (1 - e), 0x0A0710));
            controls(g, c, current, color, locked, mouseX, mouseY);
        } finally {
            zoom.pop(g);
            UiDraw.resetTheme();
        }
        if (!hoveredGear.isEmpty()) g.renderTooltip(font, hoveredGear, realX, realY);
    }

    private void header(GuiGraphics g, PlayerClass c, PlayerClass current, int color) {
        UiDraw.dyeRibbon(Colors.lerpRgb(color, 0x200810, 0.35f));
        UiDraw.banner(g, left + W / 2f, top + 2, 176, 22, 1f);
        UiDraw.title(g, c.displayName().toUpperCase(java.util.Locale.ROOT), left + W / 2f, top + 8, Colors.alpha(1f, Colors.lerpRgb(color, 0xFFFFFF, 0.55f)), 0.95f);
        UiDraw.text(g, "Calling:", left + 12, top + 9, Colors.alpha(1f, MUTED), 0.58f, false);
        UiDraw.text(g, current.displayName(), left + 12 + UiDraw.width("Calling: ") * 0.58f, top + 9, Colors.alpha(1f, current.color()), 0.58f, true);
        // a pip for each page
        for (int i = 0; i < PAGES.size(); i++) {
            PlayerClass p = PAGES.get(i);
            float x = left + W - 14 - (PAGES.size() - 1 - i) * 10, y = top + 12;
            int pc = p.available() ? p.color() : LOCKED;
            if (i == index) UiDraw.glow(g, x, y, 12, Colors.alpha(0.6f, pc));
            UiDraw.diamond(g, x, y, i == index ? 3.2f : 2.4f, Colors.alpha(i == index ? 1f : 0.55f, pc));
        }
    }

    /** The banner on the left: the emblem in its sigil and diamond, the name and calling beneath. */
    private void banner(GuiGraphics g, PlayerClass c, PlayerClass current, int color, boolean locked) {
        int x = scrollX(), y = top + 28, w = 132, h = 178;
        UiDraw.rect(g, x, y, x + w, y + h, 0xF0120D18);
        UiDraw.vGradient(g, x, y, x + w, y + h, Colors.alpha(0.28f, color), Colors.alpha(0.04f, color));
        UiDraw.border(g, x - 1, y - 1, x + w + 1, y + h + 1, 1, Colors.alpha(0.85f, LINE));
        for (float[] k : new float[][]{{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) UiDraw.diamond(g, k[0], k[1], 3, Colors.alpha(1f, GOLD));
        float cx = x + w / 2f, cy = y + 74;
        UiDraw.sigil(g, cx, cy, 50, color, time * 0.8f, locked ? 0.25f : 0.75f);
        diamondFrame(g, cx, cy, 60, color, locked);
        float bob = (float) Math.sin(time * 0.08f) * 1.4f;
        float size = 84;
        emblem(g, c, cx - size / 2, cy - size / 2 + bob, size, locked ? Colors.alpha(1f, 0x77737F) : 0xFFFFFFFF);
        if (locked) UiDraw.texture(g, LOCK, cx + 16, cy + 14, 20, 20, 0xFFFFFFFF);
        UiDraw.title(g, c.displayName(), cx, y + 140, Colors.alpha(1f, locked ? 0xA09CAA : color), 1.05f);
        UiDraw.textCentered(g, c.tagline(), cx, y + 153, Colors.alpha(1f, MUTED), 0.55f, false);
        if (c == current) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.15f);
            UiDraw.glowBorder(g, x, y, x + w, y + h, color, pulse, 3);
            UiDraw.textCentered(g, "★ YOUR CLASS ★", cx, y + 165, Colors.alpha(1f, GOLD_LIGHT), 0.55f, true);
        }
    }

    /** A diamond of the class's colour round the emblem, a gleam running along its edges. */
    private void diamondFrame(GuiGraphics g, float cx, float cy, float r, int color, boolean locked) {
        float[][] p = {{cx, cy - r}, {cx + r, cy}, {cx, cy + r}, {cx - r, cy}};
        int light = Colors.lerpRgb(color, 0xFFFFFF, 0.5f), dark = Colors.lerpRgb(color, 0x000000, 0.45f);
        float a = locked ? 0.4f : 0.95f;
        for (int i = 0; i < 4; i++) {
            float[] s = p[i], t = p[(i + 1) % 4];
            UiDraw.line(g, s[0], s[1], t[0], t[1], 1.6f, Colors.alpha(a, i < 2 ? light : color), Colors.alpha(a, i < 2 ? color : dark));
        }
        float inner = r - 5;
        float[][] q = {{cx, cy - inner}, {cx + inner, cy}, {cx, cy + inner}, {cx - inner, cy}};
        for (int i = 0; i < 4; i++) {
            float[] s = q[i], t = q[(i + 1) % 4];
            UiDraw.line(g, s[0], s[1], t[0], t[1], 0.6f, Colors.alpha(a * 0.45f, color), Colors.alpha(a * 0.45f, color));
        }
        if (locked) return;
        float run = (time * 0.012f) % 1f * 4;
        int side = (int) run;
        float f = run - side;
        float[] s = p[side], t = p[(side + 1) % 4];
        UiDraw.glow(g, s[0] + (t[0] - s[0]) * f, s[1] + (t[1] - s[1]) * f, 12, Colors.alpha(0.9f, light));
    }

    /** The page's right side: attributes, perks, gear, the signature gift and the lore. */
    private void details(GuiGraphics g, PlayerClass c, int color, boolean locked, int mouseX, int mouseY) {
        int rx = rightX(), y = top + 28;
        Page page = page(c);
        // attributes
        box(g, rx, y, rx + 118, y + 70);
        UiDraw.text(g, "ATTRIBUTES", rx + 6, y + 4, Colors.alpha(1f, GOLD), 0.55f, true);
        for (int i = 0; i < STAT_NAMES.length; i++) {
            float sy = y + 15 + i * 11;
            UiDraw.text(g, STAT_NAMES[i], rx + 6, sy, Colors.alpha(1f, locked ? 0x8A8494 : 0xE8DCC0), 0.55f, false);
            for (int j = 0; j < 5; j++) {
                float px = rx + 54 + j * 12;
                boolean on = j < page.stats()[i];
                UiDraw.rect(g, px, sy - 0.5f, px + 10, sy + 5.5f, 0xFF1A1420);
                if (on) {
                    int fill = locked ? 0x5E5A68 : color;
                    UiDraw.rect(g, px + 0.5f, sy, px + 9.5f, sy + 5, Colors.alpha(1f, fill));
                    UiDraw.rect(g, px + 0.5f, sy, px + 9.5f, sy + 1.2f, Colors.alpha(0.8f, Colors.lerpRgb(fill, 0xFFFFFF, 0.5f)));
                } else {
                    UiDraw.rect(g, px + 0.5f, sy, px + 9.5f, sy + 5, 0xFF2C2634);
                }
            }
        }
        // perks
        int px0 = rx + 124;
        box(g, px0, y, rx + 252, y + 70);
        UiDraw.text(g, "PERKS", px0 + 6, y + 4, Colors.alpha(1f, GOLD), 0.55f, true);
        float py = y + 14;
        for (String perk : c.perks()) {
            boolean drawback = perk.startsWith("-");
            UiDraw.diamond(g, px0 + 6, py + 2.3f, 1.3f, Colors.alpha(1f, locked ? LOCKED : drawback ? 0xA0402E : color));
            int lines = UiDraw.wrap(g, perk, px0 + 9, py, 118, Colors.alpha(1f, locked ? 0x8A8494 : drawback ? BAD : GOOD), 0.48f, 5);
            py += lines * 5 + 1.2f;
        }
        // the gear that answers to the class: weapons and armor side by side, each in a strip of its own that slides
        // along when it holds more than it shows
        int gy = y + 76;
        box(g, rx, gy, rx + 252, gy + 30);
        strips.clear();
        List<ItemStack> weapons = weapons(c), armor = armor(c);
        int x0 = rx + 6, avail = 240;
        if (armor.isEmpty()) {
            gearRow(g, "WEAPONS", weapons, x0, gy, avail, mouseX, mouseY, locked);
        } else {
            int gap = 10, half = (avail - gap) / 2, needW = need(weapons), needA = need(armor);
            int ww;
            if (needW <= half && needA <= half) ww = half;
            else if (needW + needA + gap <= avail) ww = needW;
            else ww = Math.clamp(Math.round((avail - gap) * weapons.size() / (float) (weapons.size() + armor.size())), 60, avail - gap - 60);
            gearRow(g, "WEAPONS", weapons, x0, gy, ww, mouseX, mouseY, locked);
            gearRow(g, "ARMOR", armor, x0 + ww + gap, gy, avail - gap - ww, mouseX, mouseY, locked);
        }
        // the signature gift
        int sy = gy + 36;
        box(g, rx, sy, rx + 252, sy + 36);
        UiDraw.rect(g, rx + 5, sy + 5, rx + 31, sy + 31, 0xFF0C0910);
        UiDraw.border(g, rx + 5, sy + 5, rx + 31, sy + 31, 1, Colors.alpha(1f, locked ? LOCKED : color));
        if (!locked) UiDraw.glow(g, rx + 18, sy + 18, 30, Colors.alpha(0.35f, color));
        var sprite = minecraft.getMobEffectTextures().get(page.giftIcon());
        g.blit(rx + 9, sy + 9, 0, 18, 18, sprite, locked ? 0.5f : 1f, locked ? 0.5f : 1f, locked ? 0.5f : 1f, 1f);
        UiDraw.text(g, Tr.s(page.gift()).toUpperCase(java.util.Locale.ROOT), rx + 37, sy + 5, Colors.alpha(1f, locked ? 0xA09CAA : color), 0.7f, true);
        UiDraw.wrap(g, page.giftText(), rx + 37, sy + 14, 210, Colors.alpha(1f, locked ? 0x8A8494 : 0xD8CCB0), 0.5f, 6);
        // a word of its lore
        int ly = sy + 42;
        box(g, rx, ly, rx + 252, top + 206);
        UiDraw.wrap(g, Component.literal("“" + c.lore() + "”").withStyle(s -> s.withItalic(true)), rx + 6, ly + 4, 240,
                Colors.alpha(1f, locked ? 0x8A8494 : 0xCFC3A6), 0.52f, 7);
    }

    /** How wide a row of these items is at full size. */
    private static int need(List<ItemStack> items) {
        return items.size() * SLOT - 1;
    }

    /**
     * A labelled row of items {@code w} wide. When they are more than it shows, it is a strip that slides along to show
     * them all, back and forth, stops while the mouse is over it, and turns with the mouse wheel; small arrows by the
     * label show which way there is more.
     */
    private void gearRow(GuiGraphics g, String label, List<ItemStack> items, int x, int y, int w, int mouseX, int mouseY, boolean locked) {
        UiDraw.text(g, label, x, y + 3, Colors.alpha(1f, GOLD), 0.5f, true);
        if (items.isEmpty()) {
            UiDraw.text(g, "None yet", x, y + 15, Colors.alpha(1f, 0x8A8494), 0.5f, false);
            return;
        }
        int iy = y + 10;
        float max = Math.max(0, need(items) - w);
        boolean over = inside(mouseX, mouseY, x, iy, w, 18);
        float[] strip = stripState.computeIfAbsent(label, k -> new float[]{0, 1});
        if (max > 0) {
            strips.add(new Strip(label, x, iy, w, max));
            if (!over && !locked) {
                // a slow slide, pausing a moment at either end
                strip[0] += strip[1] * frameTicks * STRIP_SPEED;
                if (strip[0] > max + STRIP_PAUSE || strip[0] < -STRIP_PAUSE) strip[1] = -strip[1];
            }
            strip[0] = Math.clamp(strip[0], -STRIP_PAUSE, max + STRIP_PAUSE);
            float off = Math.clamp(strip[0], 0, max);
            int more = Colors.alpha(1f, locked ? LOCKED : GOLD_LIGHT), none = Colors.alpha(0.35f, MUTED);
            chevron(g, x + w - 11, y + 5.5f, -1, off > 0.5f ? more : none);
            chevron(g, x + w - 3, y + 5.5f, 1, off < max - 0.5f ? more : none);
            zoom.scissor(g, x, iy - 1, x + w, iy + 19);
            try {
                drawItems(g, items, x - off, iy, x, x + w, mouseX, mouseY, locked);
                // the items fade out where the strip cuts them off
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                if (off > 0.5f) fade(g, x, iy - 1, iy + 19, 1);
                if (off < max - 0.5f) fade(g, x + w, iy - 1, iy + 19, -1);
                g.pose().popPose();
            } finally {
                g.disableScissor();
            }
        } else {
            strip[0] = 0;
            drawItems(g, items, x, iy, x, x + w, mouseX, mouseY, locked);
        }
    }

    /** A shadow 10 wide from the edge at {@code x} into the strip ({@code dir} 1 rightwards, -1 leftwards), darkest at the edge. */
    private static void fade(GuiGraphics g, float x, float y0, float y1, int dir) {
        for (int i = 0; i < 10; i++) {
            float a = 0.85f * (1 - i / 10f);
            float px = x + dir * i;
            UiDraw.rect(g, Math.min(px, px + dir), y0, Math.max(px, px + dir), y1, Colors.alpha(a * a, 0x0B0810));
        }
    }

    /** A small chevron at ({@code cx}, {@code cy}) pointing left ({@code dir} -1) or right (1). */
    private static void chevron(GuiGraphics g, float cx, float cy, int dir, int color) {
        UiDraw.line(g, cx - 1.2f * dir, cy - 2.2f, cx + 1.2f * dir, cy, 0.9f, color, color);
        UiDraw.line(g, cx + 1.2f * dir, cy, cx - 1.2f * dir, cy + 2.2f, 0.9f, color, color);
    }

    /** Item slots from {@code x}; only those between {@code clip0} and {@code clip1} can be hovered. */
    private void drawItems(GuiGraphics g, List<ItemStack> items, float x, int iy, int clip0, int clip1, int mouseX, int mouseY, boolean locked) {
        for (int i = 0; i < items.size(); i++) {
            float ix = x + i * SLOT;
            if (ix + 18 < clip0 || ix > clip1) continue;
            g.pose().pushPose();
            g.pose().translate(ix - (int) ix, 0, 0);
            int sx = (int) ix;
            UiDraw.rect(g, sx, iy, sx + 18, iy + 18, 0xFF0C0910);
            UiDraw.border(g, sx, iy, sx + 18, iy + 18, 1, Colors.alpha(0.7f, LINE));
            g.renderFakeItem(items.get(i), sx + 1, iy + 1);
            if (locked) UiDraw.rect(g, sx + 1, iy + 1, sx + 17, iy + 17, Colors.alpha(0.55f, 0x0A0810));
            g.pose().popPose();
            if (mouseX >= Math.max(ix, clip0) && mouseX < Math.min(ix + 18, clip1) && mouseY >= iy && mouseY < iy + 18) hoveredGear = items.get(i);
        }
    }

    private static void box(GuiGraphics g, float x0, float y0, float x1, float y1) {
        UiDraw.rect(g, x0, y0, x1, y1, Colors.alpha(0.42f, 0x000000));
        UiDraw.border(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 1, Colors.alpha(0.6f, LINE));
    }

    /** The arrows either side, the seal to take the class between them, and how matters stand. */
    private void controls(GuiGraphics g, PlayerClass c, PlayerClass current, int color, boolean locked, int mouseX, int mouseY) {
        int y = controlsY();
        arrow(g, prevX(), y, -1, color, mouseX, mouseY);
        arrow(g, nextX(), y, 1, color, mouseX, mouseY);
        long wait = ClassService.waitLeft(minecraft.player);
        boolean chosen = c == current, canTake = !locked && !chosen && wait <= 0;
        int bx = chooseX(), bw = 104, bh = 16;
        boolean over = canTake && inside(mouseX, mouseY, bx, y, bw, bh), asking = confirming == c;
        UiDraw.button(g, bx, y, bw, bh, canTake ? over ? 1 : 0 : 2, canTake ? 1f : 0.55f);
        if (canTake) {
            float pulse = asking ? 0.7f + 0.3f * (float) Math.sin(time * 0.5f) : 0.45f + 0.2f * (float) Math.sin(time * 0.2f);
            UiDraw.glowBorder(g, bx, y, bx + bw, y + bh, color, pulse, asking ? 4 : 3);
        }
        String label = locked ? "In the making" : chosen ? "Chosen" : wait > 0 ? Tr.f("Wait %s", minutes(wait)) : asking ? "Confirm?" : Tr.f("Choose %s", c.displayName());
        int labelColor = asking ? Colors.lerpRgb(color, 0xFFFFFF, 0.4f) : over ? 0xFFFFFF : canTake ? GOLD : 0x9A927E;
        UiDraw.textCentered(g, label, bx + bw / 2f, y + 4.5f, Colors.alpha(1f, labelColor), 0.72f, true);
        // how matters stand
        String status;
        int statusColor;
        if (current == PlayerClass.NONE) {
            status = "Your first choice is free";
            statusColor = GOOD;
        } else if (wait > 0) {
            status = Tr.f("Change again in %s", minutes(wait));
            statusColor = BAD;
        } else {
            status = "You may change your class";
            statusColor = GOOD;
        }
        UiDraw.textRight(g, status, left + W - 12, y + 1, Colors.alpha(1f, statusColor), 0.52f, true);
        String hint = current == PlayerClass.NONE
                ? Tr.f("Or choose later: %s", com.fantasyweapons.client.input.KeyBindings.CLASSES.getTranslatedKeyMessage().getString())
                : Tr.f("Changing waits %s min", ClassService.CHANGE_WAIT / 20 / 60);
        UiDraw.textRight(g, hint, left + W - 12, y + 9, Colors.alpha(1f, MUTED), 0.48f, false);
        UiDraw.text(g, (index + 1) + " / " + PAGES.size(), left + 14, y + 5, Colors.alpha(1f, MUTED), 0.55f, false);
    }

    /** A turning arrow: a carved button with a chevron of the class's colour. */
    private void arrow(GuiGraphics g, int x, int y, int dir, int color, int mouseX, int mouseY) {
        boolean over = inside(mouseX, mouseY, x, y, 18, 16);
        UiDraw.button(g, x, y, 18, 16, over ? 1 : 0, 1f);
        float cx = x + 9 + dir * (over ? 1.2f : 0), cy = y + 8;
        int c = Colors.alpha(1f, over ? Colors.lerpRgb(color, 0xFFFFFF, 0.4f) : color);
        for (float off : new float[]{-2.2f, 1.8f}) {
            float tx = cx + off * dir;
            UiDraw.line(g, tx - 2.2f * dir, cy - 4, tx + 1.8f * dir, cy, 1.4f, c, c);
            UiDraw.line(g, tx + 1.8f * dir, cy, tx - 2.2f * dir, cy + 4, 1.4f, c, c);
        }
    }

    private static String minutes(long ticks) {
        long secs = (ticks + 19) / 20;
        return secs >= 60 ? Tr.f("%s min", (secs + 59) / 60) : Tr.f("%ss", secs);
    }

    // ---- input ----

    private void choose() {
        PlayerClass c = shown(), current = ClassService.of(minecraft.player);
        if (!c.available() || c == current || ClassService.waitLeft(minecraft.player) > 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.2f, 0.4f));
            return;
        }
        if (confirming == c) {
            PacketDistributor.sendToServer(new QuestPayloads.ChooseClass(c.id()));
            confirming = null;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.1f, 0.8f));
        } else {
            confirming = c;
            confirmUntil = time + CONFIRM_TICKS;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.6f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = zoom.in(mouseX);
        mouseY = zoom.in(mouseY);
        int y = controlsY();
        if (inside(mouseX, mouseY, prevX(), y, 18, 16)) {
            turn(-1);
            return true;
        }
        if (inside(mouseX, mouseY, nextX(), y, 18, 16)) {
            turn(1);
            return true;
        }
        if (inside(mouseX, mouseY, chooseX(), y, 104, 16)) {
            choose();
            return true;
        }
        // the page pips
        for (int i = 0; i < PAGES.size(); i++) {
            float x = left + W - 14 - (PAGES.size() - 1 - i) * 10;
            if (i != index && inside(mouseX, mouseY, x - 4, top + 8, 8, 8)) {
                turn(i - index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = zoom.in(mouseX), my = zoom.in(mouseY);
        for (Strip strip : strips) {
            if (inside(mx, my, strip.x(), strip.y(), strip.w(), 18)) {
                // over a gear strip the wheel slides it
                float[] state = stripState.computeIfAbsent(strip.label(), k -> new float[]{0, 1});
                state[0] = Math.clamp(Math.clamp(state[0], 0, strip.max()) - (float) scrollY * SLOT, 0, strip.max());
                return true;
            }
        }
        if (scrollY != 0) turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            turn(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---- DEVELOPMENT ONLY: for the screenshot scripts ----

    public void devPage(PlayerClass c) {
        int at = PAGES.indexOf(c);
        if (at >= 0 && at != index) turn(at - index);
    }

    /** DEVELOPMENT ONLY (screenshot director): where the mouse is taken to be, in the menu's units from its corner. */
    public static double[] devMouse;

    /** DEVELOPMENT ONLY: the mouse wheel turned at {@link #devMouse}. */
    public void devScroll(double amount) {
        if (devMouse != null) mouseScrolled(zoom.out(devMouse[0]), zoom.out(devMouse[1]), 0, amount);
    }

    /** DEVELOPMENT ONLY: the menu's corner, in its own units. */
    public int devLeft() {
        return left;
    }

    public int devTop() {
        return top;
    }

    public void devChoose() {
        choose();
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
