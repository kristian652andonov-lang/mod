package com.fantasyweapons.client.fx;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Routes server effect requests to the client-side effect builders. */
public final class FxDispatcher {
    private static final Map<ResourceLocation, Consumer<FxPayload>> HANDLERS = new HashMap<>();

    private FxDispatcher() {
    }

    public static void register(ResourceLocation id, Consumer<FxPayload> handler) {
        HANDLERS.put(id, handler);
    }

    public static void dispatch(FxPayload payload) {
        Consumer<FxPayload> h = HANDLERS.get(payload.fx());
        if (h == null) {
            FantasyWeapons.LOGGER.debug("No client effect registered for {}", payload.fx());
            return;
        }
        try {
            h.accept(payload);
        } catch (RuntimeException e) {
            FantasyWeapons.LOGGER.error("Effect {} failed", payload.fx(), e);
        }
    }

    public static void init() {
        GenericFx.register();
        VoidfangFx.register();
        SolarisFx.register();
        FrostrendFx.register();
        DoomcleaverFx.register();
        StormbreakerFx.register();
        GravebiteFx.register();
        SoulreaperFx.register();
        BloomfallFx.register();
        EclipseFx.register();
    }
}
