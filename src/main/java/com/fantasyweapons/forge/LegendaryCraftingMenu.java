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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Legendary Crafting Table's menu: its 9x9 grid (kept by the table), what the grid makes, the item being forged and
 * the Forged slot. Ordinary crafting recipes are taken from the result at once, as at a crafting table; a legendary
 * recipe is forged instead - the Forge button takes the grid's items and the table works on it for the recipe's time -
 * and the Fill button lays a chosen legendary recipe out on the grid from the player's inventory.
 */
public class LegendaryCraftingMenu extends AbstractContainerMenu {
    /** The panel's size; where the grid, the result, the forging, the Forged slot and the player's inventory sit inside it. */
    public static final int WIDTH = 416, HEIGHT = 232;
    public static final int GRID_X = 14, GRID_Y = 28, RESULT_X = 210, RESULT_Y = GRID_Y + 4 * 18;
    public static final int FORGING_X = GRID_X + 2, FORGING_Y = GRID_Y + 9 * 18 + 12, OUTPUT_X = 210, OUTPUT_Y = 168;
    public static final int INV_X = 244, INV_Y = 144;
    public static final int RESULT = 0, OUTPUT = 1, FORGING = 2, GRID_START = 3, GRID_END = GRID_START + ForgeRecipe.SIZE * ForgeRecipe.SIZE,
            INV_START = GRID_END;
    /**
     * Menu buttons: start forging; lay out the legendary recipe at {@code FILL + i} in {@link ForgeRecipe#ordered}; choose it
     * at {@code CHOOSE + i} (the table remembers it), or {@code CHOOSE - 1} for none.
     */
    public static final int FORGE_BUTTON = 0, FILL = 1000, CHOOSE = 2000;
    /** Synced numbers: the forging's progress and length, its recipe (by place in {@link ForgeRecipe#ordered}), and whether the result is forged. */
    private static final int PROGRESS = 0, TOTAL = 1, RECIPE = 2, LEGENDARY = 3, CHOSEN = 4;

    private final Container grid, output, forging;
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData data;
    @Nullable
    private final LegendaryCraftingTableBlockEntity table;
    private final ContainerListener listener = this::slotsChanged;

    /** The client's menu: its contents come from the server. */
    public LegendaryCraftingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL, null);
    }

    public LegendaryCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access, @Nullable LegendaryCraftingTableBlockEntity table) {
        super(ModMenus.LEGENDARY_CRAFTING.get(), id);
        this.access = access;
        this.player = inventory.player;
        this.table = table;
        this.grid = table != null ? table.grid() : new SimpleContainer(ForgeRecipe.SIZE * ForgeRecipe.SIZE);
        this.output = table != null ? table.output() : new SimpleContainer(1);
        this.forging = table != null ? table.forging() : new SimpleContainer(1);
        this.data = table != null ? new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROGRESS -> table.progress();
                    case TOTAL -> table.total();
                    case RECIPE -> recipeIndex(table.recipe());
                    case LEGENDARY -> legendary ? 1 : 0;
                    case CHOSEN -> recipeIndex(table.chosen());
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
        addSlot(new ResultSlot(RESULT_X, RESULT_Y));
        addSlot(new Slot(output, 0, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(forging, 0, FORGING_X, FORGING_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        for (int r = 0; r < ForgeRecipe.SIZE; r++) {
            for (int c = 0; c < ForgeRecipe.SIZE; c++) addSlot(new Slot(grid, c + r * ForgeRecipe.SIZE, GRID_X + c * 18, GRID_Y + r * 18));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
        if (grid instanceof SimpleContainer simple && table != null) {
            simple.addListener(listener);
            // what lies on the grid already makes something
            slotsChanged(grid);
        }
    }

    /** Whether what the grid makes is a legendary recipe (forged at the table rather than taken). */
    private boolean legendary;

    /** What the grid makes: a forge recipe first, else any crafting recipe. */
    static Optional<? extends RecipeHolder<? extends Recipe<CraftingInput>>> recipeFor(Level level, CraftingInput input) {
        Optional<RecipeHolder<ForgeRecipe>> forged = level.getRecipeManager().getRecipeFor(ModRecipes.FORGING.get(), input, level);
        if (forged.isPresent()) return forged;
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    private CraftingInput input() {
        return CraftingInput.of(ForgeRecipe.SIZE, ForgeRecipe.SIZE, items());
    }

    private CraftingInput.Positioned positioned() {
        return CraftingInput.ofPositioned(ForgeRecipe.SIZE, ForgeRecipe.SIZE, items());
    }

    private List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>(grid.getContainerSize());
        for (int i = 0; i < grid.getContainerSize(); i++) items.add(grid.getItem(i));
        return items;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != grid) return;
        access.execute((level, pos) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
            CraftingInput input = input();
            ItemStack out = ItemStack.EMPTY;
            var recipe = recipeFor(level, input);
            legendary = recipe.isPresent() && recipe.get().value() instanceof ForgeRecipe;
            if (recipe.isPresent() && (legendary || result.setRecipeUsed(level, sp, recipe.get()))) {
                ItemStack made = recipe.get().value().assemble(input, level.registryAccess());
                if (made.isItemEnabled(level.enabledFeatures())) out = made;
            }
            result.setItem(0, out);
            setRemoteSlot(RESULT, out);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, out));
        });
    }

    // ---- forging ----

    public int progress() {
        return data.get(PROGRESS);
    }

    public int total() {
        return data.get(TOTAL);
    }

    /** The recipe being forged (by its place in {@link ForgeRecipe#ordered}), or -1. */
    public int forgingRecipe() {
        return data.get(RECIPE);
    }

    /** The recipe chosen at the table (by its place in {@link ForgeRecipe#ordered}), or -1: it stays chosen for everyone. */
    public int chosenRecipe() {
        return data.get(CHOSEN);
    }

    /** Whether the result is a legendary recipe, to be forged with the Forge button. */
    public boolean resultIsLegendary() {
        return data.get(LEGENDARY) == 1;
    }

    public boolean busy() {
        return !forging.getItem(0).isEmpty();
    }

    /** The grid (on the client, the server's copy of it). */
    public Container grid() {
        return grid;
    }

    private int recipeIndex(@Nullable net.minecraft.resources.ResourceLocation id) {
        if (id == null || table == null || table.getLevel() == null) return -1;
        var all = ForgeRecipe.ordered(table.getLevel());
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(id)) return i;
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null || table.getLevel() == null) return false;
        Level level = table.getLevel();
        if (id == FORGE_BUTTON) return forge(level, player);
        var all = ForgeRecipe.ordered(level);
        if (id >= FILL && id < FILL + all.size()) return fill(player, all.get(id - FILL).value());
        if (id == CHOOSE - 1) {
            table.choose(null);
            return true;
        }
        if (id >= CHOOSE && id < CHOOSE + all.size()) {
            table.choose(all.get(id - CHOOSE).id());
            return true;
        }
        return false;
    }

    /** Starts forging what the grid holds, if it is a legendary recipe and the table is free. */
    private boolean forge(Level level, Player player) {
        if (table == null || table.busy()) return false;
        CraftingInput.Positioned positioned = positioned();
        var recipe = recipeFor(level, positioned.input());
        if (recipe.isEmpty() || !(recipe.get().value() instanceof ForgeRecipe forgeRecipe)) return false;
        ItemStack made = forgeRecipe.assemble(positioned.input(), level.registryAccess());
        List<ItemStack> took = takeFromGrid(level, positioned);
        table.start(recipe.get().id(), made, forgeRecipe.time(), took);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.fantasyweapons.advancement.ModTriggers.FORGE_STARTED.get().trigger(server,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(made.getItem()).getPath());
            com.fantasyweapons.quest.QuestService.event(server, "forge");
        }
        slotsChanged(grid);
        return true;
    }

    /** Takes one of everything a recipe used off the grid (leaving what it leaves behind); returns what was taken. */
    private List<ItemStack> takeFromGrid(Level level, CraftingInput.Positioned positioned) {
        CraftingInput input = positioned.input();
        NonNullList<ItemStack> left = remaining(level, input);
        List<ItemStack> took = new ArrayList<>();
        for (int r = 0; r < input.height(); r++) {
            for (int c = 0; c < input.width(); c++) {
                int i = c + positioned.left() + (r + positioned.top()) * ForgeRecipe.SIZE;
                ItemStack have = grid.getItem(i);
                ItemStack remainder = left.get(c + r * input.width());
                if (!have.isEmpty()) {
                    took.add(have.copyWithCount(1));
                    grid.removeItem(i, 1);
                    have = grid.getItem(i);
                }
                if (remainder.isEmpty()) continue;
                if (have.isEmpty()) grid.setItem(i, remainder);
                else if (ItemStack.isSameItemSameComponents(have, remainder)) {
                    remainder.grow(have.getCount());
                    grid.setItem(i, remainder);
                } else if (!player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }
        return took;
    }

    private NonNullList<ItemStack> remaining(Level level, CraftingInput input) {
        var recipe = recipeFor(level, input);
        if (recipe.isPresent()) return recipe.get().value().getRemainingItems(input);
        NonNullList<ItemStack> items = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) items.set(i, input.getItem(i));
        return items;
    }

    // ---- laying a recipe out ----

    /** Where each of a recipe's ingredients goes on the grid when it is laid out (centred, as its ghost is shown). */
    public static Ingredient[] layout(ForgeRecipe recipe) {
        Ingredient[] at = new Ingredient[ForgeRecipe.SIZE * ForgeRecipe.SIZE];
        var pattern = recipe.pattern();
        int left = (ForgeRecipe.SIZE - pattern.width()) / 2, top = (ForgeRecipe.SIZE - pattern.height()) / 2;
        for (int r = 0; r < pattern.height(); r++) {
            for (int c = 0; c < pattern.width(); c++) {
                Ingredient ing = pattern.ingredients().get(c + r * pattern.width());
                if (!ing.isEmpty()) at[c + left + (r + top) * ForgeRecipe.SIZE] = ing;
            }
        }
        return at;
    }

    /**
     * Whether {@code player} has everything to lay {@code recipe} out on {@code grid}: what already lies in its place
     * counts, and what lies out of place would come back to them first.
     */
    public static boolean canFill(Player player, Container grid, ForgeRecipe recipe) {
        Ingredient[] at = layout(recipe);
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (!player.getInventory().getItem(i).isEmpty()) pool.add(player.getInventory().getItem(i).copy());
        for (int i = 0; i < at.length; i++) {
            ItemStack have = grid.getItem(i);
            if (!have.isEmpty() && (at[i] == null || !at[i].test(have))) pool.add(have.copy());
        }
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null || at[i].test(grid.getItem(i))) continue;
            ItemStack from = null;
            for (ItemStack s : pool) {
                if (s.getCount() > 0 && at[i].test(s)) {
                    from = s;
                    break;
                }
            }
            if (from == null) return false;
            from.shrink(1);
        }
        return true;
    }

    /** Lays {@code recipe} out on the grid from the player's inventory, if they have everything (see {@link #canFill}). */
    private boolean fill(Player player, ForgeRecipe recipe) {
        if (!canFill(player, grid, recipe)) return false;
        Ingredient[] at = layout(recipe);
        // whatever is out of place goes back to the player
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

    // ---- the menu ----

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (grid instanceof SimpleContainer simple && table != null) simple.removeListener(listener);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.LEGENDARY_CRAFTING_TABLE.get());
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
        if (index == RESULT) {
            access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, player));
            if (!moveItemStackTo(stack, INV_START, end, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (index < GRID_END) {
            if (!moveItemStackTo(stack, INV_START, end, false)) return ItemStack.EMPTY;
        } else if (index < hotbar) {
            if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, INV_START, hotbar, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT) player.drop(stack, false);
        return copy;
    }

    /** What the grid makes: taken at once when it is an ordinary recipe; a legendary one is forged with the Forge button. */
    private final class ResultSlot extends Slot {
        private int removeCount;

        ResultSlot(int x, int y) {
            super(result, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !resultIsLegendary();
        }

        @Override
        public ItemStack remove(int amount) {
            if (hasItem()) removeCount += Math.min(amount, getItem().getCount());
            return super.remove(amount);
        }

        @Override
        protected void onQuickCraft(ItemStack stack, int amount) {
            removeCount += amount;
            checkTakeAchievements(stack);
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            if (removeCount > 0) {
                stack.onCraftedBy(player.level(), player, removeCount);
                net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(player, stack, grid);
            }
            removeCount = 0;
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            checkTakeAchievements(stack);
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(taker);
            takeFromGrid(taker.level(), positioned());
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);
        }
    }
}
