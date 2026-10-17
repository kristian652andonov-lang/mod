package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.machine.MachineCoreBlock;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.tool.LegendaryTools;
import com.fantasyweapons.tool.ToolKind;
import com.fantasyweapons.tool.ToolUpgrades;
import com.fantasyweapons.tool.UpgradeTrack;
import com.fantasyweapons.tool.UpgradingTableBlock;
import com.fantasyweapons.tool.UpgradingTableBlockEntity;
import com.fantasyweapons.tool.UpgradingTableBlockEntity.Check;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Legendary Upgrading Table: what it takes, what it costs, how long it works, and what it gives back. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UpgradingTableGameTests {
    private static final String ARENA = "arena";
    private static final BlockPos CORE = new BlockPos(12, 2, 12);
    private static final Material MATERIAL = Material.VOID_CRYSTAL;

    private UpgradingTableGameTests() {
    }

    /** The table, whole (its core and the block above), facing north. */
    private static UpgradingTableBlockEntity table(GameTestHelper h) {
        BlockPos core = h.absolutePos(CORE);
        h.getLevel().setBlock(core, ModBlocks.UPGRADING_TABLE.get().defaultBlockState().setValue(MachineCoreBlock.FACING, Direction.NORTH), 3);
        ModBlocks.UPGRADING_TABLE.get().placeParts(h.getLevel(), core, Direction.NORTH);
        return (UpgradingTableBlockEntity) h.getLevel().getBlockEntity(core);
    }

    private static ItemStack tool(ToolUpgrades upgrades) {
        ItemStack stack = new ItemStack(ModItems.tool(MATERIAL, ToolKind.PICKAXE));
        stack.set(ModComponents.TOOL_UPGRADES.get(), upgrades);
        return stack;
    }

    private static ItemStack cores(Material m, int n) {
        return new ItemStack(ModItems.REFINED.get(m).get(), n);
    }

    private static ServerPlayer player(GameTestHelper h, int levels) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 10.5), 0);
        p.setExperienceLevels(levels);
        return p;
    }

    /** A step is paid for at once (cores from the cup, levels from the player), and raises the tool when the table is done. */
    @GameTest(template = ARENA, timeoutTicks = UpgradingTableBlockEntity.DURATION + 60)
    public static void anUpgradeTakesItsCostAndRaisesTheToolWhenDone(GameTestHelper h) {
        UpgradingTableBlockEntity be = table(h);
        ServerPlayer p = player(h, 12);
        be.items().setStackInSlot(UpgradingTableBlockEntity.TOOL, tool(ToolUpgrades.BASE));
        be.items().setStackInSlot(UpgradingTableBlockEntity.CATALYST, cores(MATERIAL, 5));
        int cost = UpgradeTrack.AREA.cores(1), levels = UpgradeTrack.AREA.levels(1);
        Check c = be.start(p, UpgradeTrack.AREA);
        h.assertTrue(c == Check.READY, "the first step of the area should start, got " + c);
        h.assertTrue(be.items().getStackInSlot(UpgradingTableBlockEntity.CATALYST).getCount() == 5 - cost,
                "it should take " + cost + " cores, left " + be.items().getStackInSlot(UpgradingTableBlockEntity.CATALYST).getCount());
        h.assertTrue(p.experienceLevel == 12 - levels, "it should take " + levels + " levels, left " + p.experienceLevel);
        h.assertTrue(be.working() && h.getBlockState(CORE).getValue(UpgradingTableBlock.WORKING), "the table should be at work");
        h.assertTrue(be.start(p, UpgradeTrack.FORTUNE) == Check.BUSY, "a working table takes no second order");
        h.assertTrue(LegendaryTools.upgrades(be.items().getStackInSlot(UpgradingTableBlockEntity.TOOL)).reach() == 1,
                "the tool is raised only when the table is done");
        h.runAfterDelay(UpgradingTableBlockEntity.DURATION + 2, () -> {
            ToolUpgrades u = LegendaryTools.upgrades(be.items().getStackInSlot(UpgradingTableBlockEntity.TOOL));
            h.assertTrue(u.reach() == 2 && u.fortune() == 3 && u.efficiency() == 5, "after the work: 5x5, Fortune III, Efficiency V, got " + u);
            h.assertTrue(!be.working() && !h.getBlockState(CORE).getValue(UpgradingTableBlock.WORKING), "the table should be idle again");
            h.succeed();
        });
    }

    /** What the table turns away, and why. */
    @GameTest(template = ARENA)
    public static void theTableTurnsAwayWhatItCannotUse(GameTestHelper h) {
        ServerPlayer p = player(h, 100);
        ItemStack tool = tool(ToolUpgrades.BASE);
        h.assertTrue(UpgradingTableBlockEntity.check(new ItemStack(Items.DIAMOND_PICKAXE), cores(MATERIAL, 9), UpgradeTrack.AREA, p) == Check.NO_TOOL,
                "a common pickaxe is no legendary tool");
        h.assertTrue(UpgradingTableBlockEntity.check(tool, ItemStack.EMPTY, UpgradeTrack.AREA, p) == Check.NO_CATALYST, "an empty cup");
        h.assertTrue(UpgradingTableBlockEntity.check(tool, cores(Material.SOLARITE, 9), UpgradeTrack.AREA, p) == Check.WRONG_CATALYST,
                "another material's cores");
        h.assertTrue(UpgradingTableBlockEntity.check(tool, cores(MATERIAL, 1), UpgradeTrack.AREA, p) == Check.FEW_CORES,
                "one core where the area takes " + UpgradeTrack.AREA.cores(1));
        h.assertTrue(UpgradingTableBlockEntity.check(tool, cores(MATERIAL, 1), UpgradeTrack.FORTUNE, p) == Check.READY,
                "one core is enough for Fortune IV");
        h.assertTrue(UpgradingTableBlockEntity.check(tool(ToolUpgrades.MAX), cores(MATERIAL, 9), UpgradeTrack.EFFICIENCY, p) == Check.MAXED,
                "Efficiency X is the highest");
        p.setExperienceLevels(4);
        h.assertTrue(UpgradingTableBlockEntity.check(tool, cores(MATERIAL, 9), UpgradeTrack.FORTUNE, p) == Check.FEW_LEVELS,
                "4 levels where Fortune IV takes " + UpgradeTrack.FORTUNE.levels(3));
        // the slots themselves: the vise holds only a legendary tool, the cup only a refined material
        h.assertTrue(UpgradingTableBlockEntity.accepts(UpgradingTableBlockEntity.TOOL, tool), "the vise takes a legendary tool");
        h.assertTrue(!UpgradingTableBlockEntity.accepts(UpgradingTableBlockEntity.TOOL, new ItemStack(Items.NETHERITE_PICKAXE)), "the vise refuses a common tool");
        h.assertTrue(UpgradingTableBlockEntity.accepts(UpgradingTableBlockEntity.CATALYST, cores(Material.SOLARITE, 1)), "the cup takes a refined material");
        h.assertTrue(!UpgradingTableBlockEntity.accepts(UpgradingTableBlockEntity.CATALYST, new ItemStack(ModItems.RAW.get(MATERIAL).get())),
                "the cup refuses raw ore");
        h.succeed();
    }

    /** Each step costs more, and a fully raised tool digs 13x13 with Fortune X and Efficiency X. */
    @GameTest(template = ARENA)
    public static void everyStepCanBeBoughtUpToTheHighest(GameTestHelper h) {
        ServerPlayer p = player(h, 0);
        p.getAbilities().instabuild = true;
        ItemStack tool = tool(ToolUpgrades.BASE);
        int total = 0;
        for (UpgradeTrack t : UpgradeTrack.values()) {
            int last = 0;
            while (!LegendaryTools.upgrades(tool).maxed(t)) {
                int level = LegendaryTools.upgrades(tool).level(t);
                h.assertTrue(t.cores(level) >= last, t + ": a step should cost no fewer cores than the one before");
                last = t.cores(level);
                total += last;
                h.assertTrue(UpgradingTableBlockEntity.check(tool, cores(MATERIAL, 64), t, p) == Check.READY, t + " " + level + " should be ready");
                ToolUpgrades u = LegendaryTools.upgrades(tool);
                tool.set(ModComponents.TOOL_UPGRADES.get(), u.with(t, level + 1));
            }
        }
        ToolUpgrades u = LegendaryTools.upgrades(tool);
        h.assertTrue(u.equals(ToolUpgrades.MAX) && u.size() == 13 && u.fortune() == 10 && u.efficiency() == 10, "fully raised: " + u);
        h.assertTrue(total > 40 && total < 80, "raising everything should take some dozens of cores, took " + total);
        h.succeed();
    }

    /** A table broken while it works finishes the step (it was paid for) and drops the raised tool and the cup's cores. */
    @GameTest(template = ARENA)
    public static void breakingAWorkingTableFinishesTheStepAndDropsItAll(GameTestHelper h) {
        UpgradingTableBlockEntity be = table(h);
        ServerPlayer p = player(h, 30);
        be.items().setStackInSlot(UpgradingTableBlockEntity.TOOL, tool(ToolUpgrades.BASE));
        be.items().setStackInSlot(UpgradingTableBlockEntity.CATALYST, cores(MATERIAL, 3));
        h.assertTrue(be.start(p, UpgradeTrack.EFFICIENCY) == Check.READY, "Efficiency VI should start");
        h.getLevel().destroyBlock(h.absolutePos(CORE), true);
        h.assertBlockNotPresent(ModBlocks.UPGRADING_TABLE.get(), CORE);
        h.assertBlockNotPresent(ModBlocks.UPGRADING_TABLE_PART.get(), CORE.above());
        ItemStack dropped = ItemStack.EMPTY;
        int left = 0;
        boolean tableDropped = false;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(1))) {
            ItemStack s = e.getItem();
            if (s.getItem() == ModItems.tool(MATERIAL, ToolKind.PICKAXE)) dropped = s;
            else if (s.is(ModItems.REFINED.get(MATERIAL).get())) left += s.getCount();
            else if (s.is(ModItems.UPGRADING_TABLE.get())) tableDropped = true;
        }
        h.assertTrue(!dropped.isEmpty() && LegendaryTools.upgrades(dropped).efficiency() == 6, "the dropped tool should have Efficiency VI");
        h.assertTrue(left == 3 - UpgradeTrack.EFFICIENCY.cores(5), "the cores left in the cup should drop, found " + left);
        h.assertTrue(tableDropped, "the table should drop itself");
        h.succeed();
    }
}
