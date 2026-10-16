package com.fantasyweapons.forge;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
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
        tooltip.add(Component.literal("⚒ Unfolds into a forge 21 wide, 21 deep and 22 high").withStyle(s -> s.withColor(faded)));
        tooltip.add(Component.literal("⚒ It rises before you: the block you choose is the foot of its stairs").withStyle(s -> s.withColor(faded)));
    }

    /**
     * The forge is far wider than anyone can reach, so it rises in front of the one placing it: the block they choose is the
     * foot of its front stairs, and its core stands {@link WorldforgeBlock#REACH} + 1 blocks further on, the way they face.
     */
    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
        Direction ahead = ctx.getHorizontalDirection();
        BlockPos core = ctx.getClickedPos().relative(ahead, WorldforgeBlock.REACH + 1);
        return BlockPlaceContext.at(ctx, core, Direction.UP);
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
