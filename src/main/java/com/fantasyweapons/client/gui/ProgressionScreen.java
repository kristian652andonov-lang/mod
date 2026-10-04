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
    private static final ResourceLocation GRID = FantasyWeapons.id("textures/gui/grid.png");
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");
    private static final ResourceLocation GEM = FantasyWeapons.id("textures/gui/mastery_point.png");
    private static final String CORE = "#core";

    // design canvas
    private static final float CW = 440, CH = 266;
    private static final float LEFT_X = 4, LEFT_W = 134;
    private static final float MID_X = 142, MID_W = 156;
    private static final float RIGHT_X = 302, RIGHT_W = 134;
    private static final float PANEL_Y = 50, PANEL_H = 192;
    private static final float TREE_Y = 108;

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
        UiDraw.vGradient(g, 0, 0, width, height, Colors.alpha(0.82f * ease, Colors.darken(theme, 0.93f)), Colors.alpha(0.9f * ease, 0x030208));
        // scrolling holographic grid
        float gs = 32 * scale;
        float off = (t * 4) % gs;
        RenderSystem.enableBlend();
        for (float y = -gs + off; y < height; y += gs) {
            for (float x = -gs + off * 0.5f; x < width; x += gs) {
                UiDraw.texture(g, GRID, x, y, gs, gs, Colors.alpha(0.07f * ease, theme));
            }
        }
        UiDraw.glow(g, width / 2f, height * 0.42f, Math.max(width, height) * 1.1f, Colors.alpha(0.22f * ease, theme));
        UiDraw.texture(g, FantasyWeapons.id("textures/gui/vignette.png"), 0, 0, width, height, Colors.alpha(0.85f * ease, 0x000000));
        // drifting motes
        for (int i = 0; i < 40; i++) {
            float seed = i * 12.9898f;
            float px = (float) ((Math.sin(seed) * 43758.5453) % 1 + 1) % 1;
            float sp = 0.02f + ((i * 7) % 10) / 300f;
            float py = 1 - ((t * sp + i * 0.137f) % 1f);
            float s = 2 + (i % 4);
            UiDraw.glow(g, px * width, py * height, s * 3 * scale, Colors.alpha(0.35f * ease * (float) Math.sin(py * Math.PI), i % 3 == 0 ? light : theme));
        }
    }

    private void drawHeader(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float ease, float mx, float my) {
        float slide = (1 - ease) * -20;
        g.pose().pushPose();
        g.pose().translate(0, slide, 0);
        float cx = CW / 2;
        UiDraw.glow(g, cx, 14, 220, Colors.alpha(0.3f * ease, theme));
        UiDraw.title(g, def.displayName().toUpperCase(), cx, 3, Colors.alpha(ease, def.rarity().color()), 1.7f);
        UiDraw.textCentered(g, def.title(), cx, 19, Colors.alpha(ease * 0.85f, light), 0.62f, false);

        // weapon switch arrows (when several weapons are in the inventory)
        if (weaponSlots().size() > 1) {
            float aw = UiDraw.font().width(def.displayName().toUpperCase()) * 1.7f / 2 + 12;
            button(g, "prev", cx - aw - 10, 3, 10, 13, "◀", theme, light, true, mx, my, 0.9f);
            button(g, "next", cx + aw, 3, 10, 13, "▶", theme, light, true, mx, my, 0.9f);
        }

        // stats strip
        float y = 29;
        UiDraw.panel(g, 4, y, CW - 8, 16, theme, ease * 0.9f);
        long need = ProgressionMath.expToNext(data.level());
        WeaponForm form = def.form(data);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        float x = 10;
        x = stat(g, x, y + 4.5f, "LEVEL", data.level() + " / " + ProgressionMath.maxLevel(), 0xFFFFFF, ease);
        UiDraw.text(g, "EXP", x, y + 4.5f, Colors.alpha(ease * 0.7f, 0xB0A8C0), 0.6f, false);
        float bx = x + 16;
        UiDraw.bar(g, bx, y + 5.5f, 70, 4, need <= 0 ? 1 : data.exp() / (float) need, theme, light, ease, t);
        UiDraw.textCentered(g, need <= 0 ? "MAX" : String.format("%,d / %,d", data.exp(), need), bx + 35, y + 10.5f, Colors.alpha(ease * 0.8f, 0xD0C8E0), 0.42f, false);
        x = bx + 78;
        x = stat(g, x, y + 4.5f, "DAMAGE", String.format("%,.0f", dmg), 0xFF8A7A, ease);
        x = stat(g, x, y + 4.5f, "MASTERY", Math.round(ProgressionMath.mastery(def, data) * 100) + "%", 0xC9A2FF, ease);
        x = stat(g, x, y + 4.5f, "ELEMENT", def.element().displayName().toUpperCase(), def.element().primary(), ease);
        x = stat(g, x, y + 4.5f, "RARITY", def.rarity().displayName().toUpperCase(), def.rarity().color(), ease);
        if (form != null) stat(g, x, y + 4.5f, form.label(), form.displayName().toUpperCase(), form.themePrimary(), ease);
        g.pose().popPose();
    }

    private float stat(GuiGraphics g, float x, float y, String label, String value, int color, float a) {
        UiDraw.text(g, label, x, y, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.6f, false);
        float lx = x + UiDraw.font().width(label) * 0.6f + 3;
        UiDraw.text(g, value, lx, y - 0.5f, Colors.alpha(a, color), 0.72f, true);
        return lx + UiDraw.font().width(value) * 0.72f + 9;
    }

    // ---- left: selected ability details ----

    private void drawLeft(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float a) {
        UiDraw.panel(g, LEFT_X, PANEL_Y, LEFT_W, PANEL_H, theme, a);
        float x = LEFT_X + 7, y = PANEL_Y + 6, w = LEFT_W - 14;
        UiDraw.text(g, "SELECTED", x, y, Colors.alpha(a * 0.6f, light), 0.55f, false);
        y += 9;
        if (CORE.equals(selected)) {
            drawCoreInfo(g, def, data, x, y, w, theme, light, a);
            return;
        }
        AbilityDefinition ab = def.ability(selected);
        if (ab == null) return;
        int lvl = data.abilityLevel(ab);
        boolean unlocked = lvl > 0;
        UiDraw.glow(g, x + 13, y + 13, 46, Colors.alpha(a * (unlocked ? 0.55f : 0.15f), theme));
        UiDraw.texture(g, ab.icon(), x, y, 26, 26, Colors.alpha(a, unlocked ? 0xFFFFFF : 0x55505F));
        if (!unlocked) UiDraw.texture(g, LOCK, x + 7, y + 7, 12, 12, Colors.alpha(a, 0xFFFFFF));
        UiDraw.border(g, x - 1, y - 1, x + 27, y + 27, 1, Colors.alpha(a, unlocked ? light : 0x55505F));
        UiDraw.text(g, ab.name().toUpperCase(), x + 31, y + 1, Colors.alpha(a, 0xFFFFFF), 0.85f, true);
        int kindColor = ab.kind() == AbilityKind.ULTIMATE ? 0xFFD84A : ab.kind() == AbilityKind.PASSIVE ? 0x7CFFB2 : light;
        UiDraw.text(g, ab.kind().displayName().toUpperCase(), x + 31, y + 11, Colors.alpha(a, kindColor), 0.58f, false);
        UiDraw.text(g, "LEVEL " + lvl + " / " + ab.maxLevel(), x + 31, y + 19, Colors.alpha(a, unlocked ? 0xC9A2FF : 0x77727F), 0.62f, false);
        y += 32;
        int lines = UiDraw.wrap(g, "\"" + ab.description() + "\"", x, y, (int) w, Colors.alpha(a * 0.9f, 0xD6CFE3), 0.6f, 7);
        y += lines * 7 + 5;
        UiDraw.hGradient(g, x, y, x + w, y + 1, Colors.alpha(a * 0.6f, theme), Colors.alpha(0, theme));
        y += 4;
        int shownLevel = Math.max(1, lvl);
        AbilityDefinition.StatContext sc = new AbilityDefinition.StatContext(ab, shownLevel, data.level(), ProgressionMath.weaponDamage(def, data));
        for (AbilityDefinition.StatLine line : ab.stats()) {
            if (y > PANEL_Y + PANEL_H - 26) break;
            UiDraw.text(g, line.label(), x, y, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.6f, false);
            String v = line.value().apply(sc);
            UiDraw.textRight(g, v, x + w, y - 0.5f, Colors.alpha(a, 0xFFFFFF), 0.68f, true);
            y += 9;
        }
        float fy = PANEL_Y + PANEL_H - 19;
        UiDraw.text(g, "UNLOCKED AT", x, fy, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
        UiDraw.textRight(g, "LEVEL " + ab.unlockLevel(), x + w, fy, Colors.alpha(a, unlocked ? 0x7CFFB2 : 0xFF6B7E), 0.62f, false);
        fy += 8;
        UiDraw.text(g, "NEXT UPGRADE", x, fy, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
        String next = lvl >= ab.maxLevel() ? "MAXED" : "LEVEL " + ab.weaponLevelFor(Math.max(1, lvl) + (unlocked ? 1 : 0));
        UiDraw.textRight(g, next, x + w, fy, Colors.alpha(a, lvl >= ab.maxLevel() ? 0xFFD84A : 0xFFFFFF), 0.62f, false);
    }

    private void drawCoreInfo(GuiGraphics g, WeaponDefinition def, WeaponData data, float x, float y, float w, int theme, int light, float a) {
        UiDraw.glow(g, x + 13, y + 13, 46, Colors.alpha(a * 0.55f, theme));
        UiDraw.diamond(g, x + 13, y + 13, 11, Colors.alpha(a, light));
        UiDraw.diamond(g, x + 13, y + 13, 7, Colors.alpha(a, theme));
        UiDraw.text(g, "BASIC ATTACKS", x + 31, y + 1, Colors.alpha(a, 0xFFFFFF), 0.85f, true);
        UiDraw.text(g, def.weaponClass().displayName().toUpperCase(), x + 31, y + 11, Colors.alpha(a, light), 0.58f, false);
        y += 32;
        int lines = UiDraw.wrap(g, def.lore().isEmpty() ? def.title() : def.lore(), x, y, (int) w, Colors.alpha(a * 0.85f, 0xD6CFE3), 0.6f, 7);
        y += lines * 7 + 5;
        UiDraw.hGradient(g, x, y, x + w, y + 1, Colors.alpha(a * 0.6f, theme), Colors.alpha(0, theme));
        y += 4;
        WeaponForm form = def.form(data);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        String[][] rows = {
                {"DAMAGE", String.format("%,.0f", dmg)},
                {"HEAVY (SNEAK)", String.format("%,.0f", dmg * def.heavyMultiplier())},
                {"CRIT CHANCE", Math.round(def.critChance() * 100) + "%"},
                {"CRIT DAMAGE", "x" + def.critMultiplier()},
                {"ATTACK SPEED", String.format("%.2f/s", def.weaponClass().attackSpeed() * (form != null ? form.attackSpeedFactor() : 1f))},
                {"CLEAVE ARC", def.weaponClass().cleaveAngle() <= 0 ? "PIERCE" : Math.round(def.weaponClass().cleaveAngle()) + "°"},
                {"LIFESTEAL", def.lifesteal() > 0 ? Math.round(def.lifesteal() * 100) + "%" : "—"},
                {"KILLS", String.valueOf(data.kills())},
        };
        for (String[] r : rows) {
            UiDraw.text(g, r[0], x, y, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.6f, false);
            UiDraw.textRight(g, r[1], x + w, y - 0.5f, Colors.alpha(a, 0xFFFFFF), 0.68f, true);
            y += 9;
        }
        float fy = PANEL_Y + PANEL_H - 19;
        float m = ProgressionMath.mastery(def, data);
        MasteryBonus.Milestone next = null;
        for (MasteryBonus.Milestone ms : MasteryBonus.MILESTONES) if (m + 1e-4f < ms.threshold()) {
            next = ms;
            break;
        }
        UiDraw.text(g, "NEXT MASTERY", x, fy, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
        UiDraw.textRight(g, next == null ? "AWAKENED" : Math.round(next.threshold() * 100) + "%", x + w, fy, Colors.alpha(a, 0xC9A2FF), 0.62f, false);
        if (next != null) UiDraw.text(g, next.description(), x, fy + 8, Colors.alpha(a * 0.85f, 0xD6CFE3), 0.55f, false);
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
        UiDraw.text(g, "MASTERY POINTS", MID_X + 6, py + 1, Colors.alpha(a * 0.65f, 0xB0A8C0), 0.5f, false);
        UiDraw.texture(g, GEM, MID_X + 6, py + 7, 9, 9, Colors.alpha(a, 0xFFFFFF));
        UiDraw.text(g, String.valueOf(data.masteryPoints()), MID_X + 17, py + 8, Colors.alpha(a, 0xE9D9FF), 0.8f, true);
        UiDraw.textRight(g, "DRAG TO ROTATE", MID_X + MID_W - 6, py + 1, Colors.alpha(a * 0.4f, 0xB0A8C0), 0.45f, false);

        UiDraw.hGradient(g, MID_X + 6, TREE_Y - 2, cx, TREE_Y - 1, Colors.alpha(0, theme), Colors.alpha(a * 0.7f, theme));
        UiDraw.hGradient(g, cx, TREE_Y - 2, MID_X + MID_W - 6, TREE_Y - 1, Colors.alpha(a * 0.7f, theme), Colors.alpha(0, theme));
        UiDraw.textCentered(g, "ABILITY TREE", cx, TREE_Y + 1, Colors.alpha(a * 0.75f, light), 0.55f, false);
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
            UiDraw.textCentered(g, "No abilities yet", MID_X + MID_W / 2, TREE_Y + 50, Colors.alpha(a * 0.6f, 0xB0A8C0), 0.6f, false);
        }
        // connections
        for (Node n : nodes) {
            if (n.ability() == null) continue;
            List<String> parents = n.ability().parents().isEmpty() ? List.of(CORE) : n.ability().parents();
            for (String pid : parents) {
                Node pn = nodes.stream().filter(o -> o.id().equals(pid)).findFirst().orElse(null);
                if (pn == null) continue;
                boolean lit = data.isUnlocked(n.ability()) && (pn.ability() == null || data.isUnlocked(pn.ability()));
                int c = lit ? Colors.alpha(a * 0.9f, theme) : Colors.alpha(a * 0.35f, 0x55505F);
                UiDraw.line(g, pn.x(), pn.y(), n.x(), n.y(), lit ? 2f : 1.2f, c, c);
                if (lit) {
                    float f = (t * 0.8f + (n.x() + n.y()) * 0.01f) % 1f;
                    float fx = pn.x() + (n.x() - pn.x()) * f, fy = pn.y() + (n.y() - pn.y()) * f;
                    UiDraw.glow(g, fx, fy, 9, Colors.alpha(a * 0.9f, light));
                }
            }
        }
        // nodes
        for (Node n : nodes) {
            boolean hover = Math.abs(mx - n.x()) < 8 && Math.abs(my - n.y()) < 8 && !confirmOpen;
            if (hover) hoverNode = n.id();
            boolean sel = n.id().equals(selected);
            if (n.ability() == null) {
                float r = 7 + (hover ? 1 : 0);
                UiDraw.glow(g, n.x(), n.y(), 26, Colors.alpha(a * (sel ? 0.8f : 0.45f), theme));
                UiDraw.diamond(g, n.x(), n.y(), r + 1, Colors.alpha(a, sel ? 0xFFFFFF : light));
                UiDraw.diamond(g, n.x(), n.y(), r - 1, Colors.alpha(a, Colors.darken(theme, 0.3f)));
                UiDraw.diamond(g, n.x(), n.y(), 2.5f, Colors.alpha(a, 0xFFFFFF));
                continue;
            }
            AbilityDefinition ab = n.ability();
            int lvl = data.abilityLevel(ab);
            boolean unlocked = lvl > 0;
            boolean maxed = unlocked && lvl >= ab.maxLevel();
            boolean available = AbilityService.upgradeDenial(data, ab) == null;
            float s = 13 + (hover ? 1.5f : 0) + (sel ? 1 : 0);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 5 + n.x());
            float glowA = !unlocked ? 0.08f + 0.06f * pulse : maxed ? 0.75f : available ? 0.45f + 0.35f * pulse : 0.4f;
            int glowC = maxed ? 0xFFD84A : theme;
            UiDraw.glow(g, n.x(), n.y(), s * 2.6f, Colors.alpha(a * glowA, glowC));
            UiDraw.rect(g, n.x() - s / 2, n.y() - s / 2, n.x() + s / 2, n.y() + s / 2, Colors.alpha(a * 0.9f, 0x07050C));
            UiDraw.texture(g, ab.icon(), n.x() - s / 2 + 0.5f, n.y() - s / 2 + 0.5f, s - 1, s - 1,
                    Colors.alpha(a * (unlocked ? 1f : 0.55f), unlocked ? 0xFFFFFF : 0x4A4652));
            if (!unlocked) UiDraw.texture(g, LOCK, n.x() - 3.5f, n.y() - 3.5f, 7, 7, Colors.alpha(a * 0.95f, 0xFFFFFF));
            int border = sel ? 0xFFFFFF : maxed ? 0xFFD84A : available ? Colors.lerpRgb(light, 0xFFFFFF, pulse * 0.5f) : unlocked ? theme : 0x45414E;
            UiDraw.border(g, n.x() - s / 2 - 1, n.y() - s / 2 - 1, n.x() + s / 2 + 1, n.y() + s / 2 + 1, 1, Colors.alpha(a, border));
            if (sel) UiDraw.glowBorder(g, n.x() - s / 2 - 2, n.y() - s / 2 - 2, n.x() + s / 2 + 2, n.y() + s / 2 + 2, light, a * 0.7f, 2);
            // level pips
            float pw = ab.maxLevel() * 3f;
            for (int i = 0; i < ab.maxLevel(); i++) {
                float px = n.x() - pw / 2 + i * 3f;
                UiDraw.rect(g, px, n.y() + s / 2 + 2, px + 2, n.y() + s / 2 + 3.5f,
                        Colors.alpha(a, i < lvl ? (maxed ? 0xFFD84A : light) : 0x3A3644));
            }
            if (hover) {
                String label = ab.name().toUpperCase() + (unlocked ? "  LV " + lvl : "  ·  LV " + ab.unlockLevel());
                float lw = UiDraw.font().width(label) * 0.55f + 6;
                float lx = Math.max(MID_X + 2, Math.min(MID_X + MID_W - lw - 2, n.x() - lw / 2));
                UiDraw.rect(g, lx, n.y() - s / 2 - 11, lx + lw, n.y() - s / 2 - 3, Colors.alpha(0.92f, 0x0A0812));
                UiDraw.border(g, lx, n.y() - s / 2 - 11, lx + lw, n.y() - s / 2 - 3, 1, Colors.alpha(0.9f, theme));
                UiDraw.text(g, label, lx + 3, n.y() - s / 2 - 9.5f, 0xFFFFFFFF, 0.55f, false);
            }
        }
    }

    // ---- right: upgrade panel ----

    private void drawRight(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float a, float mx, float my, long now) {
        UiDraw.panel(g, RIGHT_X, PANEL_Y, RIGHT_W, PANEL_H, theme, a);
        float flash = Math.max(0, 1 - (now - upgradeFlashAt) / 700f);
        if (flash > 0) UiDraw.glowBorder(g, RIGHT_X, PANEL_Y, RIGHT_X + RIGHT_W, PANEL_Y + PANEL_H, 0xFFFFFF, a * flash, 5);
        float x = RIGHT_X + 7, y = PANEL_Y + 6, w = RIGHT_W - 14;
        UiDraw.text(g, "UPGRADE", x, y, Colors.alpha(a * 0.6f, light), 0.55f, false);
        y += 10;
        AbilityDefinition ab = CORE.equals(selected) ? null : def.ability(selected);
        if (ab == null) {
            UiDraw.wrap(g, "Weapon level raises damage on every attack. Spend Mastery Points on ability nodes to strengthen them.",
                    x, y, (int) w, Colors.alpha(a * 0.85f, 0xD6CFE3), 0.6f, 7);
            y += 32;
            for (MasteryBonus.Milestone ms : MasteryBonus.MILESTONES) {
                boolean got = ProgressionMath.mastery(def, data) + 1e-4f >= ms.threshold();
                UiDraw.text(g, Math.round(ms.threshold() * 100) + "%", x, y, Colors.alpha(a, got ? 0xFFD84A : 0x77727F), 0.6f, false);
                UiDraw.wrap(g, ms.description(), x + 18, y, (int) w - 18, Colors.alpha(a, got ? 0xFFFFFF : 0x8F8A9A), 0.55f, 7);
                y += 16;
            }
            return;
        }
        int lvl = data.abilityLevel(ab);
        boolean unlocked = lvl > 0;
        boolean maxed = lvl >= ab.maxLevel();
        UiDraw.text(g, ab.name().toUpperCase(), x, y, Colors.alpha(a, 0xFFFFFF), 0.85f, true);
        y += 13;
        // current → next
        UiDraw.text(g, "CURRENT", x, y, Colors.alpha(a * 0.65f, 0xB0A8C0), 0.55f, false);
        UiDraw.textRight(g, "NEXT", x + w, y, Colors.alpha(a * 0.65f, 0xB0A8C0), 0.55f, false);
        y += 7;
        UiDraw.title(g, String.valueOf(lvl), x + 12, y, Colors.alpha(a, unlocked ? light : 0x77727F), 1.6f);
        UiDraw.title(g, maxed ? "—" : String.valueOf(lvl + 1), x + w - 12, y, Colors.alpha(a, maxed ? 0x77727F : 0xFFFFFF), 1.6f);
        float arrowPulse = 0.5f + 0.5f * (float) Math.sin(t * 6);
        UiDraw.title(g, "→", x + w / 2, y + 1, Colors.alpha(a * (0.5f + 0.5f * arrowPulse), light), 1.3f);
        y += 19;
        UiDraw.hGradient(g, x, y, x + w, y + 1, Colors.alpha(a * 0.6f, theme), Colors.alpha(0, theme));
        y += 4;
        if (!maxed) {
            UiDraw.text(g, unlocked ? "UPGRADE:" : "UNLOCKS:", x, y, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
            y += 8;
            List<String> lines = unlocked ? ab.upgradeLines(lvl + 1) : List.of("Reach weapon level " + ab.unlockLevel(), "Then upgrade with Mastery Points");
            for (String line : lines) {
                UiDraw.text(g, line, x + 2, y, Colors.alpha(a, line.startsWith("+") || line.startsWith("-") ? 0x7CFFB2 : 0xFFD84A), 0.58f, false);
                y += 8;
            }
        } else {
            UiDraw.title(g, "MAX LEVEL", x + w / 2, y + 4, Colors.alpha(a, 0xFFD84A), 1.0f);
            y += 14;
        }

        // cost
        float cy = PANEL_Y + PANEL_H - 56;
        if (!maxed && unlocked) {
            int cost = ab.costFor(lvl + 1);
            UiDraw.text(g, "COST", x, cy, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
            UiDraw.texture(g, GEM, x + 22, cy - 1.5f, 9, 9, Colors.alpha(a, 0xFFFFFF));
            boolean afford = data.masteryPoints() >= cost;
            UiDraw.text(g, cost + " MASTERY POINT" + (cost > 1 ? "S" : ""), x + 33, cy, Colors.alpha(a, afford ? 0xE9D9FF : 0xFF6B7E), 0.6f, false);
            UiDraw.text(g, "REQUIRES", x, cy + 9, Colors.alpha(a * 0.7f, 0xB0A8C0), 0.58f, false);
            int need = ab.weaponLevelFor(lvl + 1);
            UiDraw.text(g, "WEAPON LV " + need, x + 33, cy + 9, Colors.alpha(a, data.level() >= need ? 0x7CFFB2 : 0xFF6B7E), 0.6f, false);
        }
        // upgrade button
        String denial = AbilityService.upgradeDenial(data, ab);
        String label = denial == null ? "UPGRADE" : maxed ? "MAXED" : !unlocked ? "LOCKED" : denial.startsWith("Insufficient") ? "NEED POINTS"
                : "LEVEL " + ab.weaponLevelFor(lvl + 1);
        button(g, "upgrade", x, PANEL_Y + PANEL_H - 36, w, 16, label, denial == null ? theme : 0x3A3644, light, denial == null && !confirmOpen, mx, my, 0.95f);
        if (denial != null && !maxed) {
            UiDraw.textCentered(g, denial, x + w / 2, PANEL_Y + PANEL_H - 18, Colors.alpha(a * 0.75f, 0xFF8A9A), 0.5f, false);
        }
        // set active (castables)
        if (ab.kind().castable() && unlocked) {
            boolean active = ab == AbilityService.selected(def, data);
            button(g, "select", x, PANEL_Y + PANEL_H - 13, w, 9, active ? "BOUND TO " + keyName() : "BIND TO " + keyName(),
                    active ? Colors.darken(theme, 0.5f) : 0x2A2633, light, !active && !confirmOpen, mx, my, 0.55f);
        }
    }

    private static String keyName() {
        return com.fantasyweapons.client.input.KeyBindings.ABILITY.getTranslatedKeyMessage().getString().toUpperCase();
    }

    private void drawFooter(GuiGraphics g, int theme, int light, float a, float mx, float my) {
        button(g, "close", CW / 2 - 32, CH - 20, 64, 14, "CLOSE", 0x5A1022, 0xFF7A8A, !confirmOpen, mx, my, 0.85f);
        UiDraw.textCentered(g, "Kill mobs with this weapon to earn EXP · Level ups grant Mastery Points", CW / 2, CH - 4,
                Colors.alpha(a * 0.5f, 0xB0A8C0), 0.5f, false);
    }

    private void drawConfirm(GuiGraphics g, WeaponDefinition def, WeaponData data, int theme, int light, float t, float mx, float my) {
        AbilityDefinition ab = def.ability(selected);
        if (ab == null) {
            confirmOpen = false;
            return;
        }
        int lvl = data.abilityLevel(ab);
        UiDraw.rect(g, -ox / scale, -oy / scale, (width - ox) / scale, (height - oy) / scale, 0xA0000000);
        float w = 200, h = 70, x = CW / 2 - w / 2, y = CH / 2 - h / 2;
        UiDraw.glow(g, CW / 2, CH / 2, 260, Colors.alpha(0.35f, theme));
        UiDraw.panel(g, x, y, w, h, theme, 1f);
        UiDraw.title(g, "CONFIRM UPGRADE", CW / 2, y + 7, Colors.alpha(1, light), 1.0f);
        UiDraw.textCentered(g, "Upgrade " + ab.name().toUpperCase() + " to Level " + (lvl + 1) + "?", CW / 2, y + 24, 0xFFFFFFFF, 0.72f, true);
        UiDraw.textCentered(g, "Cost: " + ab.costFor(lvl + 1) + " Mastery Point" + (ab.costFor(lvl + 1) > 1 ? "s" : ""), CW / 2, y + 35,
                0xFFE9D9FF, 0.6f, false);
        button(g, "confirm", x + 16, y + h - 22, 76, 15, "CONFIRM", theme, light, true, mx, my, 0.8f);
        button(g, "cancel", x + w - 92, y + h - 22, 76, 15, "CANCEL", 0x3A3644, 0xC0B8D0, true, mx, my, 0.8f);
    }

    private void button(GuiGraphics g, String id, float x, float y, float w, float h, String label, int color, int light, boolean enabled,
                        float mx, float my, float textScale) {
        boolean hover = enabled && mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (hover && (!confirmOpen || id.equals("confirm") || id.equals("cancel"))) hoverButton = id;
        float t = Util.getMillis() / 1000f;
        int top = enabled ? (hover ? Colors.brighten(color, 0.25f) : color) : 0x2A2633;
        UiDraw.vGradient(g, x, y, x + w, y + h, Colors.alpha(0.95f, top), Colors.alpha(0.95f, Colors.darken(top, 0.45f)));
        UiDraw.vGradient(g, x + 1, y + 1, x + w - 1, y + h * 0.45f, Colors.alpha(enabled ? 0.25f : 0.08f, 0xFFFFFF), Colors.alpha(0, 0xFFFFFF));
        if (enabled) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
            UiDraw.glowBorder(g, x, y, x + w, y + h, light, hover ? 1f : 0.55f + 0.25f * pulse, hover ? 4 : 2.5f);
        } else {
            UiDraw.border(g, x, y, x + w, y + h, 1, Colors.alpha(0.6f, 0x4A4656));
        }
        UiDraw.title(g, label, x + w / 2, y + (h - 8 * textScale) / 2f, Colors.alpha(1, enabled ? 0xFFFFFF : 0x8A8698), textScale);
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
        PacketDistributor.sendToServer(new C2SPayloads.UpgradeAbility(slot, data.idOrNil(), ab.id()));
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
