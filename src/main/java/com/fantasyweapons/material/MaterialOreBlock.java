package com.fantasyweapons.material;

import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A legendary material's ore. Drawn entirely by its GeckoLib model (a block entity renderer): the stone, the crystals
 * breaking out of it and the fragments floating above, with the glow pulsing in the material's own rhythm. Mined with
 * a diamond pickaxe or better it drops the raw material (see its loot table) and some experience.
 */
public class MaterialOreBlock extends DropExperienceBlock implements EntityBlock {
    private final Material material;

    public MaterialOreBlock(Material material, Properties properties) {
        super(UniformInt.of(4, 8), properties);
        this.material = material;
    }

    public Material material() {
        return material;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** It doesn't hide its neighbours' faces, but it is still solid rock to light. */
    @Override
    protected int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaterialOreBlockEntity(pos, state);
    }
}
