package com.fantasyweapons.client.devtest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.gui.ProgressionScreen;
import com.fantasyweapons.network.C2SPayloads;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DEVELOPMENT ONLY. Scripted visual test run used to verify models, HUD, menus and VFX in a real client.
 * Completely inert unless the JVM is started with {@code -Dfantasyweapons.devtest=<script>}; it is never active in
 * normal play. Runs commands on the integrated server, drives the camera and ability keys and saves screenshots to
 * {@code screenshots/}.
 */
public final class ScreenshotDirector {
    private record Step(int delay, Consumer<Minecraft> action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;
    private static int inWorldTicks;

    private ScreenshotDirector() {
    }

    public static void initIfRequested() {
        String script = System.getProperty("fantasyweapons.devtest");
        if (script == null || script.isBlank()) return;
        FantasyWeapons.LOGGER.warn("Fantasy Weapons dev test director active: {}", script);
        DevScripts.build(script, new Builder());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (++inWorldTicks < 60) return;
        if (index >= STEPS.size()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = STEPS.get(index++);
        try {
            s.action().accept(mc);
        } catch (RuntimeException ex) {
            FantasyWeapons.LOGGER.error("devtest step {} failed", index - 1, ex);
        }
        wait = index < STEPS.size() ? STEPS.get(index).delay() : 0;
    }

    /** Fluent script builder handed to {@link DevScripts}. */
    public static final class Builder {
        private int nextDelay;

        public Builder wait(int ticks) {
            nextDelay += ticks;
            return this;
        }

        public Builder run(Consumer<Minecraft> action) {
            STEPS.add(new Step(nextDelay, action));
            nextDelay = 0;
            return this;
        }

        /** Runs a command on the integrated server as the player, with operator permission. */
        public Builder cmd(String command) {
            return run(mc -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server == null || mc.player == null) return;
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                    if (sp != null) server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), command);
                });
            });
        }

        public Builder camera(CameraType type) {
            return run(mc -> mc.options.setCameraType(type));
        }

        public Builder look(float yaw, float pitch) {
            return run(mc -> {
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                mc.player.yRotO = yaw;
                mc.player.xRotO = pitch;
                mc.player.setYHeadRot(yaw);
                mc.player.yBodyRot = yaw;
            });
        }

        public Builder hud(boolean visible) {
            return run(mc -> mc.options.hideGui = !visible);
        }

        public Builder screenshot(String name) {
            return run(mc -> Screenshot.grab(mc.gameDirectory, "fw_" + name + ".png", mc.getMainRenderTarget(),
                    msg -> FantasyWeapons.LOGGER.info("devtest screenshot {}: {}", name, msg.getString())));
        }

        public Builder abilityDown() {
            return run(mc -> {
                PacketDistributor.sendToServer(new C2SPayloads.AbilityKey(true));
                var held = mc.player.getMainHandItem();
                if (held.getItem() instanceof FantasyWeaponItem item) ClientState.predictCharge(item.definition(), FantasyWeaponItem.data(held));
            });
        }

        public Builder abilityUp() {
            return run(mc -> {
                PacketDistributor.sendToServer(new C2SPayloads.AbilityKey(false));
                ClientState.clearPrediction();
            });
        }

        public Builder select(String ability) {
            return run(mc -> {
                var held = mc.player.getMainHandItem();
                PacketDistributor.sendToServer(new C2SPayloads.SelectAbility(mc.player.getInventory().selected,
                        FantasyWeaponItem.data(held).idOrNil(), ability));
            });
        }

        public Builder swing() {
            return run(mc -> {
                if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    mc.gameMode.attack(mc.player, ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity());
                }
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            });
        }

        public Builder menu(String selectedNode) {
            return run(mc -> {
                ProgressionScreen screen = new ProgressionScreen(mc.player.getInventory().selected);
                mc.setScreen(screen);
                if (selectedNode != null) screen.select(selectedNode);
            });
        }

        public Builder closeScreen() {
            return run(mc -> mc.setScreen(null));
        }

        public Builder slot(int slot) {
            return run(mc -> mc.player.getInventory().selected = slot);
        }

        /** Views the player from an offset (relative to the player's feet) through a detached camera entity. */
        public Builder viewFrom(double dx, double dy, double dz) {
            return run(mc -> {
                var p = mc.player;
                var cam = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
                double x = p.getX() + dx, y = p.getY() + dy, z = p.getZ() + dz;
                double ey = y + cam.getEyeHeight();
                double tx = p.getX() - x, ty = p.getY() + 1.15 - ey, tz = p.getZ() - z;
                float yaw = (float) (Math.toDegrees(Math.atan2(tz, tx)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(ty, Math.sqrt(tx * tx + tz * tz)));
                cam.moveTo(x, y, z, yaw, pitch);
                cam.setYHeadRot(yaw);
                cam.setInvisible(true);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setCameraEntity(cam);
            });
        }

        public Builder playerView() {
            return run(mc -> mc.setCameraEntity(mc.player));
        }

        /** Pins the player's animation timeline (see AnimTracker#debugPin); all -1 releases it. */
        public Builder pin(float swing, boolean heavy, boolean backhand, float charge, float cast, float form) {
            return run(mc -> com.fantasyweapons.client.anim.AnimTracker.debugPin(mc.player, swing, heavy, backhand, charge, cast, form));
        }

        /** Forces the weapon grip angles (radians); null releases. */
        public Builder grip(float[] gxz) {
            return run(mc -> com.fantasyweapons.client.anim.WeaponPoses.debugGrip = gxz);
        }

        public Builder quit() {
            return run(mc -> mc.stop());
        }
    }
}
