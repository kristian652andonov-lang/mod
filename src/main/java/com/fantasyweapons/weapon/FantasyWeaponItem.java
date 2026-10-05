package com.fantasyweapons.weapon;

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
        AnimationController<FantasyWeaponItem> main = new AnimationController<>(this, CONTROLLER, 4, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            String idle = "idle";
            if (stack != null && stack.getItem() == this) {
                WeaponForm form = def.form(data(stack));
                if (form != null) idle = form.idleAnimation();
            }
            return state.setAndContinue(loops.computeIfAbsent(idle, n -> RawAnimation.begin().thenLoop(def.anim(n))));
        });
        for (String name : List.of("attack", "heavy_attack", "impact", "recovery")) {
            main.triggerableAnim(name, RawAnimation.begin().thenPlay(def.anim(name)));
        }
        main.triggerableAnim("charge", RawAnimation.begin().thenPlayAndHold(def.anim("charge")));
        for (WeaponForm form : def.forms()) {
            if (form.enterAnimation() != null) {
                main.triggerableAnim("form:" + form.id(), RawAnimation.begin().thenPlay(def.anim(form.enterAnimation())));
            }
            main.triggerableAnim("attack:" + form.id(), RawAnimation.begin().thenPlay(def.anim(form.attackAnimation())));
        }
        for (AbilityDefinition ability : def.castables()) {
            if (ability.chargeAnimation() != null && !ability.chargeAnimation().equals("charge")) {
                main.triggerableAnim("charge:" + ability.id(), RawAnimation.begin().thenPlayAndHold(def.anim(ability.chargeAnimation())));
            }
            RawAnimation cast = RawAnimation.begin();
            List<String> seq = ability.castAnimations().isEmpty() ? List.of("ability_activation", "ability_execution") : ability.castAnimations();
            for (String s : seq) cast.thenPlay(def.anim(s));
            main.triggerableAnim("cast:" + ability.id(), cast);
            ability.formCastAnimations().forEach((form, anims) -> {
                RawAnimation formCast = RawAnimation.begin();
                for (String s : anims) formCast.thenPlay(def.anim(s));
                main.triggerableAnim("cast:" + ability.id() + "@" + form, formCast);
            });
        }
        controllers.add(main);
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
        return Component.translatableWithFallback(getDescriptionId(stack), def.displayName())
                .withStyle(s -> s.withColor(def.rarity().color()).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WeaponData d = data(stack);
        int ink = 0xD8C9A8, faded = 0x9C8862, gold = 0xE8C26A;
        // epithet, kind, and a line of runes engraved along the blade
        tooltip.add(Component.literal(def.title()).withStyle(s -> s.withColor(def.element().light()).withItalic(true)));
        tooltip.add(Component.literal(def.rarity().displayName() + " " + def.weaponClass().displayName() + " of " + def.element().displayName())
                .withStyle(s -> s.withColor(mix(def.rarity().color(), gold, 0.35f))));
        String runes = (def.displayName() + " " + def.title()).toLowerCase().replaceAll("[^a-z ]", "");
        tooltip.add(Component.literal(runes.length() > 30 ? runes.substring(0, 30) : runes)
                .withStyle(s -> s.withColor(0x7A6646).withFont(ResourceLocation.withDefaultNamespace("alt"))));
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
        tooltip.add(Component.literal("\u2726 Level " + d.level() + " of " + ProgressionMath.maxLevel()).withStyle(s -> s.withColor(gold))
                .append(Component.literal(need > 0 ? "   " + String.format("%,d / %,d experience", d.exp(), need) : "   Mastered")
                        .withStyle(s -> s.withColor(faded))));
        tooltip.add(Component.literal(String.format("\u2694 %,.0f Damage", ProgressionMath.weaponDamage(def, d))).withStyle(s -> s.withColor(0xF0A080)));
        int pct = Math.round(ProgressionMath.mastery(def, d) * 100);
        tooltip.add(Component.literal("\u2727 Mastery " + pct + "%").withStyle(s -> s.withColor(0xC9A8F0))
                .append(Component.literal(d.masteryPoints() > 0 ? "   " + d.masteryPoints() + " points to spend" : "").withStyle(s -> s.withColor(faded))));
        WeaponForm form = def.form(d);
        if (form != null) {
            tooltip.add(Component.literal("\u25C8 " + form.displayName() + " form").withStyle(s -> s.withColor(form.themePrimary())));
        }
        // abilities
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Abilities").withStyle(s -> s.withColor(gold)));
        for (AbilityDefinition a : def.abilities()) {
            int lvl = d.abilityLevel(a);
            if (lvl > 0) {
                tooltip.add(Component.literal(" \u25C6 " + a.name()).withStyle(s -> s.withColor(def.element().primary()))
                        .append(Component.literal("  rank " + lvl).withStyle(s -> s.withColor(faded))));
            } else {
                tooltip.add(Component.literal(" \u25C7 " + a.name() + "  sealed until level " + a.unlockLevel()).withStyle(s -> s.withColor(0x5E5444)));
            }
        }
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
