package com.terrahaul.client;

import com.terrahaul.Terrahaul;
import com.terrahaul.client.dev.ClientScreenshotTest;
import com.terrahaul.client.particle.AmbientParticleSpawner;
import com.terrahaul.client.particle.TerrahaulParticleProviders;
import com.terrahaul.client.render.TerrahaulColors;
import com.terrahaul.client.render.TerrahaulSkyEffects;
import com.terrahaul.client.sway.SwayModels;
import com.terrahaul.client.sway.SwayShader;
import com.terrahaul.client.water.WaterEffects;
import com.terrahaul.client.wind.WindSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Client-only entry point. Nothing here is loaded on a dedicated server.
 */
@Mod(value = Terrahaul.MOD_ID, dist = Dist.CLIENT)
public final class TerrahaulClient {
    public TerrahaulClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(TerrahaulColors::blockColors);
        modBus.addListener(TerrahaulColors::itemColors);
        modBus.addListener(TerrahaulParticleProviders::register);
        modBus.addListener(SwayModels::modifyBakingResult);
        modBus.addListener(TerrahaulSkyEffects::register);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            WindSystem.tick(mc);
            AmbientParticleSpawner.tick(mc);
            WaterEffects.tick(mc);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            WindSystem.reset();
            WaterEffects.reset();
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> WaterEffects.onChunkChanged(event));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> WaterEffects.onChunkChanged(event));
        NeoForge.EVENT_BUS.addListener(SwayShader::onRenderStage);
        NeoForge.EVENT_BUS.addListener(WorldTypeDefault::onScreenInit);
        NeoForge.EVENT_BUS.addListener(MemoryHint::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientScreenshotTest::onClientTick);
    }
}
