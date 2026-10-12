package com.fantasyweapons.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Data generation entry point ({@code ./gradlew runData}); output goes to {@code src/generated/resources}. */
public final class FWDataGen {
    private FWDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        gen.addProvider(event.includeClient(), new FWBlockStates(out, files));
        gen.addProvider(event.includeClient(), new FWItemModels(out, files));

        gen.addProvider(event.includeServer(), new LootTableProvider(out, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(FWBlockLoot::new, LootContextParamSets.BLOCK)), lookup));
        FWTags.Blocks blockTags = gen.addProvider(event.includeServer(), new FWTags.Blocks(out, lookup, files));
        gen.addProvider(event.includeServer(), new FWTags.Items(out, lookup, blockTags.contentsGetter(), files));
    }
}
