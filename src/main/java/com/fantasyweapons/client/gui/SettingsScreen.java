package com.fantasyweapons.client.gui;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.text.Tr;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every setting of the mod in one menu, opened from the player's inventory. On the left the sections: this game's
 * (the client settings: effects, interface, compatibility) and this world's (the gameplay settings the server keeps:
 * progression, EXP, damage, world destruction and each ability's numbers, a weapon at a time). On the right the
 * settings of the chosen section: switches flip on a click, choices step through their values, numbers are typed in
 * (Enter keeps them, Escape drops them) and are kept only within their range. A changed setting can be put back to its
 * default. Changes are saved at once. The world's settings can be changed only in a world of one's own (singleplayer or
 * one opened to LAN); on a server they are shown as the server has them.
 * <p>
 * The settings are read from the configs themselves, so every setting there appears here: its label is the config's
 * translation key (English text, translated as all the mod's text is, see {@link Tr}), its help the config's comment.
 */
public final class SettingsScreen extends Screen {
    private static final int W = 448, H = 256;
    private static final int GOLD = 0xF2C46B, GOLD_LIGHT = 0xFFE7A8, MUTED = 0xB9AC92, GOOD = 0x8FE08A, BAD = 0xF07A62, LINE = 0xB08A3E;
    private static final int THEME = 0x8F6BD8;
    private static final int SIDE_X = 10, SIDE_W = 116, BODY_X = 134, BODY_TOP = 30, FOOT = 24, ROW = 15, TAB = 15;
    private static final int VALUE_W = 92;
    /** The weapon picker above the ability settings. */
    private static final int PICKER_H = 20;

    /** One setting: a value of the game's or the world's config, with its label and help. */
    private record Setting(ModConfigSpec spec, ModConfigSpec.ConfigValue<Object> value, String label, String help, boolean world) {
        ModConfigSpec.ValueSpec valueSpec() {
            return value.getSpec();
        }
    }

    private sealed interface Row permits Heading, Entry {
    }

    /** A group's title within a section (an ability's, say), with an optional note beside it. */
    private record Heading(String text, String note, int depth) implements Row {
    }

    private record Entry(Setting setting) implements Row {
    }

    /** A section of the sidebar; the abilities' rows depend on the weapon picked, so they are made when shown. */
    private record Section(String title, boolean world, List<Row> rows, boolean abilities) {
    }

    private final Screen parent;
    private final GuiZoom zoom = new GuiZoom(W, H);
    private final List<Section> sections = new ArrayList<>();
    private int left, top;
    private int sectionIndex;
    private int weaponIndex;
    private float scroll;
    private List<Row> shown = List.of();
    /** The number being typed, and into which setting. */
    private Setting editing;
    private String editText = "";
    /** The setting last given a value out of its range, flashing red for a moment. */
    private Setting refused;
    private long refusedAt;
    /** The section reset asks once more first: when it was first clicked (ms). */
    private long resetAskedAt;
    private Setting hovered;

    public SettingsScreen(Screen parent) {
        super(Component.literal(Tr.s("Settings")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        zoom.fit(width, height);
        left = zoom.left(width);
        top = zoom.top(height);
        build();
        sectionIndex = Math.min(sectionIndex, sections.size() - 1);
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        commitEdit();
        minecraft.setScreen(parent);
    }

    // ---- the settings, read from the configs ----

    private void build() {
        sections.clear();
        for (String group : groups(ClientConfig.SPEC)) sections.add(section(ClientConfig.SPEC, group, false));
        if (ServerConfig.SPEC.isLoaded()) {
            for (String group : groups(ServerConfig.SPEC)) sections.add(section(ServerConfig.SPEC, group, true));
        }
    }

    private static List<String> groups(ModConfigSpec spec) {
        List<String> out = new ArrayList<>();
        for (var e : spec.getValues().valueMap().entrySet()) if (e.getValue() instanceof UnmodifiableConfig) out.add(e.getKey());
        return out;
    }

    private Section section(ModConfigSpec spec, String group, boolean world) {
        boolean abilities = world && group.equals("abilities");
        List<Row> rows = new ArrayList<>();
        UnmodifiableConfig values = (UnmodifiableConfig) spec.getValues().valueMap().get(group);
        if (abilities) {
            // the settings of every ability alike; each weapon's own come with the picker (see rows())
            for (var e : values.valueMap().entrySet()) {
                if (e.getValue() instanceof ModConfigSpec.ConfigValue<?> v) rows.add(new Entry(setting(spec, v, world)));
            }
        } else {
            walk(spec, values, List.of(group), rows, 0, world);
        }
        return new Section(groupTitle(spec, List.of(group)), world, rows, abilities);
    }

    private void walk(ModConfigSpec spec, UnmodifiableConfig values, List<String> path, List<Row> rows, int depth, boolean world) {
        for (var e : values.valueMap().entrySet()) {
            List<String> at = new ArrayList<>(path);
            at.add(e.getKey());
            if (e.getValue() instanceof ModConfigSpec.ConfigValue<?> v) {
                rows.add(new Entry(setting(spec, v, world)));
            } else if (e.getValue() instanceof UnmodifiableConfig sub) {
                rows.add(new Heading(groupTitle(spec, at), null, depth));
                walk(spec, sub, at, rows, depth + 1, world);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Setting setting(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> value, boolean world) {
        ModConfigSpec.ValueSpec vs = value.getSpec();
        List<String> path = value.getPath();
        String label = vs.getTranslationKey() != null ? vs.getTranslationKey() : ServerConfig.label(path.get(path.size() - 1));
        return new Setting(spec, (ModConfigSpec.ConfigValue<Object>) value, label, ServerConfig.help(vs.getComment()), world);
    }

    private static String groupTitle(ModConfigSpec spec, List<String> path) {
        String key = spec.getLevelTranslationKey(path);
        return key != null ? key : ServerConfig.label(path.get(path.size() - 1));
    }

    /** The weapons that have abilities with settings, for the picker. */
    private static List<WeaponDefinition> weapons() {
        List<WeaponDefinition> out = new ArrayList<>();
        for (WeaponDefinition w : Weapons.all()) {
            if (w.abilities().stream().anyMatch(a -> !a.params().isEmpty())) out.add(w);
        }
        return out;
    }

    private List<Row> rows(Section s) {
        if (!s.abilities()) return s.rows();
        List<Row> rows = new ArrayList<>(s.rows());
        List<WeaponDefinition> ws = weapons();
        if (ws.isEmpty()) return rows;
        WeaponDefinition w = ws.get(Math.floorMod(weaponIndex, ws.size()));
        UnmodifiableConfig abilities = (UnmodifiableConfig) ServerConfig.SPEC.getValues().valueMap().get("abilities");
        if (!(abilities.valueMap().get(w.id()) instanceof UnmodifiableConfig weapon)) return rows;
        for (var e : weapon.valueMap().entrySet()) {
            if (!(e.getValue() instanceof UnmodifiableConfig ability)) continue;
            AbilityDefinition a = w.ability(e.getKey());
            String name = a != null ? a.name() : ServerConfig.label(e.getKey());
            String note = a != null ? Tr.f("%s · from weapon level %s", Tr.s(a.kind().displayName()), a.unlockLevel()) : null;
            rows.add(new Heading(name, note, 0));
            for (var p : ability.valueMap().entrySet()) {
                if (p.getValue() instanceof ModConfigSpec.ConfigValue<?> v) rows.add(new Entry(setting(ServerConfig.SPEC, v, true)));
            }
        }
        return rows;
    }

    private void refresh() {
        shown = sections.isEmpty() ? List.of() : rows(sections.get(sectionIndex));
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private Section current() {
        return sections.isEmpty() ? null : sections.get(sectionIndex);
    }

    // ---- values ----

    /** Whether the world's settings can be changed here: only in a world run by this game (singleplayer or LAN host). */
    private boolean worldEditable() {
        return minecraft.hasSingleplayerServer();
    }

    private boolean editable(Setting s) {
        return !s.world() || worldEditable();
    }

    private static String shownValue(Object v) {
        if (v instanceof Boolean b) return Tr.s(b ? "On" : "Off");
        if (v instanceof Enum<?> e) return Tr.s(ServerConfig.label(e.name().toLowerCase(Locale.ROOT)));
        if (v instanceof Double d) return number(d);
        if (v instanceof Float f) return number(f);
        return String.valueOf(v);
    }

    private static String number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
        return new BigDecimal(d).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void set(Setting s, Object v) {
        if (!editable(s) || !s.valueSpec().test(v)) return;
        s.value().set(v);
        s.spec().save();
        if (s.value() == (Object) ClientConfig.PUNCHY_CODEX_OWN_ANIMATION) com.fantasyweapons.compat.punchy.PunchyCompat.sync();
    }

    private void click(Setting s, boolean back) {
        if (!editable(s)) {
            deny();
            return;
        }
        Object v = s.value().get();
        if (v instanceof Boolean b) {
            set(s, !b);
        } else if (v instanceof Enum<?> e) {
            Object[] all = e.getDeclaringClass().getEnumConstants();
            int at = e.ordinal();
            for (int i = 1; i <= all.length; i++) {
                Object next = all[Math.floorMod(at + (back ? -i : i), all.length)];
                if (s.valueSpec().test(next)) {
                    set(s, next);
                    break;
                }
            }
        } else {
            commitEdit();
            editing = s;
            editText = shownValue(v);
        }
        playClick();
    }

    /** Keeps the number being typed if it is one and within its range, else drops it (flashing red). */
    private void commitEdit() {
        if (editing == null) return;
        Setting s = editing;
        editing = null;
        Object def = s.value().getDefault();
        Object parsed;
        try {
            String t = editText.trim().replace(',', '.');
            if (def instanceof Integer) parsed = (int) Math.round(Double.parseDouble(t));
            else if (def instanceof Long) parsed = Math.round(Double.parseDouble(t));
            else parsed = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            parsed = null;
        }
        if (parsed == null || !s.valueSpec().test(parsed)) {
            refused = s;
            refusedAt = Util.getMillis();
            return;
        }
        set(s, parsed);
    }

    private boolean isDefault(Setting s) {
        Object v = s.value().get(), d = s.value().getDefault();
        if (v instanceof Number a && d instanceof Number b) return Math.abs(a.doubleValue() - b.doubleValue()) < 1e-9;
        return v.equals(d);
    }

    private void resetSection() {
        Section s = current();
        if (s == null || s.world() && !worldEditable()) return;
        for (Row r : shown) {
            if (r instanceof Entry e && !isDefault(e.setting())) set(e.setting(), e.setting().value().getDefault());
        }
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void deny() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.2f, 0.4f));
    }

    // ---- layout ----

    private int bodyLeft() {
        return left + BODY_X;
    }

    private int bodyRight() {
        return left + W - 10;
    }

    private int listTop() {
        Section s = current();
        return top + BODY_TOP + 4 + (s != null && s.abilities() ? PICKER_H : 0);
    }

    private int listBottom() {
        return top + H - FOOT - 6;
    }

    private int rowHeight(Row r) {
        return r instanceof Heading h ? (h.depth() == 0 ? 16 : 13) : ROW;
    }

    private float maxScroll() {
        int total = 0;
        for (Row r : shown) total += rowHeight(r);
        return Math.max(0, total - (listBottom() - listTop()));
    }

    private int valueX() {
        return bodyRight() - 8 - VALUE_W - 14;
    }

    private int tabY(int i) {
        // the game's sections, then a gap and the world's
        int clientCount = (int) sections.stream().filter(s -> !s.world()).count();
        int y = top + BODY_TOP + 14 + i * (TAB + 2);
        if (i >= clientCount) y += 16;
        return y;
    }

    private int footY() {
        return top + H - FOOT + 2;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int realX = mouseX, realY = mouseY;
        mouseX = (int) zoom.in(mouseX);
        mouseY = (int) zoom.in(mouseY);
        hovered = null;
        String tip = null;
        float time = Util.getMillis() / 50f;
        UiDraw.style(UiDraw.NETHERITE, -1, GOLD, GOLD_LIGHT, MUTED);
        zoom.push(g);
        try {
            UiDraw.panel(g, left, top, W, H, THEME, 1f);
            UiDraw.dyeRibbon(Colors.lerpRgb(THEME, 0x200810, 0.35f));
            UiDraw.banner(g, left + W / 2f, top + 2, 190, 22, 1f);
            UiDraw.title(g, Tr.s("Settings").toUpperCase(Locale.ROOT), left + W / 2f, top + 8, Colors.alpha(1f, GOLD_LIGHT), 0.95f);
            tip = sidebar(g, mouseX, mouseY, time);
            body(g, mouseX, mouseY, time);
            footer(g, mouseX, mouseY);
        } finally {
            zoom.pop(g);
            UiDraw.resetTheme();
        }
        if (hovered != null) g.renderTooltip(font, tooltip(hovered), realX, realY);
        else if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 220), realX, realY);
    }

    /** The sections, and the game's language and controls; returns a tooltip for what is under the mouse, if any. */
    private String sidebar(GuiGraphics g, int mouseX, int mouseY, float time) {
        int x = left + SIDE_X;
        String tip = null;
        int clientCount = (int) sections.stream().filter(s -> !s.world()).count();
        UiDraw.text(g, Tr.s("This game").toUpperCase(Locale.ROOT), x + 2, top + BODY_TOP + 3, Colors.alpha(1f, MUTED), 0.5f, false);
        if (sections.size() > clientCount) {
            UiDraw.text(g, Tr.s("This world").toUpperCase(Locale.ROOT), x + 2, tabY(clientCount) - 11, Colors.alpha(1f, MUTED), 0.5f, false);
        }
        for (int i = 0; i < sections.size(); i++) {
            int y = tabY(i);
            boolean on = i == sectionIndex, over = inside(mouseX, mouseY, x, y, SIDE_W, TAB);
            UiDraw.button(g, x, y, SIDE_W, TAB, on ? 1 : over ? 1 : 0, on ? 1f : 0.9f);
            if (on) UiDraw.glowBorder(g, x, y, x + SIDE_W, y + TAB, THEME, 0.55f + 0.15f * (float) Math.sin(time * 0.2f), 3);
            int c = on ? GOLD_LIGHT : over ? 0xFFFFFF : GOLD;
            UiDraw.text(g, fit(Tr.s(sections.get(i).title()), SIDE_W - 10, 0.66f), x + 6, y + 4.5f, Colors.alpha(1f, c), 0.66f, true);
        }
        if (!ServerConfig.SPEC.isLoaded()) {
            UiDraw.wrap(g, Tr.s("The world's settings show once a world is open."), x + 2, tabY(clientCount) + 2, SIDE_W - 4, Colors.alpha(1f, MUTED), 0.5f, 7);
        }
        // the game's own language and controls, where the mod's keys are
        int by = footY() - 2 * (TAB + 2);
        String[] labels = {Tr.s("Language..."), Tr.s("Controls...")};
        for (int i = 0; i < 2; i++) {
            int y = by + i * (TAB + 2);
            boolean over = inside(mouseX, mouseY, x, y, SIDE_W, TAB);
            UiDraw.button(g, x, y, SIDE_W, TAB, over ? 1 : 0, 0.9f);
            UiDraw.textCentered(g, labels[i], x + SIDE_W / 2f, y + 4.5f, Colors.alpha(1f, over ? 0xFFFFFF : MUTED), 0.62f, true);
            if (over) tip = i == 0 ? Tr.s("The game's language, which the mod follows") : Tr.s("The keys, the mod's among them (under Fantasy Weapons)");
        }
        return tip;
    }

    private void body(GuiGraphics g, int mouseX, int mouseY, float time) {
        int x0 = bodyLeft(), x1 = bodyRight(), y0 = top + BODY_TOP, y1 = listBottom() + 2;
        UiDraw.rect(g, x0, y0, x1, y1, 0xE0120D18);
        UiDraw.border(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 1, Colors.alpha(0.8f, LINE));
        Section s = current();
        if (s == null) return;
        if (s.abilities()) picker(g, mouseX, mouseY);
        int lt = listTop(), lb = listBottom();
        zoom.scissor(g, x0, lt, x1, lb);
        try {
            int y = lt - Math.round(scroll);
            boolean odd = false;
            for (Row r : shown) {
                int h = rowHeight(r);
                if (y + h >= lt && y <= lb) {
                    if (r instanceof Heading hd) heading(g, hd, x0, y, x1);
                    else if (r instanceof Entry e) entry(g, e.setting(), x0, y, x1, odd, mouseX, mouseY, lt, lb, time);
                }
                if (r instanceof Entry) odd = !odd;
                else odd = false;
                y += h;
            }
        } finally {
            g.disableScissor();
        }
        // a thin scrollbar when the list is longer than the view
        float max = maxScroll();
        if (max > 0) {
            float view = lb - lt, total = view + max;
            float bar = Math.max(14, view * view / total), by = lt + (view - bar) * (scroll / max);
            UiDraw.rect(g, x1 - 3, lt, x1 - 1, lb, 0x40FFFFFF);
            UiDraw.rect(g, x1 - 3, by, x1 - 1, by + bar, Colors.alpha(0.9f, GOLD));
        }
    }

    private void picker(GuiGraphics g, int mouseX, int mouseY) {
        List<WeaponDefinition> ws = weapons();
        if (ws.isEmpty()) return;
        WeaponDefinition w = ws.get(Math.floorMod(weaponIndex, ws.size()));
        int x0 = bodyLeft() + 4, x1 = bodyRight() - 4, y = top + BODY_TOP + 3;
        for (int dir : new int[]{-1, 1}) {
            int bx = dir < 0 ? x0 : x1 - 16;
            boolean over = inside(mouseX, mouseY, bx, y, 16, 15);
            UiDraw.button(g, bx, y, 16, 15, over ? 1 : 0, 1f);
            UiDraw.textCentered(g, dir < 0 ? "<" : ">", bx + 8, y + 4, Colors.alpha(1f, over ? 0xFFFFFF : GOLD), 0.75f, true);
        }
        int theme = w.themePrimary();
        float cx = (x0 + x1) / 2f;
        String name = w.displayName();
        float tw = font.width(name) * 0.8f;
        var item = ModItems.WEAPONS.get(w.id());
        if (item != null) {
            g.pose().pushPose();
            g.pose().translate(cx - tw / 2f - 16, y - 1, 0);
            g.pose().scale(0.95f, 0.95f, 1);
            g.renderItem(new ItemStack(item.get()), 0, 0);
            g.pose().popPose();
        }
        UiDraw.text(g, name, cx - tw / 2f + 2, y + 3.5f, Colors.alpha(1f, Colors.lerpRgb(theme, 0xFFFFFF, 0.45f)), 0.8f, true);
        UiDraw.textRight(g, (Math.floorMod(weaponIndex, ws.size()) + 1) + " / " + ws.size(), x1 - 22, y + 5, Colors.alpha(1f, MUTED), 0.5f, false);
    }

    private void heading(GuiGraphics g, Heading h, int x0, int y, int x1) {
        float scale = h.depth() == 0 ? 0.72f : 0.62f;
        int tx = x0 + 6 + h.depth() * 8;
        String text = Tr.s(h.text());
        UiDraw.text(g, text, tx, y + (h.depth() == 0 ? 5 : 3.5f), Colors.alpha(1f, h.depth() == 0 ? GOLD_LIGHT : GOLD), scale, true);
        float after = tx + font.width(text) * scale + 6;
        if (h.note() != null) {
            UiDraw.text(g, h.note(), after, y + 6, Colors.alpha(1f, MUTED), 0.5f, false);
            after += font.width(h.note()) * 0.5f + 6;
        }
        float ly = y + (h.depth() == 0 ? 9 : 7);
        if (after < x1 - 10) UiDraw.hGradient(g, after, ly, x1 - 8, ly + 0.6f, Colors.alpha(0.6f, LINE), Colors.alpha(0f, LINE));
    }

    private void entry(GuiGraphics g, Setting s, int x0, int y, int x1, boolean odd, int mouseX, int mouseY, int lt, int lb, float time) {
        boolean visible = mouseY >= lt && mouseY < lb;
        boolean rowOver = visible && inside(mouseX, mouseY, x0, y, x1 - x0 - 4, ROW);
        if (odd) UiDraw.rect(g, x0 + 1, y, x1 - 4, y + ROW, 0x14FFFFFF);
        if (rowOver) UiDraw.rect(g, x0 + 1, y, x1 - 4, y + ROW, 0x1EF2C46B);
        boolean can = editable(s);
        int vx = valueX();
        String label = fit(Tr.s(s.label()), vx - x0 - 14, 0.62f);
        UiDraw.text(g, label, x0 + 8, y + 4.5f, Colors.alpha(1f, can ? 0xE8DCC0 : 0x9A927E), 0.62f, false);
        if (rowOver && mouseX < vx) hovered = s;
        // the value
        Object v = s.value().get();
        boolean valueOver = visible && can && inside(mouseX, mouseY, vx, y + 1, VALUE_W, ROW - 2);
        if (valueOver) hovered = s;
        boolean typing = editing == s;
        boolean flash = refused == s && Util.getMillis() - refusedAt < 900;
        if (v instanceof Boolean b) {
            int c = b ? GOOD : BAD;
            UiDraw.rect(g, vx, y + 2, vx + VALUE_W, y + ROW - 2, Colors.alpha(valueOver ? 0.42f : 0.28f, c));
            UiDraw.border(g, vx, y + 2, vx + VALUE_W, y + ROW - 2, 1, Colors.alpha(0.9f, c));
            float knob = b ? vx + VALUE_W - 10 : vx + 2;
            UiDraw.rect(g, knob, y + 4, knob + 8, y + ROW - 4, Colors.alpha(can ? 1f : 0.5f, Colors.lerpRgb(c, 0xFFFFFF, 0.3f)));
            UiDraw.textCentered(g, shownValue(b), vx + VALUE_W / 2f + (b ? -5 : 5), y + 5, Colors.alpha(1f, can ? 0xFFFFFF : 0xB0A890), 0.58f, true);
        } else {
            UiDraw.button(g, vx, y + 1, VALUE_W, ROW - 2, typing ? 1 : valueOver ? 1 : can ? 0 : 2, can ? 1f : 0.6f);
            if (flash) UiDraw.glowBorder(g, vx, y + 1, vx + VALUE_W, y + ROW - 1, 0xF04A3A, 0.9f, 3);
            else if (typing) UiDraw.glowBorder(g, vx, y + 1, vx + VALUE_W, y + ROW - 1, THEME, 0.7f + 0.2f * (float) Math.sin(time * 0.4f), 3);
            String text;
            if (typing) {
                boolean caret = (Util.getMillis() / 400) % 2 == 0;
                text = editText + (caret ? "_" : " ");
            } else if (v instanceof Enum<?>) {
                text = "‹ " + shownValue(v) + " ›";
            } else {
                text = shownValue(v);
            }
            int c = flash ? 0xFF8A7A : typing ? 0xFFFFFF : can ? GOLD_LIGHT : 0xB0A890;
            UiDraw.textCentered(g, fit(text, VALUE_W - 8, 0.62f), vx + VALUE_W / 2f, y + 4.5f, Colors.alpha(1f, c), 0.62f, true);
        }
        // put back to the default
        if (!isDefault(s)) {
            int rx = vx + VALUE_W + 3;
            boolean over = visible && can && inside(mouseX, mouseY, rx, y + 2, 11, ROW - 4);
            UiDraw.textCentered(g, "↺", rx + 5.5f, y + 3.5f, Colors.alpha(can ? 1f : 0.4f, over ? 0xFFFFFF : GOLD), 0.75f, true);
        }
    }

    private void footer(GuiGraphics g, int mouseX, int mouseY) {
        int y = footY();
        int dw = 92, dx = left + W / 2 - dw / 2;
        boolean over = inside(mouseX, mouseY, dx, y, dw, 16);
        UiDraw.button(g, dx, y, dw, 16, over ? 1 : 0, 1f);
        UiDraw.textCentered(g, Tr.s("Done"), dx + dw / 2f, y + 4.5f, Colors.alpha(1f, over ? 0xFFFFFF : GOLD), 0.72f, true);
        Section s = current();
        if (s == null) return;
        // how the section's settings are kept
        String status;
        int sc;
        if (!s.world()) {
            status = Tr.s("Saved at once, for this game");
            sc = GOOD;
        } else if (worldEditable()) {
            status = Tr.s("Saved at once, for this world");
            sc = GOOD;
        } else {
            status = Tr.s("Set by the server: shown, not changed");
            sc = BAD;
        }
        UiDraw.text(g, fit(status, dx - left - SIDE_X - 6, 0.52f), left + SIDE_X + 2, y + 5, Colors.alpha(1f, sc), 0.52f, false);
        // the whole section back to its defaults, asking once more first
        boolean can = !s.world() || worldEditable();
        int rw = 92, rx = bodyRight() - rw;
        boolean asking = Util.getMillis() - resetAskedAt < 4000;
        boolean rover = can && inside(mouseX, mouseY, rx, y, rw, 16);
        UiDraw.button(g, rx, y, rw, 16, can ? rover ? 1 : 0 : 2, can ? 1f : 0.55f);
        String label = asking ? Tr.s("Sure? Click again") : Tr.s("Reset section");
        UiDraw.textCentered(g, fit(label, rw - 8, 0.62f), rx + rw / 2f, y + 5, Colors.alpha(1f, asking ? BAD : rover ? 0xFFFFFF : can ? GOLD : 0x9A927E), 0.62f, true);
    }

    private List<FormattedCharSequence> tooltip(Setting s) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(Tr.s(s.label())).withColor(GOLD), 240));
        if (!s.help().isEmpty()) lines.addAll(font.split(Component.literal(Tr.s(s.help())).withColor(0xD8CCB0), 240));
        Object d = s.value().getDefault();
        lines.add(Component.literal(Tr.f("Default: %s", shownValue(d))).withColor(MUTED).getVisualOrderText());
        ModConfigSpec.Range<?> range = s.valueSpec().getRange();
        if (range != null) {
            lines.add(Component.literal(Tr.f("From %s to %s", shownValue(range.getMin()), shownValue(range.getMax()))).withColor(MUTED).getVisualOrderText());
        }
        if (!editable(s)) lines.add(Component.literal(Tr.s("Set by the server")).withColor(BAD).getVisualOrderText());
        return lines;
    }

    /** {@code s} cut to {@code width} menu units at {@code scale}, ending in "..." if cut. */
    private String fit(String s, float width, float scale) {
        if (font.width(s) * scale <= width) return s;
        return font.plainSubstrByWidth(s, (int) (width / scale) - font.width("...")) + "...";
    }

    /** DEVELOPMENT ONLY (screenshot director): shows a section (and for the abilities, a weapon). */
    public void debugShow(int section, int weapon) {
        sectionIndex = Math.max(0, Math.min(section, sections.size() - 1));
        weaponIndex = weapon;
        scroll = 0;
        refresh();
    }

    public int debugSections() {
        return sections.size();
    }

    // ---- input ----

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = zoom.in(mouseX), my = zoom.in(mouseY);
        boolean back = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        int x = left + SIDE_X;
        for (int i = 0; i < sections.size(); i++) {
            if (inside(mx, my, x, tabY(i), SIDE_W, TAB)) {
                commitEdit();
                if (i != sectionIndex) {
                    sectionIndex = i;
                    scroll = 0;
                    resetAskedAt = 0;
                    refresh();
                }
                playClick();
                return true;
            }
        }
        int by = footY() - 2 * (TAB + 2);
        if (inside(mx, my, x, by, SIDE_W, TAB)) {
            commitEdit();
            playClick();
            minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
            return true;
        }
        if (inside(mx, my, x, by + TAB + 2, SIDE_W, TAB)) {
            commitEdit();
            playClick();
            minecraft.setScreen(new KeyBindsScreen(this, minecraft.options));
            return true;
        }
        int fy = footY();
        if (inside(mx, my, left + W / 2f - 46, fy, 92, 16)) {
            playClick();
            onClose();
            return true;
        }
        Section s = current();
        if (s != null && inside(mx, my, bodyRight() - 92, fy, 92, 16)) {
            commitEdit();
            if (s.world() && !worldEditable()) {
                deny();
            } else if (Util.getMillis() - resetAskedAt < 4000) {
                resetSection();
                resetAskedAt = 0;
                playClick();
            } else {
                resetAskedAt = Util.getMillis();
                playClick();
            }
            return true;
        }
        if (s != null && s.abilities()) {
            int py = top + BODY_TOP + 3;
            for (int dir : new int[]{-1, 1}) {
                int bx = dir < 0 ? bodyLeft() + 4 : bodyRight() - 4 - 16;
                if (inside(mx, my, bx, py, 16, 15)) {
                    commitEdit();
                    weaponIndex += dir;
                    scroll = 0;
                    refresh();
                    playClick();
                    return true;
                }
            }
        }
        int lt = listTop(), lb = listBottom();
        if (my >= lt && my < lb && mx >= bodyLeft() && mx < bodyRight()) {
            int y = lt - Math.round(scroll);
            for (Row r : shown) {
                int h = rowHeight(r);
                if (r instanceof Entry e && my >= y && my < y + h) {
                    Setting st = e.setting();
                    int vx = valueX();
                    if (inside(mx, my, vx, y + 1, VALUE_W, ROW - 2)) {
                        click(st, back);
                        return true;
                    }
                    if (!isDefault(st) && inside(mx, my, vx + VALUE_W + 3, y + 2, 11, ROW - 4)) {
                        if (editable(st)) {
                            commitEdit();
                            set(st, st.value().getDefault());
                            playClick();
                        } else {
                            deny();
                        }
                        return true;
                    }
                }
                y += h;
            }
        }
        commitEdit();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = (float) Math.max(0, Math.min(maxScroll(), scroll - scrollY * ROW * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editing != null) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitEdit();
                case GLFW.GLFW_KEY_ESCAPE -> editing = null;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
                }
                default -> {
                    return true;
                }
            }
            return true;
        }
        Section s = current();
        if (s != null && s.abilities() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            weaponIndex += key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            scroll = 0;
            refresh();
            playClick();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            sectionIndex = Math.floorMod(sectionIndex + (key == GLFW.GLFW_KEY_UP ? -1 : 1), Math.max(1, sections.size()));
            scroll = 0;
            refresh();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (editing == null) return super.charTyped(c, modifiers);
        if ((Character.isDigit(c) || c == '.' || c == ',' || c == '-' || c == 'e' || c == 'E') && editText.length() < 24) editText += c;
        return true;
    }
}
