package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.material.MaterialOreBlockEntity;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The legendary materials: ore blocks, what they drop and what it takes to mine them. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialGameTests {
    private static final String ARENA = "arena";

    private MaterialGameTests() {
    }

    private static ItemStack tool(ServerLevel level, Item item, ResourceKey<Enchantment> enchantment, int lvl) {
        ItemStack stack = new ItemStack(item);
        if (enchantment != null) stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), lvl);
        return stack;
    }

    private static List<ItemStack> drops(GameTestHelper h, BlockPos rel, ItemStack tool) {
        BlockPos abs = h.absolutePos(rel);
        BlockState state = h.getLevel().getBlockState(abs);
        return Block.getDrops(state, h.getLevel(), abs, h.getLevel().getBlockEntity(abs), null, tool);
    }

    /** Every ore needs a diamond pickaxe; it drops exactly one raw material, the ore itself with Silk Touch. */
    /**
     * Ores written straight into a chunk section, as the vanilla ore feature does, have no block entity (and so nothing
     * to draw them); loading the chunk gives each one its block entity.
     */
    @GameTest(template = ARENA)
    public static void generatedOresGetTheirBlockEntity(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
        var chunk = level.getChunkAt(pos);
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        BlockState ore = ModBlocks.ORES.get(Material.SOLARITE).get().defaultBlockState();
        section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, ore);
        if (chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) != null) {
            h.fail("an ore written into the section already had a block entity");
        }
        com.fantasyweapons.material.OreBlockEntities.onChunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        if (!(chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) instanceof MaterialOreBlockEntity)) {
            h.fail("loading the chunk did not give the generated ore its block entity");
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void oresDropTheirRawMaterial(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = new BlockPos(5, 2, 5);
        for (Material m : Material.values()) {
            h.setBlock(at, ModBlocks.ORES.get(m).get());
            BlockState state = h.getBlockState(at);
            if (!(level.getBlockEntity(h.absolutePos(at)) instanceof MaterialOreBlockEntity be) || be.material() != m) {
                h.fail(m + ": no ore block entity");
                return;
            }
            if (new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state)) h.fail(m + ": an iron pickaxe can mine it");
            if (!new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state)) h.fail(m + ": a diamond pickaxe cannot mine it");
            if (state.getLightEmission(level, h.absolutePos(at)) <= 0) h.fail(m + ": does not glow");
            List<ItemStack> plain = drops(h, at, new ItemStack(Items.DIAMOND_PICKAXE));
            if (plain.size() != 1 || plain.get(0).getItem() != ModItems.RAW.get(m).get() || plain.get(0).getCount() != 1) {
                h.fail(m + ": diamond pickaxe drops " + plain);
            }
            List<ItemStack> silk = drops(h, at, tool(level, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1));
            if (silk.size() != 1 || silk.get(0).getItem() != ModItems.ORES.get(m).get()) h.fail(m + ": silk touch drops " + silk);
        }
        h.succeed();
    }

    /** Fortune adds up to two more raw materials, never more. */
    @GameTest(template = ARENA)
    public static void fortuneAddsUpToTwo(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 2, 5);
        h.setBlock(at, ModBlocks.ORES.get(Material.TITANSTONE).get());
        ItemStack pick = tool(h.getLevel(), Items.NETHERITE_PICKAXE, Enchantments.FORTUNE, 3);
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < 200; i++) {
            int n = drops(h, at, pick).stream().mapToInt(ItemStack::getCount).sum();
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        if (min != 1 || max != 3) h.fail("Fortune III drops between " + min + " and " + max + ", expected 1 to 3");
        else h.succeed();
    }
}
