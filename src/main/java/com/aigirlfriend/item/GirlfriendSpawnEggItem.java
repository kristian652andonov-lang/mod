package com.aigirlfriend.item;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.world.ActiveCompanions;
import com.aigirlfriend.world.CompanionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.function.Supplier;

/**
 * Spawns a companion owned by the user. Enforces the per-player limit (no duplicates) and, if one of the
 * owner's companions was lost, restores her identity, memories and relationship instead of creating a stranger.
 */
public class GirlfriendSpawnEggItem extends DeferredSpawnEggItem {
    private final Supplier<? extends EntityType<GirlfriendEntity>> type;

    public GirlfriendSpawnEggItem(Supplier<? extends EntityType<GirlfriendEntity>> type, int bg, int fg, Properties props) {
        super(type, bg, fg, props);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel server) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        return spawnFor(server, player, pos, ctx.getItemInHand()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    public boolean spawnFor(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack) {
        CompanionRegistry reg = CompanionRegistry.get(level.getServer());
        int active = 0;
        for (CompanionRegistry.Entry e : reg.byOwner(player.getUUID())) {
            if (e.status == CompanionRegistry.Status.ACTIVE) active++;
        }
        if (active >= GFConfig.MAX_PER_PLAYER.get()) {
            player.displayClientMessage(Component.translatable("aigirlfriend.message.limit"), false);
            return false;
        }
        GirlfriendEntity gf = type.get().create(level);
        if (gf == null) return false;
        gf.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180f, 0f);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(gf, level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null);
        CompanionRegistry.Entry restore = GFConfig.RESTORE_IDENTITY.get() ? reg.restorable(player.getUUID()) : null;
        if (restore != null && ActiveCompanions.get(restore.companionId) == null) {
            gf.restoreFrom(restore.snapshot);
            gf.setHealth(gf.getMaxHealth());
        }
        gf.setOwner(player.getUUID());
        if (!level.addFreshEntity(gf)) return false;
        if (restore != null) {
            reg.update(gf, true);
            player.displayClientMessage(Component.translatable("aigirlfriend.message.restored", gf.data().name), false);
            gf.controller().say(com.aigirlfriend.dialogue.DialogueTrigger.RETURN_AFTER_AWAY, java.util.Map.of("away", "so long"));
        } else {
            gf.controller().claim(player);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }

    @Override
    public boolean spawnsEntity(ItemStack stack, EntityType<?> entityType) {
        return entityType == type.get();
    }
}
