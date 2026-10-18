package com.terrahaul;

import com.mojang.logging.LogUtils;
import com.terrahaul.command.TerrahaulCommand;
import com.terrahaul.config.ConfigCondition;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.dev.AutoTest;
import com.terrahaul.registry.TerrahaulBlocks;
import com.terrahaul.registry.TerrahaulFeatures;
import com.terrahaul.registry.TerrahaulPlacements;
import com.terrahaul.registry.TerrahaulWorldgenTypes;
import com.terrahaul.worldgen.trees.SaplingGrowthHandler;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

/**
 * Terrahaul: an Overworld terrain-generation overhaul for Minecraft 1.21.1 (NeoForge).
 * <p>
 * The terrain itself is data driven: the {@code terrahaul:terrahaul} world preset selects the
 * {@code terrahaul:overworld} dimension type (Y -512..2031) and noise settings, whose noise router
 * is built from the density function types registered here. Trees, ground details and formations
 * are features added to biomes by NeoForge biome modifiers in the mod's data pack.
 */
@Mod(Terrahaul.MOD_ID)
public final class Terrahaul {
    public static final String MOD_ID = "terrahaul";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MOD_ID);

    static {
        CONDITIONS.register("config", () -> ConfigCondition.CODEC);
    }

    public Terrahaul(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TerrahaulConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, TerrahaulConfig.CLIENT_SPEC);
        TerrahaulBlocks.register(modBus);
        TerrahaulWorldgenTypes.register(modBus);
        TerrahaulFeatures.register(modBus);
        TerrahaulPlacements.register(modBus);
        CONDITIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(AutoTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TerrahaulCommand.register(e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(SaplingGrowthHandler::onGrow);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
