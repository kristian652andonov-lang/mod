package com.fantasyweapons.material;

import com.fantasyweapons.SidedHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** An ore block as an item: drawn with the ore's own animated model. */
public class MaterialOreItem extends BlockItem implements GeoItem {
    private final Material material;
    private final RawAnimation idle;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MaterialOreItem(MaterialOreBlock block, Properties properties) {
        super(block, properties);
        this.material = block.material();
        this.idle = RawAnimation.begin().thenLoop("animation." + material.oreAsset() + ".idle");
    }

    public Material material() {
        return material;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MaterialLore.append(material, MaterialLore.Form.ORE, tooltip);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(idle)));
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
