package com.fantasyweapons.forge;

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

/** The Legendary Crafting Table as an item, drawn with its own (idling) model. */
public class LegendaryCraftingTableItem extends BlockItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LegendaryCraftingTableItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ink = 0xD8C9A8, faded = 0x9C8862, star = 0xA8E6F0;
        tooltip.add(Component.literal(Tr.s("A crafting table worthy of legends")).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(Tr.s("✦ A 9x9 crafting grid")).withStyle(s -> s.withColor(star)));
        tooltip.add(Component.literal(Tr.s("✦ Forges the legendary weapons and armor")).withStyle(s -> s.withColor(star)));
        tooltip.add(Component.literal(Tr.s("✦ Crafts anything a crafting table can")).withStyle(s -> s.withColor(star)));
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
