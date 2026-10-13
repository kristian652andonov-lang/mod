package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModMenus;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The Legendary Crafting Table: a 9x9 crafting grid. It forges the legendary weapons and armor ({@link ForgeRecipe}) and, like a
 * crafting table, makes anything that can be crafted (a smaller recipe can sit anywhere on the grid).
 */
public class LegendaryCraftingMenu extends AbstractContainerMenu {
    /** The panel's size; where the grid, the result and the player's inventory sit inside it. */
    public static final int WIDTH = 416, HEIGHT = 210;
    public static final int GRID_X = 14, GRID_Y = 28, RESULT_X = 210, RESULT_Y = GRID_Y + 4 * 18;
    public static final int INV_X = 244, INV_Y = 126;
    public static final int RESULT = 0, GRID_START = 1, GRID_END = GRID_START + ForgeRecipe.SIZE * ForgeRecipe.SIZE, INV_START = GRID_END;

    private final CraftingContainer grid = new TransientCraftingContainer(this, ForgeRecipe.SIZE, ForgeRecipe.SIZE);
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;

    public LegendaryCraftingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public LegendaryCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.LEGENDARY_CRAFTING.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new ResultSlot(RESULT_X, RESULT_Y));
        for (int r = 0; r < ForgeRecipe.SIZE; r++) {
            for (int c = 0; c < ForgeRecipe.SIZE; c++) addSlot(new Slot(grid, c + r * ForgeRecipe.SIZE, GRID_X + c * 18, GRID_Y + r * 18));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
    }

    /** What the grid makes: a forge recipe first, else any crafting recipe. */
    static Optional<? extends RecipeHolder<? extends Recipe<CraftingInput>>> recipeFor(Level level, CraftingInput input) {
        Optional<RecipeHolder<ForgeRecipe>> forged = level.getRecipeManager().getRecipeFor(ModRecipes.FORGING.get(), input, level);
        if (forged.isPresent()) return forged;
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, pos) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
            CraftingInput input = grid.asCraftInput();
            ItemStack out = ItemStack.EMPTY;
            var recipe = recipeFor(level, input);
            if (recipe.isPresent() && result.setRecipeUsed(level, sp, recipe.get())) {
                ItemStack made = recipe.get().value().assemble(input, level.registryAccess());
                if (made.isItemEnabled(level.enabledFeatures())) out = made;
            }
            result.setItem(0, out);
            if (level.getBlockEntity(pos) instanceof LegendaryCraftingTableBlockEntity table) table.setCrafting(player.getUUID(), !out.isEmpty());
            setRemoteSlot(RESULT, out);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, out));
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> {
            clearContainer(player, grid);
            if (level.getBlockEntity(pos) instanceof LegendaryCraftingTableBlockEntity table) table.setCrafting(player.getUUID(), false);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.LEGENDARY_CRAFTING_TABLE.get());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
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

    /** The forged item: taking it uses up one of everything on the grid (leaving what recipes leave behind). */
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
            access.execute((level, pos) -> {
                if (!level.isClientSide && level.getBlockEntity(pos) instanceof LegendaryCraftingTableBlockEntity table) table.forged();
            });
            CraftingInput.Positioned positioned = grid.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(taker);
            NonNullList<ItemStack> left = remaining(taker.level(), input);
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);
            for (int r = 0; r < input.height(); r++) {
                for (int c = 0; c < input.width(); c++) {
                    int i = c + positioned.left() + (r + positioned.top()) * grid.getWidth();
                    ItemStack have = grid.getItem(i);
                    ItemStack remainder = left.get(c + r * input.width());
                    if (!have.isEmpty()) {
                        grid.removeItem(i, 1);
                        have = grid.getItem(i);
                    }
                    if (remainder.isEmpty()) continue;
                    if (have.isEmpty()) grid.setItem(i, remainder);
                    else if (ItemStack.isSameItemSameComponents(have, remainder)) {
                        remainder.grow(have.getCount());
                        grid.setItem(i, remainder);
                    } else if (!taker.getInventory().add(remainder)) {
                        taker.drop(remainder, false);
                    }
                }
            }
        }

        private NonNullList<ItemStack> remaining(Level level, CraftingInput input) {
            var recipe = recipeFor(level, input);
            if (recipe.isPresent()) return recipe.get().value().getRemainingItems(input);
            NonNullList<ItemStack> items = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) items.set(i, input.getItem(i));
            return items;
        }
    }
}
