package com.fantasyweapons.client.guide;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.client.input.KeyBindings;
import com.fantasyweapons.forge.ForgeRecipe;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.Util;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the Wayfarer's Codex says, page by page: the way to a legendary weapon (its ore, the Refinery, the Crafting
 * Table), then every weapon with its abilities, its forging pattern and its armor, then mastery and the reader's deeds.
 * Pictures and recipes are the game's own items and recipes, so the book always shows what the game really asks for.
 * Two pages make a spread; chapters open on a left page.
 */
public final class CodexBook {
    private final Minecraft mc;
    private final List<Consumer<PageCanvas>> pages = new ArrayList<>();
    private final List<String> chapter = new ArrayList<>();
    private final Map<String, Integer> anchors = new LinkedHashMap<>();
    private String current = "";

    CodexBook(Minecraft mc) {
        this.mc = mc;
        page(this::title);
        page(this::contents);
        path();
        ores();
        refinery();
        table();
        weapons();
        ultimates();
        mastery();
        deeds();
        if (pages.size() % 2 == 1) page(c -> ornament(c, "Fin"));
    }

    public int spreads() {
        return (pages.size() + 1) / 2;
    }

    /** Draws page {@code left ? 2s : 2s + 1} of spread {@code s}, with its running head, number and the ways onward. */
    void draw(int spread, boolean left, PageCanvas c) {
        int i = spread * 2 + (left ? 0 : 1);
        float s = 0.52f, y = PageCanvas.H - PageCanvas.MARGIN - 9, outer = PageCanvas.MARGIN, far = PageCanvas.W - PageCanvas.MARGIN;
        if (i < pages.size()) {
            pages.get(i).accept(c);
            // the chapter's name at the outer top corner, beside the frame's ornament
            if (i > 1 && !chapter.get(i).isEmpty()) {
                String head = chapter.get(i).toUpperCase(Locale.ROOT);
                float hs = 0.42f;
                while (c.width(head, hs) > 34 && head.length() > 4) head = head.substring(0, head.length() - 2).trim();
                c.text(head, left ? outer : far - c.width(head, hs), 8.5f, PageCanvas.FADED, hs);
            }
            String n = String.valueOf(i + 1);
            c.text(n, left ? outer + 9 : far - 9 - c.width(n, s), y + 0.6f, PageCanvas.FADED, s);
        }
        // the ways onward, in the bottom corners (the frame's ornament holds the middle)
        if (left) {
            if (spread > 0) c.link("\u00AB", outer - 1, y - 0.4f, 0.9f, Codex::previous);
            if (spread > 0) c.link("Contents", far - c.width("Contents", s), y + 0.6f, s, () -> Codex.turnTo(0, true));
        } else {
            if (spread < spreads() - 1) c.link("\u00BB", far - 5, y - 0.4f, 0.9f, Codex::next);
            if (Codex.canGoBack()) c.link("Back", outer, y + 0.6f, s, Codex::goBack);
            if (!c.leaf) tabs(c, i);
        }
    }

    /**
     * The chapters' thumb tabs, standing out past the right page's gilded rail and the cover's edge (the open chapter's
     * furthest), down the upper part of the page where the reader's hand does not cover them; click one to turn there.
     */
    private void tabs(PageCanvas c, int page) {
        String here = page < chapter.size() ? chapter.get(page) : "";
        // the nine tabs end where the hand holding the book begins
        float h = 9.2f, gap = 0.6f, y = 8, z = 0.02f;
        // out in front of the gilded rail and the cover's edge
        c.lift = 0.6f;
        for (Tab tab : TABS) {
            Integer at = anchors.get(tab.anchor());
            if (at == null) continue;
            boolean open = at < chapter.size() && chapter.get(at).equals(here) && !here.isEmpty();
            float x0 = PageCanvas.W + 4, x1 = PageCanvas.W + (open ? 28 : 24);
            c.region(x0, y, x1, y + h, List.of(Component.literal(tab.name())), go(tab.anchor()));
            boolean hover = c.hovered(x0, y, x1, y + h);
            int body = hover || open ? 0xFF000000 | tab.color() : 0xFF000000 | deepen(tab.color(), 0.78f);
            // the tab's end is cut in, like a ribbon's
            notched(c, x0, y, x1, y + h, NOTCH, 0xFF2A1A0C, z);
            notched(c, x0, y + 0.6f, x1 - 0.8f, y + h - 0.6f, NOTCH - 0.5f, body, z + 0.01f);
            c.quad(x0, y + 0.6f, x0, y + 1.4f, x1 - 0.7f - NOTCH * 0.8f / (h / 2), y + 1.4f, x1 - 0.7f, y + 0.6f, 0x55FFFFFF, z + 0.02f);
            c.icon(TAB_ICONS, tab.icon(), 9, x0 + 2.2f, y + 1.1f, h - 2.2f);
            y += h + gap;
        }
        c.lift = 0;
    }

    /** How deep the cut in a tab's end goes (sheet units). */
    private static final float NOTCH = 5.2f;

    /** A tab's shape: a rectangle whose outer end is cut in to a point at its middle, {@code depth} deep. */
    private static void notched(PageCanvas c, float x0, float y0, float x1, float y1, float depth, int argb, float z) {
        float mid = (y0 + y1) / 2;
        c.quad(x0, y0, x0, mid, x1 - depth, mid, x1, y0, argb, z);
        c.quad(x0, mid, x0, y1, x1, y1, x1 - depth, mid, argb, z);
    }

    /** A chapter's thumb tab, with its flat icon (by place in {@link #TAB_ICONS}). */
    private record Tab(String anchor, String name, int color, int icon) {
    }

    /** The tabs' pixel icons: the book, an ore, the furnace, the anvil, a sword, a chestplate, a flask, a star compass, a shattered world. */
    private static final net.minecraft.resources.ResourceLocation TAB_ICONS = FantasyWeapons.id("textures/gui/codex/tabs.png");

    private static final Tab[] TABS = {
            new Tab("path", "The Path of Legends", 0x9A2A1E, 0),
            new Tab("ores", "Legendary Ores", 0x8A6A3A, 1),
            new Tab("refinery", "The Arcane Refinery", 0x2F7F8C, 2),
            new Tab("table", "The Legendary Crafting Table", 0xB08A2E, 3),
            new Tab("weapons", "The Thirteen Weapons", 0x5E3A8C, 4),
            new Tab("armor", "Armor of Legend", 0x5A6470, 5),
            new Tab("ultimates", "The Ultimate Weapons", 0x8C1E12, 8),
            new Tab("mastery", "Mastery", 0x3C7A3A, 6),
            new Tab("deeds", "Deeds", 0x7A3FA8, 7)};

    private static int deepen(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }

    // ---- building ----

    private void page(Consumer<PageCanvas> page) {
        pages.add(page);
        chapter.add(current);
    }

    /** Starts a chapter on the next left page. */
    private void chapter(String name, String anchor) {
        if (pages.size() % 2 == 1) page(c -> ornament(c, null));
        current = name;
        anchors.put(anchor, pages.size());
    }

    public int spreadOf(String anchor) {
        Integer page = anchors.get(anchor);
        return page == null ? 0 : page / 2;
    }

    private Runnable go(String anchor) {
        return () -> Codex.turnTo(spreadOf(anchor), true);
    }

    // ---- the front ----

    private void title(PageCanvas c) {
        c.y = PageCanvas.TOP;
        c.centered("The Wayfarer's", c.y, PageCanvas.RED, 1.1f);
        c.y += 11;
        c.centered("Codex", c.y, PageCanvas.RED, 1.6f);
        c.y += 16;
        c.rule();
        c.centered("of the Thirteen Legendary Arms", c.y, PageCanvas.FADED, 0.55f);
        c.y += 6;
        if (mc.player != null) c.centered("carried by " + mc.player.getGameProfile().getName(), c.y, PageCanvas.FADED, 0.42f);
        c.y += 4;
        // the thirteen weapons in a ring
        List<WeaponDefinition> all = new ArrayList<>(Weapons.blades());
        float cx = PageCanvas.W / 2, cy = c.y + 25, r = 22, size = 11;
        for (int i = 0; i < all.size(); i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / all.size();
            c.item(weapon(all.get(i)), cx + (float) Math.cos(a) * r - size / 2, cy + (float) Math.sin(a) * r - size / 2, size);
        }
        c.item(new ItemStack(ModItems.CODEX.get()), cx - 8, cy - 8, 16);
        c.y = cy + 31;
        c.para("Thirteen weapons of legend lie sealed in ores no common pick can find. This book is the road to each.",
                PageCanvas.INK, 0.56f);
    }

    private void contents(PageCanvas c) {
        c.y = PageCanvas.TOP;
        c.heading("Contents");
        c.gap(2);
        String[][] entries = {{"The Path of Legends", "path"}, {"Legendary Ores", "ores"}, {"The Arcane Refinery", "refinery"},
                {"The Crafting Table", "table"}, {"The Thirteen Weapons", "weapons"}, {"Armor of Legend", "armor"},
                {"The Mage's Arts", "mage"}, {"The Ultimate Weapons", "ultimates"}, {"Mastery", "mastery"}, {"Deeds", "deeds"}};
        for (String[] e : entries) {
            int page = anchors.getOrDefault(e[1], 0) + 1;
            float s = 0.68f;
            c.link(e[0], PageCanvas.MARGIN, c.y, s, go(e[1]));
            String n = String.valueOf(page);
            c.text(n, PageCanvas.W - PageCanvas.MARGIN - c.width(n, s), c.y, PageCanvas.FADED, s);
            // dotted leader
            float from = PageCanvas.MARGIN + c.width(e[0], s) + 2, to = PageCanvas.W - PageCanvas.MARGIN - c.width(n, s) - 2;
            for (float x = from; x < to; x += 2.5f) c.fill(x, c.y + 5, x + 0.7f, c.y + 5.7f, 0x883A2614);
            c.y += 9.6f;
        }
        c.gap(2);
        c.para("Click a chapter to turn to it.", PageCanvas.FADED, 0.5f);
    }

    private void ornament(PageCanvas c, @Nullable String word) {
        c.y = PageCanvas.H / 2 - 10;
        c.rule();
        if (word != null) {
            c.centered(word, c.y, PageCanvas.FADED, 0.8f);
            c.y += 9;
            c.rule();
        }
    }

    // ---- the road ----

    private void path() {
        chapter("The Path of Legends", "path");
        String[][] steps = {
                {"Seek the ores", "Each weapon's ore glows somewhere in the Overworld, the Nether or the End."},
                {"Mine them", "Only a diamond pickaxe or better breaks one."},
                {"Refine", "An Arcane Refinery melts 8 raw and 8 of an additive into a core."},
                {"Build the Table", "The Legendary Crafting Table holds the 9x9 patterns."},
                {"Forge", "Lay the weapon's pattern on the grid and take it."},
                {"Master it", "It grows as it fights: abilities, then an ultimate."},
                {"Wear its armor", "A full set wakes a power of its own."}};
        Item[] icons = {ModItems.ORES.get(Material.SOLARITE).get().asItem(), Items.DIAMOND_PICKAXE, ModItems.ARCANE_REFINERY.get(),
                ModItems.LEGENDARY_CRAFTING_TABLE.get(), ModItems.WEAPONS.get("solaris").get(), Items.EXPERIENCE_BOTTLE,
                ModItems.ARMOR.get(ArmorSet.SOLARIS).get(ArmorItem.Type.CHESTPLATE).get()};
        for (int half = 0; half < 2; half++) {
            int from = half == 0 ? 0 : 4, to = half == 0 ? 4 : steps.length;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(from == 0 ? "The Path of Legends" : "...and Beyond");
                for (int i = from; i < to; i++) step(c, i + 1, steps[i][0], steps[i][1], new ItemStack(icons[i]));
            });
        }
    }

    private void step(PageCanvas c, int n, String name, String text, ItemStack icon) {
        float top = c.y;
        c.item(icon, PageCanvas.MARGIN, top, 14);
        c.text(n + ". " + name, PageCanvas.MARGIN + 17, top + 1, PageCanvas.RED, 0.68f);
        c.y = top + 9;
        float s = 0.52f;
        for (var line : Minecraft.getInstance().font.split(Component.literal(text), (int) ((c.inner() - 17) / s))) {
            c.text(line, PageCanvas.MARGIN + 17, c.y, PageCanvas.INK, s);
            c.y += 9 * s;
        }
        c.y = Math.max(c.y, top + 16) + 5;
    }

    // ---- ores ----

    private enum Realm {
        OVERWORLD("The Overworld"), NETHER("The Nether"), END("The End");

        final String title;

        Realm(String title) {
            this.title = title;
        }

        static Realm of(Material m) {
            String where = m.found();
            return where.contains("Nether") ? NETHER : where.contains("End") ? END : OVERWORLD;
        }
    }

    private void ores() {
        chapter("Legendary Ores", "ores");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Legendary Ores");
            c.para("One ore for each weapon, found where its element is strongest: fire and blood in the Nether, void and "
                    + "stars in the End, the rest in the Overworld. They are rare, glow faintly, and need a diamond pickaxe.",
                    PageCanvas.INK, 0.54f);
            for (Realm realm : Realm.values()) {
                float x = PageCanvas.MARGIN;
                c.link(realm.title, x, c.y, 0.62f, go("realm_" + realm.name()));
                c.y += 7;
                for (Material m : Material.values()) {
                    if (Realm.of(m) != realm) continue;
                    c.item(new ItemStack(ModItems.ORES.get(m).get()), x, c.y, 11);
                    x += 12;
                }
                c.y += 13;
            }
        });
        for (Realm realm : Realm.values()) {
            List<Material> here = new ArrayList<>();
            for (Material m : Material.values()) if (Realm.of(m) == realm) here.add(m);
            for (int i = 0; i < here.size(); i += 3) {
                if (i == 0) anchors.put("realm_" + realm.name(), pages.size());
                List<Material> on = here.subList(i, Math.min(here.size(), i + 3));
                boolean first = i == 0;
                page(c -> {
                    c.y = PageCanvas.TOP;
                    c.heading(first ? realm.title : realm.title + " (cont.)");
                    for (Material m : on) ore(c, m);
                });
            }
        }
    }

    private void ore(PageCanvas c, Material m) {
        float top = c.y;
        c.item(new ItemStack(ModItems.ORES.get(m).get()), PageCanvas.MARGIN, top, 16);
        c.text(m.oreName(), PageCanvas.MARGIN + 19, top, deep(m.color()), 0.68f);
        float x = PageCanvas.MARGIN + 19;
        c.item(new ItemStack(ModItems.RAW.get(m).get()), x, top + 7, 9);
        c.text("→", x + 10, top + 8.5f, PageCanvas.FADED, 0.6f);
        c.item(new ItemStack(ModItems.REFINED.get(m).get()), x + 15, top + 7, 9);
        c.text("for " + m.weaponDefinition().displayName(), x + 27, top + 9, PageCanvas.FADED, 0.5f);
        c.y = top + 18;
        c.para(m.found() + ".", PageCanvas.INK, 0.52f);
        c.y += 3;
    }

    // ---- the refinery ----

    private void refinery() {
        chapter("The Arcane Refinery", "refinery");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Arcane Refinery");
            craftingGrid(c, FantasyWeapons.id("arcane_refinery"), new ItemStack(ModItems.ARCANE_REFINERY.get()));
            c.para("An iron furnace for legendary materials. Craft it as above; it stands three blocks wide and two high, "
                    + "so leave it room.", PageCanvas.INK, 0.56f);
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Refining");
            c.para("Eight of a raw material and eight of its additive melt over the fire into one core, in 20 seconds. "
                    + "Infernite takes magma blocks:", PageCanvas.INK, 0.56f);
            float x = PageCanvas.MARGIN + 2, y = c.y;
            counted(c, new ItemStack(ModItems.RAW.get(Material.INFERNITE).get()), x, y, 12, Material.REFINE_COUNT);
            c.text("+", x + 20, y + 3, PageCanvas.FADED, 0.75f);
            counted(c, new ItemStack(Material.INFERNITE.additive()), x + 26, y, 12, Material.REFINE_COUNT);
            c.text("+", x + 46, y + 3, PageCanvas.FADED, 0.75f);
            c.item(new ItemStack(Items.COAL), x + 52, y, 12);
            c.text("→", x + 67, y + 3, PageCanvas.FADED, 0.75f);
            c.item(new ItemStack(ModItems.REFINED.get(Material.INFERNITE).get()), x + 76, y, 12);
            c.y += 16;
            c.para("Blaze rods and lava buckets burn twice as fast as other fuel. Hoppers work too: raw from the top, "
                    + "fuel and additive from the sides, the core out of the bottom.", PageCanvas.INK, 0.56f);
            x = PageCanvas.MARGIN + 6;
            c.item(new ItemStack(Items.BLAZE_ROD), x, c.y, 12);
            c.item(new ItemStack(Items.LAVA_BUCKET), x + 14, c.y, 12);
            c.item(new ItemStack(Items.HOPPER), x + 34, c.y, 12);
            c.y += 16;
            c.link("Every core, at a glance »", PageCanvas.MARGIN, c.y, 0.55f, go("cores"));
        });
        anchors.put("cores", pages.size());
        Material[] all = Material.values();
        for (int half = 0; half < 2; half++) {
            int from = half == 0 ? 0 : 7, to = half == 0 ? 7 : all.length;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading("Raw into Cores");
                for (int i = from; i < to; i++) {
                    Material m = all[i];
                    float x = PageCanvas.MARGIN, y = c.y;
                    // eight raw and eight of its additive make the core
                    counted(c, new ItemStack(ModItems.RAW.get(m).get()), x, y, 10, Material.REFINE_COUNT);
                    counted(c, new ItemStack(m.additive()), x + 17, y, 10, Material.REFINE_COUNT);
                    c.text("→", x + 34, y + 2.5f, PageCanvas.FADED, 0.55f);
                    c.item(new ItemStack(ModItems.REFINED.get(m).get()), x + 40, y, 10);
                    c.text(m.refinedName(), x + 53, y + 0.5f, deep(m.color()), 0.5f);
                    c.text("+ " + new ItemStack(m.additive()).getHoverName().getString(), x + 53, y + 6, PageCanvas.FADED, 0.42f);
                    c.y += 14.5f;
                }
            });
        }
    }

    /** An item with how many it takes beside it. */
    private static void counted(PageCanvas c, ItemStack stack, float x, float y, float size, int count) {
        c.item(stack, x, y, size);
        c.text(String.valueOf(count), x + size + 0.5f, y + size - 4.5f, PageCanvas.INK, 0.45f);
    }

    // ---- the table ----

    private void table() {
        chapter("The Legendary Crafting Table", "table");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Crafting Table");
            craftingGrid(c, FantasyWeapons.id("legendary_crafting_table"), new ItemStack(ModItems.LEGENDARY_CRAFTING_TABLE.get()));
            c.para("Built from a trophy of every hardest trial: the totems of raids, the Wither's star, the Dragon's breath, "
                    + "a trial chamber's heavy core and netherite. Three blocks wide, two high.", PageCanvas.INK, 0.54f);
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Forging");
            c.para("The table's grid is nine by nine. It crafts anything a crafting table can, and the patterns of the "
                    + "legendary weapons and armor.", PageCanvas.INK, 0.56f);
            c.para("Choose a weapon or piece from the recipes beside the grid: its pattern appears on the grid as faint "
                    + "ghosts. Lay the real items over them and take what you forged.", PageCanvas.INK, 0.56f);
            c.para("Each weapon's page in this book shows its pattern too.", PageCanvas.FADED, 0.54f);
            c.link("To the weapons »", PageCanvas.MARGIN, c.y + 2, 0.6f, go("weapons"));
        });
    }

    // ---- weapons and armor ----

    private void weapons() {
        List<WeaponDefinition> all = new ArrayList<>(Weapons.blades());
        chapter("The Thirteen Weapons", "weapons");
        for (int half = 0; half < 2; half++) {
            List<WeaponDefinition> on = half == 0 ? all.subList(0, 7) : all.subList(7, all.size());
            boolean first = half == 0;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(first ? "The Thirteen" : "The Thirteen (cont.)");
                for (WeaponDefinition def : on) {
                    float y = c.y;
                    c.item(weapon(def), PageCanvas.MARGIN, y, 12);
                    c.link(def.displayName(), PageCanvas.MARGIN + 15, y + 0.5f, 0.62f, go("weapon_" + def.id()));
                    c.text(def.weaponClass().displayName() + " · " + def.element().displayName(), PageCanvas.MARGIN + 15, y + 7.5f,
                            PageCanvas.FADED, 0.45f);
                    c.y += 14.5f;
                }
            });
        }
        chapter("Armor of Legend", "armor");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Armor of Legend");
            c.para("Every blade has its armor, forged from the same cores, and every element of magic its regalia. Each piece "
                    + "has bonuses of its own; wearing all four wakes the set's power.", PageCanvas.INK, 0.56f);
            float x = PageCanvas.MARGIN, y = c.y + 2;
            int i = 0;
            for (ArmorSet set : ArmorSet.values()) {
                c.item(new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.CHESTPLATE).get()), x, y, 13);
                c.region(x, y, x + 13, y + 13, List.of(Component.literal(set.displayName() + " Armor"), Component.literal("Click to read")
                        .withStyle(s -> s.withColor(0x9C8862))), go("armor_" + set.name()));
                x += 14.5f;
                if (++i % 7 == 0) {
                    x = PageCanvas.MARGIN;
                    y += 15;
                }
            }
            c.y = y + 18;
        });
        // the set powers, the warriors' sets on one page and the Mage's regalia on the next
        for (boolean regalia : new boolean[]{false, true}) {
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(regalia ? "Powers of the Regalia" : "Set Powers");
                for (ArmorSet set : ArmorSet.values()) {
                    if (set.regalia() != regalia || !c.fits(8)) continue;
                    c.link(set.abilityName(), PageCanvas.MARGIN, c.y, 0.52f, go("armor_" + set.name()));
                    String who = set.displayName();
                    c.text(who, PageCanvas.W - PageCanvas.MARGIN - c.width(who, 0.45f), c.y + 0.6f, PageCanvas.FADED, 0.45f);
                    c.y += 7.6f;
                }
            });
        }
        for (WeaponDefinition def : all) weapon(def, ArmorSet.ofWeapon(def.id()));
        mage();
    }

    /** The mage's chapter: what the six elements are for, and the twelve staffs and spellbooks. */
    private void mage() {
        List<WeaponDefinition> focuses = new ArrayList<>(Weapons.focuses());
        chapter("The Mage's Arts", "mage");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Mage's Arts");
            c.para("A mage fights from afar with a staff or a spellbook. Each click looses a bolt at the crosshair: a staff's "
                    + "is swift and strong, a spellbook's lighter but it curves after its target. Every spell is laid at a distance: "
                    + "on the ground you point at, or on the friend or foe in your sights.", PageCanvas.INK, 0.5f);
            c.gap(2);
            String[][] roles = {{"Fire", "destruction"}, {"Ice", "defense: wards and walls"}, {"Water", "healing and shields"},
                    {"Nature", "healing that grows"}, {"Light", "blessings that make allies stronger"}, {"Void", "curses that slowly kill"}};
            for (String[] r : roles) {
                c.text(r[0], PageCanvas.MARGIN, c.y, PageCanvas.RED, 0.55f);
                c.text(r[1], PageCanvas.MARGIN + 26, c.y + 0.3f, PageCanvas.INK, 0.5f);
                c.y += 7;
            }
            c.gap(2);
            c.para("Only a Mage can wield them; healing and blessing others is how a healer's weapon grows.", PageCanvas.FADED, 0.48f);
        });
        for (int half = 0; half < 2; half++) {
            List<WeaponDefinition> on = half == 0 ? focuses.subList(0, Math.min(6, focuses.size())) : focuses.subList(Math.min(6, focuses.size()), focuses.size());
            boolean first = half == 0;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(first ? "Staffs and Spellbooks" : "Staffs and Spellbooks (cont.)");
                for (WeaponDefinition def : on) {
                    float y = c.y;
                    c.item(weapon(def), PageCanvas.MARGIN, y, 12);
                    c.link(def.displayName(), PageCanvas.MARGIN + 15, y + 0.5f, 0.62f, go("weapon_" + def.id()));
                    c.text(def.weaponClass().displayName() + " · " + def.element().displayName(), PageCanvas.MARGIN + 15, y + 7.5f,
                            PageCanvas.FADED, 0.45f);
                    c.y += 17f;
                }
            });
        }
        for (WeaponDefinition def : focuses) weapon(def, null);
    }

    /** The two ultimate weapons, and the Worldforge they are forged on. */
    private void ultimates() {
        chapter("The Ultimate Weapons", "ultimates");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Worldforge");
            craftingGrid(c, FantasyWeapons.id("worldforge"), new ItemStack(ModItems.WORLDFORGE.get()));
            c.para("A Legendary Crafting Table raised on netherite, with two nether stars, two heavy cores and the Dragon's egg. "
                    + "It needs a clear space 21 blocks wide and deep and 22 high, and rises in front of you, the block you choose the foot of its stairs; set down, it unfolds.", PageCanvas.INK, 0.5f);
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Rites");
            c.para("Its grid is eleven by eleven, and it forges only the two ultimate weapons. Each rite takes all twenty-five "
                    + "legendary weapons, a core of every material and the rarest treasures of the world.", PageCanvas.INK, 0.5f);
            c.para("Lay the rite as on the table, then press Forge. It works for " + com.fantasyweapons.forge.WorldforgeRecipe.DEFAULT_TIME / 20 / 60
                    + " minutes; when it is done, lightning falls and the whole world hears.", PageCanvas.INK, 0.5f);
            c.gap(2);
            for (WeaponDefinition def : Weapons.ultimates()) {
                float y = c.y;
                c.item(weapon(def), PageCanvas.MARGIN, y, 13);
                float s = Math.min(0.5f, (c.inner() - 17) / c.width(def.displayName(), 1f));
                c.link(def.displayName(), PageCanvas.MARGIN + 16, y + 0.5f, s, go("weapon_" + def.id()));
                c.text(def.title(), PageCanvas.MARGIN + 16, y + 7f, PageCanvas.FADED, 0.45f);
                c.y = y + 16;
            }
        });
        for (WeaponDefinition def : Weapons.ultimates()) weapon(def, null);
    }

    /** What a focus's bolt leaves on the foes it strikes. */
    private static String boltMark(WeaponDefinition def) {
        return switch (def.element()) {
            case FIRE -> "It sears what it strikes, burning on.";
            case ICE -> "It frostbites what it strikes, slowing it.";
            case WATER -> "It soaks what it strikes, slowing it.";
            case NATURE -> "It poisons what it strikes.";
            case LIGHT -> "It lights up what it strikes for all to see, and burns the undead.";
            case VOID -> "It lays a layer of rot on what it strikes.";
            default -> "";
        };
    }

    private void weapon(WeaponDefinition def, @Nullable ArmorSet set) {
        chapter(def.displayName(), "weapon_" + def.id());
        int ink = deep(def.themePrimary());
        page(c -> {
            c.y = PageCanvas.TOP - 2;
            String name = def.displayName();
            if (c.width(name, 1.1f) <= c.inner()) {
                c.centered(name, c.y, ink, 1.1f);
                c.y += 11;
            } else {
                // a long name goes on two lines, broken at the space nearest its middle
                int mid = name.length() / 2, cut = name.lastIndexOf(' ', mid);
                int after = name.indexOf(' ', mid);
                if (cut < 0 || (after >= 0 && after - mid < mid - cut)) cut = after;
                String a = name.substring(0, cut), b = name.substring(cut + 1);
                float s = Math.min(1.1f, c.inner() / Math.max(c.width(a, 1f), c.width(b, 1f)));
                c.centered(a, c.y, ink, s);
                c.centered(b, c.y + 9.5f * s, ink, s);
                c.y += 9.5f * s * 2 + 1;
            }
            c.centered(def.title(), c.y, PageCanvas.FADED, 0.55f);
            c.y += 7;
            c.rule();
            float size = 42;
            c.item(weapon(def), (PageCanvas.W - size) / 2, c.y, size);
            c.y += size + 3;
            String kind = def.weaponClass().displayName() + " · " + def.element().displayName();
            c.centered(kind, c.y, PageCanvas.RED, 0.6f);
            c.y += 7;
            String stats = String.format(Locale.ROOT, def.weaponClass().magic() ? "%.0f damage · %.1f bolts a second" : "%.0f damage · %.1f swings a second",
                    def.baseDamage(), def.weaponClass().attackSpeed());
            c.centered(stats, c.y, PageCanvas.FADED, 0.5f);
            c.y += 8;
            lore(c, def.lore(), 0.46f);
        });
        // the abilities, on as many pages as they take
        List<List<AbilityDefinition>> abilityPages = new ArrayList<>();
        List<AbilityDefinition> on = new ArrayList<>();
        float room = PageCanvas.BOTTOM - PageCanvas.TOP - 16, used = 0;
        for (AbilityDefinition a : def.abilities()) {
            int lines = Minecraft.getInstance().font.split(Component.literal(a.description()), (int) ((PageCanvas.W - 2 * PageCanvas.MARGIN) / 0.48f)).size();
            float height = 12.5f + lines * 9 * 0.48f + 3;
            if (used + height > room && !on.isEmpty()) {
                abilityPages.add(on);
                on = new ArrayList<>();
                used = 0;
            }
            on.add(a);
            used += height;
        }
        if (!on.isEmpty()) abilityPages.add(on);
        for (int p = 0; p < abilityPages.size(); p++) {
            List<AbilityDefinition> these = abilityPages.get(p);
            boolean first = p == 0;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(first ? "Abilities" : "Abilities (cont.)");
                for (AbilityDefinition a : these) {
                    c.text(a.name(), PageCanvas.MARGIN, c.y, a.kind() == AbilityKind.ULTIMATE ? PageCanvas.GOLD : PageCanvas.RED, 0.62f);
                    String tag = a.kind().displayName() + (a.unlockLevel() > 1 ? " · level " + a.unlockLevel() : "");
                    // the kind beside the name, or under it when the name is long
                    if (c.width(a.name(), 0.62f) + c.width(tag, 0.45f) + 4 > c.inner()) c.y += 6;
                    c.text(tag, PageCanvas.W - PageCanvas.MARGIN - c.width(tag, 0.45f), c.y + 0.8f, PageCanvas.FADED, 0.45f);
                    c.y += 6.5f;
                    lore(c, a.description(), 0.48f);
                    c.y += 1;
                }
            });
        }
        if (def.rarity().ultimate()) {
            var rite = FantasyWeapons.id("worldforging/" + def.id());
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading("The Rite");
                forge(c, rite, 7.6f, false);
                c.para("Forged on the Worldforge.", PageCanvas.FADED, 0.48f);
            });
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading("What It Takes");
                needs(c, rite, 8.5f, 9.6f);
            });
        } else {
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading("Forging");
                forge(c, FantasyWeapons.id("forging/" + def.id()), 9.4f, true);
            });
        }
        if (set != null) anchors.put("armor_" + set.name(), pages.size());
        page(c -> {
            c.y = PageCanvas.TOP;
            if (set == null && def.rarity().ultimate()) {
                c.heading("From Afar");
                c.para("Its far-reaching powers are not cast where you stand. Press cast and Dominion Sight opens: a map of the land "
                        + "around you. The ring shows the spell's true size; click, and it falls there.", PageCanvas.INK, 0.5f);
                for (AbilityDefinition a : def.abilities()) {
                    if (!a.remote() || !c.fits(7)) continue;
                    String far = (int) a.param("remote_range") + " chunks";
                    c.text(a.name(), PageCanvas.MARGIN, c.y, PageCanvas.GOLD, 0.48f);
                    c.text(far, PageCanvas.W - PageCanvas.MARGIN - c.width(far, 0.42f), c.y + 0.5f, PageCanvas.FADED, 0.42f);
                    c.y += 6.5f;
                }
                c.gap(2);
                c.para(def.weaponClass().magic() ? "Only a Mage can wield it." : "Any warrior can wield it: an Assassin, a Berserker or a Paladin.",
                        PageCanvas.FADED, 0.48f);
                c.para("What it destroys stays destroyed.", PageCanvas.RED, 0.5f);
                return;
            }
            if (set == null) {
                if (!def.weaponClass().magic()) return;
                // a staff or spellbook has no armor: its page tells of its bolt instead
                boolean staff = def.weaponClass() == com.fantasyweapons.weapon.WeaponClass.STAFF;
                c.heading("Its Bolt");
                c.para((staff ? "A click looses a swift bolt of " : "A click looses a seeking bolt of ") + def.element().displayName().toLowerCase(Locale.ROOT)
                        + " at the crosshair. " + boltMark(def), PageCanvas.INK, 0.52f);
                c.gap(2);
                var core = Material.ofWeapon(def.id());
                if (core != null) {
                    c.text("Forged and infused with " + core.refinedName() + "s", PageCanvas.MARGIN, c.y, PageCanvas.FADED, 0.48f);
                    c.y += 6;
                    c.item(new ItemStack(ModItems.REFINED.get(core).get()), PageCanvas.MARGIN, c.y, 14);
                    c.y += 16;
                }
                return;
            }
            c.heading(set.displayName() + " Armor");
            float x = (PageCanvas.W - 4 * 22) / 2;
            for (ArmorItem.Type type : ArmorSet.PIECES) {
                ItemStack piece = new ItemStack(ModItems.ARMOR.get(set).get(type).get());
                c.item(piece, x, c.y, 18);
                x += 22;
            }
            c.y += 22;
            c.text(set.abilityName(), PageCanvas.MARGIN, c.y, PageCanvas.GOLD, 0.62f);
            c.y += 7;
            lore(c, set.abilityText(), 0.5f);
            c.gap(2);
            c.text("Forged on the table from " + set.material().refinedName() + "s:", PageCanvas.MARGIN, c.y, PageCanvas.FADED, 0.48f);
            c.y += 6;
            float px = PageCanvas.MARGIN;
            for (ArmorItem.Type type : ArmorSet.PIECES) {
                c.item(new ItemStack(ModItems.ARMOR.get(set).get(type).get()), px, c.y, 10);
                c.text("×" + cores(FantasyWeapons.id("forging/" + set.itemId(type)), set.material()), px + 11, c.y + 2.5f, PageCanvas.INK, 0.5f);
                px += 25;
            }
            c.y += 13;
        });
    }

    // ---- mastery and deeds ----

    private void mastery() {
        chapter("Mastery", "mastery");
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Mastery");
            c.para("A legendary weapon grows as it fights. Kills and hits give it experience; each level makes it stronger "
                    + "and gives points to spend on its abilities, up to level " + ProgressionMath.maxLevel() + ".", PageCanvas.INK, 0.54f);
            keyLine(c, "The weapon's tree", KeyBindings.MENU.getTranslatedKeyMessage().getString());
            keyLine(c, "Cast (hold to charge)", KeyBindings.ABILITY.getTranslatedKeyMessage().getString());
            keyLine(c, "Next ability", KeyBindings.CYCLE.getTranslatedKeyMessage().getString());
            keyLine(c, "Change form", KeyBindings.FORM.getTranslatedKeyMessage().getString());
            c.gap(3);
            c.para("Its last ability is its ultimate: a long charge and a longer cooldown, for the fights that matter.", PageCanvas.INK, 0.54f);
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Infusion");
            c.para("A core of a weapon's own material, crafted together with the weapon in any crafting grid, raises it "
                    + "one level at once.", PageCanvas.INK, 0.56f);
            float x = PageCanvas.MARGIN + 10, y = c.y + 2, cell = 16;
            c.grid(x, y, 3, 3, cell, 0.6f, 0x663A2614, 0);
            c.item(weapon(Weapons.get("voidfang")), x + 1, y + 1, cell - 2);
            c.item(new ItemStack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get()), x + cell + 1, y + 1, cell - 2);
            c.item(new ItemStack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get()), x + 2 * cell + 1, y + 1, cell - 2);
            c.text("→", x + 3 * cell + 4, y + cell + 3, PageCanvas.FADED, 1f);
            c.item(weapon(Weapons.get("voidfang")), x + 3 * cell + 14, y + cell - 1, 18);
            c.text("+2 levels", x + 3 * cell + 12, y + 2 * cell + 4, PageCanvas.GOLD, 0.5f);
            c.y = y + 3 * cell + 6;
            c.para("A full armor set gives its power on top: read about each set beside its weapon.", PageCanvas.FADED, 0.52f);
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("The Quest Journal");
            c.para("Over a hundred quests lead the way: find and mine every ore, refine its core, forge and raise every "
                    + "weapon, wear every set, hunt the great beasts and travel the realms.", PageCanvas.INK, 0.54f);
            keyLine(c, "Open the journal", KeyBindings.QUESTS.getTranslatedKeyMessage().getString());
            c.gap(1);
            c.para("A quest done is announced; claim it in the journal for its reward: ores and cores, diamonds, "
                    + "netherite, enchanted apples and more. Some quests open only once the one before is claimed.", PageCanvas.INK, 0.52f);
            float x = PageCanvas.MARGIN + 6, y = c.y + 3;
            for (ItemStack reward : new ItemStack[]{new ItemStack(ModItems.RAW.get(Material.VOID_CRYSTAL).get()),
                    new ItemStack(ModItems.REFINED.get(Material.SOLARITE).get()), new ItemStack(net.minecraft.world.item.Items.DIAMOND),
                    new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)}) {
                c.item(reward, x, y, 12);
                x += 17;
            }
            c.y = y + 16;
        });
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Classes");
            c.para("Every wayfarer may take a calling. The first choice is free; changing it waits "
                    + com.fantasyweapons.playerclass.ClassService.CHANGE_WAIT / 20 / 60 + " minutes.", PageCanvas.INK, 0.54f);
            keyLine(c, "Choose a class", KeyBindings.CLASSES.getTranslatedKeyMessage().getString());
            int count = com.fantasyweapons.playerclass.PlayerClass.values().length;
            for (var pc : com.fantasyweapons.playerclass.PlayerClass.values()) {
                if (pc == com.fantasyweapons.playerclass.PlayerClass.NONE) continue;
                float y = c.y;
                c.icon(com.fantasyweapons.client.gui.ClassScreen.EMBLEMS, pc.ordinal(), count, PageCanvas.MARGIN, y, 14);
                c.text(pc.displayName(), PageCanvas.MARGIN + 17, y + 1, PageCanvas.RED, 0.6f);
                c.text(pc.available() ? pc.tagline() : "Still being written", PageCanvas.MARGIN + 17, y + 7.5f, PageCanvas.FADED, 0.5f);
                c.y = y + 16;
            }
        });
    }

    private void keyLine(PageCanvas c, String what, String key) {
        float s = 0.52f, w = c.width(key, s) + 4;
        c.fill(PageCanvas.MARGIN, c.y - 1, PageCanvas.MARGIN + w, c.y + 6, 0x553A2614);
        c.text(key, PageCanvas.MARGIN + 2, c.y, PageCanvas.INK, s);
        c.text(what, PageCanvas.MARGIN + w + 3, c.y, PageCanvas.INK, s);
        c.y += 9;
    }

    /** The Deeds tab's sections, in the order of the road, and the order of the deeds within them. */
    private static final String[] SECTIONS = {"The Road", "Ores", "Refining", "The Table", "Weapons", "Mastery", "Armor", "The Ultimates", "Other"};
    private static final List<String> ULTIMATE = List.of("worldforge", "first_rite", "obliteration", "domination", "world_ender", "from_afar", "absolute");
    private static final List<String> ORDER = new ArrayList<>();

    static {
        ORDER.addAll(List.of("root", "open_the_codex", "glimmer"));
        for (Material m : Material.values()) ORDER.add("ore_" + m.rawId());
        ORDER.addAll(List.of("every_ore", "arcane_refinery", "first_core", "every_core", "legendary_table", "first_forging", "legend_reborn"));
        for (WeaponDefinition def : Weapons.legendaries()) ORDER.add("weapon_" + def.id());
        ORDER.addAll(List.of("the_thirteen", "archmage", "first_blood", "centurion", "thousand_souls", "giant_slayer", "level_10", "level_30", "level_50",
                "level_100", "first_ability", "ultimate", "infused", "legendary_armor", "full_set", "every_set", "every_regalia"));
        ORDER.addAll(ULTIMATE);
    }

    private static int section(String id) {
        if (id.equals("root") || id.equals("open_the_codex")) return 0;
        if (id.equals("glimmer") || id.startsWith("ore_") || id.equals("every_ore")) return 1;
        if (id.equals("arcane_refinery") || id.endsWith("_core")) return 2;
        if (id.equals("legendary_table") || id.equals("first_forging")) return 3;
        if (id.equals("legend_reborn") || id.startsWith("weapon_") || id.equals("the_thirteen") || id.equals("archmage")) return 4;
        if (id.equals("legendary_armor") || id.endsWith("_set") || id.equals("every_regalia")) return 6;
        if (ULTIMATE.contains(id)) return 7;
        return ORDER.contains(id) ? 5 : 8;
    }

    private void deeds() {
        chapter("Deeds", "deeds");
        List<AdvancementHolder> deeds = new ArrayList<>();
        if (mc.getConnection() != null) {
            for (AdvancementNode node : mc.getConnection().getAdvancements().getTree().nodes()) {
                if (com.fantasyweapons.advancement.Deeds.ours(node.holder()) && node.holder().value().display().isPresent()) deeds.add(node.holder());
            }
        }
        deeds.sort(java.util.Comparator.comparingInt((AdvancementHolder a) -> section(a.id().getPath()))
                .thenComparingInt(a -> ORDER.indexOf(a.id().getPath()) < 0 ? Integer.MAX_VALUE : ORDER.indexOf(a.id().getPath()))
                .thenComparing(a -> a.id().getPath()));
        // the opening page: how far along the road the reader is, by rank
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Deeds");
            if (deeds.isEmpty()) {
                c.para("Your deeds will be written here as you do them: the ores you find, the arms you forge, the foes you fell.", PageCanvas.INK, 0.56f);
                return;
            }
            int done = 0;
            for (AdvancementHolder d : deeds) if (done(d)) done++;
            // (only the deeds in sight are known: each comes into view as the one before it is done)
            c.centered(done + " of " + deeds.size() + " done", c.y, PageCanvas.INK, 0.62f);
            c.y += 6.5f;
            c.centered("more come to light as you go", c.y, PageCanvas.FADED, 0.42f);
            c.y += 6;
            float x0 = PageCanvas.MARGIN + 8, x1 = PageCanvas.W - PageCanvas.MARGIN - 8;
            c.fill(x0, c.y, x1, c.y + 3.5f, 0x33000000);
            c.fill(x0, c.y, x0 + (x1 - x0) * done / deeds.size(), c.y + 3.5f, PageCanvas.GOLD, 0.03f);
            c.frame(x0 - 0.5f, c.y - 0.5f, x1 + 0.5f, c.y + 4, 0.5f, 0x883A2614);
            c.y += 10;
            for (net.minecraft.advancements.AdvancementType type : new net.minecraft.advancements.AdvancementType[]{
                    net.minecraft.advancements.AdvancementType.TASK, net.minecraft.advancements.AdvancementType.GOAL,
                    net.minecraft.advancements.AdvancementType.CHALLENGE}) {
                int all = 0, got = 0;
                for (AdvancementHolder d : deeds) {
                    if (d.value().display().orElseThrow().getType() != type) continue;
                    all++;
                    if (done(d)) got++;
                }
                int color = deep(com.fantasyweapons.advancement.Deeds.color(type));
                String rank = com.fantasyweapons.advancement.Deeds.rank(type) + "s";
                c.text(com.fantasyweapons.advancement.Deeds.mark(type).getVisualOrderText(), PageCanvas.MARGIN + 8, c.y - 0.6f, 0xFFFFFFFF, 0.75f);
                c.text(rank, PageCanvas.MARGIN + 17, c.y, color, 0.6f);
                String n = got + " / " + all;
                c.text(n, PageCanvas.W - PageCanvas.MARGIN - 10 - c.width(n, 0.6f), c.y, PageCanvas.INK, 0.6f);
                c.y += 8.5f;
            }
            c.y += 3;
            c.para("Deeds mark the road, feats its trials and legends its hardest tests. Do one and the whole world hears of it.",
                    PageCanvas.FADED, 0.5f);
        });
        // then every deed, section by section
        List<Object> rows = new ArrayList<>();
        int last = -1;
        for (AdvancementHolder d : deeds) {
            int sec = section(d.id().getPath());
            if (sec != last) rows.add(SECTIONS[sec]);
            last = sec;
            rows.add(d);
        }
        float room = PageCanvas.BOTTOM - PageCanvas.TOP - 4;
        List<Object> on = new ArrayList<>();
        float used = 0;
        for (Object row : rows) {
            float h = row instanceof String ? 10 : deedHeight((AdvancementHolder) row);
            // a section's name never ends a page
            boolean header = row instanceof String;
            if (!on.isEmpty() && used + h + (header ? 16 : 0) > room) {
                deedPage(new ArrayList<>(on));
                on.clear();
                used = 0;
            }
            on.add(row);
            used += h;
        }
        if (!on.isEmpty()) deedPage(on);
    }

    private void deedPage(List<Object> rows) {
        page(c -> {
            c.y = PageCanvas.TOP;
            for (Object row : rows) {
                if (row instanceof String name) {
                    c.text(name.toUpperCase(Locale.ROOT), PageCanvas.MARGIN, c.y + 1, PageCanvas.RED, 0.5f);
                    c.fill(PageCanvas.MARGIN + c.width(name.toUpperCase(Locale.ROOT), 0.5f) + 3, c.y + 3.2f, PageCanvas.W - PageCanvas.MARGIN, c.y + 3.7f, PageCanvas.RULE);
                    c.y += 10;
                } else {
                    deed(c, (AdvancementHolder) row);
                }
            }
        });
    }

    private static final float DEED_DESC = 0.42f;

    private float deedHeight(AdvancementHolder holder) {
        var display = holder.value().display().orElseThrow();
        int lines = mc.font.split(display.getDescription(), (int) ((PageCanvas.W - 2 * PageCanvas.MARGIN - 15) / DEED_DESC)).size();
        return Math.max(6.5f + lines * 9 * DEED_DESC, 12) + 3;
    }

    private void deed(PageCanvas c, AdvancementHolder holder) {
        var display = holder.value().display().orElseThrow();
        boolean done = done(holder);
        float top = c.y;
        int titleColor = display.getTitle().getStyle().getColor() == null ? PageCanvas.INK : deep(display.getTitle().getStyle().getColor().getValue());
        int rankColor = deep(com.fantasyweapons.advancement.Deeds.color(display.getType()));
        // a gold seal behind what is done
        if (done) c.fill(PageCanvas.MARGIN - 1, top - 1, PageCanvas.MARGIN + 13, top + 13, 0x449A6A12);
        c.item(display.getIcon(), PageCanvas.MARGIN, top, 12);
        String rank = com.fantasyweapons.advancement.Deeds.rank(display.getType()).toUpperCase(Locale.ROOT);
        float rx = PageCanvas.W - PageCanvas.MARGIN - c.width(rank, 0.36f);
        c.text(display.getTitle().getString(), PageCanvas.MARGIN + 15, top, done ? titleColor : (titleColor & 0x00FFFFFF) | 0x99000000, 0.54f);
        c.text(rank, rx, top + 0.8f, (rankColor & 0x00FFFFFF) | (done ? 0xFF000000 : 0x99000000), 0.36f);
        if (done) c.text("\u2714", rx - 6, top - 0.3f, 0xFF2E7D32, 0.55f);
        else c.text(com.fantasyweapons.advancement.Deeds.mark(display.getType()).getVisualOrderText(), rx - 5.5f, top - 0.2f, 0xFFFFFFFF, 0.5f);
        c.y = top + 6.5f;
        for (var line : mc.font.split(display.getDescription(), (int) ((c.inner() - 15) / DEED_DESC))) {
            c.text(line, PageCanvas.MARGIN + 15, c.y, done ? PageCanvas.INK : PageCanvas.FADED, DEED_DESC);
            c.y += 9 * DEED_DESC;
        }
        c.y = Math.max(c.y, top + 12) + 3;
    }

    /** Whether the reader has made an advancement (the client's progress map is private; NeoForge runs on Mojang's names). */
    private boolean done(AdvancementHolder holder) {
        try {
            if (progressField == null) {
                progressField = net.minecraft.client.multiplayer.ClientAdvancements.class.getDeclaredField("progress");
                progressField.setAccessible(true);
            }
            Object map = progressField.get(mc.getConnection().getAdvancements());
            return map instanceof Map<?, ?> m && m.get(holder) instanceof net.minecraft.advancements.AdvancementProgress p && p.isDone();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static java.lang.reflect.Field progressField;

    // ---- pictures of recipes ----

    /** A 3x3 crafting recipe and what it makes. */
    private void craftingGrid(PageCanvas c, net.minecraft.resources.ResourceLocation id, ItemStack fallback) {
        var holder = mc.level == null ? null : mc.level.getRecipeManager().byKey(id).orElse(null);
        float cell = 14, x = PageCanvas.MARGIN + 6, y = c.y + 1;
        c.grid(x, y, 3, 3, cell, 0.6f, 0x663A2614, 0x22000000);
        ItemStack result = fallback;
        if (holder != null && holder.value() instanceof ShapedRecipe shaped) {
            for (int r = 0; r < shaped.getHeight(); r++) {
                for (int col = 0; col < shaped.getWidth(); col++) {
                    c.item(shown(shaped.getIngredients().get(col + r * shaped.getWidth())), x + col * cell + 1, y + r * cell + 1, cell - 2);
                }
            }
            result = shaped.getResultItem(mc.level.registryAccess());
        }
        c.text("→", x + 3 * cell + 4, y + cell + 3, PageCanvas.FADED, 1f);
        c.item(result, x + 3 * cell + 14, y + cell - 3, 20);
        c.y = y + 3 * cell + 5;
    }

    /** A forging pattern recipe's shape: the table's (9x9) or the Worldforge's (11x11), or null. */
    @Nullable
    private net.minecraft.world.item.crafting.ShapedRecipePattern pattern(net.minecraft.resources.ResourceLocation id) {
        var holder = mc.level == null ? null : mc.level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null) return null;
        if (holder.value() instanceof ForgeRecipe recipe) return recipe.pattern();
        if (holder.value() instanceof com.fantasyweapons.forge.WorldforgeRecipe recipe) return recipe.pattern();
        return null;
    }

    /** Every item a pattern takes, and how many of each, in the order they first appear. */
    private static Map<Item, Integer> needs(@Nullable net.minecraft.world.item.crafting.ShapedRecipePattern pattern) {
        Map<Item, Integer> needs = new LinkedHashMap<>();
        if (pattern != null) {
            for (Ingredient ing : pattern.ingredients()) if (!ing.isEmpty()) needs.merge(ing.getItems()[0].getItem(), 1, Integer::sum);
        }
        return needs;
    }

    /** The items a pattern takes, in rows of {@code size}-sized icons {@code step} apart, with their counts. */
    private void needs(PageCanvas c, net.minecraft.resources.ResourceLocation id, float size, float step) {
        float x0 = PageCanvas.MARGIN + 1, px = x0;
        for (var e : needs(pattern(id)).entrySet()) {
            if (px + size > PageCanvas.W - PageCanvas.MARGIN) {
                px = x0;
                c.y += step + 0.5f;
            }
            c.item(new ItemStack(e.getKey(), e.getValue()), px, c.y, size);
            px += step;
        }
        c.y += step + 3;
    }

    /** A forging pattern on its grid (9x9 or 11x11), centred, and, if {@code withNeeds}, the items it takes. */
    private void forge(PageCanvas c, net.minecraft.resources.ResourceLocation id, float cell, boolean withNeeds) {
        var found = pattern(id);
        int n = found != null && (found.width() > 9 || found.height() > 9) ? 11 : 9;
        float x = (PageCanvas.W - n * cell) / 2, y = c.y;
        c.fill(x - 1, y - 1, x + n * cell + 1, y + n * cell + 1, 0x18000000);
        c.grid(x, y, n, n, cell, 0.5f, 0x663A2614, 0);
        Map<Item, Integer> needs = new LinkedHashMap<>();
        if (found != null) {
            var pattern = found;
            int ox = (n - pattern.width()) / 2, oy = (n - pattern.height()) / 2;
            for (int r = 0; r < pattern.height(); r++) {
                for (int col = 0; col < pattern.width(); col++) {
                    Ingredient ing = pattern.ingredients().get(col + r * pattern.width());
                    if (ing.isEmpty()) continue;
                    ItemStack shown = shown(ing);
                    c.item(shown, x + (col + ox) * cell + 0.5f, y + (r + oy) * cell + 0.5f, cell - 1);
                    needs.merge(shown.getItem(), 1, Integer::sum);
                }
            }
        }
        c.y = y + n * cell + 3;
        if (!withNeeds) return;
        c.text("Takes", PageCanvas.MARGIN, c.y + 2.5f, PageCanvas.FADED, 0.48f);
        float px = PageCanvas.MARGIN + 14;
        for (var e : needs.entrySet()) {
            if (px > PageCanvas.W - PageCanvas.MARGIN - 10) {
                px = PageCanvas.MARGIN + 14;
                c.y += 10.5f;
            }
            c.item(new ItemStack(e.getKey(), e.getValue()), px, c.y, 9.5f);
            px += 11;
        }
        c.y += 12;
    }

    /** How many of a material's cores a forging recipe takes. */
    private int cores(net.minecraft.resources.ResourceLocation id, Material m) {
        var holder = mc.level == null ? null : mc.level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null) return 0;
        Item core = ModItems.REFINED.get(m).get();
        int n = 0;
        for (Ingredient ing : holder.value().getIngredients()) if (!ing.isEmpty() && ing.test(new ItemStack(core))) n++;
        return n;
    }

    /** An ingredient's item; ingredients of several items show each in turn. */
    private static ItemStack shown(Ingredient ing) {
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        return items[(int) (Util.getMillis() / 1200 % items.length)];
    }

    private static ItemStack weapon(WeaponDefinition def) {
        return new ItemStack(ModItems.WEAPONS.get(def.id()).get());
    }

    /** As much of some text as fits under the cursor, ending in an ellipsis when cut short. */
    private static void lore(PageCanvas c, String text, float scale) {
        var lines = Minecraft.getInstance().font.split(Component.literal(text), (int) (c.inner() / scale));
        for (int i = 0; i < lines.size(); i++) {
            if (!c.fits(9 * scale)) {
                c.text("...", PageCanvas.MARGIN, c.y, PageCanvas.FADED, scale);
                c.y += 9 * scale;
                break;
            }
            c.text(lines.get(i), PageCanvas.MARGIN, c.y, PageCanvas.INK, scale);
            c.y += 9 * scale;
        }
        c.y += 2;
    }

    /** A theme colour darkened enough to read on parchment. */
    private static int deep(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float k = 0.52f;
        return 0xFF000000 | ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }
}
