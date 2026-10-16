package com.fantasyweapons.forge;

import com.fantasyweapons.registry.ModBlockEntities;
import com.fantasyweapons.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * The Worldforge's block entity: the 11x11 grid (kept, and shared by everyone who opens the forge), the Forged slot and
 * the forging under way, which takes the recipe's time whether or not anyone watches. Breaking the forge drops the grid,
 * the Forged slot and, mid-forging, what the forging took.
 * <p>
 * Its model shows folded until the forge is known to be placed; it unfolds once (eight seconds) when it is, idles after,
 * and plays its crafting animation while it forges: the seven pylons pour their elements into the heart, the heart's beam
 * strikes the anvil where the weapon lies, and the hammer falls.
 */
public class WorldforgeBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation FOLDED = RawAnimation.begin().thenLoop("animation.worldforge.block");
    private static final RawAnimation UNFOLD = RawAnimation.begin().thenPlay("animation.worldforge.place").thenLoop("animation.worldforge.idle");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.worldforge.idle");
    private static final RawAnimation CRAFTING = RawAnimation.begin().thenLoop("animation.worldforge.crafting");
    /** How long the forge takes to unfold, and keeps crafting after a weapon is forged, in ticks. */
    public static final int UNFOLD_TICKS = 160, AFTERGLOW = 100;
    /** Not yet told when it was placed (a client before the server's word): shown folded. */
    private static final long UNKNOWN = Long.MIN_VALUE;
    /** The seven pylons' crowns (model pixels), whose elements pour into the heart while it forges, with their colours. */
    private static final double[][] CROWNS = {{0, 68, 34}, {-26.58, 68, 21.2}, {-33.15, 68, -7.57}, {-14.75, 68, -30.63}, {14.75, 68, -30.63},
            {33.15, 68, -7.57}, {26.58, 68, 21.2}};
    public static final int[] ELEMENT_COLORS = {0xFF6A2A, 0x9FE6FF, 0x6BE873, 0xFFE45A, 0xFFF6D0, 0xB46BFF, 0x6BF0D8};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer grid = new Contents(WorldforgeRecipe.SIZE * WorldforgeRecipe.SIZE);
    private final SimpleContainer output = new Contents(1);
    private final SimpleContainer forging = new Contents(1);
    @Nullable
    private ResourceLocation recipe;
    private int progress, total;
    private final List<ItemStack> refund = new ArrayList<>();
    private long glowUntil;
    @Nullable
    private ResourceLocation chosen;
    /** The game time it was placed at (0 when long ago), or {@link #UNKNOWN} on a client not yet told. */
    private long placedAt = UNKNOWN;

    public WorldforgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORLDFORGE.get(), pos, state);
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

    /** Just placed: it unfolds. */
    public void placed() {
        if (level == null || level.isClientSide) return;
        placedAt = level.getGameTime();
        setChanged();
        sync();
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
            level.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 1f, 0.4f);
        }
    }

    public boolean crafting() {
        return busy() || (level != null && level.getGameTime() < glowUntil);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WorldforgeBlockEntity be) {
        boolean crafting = be.crafting();
        if (state.getValue(WorldforgeBlock.CRAFTING) != crafting) level.setBlock(pos, state.setValue(WorldforgeBlock.CRAFTING, crafting), Block.UPDATE_CLIENTS);
        if (!be.busy()) return;
        if (be.progress < be.total) {
            int before = stage(be.progress, be.total);
            be.progress++;
            if (stage(be.progress, be.total) != before) {
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.4f, 0.5f);
                level.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.6f, 0.5f);
            } else if (level.random.nextInt(60) == 0) {
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9f, 0.4f + level.random.nextFloat() * 0.2f);
            }
            be.setChanged();
            return;
        }
        ItemStack made = be.forging.getItem(0), there = be.output.getItem(0);
        if (there.isEmpty()) be.output.setItem(0, made.copy());
        else return;
        be.forging.setItem(0, ItemStack.EMPTY);
        be.refund.clear();
        be.recipe = null;
        be.progress = be.total = 0;
        be.glowUntil = level.getGameTime() + AFTERGLOW;
        be.setChanged();
        be.sync();
        // the world hears it
        level.playSound(null, pos, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.BLOCKS, 2f, 0.5f);
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos.above(4)));
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
            Component line = Component.literal("☠ The Worldforge has forged ").withStyle(s -> s.withColor(0xFF4A3A))
                    .append(made.getHoverName().copy().withStyle(s -> s.withColor(0xFFE0D0).withBold(true)))
                    .append(Component.literal(". The world trembles.").withStyle(s -> s.withColor(0xFF4A3A)));
            server.getServer().getPlayerList().broadcastSystemMessage(line, false);
        }
    }

    /** Which of the {@link #STAGES} forging stages it is in. */
    public static int stage(int progress, int total) {
        return total <= 0 ? 0 : Math.min(STAGES - 1, progress * STAGES / total);
    }

    public static final int STAGES = 8;

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("PlacedAt", placedAt == UNKNOWN ? 0 : placedAt);
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

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        placedAt = tag.getLong("PlacedAt");
        forging.getItems().set(0, ItemStack.parseOptional(registries, tag.getCompound("Forging")));
        progress = tag.getInt("Progress");
        total = tag.getInt("Total");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    /** Where a spot of the model (pixels about the core's bottom centre, facing north) is in the world, as the forge faces. */
    public Vec3 spot(double mx, double my, double mz) {
        double x = -mx / 16, z = mz / 16;
        double a = switch (getBlockState().getValue(WorldforgeBlock.FACING)) {
            case SOUTH -> Math.PI;
            case WEST -> Math.PI / 2;
            case EAST -> Math.PI * 1.5;
            default -> 0;
        };
        double rx = x * Math.cos(a) + z * Math.sin(a), rz = -x * Math.sin(a) + z * Math.cos(a);
        return new Vec3(worldPosition.getX() + 0.5 + rx, worldPosition.getY() + my / 16, worldPosition.getZ() + 0.5 + rz);
    }

    /** Over the anvil, under the heart's beam, where the weapon being forged lies. */
    public Vec3 forgePoint() {
        return spot(0, 40, 0);
    }

    public Vec3 heart() {
        return spot(0, 80, 0);
    }

    /** On a client: the forging goes on between updates, and the forge shows it. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WorldforgeBlockEntity be) {
        if (be.busy() && be.progress < be.total) be.progress++;
        var random = level.random;
        Vec3 heart = be.heart();
        // always: embers drift up off the tiers
        if (random.nextInt(3) == 0) {
            Vec3 e = be.spot((random.nextDouble() - 0.5) * 100, 12, (random.nextDouble() - 0.5) * 100);
            level.addParticle(ModParticles.FORGE_EMBER.get(), e.x, e.y, e.z, 0, 0.02 + random.nextDouble() * 0.02, 0);
        }
        if (!state.getValue(WorldforgeBlock.CRAFTING)) return;
        Vec3 at = be.forgePoint();
        if (be.busy()) {
            // the seven elements pour from the pylons' crowns into the heart
            for (double[] c : CROWNS) {
                if (random.nextInt(2) != 0) continue;
                Vec3 crown = be.spot(c[0], c[1], c[2]);
                level.addParticle(ModParticles.FORGE_RUNE.get(), heart.x, heart.y, heart.z, crown.x - heart.x, crown.y - heart.y, crown.z - heart.z);
            }
            // and the heart pours down onto the anvil
            if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.END_ROD, at.x + (random.nextDouble() - 0.5) * 0.2, heart.y - 0.3,
                    at.z + (random.nextDouble() - 0.5) * 0.2, 0, -0.35, 0);
            // the hammer's blows
            if (random.nextInt(16) == 0) {
                for (int i = 0; i < 16; i++) {
                    level.addParticle(ModParticles.FORGE_SPARK.get(), at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.4, 0.1 + random.nextDouble() * 0.25,
                            (random.nextDouble() - 0.5) * 0.4);
                }
                level.addParticle(ModParticles.FORGE_FLARE.get(), at.x, at.y, at.z, 0, 0, 0);
            }
            if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x + (random.nextDouble() - 0.5), at.y - 0.2,
                    at.z + (random.nextDouble() - 0.5), 0, 0.03, 0);
        } else if (random.nextInt(2) == 0) {
            level.addParticle(ModParticles.FORGE_FLARE.get(), at.x + (random.nextDouble() - 0.5) * 0.6, at.y + random.nextDouble() * 0.4,
                    at.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
            level.addParticle(ModParticles.FORGE_STAR.get(), at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.3, 0.08 + random.nextDouble() * 0.15,
                    (random.nextDouble() - 0.5) * 0.3);
        }
    }

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
        tag.putLong("PlacedAt", placedAt == UNKNOWN ? 0 : placedAt);
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
        placedAt = tag.getLong("PlacedAt");
        progress = tag.getInt("Progress");
        total = tag.getInt("Total");
        refund.clear();
        for (Tag t : tag.getList("Refund", Tag.TAG_COMPOUND)) refund.add(ItemStack.parseOptional(registries, (CompoundTag) t));
    }

    /** The forge is far bigger than its block. */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(4, 0, 4).expandTowards(0, 7.5, 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            // folding and unfolding start at once (the model's rest pose is the whole forge); idle and crafting blend
            if (placedAt == UNKNOWN || level == null) {
                state.getController().transitionLength(0);
                return state.setAndContinue(FOLDED);
            }
            if (placedAt > 0 && level.getGameTime() - placedAt < UNFOLD_TICKS) {
                state.getController().transitionLength(0);
                return state.setAndContinue(UNFOLD);
            }
            state.getController().transitionLength(10);
            return state.setAndContinue(getBlockState().getValue(WorldforgeBlock.CRAFTING) ? CRAFTING : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private final class Contents extends SimpleContainer {
        Contents(int size) {
            super(size);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            WorldforgeBlockEntity.this.setChanged();
        }
    }
}
