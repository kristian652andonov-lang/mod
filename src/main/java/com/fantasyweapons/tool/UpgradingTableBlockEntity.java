package com.fantasyweapons.tool;

import com.fantasyweapons.material.MaterialItem;
import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Legendary Upgrading Table's workings. A legendary tool lies in the vise and its refined material (a Void Core for a
 * Void Crystal tool...) stands in the copper cup; choosing what to raise - its mining area, Fortune or Efficiency, see
 * {@link UpgradeTrack} - takes that step's cores and experience levels at once, and the table works for twelve seconds:
 * the vise closes, the lens beams onto the tool, the grindstone sparks and three pulses ring out (at 4.4, 6.4 and 8.4
 * seconds), and the tool comes out a level higher. The tool and the cup stay put while it works.
 */
public class UpgradingTableBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    public static final int TOOL = 0, CATALYST = 1, SLOTS = 2;
    /** How long an upgrade takes (one loop of the upgrading animation), and when its three pulses ring out. */
    public static final int DURATION = 240;
    public static final int[] PULSES = {88, 128, 168};
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_upgrading_table.idle");
    private static final RawAnimation UPGRADING = RawAnimation.begin().thenLoop("animation.legendary_upgrading_table.upgrading");

    /** Why an upgrade can or can't start. */
    public enum Check {
        READY(""), BUSY("The table is already at work"), NO_TOOL("Lay a legendary tool in the vise"), MAXED("Already at its highest"),
        NO_CATALYST("Set its refined material in the cup"), WRONG_CATALYST("The cup holds the wrong material"),
        FEW_CORES("Not enough cores in the cup"), FEW_LEVELS("Not enough experience");

        public final String message;

        Check(String message) {
            this.message = message;
        }
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == TOOL ? 1 : 64;
        }
    };

    /** What each slot takes: the vise a legendary tool, the cup a refined legendary material. */
    public static boolean accepts(int slot, ItemStack stack) {
        return slot == TOOL ? stack.getItem() instanceof LegendaryTool : stack.getItem() instanceof MaterialItem m && m.refined();
    }
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** What is being raised ({@link UpgradeTrack} ordinal, -1 when idle) and how far the upgrade has got. */
    private int track = -1, progress;
    /** Who started the upgrade under way (for their quests and deeds). */
    @Nullable
    private java.util.UUID upgrader;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return i == 0 ? progress : track;
        }

        @Override
        public void set(int i, int v) {
            if (i == 0) progress = v;
            else track = v;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public UpgradingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UPGRADING_TABLE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public boolean working() {
        return track >= 0;
    }

    public int progress() {
        return progress;
    }

    /** Whether {@code player} can raise {@code t} on {@code tool} with {@code catalyst} in the cup. */
    public static Check check(ItemStack tool, ItemStack catalyst, UpgradeTrack t, Player player) {
        if (!(tool.getItem() instanceof LegendaryTool lt)) return Check.NO_TOOL;
        ToolUpgrades u = LegendaryTools.upgrades(tool);
        if (u.maxed(t)) return Check.MAXED;
        if (catalyst.isEmpty()) return Check.NO_CATALYST;
        if (!catalyst.is(ModItems.REFINED.get(lt.material()).get())) return Check.WRONG_CATALYST;
        int level = u.level(t);
        if (catalyst.getCount() < t.cores(level)) return Check.FEW_CORES;
        if (!player.getAbilities().instabuild && player.experienceLevel < t.levels(level)) return Check.FEW_LEVELS;
        return Check.READY;
    }

    /** Starts raising {@code t}, paying for it at once, if it can. */
    public Check start(Player player, UpgradeTrack t) {
        if (working()) return Check.BUSY;
        ItemStack tool = items.getStackInSlot(TOOL), catalyst = items.getStackInSlot(CATALYST);
        Check c = check(tool, catalyst, t, player);
        if (c != Check.READY) return c;
        int level = LegendaryTools.upgrades(tool).level(t);
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-t.levels(level));
        catalyst.shrink(t.cores(level));
        items.setStackInSlot(CATALYST, catalyst.isEmpty() ? ItemStack.EMPTY : catalyst);
        track = t.ordinal();
        progress = 0;
        upgrader = player.getUUID();
        setWorking(true);
        if (this.level instanceof ServerLevel sl) {
            Vec3 at = spot(0, 18, -0.8);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 0.8f);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 0.9f);
        }
        setChanged();
        sync();
        return Check.READY;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, UpgradingTableBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (!working()) {
            if (getBlockState().getValue(UpgradingTableBlock.WORKING)) setWorking(false);
            return;
        }
        progress++;
        Vec3 plate = spot(0, 18, -0.8);
        for (int i = 0; i < PULSES.length; i++) {
            if (progress != PULSES[i]) continue;
            // a pulse rings out and lights one of the lamps on the front
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f + i * 0.15f);
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.8f + i * 0.2f);
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 0.6f + i * 0.2f);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 1f, 0.35f), 1.2f), plate.x, plate.y + 0.1, plate.z, 24, 0.35, 0.05, 0.25, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, plate.x, plate.y + 0.15, plate.z, 12, 0.3, 0.1, 0.2, 0.05);
        }
        if (progress % 20 == 0) setChanged();
        if (progress >= DURATION) finish(level);
    }

    /** The upgrade under way done at once (the table broken mid-upgrade). */
    public void finishNow() {
        if (working()) apply();
    }

    private void finish(ServerLevel level) {
        apply();
        Vec3 plate = spot(0, 18, -0.8);
        level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.2f);
        level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.5f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, plate.x, plate.y + 0.3, plate.z, 30, 0.3, 0.3, 0.3, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, plate.x, plate.y + 0.2, plate.z, 16, 0.25, 0.4, 0.25, 0.04);
    }

    private void apply() {
        ItemStack tool = items.getStackInSlot(TOOL);
        if (track >= 0 && track < UpgradeTrack.values().length && tool.getItem() instanceof LegendaryTool) {
            UpgradeTrack t = UpgradeTrack.values()[track];
            ToolUpgrades u = LegendaryTools.upgrades(tool), now = u.with(t, u.level(t) + 1);
            ItemStack upgraded = tool.copy();
            upgraded.set(ModComponents.TOOL_UPGRADES.get(), now);
            items.setStackInSlot(TOOL, upgraded);
            if (upgrader != null && level != null && level.getPlayerByUUID(upgrader) instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.fantasyweapons.quest.QuestService.event(sp, "tool_upgrade");
                com.fantasyweapons.advancement.ModTriggers.TOOL.get().trigger(sp, "upgrade");
                if (now.maxed()) {
                    com.fantasyweapons.quest.QuestService.event(sp, "tool_maxed");
                    com.fantasyweapons.advancement.ModTriggers.TOOL.get().trigger(sp, "maxed");
                }
            }
        }
        upgrader = null;
        track = -1;
        progress = 0;
        setWorking(false);
        setChanged();
        sync();
    }

    private void setWorking(boolean on) {
        if (level != null && !level.isClientSide && getBlockState().getValue(UpgradingTableBlock.WORKING) != on) {
            level.setBlock(worldPosition, getBlockState().setValue(UpgradingTableBlock.WORKING, on), Block.UPDATE_ALL);
        }
    }

    /** Where a spot of the model (pixels about the core's bottom centre, facing north) is in the world, as the table faces. */
    public Vec3 spot(double mx, double my, double mz) {
        double x = -mx / 16, z = mz / 16;
        double a = switch (getBlockState().getValue(UpgradingTableBlock.FACING)) {
            case SOUTH -> Math.PI;
            case WEST -> Math.PI / 2;
            case EAST -> Math.PI * 1.5;
            default -> 0;
        };
        double rx = x * Math.cos(a) + z * Math.sin(a), rz = -x * Math.sin(a) + z * Math.cos(a);
        return new Vec3(worldPosition.getX() + 0.5 + rx, worldPosition.getY() + my / 16, worldPosition.getZ() + 0.5 + rz);
    }

    // ------------------------------------------------------------------------------------------------------------
    // saving, the clients' copy, menu, animation
    // ------------------------------------------------------------------------------------------------------------

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("track", track);
        tag.putInt("progress", progress);
        if (upgrader != null) tag.putUUID("upgrader", upgrader);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        track = tag.contains("track") ? tag.getInt("track") : -1;
        progress = tag.getInt("progress");
        upgrader = tag.hasUUID("upgrader") ? tag.getUUID("upgrader") : null;
    }

    /** Clients get what lies on the table (drawn on it) and how far the upgrade has got. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.fantasyweapons.legendary_upgrading_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new UpgradingMenu(id, inventory, this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(getBlockState().getValue(UpgradingTableBlock.WORKING) ? UPGRADING : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
