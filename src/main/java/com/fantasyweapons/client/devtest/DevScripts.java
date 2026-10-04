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
            default -> {
                if (name.startsWith("weapon:")) showcase(b, name.substring(7));
                else if (name.startsWith("ability:")) single(b, name.substring(8));
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
        for (int i = 0; i < 12; i++) b.wait(2).screenshot(parts[1] + "_t" + String.format("%02d", i * 2));
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
        String[] weapons = {"voidfang", "solaris", "doomcleaver", "soulreaper", "starforge", "aetherlance", "monolith", "infernochain"};
        b.hud(false);
        for (int i = 0; i < weapons.length; i++) b.cmd("/fw give " + weapons[i] + " 1");
        b.wait(20);
        for (int i = 0; i < weapons.length; i++) {
            String w = weapons[i];
            b.slot(i).look(0, 0).wait(10);
            // first person: idle, mid swing
            b.playerView().camera(CameraType.FIRST_PERSON).hud(true).pin(-1, false, false, -1, -1, -1).wait(8).screenshot(w + "_fp_idle");
            b.pin(0.25f, false, false, -1, -1, -1).wait(3).screenshot(w + "_fp_swing25");
            b.pin(0.5f, false, false, -1, -1, -1).wait(3).screenshot(w + "_fp_swing50");
            b.pin(-1, false, false, 1f, -1, -1).wait(3).screenshot(w + "_fp_charge");
            b.hud(false);
            // third person, 3/4 front-right and side
            b.pin(-1, false, false, -1, -1, -1).viewFrom(2.6, 0.2, 2.6).wait(8).screenshot(w + "_tp_idle_front");
            b.viewFrom(-3.4, 0.3, 0.4).wait(5).screenshot(w + "_tp_idle_side");
            b.viewFrom(2.6, 0.2, 2.6);
            for (float t : new float[]{0.22f, 0.4f, 0.6f}) {
                b.pin(t, false, false, -1, -1, -1).wait(3).screenshot(w + "_tp_swing" + Math.round(t * 100));
            }
            b.pin(0.4f, false, true, -1, -1, -1).wait(3).screenshot(w + "_tp_backhand40");
            b.pin(0.35f, true, false, -1, -1, -1).wait(3).screenshot(w + "_tp_heavy35");
            b.pin(-1, false, false, 1f, -1, -1).wait(3).screenshot(w + "_tp_charge");
            b.pin(-1, false, false, -1, 0.25f, -1).wait(3).screenshot(w + "_tp_cast");
            b.pin(-1, false, false, -1, -1, 0.4f).wait(3).screenshot(w + "_tp_form");
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
}
