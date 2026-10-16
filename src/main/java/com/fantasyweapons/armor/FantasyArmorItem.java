package com.fantasyweapons.armor;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.SidedHooks;
import com.google.common.base.Suppliers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One piece of a legendary armor set, worn and shown with the set's GeckoLib model (looping idle; a ward flashes up when
 * the wearer is struck). Its piece bonuses are attribute modifiers; the set ability needs all four pieces.
 */
public class FantasyArmorItem extends ArmorItem implements GeoItem {
    private final ArmorSet set;
    private final RawAnimation idle;
    private final RawAnimation hit;
    private final Supplier<ItemAttributeModifiers> modifiers;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Per wearer and slot, the tick their last hit started (to play the hit animation once per hit). */
    private final java.util.Map<Long, Integer> lastHit = new java.util.HashMap<>();

    public FantasyArmorItem(ArmorSet set, Holder<ArmorMaterial> material, Type type, Properties properties) {
        // an empty, hidden attribute component: the item's own modifiers still apply, but vanilla's raw list stays out of
        // the tooltip (which lists the bonuses itself, in words)
        super(material, type, properties.component(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY.withTooltip(false)));
        this.set = set;
        this.idle = RawAnimation.begin().thenLoop("animation." + set.asset() + ".idle");
        this.hit = RawAnimation.begin().thenPlay("animation." + set.asset() + ".hit").thenLoop("animation." + set.asset() + ".idle");
        this.modifiers = Suppliers.memoize(this::buildModifiers);
    }

    public ArmorSet set() {
        return set;
    }

    private ItemAttributeModifiers buildModifiers() {
        ItemAttributeModifiers mods = super.getDefaultAttributeModifiers();
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(getType().getSlot());
        List<ArmorSet.Bonus> bonuses = set.bonuses(getType());
        for (int i = 0; i < bonuses.size(); i++) {
            ArmorSet.Bonus b = bonuses.get(i);
            mods = mods.withModifierAdded(b.attribute().get(),
                    new AttributeModifier(FantasyWeapons.id("armor/" + set.itemId(getType()) + "_" + i), b.amount(), b.operation()), group);
        }
        return mods;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    /** How many pieces of {@code set} {@code entity} is wearing. */
    public static int worn(LivingEntity entity, ArmorSet set) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (entity.getItemBySlot(slot).getItem() instanceof FantasyArmorItem a && a.set == set) n++;
        }
        return n;
    }

    public static boolean fullSet(LivingEntity entity, ArmorSet set) {
        return worn(entity, set) == 4;
    }

    /** The set {@code entity} wears all four pieces of, or null. */
    @org.jetbrains.annotations.Nullable
    public static ArmorSet fullSet(LivingEntity entity) {
        if (!(entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof FantasyArmorItem helmet)) return null;
        return fullSet(entity, helmet.set) ? helmet.set : null;
    }

    /** Only the set's class can put it on. */
    @Override
    public boolean canEquip(ItemStack stack, net.minecraft.world.entity.EquipmentSlot armorType, net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof Player player && !com.fantasyweapons.playerclass.ClassGear.canUse(player, stack)) return false;
        return super.canEquip(stack, armorType, entity);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!com.fantasyweapons.playerclass.ClassGear.canUse(player, stack)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) com.fantasyweapons.playerclass.ClassGear.refuse(sp, stack);
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int color = set.material().color();
        int light = mix(color, 0xFFFFFF, 0.45f), ink = mix(0xD8C9A8, color, 0.15f), faded = mix(color, 0x9C8862, 0.55f);
        tooltip.add(Component.literal(set.armorClass().displayName() + " armor of " + set.element().displayName()).withStyle(s -> s.withColor(color)));
        com.fantasyweapons.playerclass.ClassGear.tooltip(SidedHooks.get().clientPlayer(), stack, tooltip);
        for (String line : wrap(set.look(), 40)) tooltip.add(Component.literal(line).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        ArmorClass c = set.armorClass();
        tooltip.add(Component.literal(String.format(Locale.ROOT, "⛨ %d Armor   %s Toughness", c.defense(getType()), trim(c.toughness())))
                .withStyle(s -> s.withColor(0xC8D2DC)));
        for (ArmorSet.Bonus b : set.bonuses(getType())) tooltip.add(Component.literal("◆ " + b.text()).withStyle(s -> s.withColor(light)));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "◆ Legendary Ward: legendary blows -%d%%, never more than %d%% of your health with all four",
                Math.round(ArmorEffects.WARD_PER_PIECE * 100), Math.round((1 - ArmorEffects.WARD_CAP_PER_PIECE * 4) * 100))).withStyle(s -> s.withColor(0xC8D2DC)));
        tooltip.add(Component.empty());
        Player player = SidedHooks.get().clientPlayer();
        int worn = player == null ? 0 : worn(player, set);
        boolean active = worn == 4;
        tooltip.add(Component.literal("Set: " + set.abilityName().toUpperCase(Locale.ROOT) + " (" + worn + "/4)")
                .withStyle(s -> s.withColor(active ? 0xFFE7A8 : faded).withBold(active)));
        for (String line : wrap(set.abilityText(), 40)) {
            tooltip.add(Component.literal("  " + line).withStyle(s -> s.withColor(active ? ink : faded).withItalic(!active)));
        }
        for (String line : wrap(SetAbilities.details(set), 40)) {
            tooltip.add(Component.literal("  " + line).withStyle(s -> s.withColor(active ? light : faded).withItalic(!active)));
        }
    }

    private static String trim(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

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

    // ------------------------------------------------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> {
            // a hit makes the ward flash up once, then the idle carries on
            if (state.getData(DataTickets.ENTITY) instanceof LivingEntity wearer && wearer.hurtTime > 0 && wearer.hurtDuration > 0) {
                int started = wearer.tickCount - (wearer.hurtDuration - wearer.hurtTime);
                EquipmentSlot slot = state.getData(DataTickets.EQUIPMENT_SLOT);
                long key = ((long) wearer.getId() << 3) | (slot == null ? 0 : slot.ordinal());
                Integer last = lastHit.get(key);
                if (last == null || Math.abs(last - started) > 1) {
                    lastHit.put(key, started);
                    state.getController().forceAnimationReset();
                    state.getController().setAnimation(hit);
                    return software.bernie.geckolib.animation.PlayState.CONTINUE;
                }
            }
            if (state.getController().getCurrentRawAnimation() == hit && !state.getController().hasAnimationFinished()) {
                return software.bernie.geckolib.animation.PlayState.CONTINUE;
            }
            return state.setAndContinue(idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        SidedHooks.get().provideArmorRenderer(this, provider -> consumer.accept((GeoRenderProvider) provider));
    }
}
