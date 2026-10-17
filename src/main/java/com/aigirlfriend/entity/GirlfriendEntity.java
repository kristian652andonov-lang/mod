package com.aigirlfriend.entity;

import com.aigirlfriend.companion.Activity;
import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.CompanionController;
import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.Emotion;
import com.aigirlfriend.companion.Expression;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.ai.AffectionInitiativeGoal;
import com.aigirlfriend.entity.ai.CompanionLookGoal;
import com.aigirlfriend.entity.ai.FaintedGoal;
import com.aigirlfriend.entity.ai.FearResponseGoal;
import com.aigirlfriend.entity.ai.FollowOwnerCompanionGoal;
import com.aigirlfriend.entity.ai.HoldHandsGoal;
import com.aigirlfriend.entity.ai.IdleGestureGoal;
import com.aigirlfriend.entity.ai.InteractionLockGoal;
import com.aigirlfriend.entity.ai.LeadToBedGoal;
import com.aigirlfriend.entity.ai.ReturnHomeGoal;
import com.aigirlfriend.entity.ai.SeekBedGoal;
import com.aigirlfriend.entity.ai.SitWithOwnerGoal;
import com.aigirlfriend.entity.ai.StayPutGoal;
import com.aigirlfriend.interaction.Gesture;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.registry.ModEntities;
import com.aigirlfriend.util.WorldClock;
import com.aigirlfriend.world.CompanionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The companion. Gameplay state lives in {@link CompanionData} (persisted) and is driven on the server by
 * {@link CompanionController}. Only the small amount of state clients need for rendering is synced through
 * entity data; detailed status goes to the owner through status payloads.
 */
public class GirlfriendEntity extends PathfinderMob implements GeoEntity {
    // ---- synced state ----
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_OUTFIT = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPRESSION = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVITY = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COMMAND = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FEAR = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.FLOAT);
    /** Interaction ordinal + 1 (0 = none) in the low byte, initiator flag in bit 8. */
    private static final EntityDataAccessor<Integer> DATA_INTERACTION = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PARTNER = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GESTURE = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GESTURE_SEQ = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.INT);

    public static final int FLAG_TIRED = 1;
    public static final int FLAG_EXHAUSTED = 1 << 1;
    public static final int FLAG_HUNGRY = 1 << 2;
    public static final int FLAG_SCARED = 1 << 3;
    public static final int FLAG_CRITICAL = 1 << 4;
    public static final int FLAG_FAINTED = 1 << 5;
    public static final int FLAG_HIDING = 1 << 6;
    public static final int FLAG_GROUND_SIT = 1 << 7;
    public static final int FLAG_RUNNING = 1 << 8;
    public static final int FLAG_LEADING = 1 << 9;
    public static final int FLAG_SAD = 1 << 10;
    public static final int FLAG_SHY = 1 << 11;
    /** Sitting up in bed before getting out (client plays the in-bed wake-up clip). */
    public static final int FLAG_WAKING = 1 << 12;

    private final CompanionData data = new CompanionData();
    private final InteractionManager interactions = new InteractionManager(this);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private CompanionController controller;
    private CommandMode command = CommandMode.FOLLOW;
    @Nullable private BlockPos waitPos;
    private int gestureTicks;
    private boolean dataInitialized;
    private int outfitBeforeBed = -1;

    public GirlfriendEntity(EntityType<? extends GirlfriendEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
        this.setPathfindingMalus(PathType.LAVA, -1f);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1f);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16f);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1f);
        this.setPathfindingMalus(PathType.DANGER_OTHER, 12f);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1f);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 8f);
        this.setPathfindingMalus(PathType.WATER, 10f);
        this.setPathfindingMalus(PathType.WATER_BORDER, 4f);
        this.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, 8f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 3.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_NAME, "");
        builder.define(DATA_OUTFIT, 0);
        builder.define(DATA_MOOD, Mood.RELAXED.ordinal());
        builder.define(DATA_EXPRESSION, Expression.NEUTRAL.ordinal());
        builder.define(DATA_STAGE, 0);
        builder.define(DATA_ACTIVITY, Activity.IDLE.ordinal());
        builder.define(DATA_COMMAND, CommandMode.FOLLOW.ordinal());
        builder.define(DATA_FLAGS, 0);
        builder.define(DATA_FEAR, 0f);
        builder.define(DATA_INTERACTION, 0);
        builder.define(DATA_PARTNER, -1);
        builder.define(DATA_GESTURE, 0);
        builder.define(DATA_GESTURE_SEQ, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new FaintedGoal(this));
        this.goalSelector.addGoal(0, new InteractionLockGoal(this));
        this.goalSelector.addGoal(1, new FearResponseGoal(this));
        this.goalSelector.addGoal(2, new HoldHandsGoal(this));
        this.goalSelector.addGoal(2, new LeadToBedGoal(this));
        this.goalSelector.addGoal(3, new SitWithOwnerGoal(this));
        this.goalSelector.addGoal(3, new StayPutGoal(this));
        this.goalSelector.addGoal(4, new SeekBedGoal(this));
        this.goalSelector.addGoal(5, new AffectionInitiativeGoal(this));
        this.goalSelector.addGoal(6, new FollowOwnerCompanionGoal(this));
        this.goalSelector.addGoal(7, new ReturnHomeGoal(this));
        this.goalSelector.addGoal(8, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(9, new IdleGestureGoal(this));
        this.goalSelector.addGoal(10, new CompanionLookGoal(this));
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    @Nullable
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        ensureInitialized();
        return result;
    }

    /** Gives a brand-new companion her identity (name, personality, tastes). Idempotent. */
    public void ensureInitialized() {
        if (dataInitialized || level().isClientSide) return;
        dataInitialized = true;
        List<? extends String> names = GFConfig.NAMES.get();
        String name = names.isEmpty() ? "Aria" : names.get(random.nextInt(names.size()));
        data.initialize(random, name, GFConfig.OUTFIT_COUNT.get(), WorldClock.day(level()));
        applyConfigAttributes();
        setHealth(getMaxHealth());
        syncIdentity();
    }

    public void applyConfigAttributes() {
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() != GFConfig.MAX_HEALTH.get()) {
            health.setBaseValue(GFConfig.MAX_HEALTH.get());
            if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        }
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(GFConfig.MOVE_SPEED.get());
    }

    /** Re-applies identity fields to synced data after load / restore / rename. */
    public void syncIdentity() {
        entityData.set(DATA_NAME, data.name);
        entityData.set(DATA_OUTFIT, data.outfit);
        super.setCustomName(Component.literal(data.name));
        setCustomNameVisible(false);
        entityData.set(DATA_STAGE, data.relationship.stage().ordinal());
        entityData.set(DATA_MOOD, data.mood.primary.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            controller().tick();
            interactions.tick();
            if (gestureTicks > 0 && --gestureTicks == 0) entityData.set(DATA_GESTURE, 0);
        }
    }

    public CompanionController controller() {
        if (controller == null) controller = new CompanionController(this);
        return controller;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            interactions.stop(InteractionManager.StopReason.REMOVED);
            if ((reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER) && level() instanceof ServerLevel s) {
                CompanionRegistry.get(s.getServer()).update(this, true);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Companion", data.save());
        entityData.get(DATA_OWNER).ifPresent(u -> tag.putUUID("Owner", u));
        tag.putString("Command", command.name());
        if (waitPos != null) tag.putLong("WaitPos", waitPos.asLong());
        tag.putInt("OutfitBeforeBed", outfitBeforeBed);
        tag.putBoolean("Initialized", dataInitialized);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Companion")) {
            data.load(tag.getCompound("Companion"));
            dataInitialized = tag.getBoolean("Initialized") || !data.name.isEmpty();
        }
        entityData.set(DATA_OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        try {
            setCommand(CommandMode.valueOf(tag.getString("Command")));
        } catch (Exception e) {
            setCommand(CommandMode.FOLLOW);
        }
        waitPos = tag.contains("WaitPos") ? BlockPos.of(tag.getLong("WaitPos")) : null;
        outfitBeforeBed = tag.contains("OutfitBeforeBed") ? tag.getInt("OutfitBeforeBed") : -1;
        data.relationship.refreshStage();
        if (!level().isClientSide) {
            applyConfigAttributes();
            syncIdentity();
        }
    }

    /** Replaces this companion's identity with a saved snapshot (used when restoring a lost companion). */
    public void restoreFrom(CompoundTag snapshot) {
        data.load(snapshot);
        data.generation++;
        dataInitialized = true;
        data.relationship.refreshStage();
        applyConfigAttributes();
        syncIdentity();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return isScared() ? GFConfig.MAX_SAFE_FALL.get() + 1 : GFConfig.MAX_SAFE_FALL.get();
    }

    // ------------------------------------------------------------------ ownership

    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner) {
        entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    public boolean isOwnedBy(Entity e) {
        UUID o = getOwnerUUID();
        return o != null && o.equals(e.getUUID());
    }

    public boolean hasOwner() {
        return getOwnerUUID() != null;
    }

    /** The owner if online in the same dimension. */
    @Nullable
    public Player getOwner() {
        UUID o = getOwnerUUID();
        if (o == null) return null;
        Player p = level().getPlayerByUUID(o);
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    /** The owner anywhere on the server (server side only). */
    @Nullable
    public ServerPlayer getOwnerAnywhere() {
        UUID o = getOwnerUUID();
        if (o == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(o);
    }

    // ------------------------------------------------------------------ interaction entry points

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        return controller().onRightClick(sp, player.getItemInHand(hand));
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (!level().isClientSide && name != null && dataInitialized) {
            String n = name.getString().trim();
            if (!n.isEmpty() && !n.equals(data.name)) {
                String old = data.name;
                data.name = n.length() > 24 ? n.substring(0, 24) : n;
                entityData.set(DATA_NAME, data.name);
                controller().onRenamed(old);
            }
        }
        setCustomNameVisible(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (isFainted() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (interactions.isActive() && interactions.current() == InteractionType.CARRY && source.is(DamageTypeTags.IS_FALL)) return false;
        float before = getHealth();
        if (!controller().beforeHurt(source, amount)) return false;
        boolean result = super.hurt(source, amount);
        if (result) controller().afterHurt(source, before - getHealth());
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && GFConfig.FAINT_INSTEAD_OF_DEATH.get() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1f);
            controller().faint(source);
            return;
        }
        if (!level().isClientSide) controller().onDeath(source);
        super.die(source);
    }

    @Override
    public boolean isPushable() {
        return !interactions.isActive() && !isPassenger() && !isSleeping() && !isFainted() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (interactions.isActive() || isPassenger() || isSleeping()) return;
        super.pushEntities();
    }

    @Override
    protected void doPush(Entity entity) {
        if (entity instanceof Player && isOwnedBy(entity)) return;
        super.doPush(entity);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isPassenger() && getVehicle() instanceof Player && source.is(DamageTypeTags.IS_FALL)) return true;
        return super.isInvulnerableTo(source);
    }

    /** Positions her in the player's arms while carried, on their lap, and at the right height on seats. */
    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        boolean client = level().isClientSide;
        if (vehicle instanceof Player p) {
            boolean lap = getSyncedInteraction() == InteractionType.LAP_SIT;
            double forward = lap ? PoseTuning.lapForward(client) : PoseTuning.carryForward(client);
            double height = lap ? PoseTuning.lapHeight(client) : PoseTuning.carryHeight(client);
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw) * forward, fz = Mth.cos(yaw) * forward;
            // the player's passenger point is on top of their head; shift down and forward from there
            return new Vec3(-fx, p.getBbHeight() - height, -fz);
        }
        if (vehicle instanceof SeatEntity) return new Vec3(0, PoseTuning.seatHeight(client), 0);
        return super.getVehicleAttachmentPoint(vehicle);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (getVehicle() instanceof Player p) {
            boolean lap = getSyncedInteraction() == InteractionType.LAP_SIT;
            float offset = (float) (lap ? PoseTuning.lapYaw(level().isClientSide) : PoseTuning.carryYaw(level().isClientSide));
            setYRot(p.yBodyRot + offset);
            yBodyRot = getYRot();
            yBodyRotO = yBodyRot;
            yHeadRot = lap ? p.getYHeadRot() : getYRot();
        } else if (getVehicle() instanceof SeatEntity seat) {
            setYRot(seat.getYRot());
            yBodyRot = seat.getYRot();
        }
    }

    // ------------------------------------------------------------------ sleeping beside the player

    /** Lies down on a bed without claiming it (the player may be sleeping in it). */
    public void startSleepingBeside(BlockPos bed, Vec3 position) {
        if (isPassenger()) stopRiding();
        setPose(Pose.SLEEPING);
        setSleepingPos(bed);
        setPos(position.x, position.y, position.z);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
        controller().sleepingShared = true;
        onBedtime();
    }

    @Override
    public void stopSleeping() {
        if (controller != null && controller.sleepingShared) {
            controller.sleepingShared = false;
            BlockPos bed = getSleepingPos().orElse(blockPosition());
            Vec3 stand = SleepHelper.findStandUpPosition(this, bed);
            setPose(Pose.STANDING);
            clearSleepingPos();
            setPos(stand.x, stand.y, stand.z);
            onWake();
            return;
        }
        super.stopSleeping();
        onWake();
    }

    // ------------------------------------------------------------------ accessors

    public CompanionData data() {
        return data;
    }

    public InteractionManager interactions() {
        return interactions;
    }

    public CommandMode getCommand() {
        return command;
    }

    public void setCommand(CommandMode mode) {
        this.command = mode;
        entityData.set(DATA_COMMAND, mode.ordinal());
    }

    public CommandMode getSyncedCommand() {
        return CommandMode.byId(entityData.get(DATA_COMMAND));
    }

    @Nullable
    public BlockPos getWaitPos() {
        return waitPos;
    }

    public void setWaitPos(@Nullable BlockPos pos) {
        this.waitPos = pos;
    }

    public String getCompanionName() {
        String n = entityData.get(DATA_NAME);
        return n.isEmpty() ? "Companion" : n;
    }

    public int getOutfit() {
        return entityData.get(DATA_OUTFIT);
    }

    public com.aigirlfriend.companion.Outfit getOutfitType() {
        return com.aigirlfriend.companion.Outfit.byIndex(getOutfit());
    }

    /** Manual outfit choice; also cancels the automatic change back after sleeping. */
    public void setOutfit(int outfit) {
        outfitBeforeBed = -1;
        wearOutfit(outfit);
    }

    private void wearOutfit(int outfit) {
        data.outfit = outfit;
        entityData.set(DATA_OUTFIT, outfit);
    }

    /** Sleepwear on when she goes to bed (if enabled), previous outfit back when she wakes. */
    private void onBedtime() {
        if (level().isClientSide || !GFConfig.AUTO_SLEEPWEAR.get()) return;
        int sleep = com.aigirlfriend.companion.Outfit.SLEEP.ordinal();
        if (getOutfit() != sleep && sleep < GFConfig.OUTFIT_COUNT.get()) {
            outfitBeforeBed = getOutfit();
            wearOutfit(sleep);
        }
    }

    private void onWake() {
        if (level().isClientSide) return;
        if (outfitBeforeBed >= 0 && getOutfit() == com.aigirlfriend.companion.Outfit.SLEEP.ordinal()) wearOutfit(outfitBeforeBed);
        outfitBeforeBed = -1;
    }

    @Override
    public void startSleeping(BlockPos pos) {
        super.startSleeping(pos);
        onBedtime();
    }

    public Mood getSyncedMood() {
        return Mood.byId(entityData.get(DATA_MOOD));
    }

    public void setSyncedMood(Mood mood) {
        entityData.set(DATA_MOOD, mood.ordinal());
    }

    public Expression getExpression() {
        return Expression.byId(entityData.get(DATA_EXPRESSION));
    }

    public void setExpression(Expression expression) {
        if (entityData.get(DATA_EXPRESSION) != expression.ordinal()) entityData.set(DATA_EXPRESSION, expression.ordinal());
    }

    public RelationshipStage getSyncedStage() {
        return RelationshipStage.byId(entityData.get(DATA_STAGE));
    }

    public void setSyncedStage(RelationshipStage stage) {
        entityData.set(DATA_STAGE, stage.ordinal());
    }

    public Activity getActivity() {
        return Activity.byId(entityData.get(DATA_ACTIVITY));
    }

    public void setActivity(Activity activity) {
        if (entityData.get(DATA_ACTIVITY) != activity.ordinal()) entityData.set(DATA_ACTIVITY, activity.ordinal());
    }

    public int getFlags() {
        return entityData.get(DATA_FLAGS);
    }

    public boolean hasFlag(int flag) {
        return (getFlags() & flag) != 0;
    }

    public void setFlag(int flag, boolean on) {
        int f = getFlags();
        int n = on ? (f | flag) : (f & ~flag);
        if (n != f) entityData.set(DATA_FLAGS, n);
    }

    public float getFear() {
        return entityData.get(DATA_FEAR);
    }

    public void setFear(float fear) {
        float rounded = Math.round(fear * 20f) / 20f;
        if (entityData.get(DATA_FEAR) != rounded) entityData.set(DATA_FEAR, rounded);
    }

    public boolean isScared() {
        return hasFlag(FLAG_SCARED);
    }

    public boolean isFainted() {
        return hasFlag(FLAG_FAINTED);
    }

    public boolean isSeated() {
        return getVehicle() instanceof SeatEntity || hasFlag(FLAG_GROUND_SIT);
    }

    public boolean isCarried() {
        return getVehicle() instanceof Player && getSyncedInteraction() != InteractionType.LAP_SIT;
    }

    public boolean isOnLap() {
        return getVehicle() instanceof Player && getSyncedInteraction() == InteractionType.LAP_SIT;
    }

    @Nullable
    public InteractionType getSyncedInteraction() {
        int v = entityData.get(DATA_INTERACTION) & 0xFF;
        return v == 0 ? null : InteractionType.byId(v - 1);
    }

    public boolean isSyncedInteractionSelfInitiated() {
        return (entityData.get(DATA_INTERACTION) & 0x100) != 0;
    }

    public int getSyncedPartnerId() {
        return entityData.get(DATA_PARTNER);
    }

    public void setSyncedInteraction(@Nullable InteractionType type, int partnerId, boolean selfInitiated) {
        entityData.set(DATA_INTERACTION, type == null ? 0 : (type.ordinal() + 1) | (selfInitiated ? 0x100 : 0));
        entityData.set(DATA_PARTNER, type == null ? -1 : partnerId);
    }

    public Gesture getGesture() {
        return Gesture.byId(entityData.get(DATA_GESTURE));
    }

    public int getGestureSequence() {
        return entityData.get(DATA_GESTURE_SEQ);
    }

    public void playGesture(Gesture gesture) {
        if (level().isClientSide || gesture == Gesture.NONE) return;
        entityData.set(DATA_GESTURE, gesture.ordinal());
        entityData.set(DATA_GESTURE_SEQ, entityData.get(DATA_GESTURE_SEQ) + 1);
        gestureTicks = gesture.duration;
    }

    public boolean isPlayingGesture() {
        return gestureTicks > 0;
    }

    public void feel(Emotion emotion, float intensity, int durationTicks) {
        if (!level().isClientSide) controller().feel(emotion, intensity, durationTicks);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        GirlfriendAnimations.register(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** Client-side animation bookkeeping (never used on the server). */
    public final GirlfriendAnimations.ClientState animState = new GirlfriendAnimations.ClientState();

    public static boolean isGirlfriend(@Nullable Entity e) {
        return e != null && e.getType() == ModEntities.GIRLFRIEND.get();
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }
}
