package com.fantasyweapons.refinery;

import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The refinery's screen: crucible, firebox and tray above the player's inventory. */
public class RefineryMenu extends AbstractContainerMenu {
    /** The panel's size, and where the three machine slots and the player's inventory sit inside it. */
    public static final int WIDTH = 210, HEIGHT = 222;
    public static final int INPUT_X = 44, INPUT_Y = 36, FUEL_X = 44, FUEL_Y = 86, OUTPUT_X = 154, OUTPUT_Y = 61;
    public static final int INV_X = (WIDTH - 9 * 18) / 2 + 1, INV_Y = 140;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client side, from the open-menu packet. */
    public RefineryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(3), new SimpleContainerData(5), buf.readBlockPos(), inventory.player);
    }

    public RefineryMenu(int id, Inventory inventory, ArcaneRefineryBlockEntity be) {
        this(id, inventory, be.items(), be.data(), be.getBlockPos(), inventory.player);
    }

    private RefineryMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos, Player player) {
        super(ModMenus.REFINERY.get(), id);
        this.data = data;
        this.access = ContainerLevelAccess.create(player.level(), pos);
        addSlot(new SlotItemHandler(items, ArcaneRefineryBlockEntity.INPUT, INPUT_X, INPUT_Y));
        addSlot(new SlotItemHandler(items, ArcaneRefineryBlockEntity.FUEL, FUEL_X, FUEL_Y));
        addSlot(new SlotItemHandler(items, ArcaneRefineryBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public float burnFraction() {
        int total = data.get(1);
        return total <= 0 ? 0 : data.get(0) / (float) total;
    }

    public float progress() {
        int total = data.get(3);
        return total <= 0 ? 0 : Math.min(1, data.get(2) / (float) total);
    }

    public boolean burning() {
        return data.get(0) > 0;
    }

    /** Whether the fire is a blaze rod's or a lava bucket's: twice as fast. */
    public boolean fastFire() {
        return data.get(4) != 0;
    }

    /** Seconds left on the item refining now. */
    public float secondsLeft() {
        int total = data.get(3), p = data.get(2);
        return Math.max(0, (total - p) / (fastFire() ? 2f : 1f) / 20f);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int machine = 3, end = slots.size();
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, end, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (slots.get(ArcaneRefineryBlockEntity.INPUT).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (slots.get(ArcaneRefineryBlockEntity.FUEL).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < machine + 27) {
            if (!moveItemStackTo(stack, machine + 27, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, machine, machine + 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ARCANE_REFINERY.get());
    }
}
