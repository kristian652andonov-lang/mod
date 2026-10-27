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
 * <p>
 * The table also does one greater work: with the Great Sword of Obliteration in the vise and the Spell Book of Absolute
 * Domination in the cup, it fuses the two calamities into the Weapon of God. That takes a minute and many experience
 * levels; the light gathers over the plate the whole time, and the new weapon keeps the higher of the two levels.
 */
public class UpgradingTableBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    public static final int TOOL = 0, CATALYST = 1, SLOTS = 2;
    /** How long an upgrade takes (one loop of the upgrading animation), and when its three pulses ring out. */
    public static final int DURATION = 240;
    public static final int[] PULSES = {88, 128, 168};
    /** The fusion's {@code track} (after the {@link UpgradeTrack}s), how long it takes, its pulses and its cost in levels. */
    public static final int FUSE = UpgradeTrack.values().length, FUSE_DURATION = 1200, FUSE_LEVELS = 60;
    public static final int[] FUSE_PULSES = {200, 400, 600, 800, 1000, 1120};
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.legendary_upgrading_table.idle");
    private static final RawAnimation UPGRADING = RawAnimation.begin().thenLoop("animation.legendary_upgrading_table.upgrading");

    /** Why an upgrade can or can't start. */
    public enum Check {
        READY(""), BUSY("The table is already at work"), NO_TOOL("Lay a legendary tool in the vise"), MAXED("Already at its highest"),
        NO_CATALYST("Set its refined material in the cup"), WRONG_CATALYST("The cup holds the wrong material"),
        FEW_CORES("Not enough cores in the cup"), FEW_LEVELS("Not enough experience"),
        NO_SWORD("Lay the Great Sword of Obliteration in the vise"), NO_BOOK("Set the Spell Book of Absolute Domination in the cup");

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

    /**
     * What each slot takes: the vise a legendary tool (or the Great Sword of Obliteration, to fuse), the cup a refined
     * legendary material (or the Spell Book of Absolute Domination).
     */
    public static boolean accepts(int slot, ItemStack stack) {
        return slot == TOOL ? stack.getItem() instanceof LegendaryTool || isSword(stack)
                : stack.getItem() instanceof MaterialItem m && m.refined() || isBook(stack);
    }

    public static boolean isSword(ItemStack stack) {
        return weapon(stack, com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration.ID);
    }

    public static boolean isGod(ItemStack stack) {
        return weapon(stack, com.fantasyweapons.weapons.god.WeaponOfGod.ID);
    }

    public static boolean isBook(ItemStack stack) {
        return weapon(stack, com.fantasyweapons.weapons.domination.SpellBookOfDomination.ID);
    }

    private static boolean weapon(ItemStack stack, String id) {
        return stack.getItem() instanceof com.fantasyweapons.weapon.FantasyWeaponItem w && w.definition().id().equals(id);
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

    public boolean fusing() {
        return track == FUSE;
    }

    /** How long the work under way ({@code track}) takes. */
    public static int duration(int track) {
        return track == FUSE ? FUSE_DURATION : DURATION;
    }

    /** Whether {@code player} can fuse what lies in the vise ({@code tool}) and the cup into the Weapon of God. */
    public static Check checkFusion(ItemStack tool, ItemStack catalyst, Player player) {
        if (!isSword(tool)) return Check.NO_SWORD;
        if (!isBook(catalyst)) return Check.NO_BOOK;
        if (!player.getAbilities().instabuild && player.experienceLevel < FUSE_LEVELS) return Check.FEW_LEVELS;
        return Check.READY;
    }

    /** Starts fusing the two calamities, paying for it at once, if it can. */
    public Check startFusion(Player player) {
        if (working()) return Check.BUSY;
        Check c = checkFusion(items.getStackInSlot(TOOL), items.getStackInSlot(CATALYST), player);
        if (c != Check.READY) return c;
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-FUSE_LEVELS);
        track = FUSE;
        progress = 0;
        upgrader = player.getUUID();
        setWorking(true);
        if (this.level instanceof ServerLevel sl) {
            Vec3 at = spot(0, 18, -0.8);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 0.5f);
        }
        setChanged();
        sync();
        return Check.READY;
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
        if (fusing()) {
            fuseTick(level, plate);
            return;
        }
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

    /**
     * A tick of the fusion: light spirals up off the plate, the sword's red and the book's violet wound together, faster
     * and brighter as it goes; at each pulse a ring of light and a deep chime; the last moments thunder.
     */
    private void fuseTick(ServerLevel level, Vec3 plate) {
        float k = progress / (float) FUSE_DURATION;
        if (progress % 2 == 0) {
            for (int arm = 0; arm < 2; arm++) {
                double a = progress * (0.12 + 0.25 * k) + arm * Math.PI, r = 0.7 - 0.4 * k, h = (progress % 40) / 40.0 * (1.2 + 2 * k);
                var dust = new DustParticleOptions(arm == 0 ? new Vector3f(1f, 0.16f, 0.2f) : new Vector3f(0.66f, 0.4f, 1f), 1.4f);
                level.sendParticles(dust, plate.x + Math.cos(a) * r, plate.y + 0.2 + h, plate.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (progress % 5 == 0) level.sendParticles(ParticleTypes.END_ROD, plate.x, plate.y + 0.3, plate.z, 1 + (int) (3 * k), 0.15, 0.4 + k, 0.15, 0.02);
        if (progress % 60 == 0) level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.6f + 0.6f * k);
        for (int i = 0; i < FUSE_PULSES.length; i++) {
            if (progress != FUSE_PULSES[i]) continue;
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 0.5f + i * 0.12f);
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 0.6f + i * 0.1f);
            for (int n = 0; n < 36; n++) {
                double a = n / 36.0 * Math.PI * 2;
                level.sendParticles(ParticleTypes.END_ROD, plate.x, plate.y + 0.25, plate.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
            level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.93f, 0.6f), 2f), plate.x, plate.y + 0.3, plate.z, 30, 0.4, 0.4, 0.4, 0);
        }
        if (progress == FUSE_DURATION - 60) level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2f, 0.5f);
        if (progress % 20 == 0) setChanged();
        if (progress >= FUSE_DURATION) {
            apply(true);
            // and the light comes down onto it
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(plate.x, plate.y, plate.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, 0.7f);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, plate.x, plate.y + 0.4, plate.z, 80, 0.4, 0.6, 0.4, 0.4);
            level.sendParticles(ParticleTypes.FLASH, plate.x, plate.y + 0.5, plate.z, 1, 0, 0, 0, 0);
        }
    }

    /** The upgrade under way done at once (the table broken mid-upgrade). */
    public void finishNow() {
        if (working()) apply(false);
    }

    private void finish(ServerLevel level) {
        apply(true);
        Vec3 plate = spot(0, 18, -0.8);
        level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.2f);
        level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.5f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, plate.x, plate.y + 0.3, plate.z, 30, 0.3, 0.3, 0.3, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, plate.x, plate.y + 0.2, plate.z, 16, 0.25, 0.4, 0.25, 0.04);
    }

    /** Raises the tool; {@code standing}: the table stays, and goes back to idle (not while it is being broken). */
    private void apply(boolean standing) {
        ItemStack tool = items.getStackInSlot(TOOL);
        if (track == FUSE) fuse();
        else if (track >= 0 && track < UpgradeTrack.values().length && tool.getItem() instanceof LegendaryTool) {
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
        if (!standing) return;
        setWorking(false);
        setChanged();
        sync();
    }

    /**
     * The two calamities become the Weapon of God, in the vise. It keeps the higher of their levels (with the mastery
     * points those levels give its own powers) and everything the two have slain.
     */
    private void fuse() {
        ItemStack sword = items.getStackInSlot(TOOL), book = items.getStackInSlot(CATALYST);
        if (!isSword(sword) || !isBook(book)) return;
        var a = com.fantasyweapons.weapon.FantasyWeaponItem.data(sword);
        var b = com.fantasyweapons.weapon.FantasyWeaponItem.data(book);
        var def = com.fantasyweapons.weapon.Weapons.get(com.fantasyweapons.weapons.god.WeaponOfGod.ID);
        var made = com.fantasyweapons.progression.ExpService.withLevels(def, com.fantasyweapons.progression.WeaponData.EMPTY,
                Math.max(a.level(), b.level()) - 1);
        made = new com.fantasyweapons.progression.WeaponData(made.weaponId(), made.level(), made.exp(), made.totalExp(), made.upgrades(),
                made.masteryPoints(), 0, "", a.kills() + b.kills(), a.bossKills() + b.bossKills());
        ItemStack god = new ItemStack(ModItems.WEAPONS.get(com.fantasyweapons.weapons.god.WeaponOfGod.ID).get());
        god.set(ModComponents.WEAPON_DATA.get(), made);
        items.setStackInSlot(CATALYST, ItemStack.EMPTY);
        items.setStackInSlot(TOOL, god);
        if (upgrader != null && level != null && level.getPlayerByUUID(upgrader) instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.fantasyweapons.quest.QuestService.event(sp, "fuse_god");
        }
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
