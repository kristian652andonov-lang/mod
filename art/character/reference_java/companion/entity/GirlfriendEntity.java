package com.example.companion.entity;

import com.example.companion.outfit.Outfit;
import com.example.companion.outfit.Wardrobe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Reference integration. If your mod already has her entity, copy the marked sections into it:
 * the two data accessors, the Wardrobe, the sleep overrides, NBT and the animation picker.
 */
public class GirlfriendEntity extends PathfinderMob implements GeoEntity {
    // --- outfit + activity (synced to clients) ---
    private static final EntityDataAccessor<String> DATA_OUTFIT = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.STRING);
    /** Set by your own AI/interaction code: "hug", "cuddle", "carried", "lap", "pet_head", "pet_cheek", "stretch", "lie_down", "wake_up" or "". */
    private static final EntityDataAccessor<String> DATA_ACTIVITY = SynchedEntityData.defineId(GirlfriendEntity.class, EntityDataSerializers.STRING);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.girlfriend.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.girlfriend.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.girlfriend.run");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.girlfriend.swim");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.girlfriend.sit");
    private static final RawAnimation HUG = RawAnimation.begin().thenLoop("animation.girlfriend.hug");
    private static final RawAnimation CARRIED = RawAnimation.begin().thenLoop("animation.girlfriend.carried");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.girlfriend.sleep");
    private static final RawAnimation CUDDLE = RawAnimation.begin().thenLoop("animation.girlfriend.cuddle");
    private static final RawAnimation LIE_DOWN = RawAnimation.begin().thenPlayAndHold("animation.girlfriend.lie_down");
    private static final RawAnimation STRETCH = RawAnimation.begin().thenPlay("animation.girlfriend.stretch");
    private static final RawAnimation WAKE_UP = RawAnimation.begin().thenPlay("animation.girlfriend.wake_up");
    private static final RawAnimation LAP = RawAnimation.begin().thenLoop("animation.girlfriend.lap");
    private static final RawAnimation PET_HEAD = RawAnimation.begin().thenLoop("animation.girlfriend.pet_head");
    private static final RawAnimation PET_CHEEK = RawAnimation.begin().thenLoop("animation.girlfriend.pet_cheek");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Wardrobe wardrobe = new Wardrobe(this, DATA_OUTFIT);

    public GirlfriendEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OUTFIT, Outfit.EVERYDAY.id);
        builder.define(DATA_ACTIVITY, "");
    }

    // --- outfits ---
    public Outfit getOutfit() { return wardrobe.current(); }
    public Wardrobe wardrobe() { return wardrobe; }
    /** Server side. Safe at any moment, including mid-animation, in bed or while being carried. */
    public void chooseOutfit(Outfit outfit) { wardrobe.choose(outfit); }
    /** Replace with your relationship/ownership rule. */
    public boolean canBeDressedBy(Player player) { return true; }

    @Override
    public void startSleeping(BlockPos pos) {
        super.startSleeping(pos);
        if (!level().isClientSide) wardrobe.onBedtime();
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        if (!level().isClientSide) wardrobe.onWake();
    }

    public void setActivity(String activity) { entityData.set(DATA_ACTIVITY, activity); }
    public String getActivity() { return entityData.get(DATA_ACTIVITY); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        wardrobe.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        wardrobe.load(tag);
    }

    // --- animation: one controller; outfit changes never touch it ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::pickAnimation));
    }

    private PlayState pickAnimation(AnimationState<GirlfriendEntity> state) {
        String activity = getActivity();
        RawAnimation anim;
        if (isSleeping()) anim = "cuddle".equals(activity) ? CUDDLE : SLEEP;
        else if (!activity.isEmpty()) anim = switch (activity) {
            case "hug" -> HUG;
            case "carried" -> CARRIED;
            case "stretch" -> STRETCH;
            case "lie_down" -> LIE_DOWN;
            case "wake_up" -> WAKE_UP;
            case "lap" -> LAP;
            case "pet_head" -> PET_HEAD;
            case "pet_cheek" -> PET_CHEEK;
            default -> IDLE;
        };
        else if (isPassenger()) anim = getVehicle() instanceof Player ? CARRIED : SIT;
        else if (isSwimming() || (isInWater() && state.isMoving())) anim = SWIM;
        else if (state.isMoving()) anim = isSprinting() ? RUN : WALK;
        else anim = IDLE;
        return state.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
