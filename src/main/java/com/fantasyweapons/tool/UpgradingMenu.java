package com.fantasyweapons.tool;

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
import org.jetbrains.annotations.Nullable;

/**
 * The Legendary Upgrading Table's screen: the vise (a legendary tool) and the copper cup (its refined material), the three
 * things it can raise with what each next step costs, and the player's inventory. Choosing one is a menu button (its
 * {@link UpgradeTrack} ordinal); the tool and the cup can't be taken while the table works. With the two calamities on
 * it, the screen offers the fusion instead.
 */
public class UpgradingMenu extends AbstractContainerMenu {
    public static final int WIDTH = 246, HEIGHT = 232;
    public static final int TOOL_X = 30, TOOL_Y = 46, CATALYST_X = 30, CATALYST_Y = 100;
    public static final int ROWS_X = 70, ROWS_Y = 26, ROW_H = 30, ROW_W = WIDTH - ROWS_X - 12;
    public static final int INV_X = (WIDTH - 9 * 18) / 2 + 1, INV_Y = 150;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable
    private final UpgradingTableBlockEntity table;

    /** Client side, from the open-menu packet. */
    public UpgradingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new ItemStackHandler(UpgradingTableBlockEntity.SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return UpgradingTableBlockEntity.accepts(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot == UpgradingTableBlockEntity.TOOL ? 1 : 64;
            }
        }, new SimpleContainerData(2), buf.readBlockPos(), null);
    }

    public UpgradingMenu(int id, Inventory inventory, UpgradingTableBlockEntity be) {
        this(id, inventory, be.items(), be.data(), be.getBlockPos(), be);
    }

    private UpgradingMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos, @Nullable UpgradingTableBlockEntity table) {
        super(ModMenus.UPGRADING.get(), id);
        this.data = data;
        this.table = table;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addSlot(new TableSlot(items, UpgradingTableBlockEntity.TOOL, TOOL_X, TOOL_Y));
        addSlot(new TableSlot(items, UpgradingTableBlockEntity.CATALYST, CATALYST_X, CATALYST_Y));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    /** A slot of the table: locked while it works. */
    private final class TableSlot extends SlotItemHandler {
        TableSlot(IItemHandler items, int index, int x, int y) {
            super(items, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !working() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !working() && super.mayPickup(player);
        }
    }

    public boolean working() {
        return data.get(1) >= 0;
    }

    /** What is being raised, or null. */
    @Nullable
    public UpgradeTrack track() {
        int t = data.get(1);
        return t >= 0 && t < UpgradeTrack.values().length ? UpgradeTrack.values()[t] : null;
    }

    /** Whether the table is fusing the two calamities. */
    public boolean fusing() {
        return data.get(1) == UpgradingTableBlockEntity.FUSE;
    }

    public float progress() {
        return working() ? Math.min(1f, data.get(0) / (float) UpgradingTableBlockEntity.duration(data.get(1))) : 0;
    }

    /**
     * Whether the screen shows the fusion rather than the tool upgrades: a calamity is on the table, the fusion is under
     * way, or the Weapon of God it made lies in the vise.
     */
    public boolean fusion() {
        return fusing() || UpgradingTableBlockEntity.isSword(tool()) || UpgradingTableBlockEntity.isGod(tool())
                || tool().isEmpty() && UpgradingTableBlockEntity.isBook(catalyst());
    }

    public ItemStack tool() {
        return slots.get(UpgradingTableBlockEntity.TOOL).getItem();
    }

    public ItemStack catalyst() {
        return slots.get(UpgradingTableBlockEntity.CATALYST).getItem();
    }

    /** A button: raise the {@link UpgradeTrack} of that ordinal, or ({@link UpgradingTableBlockEntity#FUSE}) fuse. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null) return false;
        if (id == UpgradingTableBlockEntity.FUSE) return table.startFusion(player) == UpgradingTableBlockEntity.Check.READY;
        if (id < 0 || id >= UpgradeTrack.values().length) return false;
        return table.start(player, UpgradeTrack.values()[id]) == UpgradingTableBlockEntity.Check.READY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int machine = UpgradingTableBlockEntity.SLOTS, end = slots.size();
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, end, true)) return ItemStack.EMPTY;
        } else if (slots.get(UpgradingTableBlockEntity.TOOL).mayPlace(stack)) {
            if (!moveItemStackTo(stack, UpgradingTableBlockEntity.TOOL, UpgradingTableBlockEntity.TOOL + 1, false)) return ItemStack.EMPTY;
        } else if (slots.get(UpgradingTableBlockEntity.CATALYST).mayPlace(stack)) {
            if (!moveItemStackTo(stack, UpgradingTableBlockEntity.CATALYST, UpgradingTableBlockEntity.CATALYST + 1, false)) return ItemStack.EMPTY;
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
        return stillValid(access, player, ModBlocks.UPGRADING_TABLE.get());
    }
}
