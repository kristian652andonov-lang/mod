package com.fantasyweapons.client.hud;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.ability.AbilityState;
import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.input.KeyBindings;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The custom weapon HUD: a compact fantasy panel (bottom-right by default) showing the held weapon's name, rarity,
 * level, animated EXP bar, damage, mastery, form/mode, the selected ability with its live state (ready / charging /
 * full charge / active / cooldown) and the ability strip, plus status effects and a radial charge ring at the
 * crosshair. Everything shown is read from server-synced data.
 */
public final class WeaponHud {
    public static final int W = 176;
    public static final int H = 88;
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");

    private static ItemStack shownStack = ItemStack.EMPTY;
    private static float alpha;
    private static float shownExp;
    private static int shownLevel = -1;
    private static long lastFrame;
    private static AbilityState lastState;
    private static String lastAbility = "";
    private static long stateFlashAt;
    private static long castFlashAt;

    private WeaponHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long nowMs = Util.getMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (nowMs - lastFrame) / 1000f);
        lastFrame = nowMs;
        if (mc.player == null || mc.options.hideGui || !ClientConfig.get(ClientConfig.HUD_ENABLED, true)) return;

        ItemStack held = ClientState.heldWeapon(mc.player);
        if (!held.isEmpty()) shownStack = held;
        alpha += ((held.isEmpty() ? 0f : 1f) - alpha) * Math.min(1, dt * 9);
        if (alpha < 0.02f || shownStack.isEmpty()) {
            if (held.isEmpty()) shownLevel = -1;
            return;
        }
        if (!(shownStack.getItem() instanceof FantasyWeaponItem item)) return;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(shownStack);
        renderPanel(g, def, data, dt, nowMs);
        if (!held.isEmpty()) renderChargeRing(g, def, data, nowMs);
    }

    private static void renderPanel(GuiGraphics g, WeaponDefinition def, WeaponData data, float dt, long nowMs) {
        float scale = (float) ClientConfig.get(ClientConfig.HUD_SCALE, 1.0);
        int sw = g.guiWidth(), sh = g.guiHeight();
        ClientConfig.HudAnchor anchor = ClientConfig.SPEC.isLoaded() ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.BOTTOM_RIGHT;
        float x0 = switch (anchor) {
            case BOTTOM_RIGHT, TOP_RIGHT -> sw - W * scale - 8;
            case BOTTOM_LEFT, TOP_LEFT -> 8;
        };
        float y0 = switch (anchor) {
            case BOTTOM_RIGHT, BOTTOM_LEFT -> sh - H * scale - 8;
            case TOP_RIGHT, TOP_LEFT -> 22;
        };
        x0 += ClientConfig.get(ClientConfig.HUD_OFFSET_X, 0);
        y0 += ClientConfig.get(ClientConfig.HUD_OFFSET_Y, 0);
        // slide in slightly while fading
        float slide = (1 - alpha) * 14;
        g.pose().pushPose();
        g.pose().translate(x0 + (anchor == ClientConfig.HudAnchor.BOTTOM_RIGHT || anchor == ClientConfig.HudAnchor.TOP_RIGHT ? slide : -slide), y0, 0);
        g.pose().scale(scale, scale, 1);

        int theme = def.themePrimary(data);
        int themeLight = def.themeSecondary(data);
        int rarity = def.rarity().color();
        float t = nowMs / 1000f;
        float a = alpha;

        AbilityDefinition sel = AbilityService.selected(def, data);
        AbilityState state = sel == null ? AbilityState.LOCKED : ClientState.state(def, data, sel);
        trackStateChanges(sel, state, nowMs);
        float stateFlash = Math.max(0, 1 - (nowMs - stateFlashAt) / 450f);
        float castFlash = Math.max(0, 1 - (nowMs - castFlashAt) / 600f);
        boolean full = state == AbilityState.FULLY_CHARGED;
        float fullPulse = full ? 0.5f + 0.5f * (float) Math.sin(t * 9) : 0;

        // ---- frame ----
        UiDraw.panel(g, 0, 0, W, H, theme, a);
        if (full || castFlash > 0) UiDraw.glowBorder(g, 0, 0, W, H, themeLight, a * Math.max(fullPulse * 0.8f, castFlash), 4);

        // ---- header ----
        UiDraw.glow(g, 11, 10, 18, Colors.alpha(a * 0.6f, theme));
        UiDraw.diamond(g, 11, 10, 4, Colors.alpha(a, themeLight));
        UiDraw.diamond(g, 11, 10, 2, Colors.alpha(a, theme));
        UiDraw.text(g, def.displayName().toUpperCase(), 19, 5, Colors.alpha(a, rarity), 1.0f, true);
        String lv = "LV " + data.level();
        float lvW = UiDraw.font().width(lv) * 0.85f + 8;
        UiDraw.hGradient(g, W - 7 - lvW, 4, W - 7, 14, Colors.alpha(a * 0.9f, theme), Colors.alpha(a * 0.9f, Colors.darken(theme, 0.4f)));
        UiDraw.border(g, W - 7 - lvW, 4, W - 7, 14, 1, Colors.alpha(a * 0.8f, themeLight));
        UiDraw.text(g, lv, W - 7 - lvW + 4, 5.5f, Colors.alpha(a, 0xFFFFFF), 0.85f, true);
        String sub = def.rarity().displayName().toUpperCase() + " · " + def.element().displayName().toUpperCase();
        UiDraw.text(g, sub, 19, 15.5f, Colors.alpha(a * 0.85f, Colors.lerpRgb(rarity, 0xB0A8C0, 0.4f)), 0.55f, false);
        WeaponForm form = def.form(data);
        if (form != null) {
            String fs = form.label() + ": " + form.displayName().toUpperCase();
            UiDraw.textRight(g, fs, W - 8, 16, Colors.alpha(a, form.themePrimary()), 0.55f, false);
        }

        // ---- EXP ----
        long need = ProgressionMath.expToNext(data.level());
        float expFrac = need <= 0 ? 1f : data.exp() / (float) need;
        if (shownLevel != data.level()) {
            if (shownLevel > 0 && shownLevel < data.level()) shownExp = Math.min(shownExp, 0.999f);
            shownLevel = data.level();
            if (shownExp > expFrac + 0.0001f) shownExp = 0;
        }
        shownExp += (expFrac - shownExp) * Math.min(1, dt * 5);
        UiDraw.bar(g, 8, 26, W - 16, 4, shownExp, theme, themeLight, a, t);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        UiDraw.text(g, String.format("DMG %,.0f", dmg), 8, 33, Colors.alpha(a, 0xFF8A7A), 0.62f, false);
        int mastery = Math.round(ProgressionMath.mastery(def, data) * 100);
        UiDraw.text(g, "MASTERY " + mastery + "%", 8 + UiDraw.font().width(String.format("DMG %,.0f", dmg)) * 0.62f + 6, 33,
                Colors.alpha(a * 0.9f, 0xC9A2FF), 0.62f, false);
        String expText = need <= 0 ? "MAX LEVEL" : String.format("%,d / %,d EXP", data.exp(), need);
        UiDraw.textRight(g, expText, W - 8, 33, Colors.alpha(a * 0.8f, 0xBDB6CC), 0.58f, false);

        UiDraw.hGradient(g, 8, 41, W / 2f, 42, Colors.alpha(0, theme), Colors.alpha(a * 0.6f, theme));
        UiDraw.hGradient(g, W / 2f, 41, W - 8, 42, Colors.alpha(a * 0.6f, theme), Colors.alpha(0, theme));

        // ---- ability card ----
        renderAbilityCard(g, def, data, sel, state, a, t, theme, themeLight, stateFlash, castFlash, fullPulse);

        // ---- status effects (above the panel) ----
        renderStatuses(g, a);
        g.pose().popPose();
    }

    private static void trackStateChanges(AbilityDefinition sel, AbilityState state, long nowMs) {
        String id = sel == null ? "" : sel.id();
        if (!id.equals(lastAbility)) {
            lastAbility = id;
            lastState = state;
            return;
        }
        if (lastState != state) {
            if ((lastState == AbilityState.CHARGING || lastState == AbilityState.FULLY_CHARGED)
                    && (state == AbilityState.COOLDOWN || state == AbilityState.ACTIVE)) {
                castFlashAt = nowMs;
            }
            if (state == AbilityState.READY && lastState == AbilityState.COOLDOWN) stateFlashAt = nowMs;
            if (state == AbilityState.FULLY_CHARGED) stateFlashAt = nowMs;
            lastState = state;
        }
    }

    private static void renderAbilityCard(GuiGraphics g, WeaponDefinition def, WeaponData data, AbilityDefinition sel, AbilityState state,
                                          float a, float t, int theme, int light, float stateFlash, float castFlash, float fullPulse) {
        float ix = 8, iy = 46, is = 26;
        if (sel == null) {
            UiDraw.rect(g, ix, iy, ix + is, iy + is, Colors.alpha(a * 0.6f, 0x0A0810));
            UiDraw.border(g, ix, iy, ix + is, iy + is, 1, Colors.alpha(a * 0.5f, 0x55505F));
            UiDraw.texture(g, LOCK, ix + 7, iy + 7, 12, 12, Colors.alpha(a * 0.8f, 0xFFFFFF));
            AbilityDefinition next = null;
            for (AbilityDefinition ab : def.castables()) if (!data.isUnlocked(ab)) {
                next = ab;
                break;
            }
            UiDraw.text(g, "NO ABILITY UNLOCKED", 40, 48, Colors.alpha(a, 0xB8B0C8), 0.75f, false);
            if (next != null) {
                UiDraw.text(g, next.name().toUpperCase() + " AT LEVEL " + next.unlockLevel(), 40, 59, Colors.alpha(a * 0.85f, light), 0.6f, false);
            } else {
                UiDraw.text(g, "Abilities arrive in a later update", 40, 59, Colors.alpha(a * 0.7f, 0x8F8A9A), 0.6f, false);
            }
            renderStrip(g, def, data, null, a, theme, light);
            return;
        }
        int stateColor = switch (state) {
            case READY -> 0x7CFFB2;
            case CHARGING -> light;
            case FULLY_CHARGED -> 0xFFFFFF;
            case ACTIVE -> 0xFFD84A;
            case COOLDOWN -> 0x9A96AE;
            default -> 0x6A6676;
        };

        // icon frame with glow
        float pulse = state == AbilityState.FULLY_CHARGED ? 1 + 0.08f * fullPulse : 1f;
        float glowA = state == AbilityState.READY ? 0.35f : state == AbilityState.FULLY_CHARGED ? 0.6f + 0.4f * fullPulse
                : state == AbilityState.CHARGING ? 0.45f : 0.12f;
        UiDraw.glow(g, ix + is / 2, iy + is / 2, is * 2.2f * pulse, Colors.alpha(a * Math.max(glowA, Math.max(stateFlash, castFlash)), theme));
        g.pose().pushPose();
        g.pose().translate(ix + is / 2, iy + is / 2, 0);
        g.pose().scale(pulse, pulse, 1);
        UiDraw.rect(g, -is / 2, -is / 2, is / 2, is / 2, Colors.alpha(a * 0.8f, 0x07050C));
        int iconTint = state == AbilityState.COOLDOWN ? 0x8A8698 : 0xFFFFFF;
        UiDraw.texture(g, sel.icon(), -is / 2 + 1, -is / 2 + 1, is - 2, is - 2, Colors.alpha(a, iconTint));
        if (state == AbilityState.COOLDOWN) {
            long rem = ClientState.cooldownRemaining(data, sel);
            int tot = Math.max(1, ClientState.cooldownTotal(data, sel));
            float f = rem / (float) tot;
            UiDraw.rect(g, -is / 2 + 1, -is / 2 + 1 + (is - 2) * (1 - f), is / 2 - 1, is / 2 - 1, Colors.alpha(a * 0.55f, 0x000000));
        }
        UiDraw.border(g, -is / 2, -is / 2, is / 2, is / 2, 1, Colors.alpha(a, state == AbilityState.COOLDOWN ? 0x5C5868 : light));
        g.pose().popPose();

        // name + state
        int nameColor = Colors.lerpRgb(0xFFFFFF, light, 0.15f);
        if (castFlash > 0) nameColor = Colors.lerpRgb(nameColor, 0xFFFFFF, castFlash);
        UiDraw.text(g, sel.name().toUpperCase(), 40, 47, Colors.alpha(a, nameColor), 0.85f, true);
        int lvl = data.abilityLevel(sel);
        UiDraw.text(g, "LV " + lvl, 40 + UiDraw.font().width(sel.name().toUpperCase()) * 0.85f + 4, 48.5f, Colors.alpha(a * 0.8f, 0xC9A2FF), 0.6f, false);
        String label = state.label();
        UiDraw.text(g, label, 40, 57.5f, Colors.alpha(a, stateColor), 0.68f, false);

        // bar + right text
        float bx = 40, by = 66, bw = W - 48, bh = 4;
        String right;
        switch (state) {
            case CHARGING, FULLY_CHARGED -> {
                ClientState.ChargeInfo ci = ClientState.localCharge();
                float f = ci == null ? 0 : ci.fraction();
                UiDraw.bar(g, bx, by, bw, bh, f, theme, state == AbilityState.FULLY_CHARGED ? 0xFFFFFF : light, a, t * 2);
                right = state == AbilityState.FULLY_CHARGED ? "MAX" : Math.round(f * 100) + "%";
            }
            case COOLDOWN -> {
                long rem = ClientState.cooldownRemaining(data, sel);
                int tot = Math.max(1, ClientState.cooldownTotal(data, sel));
                UiDraw.bar(g, bx, by, bw, bh, rem / (float) tot, 0x5C5868, 0x8A8698, a, -1);
                right = String.format("%.1fs", rem / 20f);
            }
            case ACTIVE -> {
                float p = 0.6f + 0.4f * (float) Math.sin(t * 6);
                UiDraw.bar(g, bx, by, bw, bh, 1, 0xFFB627, 0xFFE27A, a * p, t);
                right = "ACTIVE";
            }
            default -> {
                UiDraw.bar(g, bx, by, bw, bh, 1, Colors.darken(theme, 0.2f), light, a * 0.85f, t * 0.5f);
                right = keyName(KeyBindings.ABILITY);
            }
        }
        UiDraw.textRight(g, right, W - 8, 57.5f, Colors.alpha(a, stateColor), 0.68f, false);
        if (stateFlash > 0) UiDraw.glowBorder(g, bx, by, bx + bw, by + bh, stateColor, a * stateFlash, 3);

        renderStrip(g, def, data, sel, a, theme, light);
    }

    private static void renderStrip(GuiGraphics g, WeaponDefinition def, WeaponData data, AbilityDefinition sel, float a, int theme, int light) {
        List<AbilityDefinition> castables = def.castables();
        float x = 40, y = 75.5f, s = 9;
        for (AbilityDefinition ab : castables) {
            boolean unlocked = data.isUnlocked(ab);
            boolean isSel = ab == sel;
            AbilityState st = unlocked ? ClientState.state(def, data, ab) : AbilityState.LOCKED;
            int tint = !unlocked ? 0x403C48 : st == AbilityState.COOLDOWN ? 0x8A8698 : 0xFFFFFF;
            if (isSel) UiDraw.glow(g, x + s / 2, y + s / 2, s * 2.4f, Colors.alpha(a * 0.5f, theme));
            UiDraw.texture(g, ab.icon(), x, y, s, s, Colors.alpha(a * (unlocked ? 1f : 0.7f), tint));
            if (!unlocked) UiDraw.texture(g, LOCK, x + 2, y + 2, s - 4, s - 4, Colors.alpha(a * 0.9f, 0xFFFFFF));
            if (st == AbilityState.COOLDOWN) {
                long rem = ClientState.cooldownRemaining(data, ab);
                int tot = Math.max(1, ClientState.cooldownTotal(data, ab));
                UiDraw.rect(g, x, y + s * (1 - rem / (float) tot), x + s, y + s, Colors.alpha(a * 0.55f, 0x000000));
            }
            UiDraw.border(g, x - 1, y - 1, x + s + 1, y + s + 1, 1, Colors.alpha(a * (isSel ? 1f : 0.45f), isSel ? light : 0x5C5868));
            x += s + 4;
        }
        String hint = keyName(KeyBindings.CYCLE) + " NEXT  " + keyName(KeyBindings.MENU) + " MENU";
        UiDraw.text(g, hint, 8, 77, Colors.alpha(a * 0.55f, 0xA8A0B8), 0.48f, false);
    }

    private static void renderStatuses(GuiGraphics g, float a) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        StatusEffects effects = player.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return;
        float x = W - 14, y = -16;
        for (StatusEffects.Instance inst : effects.all()) {
            int col = inst.type.color();
            UiDraw.glow(g, x + 6, y + 6, 22, Colors.alpha(a * 0.35f, col));
            UiDraw.texture(g, inst.type.icon(), x, y, 12, 12, Colors.alpha(a, 0xFFFFFF));
            float f = inst.maxDuration <= 0 ? 0 : inst.duration / (float) inst.maxDuration;
            UiDraw.rect(g, x, y + 13, x + 12 * f, y + 14, Colors.alpha(a, col));
            if (inst.stacks > 1) UiDraw.textRight(g, String.valueOf(inst.stacks), x + 13, y + 6, Colors.alpha(a, 0xFFFFFF), 0.55f, true);
            x -= 15;
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    /** Radial charge indicator around the crosshair. */
    private static void renderChargeRing(GuiGraphics g, WeaponDefinition def, WeaponData data, long nowMs) {
        if (!ClientConfig.get(ClientConfig.CROSSHAIR_CHARGE_RING, true)) return;
        ClientState.ChargeInfo ci = ClientState.localCharge();
        if (ci == null) return;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        int theme = def.themePrimary(data), light = def.themeSecondary(data);
        float f = ci.fraction();
        boolean full = f >= 1f;
        float t = nowMs / 1000f;
        float pulse = full ? 0.5f + 0.5f * (float) Math.sin(t * 10) : 0;
        float r0 = 11, r1 = 14;
        UiDraw.glow(g, cx, cy, 44 + pulse * 10, Colors.alpha(0.25f + 0.35f * f + pulse * 0.3f, theme));
        UiDraw.arc(g, cx, cy, r0, r1, 0, (float) (Math.PI * 2), Colors.alpha(0.35f, 0x0A0612), 48);
        UiDraw.arc(g, cx, cy, r0, r1, 0, (float) (Math.PI * 2 * f), Colors.alpha(0.95f, full ? Colors.lerpRgb(light, 0xFFFFFF, pulse) : light), 48);
        for (int i = 1; i < 4; i++) {
            float ang = (float) (Math.PI * 2 * i / 4);
            UiDraw.arc(g, cx, cy, r1 + 0.5f, r1 + 2.5f, ang - 0.04f, ang + 0.04f, Colors.alpha(0.8f, f >= i / 4f ? light : 0x6A6676), 2);
        }
        if (full) UiDraw.arc(g, cx, cy, r1 + 3, r1 + 4 + pulse * 2, 0, (float) (Math.PI * 2), Colors.alpha(0.5f + 0.5f * pulse, 0xFFFFFF), 48);
        AbilityDefinition a = def.ability(ci.abilityId());
        String label = full ? "FULL CHARGE" : Math.round(f * 100) + "%";
        UiDraw.title(g, label, cx, cy + r1 + 7, Colors.alpha(1f, full ? 0xFFFFFF : light), full ? 0.8f : 0.7f);
        if (a != null) UiDraw.textCentered(g, a.name().toUpperCase(), cx, cy - r1 - 12, Colors.alpha(0.85f, light), 0.6f, true);
    }

    static String keyName(net.minecraft.client.KeyMapping key) {
        return "[" + key.getTranslatedKeyMessage().getString().toUpperCase() + "]";
    }
}
