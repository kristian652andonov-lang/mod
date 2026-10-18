package com.terrahaul.mixin;

import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Invoker("salt")
    int terrahaul$salt();
}
