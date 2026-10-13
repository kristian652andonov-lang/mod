package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Legendary Crafting Table's block entity: plays the artist's idle animation, and the crafting one while the table is
 * {@link LegendaryCraftingTableBlock#CRAFTING}. Whether it is crafting is kept here on the server: the players whose grid
 * makes something (told by their {@link LegendaryCraftingMenu}), and a few seconds after anything is forged.
 */
public class LegendaryCraftingTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.idle");
    private static final RawAnimation CRAFTING = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.crafting");
    /** How long the table keeps crafting after an item is taken from it, in ticks. */
    public static final int AFTERGLOW = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> crafters = new HashSet<>();
    private long forgedUntil;

    public LegendaryCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEGENDARY_CRAFTING_TABLE.get(), pos, state);
    }

    /** A table from before it was three blocks wide has no parts: its core builds them (see MachineCoreBlock#tick). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel server) server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Whether {@code player}'s grid at this table makes something. */
    public void setCrafting(UUID player, boolean crafting) {
        if (crafting) crafters.add(player);
        else crafters.remove(player);
    }

    /** An item was just forged here. */
    public void forged() {
        if (level != null) forgedUntil = level.getGameTime() + AFTERGLOW;
    }

    public boolean crafting() {
        return !crafters.isEmpty() || (level != null && level.getGameTime() < forgedUntil);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LegendaryCraftingTableBlockEntity be) {
        boolean crafting = be.crafting();
        if (state.getValue(LegendaryCraftingTableBlock.CRAFTING) != crafting) {
            level.setBlock(pos, state.setValue(LegendaryCraftingTableBlock.CRAFTING, crafting), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 10,
                state -> state.setAndContinue(getBlockState().getValue(LegendaryCraftingTableBlock.CRAFTING) ? CRAFTING : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
