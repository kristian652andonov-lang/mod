package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The Legendary Crafting Table's block entity. It keeps what lies on the 9x9 grid (shared by everyone who opens the
 * table, and kept when they close it), the "Forged" slot, and the forging under way: a legendary recipe started at the
 * table takes its time ({@link ForgeRecipe#time()}), whether or not anyone watches, and the forged item then waits in the
 * Forged slot. Breaking the table drops the grid, the Forged slot and, mid-forging, what the forging took.
 * <p>
 * Plays the artist's crafting animation (and is {@link LegendaryCraftingTableBlock#CRAFTING}) while it forges and for a
 * moment after; the idle one otherwise.
 */
public class LegendaryCraftingTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.idle");
    private static final RawAnimation CRAFTING = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.crafting");
    /** How long the table keeps crafting after an item is forged, in ticks. */
    public static final int AFTERGLOW = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer grid = new Contents(ForgeRecipe.SIZE * ForgeRecipe.SIZE);
    private final SimpleContainer output = new Contents(1);
    /** The item being forged (shown in the table's screen, never taken from there). */
    private final SimpleContainer forging = new Contents(1);
    @Nullable
    private ResourceLocation recipe;
    private int progress, total;
    /** What the forging took from the grid, given back if the table is broken before it is done. */
    private final List<ItemStack> refund = new ArrayList<>();
    private long glowUntil;

    public LegendaryCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEGENDARY_CRAFTING_TABLE.get(), pos, state);
    }

    public SimpleContainer grid() {
        return grid;
    }

    public SimpleContainer output() {
        return output;
    }

    public SimpleContainer forging() {
        return forging;
    }

    public boolean busy() {
        return !forging.getItem(0).isEmpty();
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return total;
    }

    @Nullable
    public ResourceLocation recipe() {
        return recipe;
    }

    /** Starts forging {@code result} by {@code id}, which takes {@code time} ticks; {@code took} is what came off the grid. */
    public void start(ResourceLocation id, ItemStack result, int time, List<ItemStack> took) {
        recipe = id;
        forging.setItem(0, result.copy());
        progress = 0;
        total = Math.max(1, time);
        refund.clear();
        for (ItemStack s : took) if (!s.isEmpty()) refund.add(s.copy());
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.TABLE_FORGE_START.get(), SoundSource.BLOCKS, 1f, 1f);
        }
    }

    /** Which stage of {@code stages} the forging is in. */
    public static int stage(int progress, int total, int stages) {
        return total <= 0 ? 0 : Math.min(stages - 1, progress * stages / total);
    }

    public boolean crafting() {
        return busy() || (level != null && level.getGameTime() < glowUntil);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LegendaryCraftingTableBlockEntity be) {
        boolean crafting = be.crafting();
        if (state.getValue(LegendaryCraftingTableBlock.CRAFTING) != crafting) {
            level.setBlock(pos, state.setValue(LegendaryCraftingTableBlock.CRAFTING, crafting), Block.UPDATE_CLIENTS);
        }
        if (!be.busy()) return;
        if (be.progress < be.total) {
            int stages = ForgeStages.count(be.forging.getItem(0));
            int before = stage(be.progress, be.total, stages);
            be.progress++;
            if (stage(be.progress, be.total, stages) != before) {
                level.playSound(null, pos, ModSounds.TABLE_FORGE_STAGE.get(), SoundSource.BLOCKS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
            } else if (level.random.nextInt(70) == 0) {
                // the table's own hammer, now and then
                level.playSound(null, pos, ModSounds.TABLE_FORGE_STRIKE.get(), SoundSource.BLOCKS, 0.55f, 0.85f + level.random.nextFloat() * 0.3f);
            }
            be.setChanged();
            return;
        }
        // forged: into the Forged slot, as soon as there is room there
        ItemStack made = be.forging.getItem(0), there = be.output.getItem(0);
        if (there.isEmpty()) {
            be.output.setItem(0, made.copy());
        } else if (ItemStack.isSameItemSameComponents(there, made) && there.getCount() + made.getCount() <= there.getMaxStackSize()) {
            there.grow(made.getCount());
            be.output.setChanged();
        } else {
            return;
        }
        be.forging.setItem(0, ItemStack.EMPTY);
        be.refund.clear();
        be.recipe = null;
        be.progress = be.total = 0;
        be.glowUntil = level.getGameTime() + AFTERGLOW;
        be.setChanged();
        level.playSound(null, pos, ModSounds.TABLE_FORGE_DONE.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    /** Everything the table drops when it is broken. */
    public List<ItemStack> drops() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < grid.getContainerSize(); i++) if (!grid.getItem(i).isEmpty()) all.add(grid.getItem(i));
        if (!output.getItem(0).isEmpty()) all.add(output.getItem(0));
        if (busy()) all.addAll(refund);
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Grid", ContainerHelper.saveAllItems(new CompoundTag(), grid.getItems(), true, registries));
        if (!output.getItem(0).isEmpty()) tag.put("Output", output.getItem(0).save(registries));
        if (busy()) {
            tag.put("Forging", forging.getItem(0).save(registries));
            if (recipe != null) tag.putString("Recipe", recipe.toString());
            tag.putInt("Progress", progress);
            tag.putInt("Total", total);
            ListTag took = new ListTag();
            for (ItemStack s : refund) took.add(s.save(registries));
            tag.put("Refund", took);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Grid"), items, registries);
        for (int i = 0; i < items.size(); i++) grid.getItems().set(i, items.get(i));
        output.getItems().set(0, ItemStack.parseOptional(registries, tag.getCompound("Output")));
        forging.getItems().set(0, ItemStack.parseOptional(registries, tag.getCompound("Forging")));
        recipe = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        progress = tag.getInt("Progress");
        total = tag.getInt("Total");
        refund.clear();
        for (Tag t : tag.getList("Refund", Tag.TAG_COMPOUND)) refund.add(ItemStack.parseOptional(registries, (CompoundTag) t));
    }

    /** A table has no parts yet when it was placed before it was three blocks wide: its core builds them (see MachineCoreBlock#tick). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel server) server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
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

    /** A container of the table's that saves the table when it changes. */
    private final class Contents extends SimpleContainer {
        Contents(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            LegendaryCraftingTableBlockEntity.this.setChanged();
        }
    }
}
