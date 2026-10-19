package com.terrahaul.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The rooms of an ocean monument: the building keeps them in a list of its own and places them
 * itself, so moving the building piece leaves them behind (see OceanMonumentStructureMixin).
 */
@Mixin(OceanMonumentPieces.MonumentBuilding.class)
public interface MonumentBuildingAccessor {
    @Accessor("childPieces")
    List<?> terrahaul$childPieces();
}
