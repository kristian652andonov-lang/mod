package com.fantasyweapons.material;

import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModRecipes;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Infusion: a weapon crafted together with refined cores of its own material comes out one whole level higher for
 * each core. It is the same weapon - its id, abilities, upgrades, form and kills are kept - with the experience and
 * mastery points of the levels gained. No more cores are taken than the weapon has levels left.
 */
public class InfusionRecipe extends CustomRecipe {
    public InfusionRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The weapon and the number of cores in the grid, or null if it isn't a valid infusion. */
    @Nullable
    static Found find(CraftingInput input) {
        ItemStack weapon = ItemStack.EMPTY;
        int cores = 0;
        Material coreMaterial = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof FantasyWeaponItem) {
                if (!weapon.isEmpty()) return null;
                weapon = s;
            } else if (s.getItem() instanceof MaterialItem m && m.refined()) {
                if (coreMaterial != null && coreMaterial != m.material()) return null;
                coreMaterial = m.material();
                cores++;
            } else {
                return null;
            }
        }
        if (weapon.isEmpty() || cores == 0) return null;
        FantasyWeaponItem item = (FantasyWeaponItem) weapon.getItem();
        if (Material.ofWeapon(item.definition().id()) != coreMaterial) return null;
        int level = FantasyWeaponItem.data(weapon).level();
        if (level + cores > ProgressionMath.maxLevel()) return null;
        return new Found(weapon, cores);
    }

    record Found(ItemStack weapon, int cores) {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found f = find(input);
        if (f == null) return ItemStack.EMPTY;
        ItemStack out = f.weapon().copyWithCount(1);
        out.set(ModComponents.WEAPON_DATA.get(), ExpService.withLevels(((FantasyWeaponItem) f.weapon().getItem()).definition(), FantasyWeaponItem.data(f.weapon()), f.cores()));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.INFUSION_SERIALIZER.get();
    }
}
