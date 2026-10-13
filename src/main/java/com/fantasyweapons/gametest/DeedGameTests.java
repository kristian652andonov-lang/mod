package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.advancement.Deeds;
import com.fantasyweapons.advancement.ModTriggers;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The mod's deeds (advancements): their own tab, earned by the mod's triggers, announced in the mod's way. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeedGameTests {
    private static final String ARENA = "arena";

    private DeedGameTests() {
    }

    private static AdvancementHolder deed(GameTestHelper h, String name) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(FantasyWeapons.id(name));
        if (holder == null) throw new IllegalStateException("no deed " + name);
        return holder;
    }

    private static boolean done(ServerPlayer p, AdvancementHolder holder) {
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** At least fifty deeds, in one tree with its own root and background, none using vanilla's toast or chat line. */
    @GameTest(template = ARENA)
    public static void theDeedsHaveATabOfTheirOwn(GameTestHelper h) {
        int count = 0;
        for (AdvancementHolder holder : h.getLevel().getServer().getAdvancements().getAllAdvancements()) {
            if (!Deeds.ours(holder) || holder.value().display().isEmpty()) continue;
            count++;
            DisplayInfo display = holder.value().display().get();
            if (display.shouldShowToast() || display.shouldAnnounceChat()) h.fail(holder.id() + " uses vanilla's announcement");
            if (display.getTitle().getStyle().getColor() == null) h.fail(holder.id() + " has no colour of its own");
            if (holder.id().getPath().equals("root")) {
                if (holder.value().parent().isPresent()) h.fail("the root has a parent");
                if (display.getBackground().isEmpty()) h.fail("the tab has no background");
            } else if (holder.value().parent().map(p -> !p.getNamespace().equals(FantasyWeapons.MOD_ID)).orElse(true)) {
                h.fail(holder.id() + " is outside the tab");
            }
        }
        if (count < 50) h.fail("only " + count + " deeds");
        h.succeed();
    }

    /** The mod's triggers earn their deeds: weapon levels and kills, abilities, armor sets, the table and the Codex. */
    @GameTest(template = ARENA)
    public static void theTriggersEarnTheirDeeds(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        AdvancementHolder level10 = deed(h, "level_10"), level30 = deed(h, "level_30"), blood = deed(h, "first_blood"),
                ability = deed(h, "first_ability"), ultimate = deed(h, "ultimate"), set = deed(h, "full_set"), every = deed(h, "every_set"),
                forging = deed(h, "first_forging"), codex = deed(h, "open_the_codex"), infused = deed(h, "infused");
        ModTriggers.WEAPON_MILESTONE.get().trigger(p, "voidfang", 12, 0, 0);
        if (!done(p, level10)) h.fail("level 12 did not earn Blooded");
        if (done(p, level30)) h.fail("level 12 earned Seasoned");
        if (done(p, blood)) h.fail("no kills earned First Blood");
        ModTriggers.WEAPON_MILESTONE.get().trigger(p, "voidfang", 12, 1, 0);
        if (!done(p, blood)) h.fail("a kill did not earn First Blood");
        ModTriggers.ABILITY_CAST.get().trigger(p, "active");
        if (!done(p, ability) || done(p, ultimate)) h.fail("an active ability: " + done(p, ability) + " / ultimate " + done(p, ultimate));
        ModTriggers.ABILITY_CAST.get().trigger(p, "ultimate");
        if (!done(p, ultimate)) h.fail("an ultimate did not earn Cataclysm");
        ModTriggers.FULL_SET.get().trigger(p, ArmorSet.SOLARIS.id());
        if (!done(p, set)) h.fail("a full set did not earn Full Regalia");
        if (done(p, every)) h.fail("one set earned Wardrobe of Legends");
        for (ArmorSet s : ArmorSet.values()) ModTriggers.FULL_SET.get().trigger(p, s.id());
        if (!done(p, every)) h.fail("every set did not earn Wardrobe of Legends");
        ModTriggers.FORGE_STARTED.get().trigger(p, "voidfang");
        ModTriggers.CODEX_OPENED.get().trigger(p, "");
        ModTriggers.INFUSED.get().trigger(p, "voidfang");
        if (!done(p, forging) || !done(p, codex) || !done(p, infused)) h.fail("table / codex / infusion: " + done(p, forging) + done(p, codex) + done(p, infused));
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Holding a weapon earns its deed; the announcement names the player and the deed in its colour. */
    @GameTest(template = ARENA)
    public static void forgingAWeaponIsAnnounced(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        AdvancementHolder voidfang = deed(h, "weapon_voidfang"), reborn = deed(h, "legend_reborn"), thirteen = deed(h, "the_thirteen");
        p.getInventory().add(new ItemStack(ModItems.WEAPONS.get("voidfang").get()));
        p.inventoryMenu.broadcastChanges();
        if (!done(p, voidfang) || !done(p, reborn)) h.fail("holding the Voidfang: " + done(p, voidfang) + " / " + done(p, reborn));
        if (done(p, thirteen)) h.fail("one weapon earned The Thirteen");
        DisplayInfo display = voidfang.value().display().orElseThrow();
        var line = Deeds.announcement(p, display);
        String text = line.getString();
        if (!text.contains(p.getGameProfile().getName()) || !text.contains("The Void Bites") || !text.contains("feat")) h.fail("announcement: " + text);
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
