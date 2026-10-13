package com.fantasyweapons.refinery;

import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;

/**
 * The refinery's furnace. Eight of a raw legendary material in the crucible with eight of its additive (magma for
 * Infernite, ender pearls for Void Crystal...) and fuel in the firebox: while something refines, fuel burns and the melt
 * becomes one refined core in {@link RefiningRecipe#time()} ticks (20 seconds; half that on a blaze rod's or a lava
 * bucket's fire), which slides out onto the tray. Hoppers feed the crucible from above, the firebox and the additive from
 * the sides (each item where it belongs) and empty the tray (and the firebox of empty buckets) from below.
 */
public class ArcaneRefineryBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    public static final int INPUT = 0, FUEL = 1, OUTPUT = 2, ADDITIVE = 3, SLOTS = 4;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.arcane_refinery.idle");
    private static final RawAnimation REFINING = RawAnimation.begin().thenLoop("animation.arcane_refinery.refining");

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case INPUT -> level == null || isRaw(level, stack);
                case FUEL -> burnTime(stack) > 0;
                case ADDITIVE -> level == null || isAdditive(level, stack);
                default -> false;
            };
        }
    };
    private final RecipeManager.CachedCheck<RefiningInput, RefiningRecipe> recipes = RecipeManager.createCheck(ModRecipes.REFINING.get());
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Fire left in the firebox, the burn time of the fuel that lit it, refining progress and how long it takes. */
    private int burn, burnTotal, progress, total = RefiningRecipe.DEFAULT_TIME;
    /** Whether the fire burning now is a blaze rod's or a lava bucket's (refines twice as fast). */
    private boolean blaze;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> burn;
                case 1 -> burnTotal;
                case 2 -> progress;
                case 3 -> total;
                default -> blaze ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> burn = v;
                case 1 -> burnTotal = v;
                case 2 -> progress = v;
                case 3 -> total = v;
                default -> blaze = v != 0;
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public ArcaneRefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_REFINERY.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    static int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    /** Fuels whose fire refines twice as fast: blaze rods and lava. */
    public static boolean fastFuel(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.is(Items.LAVA_BUCKET);
    }

    Optional<RecipeHolder<RefiningRecipe>> recipeFor(ItemStack raw, ItemStack additive) {
        if (raw.isEmpty() || level == null) return Optional.empty();
        return recipes.getRecipeFor(new RefiningInput(raw, additive), level);
    }

    /** Whether some refining recipe takes {@code stack} as its raw material. */
    public static boolean isRaw(Level level, ItemStack stack) {
        for (var r : level.getRecipeManager().getAllRecipesFor(ModRecipes.REFINING.get())) if (r.value().ingredient().test(stack)) return true;
        return false;
    }

    /** Whether some refining recipe melts {@code stack} in as its additive. */
    public static boolean isAdditive(Level level, ItemStack stack) {
        for (var r : level.getRecipeManager().getAllRecipesFor(ModRecipes.REFINING.get())) {
            if (!r.value().additive().isEmpty() && r.value().additive().test(stack)) return true;
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArcaneRefineryBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean wasBurning = burn > 0;
        boolean changed = false;
        if (burn > 0) burn--;
        ItemStack input = items.getStackInSlot(INPUT), additive = items.getStackInSlot(ADDITIVE);
        RefiningRecipe recipe = recipeFor(input, additive).map(RecipeHolder::value).orElse(null);
        ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.assemble(new RefiningInput(input, additive), level.registryAccess());
        boolean canRefine = recipe != null && fits(result);
        // light the fire when there is something to refine and nothing burning
        if (burn <= 0 && canRefine) {
            ItemStack fuel = items.getStackInSlot(FUEL);
            int t = burnTime(fuel);
            if (t > 0) {
                burn = burnTotal = t;
                blaze = fastFuel(fuel);
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty()) items.setStackInSlot(FUEL, remainder);
                changed = true;
            }
        }
        if (burn > 0 && canRefine) {
            total = recipe.time();
            progress += blaze ? 2 : 1;
            if (progress >= total) {
                progress = 0;
                input.shrink(recipe.count());
                if (!recipe.additive().isEmpty()) additive.shrink(recipe.additiveCount());
                ItemStack out = items.getStackInSlot(OUTPUT);
                if (out.isEmpty()) items.setStackInSlot(OUTPUT, result.copy());
                else out.grow(result.getCount());
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.25f, 1.6f);
            }
            changed = true;
        } else if (progress > 0) {
            // the melt cools back down without fire or without anything to refine
            progress = Math.max(0, progress - 2);
            changed = true;
        }
        boolean refining = burn > 0 && canRefine;
        if (state.getValue(ArcaneRefineryBlock.LIT) != refining) {
            level.setBlock(pos, state.setValue(ArcaneRefineryBlock.LIT, refining), 3);
        }
        if (changed || wasBurning != burn > 0) setChanged();
    }

    private boolean fits(ItemStack result) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    public boolean refining() {
        return getBlockState().getValue(ArcaneRefineryBlock.LIT);
    }

    // ------------------------------------------------------------------------------------------------------------
    // hoppers: the crucible from above, the firebox from the sides, the tray from below
    // ------------------------------------------------------------------------------------------------------------

    public IItemHandler handler(@Nullable Direction side) {
        if (side == null) return items;
        return switch (side) {
            case UP -> new SlotAccess(items, INPUT, true, false);
            case DOWN -> new Bottom(items);
            default -> new Sides(items);
        };
    }

    /** The bottom: the refined material from the tray, and the empty buckets lava leaves in the firebox. */
    private record Bottom(ItemStackHandler items) implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        private int slot(int s) {
            return s == 0 ? OUTPUT : FUEL;
        }

        @Override
        public ItemStack getStackInSlot(int s) {
            return items.getStackInSlot(slot(s));
        }

        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int s, int amount, boolean simulate) {
            if (s == 1 && !items.getStackInSlot(FUEL).is(Items.BUCKET)) return ItemStack.EMPTY;
            return items.extractItem(slot(s), amount, simulate);
        }

        @Override
        public int getSlotLimit(int s) {
            return items.getSlotLimit(slot(s));
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return false;
        }
    }

    /** The sides: fuel into the firebox, an additive into its slot, whichever the item is. */
    private record Sides(ItemStackHandler items) implements IItemHandler {
        private static int slot(int s) {
            return s == 0 ? FUEL : ADDITIVE;
        }

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int s) {
            return items.getStackInSlot(slot(s));
        }

        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return items.insertItem(slot(s), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int s, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int s) {
            return items.getSlotLimit(slot(s));
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return items.isItemValid(slot(s), stack);
        }
    }

    /** One slot of the inventory, insert-only or extract-only. */
    private record SlotAccess(ItemStackHandler items, int slot, boolean insert, boolean extract) implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int s) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return insert ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int s, int amount, boolean simulate) {
            return extract ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int s) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return insert && items.isItemValid(slot, stack);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // saving, menu, animation
    // ------------------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("burn", burn);
        tag.putInt("burn_total", burnTotal);
        tag.putInt("progress", progress);
        tag.putInt("total", total);
        tag.putBoolean("blaze", blaze);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // refineries from before the additive slot saved three slots: keep them, with room for the fourth
        CompoundTag saved = tag.getCompound("items").copy();
        if (saved.getInt("Size") < SLOTS) saved.putInt("Size", SLOTS);
        items.deserializeNBT(registries, saved);
        burn = tag.getInt("burn");
        burnTotal = tag.getInt("burn_total");
        progress = tag.getInt("progress");
        total = tag.contains("total") ? tag.getInt("total") : RefiningRecipe.DEFAULT_TIME;
        blaze = tag.getBoolean("blaze");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.fantasyweapons.arcane_refinery");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefineryMenu(id, inventory, this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 10, state -> state.setAndContinue(refining() ? REFINING : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
