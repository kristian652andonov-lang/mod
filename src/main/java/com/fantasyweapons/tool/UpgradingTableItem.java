package com.fantasyweapons.tool;

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

/** The Legendary Upgrading Table as an item, drawn with its own (idling) model. */
public class UpgradingTableItem extends BlockItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_upgrading_table.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public UpgradingTableItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ink = 0xD8C9A8, faded = 0x9C8862, copper = 0xF0A878, xp = 0xB6F27A;
        tooltip.add(Component.literal(Tr.s("Where legendary tools are made greater")).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(Tr.s("⚒ Raises a legendary tool's mining area,")).withStyle(s -> s.withColor(copper)));
        tooltip.add(Component.literal(Tr.s("   Fortune and Efficiency, a step at a time")).withStyle(s -> s.withColor(copper)));
        tooltip.add(Component.literal(Tr.s("✦ Costs the tool's refined material and experience")).withStyle(s -> s.withColor(xp)));
        tooltip.add(Component.literal(Tr.s("⚒ Needs a space 1 wide and 2 high")).withStyle(s -> s.withColor(faded)));
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
