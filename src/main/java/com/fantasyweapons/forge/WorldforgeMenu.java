package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModMenus;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Worldforge's menu: its 11x11 grid (kept by the forge), what the grid would forge, the weapon being forged and the
 * Forged slot. Nothing here is simply taken: a recipe on the grid is forged with the Forge button (the grid's items are
 * consumed and the forge works for the recipe's time), and the Fill button lays a chosen recipe out from the inventory.
 */
public class WorldforgeMenu extends AbstractContainerMenu {
    public static final int SIZE = WorldforgeRecipe.SIZE;
    /** The panel's size; where the grid, the result, the forging, the Forged slot and the player's inventory sit inside it. */
    public static final int WIDTH = 436, HEIGHT = 268;
    public static final int GRID_X = 16, GRID_Y = 44, RIGHT_X = 236;
    public static final int RESULT_X = 270, RESULT_Y = 104, FORGING_X = 326, FORGING_Y = 104, OUTPUT_X = 382, OUTPUT_Y = 104;
    public static final int INV_X = 250, INV_Y = 184;
    public static final int RESULT = 0, OUTPUT = 1, FORGING = 2, GRID_START = 3, GRID_END = GRID_START + SIZE * SIZE, INV_START = GRID_END;
    /** Menu buttons: forge; lay out the recipe at {@code FILL + i} of {@link WorldforgeRecipe#ordered}; choose it at {@code CHOOSE + i} ({@code CHOOSE - 1}: none). */
    public static final int FORGE_BUTTON = 0, FILL = 1000, CHOOSE = 2000;
    private static final int PROGRESS = 0, TOTAL = 1, RECIPE = 2, VALID = 3, CHOSEN = 4;

    private final Container grid, output, forging;
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData data;
    @Nullable
    private final WorldforgeBlockEntity forge;
    private final ContainerListener listener = this::slotsChanged;
    private boolean valid;

    public WorldforgeMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL, null);
    }

    public WorldforgeMenu(int id, Inventory inventory, ContainerLevelAccess access, @Nullable WorldforgeBlockEntity forge) {
        super(ModMenus.WORLDFORGE.get(), id);
        this.access = access;
        this.player = inventory.player;
        this.forge = forge;
        this.grid = forge != null ? forge.grid() : new SimpleContainer(SIZE * SIZE);
        this.output = forge != null ? forge.output() : new SimpleContainer(1);
        this.forging = forge != null ? forge.forging() : new SimpleContainer(1);
        this.data = forge != null ? new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROGRESS -> forge.progress();
                    case TOTAL -> forge.total();
                    case RECIPE -> recipeIndex(forge.recipe());
                    case VALID -> valid ? 1 : 0;
                    case CHOSEN -> recipeIndex(forge.chosen());
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 5;
            }
        } : new SimpleContainerData(5);
        addSlot(new Locked(result, 0, RESULT_X, RESULT_Y));
        addSlot(new Slot(output, 0, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Locked(forging, 0, FORGING_X, FORGING_Y));
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) addSlot(new Slot(grid, c + r * SIZE, GRID_X + c * 18, GRID_Y + r * 18));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
        if (grid instanceof SimpleContainer simple && forge != null) {
            simple.addListener(listener);
            slotsChanged(grid);
        }
    }

    private static Optional<RecipeHolder<WorldforgeRecipe>> recipeFor(Level level, CraftingInput input) {
        return level.getRecipeManager().getRecipeFor(ModRecipes.WORLDFORGING.get(), input, level);
    }

    private List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>(grid.getContainerSize());
        for (int i = 0; i < grid.getContainerSize(); i++) items.add(grid.getItem(i));
        return items;
    }

    private CraftingInput.Positioned positioned() {
        return CraftingInput.ofPositioned(SIZE, SIZE, items());
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != grid) return;
        access.execute((level, pos) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
            var recipe = recipeFor(level, CraftingInput.of(SIZE, SIZE, items()));
            valid = recipe.isPresent();
            ItemStack out = recipe.map(r -> r.value().assemble(CraftingInput.of(SIZE, SIZE, items()), level.registryAccess())).orElse(ItemStack.EMPTY);
            result.setItem(0, out);
            setRemoteSlot(RESULT, out);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, out));
        });
    }

    public int progress() {
        return data.get(PROGRESS);
    }

    public int total() {
        return data.get(TOTAL);
    }

    public int forgingRecipe() {
        return data.get(RECIPE);
    }

    public int chosenRecipe() {
        return data.get(CHOSEN);
    }

    /** Whether the grid holds a whole recipe, ready to forge. */
    public boolean ready() {
        return data.get(VALID) == 1;
    }

    public boolean busy() {
        return !forging.getItem(0).isEmpty();
    }

    public Container grid() {
        return grid;
    }

    private int recipeIndex(@Nullable net.minecraft.resources.ResourceLocation id) {
        if (id == null || forge == null || forge.getLevel() == null) return -1;
        var all = WorldforgeRecipe.ordered(forge.getLevel());
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(id)) return i;
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (forge == null || forge.getLevel() == null) return false;
        Level level = forge.getLevel();
        if (id == FORGE_BUTTON) return forge(level, player);
        var all = WorldforgeRecipe.ordered(level);
        if (id >= FILL && id < FILL + all.size()) return fill(player, all.get(id - FILL).value());
        if (id == CHOOSE - 1) {
            forge.choose(null);
            return true;
        }
        if (id >= CHOOSE && id < CHOOSE + all.size()) {
            forge.choose(all.get(id - CHOOSE).id());
            return true;
        }
        return false;
    }

    private boolean forge(Level level, Player player) {
        if (forge == null || forge.busy() || !forge.output().getItem(0).isEmpty()) return false;
        CraftingInput.Positioned positioned = positioned();
        var recipe = recipeFor(level, positioned.input());
        if (recipe.isEmpty()) return false;
        ItemStack made = recipe.get().value().assemble(positioned.input(), level.registryAccess());
        List<ItemStack> took = takeFromGrid(level, positioned);
        forge.start(recipe.get().id(), made, recipe.get().value().time(), took);
        if (player instanceof ServerPlayer server) {
            com.fantasyweapons.advancement.ModTriggers.FORGE_STARTED.get().trigger(server,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(made.getItem()).getPath());
            com.fantasyweapons.quest.QuestService.event(server, "worldforge");
        }
        slotsChanged(grid);
        return true;
    }

    private List<ItemStack> takeFromGrid(Level level, CraftingInput.Positioned positioned) {
        CraftingInput input = positioned.input();
        var recipe = recipeFor(level, input);
        NonNullList<ItemStack> left = recipe.isPresent() ? recipe.get().value().getRemainingItems(input) : NonNullList.withSize(input.size(), ItemStack.EMPTY);
        List<ItemStack> took = new ArrayList<>();
        for (int r = 0; r < input.height(); r++) {
            for (int c = 0; c < input.width(); c++) {
                int i = c + positioned.left() + (r + positioned.top()) * SIZE;
                ItemStack have = grid.getItem(i);
                if (!have.isEmpty()) {
                    took.add(have.copyWithCount(1));
                    grid.removeItem(i, 1);
                }
                ItemStack remainder = left.get(c + r * input.width());
                if (remainder.isEmpty()) continue;
                if (grid.getItem(i).isEmpty()) grid.setItem(i, remainder);
                else if (!player.getInventory().add(remainder)) player.drop(remainder, false);
            }
        }
        return took;
    }

    /** Where each of a recipe's ingredients goes on the grid when it is laid out (centred, as its ghost is shown). */
    public static Ingredient[] layout(WorldforgeRecipe recipe) {
        Ingredient[] at = new Ingredient[SIZE * SIZE];
        var pattern = recipe.pattern();
        int left = (SIZE - pattern.width()) / 2, top = (SIZE - pattern.height()) / 2;
        for (int r = 0; r < pattern.height(); r++) {
            for (int c = 0; c < pattern.width(); c++) {
                Ingredient ing = pattern.ingredients().get(c + r * pattern.width());
                if (!ing.isEmpty()) at[c + left + (r + top) * SIZE] = ing;
            }
        }
        return at;
    }

    /** How many cells of {@code recipe} still wanting their ingredient the player could fill from their inventory and the grid. */
    public static int fillable(Player player, Container grid, WorldforgeRecipe recipe) {
        Ingredient[] at = layout(recipe);
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (!player.getInventory().getItem(i).isEmpty()) pool.add(player.getInventory().getItem(i).copy());
        for (int i = 0; i < at.length; i++) {
            ItemStack have = grid.getItem(i);
            if (!have.isEmpty() && (at[i] == null || !at[i].test(have))) pool.add(have.copy());
        }
        int filled = 0;
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null || at[i].test(grid.getItem(i))) continue;
            for (ItemStack s : pool) {
                if (s.getCount() > 0 && at[i].test(s)) {
                    s.shrink(1);
                    filled++;
                    break;
                }
            }
        }
        return filled;
    }

    /** How many of {@code recipe}'s cells already hold their ingredient. */
    public static int laid(Container grid, WorldforgeRecipe recipe) {
        Ingredient[] at = layout(recipe);
        int n = 0;
        for (int i = 0; i < at.length; i++) if (at[i] != null && at[i].test(grid.getItem(i))) n++;
        return n;
    }

    private boolean fill(Player player, WorldforgeRecipe recipe) {
        if (fillable(player, grid, recipe) <= 0) return false;
        Ingredient[] at = layout(recipe);
        for (int i = 0; i < at.length; i++) {
            ItemStack have = grid.getItem(i);
            if (have.isEmpty() || (at[i] != null && at[i].test(have) && have.getCount() == 1)) continue;
            ItemStack back = at[i] != null && at[i].test(have) ? grid.removeItem(i, have.getCount() - 1) : grid.removeItem(i, have.getCount());
            if (!player.getInventory().add(back)) player.drop(back, false);
        }
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null || !grid.getItem(i).isEmpty()) continue;
            Inventory inv = player.getInventory();
            for (int s = 0; s < 36; s++) {
                if (at[i].test(inv.getItem(s))) {
                    grid.setItem(i, inv.removeItem(s, 1));
                    break;
                }
            }
        }
        slotsChanged(grid);
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (grid instanceof SimpleContainer simple && forge != null) simple.removeListener(listener);
    }

    @Override
    public boolean stillValid(Player player) {
        // the forge is 21 blocks across: it can be used from anywhere on it or just beside it
        double reach = WorldforgeBlock.REACH + 8;
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(ModBlocks.WORLDFORGE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 6, pos.getZ() + 0.5) <= reach * reach, true);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && slot.container != forging && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int end = slots.size(), hotbar = end - 9;
        if (index < GRID_END) {
            if (!moveItemStackTo(stack, INV_START, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
            if (index < hotbar ? !moveItemStackTo(stack, hotbar, end, false) : !moveItemStackTo(stack, INV_START, hotbar, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    /** A slot shown but never put into nor taken from (what the grid would make; the weapon being forged). */
    private static final class Locked extends Slot {
        Locked(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
