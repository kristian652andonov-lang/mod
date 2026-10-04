package com.fantasyweapons.weapon;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModComponents;
import net.minecraft.ChatFormatting;
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
        tooltip.add(Component.literal(def.title()).withStyle(s -> s.withColor(def.element().light()).withItalic(true)));
        tooltip.add(Component.literal(def.rarity().displayName().toUpperCase() + "  ·  " + def.element().displayName().toUpperCase()
                + "  ·  " + def.weaponClass().displayName()).withStyle(s -> s.withColor(def.rarity().color())));
        tooltip.add(Component.empty());
        long need = ProgressionMath.expToNext(d.level());
        tooltip.add(Component.literal("Level " + d.level() + " / " + ProgressionMath.maxLevel()).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(need > 0 ? "   EXP " + d.exp() + " / " + need : "   MAX").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal(String.format("Damage %,.0f", ProgressionMath.weaponDamage(def, d))).withStyle(s -> s.withColor(0xFF6B6B)));
        int pct = Math.round(ProgressionMath.mastery(def, d) * 100);
        tooltip.add(Component.literal("Mastery " + pct + "%" + (d.masteryPoints() > 0 ? "   (" + d.masteryPoints() + " points unspent)" : ""))
                .withStyle(s -> s.withColor(0xC9A2FF)));
        WeaponForm form = def.form(d);
        if (form != null) tooltip.add(Component.literal(form.label() + ": " + form.displayName()).withStyle(s -> s.withColor(form.themePrimary())));
        tooltip.add(Component.empty());
        for (AbilityDefinition a : def.abilities()) {
            int lvl = d.abilityLevel(a);
            if (lvl > 0) {
                tooltip.add(Component.literal("◆ " + a.name() + "  Lv " + lvl).withStyle(s -> s.withColor(def.element().primary())));
            } else {
                tooltip.add(Component.literal("◇ " + a.name() + "  (Lv " + a.unlockLevel() + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (flag.hasShiftDown() && !def.lore().isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal(def.lore()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
    }
}
