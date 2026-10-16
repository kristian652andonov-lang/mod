package com.fantasyweapons.forge;

import com.fantasyweapons.SidedHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** The Worldforge as an item: its folded core (the artist's core model). */
public class WorldforgeItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WorldforgeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ink = 0xD8B0A8, blood = 0xFF5A4A, faded = 0x9C7068;
        tooltip.add(Component.literal("It was not built. It was summoned.").withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("☠ An 11x11 forging grid").withStyle(s -> s.withColor(blood)));
        tooltip.add(Component.literal("☠ Forges the two ultimate weapons from every legend").withStyle(s -> s.withColor(blood)));
        tooltip.add(Component.literal("⚒ Unfolds into a forge 7 wide, 7 deep and 7 high").withStyle(s -> s.withColor(faded)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
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
