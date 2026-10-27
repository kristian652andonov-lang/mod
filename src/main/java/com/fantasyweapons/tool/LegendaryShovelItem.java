package com.fantasyweapons.tool;

import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.material.Material;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** A legendary shovel (see {@link LegendaryTool}): earth, sand, gravel and snow (and it still makes paths). */
public class LegendaryShovelItem extends ShovelItem implements LegendaryTool {
    private final Material material;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LegendaryShovelItem(Material material, Properties properties) {
        super(LegendaryTools.TIER, properties.attributes(ShovelItem.createAttributes(LegendaryTools.TIER, 1.5f, -3.0f)));
        this.material = material;
    }

    @Override
    public Material material() {
        return material;
    }

    @Override
    public ToolKind kind() {
        return ToolKind.SHOVEL;
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        return LegendaryTools.enchantmentLevel(stack, enchantment, super.getEnchantmentLevel(stack, enchantment));
    }

    @Override
    public ItemEnchantments getAllEnchantments(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup) {
        return LegendaryTools.allEnchantments(stack, lookup, super.getAllEnchantments(stack, lookup));
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return LegendaryTools.supports(enchantment) && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LegendaryTools.tooltip(this, stack, tooltip);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        LegendaryTools.controllers(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        SidedHooks.get().provideItemRenderer(this, provider -> consumer.accept((GeoRenderProvider) provider));
    }
}
