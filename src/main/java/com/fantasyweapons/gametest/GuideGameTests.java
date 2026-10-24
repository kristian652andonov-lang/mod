package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.guide.CodexGift;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Wayfarer's Codex: every player is given one on first joining, and a lost one can be crafted again. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private static final String ARENA = "arena";

    private GuideGameTests() {
    }

    /** Joining for the first time gives one Codex; joining again gives no more. */
    @GameTest(template = ARENA)
    public static void newPlayersAreGivenTheCodex(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        CodexGift.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        CodexGift.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        int books = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.CODEX.get())) books += p.getInventory().getItem(i).getCount();
        }
        if (books != 1) h.fail("given " + books + " codices");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A lost Codex is written again from a book, a gold ingot and an amethyst shard. */
    @GameTest(template = ARENA)
    public static void theCodexCanBeCrafted(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().byKey(FantasyWeapons.id("wayfarers_codex")).orElse(null);
        if (recipe == null) {
            h.fail("no recipe for the codex");
            return;
        }
        if (!recipe.value().getResultItem(h.getLevel().registryAccess()).is(ModItems.CODEX.get())) h.fail("the recipe makes something else");
        h.succeed();
    }
}
