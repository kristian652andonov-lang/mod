package com.fantasyweapons.weapon;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.progression.WeaponData;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Static description of one of the 13 weapons: identity, melee stats, forms and its ability tree.
 */
public final class WeaponDefinition {
    private final String id;
    private final String displayName;
    private final String title;
    private final String lore;
    private final String loreSource;
    private final Element element;
    private final Rarity rarity;
    private final WeaponClass weaponClass;
    private final float baseDamage;
    private final float heavyMultiplier;
    private final float critChance;
    private final float critMultiplier;
    private final float lifesteal;
    private final int themePrimary;
    private final int themeSecondary;
    private final List<WeaponForm> forms;
    private final List<AbilityDefinition> abilities;
    private final String geoName;
    @Nullable
    private final MeleeHook meleeHook;

    /** Weapon-specific reaction to a landed melee hit (server side). */
    @FunctionalInterface
    public interface MeleeHook {
        void onHit(com.fantasyweapons.combat.MeleeContext ctx);
    }

    private WeaponDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.title = b.title;
        this.lore = b.lore;
        this.loreSource = b.loreSource;
        this.element = b.element;
        this.rarity = b.rarity;
        this.weaponClass = b.weaponClass;
        this.baseDamage = b.baseDamage;
        this.heavyMultiplier = b.heavyMultiplier;
        this.critChance = b.critChance;
        this.critMultiplier = b.critMultiplier;
        this.lifesteal = b.lifesteal;
        this.themePrimary = b.themePrimary < 0 ? b.element.primary() : b.themePrimary;
        this.themeSecondary = b.themeSecondary < 0 ? b.element.light() : b.themeSecondary;
        this.forms = List.copyOf(b.forms);
        this.abilities = List.copyOf(b.abilities);
        this.geoName = b.geoName == null ? b.id : b.geoName;
        this.meleeHook = b.meleeHook;
        for (AbilityDefinition a : abilities) a.bind(this);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public ResourceLocation key() {
        return ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, id);
    }

    public String displayName() {
        return displayName;
    }

    public String title() {
        return title;
    }

    /** Where the lore is "quoted" from (a ballad, a chronicle...), or empty. */
    public String loreSource() {
        return loreSource;
    }

    public String lore() {
        return lore;
    }

    public Element element() {
        return element;
    }

    public Rarity rarity() {
        return rarity;
    }

    public WeaponClass weaponClass() {
        return weaponClass;
    }

    public float baseDamage() {
        return baseDamage;
    }

    public float heavyMultiplier() {
        return heavyMultiplier;
    }

    public float critChance() {
        return critChance;
    }

    public float critMultiplier() {
        return critMultiplier;
    }

    public float lifesteal() {
        return lifesteal;
    }

    public List<WeaponForm> forms() {
        return forms;
    }

    public boolean hasForms() {
        return forms.size() > 1;
    }

    @Nullable
    public WeaponForm form(int index) {
        if (forms.isEmpty()) return null;
        return forms.get(Math.floorMod(index, forms.size()));
    }

    @Nullable
    public WeaponForm form(WeaponData data) {
        return form(data.form());
    }

    public int themePrimary(WeaponData data) {
        WeaponForm f = form(data);
        return f != null ? f.themePrimary() : themePrimary;
    }

    public int themeSecondary(WeaponData data) {
        WeaponForm f = form(data);
        return f != null ? f.themeSecondary() : themeSecondary;
    }

    public List<AbilityDefinition> abilities() {
        return abilities;
    }

    @Nullable
    public AbilityDefinition ability(String abilityId) {
        for (AbilityDefinition a : abilities) if (a.id().equals(abilityId)) return a;
        return null;
    }

    /** Castable abilities (active + ultimate) in tree order. */
    public List<AbilityDefinition> castables() {
        List<AbilityDefinition> out = new ArrayList<>();
        for (AbilityDefinition a : abilities) if (a.kind() != AbilityKind.PASSIVE) out.add(a);
        return out;
    }

    /** Asset base name used by the GeckoLib files (e.g. {@code eclipse_reaper}). */
    public String geoName() {
        return geoName;
    }

    /** Full GeckoLib animation name for a short name, e.g. {@code attack -> animation.voidfang.attack}. */
    public String anim(String shortName) {
        return "animation." + geoName + "." + shortName;
    }

    @Nullable
    public MeleeHook meleeHook() {
        return meleeHook;
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private String title = "";
        private String lore = "";
        private String loreSource = "";
        private Element element = Element.VOID;
        private Rarity rarity = Rarity.LEGENDARY;
        private WeaponClass weaponClass = WeaponClass.LONGSWORD;
        private float baseDamage = 100;
        private float heavyMultiplier = 2.2f;
        private float critChance = 0.10f;
        private float critMultiplier = 1.75f;
        private float lifesteal;
        private int themePrimary = -1;
        private int themeSecondary = -1;
        private final List<WeaponForm> forms = new ArrayList<>();
        private final List<AbilityDefinition> abilities = new ArrayList<>();
        private String geoName;
        private MeleeHook meleeHook;

        private Builder(String id) {
            this.id = id;
            this.displayName = id.toUpperCase();
        }

        public Builder name(String displayName, String title) {
            this.displayName = displayName;
            this.title = title;
            return this;
        }

        /** The weapon's legend and the in-world text it comes from. */
        public Builder lore(String lore, String source) {
            this.loreSource = source;
            return lore(lore);
        }

        public Builder lore(String lore) {
            this.lore = lore;
            return this;
        }

        public Builder element(Element element) {
            this.element = element;
            return this;
        }

        public Builder rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder type(WeaponClass weaponClass) {
            this.weaponClass = weaponClass;
            return this;
        }

        public Builder damage(float baseDamage, float heavyMultiplier) {
            this.baseDamage = baseDamage;
            this.heavyMultiplier = heavyMultiplier;
            return this;
        }

        public Builder crit(float chance, float multiplier) {
            this.critChance = chance;
            this.critMultiplier = multiplier;
            return this;
        }

        public Builder lifesteal(float fraction) {
            this.lifesteal = fraction;
            return this;
        }

        public Builder theme(int primary, int secondary) {
            this.themePrimary = primary;
            this.themeSecondary = secondary;
            return this;
        }

        public Builder form(WeaponForm form) {
            this.forms.add(form);
            return this;
        }

        public Builder ability(AbilityDefinition ability) {
            this.abilities.add(ability);
            return this;
        }

        public Builder geo(String geoName) {
            this.geoName = geoName;
            return this;
        }

        public Builder onHit(MeleeHook hook) {
            this.meleeHook = hook;
            return this;
        }

        public WeaponDefinition build() {
            return new WeaponDefinition(this);
        }
    }
}
