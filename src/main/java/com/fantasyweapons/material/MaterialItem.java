package com.fantasyweapons.material;

import com.fantasyweapons.SidedHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
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

/** A raw or refined legendary material, drawn with its glowing, idly animated GeckoLib model. */
public class MaterialItem extends Item implements GeoItem {
    private final Material material;
    private final boolean refined;
    private final RawAnimation idle;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MaterialItem(Material material, boolean refined, Properties properties) {
        super(properties);
        this.material = material;
        this.refined = refined;
        this.idle = RawAnimation.begin().thenLoop("animation." + asset() + ".idle");
    }

    public Material material() {
        return material;
    }

    public boolean refined() {
        return refined;
    }

    /** Asset name of the model, e.g. {@code voidfang_raw}. */
    public String asset() {
        return refined ? material.refinedAsset() : material.rawAsset();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MaterialLore.append(material, refined ? MaterialLore.Form.REFINED : MaterialLore.Form.RAW, tooltip);
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
