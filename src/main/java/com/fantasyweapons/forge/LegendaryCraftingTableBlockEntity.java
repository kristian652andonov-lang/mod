package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModParticles;

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
 * Is {@link LegendaryCraftingTableBlock#CRAFTING} while it forges and for a moment after. The artist's idle animation plays
 * throughout; while it forges the item being forged floats over the grid (it is sent to the clients, see the renderer),
 * glyphs stream into it from the two totems, the hammer's sparks burst about it and embers rise from the grid.
 */
public class LegendaryCraftingTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_crafting_table.idle");
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
    /** The recipe last chosen in the table's screen, shown to whoever opens it next. */
    @Nullable
    private ResourceLocation chosen;

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

    @Nullable
    public ResourceLocation chosen() {
        return chosen;
    }

    public void choose(@Nullable ResourceLocation id) {
        chosen = id;
        setChanged();
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
        sync();
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
        be.sync();
        level.playSound(null, pos, ModSounds.TABLE_FORGE_DONE.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    /** Tells the players nearby what is being forged (and how far along), for the item floating over the grid. */
    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (busy()) {
            tag.put("Forging", forging.getItem(0).save(registries));
            tag.putInt("Progress", progress);
            tag.putInt("Total", total);
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** On a client: only what is being forged (the rest of the table lives on the server). */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        forging.getItems().set(0, ItemStack.parseOptional(registries, tag.getCompound("Forging")));
        progress = tag.getInt("Progress");
        total = tag.getInt("Total");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    /** Where a spot of the model (pixels about the core's bottom centre, facing north) is in the world, as the table faces. */
    public net.minecraft.world.phys.Vec3 spot(double mx, double my, double mz) {
        double x = -mx / 16, z = mz / 16;
        var facing = getBlockState().getValue(LegendaryCraftingTableBlock.FACING);
        double a = switch (facing) {
            case SOUTH -> Math.PI;
            case WEST -> Math.PI / 2;
            case EAST -> Math.PI * 1.5;
            default -> 0;
        };
        double rx = x * Math.cos(a) + z * Math.sin(a), rz = -x * Math.sin(a) + z * Math.cos(a);
        return new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + rx, worldPosition.getY() + my / 16, worldPosition.getZ() + 0.5 + rz);
    }

    /** Just over the grid, under the gyroscope, where the item being forged lies. */
    public net.minecraft.world.phys.Vec3 forgePoint() {
        return spot(0, 19.6, 0);
    }

    /** On a client: the forging goes on between updates, and the table shows it. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, LegendaryCraftingTableBlockEntity be) {
        if (be.busy() && be.progress < be.total) be.progress++;
        if (!state.getValue(LegendaryCraftingTableBlock.CRAFTING)) return;
        var random = level.random;
        var at = be.forgePoint();
        if (be.busy()) {
            // runes drawn from the totems' crowns into the work
            for (int side = -1; side <= 1; side += 2) {
                var crown = be.spot(side * 8.9, 27.5, 6.4);
                if (random.nextInt(3) == 0) {
                    level.addParticle(ModParticles.FORGE_RUNE.get(), at.x, at.y, at.z,
                            crown.x - at.x + (random.nextDouble() - 0.5) * 0.25, crown.y - at.y + random.nextDouble() * 0.3,
                            crown.z - at.z + (random.nextDouble() - 0.5) * 0.25);
                }
            }
            // the hammer's strikes: sparks flung off the work, and a flash
            if (random.nextInt(18) == 0) {
                for (int i = 0; i < 12; i++) {
                    double vx = (random.nextDouble() - 0.5) * 0.3, vy = 0.08 + random.nextDouble() * 0.2, vz = (random.nextDouble() - 0.5) * 0.3;
                    level.addParticle(ModParticles.FORGE_SPARK.get(), at.x, at.y + 0.05, at.z, vx, vy, vz);
                }
                level.addParticle(ModParticles.FORGE_FLARE.get(), at.x, at.y + 0.05, at.z, 0, 0, 0);
            }
            // embers rising off the grid
            if (random.nextInt(4) == 0) {
                var ember = be.spot((random.nextDouble() - 0.5) * 11, 18.6, (random.nextDouble() - 0.5) * 11);
                level.addParticle(ModParticles.FORGE_EMBER.get(), ember.x, ember.y, ember.z, 0, 0.012 + random.nextDouble() * 0.015, 0);
            }
            // stars circling up about the work
            if (level.getGameTime() % 4 == 0) {
                double a = level.getGameTime() * 0.31;
                level.addParticle(ModParticles.FORGE_STAR.get(), at.x + Math.cos(a) * 0.45, at.y, at.z + Math.sin(a) * 0.45, 0, 0.025, 0);
            }
        } else if (random.nextInt(3) == 0) {
            // forged: flares of light bursting off the finished work
            level.addParticle(ModParticles.FORGE_FLARE.get(), at.x + (random.nextDouble() - 0.5) * 0.4, at.y + random.nextDouble() * 0.3,
                    at.z + (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.06, random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.06);
            level.addParticle(ModParticles.FORGE_STAR.get(), at.x, at.y, at.z,
                    (random.nextDouble() - 0.5) * 0.2, 0.05 + random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.2);
        }
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
        if (chosen != null) tag.putString("Chosen", chosen.toString());
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
        chosen = tag.contains("Chosen") ? ResourceLocation.tryParse(tag.getString("Chosen")) : null;
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
        // the artist's crafting animation forms a chestplate whatever is forged: the idle one plays always, and the table's
        // own effects show the forging (see clientTick and the renderer)
        controllers.add(new AnimationController<>(this, "main", 10, state -> state.setAndContinue(IDLE)));
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
