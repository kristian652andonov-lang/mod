package com.terrahaul.client;

import com.terrahaul.Terrahaul;
import com.terrahaul.config.TerrahaulConfig;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Pre-selects the Terrahaul world type when the Create World screen opens (client option
 * {@code selectTerrahaulByDefault}). It only runs once per screen, never overrides a world type the
 * player already picked, and leaves "Re-Create" worlds on their original type.
 */
public final class WorldTypeDefault {
    private static final ResourceKey<WorldPreset> TERRAHAUL = ResourceKey.create(Registries.WORLD_PRESET, Terrahaul.id("terrahaul"));
    private static final Set<CreateWorldScreen> HANDLED = Collections.newSetFromMap(new WeakHashMap<>());

    private WorldTypeDefault() {
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen) || !HANDLED.add(screen)) {
            return;
        }
        if (screen.recreated || !TerrahaulConfig.selectTerrahaulByDefault()) {
            return;
        }
        WorldCreationUiState state = screen.getUiState();
        WorldCreationUiState.WorldTypeEntry current = state.getWorldType();
        if (current.preset() == null || !current.preset().is(WorldPresets.NORMAL)) {
            return;
        }
        for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
            if (entry.preset() != null && entry.preset().is(TERRAHAUL)) {
                state.setWorldType(entry);
                return;
            }
        }
    }
}
