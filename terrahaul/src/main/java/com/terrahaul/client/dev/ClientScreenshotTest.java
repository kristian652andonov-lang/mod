package com.terrahaul.client.dev;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only visual test. When the client is started with
 * {@code -Dterrahaul.screenshots=<shots>} it waits for the singleplayer world to load, then for
 * every shot teleports the (spectator) player, waits until the terrain has been built and takes a
 * screenshot into {@code screenshots/}. Shots are separated by ';' and are either
 * {@code name@x,y,z,yaw,pitch} or {@code name@find:<landform>[,distance,height]} (the camera is
 * placed {@code distance} blocks south of the found landform, {@code height} blocks above it,
 * looking at it). Afterwards the client shuts down. Without the property this class does nothing.
 */
public final class ClientScreenshotTest {
    private static final String SPEC = System.getProperty("terrahaul.screenshots");
    private static final List<String> shots = new ArrayList<>();
    private static int index = -1;
    private static int wait;
    private static int stage;
    private static int startDelay = 100;
    private static boolean setup;

    private ClientScreenshotTest() {
    }

    public static boolean enabled() {
        return SPEC != null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (SPEC == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        if (startDelay-- > 0) {
            return;
        }
        if (!setup) {
            setup = true;
            for (String s : SPEC.split(";")) {
                if (!s.isBlank()) {
                    shots.add(s.trim());
                }
            }
            // run directly on the integrated server: a world created by a dedicated server has
            // cheats disabled, so chat commands would be rejected
            IntegratedServer server = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.setDayTime(Long.getLong("terrahaul.screenshots.time", 6000L));
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setWeatherParameters(12000, 0, false, false);
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                }
            });
            mc.options.hideGui = true;
            index = -1;
            stage = 0;
        }
        if (stage == 0) {
            index++;
            if (index >= shots.size()) {
                Terrahaul.LOGGER.info("[screenshots] done");
                mc.stop();
                return;
            }
            String shot = shots.get(index);
            double[] cam = resolve(mc, shot.substring(shot.indexOf('@') + 1));
            if (cam == null) {
                Terrahaul.LOGGER.warn("[screenshots] could not resolve {}", shot);
                return;
            }
            IntegratedServer server = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    player.teleportTo(server.overworld(), cam[0], cam[1], cam[2], (float) cam[3], (float) cam[4]);
                }
            });
            wait = 0;
            stage = 1;
        } else if (stage == 1) {
            wait++;
            boolean ready = wait > 200 && mc.levelRenderer.hasRenderedAllSections();
            if (ready || wait > Integer.getInteger("terrahaul.screenshots.maxWait", 1800)) {
                String name = shots.get(index);
                name = name.substring(0, name.indexOf('@'));
                Screenshot.grab(mc.gameDirectory, "terrahaul_" + name + ".png", mc.getMainRenderTarget(),
                    msg -> Terrahaul.LOGGER.info("[screenshots] {}", msg.getString()));
                Terrahaul.LOGGER.info("[screenshots] {} after {} ticks (all sections rendered: {})", name, wait, ready);
                stage = 2;
                wait = 0;
            }
        } else if (stage == 2 && ++wait > 10) {
            stage = 0;
        }
    }

    private static double[] resolve(Minecraft mc, String spec) {
        if (spec.startsWith("biome:")) {
            // biome:<id>,distance,height : look at the nearest biome of that type
            String[] p = spec.substring(6).split(",");
            ServerLevel level = mc.getSingleplayerServer().overworld();
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(p[0]));
            var found = level.findClosestBiome3d(h -> h.is(key), mc.player.blockPosition(), 12000, 32, 64);
            if (found == null) {
                return null;
            }
            BlockPos pos = found.getFirst();
            TerrainModel model = TerrahaulGeneration.model(level);
            double distance = p.length > 1 ? Double.parseDouble(p[1]) : 40;
            double height = p.length > 2 ? Double.parseDouble(p[2]) : 20;
            double ground = model != null ? model.sample(pos.getX(), pos.getZ()).height : 80;
            double camGround = model != null ? model.sample(pos.getX(), pos.getZ() + (int) distance).height : 80;
            double camY = Math.max(Math.max(camGround, 63), ground) + height;
            double pitch = -Math.toDegrees(Math.atan2(ground + 8 - camY, distance));
            return new double[]{pos.getX() + 0.5, camY, pos.getZ() + distance + 0.5, 180.0, pitch};
        }
        if (!spec.startsWith("find:")) {
            String[] p = spec.split(",");
            return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Double.parseDouble(p[4])};
        }
        String[] p = spec.substring(5).split(",");
        IntegratedServer server = mc.getSingleplayerServer();
        ServerLevel level = server.overworld();
        TerrainModel model = TerrahaulGeneration.model(level);
        TerrainFinder.Target target = TerrainFinder.targets().get(p[0]);
        if (model == null || target == null) {
            return null;
        }
        BlockPos origin = mc.player.blockPosition();
        TerrainFinder.Result r = TerrainFinder.find(model, target, origin.getX(), origin.getZ(), 20000, () -> false);
        if (r == null) {
            return null;
        }
        double distance = p.length > 1 ? Double.parseDouble(p[1]) : 60;
        double height = p.length > 2 ? Double.parseDouble(p[2]) : 25;
        ColumnSample s = new ColumnSample();
        model.sampleInto(r.x(), r.z() + (int) distance, s);
        double camY = Math.max(Math.max(s.height, 63), r.y()) + height;
        double dy = r.y() - camY;
        double pitch = -Math.toDegrees(Math.atan2(dy, distance));
        Terrahaul.LOGGER.info("[screenshots] {} found at {} {} {}", p[0], r.x(), r.y(), r.z());
        return new double[]{r.x() + 0.5, camY, r.z() + distance + 0.5, 180.0, pitch};
    }
}
