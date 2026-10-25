package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityState;
import com.fantasyweapons.armor.ArmorClass;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.ArmorStat;
import com.fantasyweapons.armor.SetAbilities;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.forge.ForgeStages;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.quest.Chapter;
import com.fantasyweapons.quest.Quest;
import com.fantasyweapons.quest.QuestObjective;
import com.fantasyweapons.quest.Quests;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.text.Tr;
import com.fantasyweapons.tool.ToolAbilities;
import com.fantasyweapons.tool.ToolKind;
import com.fantasyweapons.tool.ToolUpgrades;
import com.fantasyweapons.tool.UpgradeTrack;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.weapon.Weapons;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * The Russian language file translates all of the mod's text: every name, description, quest, setting and line its
 * definitions put together. The test asks each of them for its text while noting the English asked for, then looks each
 * piece up in {@code ru_ru.json}, along with the English file's own keys and the deeds' titles and descriptions; whatever is
 * missing is listed in {@code fw_l10n_missing.txt} (beside the world).
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LocalizationGameTests {
    private static final String ARENA = "arena";
    private static final String RUSSIAN = "/assets/fantasyweapons/lang/ru_ru.json";
    private static final String ENGLISH = "/assets/fantasyweapons/lang/en_us.json";

    private LocalizationGameTests() {
    }

    @GameTest(template = ARENA)
    public static void russianTranslatesEveryText(GameTestHelper h) {
        Set<String> asked = new ConcurrentSkipListSet<>();
        Tr.record(asked);
        try {
            gather();
        } finally {
            Tr.record(null);
        }
        // the language file's own keys (items, blocks, sounds, keys...) and the deeds' titles and descriptions
        JsonObject english = read(ENGLISH);
        if (english != null) asked.addAll(english.keySet());
        for (var holder : h.getLevel().getServer().getAdvancements().getAllAdvancements()) {
            if (!holder.id().getNamespace().equals(FantasyWeapons.MOD_ID)) continue;
            holder.value().display().ifPresent(d -> {
                if (d.getTitle().getContents() instanceof TranslatableContents t) asked.add(t.getKey());
                if (d.getDescription().getContents() instanceof TranslatableContents t) asked.add(t.getKey());
            });
        }
        JsonObject russian = read(RUSSIAN);
        Set<String> missing = new TreeSet<>();
        for (String key : asked) {
            if (!key.chars().anyMatch(Character::isLetter)) continue;
            // the game's own keys (a mob's name in a quest) are translated by the game
            if (key.matches("[a-z_]+\\.minecraft\\..*")) continue;
            if (russian == null || !russian.has(key)) missing.add(key);
        }
        try {
            Files.write(Path.of("fw_l10n_keys.txt"), asked.stream().map(LocalizationGameTests::escape).toList(), StandardCharsets.UTF_8);
            Files.write(Path.of("fw_l10n_missing.txt"), missing.stream().map(LocalizationGameTests::escape).toList(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FantasyWeapons.LOGGER.warn("Could not write the text lists", e);
        }
        if (russian == null) h.fail("no " + RUSSIAN);
        if (!missing.isEmpty()) h.fail(missing.size() + " texts have no Russian, the first: " + missing.iterator().next());
        h.succeed();
    }

    /** One text to a line, its line breaks written as \n. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static JsonObject read(String file) {
        try (InputStream in = LocalizationGameTests.class.getResourceAsStream(file)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    /** Asks everything that makes text for it. */
    private static void gather() {
        for (Element e : Element.values()) e.displayName();
        for (Rarity r : Rarity.values()) {
            r.displayName();
            r.shortName();
        }
        for (WeaponClass c : WeaponClass.values()) c.displayName();
        for (AbilityKind k : AbilityKind.values()) k.displayName();
        for (AbilityState s : AbilityState.values()) s.label();
        for (StatusType s : StatusType.values()) s.displayName();
        for (ToolKind k : ToolKind.values()) {
            k.displayName();
            ToolAbilities.name(k);
            ToolAbilities.describe(k, ToolUpgrades.BASE);
        }
        for (UpgradeTrack t : UpgradeTrack.values()) t.displayName();
        for (ArmorClass c : ArmorClass.values()) c.displayName();
        for (Chapter c : Chapter.values()) c.title();
        for (var m : com.fantasyweapons.progression.MasteryBonus.MILESTONES) m.description();
        Tr.s("QUEST COMPLETE");
        Tr.s("Infuse a %s for a level");
        Tr.s("Infuse an %s for a level");

        for (WeaponDefinition def : Weapons.all()) {
            def.displayName();
            def.title();
            def.lore();
            def.loreSource();
            for (WeaponForm f : def.forms()) {
                f.displayName();
                f.label();
            }
            for (AbilityDefinition a : def.abilities()) ability(a);
            ItemStack stack = new ItemStack(ModItems.WEAPONS.get(def.id()).get());
            for (int stage = 0; stage < ForgeStages.count(stack); stage++) ForgeStages.line(stack, null, stage);
        }
        for (ArmorSet set : ArmorSet.values()) {
            set.displayName();
            set.look();
            set.abilityName();
            set.abilityText();
            SetAbilities.details(set);
            for (var type : ArmorSet.PIECES) {
                for (ArmorSet.Bonus b : set.bonuses(type)) b.text();
                ItemStack stack = new ItemStack(ModItems.ARMOR.get(set).get(type).get());
                for (int stage = 0; stage < ForgeStages.count(stack); stage++) ForgeStages.line(stack, null, stage);
            }
        }
        for (ArmorStat stat : ArmorStat.values()) {
            stat.displayName();
            stat.describe(1);
            stat.describe(2);
            stat.describe(0.25);
        }
        for (Material m : Material.values()) {
            m.materialName();
            m.oreName();
            m.rawName();
            m.refinedName();
            m.pulse();
            m.found();
            m.look();
            // a forging's stage lines name the cores in the plural
            Tr.s(m.refinedEnglish() + "s");
        }
        for (PlayerClass c : PlayerClass.values()) {
            c.displayName();
            c.tagline();
            c.lore();
            c.perks();
        }
        for (Quest q : Quests.all()) {
            q.title();
            q.description();
            if (q.group() != null) q.group().title();
            for (QuestObjective o : q.objectives()) o.label();
        }
        config(ClientConfig.SPEC);
        config(ServerConfig.SPEC);
        Tr.s("On");
        Tr.s("Off");
    }

    private static void ability(AbilityDefinition a) {
        a.name();
        a.description();
        a.kind().displayName();
        for (var stat : a.stats()) {
            stat.label();
            for (int level : new int[]{1, a.maxLevel()}) {
                try {
                    stat.value().apply(new AbilityDefinition.StatContext(a, level, Math.max(a.unlockLevel(), 1), 10f));
                } catch (RuntimeException ignored) {
                    // a stat that cannot be worked out away from a player makes no text
                }
            }
        }
        for (int level = 2; level <= a.maxLevel(); level++) a.upgradeLines(level);
    }

    /** Each section's and setting's label, help and choices. */
    private static void config(ModConfigSpec spec) {
        walk(spec, spec.getValues(), new ArrayList<>());
    }

    private static void walk(ModConfigSpec spec, UnmodifiableConfig level, List<String> path) {
        for (var e : level.valueMap().entrySet()) {
            List<String> at = new ArrayList<>(path);
            at.add(e.getKey());
            if (e.getValue() instanceof UnmodifiableConfig sub) {
                String key = spec.getLevelTranslationKey(at);
                Tr.s(key != null ? key : ServerConfig.label(e.getKey()));
                walk(spec, sub, at);
            } else if (e.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                ModConfigSpec.ValueSpec vs = value.getSpec();
                Tr.s(vs.getTranslationKey() != null ? vs.getTranslationKey() : ServerConfig.label(e.getKey()));
                Tr.s(ServerConfig.help(vs.getComment()));
                if (vs.getDefault() instanceof Enum<?> en) {
                    for (Object c : en.getDeclaringClass().getEnumConstants()) {
                        Tr.s(ServerConfig.label(((Enum<?>) c).name().toLowerCase(java.util.Locale.ROOT)));
                    }
                }
            }
        }
    }
}
