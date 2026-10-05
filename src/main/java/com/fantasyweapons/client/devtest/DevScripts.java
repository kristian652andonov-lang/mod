package com.fantasyweapons.client.devtest;

import net.minecraft.client.CameraType;

/** DEVELOPMENT ONLY: named screenshot scripts for {@link ScreenshotDirector}. */
final class DevScripts {
    private DevScripts() {
    }

    static void build(String name, ScreenshotDirector.Builder b) {
        b.cmd("/gamerule doDaylightCycle false").cmd("/time set 6000").cmd("/weather clear").cmd("/gamerule doMobSpawning false")
                .cmd("/kill @e[type=!player]").cmd("/tp @s 0 -60 0 0 0").cmd("/clear @s");
        switch (name) {
            case "voidfang" -> voidfang(b);
            case "poses" -> poses(b);
            case "grip" -> grip(b);
            case "check" -> check(b);
            case "plant" -> plant(b);
            case "infernochain" -> infernochain(b);
            case "chaingrip" -> chainGrip(b);
            case "menus" -> menus(b);
            case "holds" -> holds(b);
            case "yaw" -> yaw(b);
            case "levelup" -> levelUp(b);
            case "anchors" -> anchors(b);
            case "rift" -> rift(b);
            default -> {
                if (name.startsWith("weapon:")) showcase(b, name.substring(7));
                else if (name.startsWith("ability:")) single(b, name.substring(8));
                else if (name.startsWith("abilities:")) {
                    for (String k : name.substring(10).split(",")) single(b.cmd("/clear @s").cmd("/kill @e[type=!player]").wait(10), k);
                }
                else b.wait(20);
            }
        }
        b.wait(40).quit();
    }

    /**
     * Casts every castable ability of a weapon at a group of dummies, from a third-person side view, capturing the
     * charge, the release and the aftermath.
     */
    private static void showcase(ScreenshotDirector.Builder b, String weapon) {
        var def = com.fantasyweapons.weapon.Weapons.get(weapon);
        if (def == null) return;
        b.cmd("/fw give " + weapon + " 100").wait(20).slot(0).cmd("/fw points 200").hud(true);
        for (var a : def.castables()) {
            b.cmd("/kill @e[type=minecraft:husk]").wait(25);
            for (int i = 0; i < 5; i++) {
                b.cmd("/summon minecraft:husk " + (i % 2 == 0 ? -1 : 1) * (i / 2) * 2 + " -60 " + (6 + (i % 3) * 2)
                        + " {NoAI:1b,Health:1000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000d}]}");
            }
            b.cmd("/fw cooldowns").wait(10);
            b.camera(CameraType.FIRST_PERSON).playerView().look(0, 12).wait(5);
            if (a.requiredForm() != null) b.form(a.requiredForm()).wait(30);
            b.select(a.id()).wait(5).hud(false).viewFrom(-6.5, 2.2, 3.0);
            int charge = Math.max(0, a.chargeTicks());
            b.abilityDown().wait(Math.max(2, charge - 2)).screenshot(weapon + "_" + a.id() + "_0charge").wait(3).abilityUp();
            b.wait(3).screenshot(weapon + "_" + a.id() + "_1release").wait(6).screenshot(weapon + "_" + a.id() + "_2mid")
                    .wait(10).screenshot(weapon + "_" + a.id() + "_3late");
            if (a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE) {
                b.wait(40).screenshot(weapon + "_" + a.id() + "_4ult").wait(60).screenshot(weapon + "_" + a.id() + "_5ult")
                        .wait(40).screenshot(weapon + "_" + a.id() + "_6ult");
            }
            b.wait(20).playerView().hud(true);
        }
    }

    /** One ability ("weapon/ability") captured every 2 ticks from a closer three-quarter view. */
    private static void single(ScreenshotDirector.Builder b, String key) {
        String[] parts = key.split("/");
        var def = com.fantasyweapons.weapon.Weapons.get(parts[0]);
        if (def == null || def.ability(parts[1]) == null) return;
        var a = def.ability(parts[1]);
        b.cmd("/fw give " + parts[0] + " 100").wait(20).slot(0).cmd("/fw points 200").hud(false);
        for (int i = 0; i < 4; i++) {
            b.cmd("/summon minecraft:husk " + (i - 1.5) * 1.5 + " -60 " + (5 + (i % 2) * 1.5)
                    + " {NoAI:1b,Health:1000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000d}]}");
        }
        b.wait(30).camera(CameraType.FIRST_PERSON).look(0, 10);
        if (a.requiredForm() != null) b.form(a.requiredForm()).wait(30);
        b.select(a.id()).wait(5).viewFrom(9.5, 3.5, 4.0);
        b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
        int frames = a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE ? 26 : 12;
        for (int i = 0; i < frames; i++) b.wait(2).screenshot(parts[1] + "_t" + String.format("%02d", i * 2));
        b.playerView();
    }

    /** Quick checks: weapon-kill dissolve from the side, first-person heavy wind-ups and charge. */
    private static void check(ScreenshotDirector.Builder b) {
        b.cmd("/fw give voidfang 1").cmd("/fw give doomcleaver 1").cmd("/fw give starforge 1").wait(20).slot(0);
        b.cmd("/summon minecraft:husk 0 -60 2 {NoAI:1b,Health:1f}").wait(15).hud(false);
        b.camera(CameraType.FIRST_PERSON).look(0, 20).wait(5).swing().viewFrom(-3.5, 0.6, 2.2);
        b.wait(16).screenshot("dissolve_a").wait(4).screenshot("dissolve_b").wait(6).screenshot("dissolve_c");
        b.playerView().hud(true).look(0, 0);
        for (int i = 0; i < 3; i++) {
            String w = i == 0 ? "voidfang" : i == 1 ? "doomcleaver" : "starforge";
            b.slot(i).wait(10);
            for (float t : new float[]{0.15f, 0.3f, 0.45f, 0.6f}) b.pin(t, false, false, -1, -1, -1).wait(3).screenshot(w + "_fp_swing" + Math.round(t * 100));
            b.pin(-1, false, false, 1f, -1, -1).wait(3).screenshot(w + "_fp_charge").pin(-1, false, false, -1, -1, -1);
        }
    }

    /** Calibration: grip angles on a one- and a two-handed weapon. */
    private static void grip(ScreenshotDirector.Builder b) {
        b.hud(false).cmd("/fw give voidfang 1").cmd("/fw give solaris 1").wait(20);
        float[][] grips = {{0, 0}, {0.8f, 0}, {1.6f, 0}, {2.4f, 0}, {-0.8f, 0}, {0, 0.8f}, {0, 1.6f}, {0, -0.8f}};
        for (int i = 0; i < 2; i++) {
            b.slot(i).look(0, 0).wait(10);
            for (float[] g : grips) {
                String n = (i == 0 ? "voidfang" : "solaris") + "_gx" + Math.round(g[0] * 10) + "_gz" + Math.round(g[1] * 10);
                b.grip(g).viewFrom(-3.4, 0.3, 0.4).wait(4).screenshot(n + "_side").viewFrom(0.6, 0.3, 3.4).wait(4).screenshot(n + "_front");
            }
        }
        b.grip(null).playerView();
    }

    /** One weapon per class: idle, swing keyframes, heavy, charge, release and form from several angles. */
    private static void poses(ScreenshotDirector.Builder b) {
        var all = com.fantasyweapons.weapon.Weapons.all();
        b.hud(false);
        for (int i = 0; i < all.size(); i++) {
            String w = all.get(i).id();
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).look(0, 0).wait(8);
            // first person: idle and mid swing
            b.playerView().camera(CameraType.FIRST_PERSON).hud(true).pin(-1, false, false, -1, -1, -1).wait(6).screenshot(w + "_fp_idle");
            b.pin(0.4f, false, false, -1, -1, -1).wait(3).screenshot(w + "_fp_swing40");
            b.hud(false);
            // third person: front and side stance, swing keyframes from the front three-quarter view
            b.pin(-1, false, false, -1, -1, -1).viewFrom(1.2, 0.4, 4.0).wait(6).screenshot(w + "_tp_front");
            b.viewFrom(4.2, 0.5, 0.6).wait(4).screenshot(w + "_tp_side");
            b.viewFrom(2.8, 0.4, 3.0);
            for (float t : new float[]{0.24f, 0.44f, 0.7f}) b.pin(t, false, false, -1, -1, -1).wait(3).screenshot(w + "_tp_swing" + Math.round(t * 100));
            b.pin(-1, false, false, -1, -1, -1);
        }
        b.playerView();
    }

    /** Monolith's plant pose: overhead, mid-drive, planted, pulled free; third person side/front and first person. */
    private static void plant(ScreenshotDirector.Builder b) {
        b.cmd("/fw give monolith 1").wait(20).slot(0).hud(false).look(0, 0).wait(10);
        float[] ticks = {0, 2, 4, 14, 26};
        for (float t : ticks) b.plant(t, 30).viewFrom(-4.2, 0.4, 1.4).wait(3).screenshot("plant_side_" + Math.round(t));
        b.plant(14, 30).viewFrom(2.8, 0.6, 3.6).wait(3).screenshot("plant_front_14");
        b.playerView().camera(CameraType.FIRST_PERSON).hud(true);
        for (float t : ticks) b.plant(t, 30).wait(3).screenshot("plant_fp_" + Math.round(t));
        b.look(0, 40).plant(14, 30).wait(3).screenshot("plant_fp_down");
        b.plant(-1, 30).playerView();
    }

    /** Infernochain: sword idle, Transform into the chainblade, the burning chain idle, a chainblade whip, Inferno Lash in both forms. */
    private static void infernochain(ScreenshotDirector.Builder b) {
        b.cmd("/fw give infernochain 100").wait(20).slot(0).cmd("/fw points 200").hud(false).look(0, 0).wait(10);
        for (int i = 0; i < 3; i++) {
            b.cmd("/summon minecraft:husk " + (i - 1) * 2 + " -60 6 {NoAI:1b,Health:1000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000d}]}");
        }
        b.viewFrom(-5.5, 1.2, 2.4).wait(10).screenshot("inferno_sword_idle");
        b.form("chainblade");
        for (int t = 0; t < 6; t++) b.wait(7).screenshot("inferno_transform_" + t);
        b.wait(20).screenshot("inferno_chain_idle");
        b.swing();
        for (int t = 0; t < 8; t++) b.wait(2).screenshot("inferno_whip_" + t);
        b.wait(30).playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(5).screenshot("inferno_fp_chain_idle").swing();
        for (int t = 0; t < 4; t++) b.wait(3).screenshot("inferno_fp_whip_" + t);
        b.hud(false).viewFrom(-5.5, 1.2, 2.4).select("inferno_lash").wait(5).cmd("/fw cooldowns");
        b.abilityDown().wait(14).abilityUp();
        for (int t = 0; t < 4; t++) b.wait(2).screenshot("inferno_lash_chain_" + t);
        b.wait(30).form("sword").wait(30).cmd("/fw cooldowns").abilityDown().wait(14).abilityUp();
        for (int t = 0; t < 3; t++) b.wait(2).screenshot("inferno_lash_sword_" + t);
        b.wait(20).playerView();
    }

    /** Chain-form stance candidates (grip tilt/twist) from the side and the front. */
    private static void chainGrip(ScreenshotDirector.Builder b) {
        b.cmd("/fw give infernochain 100").wait(20).slot(0).hud(false).look(0, 0).wait(10).form("chainblade").wait(60);
        float[][] grips = {{-0.35f, 0.1f}, {0.95f, 0f}, {0.4f, 0f}, {1.6f, 0f}, {0.95f, 0.8f}, {0.95f, -0.8f}, {2.3f, 0f}, {-1.2f, 0f}};
        for (int i = 0; i < grips.length; i++) {
            b.grip(grips[i]).viewFrom(-4.5, 0.6, 1.2).wait(3).screenshot("cg_side_" + i);
            b.viewFrom(1.5, 0.6, 4.5).wait(3).screenshot("cg_front_" + i);
        }
        b.grip(null).playerView();
    }

    /** Progression menu and HUD of the newest weapons. */
    private static void menus(ScreenshotDirector.Builder b) {
        String[] weapons = {"monolith", "infernochain"};
        for (String w : weapons) b.cmd("/fw give " + w + " 100");
        b.wait(20).cmd("/fw points 200").camera(CameraType.FIRST_PERSON).hud(true).look(0, 5);
        for (int i = 0; i < weapons.length; i++) {
            var def = com.fantasyweapons.weapon.Weapons.get(weapons[i]);
            b.slot(i).wait(20).screenshot(weapons[i] + "_hud");
            b.menu(def.castables().get(def.castables().size() - 1).id()).wait(20).screenshot(weapons[i] + "_menu").closeScreen().wait(5);
        }
    }

    /** Every weapon held: third person from the player's right side and from the front, and first person. */
    private static void holds(ScreenshotDirector.Builder b) {
        var all = com.fantasyweapons.weapon.Weapons.all();
        for (var def : all) b.cmd("/fw give " + def.id() + " 1");
        b.wait(20).look(0, 0);
        for (int i = 0; i < all.size(); i++) {
            String w = all.get(i).id();
            // the hotbar has 9 slots: swap the remaining weapons in as we go
            if (i == 9) b.cmd("/clear @s").cmd("/fw give " + all.get(9).id() + " 1").cmd("/fw give " + all.get(10).id() + " 1")
                    .cmd("/fw give " + all.get(11).id() + " 1").cmd("/fw give " + all.get(12).id() + " 1").wait(10);
            b.slot(i % 9).wait(12).hud(false);
            b.viewFrom(4.2, 0.5, 0.6).wait(4).screenshot("hold_" + w + "_right");
            b.viewFrom(0.8, 0.5, 4.2).wait(4).screenshot("hold_" + w + "_front");
            b.playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(6).screenshot("hold_" + w + "_fp");
        }
    }

    /** Handle-yaw candidates for a few asymmetric weapons, first person and third person side view. */
    private static void yaw(ScreenshotDirector.Builder b) {
        String[] ws = {"soulreaper", "starforge", "doomcleaver", "solaris", "bloomfall", "eclipse_reaper"};
        for (String w : ws) b.cmd("/fw give " + w + " 1");
        b.wait(20).look(0, 0);
        float[] fps = {0, 60, 90, -60, -90};
        for (int i = 0; i < ws.length; i++) {
            b.slot(i).wait(10).playerView().camera(CameraType.FIRST_PERSON).hud(true);
            for (float y : fps) {
                b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.debugFpYaw = y).wait(3).screenshot("yaw_" + ws[i] + "_fp" + Math.round(y));
            }
            b.hud(false);
            for (float y : new float[]{0, 180}) {
                b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.debugTpYaw = y).viewFrom(4.2, 0.5, 0.6).wait(3).screenshot("yaw_" + ws[i] + "_tp" + Math.round(y));
            }
            b.run(mc -> {
                com.fantasyweapons.client.render.WeaponRenderer.debugFpYaw = null;
                com.fantasyweapons.client.render.WeaponRenderer.debugTpYaw = null;
            });
        }
        b.playerView();
    }

    private static void voidfang(ScreenshotDirector.Builder b) {
        b.cmd("/fw give voidfang 1").wait(20).slot(0);
        // model + HUD at level 1
        b.wait(20).camera(CameraType.FIRST_PERSON).look(0, 10).wait(30).screenshot("01_firstperson_lv1");
        b.camera(CameraType.THIRD_PERSON_FRONT).look(0, 10).wait(30).screenshot("02_thirdperson_front");
        b.camera(CameraType.THIRD_PERSON_BACK).look(90, 20).wait(30).screenshot("03_thirdperson_side");
        b.camera(CameraType.FIRST_PERSON).look(0, 0);
        // level up to 100 (banner) and unlock everything
        b.cmd("/fw level 27").wait(10).screenshot("04_levelup_banner");
        b.wait(90).screenshot("05_unlock_banner");
        b.cmd("/fw level 100").cmd("/fw points 80").wait(200);
        b.menu(null).wait(25).screenshot("06_menu_core");
        b.menu("void_blink").wait(25).screenshot("07_menu_blink");
        b.closeScreen().wait(10);
        // melee on a dummy
        b.cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:2000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:2000d}]}").wait(20);
        b.look(0, 15).wait(5).swing().wait(2).screenshot("08_melee_hit");
        b.wait(30);
        // weapon kill: custom dissolve instead of the vanilla death puff
        b.cmd("/kill @e[type=minecraft:husk]").wait(30).cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:1f}").wait(15);
        b.camera(CameraType.THIRD_PERSON_BACK).look(0, 15).wait(5).swing().wait(2).screenshot("08b_kill");
        b.wait(17).screenshot("08c_dissolve").wait(5).screenshot("08d_dissolve_late");
        b.camera(CameraType.FIRST_PERSON).look(0, 15);
        b.cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:2000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:2000d}]}").wait(20);
        // void slash
        b.select("void_slash").wait(5).abilityDown().wait(6).screenshot("09_charging").wait(8).abilityUp().wait(4).screenshot("10_void_slash");
        b.cmd("/fw cooldowns").wait(30);
        // void blink (third person to see the rift left behind)
        b.camera(CameraType.THIRD_PERSON_BACK).look(0, 15);
        b.select("void_blink").wait(5).abilityDown().wait(35).screenshot("11_blink_full_charge").abilityUp().wait(6).screenshot("12_void_blink");
        b.wait(30).cmd("/fw cooldowns").cmd("/tp @s 0 -60 -6 0 0").wait(20);
        // rift tear on the dummy
        b.select("rift_tear").wait(5).abilityDown().wait(55).abilityUp().wait(14).screenshot("13_rift_tear").wait(30).screenshot("14_rift_tear_late");
        b.wait(40).cmd("/fw cooldowns");
        // void execution
        b.cmd("/summon minecraft:husk 0 -60 6 {NoAI:1b}").wait(10).camera(CameraType.THIRD_PERSON_BACK).look(0, 10);
        b.select("void_execution").wait(5).abilityDown().wait(45).abilityUp().wait(3).screenshot("15_void_execution");
        b.wait(30).cmd("/fw cooldowns").cmd("/tp @s 0 -60 -10 0 0").wait(10);
        // void dimension
        for (int i = 0; i < 6; i++) b.cmd("/summon minecraft:husk " + (i * 2 - 5) + " -60 " + (i % 2 == 0 ? -4 : -1) + " {NoAI:1b}");
        b.camera(CameraType.THIRD_PERSON_BACK).look(0, 35);
        b.select("void_dimension").wait(5).abilityDown().wait(85).abilityUp().wait(20).screenshot("16_void_dimension").wait(40)
                .screenshot("17_void_dimension_mid").wait(80).screenshot("18_dimension_collapse");
        b.camera(CameraType.FIRST_PERSON).hud(true).wait(20).screenshot("19_hud_cooldowns");
    }

    /** Level-up effects at levels 10, 25, 50, 75 and the max level: close view of the circle, then the columns to the sky. */
    private static void levelUp(ScreenshotDirector.Builder b) {
        b.cmd("/fw give voidfang 1").wait(20).slot(0).hud(false).look(0, 0).wait(10);
        for (int lv : new int[]{10, 25, 50, 75, 100}) {
            b.cmd("/fw level " + (lv - 1)).wait(70).viewFrom(6.5, 4.5, 7.5, 0).wait(2).cmd("/fw level " + lv);
            b.wait(12).screenshot("lvl" + lv + "_a_close").wait(14).screenshot("lvl" + lv + "_b_close");
            b.viewFrom(22, 3, 30, 22).wait(14).screenshot("lvl" + lv + "_c_sky").wait(20).screenshot("lvl" + lv + "_d_sky");
            if (lv == 100) b.viewFrom(6.5, 4.5, 7.5, 0).wait(25).screenshot("lvl100_e_close").wait(25).screenshot("lvl100_f_close").wait(8).screenshot("lvl100_g_fading");
            b.wait(40);
        }
    }

    /**
     * Area abilities cast while flying 7 blocks up: they must land on the ground below and stay where they were cast
     * while the caster flies away. Also shows the rune circles with their orbiting small circles.
     */
    private static void anchors(ScreenshotDirector.Builder b) {
        String[][] casts = {{"stormbreaker", "wrath_of_the_storm"}, {"starforge", "gravity_slam"}, {"frostrend", "glacial_domain"},
                {"doomcleaver", "crimson_apocalypse"}, {"solaris", "celestial_inferno"}, {"gravebite", "legion_of_the_damned"}};
        b.cmd("/gamemode creative").hud(false);
        for (String[] c : casts) {
            var a = com.fantasyweapons.weapon.Weapons.get(c[0]).ability(c[1]);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/tp @s 0 -53 0 0 20").cmd("/fw give " + c[0] + " 100").wait(15).slot(0)
                    .cmd("/fw points 200").cmd("/fw cooldowns");
            for (int i = 0; i < 4; i++) {
                b.cmd("/summon minecraft:husk " + (i - 1.5) * 2.5 + " -60 " + (3 + (i % 2) * 2)
                        + " {NoAI:1b,Health:1000f,Attributes:[{Id:\"minecraft:generic.max_health\",Base:1000d}]}");
            }
            b.run(mc -> {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            });
            b.select(c[1]).wait(10).viewFrom(13, 1, 13, -4);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            b.wait(14).screenshot("anchor_" + c[1] + "_a");
            b.cmd("/tp @s 9 -48 -9").wait(26).screenshot("anchor_" + c[1] + "_b_moved");
            b.wait(40).screenshot("anchor_" + c[1] + "_c");
            b.wait(120);
        }
        b.cmd("/gamemode survival");
    }

    /** Rift Tear on its own (client-side effect only), every 3 ticks from the caster's eyes, then from the side. */
    private static void rift(ScreenshotDirector.Builder b) {
        b.cmd("/fw give voidfang 100").wait(120).slot(0).hud(false).look(0, 0).camera(CameraType.FIRST_PERSON).playerView().wait(10);
        for (String v : new String[]{"fp", "side"}) {
            if (v.equals("side")) b.viewFrom(9, 2, 6, 2);
            b.run(mc -> com.fantasyweapons.client.fx.FxDispatcher.dispatch(com.fantasyweapons.network.FxPayload.of(com.fantasyweapons.network.FxIds.VOID_RIFT)
                    .caster(mc.player.getId()).pos(mc.player.position().add(0, 2.5, 9)).dir(new net.minecraft.world.phys.Vec3(0, 0, 1)).scale(3.5f)
                    .power(50).seed(7).build()));
            for (int t = 0; t < 12; t++) b.wait(3).screenshot("rift_" + v + "_" + String.format("%02d", t * 3));
            b.wait(40);
        }
        b.playerView();
    }
}
