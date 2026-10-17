package com.aigirlfriend.client;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.client.anim.PlayerAnimationLibrary;
import com.aigirlfriend.client.hud.CompanionHud;
import com.aigirlfriend.client.render.GirlfriendRenderer;
import com.aigirlfriend.network.ClientBridge;
import com.aigirlfriend.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point: renderers, keybinds, HUD, player animations, config screen. */
@Mod(value = AIGirlfriend.MOD_ID, dist = Dist.CLIENT)
public final class AIGirlfriendClient {
    public AIGirlfriendClient(IEventBus modBus, ModContainer container) {
        ClientBridge.Holder.instance = new ClientBridgeImpl();
        modBus.addListener(AIGirlfriendClient::onRenderers);
        modBus.addListener(Keybinds::register);
        modBus.addListener(AIGirlfriendClient::onGuiLayers);
        modBus.addListener(AIGirlfriendClient::onReloadListeners);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new ClientEvents());
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GIRLFRIEND.get(), GirlfriendRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new);
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "companion_hud"), new CompanionHud());
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PlayerAnimationLibrary());
    }
}
