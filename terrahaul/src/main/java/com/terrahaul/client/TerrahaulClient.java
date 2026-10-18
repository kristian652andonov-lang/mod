package com.terrahaul.client;

import com.terrahaul.Terrahaul;
import com.terrahaul.client.dev.ClientScreenshotTest;
import com.terrahaul.client.render.TerrahaulColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point. Nothing here is loaded on a dedicated server.
 */
@Mod(value = Terrahaul.MOD_ID, dist = Dist.CLIENT)
public final class TerrahaulClient {
    public TerrahaulClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(TerrahaulColors::blockColors);
        modBus.addListener(TerrahaulColors::itemColors);
        NeoForge.EVENT_BUS.addListener(ClientScreenshotTest::onClientTick);
    }
}
