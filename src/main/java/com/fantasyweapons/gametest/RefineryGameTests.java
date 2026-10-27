package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.refinery.ArcaneRefineryBlock;
import com.fantasyweapons.refinery.ArcaneRefineryBlockEntity;
import com.fantasyweapons.machine.MachinePart;
import com.fantasyweapons.machine.MachinePartBlock;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Arcane Refinery: building it, refining with any fuel, blaze rods, hoppers and taking it apart. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefineryGameTests {
    private static final String ARENA = "arena";
    private static final BlockPos CORE = new BlockPos(10, 2, 10);

    private RefineryGameTests() {
    }

    /** Builds the refinery (core and five parts) facing north with its core at {@code core}. */
    private static ArcaneRefineryBlockEntity build(GameTestHelper h, BlockPos core) {
        Direction facing = Direction.NORTH;
        h.setBlock(core, ModBlocks.ARCANE_REFINERY.get().defaultBlockState().setValue(ArcaneRefineryBlock.FACING, facing));
        for (MachinePart part : MachinePart.values()) {
            h.getLevel().setBlock(h.absolutePos(part.from(core, facing)), ModBlocks.REFINERY_PART.get().defaultBlockState()
                    .setValue(MachinePartBlock.FACING, facing).setValue(MachinePartBlock.PART, part), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        return (ArcaneRefineryBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(core));
    }

    private static ItemStack raw(Material m, int n) {
        return new ItemStack(ModItems.RAW.get(m).get(), n);
    }

    private static ItemStack additive(Material m, int n) {
        return new ItemStack(m.additive(), n);
    }

    /** Puts {@code batches} batches of {@code m} (eight raw and eight of its additive each) into the refinery. */
    private static void load(ArcaneRefineryBlockEntity be, Material m, int batches) {
        be.items().setStackInSlot(ArcaneRefineryBlockEntity.INPUT, raw(m, Material.REFINE_COUNT * batches));
        be.items().setStackInSlot(ArcaneRefineryBlockEntity.ADDITIVE, additive(m, Material.REFINE_COUNT * batches));
    }

    /** Eight raw Solarite, eight glowstone dust and coal: one core after 400 ticks (20 s), not before. */
    @GameTest(template = ARENA, timeoutTicks = 520)
    public static void refinesInTwentySeconds(GameTestHelper h) {
        ArcaneRefineryBlockEntity be = build(h, CORE);
        load(be, Material.SOLARITE, 1);
        be.items().setStackInSlot(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.COAL));
        h.runAfterDelay(380, () -> {
            if (!be.items().getStackInSlot(ArcaneRefineryBlockEntity.OUTPUT).isEmpty()) h.fail("refined too early");
            if (!be.refining()) h.fail("not refining (lit) while working");
        });
        h.runAfterDelay(420, () -> {
            ItemStack out = be.items().getStackInSlot(ArcaneRefineryBlockEntity.OUTPUT);
            if (out.getItem() != ModItems.REFINED.get(Material.SOLARITE).get() || out.getCount() != 1) h.fail("output after 20 s: " + out);
            else if (!be.items().getStackInSlot(ArcaneRefineryBlockEntity.INPUT).isEmpty()) h.fail("raw not used up");
            else if (!be.items().getStackInSlot(ArcaneRefineryBlockEntity.ADDITIVE).isEmpty()) h.fail("additive not used up");
            else h.succeed();
        });
    }

    /** Seven of either is not enough: nothing refines, and the fire is not lit for it. */
    @GameTest(template = ARENA, timeoutTicks = 460)
    public static void needsEightOfEach(GameTestHelper h) {
        ArcaneRefineryBlockEntity few = build(h, CORE), wrong = build(h, CORE.offset(5, 0, 0)), none = build(h, CORE.offset(-5, 0, 0));
        few.items().setStackInSlot(ArcaneRefineryBlockEntity.INPUT, raw(Material.INFERNITE, 7));
        few.items().setStackInSlot(ArcaneRefineryBlockEntity.ADDITIVE, additive(Material.INFERNITE, 8));
        // eight of each, but another material's additive
        wrong.items().setStackInSlot(ArcaneRefineryBlockEntity.INPUT, raw(Material.INFERNITE, 8));
        wrong.items().setStackInSlot(ArcaneRefineryBlockEntity.ADDITIVE, additive(Material.FROSTHEART, 8));
        none.items().setStackInSlot(ArcaneRefineryBlockEntity.INPUT, raw(Material.INFERNITE, 8));
        none.items().setStackInSlot(ArcaneRefineryBlockEntity.ADDITIVE, additive(Material.INFERNITE, 7));
        for (ArcaneRefineryBlockEntity be : new ArcaneRefineryBlockEntity[]{few, wrong, none}) {
            be.items().setStackInSlot(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.COAL));
        }
        h.runAfterDelay(420, () -> {
            for (ArcaneRefineryBlockEntity be : new ArcaneRefineryBlockEntity[]{few, wrong, none}) {
                if (!be.items().getStackInSlot(ArcaneRefineryBlockEntity.OUTPUT).isEmpty()) h.fail("refined without eight of each");
                if (be.items().getStackInSlot(ArcaneRefineryBlockEntity.FUEL).getCount() != 1) h.fail("burned fuel with nothing to refine");
            }
            h.succeed();
        });
    }

    /** Blaze rods refine twice as fast. */
    @GameTest(template = ARENA, timeoutTicks = 320)
    public static void blazeRodsAreTwiceAsFast(GameTestHelper h) {
        ArcaneRefineryBlockEntity be = build(h, CORE);
        load(be, Material.VOID_CRYSTAL, 2);
        be.items().setStackInSlot(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.BLAZE_ROD));
        h.runAfterDelay(215, () -> {
            ItemStack out = be.items().getStackInSlot(ArcaneRefineryBlockEntity.OUTPUT);
            if (out.getItem() != ModItems.REFINED.get(Material.VOID_CRYSTAL).get() || out.getCount() != 1) h.fail("after 10 s on a blaze rod: " + out);
            else h.succeed();
        });
    }

    /** A lava bucket's fire is as fast as a blaze rod's; it leaves its bucket, which a hopper below takes out. */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void lavaIsAsFastAsBlazeRods(GameTestHelper h) {
        ArcaneRefineryBlockEntity be = build(h, CORE);
        h.setBlock(CORE.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        load(be, Material.INFERNITE, 2);
        be.items().setStackInSlot(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.LAVA_BUCKET));
        h.runAfterDelay(215, () -> {
            HopperBlockEntity below = (HopperBlockEntity) h.getBlockEntity(CORE.below());
            int refined = 0;
            boolean bucket = false;
            for (int i = 0; i < below.getContainerSize(); i++) {
                ItemStack s = below.getItem(i);
                if (s.getItem() == ModItems.REFINED.get(Material.INFERNITE).get()) refined += s.getCount();
                bucket |= s.is(Items.BUCKET);
            }
            refined += be.items().getStackInSlot(ArcaneRefineryBlockEntity.OUTPUT).getCount();
            if (refined != 1) h.fail("after 10 s on lava: " + refined + " refined");
            else if (!bucket) h.fail("the empty bucket was not taken out below: fuel slot holds " + be.items().getStackInSlot(ArcaneRefineryBlockEntity.FUEL));
            else h.succeed();
        });
    }

    /** Only raw materials go into the crucible, only fuel into the firebox, only additives beside the crucible. */
    @GameTest(template = ARENA)
    public static void onlyRawMaterialsAndFuel(GameTestHelper h) {
        ArcaneRefineryBlockEntity be = build(h, CORE);
        if (be.items().isItemValid(ArcaneRefineryBlockEntity.INPUT, new ItemStack(Items.IRON_ORE))) h.fail("iron ore accepted as input");
        if (be.items().isItemValid(ArcaneRefineryBlockEntity.INPUT, new ItemStack(ModItems.REFINED.get(Material.BLOODSTONE).get()))) h.fail("refined accepted");
        for (Material m : Material.values()) if (!be.items().isItemValid(ArcaneRefineryBlockEntity.INPUT, raw(m, 1))) h.fail(m + " raw refused");
        if (!be.items().isItemValid(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.OAK_LOG))) h.fail("wood refused as fuel");
        if (be.items().isItemValid(ArcaneRefineryBlockEntity.FUEL, new ItemStack(Items.STONE))) h.fail("stone accepted as fuel");
        for (Material m : Material.values()) {
            if (!be.items().isItemValid(ArcaneRefineryBlockEntity.ADDITIVE, additive(m, 1))) h.fail(m + "'s additive refused");
            if (be.items().isItemValid(ArcaneRefineryBlockEntity.INPUT, additive(m, 1))) h.fail(m + "'s additive accepted as raw");
            if (be.items().isItemValid(ArcaneRefineryBlockEntity.FUEL, additive(m, 1))) h.fail(m + "'s additive accepted as fuel");
        }
        if (be.items().isItemValid(ArcaneRefineryBlockEntity.ADDITIVE, new ItemStack(Items.STONE))) h.fail("stone accepted as an additive");
        if (be.items().isItemValid(ArcaneRefineryBlockEntity.ADDITIVE, raw(Material.SOLARITE, 1))) h.fail("raw accepted as an additive");
        h.succeed();
    }

    /** A hopper above feeds raw material, one at the side feeds fuel and the additive, one below takes the refined material away. */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void hoppersFeedAndEmptyIt(GameTestHelper h) {
        build(h, CORE);
        BlockPos top = MachinePart.TOP.from(CORE, Direction.NORTH);
        BlockPos right = MachinePart.RIGHT.from(CORE, Direction.NORTH);
        // hoppers: above the top block (pointing down), beside the right block (pointing into it), under the core
        h.setBlock(top.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Direction into = Direction.NORTH.getCounterClockWise();
        h.setBlock(right.relative(into.getOpposite()), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, into));
        h.setBlock(CORE.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(top.above())).setItem(0, raw(Material.INFERNITE, Material.REFINE_COUNT));
        HopperBlockEntity side = (HopperBlockEntity) h.getBlockEntity(right.relative(into.getOpposite()));
        side.setItem(0, new ItemStack(Items.COAL));
        side.setItem(1, additive(Material.INFERNITE, Material.REFINE_COUNT));
        h.runAfterDelay(600, () -> {
            HopperBlockEntity below = (HopperBlockEntity) h.getBlockEntity(CORE.below());
            boolean found = false;
            for (int i = 0; i < below.getContainerSize(); i++) found |= below.getItem(i).getItem() == ModItems.REFINED.get(Material.INFERNITE).get();
            if (!found) h.fail("the hopper below did not receive the refined material");
            else h.succeed();
        });
    }

    /** Placing the item builds all six blocks facing the player; there must be room for them. */
    @GameTest(template = ARENA)
    public static void placingBuildsTheWholeMachine(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180); // looking north: the refinery faces back south, towards the player
        ItemStack stack = new ItemStack(ModItems.ARCANE_REFINERY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = h.absolutePos(CORE);
        var result = ModItems.ARCANE_REFINERY.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        if (!result.consumesAction()) {
            h.fail("could not place: " + result);
            return;
        }
        Direction facing = h.getLevel().getBlockState(at).getValue(ArcaneRefineryBlock.FACING);
        if (facing != Direction.SOUTH) h.fail("faces " + facing + ", expected south (towards the player)");
        for (MachinePart part : MachinePart.values()) {
            if (!(h.getLevel().getBlockState(part.from(at, facing)).getBlock() instanceof MachinePartBlock)) h.fail("missing part " + part);
        }
        // no room: a block where the top-left part would go
        BlockPos other = CORE.offset(6, 0, 0);
        h.setBlock(MachinePart.TOP_LEFT.from(other, Direction.SOUTH), Blocks.STONE);
        BlockPos at2 = h.absolutePos(other);
        ItemStack stack2 = new ItemStack(ModItems.ARCANE_REFINERY.get());
        var blocked = ModItems.ARCANE_REFINERY.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack2,
                new BlockHitResult(Vec3.atCenterOf(at2), Direction.UP, at2, false)));
        if (blocked.consumesAction()) h.fail("placed without room");
        h.succeed();
    }

    /** Breaking any one of its blocks takes the whole refinery down. */
    @GameTest(template = ARENA)
    public static void breakingAPartRemovesItAll(GameTestHelper h) {
        build(h, CORE);
        h.destroyBlock(MachinePart.TOP_RIGHT.from(CORE, Direction.NORTH));
        h.runAfterDelay(2, () -> {
            if (!h.getLevel().getBlockState(h.absolutePos(CORE)).isAir()) h.fail("core still there");
            for (MachinePart part : MachinePart.values()) {
                if (!h.getLevel().getBlockState(h.absolutePos(part.from(CORE, Direction.NORTH))).isAir()) h.fail(part + " still there");
            }
            h.succeed();
        });
    }
}
