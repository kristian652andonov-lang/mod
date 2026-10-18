package com.terrahaul;

import com.mojang.logging.LogUtils;
import com.terrahaul.command.TerrahaulCommand;
import com.terrahaul.dev.AutoTest;
import com.terrahaul.registry.TerrahaulFeatures;
import com.terrahaul.registry.TerrahaulWorldgenTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Terrahaul: an Overworld terrain-generation overhaul for Minecraft 1.21.1 (NeoForge).
 * <p>
 * The terrain itself is data driven: the {@code terrahaul:terrahaul} world preset selects the
 * {@code terrahaul:overworld} dimension type (Y -512..2031) and noise settings, whose noise router
 * is built from the density function types registered here.
 */
@Mod(Terrahaul.MOD_ID)
public final class Terrahaul {
    public static final String MOD_ID = "terrahaul";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Terrahaul(IEventBus modBus, ModContainer container) {
        TerrahaulWorldgenTypes.register(modBus);
        TerrahaulFeatures.register(modBus);
        NeoForge.EVENT_BUS.addListener(AutoTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> TerrahaulCommand.register(e.getDispatcher(), e.getBuildContext()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
