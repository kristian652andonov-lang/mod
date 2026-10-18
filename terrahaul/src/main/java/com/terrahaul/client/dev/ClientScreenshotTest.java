package com.terrahaul.client.dev;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.TerrahaulGeneration;
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
 * {@code name@x,y,z,yaw,pitch}, {@code name@find:<landform>[,distance,height]} or
 * {@code name@biome:<biome id>[,distance,height]} (the camera is placed {@code distance} blocks from
 * the found place, {@code height} blocks above it, on the side with the clearest view, looking at
 * it). Afterwards the client shuts down. Without the property this class does nothing.
 */
public final class ClientScreenshotTest {
    private static final String SPEC = System.getProperty("terrahaul.screenshots");
    private static final List<String> shots = new ArrayList<>();
    private static int index = -1;
    private static int wait;
    private static int stage;
    private static int startDelay = 100;
    private static boolean setup;
    private static double[] lastCamera;
    private static int stableTicks;
    private static final int MIN_WAIT = Integer.getInteger("terrahaul.screenshots.minWait", 400);

    private ClientScreenshotTest() {
    }

    public static boolean enabled() {
        return SPEC != null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (MEMORY_LOG && Minecraft.getInstance().level != null && ++memoryTicks % MEMORY_INTERVAL == 0) {
            logMemory(Minecraft.getInstance(), "tick " + memoryTicks + " loaded chunks " + Minecraft.getInstance().level.getChunkSource().getLoadedChunksCount());
            if (memoryTicks >= MEMORY_TICKS) {
                Minecraft.getInstance().stop();
            }
        }
        if (CREATE_WORLD_CHECK) {
            checkCreateWorldScreen(Minecraft.getInstance());
            return;
        }
        if (JOIN_CHECK) {
            checkMultiplayer(Minecraft.getInstance());
            return;
        }
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
                    // lets underwater shots (trenches, sea floors) show more than murky blue
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                        -1, 0, false, false));
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
            String where = shot.substring(shot.indexOf('@') + 1);
            // "same" repeats the previous camera, for comparing animation frames
            double[] cam = where.equals("same") ? lastCamera : resolve(mc, where);
            lastCamera = cam;
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
            stableTicks = 0;
            stage = 1;
        } else if (stage == 1) {
            wait++;
            // chunks around a far teleport target arrive over several seconds; an empty world
            // also counts as "all sections rendered", so wait for the nearby chunks first
            // with software rendering only a few frames run per second and the section graph
            // catches up slowly, so require "all rendered" to hold for three seconds
            boolean rendered = nearbyChunksLoaded(mc, Math.min(8, mc.options.getEffectiveRenderDistance() - 1))
                && mc.levelRenderer.hasRenderedAllSections();
            stableTicks = rendered ? stableTicks + 1 : 0;
            boolean ready = wait > MIN_WAIT && stableTicks > 60;
            if (ready || wait > Integer.getInteger("terrahaul.screenshots.maxWait", 1800)) {
                String name = shots.get(index);
                name = name.substring(0, name.indexOf('@'));
                Screenshot.grab(mc.gameDirectory, "terrahaul_" + name + ".png", mc.getMainRenderTarget(),
                    msg -> Terrahaul.LOGGER.info("[screenshots] {}", msg.getString()));
                Terrahaul.LOGGER.info("[screenshots] {} after {} ticks (all sections rendered: {}), particles: {}, render distance {}, chunks loaded r8 {}, at {}",
                    name, wait, ready, mc.particleEngine.countParticles(), mc.options.getEffectiveRenderDistance(),
                    nearbyChunksLoaded(mc, 8), mc.player.blockPosition().toShortString());
                logMemory(mc, name);
                stage = 2;
                wait = 0;
            }
        } else if (stage == 2 && ++wait > 10) {
            stage = 0;
        }
    }

    private static final boolean MEMORY_LOG = Boolean.getBoolean("terrahaul.screenshots.memory");
    private static final int MEMORY_TICKS = Integer.getInteger("terrahaul.screenshots.memoryTicks", 4000);
    private static final int MEMORY_INTERVAL = Integer.getInteger("terrahaul.screenshots.memoryInterval", 200);
    private static int memoryTicks;
    private static final boolean JOIN_CHECK = Boolean.getBoolean("terrahaul.screenshots.join");
    private static int joinTicks;

    /**
     * With {@code -Dterrahaul.screenshots.join=true} (and a {@code --quickPlayMultiplayer} server):
     * once connected, waits for the terrain to load, logs what the client received, takes a
     * screenshot and disconnects. Used to test a client against a dedicated server over the network.
     */
    private static void checkMultiplayer(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() != null) {
            return;
        }
        joinTicks++;
        if (joinTicks == 1) {
            mc.options.hideGui = true;
            Terrahaul.LOGGER.info("[screenshots] joined {} at {}, dimension height {}..{}", mc.getCurrentServer() == null ? "?" : mc.getCurrentServer().ip,
                mc.player.blockPosition().toShortString(), mc.level.getMinBuildHeight(), mc.level.getMaxBuildHeight());
        }
        if (joinTicks == 600) {
            Terrahaul.LOGGER.info("[screenshots] multiplayer: chunks loaded r6 {}, sky effects {}, particles {}", nearbyChunksLoaded(mc, 6),
                mc.level.effects().getClass().getSimpleName(), mc.particleEngine.countParticles());
            Screenshot.grab(mc.gameDirectory, "terrahaul_multiplayer.png", mc.getMainRenderTarget(),
                msg -> Terrahaul.LOGGER.info("[screenshots] {}", msg.getString()));
        }
        if (joinTicks == 620) {
            mc.level.disconnect();
            mc.stop();
        }
    }

    private static final boolean CREATE_WORLD_CHECK = Boolean.getBoolean("terrahaul.screenshots.createWorld");
    private static int createWorldTicks;

    /**
     * With {@code -Dterrahaul.screenshots.createWorld=true}: opens the Create World screen from the
     * title screen, logs the selected world type and the available presets, takes a screenshot of
     * the World tab and closes the client.
     */
    private static void checkCreateWorldScreen(Minecraft mc) {
        if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen title && createWorldTicks == 0) {
            net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, title);
            createWorldTicks = 1;
            return;
        }
        if (createWorldTicks > 0 && mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen screen) {
            createWorldTicks++;
            if (createWorldTicks == 40) {
                var state = screen.getUiState();
                Terrahaul.LOGGER.info("[screenshots] create world: selected type {}", state.getWorldType().preset() == null ? "null"
                    : state.getWorldType().preset().unwrapKey().map(k -> k.location().toString()).orElse("?"));
                for (var entry : state.getNormalPresetList()) {
                    Terrahaul.LOGGER.info("[screenshots] create world: preset available {}", entry.preset() == null ? "null"
                        : entry.preset().unwrapKey().map(k -> k.location().toString()).orElse("?"));
                }
                // switch to the World tab to show the world type button
                screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.tabs.TabNavigationBar)
                    .map(c -> (net.minecraft.client.gui.components.tabs.TabNavigationBar) c).findFirst().ifPresent(bar -> bar.selectTab(1, false));
            }
            if (createWorldTicks == 60) {
                Screenshot.grab(mc.gameDirectory, "terrahaul_create_world.png", mc.getMainRenderTarget(),
                    msg -> Terrahaul.LOGGER.info("[screenshots] {}", msg.getString()));
            }
            if (createWorldTicks == 70) {
                mc.stop();
            }
        }
    }

    /** Logs heap use, process memory and the renderer's section statistics. */
    static void logMemory(Minecraft mc, String label) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        // the live heap: what is left after a full collection
        System.gc();
        long live = (rt.totalMemory() - rt.freeMemory()) >> 20;
        String rss = "?";
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS")) {
                    rss = line.substring(6).trim();
                }
            }
        } catch (Exception ignored) {
            // not on Linux
        }
        Terrahaul.LOGGER.info("[screenshots] memory at {}: heap used {} MB (live {} MB) of max {} MB, process RSS {}, renderer: {}", label,
            used, live, rt.maxMemory() >> 20, rss, mc.levelRenderer.getSectionStatistics());
    }

    private static boolean nearbyChunksLoaded(Minecraft mc, int radius) {
        int cx = mc.player.blockPosition().getX() >> 4;
        int cz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!mc.level.hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double[] resolve(Minecraft mc, String spec) {
        IntegratedServer server = mc.getSingleplayerServer();
        ServerLevel level = server.overworld();
        TerrainModel model = TerrahaulGeneration.model(level);
        BlockPos origin = mc.player.blockPosition();
        if (spec.startsWith("biome:")) {
            // biome:<id>,distance,height : camera height is above the local ground, so a small
            // height gives a view from under the canopy
            String[] p = spec.substring(6).split(",");
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(p[0]));
            double distance = p.length > 1 ? Double.parseDouble(p[1]) : 40;
            double height = p.length > 2 ? Double.parseDouble(p[2]) : 20;
            return server.submit(() -> {
                var found = level.findClosestBiome3d(h -> h.is(key), origin, 12000, 32, 64);
                if (found == null) {
                    return null;
                }
                BlockPos pos = found.getFirst();
                Terrahaul.LOGGER.info("[screenshots] {} found at {} {}", p[0], pos.getX(), pos.getZ());
                return viewpoint(level, pos.getX(), Double.NaN, pos.getZ(), distance, height, true);
            }).join();
        }
        if (!spec.startsWith("find:")) {
            String[] p = spec.split(",");
            return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Double.parseDouble(p[4])};
        }
        String[] p = spec.substring(5).split(",");
        TerrainFinder.Target target = TerrainFinder.targets().get(p[0]);
        if (model == null || target == null) {
            return null;
        }
        TerrainFinder.Result r = TerrainFinder.find(model, target, origin.getX(), origin.getZ(), 20000, () -> false);
        if (r == null) {
            return null;
        }
        Terrahaul.LOGGER.info("[screenshots] {} found at {} {} {}", p[0], r.x(), r.y(), r.z());
        double distance = p.length > 1 ? Double.parseDouble(p[1]) : 60;
        double height = p.length > 2 ? Double.parseDouble(p[2]) : 25;
        // the finder reports the water surface for underwater targets (a safe /tp height); look at
        // the sea floor instead
        double ty = p[0].equals("trench") || p[0].equals("deep_ocean") ? model.sample(r.x(), r.z()).height + 4.0 : r.y();
        boolean underwater = ty < r.y() - 8;
        return server.submit(() -> underwater ? viewpointUnderwater(level, r.x(), ty, r.z(), distance, height)
            : viewpoint(level, r.x(), ty, r.z(), distance, height, false)).join();
    }

    /** A camera {@code height} blocks above an underwater target and {@code distance} away, looking at it. */
    private static double[] viewpointUnderwater(ServerLevel level, double tx, double ty, double tz, double distance, double height) {
        level.getChunk((int) Math.floor(tx) >> 4, (int) Math.floor(tz) >> 4);
        double cx = tx;
        double cz = tz + distance;
        double cy = ty + height;
        double pitch = -Math.toDegrees(Math.atan2(ty - cy, distance));
        return new double[]{cx, cy, cz, 180.0, pitch};
    }

    /**
     * Picks a camera position {@code distance} blocks from the target, in the direction (of eight)
     * with the clearest line of sight through the generated world, and aims it at the target. The
     * camera is {@code height} blocks above the local ground ({@code fromGround}) or above the
     * higher of target and local ground. Runs on the server thread; generates chunks as needed.
     */
    private static double[] viewpoint(ServerLevel level, double tx, double ty, double tz, double distance, double height, boolean fromGround) {
        int txi = (int) Math.floor(tx);
        int tzi = (int) Math.floor(tz);
        level.getChunk(txi >> 4, tzi >> 4);
        if (Double.isNaN(ty)) {
            ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, txi, tzi) + 1.5;
        }
        double best = Double.MAX_VALUE;
        double[] result = null;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            double cx = tx + Math.sin(angle) * distance;
            double cz = tz + Math.cos(angle) * distance;
            int cxi = (int) Math.floor(cx);
            int czi = (int) Math.floor(cz);
            level.getChunk(cxi >> 4, czi >> 4);
            double ground = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cxi, czi), level.getSeaLevel());
            double cy = fromGround ? ground + height : Math.max(ground, ty) + height;
            net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(cx, cy, cz);
            net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(tx, ty, tz);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            double blocked = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 0.0 : from.distanceTo(to) - from.distanceTo(hit.getLocation());
            BlockPos eye = BlockPos.containing(from);
            if (!level.getBlockState(eye).getCollisionShape(level, eye).isEmpty()) {
                blocked += 1000.0;
            }
            double score = blocked + i * 0.01;
            if (score < best) {
                best = score;
                double dx = tx - cx;
                double dz = tz - cz;
                double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                double pitch = -Math.toDegrees(Math.atan2(ty - cy, Math.sqrt(dx * dx + dz * dz)));
                result = new double[]{cx, cy, cz, yaw, pitch};
            }
        }
        return result;
    }
}
