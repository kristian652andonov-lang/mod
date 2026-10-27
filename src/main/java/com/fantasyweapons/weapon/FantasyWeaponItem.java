package com.fantasyweapons.weapon;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One of the 13 fantasy weapons. A GeckoLib item using the artist-supplied model/animations.
 * <p>
 * All gameplay numbers are derived from the stack's {@link WeaponData}; the item itself holds no mutable state.
 */
public class FantasyWeaponItem extends Item implements GeoItem {
    public static final String CONTROLLER = "main";
    private static final ResourceLocation REACH_ID = FantasyWeapons.id("weapon_reach");

    private final WeaponDefinition def;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<String, RawAnimation> loops = new HashMap<>();

    public FantasyWeaponItem(WeaponDefinition def, Properties properties) {
        super(properties);
        this.def = def;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public WeaponDefinition definition() {
        return def;
    }

    public static WeaponData data(ItemStack stack) {
        return stack.getOrDefault(ModComponents.WEAPON_DATA.get(), WeaponData.EMPTY);
    }

    // ------------------------------------------------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<FantasyWeaponItem> main = new RigidAnimationController<>(this, CONTROLLER, 4, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            String idle = "idle";
            if (stack != null && stack.getItem() == this) {
                WeaponForm form = def.form(data(stack));
                if (form != null) idle = form.idleAnimation();
            }
            return state.setAndContinue(loops.computeIfAbsent(idle, n -> RawAnimation.begin().thenLoop(def.anim(n))));
        });
        Map<String, List<String>> plays = triggers(def);
        java.util.Set<String> held = heldTriggers(def);
        plays.forEach((trigger, names) -> {
            main.triggerableAnim(trigger, sequence(names, held.contains(trigger), null));
            // and in each form that has its own versions of the animations
            for (WeaponForm form : def.forms()) {
                if (form.animationVariant() != null) main.triggerableAnim(formTrigger(trigger, form), sequence(names, held.contains(trigger), form));
            }
        });
        for (WeaponForm form : def.forms()) {
            if (form.enterAnimation() != null) {
                main.triggerableAnim("form:" + form.id(), RawAnimation.begin().thenPlay(def.anim(form.enterAnimation())));
            }
            main.triggerableAnim("attack:" + form.id(), RawAnimation.begin().thenPlay(def.anim(form.animation(form.attackAnimation()))));
        }
        controllers.add(main);
    }

    /** The trigger a summoned power plays when it is released. */
    public static final String RELEASE = "release";

    /**
     * Every trigger of a weapon but those that name a form ("form:", "attack:"), as the animations it plays in turn
     * (short names, as the weapon plays them as made).
     */
    public static Map<String, List<String>> triggers(WeaponDefinition def) {
        Map<String, List<String>> plays = new java.util.LinkedHashMap<>();
        for (String name : List.of("attack", "heavy_attack", "impact", "recovery", "charge")) plays.put(name, List.of(name));
        for (AbilityDefinition ability : def.castables()) {
            if (ability.chargeAnimation() != null && !ability.chargeAnimation().equals("charge")) {
                plays.put("charge:" + ability.id(), List.of(ability.chargeAnimation()));
            }
            plays.put("cast:" + ability.id(), ability.castAnimations().isEmpty() ? List.of("ability_activation", "ability_execution") : ability.castAnimations());
            ability.formCastAnimations().forEach((form, anims) -> plays.put("cast:" + ability.id() + "@" + form, anims));
        }
        if (def.rarity() == Rarity.BRILLIANT_LIGHT) plays.put(RELEASE, List.of("ability_execution", "recovery"));
        return plays;
    }

    /** The triggers whose last animation holds its pose until the trigger is stopped (charges; summoned powers). */
    private static java.util.Set<String> heldTriggers(WeaponDefinition def) {
        java.util.Set<String> held = new java.util.HashSet<>();
        held.add("charge");
        for (AbilityDefinition ability : def.castables()) {
            if (ability.chargeAnimation() != null && !ability.chargeAnimation().equals("charge")) held.add("charge:" + ability.id());
            // a summoned power holds its last pose while it is summoned, until it is released (RELEASE)
            if (def.rarity() == Rarity.BRILLIANT_LIGHT) held.add("cast:" + ability.id());
        }
        return held;
    }

    /** The animations {@code names} played in turn (as {@code form} plays them, if given), the last one held if asked. */
    private RawAnimation sequence(List<String> names, boolean holdLast, @org.jetbrains.annotations.Nullable WeaponForm form) {
        RawAnimation raw = RawAnimation.begin();
        for (int i = 0; i < names.size(); i++) {
            String anim = def.anim(form == null ? names.get(i) : form.animation(names.get(i)));
            if (holdLast && i == names.size() - 1) raw.thenPlayAndHold(anim);
            else raw.thenPlay(anim);
        }
        return raw;
    }

    /**
     * The name a trigger goes by in {@code form}: a form with its own versions of the weapon's animations (Eclipse
     * Reaper's dark form) has its own copy of each trigger, playing those. Triggers that name a form already
     * ("form:", "attack:") are the same in every form.
     */
    public static String formTrigger(String trigger, @org.jetbrains.annotations.Nullable WeaponForm form) {
        if (form == null || form.animationVariant() == null || trigger.startsWith("form:") || trigger.startsWith("attack:")) return trigger;
        return trigger + "~" + form.id();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        SidedHooks.get().provideRenderer(this, provider -> consumer.accept((GeoRenderProvider) provider));
    }

    /** Name of the trigger to use for the cast animation of an ability, honouring form-specific overrides. */
    public static String castTrigger(AbilityDefinition ability, @org.jetbrains.annotations.Nullable WeaponForm form) {
        return form != null && ability.formCastAnimations().containsKey(form.id()) ? "cast:" + ability.id() + "@" + form.id() : "cast:" + ability.id();
    }

    /** Name of the trigger to use for the charge animation of an ability. */
    public static String chargeTrigger(AbilityDefinition ability) {
        return ability.chargeAnimation() != null && !ability.chargeAnimation().equals("charge") ? "charge:" + ability.id() : "charge";
    }

    // ------------------------------------------------------------------------------------------------------------
    // Item behaviour
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel serverLevel) {
            WeaponData d = data(stack);
            if (d.weaponId().isEmpty()) {
                stack.set(ModComponents.WEAPON_DATA.get(), d.withId(UUID.randomUUID()));
            }
            // a weapon at its last level is owed any points its levels now give that it never got
            if (serverLevel.getGameTime() % 40 == 0) {
                WeaponData owed = com.fantasyweapons.progression.ExpService.owed(def, data(stack));
                if (owed != null) stack.set(ModComponents.WEAPON_DATA.get(), owed);
            }
            GeoItem.getOrAssignId(stack, serverLevel);
        }
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        WeaponData d = data(stack);
        WeaponForm form = def.form(d);
        float damage = ProgressionMath.weaponDamage(def, d) * (form != null ? form.damageFactor() : 1f);
        double speed = def.weaponClass().attackSpeed() * (form != null ? form.attackSpeedFactor() : 1f);
        double reach = def.weaponClass().reachBonus() + (form != null ? form.reachBonus() : 0f);
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()
                .withTooltip(false);
    }

    /** Progression updates change the stack's data; that must not replay the equip animation every kill. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.getItem() != newStack.getItem()) return true;
        return !data(oldStack).idOrNil().equals(data(newStack).idOrNil());
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (entity.level().isClientSide) {
            SidedHooks.get().onWeaponSwing(entity, stack, hand);
        }
        return false;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        // each weapon's name in its own colour (void purple, wild green, frost blue...), not just its rarity's
        int theme = def.themePrimary(data(stack));
        return Component.translatableWithFallback(getDescriptionId(stack), def.displayName())
                .withStyle(s -> s.withColor(mix(theme, 0xFFFFFF, 0.12f)).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WeaponData d = data(stack);
        // everything is tinted by the weapon's own colour: its name, epithet, runes, headings and border
        int theme = def.themePrimary(d);
        int light = mix(theme, 0xFFFFFF, 0.55f);
        int ink = mix(0xD8C9A8, theme, 0.15f), faded = mix(theme, 0x9C8862, 0.5f), gold = mix(theme, 0xFFFFFF, 0.3f);
        // epithet, kind, and a line of runes engraved along the blade
        tooltip.add(Component.literal(def.title()).withStyle(s -> s.withColor(light).withItalic(true)));
        if (def.rarity().classTier()) {
            // a class of its own: named on its own line, in its own colour
            tooltip.add(Component.literal("\u2726 " + def.rarity().displayName() + " \u2726").withStyle(s -> s.withColor(def.rarity().color()).withBold(true)));
            tooltip.add(Component.literal(Tr.f("%s of %s", def.weaponClass().displayName(), def.element().displayName())).withStyle(s -> s.withColor(theme)));
        } else {
            tooltip.add(Component.literal(Tr.f("%s %s of %s", def.rarity().displayName(), def.weaponClass().displayName(), def.element().displayName()))
                    .withStyle(s -> s.withColor(theme)));
        }
        com.fantasyweapons.playerclass.ClassGear.tooltip(com.fantasyweapons.SidedHooks.get().clientPlayer(), stack, tooltip);
        String runes = (def.englishName() + " " + def.englishTitle()).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "");
        tooltip.add(Component.literal(runes.length() > 30 ? runes.substring(0, 30) : runes)
                .withStyle(s -> s.withColor(mix(theme, 0x000000, 0.4f)).withFont(ResourceLocation.withDefaultNamespace("alt"))));
        // the legend
        if (!def.lore().isEmpty()) {
            tooltip.add(Component.empty());
            List<String> lines = wrap(def.lore(), 40);
            for (int i = 0; i < lines.size(); i++) {
                String line = (i == 0 ? "\u201C" : " ") + lines.get(i) + (i == lines.size() - 1 ? "\u201D" : "");
                tooltip.add(Component.literal(line).withStyle(s -> s.withColor(ink).withItalic(true)));
            }
            if (!def.loreSource().isEmpty()) {
                tooltip.add(Component.literal("        \u2014 " + def.loreSource()).withStyle(s -> s.withColor(faded).withItalic(true)));
            }
        }
        tooltip.add(Component.empty());
        long need = ProgressionMath.expToNext(d.level());
        tooltip.add(Component.literal("\u2726 " + Tr.f("Level %s of %s", d.level(), ProgressionMath.maxLevel())).withStyle(s -> s.withColor(gold))
                .append(Component.literal("   " + (need > 0 ? Tr.f("%s / %s experience", String.format(java.util.Locale.ROOT, "%,d", d.exp()), String.format(java.util.Locale.ROOT, "%,d", need)) : Tr.s("Mastered")))
                        .withStyle(s -> s.withColor(faded))));
        if (need > 0) {
            var core = com.fantasyweapons.material.Material.ofWeapon(def.id());
            if (core != null) {
                String article = "AEIOU".indexOf(core.refinedEnglish().charAt(0)) >= 0 ? "Infuse an %s for a level" : "Infuse a %s for a level";
                tooltip.add(Component.literal("   " + Tr.f(article, core.refinedName())).withStyle(s -> s.withColor(faded).withItalic(true)));
            }
        }
        tooltip.add(Component.literal("\u2694 " + Tr.f("%s Damage", String.format(java.util.Locale.ROOT, "%,.0f", ProgressionMath.weaponDamage(def, d)))).withStyle(s -> s.withColor(0xF0A080)));
        int pct = Math.round(ProgressionMath.mastery(def, d) * 100);
        tooltip.add(Component.literal("\u2727 " + Tr.f("Mastery %s%%", pct)).withStyle(s -> s.withColor(0xC9A8F0))
                .append(Component.literal(d.masteryPoints() > 0 ? "   " + Tr.f(d.masteryPoints() == 1 ? "%s point to spend" : "%s points to spend", d.masteryPoints()) : "").withStyle(s -> s.withColor(faded))));
        WeaponForm form = def.form(d);
        if (form != null) {
            tooltip.add(Component.literal("\u25C8 " + Tr.f("%s form", form.displayName())).withStyle(s -> s.withColor(form.themePrimary())));
        }
        // abilities
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(Tr.s("Abilities")).withStyle(s -> s.withColor(gold)));
        for (AbilityDefinition a : def.abilities()) {
            int lvl = d.abilityLevel(a);
            if (lvl > 0) {
                tooltip.add(Component.literal(" \u25C6 " + a.name()).withStyle(s -> s.withColor(light))
                        .append(Component.literal("  " + Tr.f("rank %s", lvl)).withStyle(s -> s.withColor(faded))));
            } else {
                tooltip.add(Component.literal(" \u25C7 " + a.name() + "  " + Tr.f("sealed until level %s", a.unlockLevel())).withStyle(s -> s.withColor(0x5E5444)));
            }
        }
    }

    /** Tooltip border/background colours for a weapon stack: {top border, bottom border, background}. */
    public static int[] tooltipColors(ItemStack stack) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return null;
        int theme = item.def.themePrimary(data(stack));
        return new int[]{0xFF000000 | mix(theme, 0xFFFFFF, 0.2f), 0xFF000000 | mix(theme, 0x000000, 0.6f), 0xF0000000 | mix(theme, 0x0A0604, 0.9f)};
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Word-wraps {@code text} into lines of at most {@code width} characters. */
    private static List<String> wrap(String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
