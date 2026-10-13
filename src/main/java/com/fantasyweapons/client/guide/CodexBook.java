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
        }
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
        List<WeaponDefinition> all = new ArrayList<>(Weapons.all());
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
                {"Mastery", "mastery"}, {"Deeds", "deeds"}};
        for (String[] e : entries) {
            int page = anchors.getOrDefault(e[1], 0) + 1;
            float s = 0.68f;
            c.link(e[0], PageCanvas.MARGIN, c.y, s, go(e[1]));
            String n = String.valueOf(page);
            c.text(n, PageCanvas.W - PageCanvas.MARGIN - c.width(n, s), c.y, PageCanvas.FADED, s);
            // dotted leader
            float from = PageCanvas.MARGIN + c.width(e[0], s) + 2, to = PageCanvas.W - PageCanvas.MARGIN - c.width(n, s) - 2;
            for (float x = from; x < to; x += 2.5f) c.fill(x, c.y + 5, x + 0.7f, c.y + 5.7f, 0x883A2614);
            c.y += 12;
        }
        c.gap(4);
        c.para("Click a chapter to turn to it. Turn the pages with the corners, the arrow keys or the mouse wheel.", PageCanvas.FADED, 0.5f);
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
                {"Refine", "An Arcane Refinery burns the raw material into its core."},
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
            c.para("Put a raw material in the top slot and fuel in the side slot. In 20 seconds it is refined into its core.", PageCanvas.INK, 0.56f);
            float x = PageCanvas.MARGIN + 6, y = c.y;
            c.item(new ItemStack(ModItems.RAW.get(Material.INFERNITE).get()), x, y, 14);
            c.text("+", x + 17, y + 4, PageCanvas.FADED, 0.8f);
            c.item(new ItemStack(Items.COAL), x + 24, y, 14);
            c.text("→", x + 42, y + 4, PageCanvas.FADED, 0.8f);
            c.item(new ItemStack(ModItems.REFINED.get(Material.INFERNITE).get()), x + 52, y, 14);
            c.y += 19;
            c.para("Blaze rods and lava buckets burn twice as fast as other fuel. Hoppers work too: input from the top, "
                    + "fuel from the sides, the core out of the bottom.", PageCanvas.INK, 0.56f);
            x = PageCanvas.MARGIN + 6;
            c.item(new ItemStack(Items.BLAZE_ROD), x, c.y, 12);
            c.item(new ItemStack(Items.LAVA_BUCKET), x + 14, c.y, 12);
            c.item(new ItemStack(Items.HOPPER), x + 34, c.y, 12);
            c.y += 16;
            c.link("Every core, at a glance »", PageCanvas.MARGIN, c.y, 0.55f, go("cores"));
        });
        anchors.put("cores", pages.size());
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Raw into Cores");
            for (Material m : Material.values()) {
                float y = c.y;
                c.item(new ItemStack(ModItems.RAW.get(m).get()), PageCanvas.MARGIN, y, 8);
                c.text("→", PageCanvas.MARGIN + 9, y + 1.5f, PageCanvas.FADED, 0.5f);
                c.item(new ItemStack(ModItems.REFINED.get(m).get()), PageCanvas.MARGIN + 14, y, 8);
                c.text(m.refinedName(), PageCanvas.MARGIN + 25, y + 1.5f, deep(m.color()), 0.52f);
                c.y += 9.4f;
            }
        });
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
        List<WeaponDefinition> all = new ArrayList<>(Weapons.all());
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
            c.para("Every weapon has its armor, forged from the same cores. Each piece has bonuses of its own; wearing all "
                    + "four wakes the set's power.", PageCanvas.INK, 0.56f);
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
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Set Powers");
            for (ArmorSet set : ArmorSet.values()) {
                if (!c.fits(8)) break;
                c.link(set.abilityName(), PageCanvas.MARGIN, c.y, 0.52f, go("armor_" + set.name()));
                String who = set.displayName();
                c.text(who, PageCanvas.W - PageCanvas.MARGIN - c.width(who, 0.45f), c.y + 0.6f, PageCanvas.FADED, 0.45f);
                c.y += 7.6f;
            }
        });
        for (WeaponDefinition def : all) weapon(def, ArmorSet.ofWeapon(def.id()));
    }

    private void weapon(WeaponDefinition def, @Nullable ArmorSet set) {
        chapter(def.displayName(), "weapon_" + def.id());
        int ink = deep(def.themePrimary());
        page(c -> {
            c.y = PageCanvas.TOP - 2;
            c.centered(def.displayName(), c.y, ink, 1.1f);
            c.y += 11;
            c.centered(def.title(), c.y, PageCanvas.FADED, 0.55f);
            c.y += 7;
            c.rule();
            float size = 42;
            c.item(weapon(def), (PageCanvas.W - size) / 2, c.y, size);
            c.y += size + 3;
            String kind = def.weaponClass().displayName() + " · " + def.element().displayName();
            c.centered(kind, c.y, PageCanvas.RED, 0.6f);
            c.y += 7;
            String stats = String.format(Locale.ROOT, "%.0f damage · %.1f swings a second", def.baseDamage(), def.weaponClass().attackSpeed());
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
        page(c -> {
            c.y = PageCanvas.TOP;
            c.heading("Forging");
            forge(c, FantasyWeapons.id("forging/" + def.id()), 9.4f);
        });
        if (set != null) anchors.put("armor_" + set.name(), pages.size());
        page(c -> {
            c.y = PageCanvas.TOP;
            if (set == null) return;
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
            for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) c.frame(x + col * cell, y + r * cell, x + (col + 1) * cell, y + (r + 1) * cell, 0.6f, 0x663A2614);
            c.item(weapon(Weapons.get("voidfang")), x + 1, y + 1, cell - 2);
            c.item(new ItemStack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get()), x + cell + 1, y + 1, cell - 2);
            c.item(new ItemStack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get()), x + 2 * cell + 1, y + 1, cell - 2);
            c.text("→", x + 3 * cell + 4, y + cell + 3, PageCanvas.FADED, 1f);
            c.item(weapon(Weapons.get("voidfang")), x + 3 * cell + 14, y + cell - 1, 18);
            c.text("+2 levels", x + 3 * cell + 12, y + 2 * cell + 4, PageCanvas.GOLD, 0.5f);
            c.y = y + 3 * cell + 6;
            c.para("A full armor set gives its power on top: read about each set beside its weapon.", PageCanvas.FADED, 0.52f);
        });
    }

    private void keyLine(PageCanvas c, String what, String key) {
        float s = 0.52f, w = c.width(key, s) + 4;
        c.fill(PageCanvas.MARGIN, c.y - 1, PageCanvas.MARGIN + w, c.y + 6, 0x553A2614);
        c.text(key, PageCanvas.MARGIN + 2, c.y, PageCanvas.INK, s);
        c.text(what, PageCanvas.MARGIN + w + 3, c.y, PageCanvas.INK, s);
        c.y += 9;
    }

    private void deeds() {
        chapter("Deeds", "deeds");
        List<AdvancementNode> deeds = new ArrayList<>();
        if (mc.getConnection() != null) {
            for (AdvancementNode node : mc.getConnection().getAdvancements().getTree().nodes()) {
                if (node.holder().id().getNamespace().equals(FantasyWeapons.MOD_ID) && node.holder().value().display().isPresent()) deeds.add(node);
            }
        }
        int perPage = 8;
        if (deeds.isEmpty()) {
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading("Deeds");
                c.para("Your deeds will be written here as you do them: the ores you find, the arms you forge, the foes you fell.", PageCanvas.INK, 0.56f);
            });
            return;
        }
        for (int i = 0; i < deeds.size(); i += perPage) {
            List<AdvancementNode> on = deeds.subList(i, Math.min(deeds.size(), i + perPage));
            boolean first = i == 0;
            page(c -> {
                c.y = PageCanvas.TOP;
                c.heading(first ? "Deeds" : "Deeds (cont.)");
                for (AdvancementNode node : on) deed(c, node.holder());
            });
        }
    }

    private void deed(PageCanvas c, AdvancementHolder holder) {
        var display = holder.value().display().orElseThrow();
        boolean done = done(holder);
        float top = c.y;
        c.item(display.getIcon(), PageCanvas.MARGIN, top, 12);
        c.text(display.getTitle().getString(), PageCanvas.MARGIN + 15, top, done ? PageCanvas.GOLD : PageCanvas.FADED, 0.56f);
        if (done) c.text("✔", PageCanvas.W - PageCanvas.MARGIN - 5, top, 0xFF2E7D32, 0.7f);
        c.y = top + 6.5f;
        String desc = display.getDescription().getString();
        float s = 0.45f;
        for (var line : Minecraft.getInstance().font.split(Component.literal(desc), (int) ((c.inner() - 15) / s))) {
            c.text(line, PageCanvas.MARGIN + 15, c.y, PageCanvas.INK, s);
            c.y += 9 * s;
        }
        c.y = Math.max(c.y, top + 13) + 3;
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
        for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) {
            c.fill(x + col * cell, y + r * cell, x + (col + 1) * cell, y + (r + 1) * cell, 0x22000000);
            c.frame(x + col * cell, y + r * cell, x + (col + 1) * cell, y + (r + 1) * cell, 0.5f, 0x663A2614);
        }
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

    /** A forging pattern on the 9x9 grid, centred, and the items it takes. */
    private void forge(PageCanvas c, net.minecraft.resources.ResourceLocation id, float cell) {
        var holder = mc.level == null ? null : mc.level.getRecipeManager().byKey(id).orElse(null);
        float x = (PageCanvas.W - 9 * cell) / 2, y = c.y;
        c.fill(x - 1, y - 1, x + 9 * cell + 1, y + 9 * cell + 1, 0x18000000);
        for (int r = 0; r < 9; r++) for (int col = 0; col < 9; col++) c.frame(x + col * cell, y + r * cell, x + (col + 1) * cell, y + (r + 1) * cell, 0.35f, 0x553A2614);
        Map<Item, Integer> needs = new LinkedHashMap<>();
        if (holder != null && holder.value() instanceof ForgeRecipe recipe) {
            var pattern = recipe.pattern();
            int ox = (9 - pattern.width()) / 2, oy = (9 - pattern.height()) / 2;
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
        c.y = y + 9 * cell + 3;
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
