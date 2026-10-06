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
    public static final int H = 116;
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
        UiDraw.theme(def.themePrimary(data), def.themeSecondary(data));
        try {
            renderPanel(g, def, data, dt, nowMs);
            if (!held.isEmpty()) renderChargeRing(g, def, data, nowMs);
        } finally {
            UiDraw.resetTheme();
        }
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
        if (full || castFlash > 0) UiDraw.glow(g, 26, 66, 70, Colors.alpha(a * Math.max(fullPulse * 0.5f, castFlash * 0.6f), themeLight));

        // ---- header: name, rarity and element, level plaque ----
        UiDraw.text(g, def.displayName(), 9, 6, Colors.alpha(a, UiDraw.GOLD_LIGHT), 0.95f, true);
        String lv = "Lv " + data.level();
        float lvW = UiDraw.font().width(lv) * 0.7f + 10;
        UiDraw.button(g, W - 8 - lvW, 4, lvW, 12, 0, a);
        UiDraw.title(g, lv, W - 8 - lvW / 2, 6.5f, Colors.alpha(a, UiDraw.GOLD_LIGHT), 0.7f);
        String sub = def.rarity().displayName() + " · " + def.element().displayName();
        UiDraw.text(g, sub, 9, 16, Colors.alpha(a, Colors.lerpRgb(rarity, UiDraw.INK_MUTED, 0.35f)), 0.55f, false);
        WeaponForm form = def.form(data);
        if (form != null) {
            UiDraw.textRight(g, form.displayName() + " form", W - 9, 18, Colors.alpha(a, form.themePrimary()), 0.52f, false);
        }

        // ---- experience ----
        long need = ProgressionMath.expToNext(data.level());
        float expFrac = need <= 0 ? 1f : data.exp() / (float) need;
        if (shownLevel != data.level()) {
            if (shownLevel > 0 && shownLevel < data.level()) shownExp = Math.min(shownExp, 0.999f);
            shownLevel = data.level();
            if (shownExp > expFrac + 0.0001f) shownExp = 0;
        }
        shownExp += (expFrac - shownExp) * Math.min(1, dt * 5);
        UiDraw.bar(g, 9, 27, W - 18, 3, shownExp, Colors.darken(theme, 0.15f), themeLight, a, t);
        float dmg = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        String dmgText = String.format("Damage %,.0f", dmg);
        UiDraw.text(g, dmgText, 9, 34, Colors.alpha(a, 0xF0A080), 0.58f, false);
        int mastery = Math.round(ProgressionMath.mastery(def, data) * 100);
        UiDraw.text(g, "Mastery " + mastery + "%", 9 + UiDraw.font().width(dmgText) * 0.58f + 6, 34, Colors.alpha(a, UiDraw.ARCANE), 0.58f, false);
        String expText = need <= 0 ? "Mastered" : String.format("%,d / %,d", data.exp(), need);
        UiDraw.textRight(g, expText, W - 9, 34, Colors.alpha(a, UiDraw.INK_MUTED), 0.55f, false);

        UiDraw.divider(g, W / 2f, 44, W - 30, a);

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
        float cx = 24, cy = 64, r = 14;
        if (sel == null) {
            UiDraw.rect(g, cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, Colors.alpha(a, 0x0C0704));
            UiDraw.texture(g, LOCK, cx - 6, cy - 6, 12, 12, Colors.alpha(a * 0.8f, 0xFFFFFF));
            UiDraw.medallion(g, cx, cy, r, Colors.alpha(a, 0x9A9088));
            AbilityDefinition next = null;
            for (AbilityDefinition ab : def.castables()) if (!data.isUnlocked(ab)) {
                next = ab;
                break;
            }
            UiDraw.text(g, "No ability awakened yet", 44, 52, Colors.alpha(a, UiDraw.INK), 0.68f, false);
            if (next != null) {
                UiDraw.text(g, next.name() + " awakens at level " + next.unlockLevel(), 44, 62, Colors.alpha(a, UiDraw.INK_MUTED), 0.55f, false);
            }
            renderStrip(g, def, data, null, a, theme, light);
            return;
        }
        int stateColor = switch (state) {
            case READY -> UiDraw.GOOD;
            case CHARGING -> light;
            case FULLY_CHARGED -> UiDraw.GOLD_LIGHT;
            case ACTIVE -> UiDraw.GOLD;
            case COOLDOWN -> 0x9A8E7E;
            default -> 0x7A6E60;
        };

        // the ability's icon set in a medallion, glowing with its state
        float pulse = state == AbilityState.FULLY_CHARGED ? 1 + 0.06f * fullPulse : 1f;
        float glowA = state == AbilityState.READY ? 0.3f : state == AbilityState.FULLY_CHARGED ? 0.55f + 0.35f * fullPulse
                : state == AbilityState.CHARGING ? 0.4f : 0.08f;
        UiDraw.glow(g, cx, cy, r * 3.4f * pulse, Colors.alpha(a * Math.max(glowA, Math.max(stateFlash, castFlash)), theme));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(pulse, pulse, 1);
        float in = r * 0.72f;
        UiDraw.rect(g, -in, -in, in, in, Colors.alpha(a, 0x0C0704));
        int iconTint = state == AbilityState.COOLDOWN ? 0x8A8070 : 0xFFFFFF;
        UiDraw.texture(g, sel.icon(), -in, -in, in * 2, in * 2, Colors.alpha(a, iconTint));
        if (state == AbilityState.COOLDOWN) {
            long rem = ClientState.cooldownRemaining(data, sel);
            int tot = Math.max(1, ClientState.cooldownTotal(data, sel));
            float f = rem / (float) tot;
            UiDraw.rect(g, -in, -in + in * 2 * (1 - f), in, in, Colors.alpha(a * 0.6f, 0x000000));
        }
        UiDraw.medallion(g, 0, 0, r, Colors.alpha(a, state == AbilityState.COOLDOWN ? 0xA09488 : 0xFFFFFF));
        g.pose().popPose();

        // name, rank and state
        int nameColor = castFlash > 0 ? Colors.lerpRgb(UiDraw.INK, 0xFFFFFF, castFlash) : UiDraw.INK;
        UiDraw.text(g, sel.name(), 44, 50, Colors.alpha(a, nameColor), 0.78f, true);
        int lvl = data.abilityLevel(sel);
        UiDraw.text(g, "Rank " + lvl, 44 + UiDraw.font().width(sel.name()) * 0.78f + 4, 51.5f, Colors.alpha(a, UiDraw.ARCANE), 0.55f, false);
        UiDraw.text(g, UiDraw.titleCase(state.label()), 44, 59.5f, Colors.alpha(a, stateColor), 0.62f, false);

        float bx = 44, by = 68, bw = W - 53, bh = 3;
        String right;
        switch (state) {
            case CHARGING, FULLY_CHARGED -> {
                ClientState.ChargeInfo ci = ClientState.localCharge();
                float f = ci == null ? 0 : ci.fraction();
                UiDraw.bar(g, bx, by, bw, bh, f, Colors.darken(theme, 0.15f), state == AbilityState.FULLY_CHARGED ? UiDraw.GOLD_LIGHT : light, a, t * 2);
                right = state == AbilityState.FULLY_CHARGED ? "Full" : Math.round(f * 100) + "%";
            }
            case COOLDOWN -> {
                long rem = ClientState.cooldownRemaining(data, sel);
                int tot = Math.max(1, ClientState.cooldownTotal(data, sel));
                UiDraw.bar(g, bx, by, bw, bh, rem / (float) tot, 0x5A5048, 0x8A8070, a, -1);
                right = String.format("%.1fs", rem / 20f);
            }
            case ACTIVE -> {
                float p = 0.6f + 0.4f * (float) Math.sin(t * 6);
                UiDraw.bar(g, bx, by, bw, bh, 1, 0xE0A040, UiDraw.GOLD_LIGHT, a * p, t);
                right = "Active";
            }
            default -> {
                UiDraw.bar(g, bx, by, bw, bh, 1, Colors.darken(theme, 0.15f), light, a * 0.85f, t * 0.5f);
                right = keyName(KeyBindings.ABILITY);
            }
        }
        UiDraw.textRight(g, right, W - 9, 59.5f, Colors.alpha(a, stateColor), 0.62f, false);
        if (stateFlash > 0) UiDraw.glow(g, bx + bw / 2, by + 1, bw * 1.2f, Colors.alpha(a * stateFlash * 0.5f, stateColor));

        renderStrip(g, def, data, sel, a, theme, light);
    }

    private static void renderStrip(GuiGraphics g, WeaponDefinition def, WeaponData data, AbilityDefinition sel, float a, int theme, int light) {
        List<AbilityDefinition> castables = def.castables();
        float r = 5.5f, x = 44 + r, y = 79;
        for (AbilityDefinition ab : castables) {
            boolean unlocked = data.isUnlocked(ab);
            boolean isSel = ab == sel;
            AbilityState st = unlocked ? ClientState.state(def, data, ab) : AbilityState.LOCKED;
            int tint = !unlocked ? 0x403830 : st == AbilityState.COOLDOWN ? 0x8A8070 : 0xFFFFFF;
            if (isSel) UiDraw.glow(g, x, y, r * 3.6f, Colors.alpha(a * 0.55f, UiDraw.GOLD_LIGHT));
            float in = r * 0.72f;
            UiDraw.rect(g, x - in, y - in, x + in, y + in, Colors.alpha(a, 0x0C0704));
            UiDraw.texture(g, ab.icon(), x - in, y - in, in * 2, in * 2, Colors.alpha(a * (unlocked ? 1f : 0.7f), tint));
            if (!unlocked) UiDraw.texture(g, LOCK, x - 2.5f, y - 2.5f, 5, 5, Colors.alpha(a * 0.9f, 0xFFFFFF));
            if (st == AbilityState.COOLDOWN) {
                long rem = ClientState.cooldownRemaining(data, ab);
                int tot = Math.max(1, ClientState.cooldownTotal(data, ab));
                UiDraw.rect(g, x - in, y - in + in * 2 * (1 - rem / (float) tot), x + in, y + in, Colors.alpha(a * 0.6f, 0x000000));
            }
            UiDraw.medallion(g, x, y, r, Colors.alpha(a, isSel ? 0xFFF4D0 : unlocked ? 0xC8BCA8 : 0x7A7068));
            x += r * 2 + 3;
        }
        // key hints on their own row, clear of the icons
        String hint = keyName(KeyBindings.CYCLE) + " next ability   " + keyName(KeyBindings.MENU) + " open the tome";
        UiDraw.textCentered(g, hint, W / 2f, H - 21f, Colors.alpha(a * 0.85f, UiDraw.INK_MUTED), 0.46f, false);
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
            UiDraw.texture(g, inst.type.icon(), x + 1.5f, y + 1.5f, 9, 9, Colors.alpha(a, 0xFFFFFF));
            UiDraw.medallion(g, x + 6, y + 6, 7, Colors.alpha(a, 0xFFFFFF));
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
        String label = full ? "Full Charge" : Math.round(f * 100) + "%";
        UiDraw.title(g, label, cx, cy + r1 + 7, Colors.alpha(1f, full ? UiDraw.GOLD_LIGHT : light), full ? 0.8f : 0.7f);
        if (a != null) UiDraw.textCentered(g, a.name(), cx, cy - r1 - 12, Colors.alpha(0.9f, UiDraw.INK), 0.62f, true);
    }

    static String keyName(net.minecraft.client.KeyMapping key) {
        return "[" + key.getTranslatedKeyMessage().getString().toUpperCase() + "]";
    }
}
