package com.aigirlfriend;

import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.event.CommonEvents;
import com.aigirlfriend.network.ModNetwork;
import com.aigirlfriend.registry.ModEntities;
import com.aigirlfriend.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(AIGirlfriend.MOD_ID)
public final class AIGirlfriend {
    public static final String MOD_ID = "aigirlfriend";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AIGirlfriend(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(AIGirlfriend::onAttributes);
        modBus.addListener(ModItems::addToTabs);

        container.registerConfig(ModConfig.Type.COMMON, GFConfig.SPEC, MOD_ID + "-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, GFClientConfig.SPEC, MOD_ID + "-client.toml");

        NeoForge.EVENT_BUS.register(new CommonEvents());
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GIRLFRIEND.get(), GirlfriendEntity.createAttributes().build());
    }
}
