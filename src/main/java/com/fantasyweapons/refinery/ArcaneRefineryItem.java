package com.fantasyweapons.refinery;

import com.fantasyweapons.text.Tr;
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
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** The refinery as an item, drawn with its own (idling) model. */
public class ArcaneRefineryItem extends BlockItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.arcane_refinery.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArcaneRefineryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ink = 0xD8C9A8, faded = 0x9C8862, arcane = 0xC9A8F0;
        tooltip.add(Component.literal(Tr.s("An iron furnace built to refine legendary materials")).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(Tr.s("♨ Raw material + any fuel → refined in 20 s")).withStyle(s -> s.withColor(arcane)));
        tooltip.add(Component.literal(Tr.s("♨ Blaze rods refine twice as fast")).withStyle(s -> s.withColor(arcane)));
        tooltip.add(Component.literal(Tr.s("⚒ Hoppers: top = input, sides = fuel, bottom = output")).withStyle(s -> s.withColor(faded)));
        tooltip.add(Component.literal(Tr.s("⚒ Needs a space 3 wide and 2 high")).withStyle(s -> s.withColor(faded)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
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
