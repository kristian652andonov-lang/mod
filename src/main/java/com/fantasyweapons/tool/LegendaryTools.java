package com.fantasyweapons.tool;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.SimpleTier;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

/**
 * What the three legendary tool items share: their tier, the Fortune and Efficiency they carry from their upgrades (not
 * stored as enchantments: they are worked out from {@link ToolUpgrades} wherever the game asks for an enchantment's level,
 * so they can neither be ground off nor go stale), their tooltip and their idle animation.
 */
public final class LegendaryTools {
    /**
     * Better than netherite: as fast as Efficiency allows on top of a speed of 10, harder-hitting, and it mines everything
     * netherite does. Never wears out (the items are unbreakable); it is not enchanted at a table, its gifts are its own.
     */
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031, 10f, 5f, 0, () -> Ingredient.EMPTY);

    private LegendaryTools() {
    }

    public static ToolUpgrades upgrades(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TOOL_UPGRADES.get(), ToolUpgrades.BASE);
    }

    /** Fortune and Efficiency at least as high as the tool's upgrades, whatever is enchanted on it. */
    public static int enchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment, int enchanted) {
        if (enchantment.is(Enchantments.FORTUNE)) return Math.max(enchanted, upgrades(stack).fortune());
        if (enchantment.is(Enchantments.EFFICIENCY)) return Math.max(enchanted, upgrades(stack).efficiency());
        return enchanted;
    }

    public static ItemEnchantments allEnchantments(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup, ItemEnchantments enchanted) {
        ToolUpgrades u = upgrades(stack);
        ItemEnchantments.Mutable all = new ItemEnchantments.Mutable(enchanted);
        lookup.get(Enchantments.FORTUNE).ifPresent(h -> all.set(h, Math.max(all.getLevel(h), u.fortune())));
        lookup.get(Enchantments.EFFICIENCY).ifPresent(h -> all.set(h, Math.max(all.getLevel(h), u.efficiency())));
        return all.toImmutable();
    }

    /**
     * Books can't add what the tool already has (Fortune, Efficiency) nor Silk Touch, which would undo its Fortune; other
     * enchantments go on at an anvil as on any tool.
     */
    public static boolean supports(Holder<Enchantment> enchantment) {
        return !enchantment.is(Enchantments.FORTUNE) && !enchantment.is(Enchantments.EFFICIENCY) && !enchantment.is(Enchantments.SILK_TOUCH);
    }

    public static void controllers(LegendaryTool tool, AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop("animation." + tool.asset() + ".idle");
        controllers.add(new AnimationController<>(tool, "idle", 0, state -> state.setAndContinue(idle)));
    }

    public static void tooltip(LegendaryTool tool, ItemStack stack, List<Component> tooltip) {
        Material m = tool.material();
        int color = m.color(), light = mix(color, 0xFFFFFF, 0.45f), faded = mix(color, 0x9C8862, 0.55f), stat = 0xC8D2DC;
        ToolUpgrades u = upgrades(stack);
        tooltip.add(Component.literal(Tr.f("Legendary %s of %s", tool.kind().displayName().toLowerCase(java.util.Locale.ROOT), m.materialName()))
                .withStyle(s -> s.withColor(color)));
        tooltip.add(Component.empty());
        if (tool.kind() == ToolKind.AXE) tooltip.add(Component.literal("⛏ " + Tr.s("Fells a whole tree at a stroke")).withStyle(s -> s.withColor(stat)));
        for (UpgradeTrack t : UpgradeTrack.values()) {
            String icon = t == UpgradeTrack.AREA ? "⛏ " : "✦ ";
            String label = t == UpgradeTrack.AREA && tool.kind() == ToolKind.AXE ? Tr.s("Other Wood") : t.displayName();
            tooltip.add(Component.literal(icon + label + "  " + t.describe(u.level(t)) + "  ")
                    .withStyle(s -> s.withColor(stat))
                    .append(Component.literal(pips(u.level(t) - t.min() + 1, t.max() - t.min() + 1)).withStyle(s -> s.withColor(u.maxed(t) ? 0xFFE7A8 : light))));
        }
        tooltip.add(Component.literal(Tr.s("Sneak to dig a single block")).withStyle(s -> s.withColor(faded).withItalic(true)));
        tooltip.add(Component.empty());
        // its power, on the ability key
        String key = com.fantasyweapons.SidedHooks.get().abilityKeyName();
        tooltip.add(Component.literal("[" + key + "] " + ToolAbilities.name(tool.kind())).withStyle(s -> s.withColor(light).withBold(true))
                .append(Component.literal("  " + Tr.f("rests %ss", ToolAbilities.cooldown(tool.kind()) / 20)).withStyle(s -> s.withColor(faded).withBold(false))));
        for (String line : wrap(ToolAbilities.describe(tool.kind(), u), 40)) {
            tooltip.add(Component.literal("  " + line).withStyle(s -> s.withColor(stat)));
        }
        tooltip.add(Component.empty());
        if (u.maxed()) {
            tooltip.add(Component.literal("★ " + Tr.s("Fully upgraded")).withStyle(s -> s.withColor(0xFFE7A8).withBold(true)));
        } else {
            tooltip.add(Component.literal("⚒ " + Tr.s("Upgrade at the Legendary Upgrading Table")).withStyle(s -> s.withColor(light)));
            tooltip.add(Component.literal("   " + Tr.f("with %s and experience", m.refinedName())).withStyle(s -> s.withColor(faded)));
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private static String pips(int filled, int total) {
        return "◆".repeat(Math.max(0, filled)) + "◇".repeat(Math.max(0, total - filled));
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
