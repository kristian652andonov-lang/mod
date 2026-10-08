package com.fantasyweapons;

import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.FWNetwork;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModTabs;
import com.fantasyweapons.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FantasyWeapons.MOD_ID)
public final class FantasyWeapons {
    public static final String MOD_ID = "fantasyweapons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FantasyWeapons(IEventBus modBus, ModContainer container) {
        ModComponents.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModEntities.REGISTER.register(modBus);
        ModTabs.REGISTER.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(FWNetwork::register);
        ModEvents.register(NeoForge.EVENT_BUS);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.fantasyweapons.client.ClientSetup.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
