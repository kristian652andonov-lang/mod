package com.aigirlfriend.client;

import com.aigirlfriend.client.anim.PlayerPoseController;
import com.aigirlfriend.client.hud.CompanionHud;
import com.aigirlfriend.client.screen.StatusScreen;
import com.aigirlfriend.client.screen.TalkScreen;
import com.aigirlfriend.client.voice.VoiceManager;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.ActionType;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.interaction.Positioning;
import com.aigirlfriend.network.payload.CancelInteractionPayload;
import com.aigirlfriend.network.payload.OpenMenuRequestPayload;
import com.aigirlfriend.network.payload.PerformActionPayload;
import com.aigirlfriend.network.payload.SitPayload;
import com.aigirlfriend.network.payload.StatusRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/** Client game-bus handlers: keybinds, movement lock, hand pull, player root poses. */
public final class ClientEvents {
    private final Set<Integer> pushedRoot = new HashSet<>();
    private int faceTicks;
    private InteractionType lastLocal;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        ClientState.clientTicks++;
        PlayerPoseController.tick();
        VoiceManager.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        handleKeys(mc, player);
        handleLocalInteraction(player);
    }

    private static int targetId(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof GirlfriendEntity gf && mc.player != null && gf.isOwnedBy(mc.player)) return gf.getId();
        GirlfriendEntity gf = CompanionHud.nearestOwned(10);
        return gf == null ? -1 : gf.getId();
    }

    private void handleKeys(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null) return;
        while (Keybinds.WHEEL.consumeClick()) PacketDistributor.sendToServer(new OpenMenuRequestPayload(targetId(mc)));
        while (Keybinds.STATUS.consumeClick()) {
            int id = targetId(mc);
            if (id < 0) id = nearestId(48);
            if (id >= 0) {
                PacketDistributor.sendToServer(new StatusRequestPayload(id));
                mc.setScreen(new StatusScreen(id));
            }
        }
        while (Keybinds.TALK.consumeClick()) {
            int id = targetId(mc);
            if (id >= 0) {
                PacketDistributor.sendToServer(new StatusRequestPayload(id));
                mc.setScreen(new TalkScreen(id));
            }
        }
        while (Keybinds.HOLD_HANDS.consumeClick()) {
            int id = targetId(mc);
            if (id < 0) continue;
            GirlfriendEntity gf = (GirlfriendEntity) mc.level.getEntity(id);
            boolean holding = gf != null && gf.getSyncedPartnerId() == player.getId()
                    && (gf.getSyncedInteraction() == InteractionType.HOLD_HANDS || gf.getSyncedInteraction() == InteractionType.LEAD_TO_BED);
            PacketDistributor.sendToServer(new PerformActionPayload(id, (holding ? ActionType.LET_GO : ActionType.HOLD_HANDS).ordinal()));
        }
        quick(mc, Keybinds.HUG, ActionType.HUG);
        quick(mc, Keybinds.KISS, ActionType.KISS);
        quick(mc, Keybinds.CUDDLE, ActionType.CUDDLE);
        quick(mc, Keybinds.PET, ActionType.PET_HAIR);
        while (Keybinds.LAP.consumeClick()) {
            int id = targetId(mc);
            if (id < 0) continue;
            boolean onLap = player.getPassengers().stream().anyMatch(p -> p.getId() == id);
            PacketDistributor.sendToServer(new PerformActionPayload(id, (onLap ? ActionType.GET_UP : ActionType.LAP_SIT).ordinal()));
        }
        while (Keybinds.CARRY.consumeClick()) {
            int id = targetId(mc);
            if (id < 0) continue;
            boolean carrying = player.getPassengers().stream().anyMatch(p -> p.getId() == id && p instanceof GirlfriendEntity g && g.isCarried());
            PacketDistributor.sendToServer(new PerformActionPayload(id, (carrying ? ActionType.PUT_DOWN : ActionType.PICK_UP).ordinal()));
        }
        while (Keybinds.SIT.consumeClick()) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK && !player.isPassenger()
                    && bhr.getLocation().distanceTo(player.getEyePosition()) <= 4.5) {
                PacketDistributor.sendToServer(new SitPayload(true, bhr.getBlockPos()));
            } else {
                PacketDistributor.sendToServer(new SitPayload(false, BlockPos.ZERO));
            }
        }
        while (Keybinds.CANCEL.consumeClick()) PacketDistributor.sendToServer(new CancelInteractionPayload(0));
    }

    private static void quick(Minecraft mc, net.minecraft.client.KeyMapping key, ActionType action) {
        while (key.consumeClick()) {
            int id = targetId(mc);
            if (id >= 0) PacketDistributor.sendToServer(new PerformActionPayload(id, action.ordinal()));
        }
    }

    private static int nearestId(double radius) {
        GirlfriendEntity gf = CompanionHud.nearestOwned(radius);
        return gf == null ? -1 : gf.getId();
    }

    /** Turns to face her at the start of face-to-face interactions, and feels her gentle pull when led by the hand. */
    private void handleLocalInteraction(LocalPlayer player) {
        InteractionType t = PlayerPoseController.interactionOf(player);
        GirlfriendEntity gf = PlayerPoseController.partnerOf(player);
        if (t != lastLocal) {
            faceTicks = 0;
            lastLocal = t;
        }
        if (t == null || gf == null) return;
        if (t.positioning == Positioning.FACE && faceTicks++ < 12) {
            Vec3 d = gf.position().subtract(player.position());
            float targetYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            float yaw = Mth.rotLerp(0.35f, player.getYRot(), targetYaw);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.yBodyRot = Mth.rotLerp(0.35f, player.yBodyRot, targetYaw);
        }
        if (t == InteractionType.LEAD_TO_BED && gf.hasFlag(GirlfriendEntity.FLAG_LEADING) && GFClientConfig.HAND_PULL.get() && !player.isShiftKeyDown()) {
            Vec3 d = gf.position().subtract(player.position());
            double dist = d.horizontalDistance();
            if (dist > 2.0 && dist < 9.0) {
                double strength = GFClientConfig.HAND_PULL_STRENGTH.get() * 0.035 * Math.min(3.0, dist - 2.0);
                Vec3 dir = new Vec3(d.x / dist, 0, d.z / dist);
                player.setDeltaMovement(player.getDeltaMovement().add(dir.scale(strength)));
            }
        }
    }

    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent event) {
        if (!GFClientConfig.LOCK_MOVEMENT.get()) return;
        InteractionType t = PlayerPoseController.interactionOf(event.getEntity());
        if (t == null || !t.locksPlayer) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
    }

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        PlayerPoseController.alignBody(event.getEntity());
        if (PlayerPoseController.pushRootTransform(event.getEntity(), event.getPoseStack(), event.getPartialTick())) {
            pushedRoot.add(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushedRoot.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public void onShutdown(GameShuttingDownEvent event) {
        VoiceManager.shutdown();
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clear();
        PlayerPoseController.clear();
        VoiceManager.clear();
        pushedRoot.clear();
    }
}
