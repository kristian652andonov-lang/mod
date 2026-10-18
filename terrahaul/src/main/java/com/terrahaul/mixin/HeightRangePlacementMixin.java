package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.VanillaHeightContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps features placed with {@code minecraft:height_range} (ores, geodes, springs, cave
 * decorations) at their vanilla depths in Terrahaul worlds.
 * <p>
 * Vanilla data anchors many of them to the world bottom or top ({@code above_bottom},
 * {@code below_top}). With Terrahaul's Y -512..2031 world those anchors would move diamonds and
 * redstone 448 blocks deeper and stretch coal up to the build limit. When the {@code oreDepthFix}
 * option is on, the height provider is sampled against the vanilla Overworld frame (Y -64..319)
 * instead, so every such feature lands exactly where it would in a vanilla world. Terrahaul adds
 * its own resource layers for the deep and high zones. Other worlds are unaffected.
 */
@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementMixin {
    @WrapOperation(method = "getPositions", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int terrahaul$vanillaFrame(HeightProvider provider, RandomSource random, WorldGenerationContext context, Operation<Integer> original) {
        if (context instanceof PlacementContext placement && TerrahaulConfig.oreDepthFix() && TerrahaulGeneration.isTerrahaul(placement.generator())) {
            return original.call(provider, random, VanillaHeightContext.get(placement.generator()));
        }
        return original.call(provider, random, context);
    }
}
