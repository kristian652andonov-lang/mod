package com.fantasyweapons.client.gui;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.network.C2SPayloads;
import com.fantasyweapons.progression.MasteryBonus;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The weapon progression menu. Layout follows the reference: a header with the weapon's stats, the selected ability's
 * details on the LEFT, the 3D weapon model and the ability tree in the CENTRE, and the upgrade information with a big
 * UPGRADE button on the RIGHT. All values are read live from the item's synced data; every action is a request the
 * server validates.
 */
public class ProgressionScreen extends Screen {
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");
    private static final ResourceLocation GEM = FantasyWeapons.id("textures/gui/mastery_point.png");
    private static final String CORE = "#core";

    // design canvas
    private static final float CW = 440, CH = 272;
    private static final float LEFT_X = 4, LEFT_W = 134;
    private static final float MID_X = 142, MID_W = 156;
    private static final float RIGHT_X = 302, RIGHT_W = 134;
    private static final float PANEL_Y = 56, PANEL_H = 192;
    private static final float TREE_Y = 114;

    private int slot;
    private String selected = CORE;
    private long openedAt;
    private float scale = 1;
    private float ox, oy;
    private float modelYaw = 30;
    private boolean dragging;
    private double lastDragX;
    private boolean confirmOpen;
    private String hoverNode;
    private String hoverButton;
    private String lastHoverSound = "";
    private long upgradeFlashAt;
    private int lastPoints = -1;

    public ProgressionScreen(int slot) {
        super(Component.translatableWithFallback("screen.fantasyweapons.progression", "Weapon Progression"));
        this.slot = slot;
    }

    /** Main-hand slot if it holds a weapon, else the first weapon in the inventory, else -1. */
    public static int findWeaponSlot(Player player) {
        int sel = player.getInventory().selected;
        if (player.getInventory().getItem(sel).getItem() instanceof FantasyWeaponItem) return sel;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof FantasyWeaponItem) return i;
        }
        return -1;
    }

    @Override
    protected void init() {
        openedAt = Util.getMillis();
        scale = Math.min(1.6f, Math.min(width / (CW + 8), height / (CH + 6)));
        ox = (width - CW * scale) / 2f;
        oy = (height - CH * scale) / 2f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack stack() {
        Player p = Minecraft.getInstance().player;
        if (p == null || slot < 0 || slot >= p.getInventory().getContainerSize()) return ItemStack.EMPTY;
        ItemStack s = p.getInventory().getItem(slot);
        return s.getItem() instanceof FantasyWeaponItem ? s : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // drawn in render() so the element theme can tint it
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        ItemStack stack = stack();
        if (stack.isEmpty()) {
            onClose();
            return;
        }
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        int theme = def.themePrimary(data);
        int light = def.themeSecondary(data);
        // the whole tome is dressed in the weapon's own colours
        UiDraw.theme(theme, light);
        try {
            renderThemed(g, mouseX, mouseY, partial, stack, item, def, data, theme, light);
        } finally {
            UiDraw.resetTheme();
        }
    }

    private void renderThemed(GuiGraphics g, int mouseX, int mouseY, float partial, ItemStack stack, FantasyWeaponItem item, WeaponDefinition def, WeaponData data,
                              int theme, int light) {
        long now = Util.getMillis();
        float t = now / 1000f;
        float open = Math.min(1f, (now - openedAt) / 420f);
        float ease = 1 - (1 - open) * (1 - open) * (1 - open);
        if (lastPoints >= 0 && data.masteryPoints() < lastPoints) upgradeFlashAt = now;
        lastPoints = data.masteryPoints();

        drawBackdrop(g, theme, light, t, ease);

        float mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        hoverNode = null;
        hoverButton = null;

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);

        drawHeader(g, def, data, theme, light, t, ease, mx, my);
        g.pose().pushPose();
        g.pose().translate(-(1 - ease) * 40, 0, 0);
        drawLeft(g, def, data, theme, light, ease);
        g.pose().popPose();
        drawCenter(g, stack, def, data, theme, light, t, ease, mx, my, partial);
        g.pose().pushPose();
        g.pose().translate((1 - ease) * 40, 0, 0);
        drawRight(g, def, data, theme, light, t, ease, mx, my, now);
        g.pose().popPose();
        drawFooter(g, theme, light, ease, mx, my);
        if (confirmOpen) drawConfirm(g, def, data, theme, light, t, mx, my);

        g.pose().popPose();

        String h = hoverNode != null ? "n:" + hoverNode : hoverButton != null ? "b:" + hoverButton : "";
        if (!h.isEmpty() && !h.equals(lastHoverSound)) playSound(ModSounds.UI_HOVER.get(), 1.4f, 0.35f);
        lastHoverSound = h;
    }

    private void drawBackdrop(GuiGraphics g, int theme, int light, float t, float ease) {
        // a dim, warm chamber: dark wash, a faint glow of the weapon's colour, heavy vignette and drifting embers
        UiDraw.vGradient(g, 0, 0, width, height, Colors.alpha(0.84f * ease, 0x140C07), Colors.alpha(0.92f * ease, 0x050302));
        UiDraw.glow(g, width / 2f, height * 0.45f, Math.max(width, height) * 1.1f, Colors.alpha(0.12f * ease, theme));
        UiDraw.glow(g, width / 2f, height * 0.45f, Math.max(width, height) * 0.7f, Colors.alpha(0.08f * ease, 0xFFB060));
        UiDraw.texture(g, FantasyWeapons.id("textures/gui/vignette.png"), 0, 0, width, height, Colors.alpha(0.9f * ease, 0x000000));
        for (int i = 0; i < 34; i++) {
            float seed = i * 12.9898f;
            float px = (float) ((Math.sin(seed) * 43758.5453) % 1 + 1) % 1;
            float sp = 0.015f + ((i * 7) % 10) / 400f;
            float py = 1 - ((t * sp + i * 0.137f) % 1f);
            float sway = (float) Math.sin(t * 0.7f + i) * 6 * scale;
            float s = 1.5f + (i % 3);
            UiDraw.glow(g, px * width + sway, py * height, s * 3 * scale, Colors.alpha(0.3f * ease * (float) Math.sin(py * Math.PI), i % 4 == 0 ? theme : 0xFFA850));
        }
    }

    private void drawHeader(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float ease, float mx, float my) {
        float slide = (1 - ease) * -20;
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);
        float cx = CW / 2;
        // the weapon's name on a ribbon banner, its epithet beneath
        String name = def.displayName();
        float nameW = UiDraw.font().width(name) * 1.5f;
        UiDraw.banner(g, cx, 1, nameW + 64, 24, ease);
        UiDraw.title(g, name, cx, 6.5f, Colors.alpha(ease, UiDraw.GOLD_LIGHT), 1.5f);
        UiDraw.textCentered(g, def.title(), cx, 26, Colors.alpha(ease * 0.9f, UiDraw.INK_MUTED), 0.62f, false);

        // weapon switch arrows (when several weapons are in the inventory)
        if (weaponSlots().size() > 1) {
            float aw = nameW / 2 + 38;
            button(g, "prev", cx - aw - 14, 6, 13, 13, "<", true, mx, my, 0.8f);
            button(g, "next", cx + aw + 1, 6, 13, 13, ">", true, mx, my, 0.8f);
        }

        // stats strip
        float y = 35;
        UiDraw.panel(g, 4, y, CW - 8, 15, theme, ease * 0.95f);
        long need = ProgressionMath.expToNext(data.level());
        WeaponForm form = def.form(data);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        float x = 11, ty = y + 4.5f;
        x = stat(g, x, ty, "Level", data.level() + " / " + ProgressionMath.maxLevel(), UiDraw.INK, ease);
        UiDraw.text(g, "Experience", x, ty, Colors.alpha(ease, UiDraw.INK_MUTED), 0.58f, false);
        float bx = x + UiDraw.font().width("Experience") * 0.58f + 4;
        UiDraw.bar(g, bx, y + 5f, 62, 4, need <= 0 ? 1 : data.exp() / (float) need, Colors.darken(theme, 0.2f), light, ease, t);
        UiDraw.textCentered(g, need <= 0 ? "Mastered" : String.format("%,d / %,d", data.exp(), need), bx + 31, y + 10.5f,
                Colors.alpha(ease * 0.85f, UiDraw.INK_MUTED), 0.4f, false);
        x = bx + 70;
        x = stat(g, x, ty, "Damage", String.format("%,.0f", dmg), 0xF0A080, ease);
        x = stat(g, x, ty, "Mastery", Math.round(ProgressionMath.mastery(def, data) * 100) + "%", UiDraw.ARCANE, ease);
        x = stat(g, x, ty, "Element", def.element().displayName(), def.element().primary(), ease);
        x = stat(g, x, ty, "Rarity", def.rarity().displayName(), def.rarity().color(), ease);
        if (form != null) stat(g, x, ty, UiDraw.titleCase(form.label()), form.displayName(), form.themePrimary(), ease);
        g.pose().popPose();
    }

    private float stat(GuiGraphics g, float x, float y, String label, String value, int color, float a) {
        UiDraw.text(g, label, x, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.58f, false);
        float lx = x + UiDraw.font().width(label) * 0.58f + 3;
        UiDraw.text(g, value, lx, y - 0.5f, Colors.alpha(a, color), 0.7f, true);
        return lx + UiDraw.font().width(value) * 0.7f + 9;
    }

    /** A small gold heading with a flourish under it. */
    private void heading(GuiGraphics g, String text, float x, float y, float w, float a) {
        UiDraw.text(g, text, x, y, Colors.alpha(a, UiDraw.GOLD), 0.62f, true);
        UiDraw.hGradient(g, x, y + 7, x + w, y + 7.6f, Colors.alpha(a * 0.8f, UiDraw.GOLD), Colors.alpha(0, UiDraw.GOLD));
    }

    // ---- left: selected ability details ----

    private void drawLeft(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float a) {
        UiDraw.panel(g, LEFT_X, PANEL_Y, LEFT_W, PANEL_H, theme, a);
        float x = LEFT_X + 8, y = PANEL_Y + 7, w = LEFT_W - 16;
        heading(g, CORE.equals(selected) ? "The Weapon" : "Selected Ability", x, y, w, a);
        y += 12;
        if (CORE.equals(selected)) {
            drawCoreInfo(g, def, data, x, y, w, theme, light, a);
            return;
        }
        AbilityDefinition ab = def.ability(selected);
        if (ab == null) return;
        int lvl = data.abilityLevel(ab);
        boolean unlocked = lvl > 0;
        UiDraw.glow(g, x + 13, y + 13, 40, Colors.alpha(a * (unlocked ? 0.4f : 0.1f), theme));
        UiDraw.texture(g, ab.icon(), x + 4, y + 4, 18, 18, Colors.alpha(a, unlocked ? 0xFFFFFF : 0x5A5048));
        if (!unlocked) UiDraw.texture(g, LOCK, x + 8, y + 8, 10, 10, Colors.alpha(a, 0xFFFFFF));
        UiDraw.medallion(g, x + 13, y + 13, 14, Colors.alpha(a, unlocked ? 0xFFFFFF : 0x9A9088));
        UiDraw.text(g, ab.name(), x + 31, y + 2, Colors.alpha(a, UiDraw.INK), 0.82f, true);
        int kindColor = ab.kind() == AbilityKind.ULTIMATE ? UiDraw.GOLD_LIGHT : ab.kind() == AbilityKind.PASSIVE ? UiDraw.GOOD : light;
        UiDraw.text(g, UiDraw.titleCase(ab.kind().displayName()), x + 31, y + 11.5f, Colors.alpha(a, kindColor), 0.58f, false);
        UiDraw.text(g, "Rank " + lvl + " of " + ab.maxLevel(), x + 31, y + 19, Colors.alpha(a, unlocked ? UiDraw.ARCANE : 0x8A7F70), 0.58f, false);
        y += 31;
        Component desc = Component.literal(ab.description()).withStyle(net.minecraft.ChatFormatting.ITALIC);
        int lines = UiDraw.wrap(g, desc, x, y, (int) w, Colors.alpha(a * 0.95f, 0xD8C9A8), 0.58f, 7);
        y += lines * 7 + 3;
        UiDraw.divider(g, x + w / 2, y + 2, w, a);
        y += 7;
        int shownLevel = Math.max(1, lvl);
        AbilityDefinition.StatContext sc = new AbilityDefinition.StatContext(ab, shownLevel, data.level(), ProgressionMath.weaponDamage(def, data));
        for (AbilityDefinition.StatLine line : ab.stats()) {
            if (y > PANEL_Y + PANEL_H - 27) break;
            UiDraw.text(g, UiDraw.titleCase(line.label()), x, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.58f, false);
            String v = line.value().apply(sc);
            UiDraw.textRight(g, v, x + w, y - 0.5f, Colors.alpha(a, UiDraw.INK), 0.66f, true);
            y += 9;
        }
        float fy = PANEL_Y + PANEL_H - 20;
        UiDraw.text(g, "Unlocked at", x, fy, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
        UiDraw.textRight(g, "Level " + ab.unlockLevel(), x + w, fy, Colors.alpha(a, unlocked ? UiDraw.GOOD : UiDraw.BAD), 0.6f, false);
        fy += 8;
        UiDraw.text(g, "Next rank", x, fy, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
        String next = lvl >= ab.maxLevel() ? "Mastered" : "Level " + ab.weaponLevelFor(Math.max(1, lvl) + (unlocked ? 1 : 0));
        UiDraw.textRight(g, next, x + w, fy, Colors.alpha(a, lvl >= ab.maxLevel() ? UiDraw.GOLD_LIGHT : UiDraw.INK), 0.6f, false);
    }

    private void drawCoreInfo(GuiGraphics g, WeaponDefinition def, WeaponData data, float x, float y, float w, int theme, int light, float a) {
        UiDraw.glow(g, x + 13, y + 13, 40, Colors.alpha(a * 0.4f, theme));
        UiDraw.diamond(g, x + 13, y + 13, 6, Colors.alpha(a, UiDraw.GOLD));
        UiDraw.diamond(g, x + 13, y + 13, 3.5f, Colors.alpha(a, theme));
        UiDraw.medallion(g, x + 13, y + 13, 14, Colors.alpha(a, 0xFFFFFF));
        UiDraw.text(g, "Basic Attacks", x + 31, y + 2, Colors.alpha(a, UiDraw.INK), 0.82f, true);
        UiDraw.text(g, UiDraw.titleCase(def.weaponClass().displayName()), x + 31, y + 11.5f, Colors.alpha(a, light), 0.58f, false);
        y += 31;
        Component lore = Component.literal(def.lore().isEmpty() ? def.title() : "\u201C" + def.lore() + "\u201D").withStyle(net.minecraft.ChatFormatting.ITALIC);
        int lines = UiDraw.wrap(g, lore, x, y, (int) w, Colors.alpha(a * 0.95f, 0xD8C9A8), 0.5f, 6);
        y += lines * 6 + 1;
        if (!def.loreSource().isEmpty()) {
            UiDraw.textRight(g, "\u2014 " + def.loreSource(), x + w, y, Colors.alpha(a * 0.9f, 0x9C8862), 0.45f, false);
            y += 6;
        }
        UiDraw.divider(g, x + w / 2, y + 2, w, a);
        y += 7;
        WeaponForm form = def.form(data);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        String[][] rows = {
                {"Damage", String.format("%,.0f", dmg)},
                {"Heavy (sneak)", String.format("%,.0f", dmg * def.heavyMultiplier())},
                {"Critical chance", Math.round(def.critChance() * 100) + "%"},
                {"Critical damage", "x" + def.critMultiplier()},
                {"Attack speed", String.format("%.2f/s", def.weaponClass().attackSpeed() * (form != null ? form.attackSpeedFactor() : 1f))},
                {"Cleave arc", def.weaponClass().cleaveAngle() <= 0 ? "Pierce" : Math.round(def.weaponClass().cleaveAngle()) + "°"},
                {"Lifesteal", def.lifesteal() > 0 ? Math.round(def.lifesteal() * 100) + "%" : "—"},
                {"Foes slain", String.valueOf(data.kills())},
        };
        for (String[] r : rows) {
            if (y > PANEL_Y + PANEL_H - 27) break;
            UiDraw.text(g, r[0], x, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.58f, false);
            UiDraw.textRight(g, r[1], x + w, y - 0.5f, Colors.alpha(a, UiDraw.INK), 0.66f, true);
            y += 9;
        }
        float fy = PANEL_Y + PANEL_H - 20;
        float m = ProgressionMath.mastery(def, data);
        MasteryBonus.Milestone next = null;
        for (MasteryBonus.Milestone ms : MasteryBonus.MILESTONES) if (m + 1e-4f < ms.threshold()) {
            next = ms;
            break;
        }
        UiDraw.text(g, "Next mastery", x, fy, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
        UiDraw.textRight(g, next == null ? "Awakened" : Math.round(next.threshold() * 100) + "%", x + w, fy, Colors.alpha(a, UiDraw.ARCANE), 0.6f, false);
        if (next != null) UiDraw.text(g, next.description(), x, fy + 8, Colors.alpha(a * 0.9f, 0xD8C9A8), 0.52f, false);
    }

    // ---- centre: model + ability tree ----

    private void drawCenter(GuiGraphics g, ItemStack stack, WeaponDefinition def, WeaponData data, int theme, int light, float t, float a,
                            float mx, float my, float partial) {
        UiDraw.panel(g, MID_X, PANEL_Y, MID_W, PANEL_H, theme, a);
        float cx = MID_X + MID_W / 2;
        // model preview
        float py = PANEL_Y + 4, ph = TREE_Y - PANEL_Y - 8;
        UiDraw.glow(g, cx, py + ph / 2, 120, Colors.alpha(0.45f * a, theme));
        UiDraw.additive(g, VfxTextures.RUNE_CIRCLE, cx - 34, py + ph / 2 - 34, 68, 68, Colors.alpha(0.18f * a, light));
        drawModel(g, stack, cx, py + ph / 2, ph, t, partial);
        if (!dragging) modelYaw += 0.35f;
        UiDraw.text(g, "Mastery Points", MID_X + 8, py + 3, Colors.alpha(a, UiDraw.INK_MUTED), 0.5f, false);
        UiDraw.texture(g, GEM, MID_X + 8, py + 9, 9, 9, Colors.alpha(a, 0xFFFFFF));
        UiDraw.text(g, String.valueOf(data.masteryPoints()), MID_X + 19, py + 10, Colors.alpha(a, UiDraw.ARCANE), 0.8f, true);
        UiDraw.textRight(g, "Drag to turn", MID_X + MID_W - 8, py + 3, Colors.alpha(a * 0.6f, UiDraw.INK_MUTED), 0.45f, false);

        UiDraw.divider(g, cx, TREE_Y - 1, MID_W - 16, a);
        UiDraw.textCentered(g, "Ability Tree", cx, TREE_Y + 3, Colors.alpha(a, UiDraw.GOLD), 0.58f, true);
        drawTree(g, def, data, theme, light, t, a, mx, my);
    }

    private void drawModel(GuiGraphics g, ItemStack stack, float cx, float cy, float size, float t, float partial) {
        Minecraft mc = Minecraft.getInstance();
        g.flush();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 120);
        g.pose().scale(size * 0.95f, -size * 0.95f, size * 0.95f);
        g.pose().mulPose(Axis.YP.rotationDegrees(modelYaw));
        g.pose().mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.8) * 4));
        Lighting.setupForEntityInInventory();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g.pose(),
                g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    private record Node(String id, float x, float y, @Nullable AbilityDefinition ability) {
    }

    private List<Node> layoutNodes(WeaponDefinition def) {
        List<Node> nodes = new ArrayList<>();
        float cx = MID_X + MID_W / 2;
        float colW = 28;
        float top = TREE_Y + 16;
        int maxRow = 0;
        for (AbilityDefinition a : def.abilities()) maxRow = Math.max(maxRow, a.nodeY() + 1);
        float rowH = Math.min(26, (PANEL_Y + PANEL_H - 12 - top) / Math.max(1, maxRow));
        nodes.add(new Node(CORE, cx, top, null));
        for (AbilityDefinition a : def.abilities()) {
            nodes.add(new Node(a.id(), cx + (a.nodeX() - 2) * colW, top + (a.nodeY() + 1) * rowH, a));
        }
        return nodes;
    }

    private void drawTree(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float a, float mx, float my) {
        List<Node> nodes = layoutNodes(def);
        if (def.abilities().isEmpty()) {
            UiDraw.textCentered(g, "No abilities yet", MID_X + MID_W / 2, TREE_Y + 50, Colors.alpha(a * 0.8f, UiDraw.INK_MUTED), 0.6f, false);
        }
        // connections: gold where the path is open, dull bronze where it is still sealed
        for (Node n : nodes) {
            if (n.ability() == null) continue;
            List<String> parents = n.ability().parents().isEmpty() ? List.of(CORE) : n.ability().parents();
            for (String pid : parents) {
                Node pn = nodes.stream().filter(o -> o.id().equals(pid)).findFirst().orElse(null);
                if (pn == null) continue;
                boolean lit = data.isUnlocked(n.ability()) && (pn.ability() == null || data.isUnlocked(pn.ability()));
                UiDraw.line(g, pn.x(), pn.y(), n.x(), n.y(), lit ? 2.6f : 2f, Colors.alpha(a, 0x1A0E06), Colors.alpha(a, 0x1A0E06));
                int c = lit ? Colors.alpha(a, UiDraw.GOLD) : Colors.alpha(a, 0x5A4630);
                UiDraw.line(g, pn.x(), pn.y(), n.x(), n.y(), lit ? 1.3f : 0.9f, c, c);
                if (lit) {
                    float f = (t * 0.5f + (n.x() + n.y()) * 0.01f) % 1f;
                    UiDraw.glow(g, pn.x() + (n.x() - pn.x()) * f, pn.y() + (n.y() - pn.y()) * f, 7, Colors.alpha(a * 0.8f, UiDraw.GOLD_LIGHT));
                }
            }
        }
        // nodes: medallions with the ability's icon set inside
        for (Node n : nodes) {
            boolean hover = Math.abs(mx - n.x()) < 8 && Math.abs(my - n.y()) < 8 && !confirmOpen;
            if (hover) hoverNode = n.id();
            boolean sel = n.id().equals(selected);
            if (n.ability() == null) {
                float r = 8 + (hover ? 1 : 0);
                UiDraw.glow(g, n.x(), n.y(), 24, Colors.alpha(a * (sel ? 0.7f : 0.35f), theme));
                UiDraw.diamond(g, n.x(), n.y(), 4.5f, Colors.alpha(a, UiDraw.GOLD));
                UiDraw.diamond(g, n.x(), n.y(), 2.5f, Colors.alpha(a, theme));
                UiDraw.medallion(g, n.x(), n.y(), r, Colors.alpha(a, sel ? 0xFFF4D0 : 0xFFFFFF));
                continue;
            }
            AbilityDefinition ab = n.ability();
            int lvl = data.abilityLevel(ab);
            boolean unlocked = lvl > 0;
            boolean maxed = unlocked && lvl >= ab.maxLevel();
            boolean available = AbilityService.upgradeDenial(data, ab) == null;
            float r = 8.5f + (hover ? 1f : 0) + (sel ? 0.5f : 0);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4 + n.x());
            float glowA = !unlocked ? 0f : maxed ? 0.55f : available ? 0.3f + 0.3f * pulse : 0.25f;
            if (glowA > 0) UiDraw.glow(g, n.x(), n.y(), r * 3.2f, Colors.alpha(a * glowA, maxed ? UiDraw.GOLD_LIGHT : theme));
            UiDraw.rect(g, n.x() - r * 0.72f, n.y() - r * 0.72f, n.x() + r * 0.72f, n.y() + r * 0.72f, Colors.alpha(a, 0x0C0704));
            float ic = r * 1.25f;
            UiDraw.texture(g, ab.icon(), n.x() - ic / 2, n.y() - ic / 2, ic, ic, Colors.alpha(a * (unlocked ? 1f : 0.6f), unlocked ? 0xFFFFFF : 0x4A4038));
            if (!unlocked) UiDraw.texture(g, LOCK, n.x() - 3.5f, n.y() - 3.5f, 7, 7, Colors.alpha(a * 0.95f, 0xFFFFFF));
            int rim = sel ? 0xFFF4D0 : maxed ? 0xFFE7A8 : unlocked ? 0xFFFFFF : 0x8A8078;
            UiDraw.medallion(g, n.x(), n.y(), r, Colors.alpha(a, rim));
            if (sel) UiDraw.glow(g, n.x(), n.y(), r * 2.6f, Colors.alpha(a * 0.35f, UiDraw.GOLD_LIGHT));
            // rank pips
            float pw = ab.maxLevel() * 3f;
            for (int i = 0; i < ab.maxLevel(); i++) {
                float px = n.x() - pw / 2 + i * 3f + 0.5f;
                UiDraw.diamond(g, px + 1, n.y() + r + 2.5f, 1.2f, Colors.alpha(a, i < lvl ? (maxed ? UiDraw.GOLD_LIGHT : UiDraw.GOLD) : 0x4A3A28));
            }
            if (hover) {
                String label = ab.name() + (unlocked ? "  ·  Rank " + lvl : "  ·  Level " + ab.unlockLevel());
                float lw = UiDraw.font().width(label) * 0.55f + 10;
                float lx = Math.max(MID_X + 2, Math.min(MID_X + MID_W - lw - 2, n.x() - lw / 2));
                float ly = n.y() - r - 13;
                UiDraw.panel(g, lx, ly, lw, 10, theme, 1f);
                UiDraw.text(g, label, lx + 5, ly + 2.5f, Colors.alpha(1f, UiDraw.INK), 0.55f, false);
            }
        }
    }

    // ---- right: upgrade panel ----

    private void drawRight(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float a, float mx, float my, long now) {
        UiDraw.panel(g, RIGHT_X, PANEL_Y, RIGHT_W, PANEL_H, theme, a);
        float flash = Math.max(0, 1 - (now - upgradeFlashAt) / 700f);
        if (flash > 0) UiDraw.glow(g, RIGHT_X + RIGHT_W / 2, PANEL_Y + PANEL_H / 2, RIGHT_W * 1.6f, Colors.alpha(a * flash * 0.5f, UiDraw.GOLD_LIGHT));
        float x = RIGHT_X + 8, y = PANEL_Y + 7, w = RIGHT_W - 16;
        heading(g, "Empower", x, y, w, a);
        y += 13;
        AbilityDefinition ab = CORE.equals(selected) ? null : def.ability(selected);
        if (ab == null) {
            Component tip = Component.literal("Each level makes every blow stronger. Spend Mastery Points on the abilities in the tree to empower them.")
                    .withStyle(net.minecraft.ChatFormatting.ITALIC);
            int n = UiDraw.wrap(g, tip, x, y, (int) w, Colors.alpha(a * 0.95f, 0xD8C9A8), 0.58f, 7);
            y += n * 7 + 6;
            UiDraw.text(g, "Mastery Milestones", x, y, Colors.alpha(a, UiDraw.GOLD), 0.58f, true);
            y += 10;
            for (MasteryBonus.Milestone ms : MasteryBonus.MILESTONES) {
                boolean got = ProgressionMath.mastery(def, data) + 1e-4f >= ms.threshold();
                UiDraw.text(g, Math.round(ms.threshold() * 100) + "%", x, y, Colors.alpha(a, got ? UiDraw.GOLD_LIGHT : 0x7A6E60), 0.58f, false);
                UiDraw.wrap(g, ms.description(), x + 18, y, (int) w - 18, Colors.alpha(a, got ? UiDraw.INK : 0x8A7F70), 0.54f, 7);
                y += 16;
            }
            return;
        }
        int lvl = data.abilityLevel(ab);
        boolean unlocked = lvl > 0;
        boolean maxed = lvl >= ab.maxLevel();
        UiDraw.text(g, ab.name(), x, y, Colors.alpha(a, UiDraw.INK), 0.82f, true);
        y += 12;
        // current → next rank
        UiDraw.text(g, "Rank", x, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.54f, false);
        UiDraw.textRight(g, "Next", x + w, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.54f, false);
        y += 7;
        UiDraw.title(g, String.valueOf(lvl), x + 12, y, Colors.alpha(a, unlocked ? UiDraw.GOLD : 0x7A6E60), 1.5f);
        UiDraw.title(g, maxed ? "—" : String.valueOf(lvl + 1), x + w - 12, y, Colors.alpha(a, maxed ? 0x7A6E60 : UiDraw.GOLD_LIGHT), 1.5f);
        UiDraw.divider(g, x + w / 2, y + 6, w * 0.45f, a * (0.6f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 4))));
        y += 18;
        if (!maxed) {
            UiDraw.text(g, unlocked ? "Next rank grants" : "To unlock", x, y, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
            y += 8;
            List<String> lines = unlocked ? ab.upgradeLines(lvl + 1) : List.of("Reach weapon level " + ab.unlockLevel(), "Then empower it with Mastery Points");
            for (String line : lines) {
                boolean bonus = line.startsWith("+") || line.startsWith("-");
                UiDraw.text(g, bonus ? UiDraw.titleCase(line) : line, x + 2, y, Colors.alpha(a, bonus ? UiDraw.GOOD : UiDraw.GOLD_LIGHT), 0.56f, false);
                y += 8;
            }
        } else {
            UiDraw.title(g, "Fully Mastered", x + w / 2, y + 2, Colors.alpha(a, UiDraw.GOLD_LIGHT), 0.95f);
            y += 14;
        }

        // cost
        float cy = PANEL_Y + PANEL_H - 70;
        if (!maxed && unlocked) {
            int cost = ab.costFor(lvl + 1);
            boolean afford = data.masteryPoints() >= cost;
            UiDraw.text(g, "Cost", x, cy, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
            UiDraw.texture(g, GEM, x + 32, cy - 1.5f, 9, 9, Colors.alpha(a, 0xFFFFFF));
            UiDraw.text(g, cost + " Mastery Point" + (cost > 1 ? "s" : ""), x + 43, cy, Colors.alpha(a, afford ? UiDraw.ARCANE : UiDraw.BAD), 0.58f, false);
            int need = ab.weaponLevelFor(lvl + 1);
            UiDraw.text(g, "Requires", x, cy + 9, Colors.alpha(a, UiDraw.INK_MUTED), 0.56f, false);
            UiDraw.text(g, "Weapon level " + need, x + 32, cy + 9, Colors.alpha(a, data.level() >= need ? UiDraw.GOOD : UiDraw.BAD), 0.58f, false);
        }
        // buttons: empower one rank, empower as far as the points and the weapon's level allow, bind to the ability key
        String denial = AbilityService.upgradeDenial(data, ab);
        String label = denial == null ? "Empower" : maxed ? "Mastered" : !unlocked ? "Sealed" : denial.startsWith("Insufficient") ? "Need Points"
                : "Level " + ab.weaponLevelFor(lvl + 1);
        float by = PANEL_Y + PANEL_H - 50;
        button(g, "upgrade", x, by, w, 15, label, denial == null && !confirmOpen, mx, my, 0.8f);
        int[] plan = AbilityService.maxUpgrade(data, ab);
        boolean canMax = plan[0] >= 2 && !confirmOpen;
        button(g, "upgrade_max", x, by + 17, w, 12, plan[0] >= 2 ? "Empower to Rank " + (lvl + plan[0]) + "  (" + plan[1] + " pts)" : "Empower to Max",
                canMax, mx, my, 0.55f);
        if (denial != null && !maxed) {
            UiDraw.textCentered(g, denial, x + w / 2, by - 8, Colors.alpha(a * 0.9f, UiDraw.BAD), 0.5f, false);
        }
        if (ab.kind().castable() && unlocked) {
            boolean active = ab == AbilityService.selected(def, data);
            button(g, "select", x, PANEL_Y + PANEL_H - 16, w, 10, active ? "Bound to " + keyName() : "Bind to " + keyName(), !active && !confirmOpen, mx, my,
                    0.5f);
        }
    }

    private static String keyName() {
        return com.fantasyweapons.client.input.KeyBindings.ABILITY.getTranslatedKeyMessage().getString().toUpperCase();
    }

    private void drawFooter(GuiGraphics g, int theme, int light, float a, float mx, float my) {
        button(g, "close", CW / 2 - 32, CH - 21, 64, 14, "Close", !confirmOpen, mx, my, 0.75f);
        UiDraw.textCentered(g, "Slay foes with this weapon to gain experience; every level grants Mastery Points", CW / 2, CH - 5,
                Colors.alpha(a * 0.75f, UiDraw.INK_MUTED), 0.48f, false);
    }

    private void drawConfirm(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float mx, float my) {
        AbilityDefinition ab = def.ability(selected);
        if (ab == null) {
            confirmOpen = false;
            return;
        }
        int lvl = data.abilityLevel(ab);
        UiDraw.rect(g, -ox / scale, -oy / scale, (width - ox) / scale, (height - oy) / scale, 0xA0000000);
        float w = 200, h = 74, x = CW / 2 - w / 2, y = CH / 2 - h / 2;
        UiDraw.panel(g, x, y, w, h, theme, 1f);
        UiDraw.title(g, "Empower " + ab.name() + "?", CW / 2, y + 9, Colors.alpha(1, UiDraw.GOLD_LIGHT), 0.9f);
        UiDraw.divider(g, CW / 2, y + 22, w - 40, 1f);
        UiDraw.textCentered(g, "Raise it to rank " + (lvl + 1) + " for " + ab.costFor(lvl + 1) + " Mastery Point" + (ab.costFor(lvl + 1) > 1 ? "s" : "") + ".",
                CW / 2, y + 30, Colors.alpha(1, UiDraw.INK), 0.62f, false);
        button(g, "confirm", x + 16, y + h - 24, 76, 15, "Empower", true, mx, my, 0.75f);
        button(g, "cancel", x + w - 92, y + h - 24, 76, 15, "Cancel", true, mx, my, 0.75f);
    }

    private void button(GuiGraphics g, String id, float x, float y, float w, float h, String label, boolean enabled, float mx, float my, float textScale) {
        boolean hover = enabled && mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (hover && (!confirmOpen || id.equals("confirm") || id.equals("cancel"))) hoverButton = id;
        UiDraw.button(g, x, y, w, h, !enabled ? 2 : hover ? 1 : 0, 1f);
        int ink = !enabled ? 0x8A8070 : hover ? 0x2A1606 : UiDraw.GOLD_LIGHT;
        float ty = y + (h - 8 * textScale) / 2f + 0.5f;
        if (hover) UiDraw.textCentered(g, label, x + w / 2, ty, Colors.alpha(1, ink), textScale, false);
        else UiDraw.title(g, label, x + w / 2, ty, Colors.alpha(1, ink), textScale);
    }

    // ------------------------------------------------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ItemStack stack = stack();
        if (stack.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        if (button == 0) {
            if (hoverButton != null) {
                playSound(ModSounds.UI_CLICK.get(), 1f, 0.7f);
                handleButton(hoverButton, def, data);
                return true;
            }
            if (!confirmOpen && hoverNode != null) {
                selected = hoverNode;
                playSound(ModSounds.UI_CLICK.get(), 1.2f, 0.6f);
                return true;
            }
            float mx = (float) (mouseX - ox) / scale, my = (float) (mouseY - oy) / scale;
            if (!confirmOpen && mx >= MID_X && mx <= MID_X + MID_W && my >= PANEL_Y && my <= TREE_Y) {
                dragging = true;
                lastDragX = mouseX;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleButton(String id, WeaponDefinition def, WeaponData data) {
        AbilityDefinition ab = def.ability(selected);
        switch (id) {
            case "upgrade" -> {
                if (ab == null || AbilityService.upgradeDenial(data, ab) != null) return;
                if (ClientConfig.get(ClientConfig.CONFIRM_UPGRADES, true)) confirmOpen = true;
                else sendUpgrade(data, ab);
            }
            case "confirm" -> {
                confirmOpen = false;
                if (ab != null) sendUpgrade(data, ab);
            }
            case "cancel" -> confirmOpen = false;
            case "upgrade_max" -> {
                if (ab != null && AbilityService.maxUpgrade(data, ab)[0] > 0) {
                    PacketDistributor.sendToServer(new C2SPayloads.UpgradeAbility(slot, data.idOrNil(), ab.id(), true));
                }
            }
            case "select" -> {
                if (ab != null) PacketDistributor.sendToServer(new C2SPayloads.SelectAbility(slot, data.idOrNil(), ab.id()));
            }
            case "close" -> onClose();
            case "prev", "next" -> {
                List<Integer> slots = weaponSlots();
                int i = slots.indexOf(slot);
                if (!slots.isEmpty()) slot = slots.get(Math.floorMod(i + (id.equals("next") ? 1 : -1), slots.size()));
                selected = CORE;
            }
            default -> {
            }
        }
    }

    private void sendUpgrade(WeaponData data, AbilityDefinition ab) {
        PacketDistributor.sendToServer(new C2SPayloads.UpgradeAbility(slot, data.idOrNil(), ab.id(), false));
    }

    private List<Integer> weaponSlots() {
        List<Integer> out = new ArrayList<>();
        Player p = Minecraft.getInstance().player;
        if (p == null) return out;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof FantasyWeaponItem) out.add(i);
        }
        return out;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            modelYaw += (float) (mouseX - lastDragX) * 1.2f / scale;
            lastDragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmOpen && keyCode == 256) {
            confirmOpen = false;
            return true;
        }
        if (com.fantasyweapons.client.input.KeyBindings.MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void playSound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** For tests / screenshots: select a node programmatically. */
    public void select(String abilityId) {
        this.selected = abilityId;
    }
}
