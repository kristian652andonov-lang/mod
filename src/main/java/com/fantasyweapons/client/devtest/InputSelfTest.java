package com.fantasyweapons.client.devtest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.input.KeyBindings;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.weapon.Weapons;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * DEVELOPMENT ONLY ({@code -Dfantasyweapons.devtest=inputs}). Plays every ability of every weapon through the real
 * input path — physical key events for the form (F), cycle (G) and ability (R) bindings go through Minecraft's
 * keyboard handler exactly like a player's — and checks that each one selects, charges, casts and then refuses to
 * charge again while on cooldown. Writes {@code fw_input_report.txt} and quits.
 */
final class InputSelfTest {
    private interface Step {
        /** @return ticks to wait before the next step */
        int run(Minecraft mc);
    }

    private static final Deque<Step> QUEUE = new ArrayDeque<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int wait;
    private static int inWorld;
    private static int failures;

    private InputSelfTest() {
    }

    static void start() {
        for (WeaponDefinition def : Weapons.all()) planWeapon(def);
        QUEUE.add(mc -> {
            REPORT.add(0, failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
            try {
                Files.write(mc.gameDirectory.toPath().resolve("fw_input_report.txt"), REPORT);
            } catch (IOException e) {
                FantasyWeapons.LOGGER.error("could not write input report", e);
            }
            REPORT.forEach(l -> FantasyWeapons.LOGGER.warn("[input-test] {}", l));
            mc.stop();
            return 1;
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (++inWorld < 60 || QUEUE.isEmpty()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = QUEUE.pollFirst();
        try {
            wait = s.run(mc);
        } catch (RuntimeException ex) {
            fail("step crashed: " + ex);
            FantasyWeapons.LOGGER.error("input test step failed", ex);
        }
    }

    private static void pass(String msg) {
        REPORT.add("PASS " + msg);
    }

    private static void fail(String msg) {
        failures++;
        REPORT.add("FAIL " + msg);
    }

    // ---- input & world helpers ----

    private static void key(Minecraft mc, KeyMapping mapping, boolean down) {
        InputConstants.Key k = mapping.getKey();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), k.getValue(), 0, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    private static void tap(Minecraft mc, KeyMapping mapping) {
        key(mc, mapping, true);
        key(mc, mapping, false);
    }

    private static void cmd(Minecraft mc, String command) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
        });
    }

    private static ItemStack held(Minecraft mc) {
        return mc.player.getMainHandItem();
    }

    // ---- plan ----

    private static void planWeapon(WeaponDefinition def) {
        QUEUE.add(mc -> {
            cmd(mc, "/kill @e[type=!player]");
            cmd(mc, "/clear @s");
            cmd(mc, "/effect give @s minecraft:resistance 99999 255 true");
            cmd(mc, "/fw give " + def.id() + " 100");
            mc.player.getInventory().selected = 0;
            return 15;
        });
        QUEUE.add(mc -> {
            if (!(held(mc).getItem() instanceof FantasyWeaponItem item) || item.definition() != def) fail(def.id() + ": weapon not in hand");
            return 1;
        });
        for (AbilityDefinition a : def.castables()) planAbility(def, a);
        for (AbilityDefinition a : def.castables()) if (a.requiredForm() != null) planAutoTransform(def, a);
    }

    /** Cycling (G) onto an ability that needs the other form must transform the weapon by itself. */
    private static void planAutoTransform(WeaponDefinition def, AbilityDefinition a) {
        String key = def.id() + "/" + a.id();
        QUEUE.add(mc -> {
            WeaponForm f = def.form(FantasyWeaponItem.data(held(mc)));
            if (f != null && f.id().equals(a.requiredForm())) tap(mc, KeyBindings.FORM); // leave the required form first
            return 30;
        });
        int[] tries = {0};
        QUEUE.add(new Step() {
            @Override
            public int run(Minecraft mc) {
                var data = FantasyWeaponItem.data(held(mc));
                WeaponForm f = def.form(data);
                if (AbilityService.selected(def, data) == a && f != null && f.id().equals(a.requiredForm())) {
                    pass(key + ": cycling onto it transformed the weapon to " + f.id());
                    return 1;
                }
                if (tries[0]++ > def.castables().size() + 2) {
                    fail(key + ": cycling never selected it / never transformed (form " + (f == null ? "-" : f.id()) + ")");
                    return 1;
                }
                tap(mc, KeyBindings.CYCLE);
                QUEUE.addFirst(this);
                return 25;
            }
        });
    }

    private static void planAbility(WeaponDefinition def, AbilityDefinition a) {
        String key = def.id() + "/" + a.id();
        QUEUE.add(mc -> {
            cmd(mc, "/kill @e[type=!player]");
            cmd(mc, "/tp @s 0 -60 0 0 8");
            cmd(mc, "/summon minecraft:husk 0 -60 5 {NoAI:1b,Health:1000000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000000d}]}");
            cmd(mc, "/fw cooldowns");
            return 6;
        });
        // switch form with the real F key if the ability needs another form
        int[] formTries = {0};
        Step formStep = new Step() {
            @Override
            public int run(Minecraft mc) {
                if (a.requiredForm() == null) return 1;
                ItemStack s = held(mc);
                WeaponForm f = def.form(FantasyWeaponItem.data(s));
                if (f != null && f.id().equals(a.requiredForm())) {
                    pass(key + ": switched to form " + f.id() + " with " + KeyBindings.FORM.getTranslatedKeyMessage().getString());
                    return 1;
                }
                if (formTries[0]++ >= 3) {
                    fail(key + ": form key did not switch to " + a.requiredForm());
                    return 1;
                }
                tap(mc, KeyBindings.FORM);
                QUEUE.addFirst(this);
                return 30;
            }
        };
        QUEUE.add(formStep);
        // select it with the real cycle key
        int[] tries = {0};
        Step selectStep = new Step() {
            @Override
            public int run(Minecraft mc) {
                ItemStack s = held(mc);
                AbilityDefinition sel = AbilityService.selected(def, FantasyWeaponItem.data(s));
                if (sel == a) return 1;
                if (tries[0]++ >= def.castables().size() + 2) {
                    fail(key + ": cycle key never selected it (selected " + (sel == null ? "none" : sel.id()) + ")");
                    return 1;
                }
                tap(mc, KeyBindings.CYCLE);
                QUEUE.addFirst(this);
                return 5;
            }
        };
        QUEUE.add(selectStep);
        // wait for a thrown weapon (scythes, discs) to come back first
        int[] waited = {0};
        QUEUE.add(new Step() {
            @Override
            public int run(Minecraft mc) {
                AbilityRuntime rt = mc.player.getData(ModAttachments.ABILITY_RUNTIME);
                if (rt.isThrown(FantasyWeaponItem.data(held(mc)).idOrNil()) && waited[0]++ < 30) {
                    QUEUE.addFirst(this);
                    return 10;
                }
                if (waited[0] >= 30) fail(key + ": thrown weapon never came back");
                return 1;
            }
        });
        // a fresh target right in front, then charge with the real ability key, release, verify the cast
        QUEUE.add(mc -> {
            cmd(mc, "/kill @e[type=minecraft:husk]");
            cmd(mc, "/summon minecraft:husk 0 -60 5 {NoAI:1b,Health:1000000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000000d}]}");
            return 4;
        });
        QUEUE.add(mc -> {
            key(mc, KeyBindings.ABILITY, true);
            int charge = ProgressionMath.chargeTicks(a, ProgressionMath.mastery(def, FantasyWeaponItem.data(held(mc))));
            return Math.max(2, charge + 3);
        });
        QUEUE.add(mc -> {
            AbilityRuntime rt = mc.player.getData(ModAttachments.ABILITY_RUNTIME);
            if (!rt.isCharging() && a.chargeTicks() > 0) {
                fail(key + ": was not charging when released (last refusal: " + com.fantasyweapons.client.hud.Notifications.lastDenied() + ")");
            }
            key(mc, KeyBindings.ABILITY, false);
            return 8;
        });
        QUEUE.add(mc -> {
            AbilityRuntime rt = mc.player.getData(ModAttachments.ABILITY_RUNTIME);
            long cd = rt.cooldownRemaining(FantasyWeaponItem.data(held(mc)).idOrNil(), a.id(), ClientState.now());
            if (cd > 12) pass(key + ": cast (cooldown " + cd + "t)");
            else fail(key + ": did not cast (cooldown " + cd + "t, last refusal: " + com.fantasyweapons.client.hud.Notifications.lastDenied() + ")");
            // try again while on cooldown: nothing may charge, not even the client prediction
            key(mc, KeyBindings.ABILITY, true);
            return 5;
        });
        QUEUE.add(mc -> {
            AbilityRuntime rt = mc.player.getData(ModAttachments.ABILITY_RUNTIME);
            boolean charging = rt.isCharging() || ClientState.localCharge() != null;
            if (charging) fail(key + ": charges while on cooldown");
            else pass(key + ": refused to charge on cooldown");
            key(mc, KeyBindings.ABILITY, false);
            // let long effects (ultimates, leaps) finish before the next test
            return a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE ? 160 : 30;
        });
    }
}
