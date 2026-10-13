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
        // the Legendary Crafting Table's recipes are shaped patterns up to 9x9
        net.minecraft.world.item.crafting.ShapedRecipePattern.setCraftingSize(com.fantasyweapons.forge.ForgeRecipe.SIZE, com.fantasyweapons.forge.ForgeRecipe.SIZE);
        ModComponents.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModAttributes.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModArmorMaterials.REGISTER.register(modBus);
        modBus.addListener(com.fantasyweapons.registry.ModAttributes::addToPlayers);
        com.fantasyweapons.registry.ModBlocks.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModBlockEntities.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        com.fantasyweapons.advancement.ModTriggers.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModMenus.REGISTER.register(modBus);
        com.fantasyweapons.registry.ModRecipes.TYPES.register(modBus);
        com.fantasyweapons.registry.ModRecipes.SERIALIZERS.register(modBus);
        modBus.addListener(FantasyWeapons::registerCapabilities);
        com.fantasyweapons.registry.ModEntities.REGISTER.register(modBus);
        ModTabs.REGISTER.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(FWNetwork::register);
        modBus.addListener(com.fantasyweapons.datagen.FWDataGen::gather);
        ModEvents.register(NeoForge.EVENT_BUS);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.fantasyweapons.client.ClientSetup.init(modBus);
        }
    }

    /** Hoppers reach the refinery's inventory through any of its six blocks (top: input, sides: fuel, bottom: output). */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        var itemHandler = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        event.registerBlockEntity(itemHandler, com.fantasyweapons.registry.ModBlockEntities.ARCANE_REFINERY.get(), (be, side) -> be.handler(side));
        event.registerBlock(itemHandler, (level, pos, state, be, side) -> {
            var core = com.fantasyweapons.machine.MachinePartBlock.core(state, pos);
            return level.getBlockEntity(core) instanceof com.fantasyweapons.refinery.ArcaneRefineryBlockEntity refinery ? refinery.handler(side) : null;
        }, com.fantasyweapons.registry.ModBlocks.REFINERY_PART.get());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
