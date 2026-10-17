package com.aigirlfriend.entity;

import com.aigirlfriend.config.GFClientConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Riding offsets (carried, lap, seats). The client uses the player's own tuning from the client config so custom
 * models line up; the server uses the defaults (passenger positions are visual only).
 */
public final class PoseTuning {
    private PoseTuning() {}

    private static double get(boolean clientSide, java.util.function.Supplier<Double> client, double fallback) {
        if (!clientSide || !FMLEnvironment.dist.isClient()) return fallback;
        try {
            return client.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static double carryForward(boolean c) { return get(c, GFClientConfig.CARRY_FORWARD::get, 0.42); }
    public static double carryHeight(boolean c) { return get(c, GFClientConfig.CARRY_HEIGHT::get, 0.62); }
    public static double carryYaw(boolean c) { return get(c, GFClientConfig.CARRY_YAW::get, 90.0); }
    public static double lapForward(boolean c) { return get(c, GFClientConfig.LAP_FORWARD::get, 0.22); }
    public static double lapHeight(boolean c) { return get(c, GFClientConfig.LAP_HEIGHT::get, 0.05); }
    public static double lapYaw(boolean c) { return get(c, GFClientConfig.LAP_YAW::get, 90.0); }
    public static double seatHeight(boolean c) { return get(c, GFClientConfig.SEAT_HEIGHT::get, 0.70); }
}
