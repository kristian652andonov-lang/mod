package com.fantasyweapons.material;

import com.fantasyweapons.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Holds the animation state of an ore block: its idle loop (crystals breathing, fragments drifting). */
public class MaterialOreBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation[] IDLE = new RawAnimation[Material.values().length];

    static {
        for (Material m : Material.values()) IDLE[m.ordinal()] = RawAnimation.begin().thenLoop("animation." + m.oreAsset() + ".idle");
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MaterialOreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATERIAL_ORE.get(), pos, state);
    }

    public Material material() {
        return getBlockState().getBlock() instanceof MaterialOreBlock ore ? ore.material() : Material.VOID_CRYSTAL;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE[material().ordinal()])));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
