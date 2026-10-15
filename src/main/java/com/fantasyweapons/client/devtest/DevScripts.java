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
            case "ray" -> ray(b);
            case "tooltips" -> tooltips(b);
            case "facing" -> facing(b);
            case "spinend" -> spinEnd(b);
            case "scythe" -> scythe(b);
            case "blockspam" -> blockSpam(b);
            case "bonelog" -> boneLog(b);
            case "lanceclip" -> lanceClip(b);
            case "starrings" -> starRings(b);
            case "gravebite" -> gravebite(b);
            case "gravechains" -> graveChains(b);
            case "ghosts" -> ghosts(b);
            case "swings" -> swings(b, null);
            case "combos" -> combos(b, null);
            case "allabilities" -> allAbilities(b);
            case "fpall" -> fpAll(b, null);
            case "rigid" -> rigid(b);
            case "bloom" -> near(b, "bloomfall/entangling_roots,bloomfall/overgrowth,bloomfall/wrath_of_the_wild");
            case "skycircles" -> wide(b, "solaris/celestial_inferno,eclipse_reaper/total_eclipse");
            default -> {
                if (name.startsWith("weapon:")) showcase(b, name.substring(7));
                else if (name.startsWith("ability:")) single(b, name.substring(8));
                else if (name.startsWith("wide:")) wide(b, name.substring(5));
                else if (name.startsWith("near:")) near(b, name.substring(5));
                else if (name.startsWith("swings:")) swings(b, java.util.List.of(name.substring(7).split(",")));
                else if (name.startsWith("fpall:")) fpAll(b, java.util.List.of(name.substring(6).split(",")));
                else if (name.startsWith("kills:")) kills(b, name.substring(6));
                else if (name.startsWith("groundaudit")) groundAudit(b, name.length() > 12 ? java.util.List.of(name.substring(12).split(",")) : null);
                else if (name.startsWith("aircasts:")) airCasts(b, name.substring(9));
                else if (name.equals("materials")) materials(b);
                else if (name.equals("refinery")) refinery(b);
                else if (name.equals("armor")) armor(b);
                else if (name.startsWith("setfx")) setFx(b, name.length() > 6 ? java.util.List.of(name.substring(6).split(",")) : null);
                else if (name.equals("infuse")) infuse(b);
                else if (name.equals("fit")) fit(b);
                else if (name.equals("forge")) forge(b);
                else if (name.equals("codex")) codex(b);
                else if (name.equals("codexall")) codexAll(b);
                else if (name.equals("deeds")) deeds(b);
                else if (name.equals("quests")) quests(b);
                else if (name.equals("oreicons")) oreIcons(b);
                else if (name.equals("classpages")) classPages(b);
                else if (name.equals("gear")) gear(b);
                else if (name.equals("forgefx")) forgeFx(b);
                else if (name.equals("codexquests")) {
                    b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/fw class set none").cmd("/give @s fantasyweapons:wayfarers_codex")
                            .wait(10).slot(0).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10)
                            .run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110);
                    for (int i = 0; i <= 3; i++) {
                        int n = i;
                        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("mastery") + n, false))
                                .wait(16).screenshot("codex_mastery_" + n);
                    }
                    b.closeScreen().wait(30).run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.ClassScreen())).wait(5);
                    classScreen(b, c -> c.devPage(com.fantasyweapons.playerclass.PlayerClass.BERSERKER));
                    b.wait(10);
                    classScreen(b, c -> c.devChoose());
                    b.wait(6).screenshot("class_confirm2").closeScreen();
                }
                else if (name.equals("oretiming")) {
                    b.cmd("/gamemode creative").cmd("/clear @s").cmd("/fw quest reset").wait(20).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10)
                            .run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen())).wait(2)
                            .run(mc -> clickPanel(mc, 416, 236, 15, 30 + 25 + 5));
                    for (int t : new int[]{1, 4, 10, 20, 40, 80, 160}) b.wait(t == 1 ? 1 : t / 2).screenshot("oretime_" + t);
                    b.closeScreen();
                }
                else if (name.equals("codexone")) codexOne(b);
                else if (name.equals("impact")) impact(b);
                else if (name.equals("codexflip")) codexFlip(b);
                else if (name.startsWith("forms:")) forms(b, name.substring(6));
                else if (name.startsWith("hud:")) hud(b, name.substring(4));
                else if (name.startsWith("fpswings:")) fpSwings(b, name.substring(9));
                else if (name.startsWith("fpcal:")) fpCalibrate(b, name.substring(6));
                else if (name.startsWith("after:")) after(b, name.substring(6));
                else if (name.startsWith("fpafter:")) fpAfter(b, name.substring(8));
                else if (name.startsWith("combos:")) combos(b, java.util.List.of(name.substring(7).split(",")));
                else if (name.startsWith("holds:")) holds(b, java.util.Arrays.stream(name.substring(6).split(",")).map(com.fantasyweapons.weapon.Weapons::get).toList());
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
                        + " {NoAI:1b,Health:1000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000d}]}");
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
        b.cmd("/fw give " + parts[0] + " 100").wait(130).slot(0).cmd("/fw points 200").hud(false);
        for (int i = 0; i < 4; i++) {
            b.cmd("/summon minecraft:husk " + (i - 1.5) * 1.5 + " -60 " + (5 + (i % 2) * 1.5)
                    + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
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
            b.cmd("/summon minecraft:husk " + (i - 1) * 2 + " -60 6 {NoAI:1b,Health:1000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000d}]}");
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
        String[] weapons = {"voidfang", "bloomfall", "frostrend", "infernochain", "soulreaper", "solaris"};
        for (String w : weapons) b.cmd("/fw give " + w + " 100");
        b.wait(20).cmd("/fw points 200").camera(CameraType.FIRST_PERSON).hud(true).look(0, 5);
        for (int i = 0; i < weapons.length; i++) {
            var def = com.fantasyweapons.weapon.Weapons.get(weapons[i]);
            b.slot(i).wait(20).screenshot(weapons[i] + "_hud");
            b.menu(def.castables().get(def.castables().size() - 1).id()).wait(20).screenshot(weapons[i] + "_menu").closeScreen().wait(5);
            b.menu(def.castables().get(0).id()).wait(20).screenshot(weapons[i] + "_menu_first").closeScreen().wait(5);
            b.menu("#core").wait(20).screenshot(weapons[i] + "_menu_core").closeScreen().wait(5);
        }
    }

    /** Every weapon held: third person from the player's right side and from the front, and first person. */
    private static void holds(ScreenshotDirector.Builder b) {
        holds(b, com.fantasyweapons.weapon.Weapons.all());
    }

    private static void holds(ScreenshotDirector.Builder b, java.util.List<com.fantasyweapons.weapon.WeaponDefinition> all) {
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
            b.viewAt(1.4, 1.5, -3.6, 0, 1.2, 2.5).wait(4).screenshot("hold_" + w + "_back");
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
        b.cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:2000f,attributes:[{id:\"minecraft:generic.max_health\",base:2000d}]}").wait(20);
        b.look(0, 15).wait(5).swing().wait(2).screenshot("08_melee_hit");
        b.wait(30);
        // weapon kill: custom dissolve instead of the vanilla death puff
        b.cmd("/kill @e[type=minecraft:husk]").wait(30).cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:1f}").wait(15);
        b.camera(CameraType.THIRD_PERSON_BACK).look(0, 15).wait(5).swing().wait(2).screenshot("08b_kill");
        b.wait(17).screenshot("08c_dissolve").wait(5).screenshot("08d_dissolve_late");
        b.camera(CameraType.FIRST_PERSON).look(0, 15);
        b.cmd("/summon minecraft:husk 0 -60 3 {NoAI:1b,Health:2000f,attributes:[{id:\"minecraft:generic.max_health\",base:2000d}]}").wait(20);
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
            if (lv == 100) {
                b.viewFrom(6.5, 4.5, 7.5, 0).wait(10).screenshot("lvl100_e_close").viewFrom(9, 3.2, 0.5, 3.5).wait(6).screenshot("lvl100_side")
                        .wait(25).screenshot("lvl100_f_close").wait(8).screenshot("lvl100_g_fading");
            }
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
                        + " {NoAI:1b,Health:1000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000d}]}");
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

    /** Judgement Ray from the caster's eyes, then from the side. */
    private static void ray(ScreenshotDirector.Builder b) {
        b.cmd("/fw give aetherlance 100").wait(130).slot(0).cmd("/fw points 200").hud(false).look(0, 2).camera(CameraType.FIRST_PERSON).playerView();
        for (int i = 0; i < 3; i++) {
            b.cmd("/summon minecraft:husk " + (i - 1) * 3 + " -60 14 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
        }
        b.select("judgement_ray").wait(10);
        for (String v : new String[]{"fp", "side"}) {
            b.cmd("/fw cooldowns").wait(5);
            if (v.equals("side")) b.viewFrom(8, 2.5, 7, 1.5);
            b.abilityDown().wait(66).abilityUp();
            for (int t = 0; t < 6; t++) b.wait(t == 0 ? 2 : 8).screenshot("ray_" + v + "_" + t);
            b.wait(120);
        }
        b.playerView();
    }

    /** The item tooltip (legend, runes, stats, abilities) of a few weapons. */
    private static void tooltips(ScreenshotDirector.Builder b) {
        for (String w : new String[]{"monolith", "voidfang", "infernochain"}) {
            b.cmd("/clear @s").cmd("/fw give " + w + " 40").wait(140).slot(0);
            b.run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getMainHandItem()))).wait(10).screenshot("tooltip_" + w).closeScreen().wait(5);
        }
    }

    /** Every weapon held, seen from behind the player (the usual third-person view) and from its right; with "flip" variants. */
    private static void facing(ScreenshotDirector.Builder b) {
        var all = com.fantasyweapons.weapon.Weapons.all();
        b.hud(false).look(0, 0);
        for (var def : all) {
            String w = def.id();
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).wait(6);
            b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.debugTpYaw = null);
            b.viewFrom(1.1, 1.0, -3.6).wait(4).screenshot("face_" + w + "_back");
            b.viewFrom(-4.0, 0.6, 0.4).wait(4).screenshot("face_" + w + "_right");
            if (w.equals("doomcleaver") || w.equals("gravebite") || w.equals("starforge")) {
                b.playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(4).screenshot("face_" + w + "_fp").hud(false);
            }
            if (w.equals("doomcleaver") || w.equals("gravebite")) {
                b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.debugTpYaw = 180f);
                b.viewFrom(1.1, 1.0, -3.6).wait(4).screenshot("face_" + w + "_back_flip");
                b.viewFrom(-4.0, 0.6, 0.4).wait(4).screenshot("face_" + w + "_right_flip");
                b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.debugTpYaw = null);
            }
        }
        b.playerView();
    }

    /** The last moments of Stormbreaker's Tempest Spin and the blend back to idle, every two ticks, from the caster's eyes. */
    private static void spinEnd(ScreenshotDirector.Builder b) {
        var a = com.fantasyweapons.weapon.Weapons.get("stormbreaker").ability("tempest_spin");
        b.cmd("/fw give stormbreaker 100").wait(130).slot(0).cmd("/fw points 200").hud(false).look(0, 15).camera(CameraType.FIRST_PERSON).playerView();
        b.select("tempest_spin").wait(10).viewFrom(-3.2, 0.8, 1.2, 1.4).abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp().wait(24);
        for (int t = 0; t < 16; t++) b.wait(2).screenshot("spinend_" + String.format("%02d", 24 + t * 2));
    }

    /** Soulreaper's two throws from above and to the side, every four ticks, through to the catch. */
    private static void scythe(ScreenshotDirector.Builder b) {
        var def = com.fantasyweapons.weapon.Weapons.get("soulreaper");
        b.cmd("/fw give soulreaper 100").wait(130).slot(0).cmd("/fw points 200").hud(false).look(0, 0).camera(CameraType.FIRST_PERSON).playerView();
        for (String id : new String[]{"reaping_whirl", "reapers_throw"}) {
            var a = def.ability(id);
            b.cmd("/fw cooldowns").select(id).wait(10).viewFrom(5.5, 6.5, 5.5, 1);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int t = 0; t < 22; t++) b.wait(4).screenshot("scythe_" + id + "_" + String.format("%02d", t * 4));
            b.wait(40);
        }
        b.playerView();
    }

    /** Clicks attack on the ground every two ticks for three seconds with a slow weapon and logs how many swings happened. */
    private static void blockSpam(ScreenshotDirector.Builder b) {
        int[] swings = {0};
        boolean[] was = {false};
        b.cmd("/fw give monolith 1").wait(30).slot(0).look(0, 80).wait(10);
        b.run(mc -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            var p = mc.player;
            if (p == null) return;
            if (p.swinging && !was[0]) swings[0]++;
            was[0] = p.swinging;
        }));
        for (int i = 0; i < 30; i++) b.run(mc -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey())).wait(2);
        b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("[blockspam] {} swings in 60 ticks with Monolith (attack delay {} ticks)", swings[0],
                Math.round(20 / com.fantasyweapons.weapon.WeaponClass.COLOSSAL.attackSpeed())));
    }

    /** Logs Eclipse Reaper's eclipse_ring through a Solar Flare charge, cast and the blend back to idle. */
    private static void boneLog(ScreenshotDirector.Builder b) {
        var a = com.fantasyweapons.weapon.Weapons.get("eclipse_reaper").ability("solar_flare");
        b.cmd("/fw give eclipse_reaper 100").wait(130).slot(0).cmd("/fw points 200").hud(false).camera(CameraType.FIRST_PERSON).playerView();
        b.select("solar_flare").wait(10).viewFrom(-3.5, 1, 1.5, 1.4).run(mc -> com.fantasyweapons.client.render.WeaponGeoModel.debugBone = "eclipse_ring");
        b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp().wait(60);
        b.run(mc -> com.fantasyweapons.client.render.WeaponGeoModel.debugBone = null);
    }

    /** Aetherlance held through and after each of its castable abilities, seen from in front of the player. */
    private static void lanceClip(ScreenshotDirector.Builder b) {
        var def = com.fantasyweapons.weapon.Weapons.get("aetherlance");
        b.cmd("/fw give aetherlance 100").wait(130).slot(0).cmd("/fw points 200").hud(false).camera(CameraType.FIRST_PERSON).look(0, 0).wait(5);
        b.viewFrom(-2.6, 0.9, 3.8, 1.0).wait(5).screenshot("lc_idle");
        for (var a : def.castables()) {
            String id = a.id();
            b.playerView().camera(CameraType.FIRST_PERSON).look(0, 0).cmd("/fw cooldowns").wait(5).select(id).wait(5).viewFrom(-2.6, 0.9, 3.8, 1.0);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() / 2)).screenshot("lc_" + id + "_charge").wait(Math.max(1, a.chargeTicks() / 2 + 1)).abilityUp();
            int n = a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE ? 14 : 8;
            for (int i = 0; i < n; i++) b.wait(i < 3 ? 3 : 6).viewFrom(-2.6, 0.9, 3.8, 1.0).screenshot("lc_" + id + "_t" + String.format("%02d", i));
        }
        b.playerView();
    }

    /** Starforge's gravity rings through a charge and cast, from the side (third person) and through the player's eyes. */
    private static void starRings(ScreenshotDirector.Builder b) {
        var def = com.fantasyweapons.weapon.Weapons.get("starforge");
        var a = def.castables().get(0);
        b.cmd("/fw give starforge 100").wait(130).slot(0).cmd("/fw points 200").hud(false).camera(CameraType.FIRST_PERSON).look(0, 0).wait(5);
        for (int pass = 0; pass < 2; pass++) {
            String v = pass == 0 ? "tp" : "fp";
            b.cmd("/fw cooldowns").select(a.id()).wait(5);
            if (pass == 0) b.viewFrom(-3.2, 0.9, 1.6, 1.4);
            else b.playerView().camera(CameraType.FIRST_PERSON).hud(true);
            b.wait(4).screenshot("sr_" + v + "_idle").abilityDown();
            for (int i = 0; i < 4; i++) b.wait(Math.max(2, a.chargeTicks() / 4)).screenshot("sr_" + v + "_charge" + i);
            b.wait(2).abilityUp();
            for (int i = 0; i < 6; i++) b.wait(4).screenshot("sr_" + v + "_cast" + i);
            b.wait(30);
        }
        b.playerView();
    }

    /** The death effect of mobs killed by each weapon ("weapon,..."), from the side and from the wielder's eyes. */
    private static void kills(ScreenshotDirector.Builder b, String list) {
        for (String w : list.split(",")) {
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 100").wait(130).slot(0).hud(false);
            for (int pass = 0; pass < 2; pass++) {
                String v = w + (pass == 0 ? "_side" : "_fp");
                b.cmd("/kill @e[type=!player]").wait(5).cmd("/summon minecraft:husk 0 -60 2.6 {NoAI:1b,Health:1f}").wait(10);
                b.playerView().camera(CameraType.FIRST_PERSON).look(0, 15).wait(5).swing();
                if (pass == 0) b.viewAt(4.5, 1.6, 5.5, 0, 1.4, 2.6);
                b.wait(19);
                for (int i = 0; i < 5; i++) b.wait(i == 0 ? 1 : 3).screenshot("kill_" + v + "_" + i);
                b.wait(20);
            }
            b.playerView();
        }
    }

    /**
     * Swings every weapon through its three attacks and casts every ability in first person, recording how far the
     * weld moved each piece of the weapon back onto the grip (see WeaponGeoModel#weldToGrip), then logs the largest.
     */
    /**
     * Casts every castable ability of every weapon (or of the listed ones) hovering at jump height, hovering eight blocks
     * up and standing while aiming at the sky, and has {@link GroundAudit} log every ground effect left in the air.
     */
    private static void groundAudit(ScreenshotDirector.Builder b, java.util.List<String> only) {
        b.cmd("/gamemode creative").hud(false);
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            String w = def.id();
            if (only != null && !only.contains(w)) continue;
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 100").wait(40).slot(0).cmd("/fw points 300");
            for (var a : def.castables()) {
                for (String scenario : new String[]{"jump", "high", "skyward"}) {
                    b.cmd("/kill @e[type=!player]").wait(3);
                    for (int i = 0; i < 3; i++) {
                        b.cmd("/summon minecraft:husk " + (i - 1) * 2.5 + " -60 " + (6 + (i % 2) * 1.5)
                                + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
                    }
                    b.cmd("/fw cooldowns");
                    if (a.requiredForm() != null) b.form(a.requiredForm()).wait(40).cmd("/fw cooldowns");
                    boolean fly = !scenario.equals("skyward");
                    // up first, then fly: a flying player standing on the ground drops out of flight at once
                    b.cmd("/tp @s 0 " + (scenario.equals("jump") ? -58.8 : scenario.equals("high") ? -52 : -60) + " 0 0 " + (fly ? 10 : -40)).wait(2);
                    b.run(mc -> {
                        mc.player.getAbilities().flying = fly;
                        mc.player.onUpdateAbilities();
                        mc.player.setDeltaMovement(0, 0, 0);
                    }).wait(2);
                    b.select(a.id()).wait(5);
                    String label = w + "/" + a.id() + " " + scenario;
                    b.run(mc -> GroundAudit.begin(label + String.format(" (cast %.1f above ground)", mc.player.getY() + 60)));
                    b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
                    b.wait(a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE ? 110 : 45);
                }
            }
        }
        b.run(mc -> GroundAudit.flush()).cmd("/gamemode survival").wait(5).quit();
    }

    /** Each "weapon/ability" cast hovering six blocks up, seen from the side a moment after release. */
    private static void airCasts(ScreenshotDirector.Builder b, String list) {
        b.cmd("/gamemode creative").hud(false);
        for (String key : list.split(",")) {
            String[] k = key.split("/");
            var a = com.fantasyweapons.weapon.Weapons.get(k[0]).ability(k[1]);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + k[0] + " 100").wait(30).slot(0).cmd("/fw points 300");
            for (int i = 0; i < 3; i++) {
                b.cmd("/summon minecraft:husk " + (i - 1) * 2.5 + " -60 " + (6 + (i % 2) * 1.5)
                        + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            }
            b.cmd("/fw cooldowns").cmd("/tp @s 0 -54 0 0 15").wait(2).run(mc -> {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
                mc.player.setDeltaMovement(0, 0, 0);
            }).wait(3);
            b.select(a.id()).wait(5).viewAt(12, -1.5, 3, 0, -3, 3.5);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            b.wait(a.id().equals("supernova") ? 16 : 8).screenshot("air_" + k[1]).wait(a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE ? 100 : 40);
            b.playerView();
        }
        b.cmd("/gamemode survival").wait(5).quit();
    }

    /** The 13 ores placed in a row (on the ground and set into a wall), then the materials in the inventory and in hand. */
    private static void materials(ScreenshotDirector.Builder b) {
        var ms = com.fantasyweapons.material.Material.values();
        b.cmd("/gamemode creative").hud(false).cmd("/fill -14 -60 3 14 -55 8 minecraft:air").cmd("/fill -14 -61 3 14 -61 8 minecraft:stone")
                .cmd("/fill -14 -60 8 14 -57 8 minecraft:stone");
        for (int i = 0; i < ms.length; i++) {
            String id = "fantasyweapons:" + ms[i].oreId();
            b.cmd("/setblock " + (i * 2 - 12) + " -60 5 " + id).cmd("/setblock " + (i * 2 - 12) + " -59 8 " + id);
        }
        b.wait(40).viewAt(0, 3.5, -3, 0, 0, 6).wait(10).screenshot("mat_ores_row");
        b.viewAt(-11, 1.2, 1.5, -10, 0.2, 5.2).wait(10).screenshot("mat_ores_close_a");
        b.viewAt(3, 1.2, 1.5, 4, 0.2, 5.2).wait(10).screenshot("mat_ores_close_b");
        b.viewAt(9, 1.2, 1.5, 10, 0.2, 5.2).wait(10).screenshot("mat_ores_close_c");
        // night: the ores light the dark
        b.cmd("/time set 18000").viewAt(0, 2.5, 0, 0, 0, 6).wait(30).screenshot("mat_ores_night").cmd("/time set 6000");
        b.playerView().cmd("/clear @s");
        for (var m : ms) {
            b.cmd("/give @s fantasyweapons:" + m.oreId()).cmd("/give @s fantasyweapons:" + m.rawId()).cmd("/give @s fantasyweapons:" + m.refinedId());
        }
        b.cmd("/gamemode survival").wait(20).run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(20)
                .screenshot("mat_inventory").closeScreen().cmd("/gamemode creative");
        b.hud(true).slot(0).cmd("/item replace entity @s weapon.mainhand with fantasyweapons:void_crystal").look(0, 10).wait(20).screenshot("mat_hand_raw");
        b.cmd("/item replace entity @s weapon.mainhand with fantasyweapons:solarite_core").wait(20).screenshot("mat_hand_refined");
        b.cmd("/item replace entity @s weapon.mainhand with fantasyweapons:titanstone_ore").wait(20).screenshot("mat_hand_ore");
        b.camera(CameraType.THIRD_PERSON_FRONT).cmd("/item replace entity @s weapon.mainhand with fantasyweapons:frostheart_crystal").wait(20)
                .screenshot("mat_hand_third").camera(CameraType.FIRST_PERSON);
        b.cmd("/summon item 2 -60 3 {Item:{id:\"fantasyweapons:bloodstone_core\",count:1}}").cmd("/summon item 3 -60 3 {Item:{id:\"fantasyweapons:astralite\",count:1}}")
                .viewAt(2.5, 1.0, 1.0, 2.5, -0.2, 3.0).wait(25).screenshot("mat_dropped");
        b.cmd("/gamemode survival").wait(5).quit();
    }

    /** The Arcane Refinery placed by the player, idle, fed by hoppers and refining, and its screen. */
    private static void refinery(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").hud(false).cmd("/fill -6 -60 2 6 -56 10 minecraft:air").cmd("/kill @e[type=item]").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:arcane_refinery").wait(10).slot(0).cmd("/tp @s 0 -60 0 0 30").wait(10);
        // place it as a player would, on the ground two blocks ahead (the core at 0 -60 4, facing the player)
        b.run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -61, 4.5), net.minecraft.core.Direction.UP,
                        new net.minecraft.core.BlockPos(0, -61, 4), false))).wait(20);
        // the outline of the block looked at (each block shaped by the model), and a pig's shadow beside it
        b.playerView().look(0, 20).hud(true).wait(10).screenshot("ref_outline").look(-14, 16).wait(5).screenshot("ref_outline_side").hud(false);
        b.cmd("/summon pig 1.8 -60 3.3 {NoAI:1b,Rotation:[90f,0f]}").viewAt(3.4, 1.2, 1.4, 1.0, 0.4, 3.8).wait(15).screenshot("ref_shadow").cmd("/kill @e[type=pig]");
        b.viewAt(3.2, 1.6, 1.0, 0.5, 0.9, 4.5).wait(30).screenshot("ref_idle_a");
        b.viewAt(-3.2, 2.4, 1.6, 0.5, 0.9, 4.5).wait(5).screenshot("ref_idle_b");
        b.viewAt(0.5, 1.4, 7.8, 0.5, 0.9, 4.5).wait(5).screenshot("ref_idle_back");
        // feed it: eight raw Solarite from a hopper on top; lava and eight glowstone dust (its additive) from one at the side
        b.cmd("/setblock 0 -58 4 minecraft:hopper[facing=down]").cmd("/item replace block 0 -58 4 container.0 with fantasyweapons:solarite_crystal 8")
                .cmd("/setblock -2 -60 4 minecraft:hopper[facing=east]").cmd("/item replace block -2 -60 4 container.0 with minecraft:lava_bucket 1")
                .cmd("/item replace block -2 -60 4 container.1 with minecraft:glowstone_dust 8").wait(90);
        b.viewAt(3.2, 1.6, 1.0, 0.5, 0.9, 4.5).wait(10).screenshot("ref_refining_a").wait(40).screenshot("ref_refining_b");
        // then five more raw and no glowstone: the screen says what it still needs (the first core waits on the tray)
        b.cmd("/item replace block 0 -58 4 container.0 with fantasyweapons:solarite_crystal 5").wait(160);
        b.playerView().cmd("/clear @s").look(0, 30).wait(5).run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -60, 4.0), net.minecraft.core.Direction.NORTH,
                        new net.minecraft.core.BlockPos(0, -60, 4), false))).wait(20).hud(true).screenshot("ref_screen").closeScreen().hud(false);
        b.cmd("/gamemode survival").wait(5).quit();
    }

    /** All 13 armor sets on armor stands (front and back), worn by the player, the hit ward, dark mode and the icons. */
    private static void armor(ScreenshotDirector.Builder b) {
        var sets = com.fantasyweapons.armor.ArmorSet.values();
        b.cmd("/gamemode creative").hud(false).cmd("/kill @e[type=!player]").cmd("/fill -16 -60 2 16 -56 10 minecraft:air").cmd("/clear @s");
        for (int i = 0; i < sets.length; i++) {
            String id = "fantasyweapons:" + sets[i].id();
            b.cmd("/summon armor_stand " + (i * 2 - 12) + " -60 6 {Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,ArmorItems:[{id:\"" + id + "_boots\",count:1},{id:\""
                    + id + "_leggings\",count:1},{id:\"" + id + "_chestplate\",count:1},{id:\"" + id + "_helmet\",count:1}]}");
        }
        b.wait(30);
        for (int g = 0; g < 4; g++) {
            double cx = -12 + g * 7 + 2;
            b.viewAt(cx + 0.5, 1.5, 2.2, cx + 0.5, 1.1, 6).wait(15).screenshot("arm_front_" + g);
            b.viewAt(cx + 0.5, 1.5, 9.8, cx + 0.5, 1.1, 6).wait(10).screenshot("arm_back_" + g);
        }
        // worn by the player: struck (the ward flashes up), and Eclipse Reaper's dark mode
        b.playerView().cmd("/item replace entity @s armor.head with fantasyweapons:solaris_helmet").cmd("/item replace entity @s armor.chest with fantasyweapons:solaris_chestplate")
                .cmd("/item replace entity @s armor.legs with fantasyweapons:solaris_leggings").cmd("/item replace entity @s armor.feet with fantasyweapons:solaris_boots")
                .cmd("/gamemode survival").wait(10).camera(CameraType.THIRD_PERSON_FRONT).wait(10).screenshot("arm_worn")
                .cmd("/damage @s 1 minecraft:generic").wait(4).screenshot("arm_hit");
        b.cmd("/item replace entity @s armor.head with fantasyweapons:eclipse_reaper_helmet").cmd("/item replace entity @s armor.chest with fantasyweapons:eclipse_reaper_chestplate")
                .cmd("/item replace entity @s armor.legs with fantasyweapons:eclipse_reaper_leggings").cmd("/item replace entity @s armor.feet with fantasyweapons:eclipse_reaper_boots")
                .cmd("/fw give eclipse_reaper 50").wait(20).slot(0).wait(10).screenshot("arm_eclipse_light").form("dark").wait(40).screenshot("arm_eclipse_dark")
                .camera(CameraType.FIRST_PERSON);
        b.cmd("/clear @s");
        for (var set : sets) for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) b.cmd("/give @s fantasyweapons:" + set.itemId(type));
        b.wait(20).run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(20).hud(true)
                .screenshot("arm_icons").closeScreen().hud(false);
        b.wait(5).quit();
    }

    /**
     * Every full-set ability set off (with {@code /fw setfx}) against a group of husks, seen from the side as it plays,
     * with the set plaque on the HUD.
     */
    private static void setFx(ScreenshotDirector.Builder b, java.util.List<String> only) {
        // when each effect is worth a picture: [first, second] ticks after it is set off
        java.util.Map<String, int[]> at = java.util.Map.ofEntries(java.util.Map.entry("voidfang", new int[]{4, 10}),
                java.util.Map.entry("solaris", new int[]{2, 4}), java.util.Map.entry("frostrend", new int[]{2, 6}),
                java.util.Map.entry("doomcleaver", new int[]{3, 14}), java.util.Map.entry("stormbreaker", new int[]{1, 3}),
                java.util.Map.entry("gravebite", new int[]{8, 24}), java.util.Map.entry("soulreaper", new int[]{2, 8}),
                java.util.Map.entry("bloomfall", new int[]{3, 16}), java.util.Map.entry("eclipse_reaper", new int[]{3, 8}),
                java.util.Map.entry("starforge", new int[]{3, 5}), java.util.Map.entry("aetherlance", new int[]{1, 2}),
                java.util.Map.entry("monolith", new int[]{2, 5}), java.util.Map.entry("infernochain", new int[]{4, 16}));
        String[] spots = {"~ ~ ~3.5", "~2.2 ~ ~4.6", "~-2 ~ ~5.4"};
        b.cmd("/gamemode survival").cmd("/time set noon").cmd("/weather clear").cmd("/fill -8 -60 -6 8 -55 12 minecraft:air").look(0, 0);
        for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
            if (only != null && !only.contains(set.id())) continue;
            String id = "fantasyweapons:" + set.id();
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/effect give @s minecraft:instant_health 1 10 true").playerView().hud(false)
                    .cmd("/item replace entity @s armor.head with " + id + "_helmet").cmd("/item replace entity @s armor.chest with " + id + "_chestplate")
                    .cmd("/item replace entity @s armor.legs with " + id + "_leggings").cmd("/item replace entity @s armor.feet with " + id + "_boots");
            for (int i = 0; i < spots.length; i++) {
                b.cmd("/summon husk " + spots[i] + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"fw" + i + "\"],Health:500f,"
                        + "attributes:[{id:\"minecraft:generic.max_health\",base:500d}]}");
            }
            b.wait(30).viewAt(6.5, 2.6, -1.5, 0, 1, 3.2).wait(3).cmd("/fw setfx " + set.id());
            int[] t = at.get(set.id());
            b.wait(t[0]).screenshot("setfx_" + set.id() + "_a").wait(t[1] - t[0]).screenshot("setfx_" + set.id() + "_b");
            // the wearer's own view, with the set plaque
            b.playerView().run(mc -> mc.gui.getChat().clearMessages(false)).hud(true).wait(2).screenshot("setfx_" + set.id() + "_hud").hud(false).wait(30);
            if (set == com.fantasyweapons.armor.ArmorSet.SOULREAPER) {
                // three kills: three souls on the plaque
                for (int i = 0; i < spots.length; i++) b.cmd("/damage @e[tag=fw" + i + ",limit=1] 1000 minecraft:player_attack by @s").wait(2);
                b.wait(30).run(mc -> mc.gui.getChat().clearMessages(false)).hud(true).wait(2).screenshot("setfx_soulreaper_souls").hud(false);
            }
        }
    }

    /**
     * Ores side by side and stacked (do neighbours overlap? does the outline show? fragments only under open sky), and
     * every material item in the inventory and the creative tab beside vanilla blocks for size.
     */
    private static void fit(ScreenshotDirector.Builder b) {
        var ms = com.fantasyweapons.material.Material.values();
        b.cmd("/gamerule sendCommandFeedback false").cmd("/gamemode creative").cmd("/time set noon").hud(false).cmd("/kill @e[type=!player]")
                .cmd("/fill -10 -60 2 10 -55 9 minecraft:air").cmd("/tp @s 0 -60 0 0 0");
        for (int i = 0; i < ms.length; i++) {
            String id = "fantasyweapons:" + ms[i].oreId();
            // a row of ores touching, every other one covered by stone; behind it ores stacked two high
            b.cmd("/setblock " + (i - 6) + " -60 4 " + id).cmd("/setblock " + (i - 6) + " -60 6 " + id).cmd("/setblock " + (i - 6) + " -59 6 " + id);
            if (i % 2 == 1) b.cmd("/setblock " + (i - 6) + " -59 4 minecraft:stone");
        }
        b.cmd("/setblock 7 -60 4 minecraft:diamond_ore").cmd("/setblock 8 -60 4 minecraft:stone");
        b.wait(40).viewAt(0, 3.2, -1.5, 0.5, 0, 5).wait(10).screenshot("fit_row");
        b.viewAt(-4.5, 1.6, 1.8, -3.5, 0.3, 4.6).wait(10).screenshot("fit_close_a");
        b.viewAt(3.5, 1.6, 1.8, 4.5, 0.3, 4.6).wait(10).screenshot("fit_close_b");
        b.viewAt(-7.5, 2.4, 9.5, -2, 0.6, 5).wait(10).screenshot("fit_back");
        // the outline on an ore the player looks at
        b.playerView().cmd("/tp @s -2.5 -60 1.6 0 20").hud(true).wait(15).run(mc -> mc.gui.getChat().clearMessages(false)).wait(2).screenshot("fit_outline");
        b.cmd("/tp @s 0 -60 0 0 0").hud(false);
        // the inventory, beside vanilla blocks and items
        b.cmd("/clear @s").cmd("/gamemode survival");
        b.cmd("/give @s minecraft:diamond_ore").cmd("/give @s minecraft:stone").cmd("/give @s minecraft:diamond").cmd("/give @s minecraft:iron_chestplate");
        for (var m : ms) b.cmd("/give @s fantasyweapons:" + m.oreId());
        for (var m : ms) b.cmd("/give @s fantasyweapons:" + m.rawId());
        b.cmd("/give @s fantasyweapons:arcane_refinery");
        b.wait(20).run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(20).screenshot("fit_inventory_a").closeScreen();
        b.cmd("/clear @s");
        for (var m : ms) b.cmd("/give @s fantasyweapons:" + m.refinedId());
        for (var set : com.fantasyweapons.armor.ArmorSet.values()) b.cmd("/give @s fantasyweapons:" + set.itemId(net.minecraft.world.item.ArmorItem.Type.HELMET))
                .cmd("/give @s fantasyweapons:" + set.itemId(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
        b.wait(20).run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(20).screenshot("fit_inventory_b").closeScreen();
        // the creative tab
        b.cmd("/gamemode creative").wait(10).run(mc -> {
            try {
                var f = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                f.setAccessible(true);
                f.set(null, com.fantasyweapons.registry.ModTabs.COLLECTION.get());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
        }).wait(20).screenshot("fit_creative").closeScreen();
    }

    /** The Legendary Crafting Table in the world, its screen with a recipe laid out as ghosts, partly filled, and forging. */
    /** The table at work with its own particles, its last flourish, and a table and a refinery broken. */
    private static void forgeFx(ScreenshotDirector.Builder b) {
        b.cmd("/gamerule sendCommandFeedback false").cmd("/gamemode creative").cmd("/time set midnight").hud(false).cmd("/kill @e[type=!player]")
                .cmd("/fill -6 -60 2 6 -56 10 minecraft:air").cmd("/kill @e[type=item]").cmd("/tp @s 0 -60 0 0 30").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:legendary_crafting_table").wait(10).slot(0);
        b.run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -61, 4.5), net.minecraft.core.Direction.UP,
                        new net.minecraft.core.BlockPos(0, -61, 4), false))).wait(20);
        b.run(mc -> mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            if (level.getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 4)) instanceof com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity t) {
                t.start(com.fantasyweapons.FantasyWeapons.id("forging/voidfang"), new net.minecraft.world.item.ItemStack(
                        com.fantasyweapons.registry.ModItems.WEAPONS.get("voidfang").get()), 120, java.util.List.of());
            }
        })).viewAt(0.5, 2.1, 2.1, 0.5, 1.4, 4.5).wait(30).screenshot("forgefx_a").wait(13).screenshot("forgefx_b").wait(17).screenshot("forgefx_c")
                .viewAt(2.4, 1.9, 2.6, 0.5, 1.45, 4.5).wait(20).screenshot("forgefx_d").wait(50).screenshot("forgefx_done").wait(10).screenshot("forgefx_done2");
        b.run(mc -> mc.getSingleplayerServer().execute(() -> {
            if (mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 4))
                    instanceof com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity t) t.output().clearContent();
        })).cmd("/time set noon").wait(5);
        // broken: debris over the whole table, a block's worth for each of its blocks
        b.viewAt(0.5, 2.4, 0.6, 0.5, 0.6, 4.5).wait(10).screenshot("break_table_before")
                .run(mc -> mc.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(0, -60, 4), net.minecraft.core.Direction.NORTH))
                .wait(2).screenshot("break_table_a").wait(6).screenshot("break_table_b").wait(30);
        b.cmd("/clear @s").cmd("/give @s fantasyweapons:arcane_refinery").wait(5).slot(0)
                .run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -61, 4.5), net.minecraft.core.Direction.UP,
                                new net.minecraft.core.BlockPos(0, -61, 4), false))).wait(20)
                .run(mc -> mc.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(0, -60, 4), net.minecraft.core.Direction.NORTH))
                .wait(2).screenshot("break_refinery_a").wait(30);
    }

    private static void forge(ScreenshotDirector.Builder b) {
        b.cmd("/gamerule sendCommandFeedback false").cmd("/gamemode creative").cmd("/time set noon").hud(false).cmd("/kill @e[type=!player]")
                .cmd("/fill -6 -60 2 6 -56 10 minecraft:air").cmd("/kill @e[type=item]").cmd("/tp @s 0 -60 0 0 30").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:legendary_crafting_table").wait(10).slot(0).hud(true).wait(5).screenshot("forge_hotbar").hud(false);
        // placed as a player would, on the ground two blocks ahead (the core at 0 -60 4, facing the player)
        b.run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -61, 4.5), net.minecraft.core.Direction.UP,
                        new net.minecraft.core.BlockPos(0, -61, 4), false))).wait(20).cmd("/setblock 3 -60 4 minecraft:crafting_table");
        // its outline, the whole table's, looking at the middle and at a side block
        b.playerView().look(0, 20).hud(true).wait(10).run(mc -> {
            var hit = mc.hitResult;
            String what = hit instanceof net.minecraft.world.phys.BlockHitResult bh ? mc.level.getBlockState(bh.getBlockPos()) + " at " + bh.getBlockPos() + " "
                    + bh.getLocation() + " shape " + mc.level.getBlockState(bh.getBlockPos()).getShape(mc.level, bh.getBlockPos()).bounds() : String.valueOf(hit == null ? null : hit.getType());
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("[outline] looking at {}", what);
        }).screenshot("forge_outline").look(13, 16).wait(5).screenshot("forge_outline_side")
                .look(-9, 6).wait(5).screenshot("forge_outline_totem").hud(false);
        b.cmd("/summon pig 1.8 -60 3.3 {NoAI:1b,Rotation:[90f,0f]}").viewAt(3.4, 1.2, 1.4, 1.0, 0.4, 3.8).wait(15).screenshot("forge_shadow").cmd("/kill @e[type=pig]");
        b.viewAt(0.5, 1.8, 0.4, 0.5, 1.0, 4.5).wait(30).screenshot("forge_block_front");
        b.viewAt(3.6, 2.0, 1.2, 0.5, 1.0, 4.5).wait(5).screenshot("forge_block_side");
        b.viewAt(-2.8, 2.6, 7.6, 0.5, 1.0, 4.5).wait(5).screenshot("forge_block_back");
        // crafting (a short forging started on the server), seen over a few seconds of its animation
        b.run(mc -> mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            if (level.getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 4)) instanceof com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity t) {
                t.start(com.fantasyweapons.FantasyWeapons.id("forging/voidfang"), new net.minecraft.world.item.ItemStack(
                        com.fantasyweapons.registry.ModItems.WEAPONS.get("voidfang").get()), 160, java.util.List.of());
            }
        })).viewAt(0.5, 2.3, 1.9, 0.5, 1.5, 4.5).wait(30).screenshot("forge_crafting_a").wait(40).screenshot("forge_crafting_b")
                .viewAt(2.9, 2.2, 2.0, 0.5, 1.5, 4.5).wait(40).screenshot("forge_crafting_c").wait(120).screenshot("forge_idle_again")
                .run(mc -> mc.getSingleplayerServer().execute(() -> {
                    if (mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 4))
                            instanceof com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity t) t.output().clearContent();
                }));
        // the screen: a recipe chosen, laid out with Fill, forged with Forge
        b.playerView().cmd("/clear @s").cmd("/give @s fantasyweapons:void_core 13").cmd("/give @s minecraft:crying_obsidian 3").cmd("/give @s minecraft:ender_eye")
                .cmd("/give @s minecraft:end_rod 2").cmd("/give @s minecraft:oak_log 3").cmd("/gamemode survival").wait(10).hud(true);
        b.run(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -60, 4.0), net.minecraft.core.Direction.NORTH,
                        new net.minecraft.core.BlockPos(0, -60, 4), false))).wait(15).screenshot("forge_screen_empty");
        // choose the Voidfang's recipe (the first icon)
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.LegendaryCraftingScreen s) {
                int left = (s.width - com.fantasyweapons.forge.LegendaryCraftingMenu.WIDTH) / 2, top = (s.height - com.fantasyweapons.forge.LegendaryCraftingMenu.HEIGHT) / 2;
                s.mouseClicked(left + com.fantasyweapons.forge.LegendaryCraftingMenu.INV_X + 8, top + 48, 0);
            }
        }).wait(10).screenshot("forge_screen_ghost");
        b.run(mc -> {
            if (!(mc.screen instanceof com.fantasyweapons.client.gui.LegendaryCraftingScreen s)) return;
            var all = com.fantasyweapons.forge.ForgeRecipe.ordered(mc.level);
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().getPath().equals("forging/voidfang")) {
                    mc.gameMode.handleInventoryButtonClick(s.getMenu().containerId, com.fantasyweapons.forge.LegendaryCraftingMenu.FILL + i);
                }
            }
        }).wait(10).screenshot("forge_screen_filled");
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.LegendaryCraftingScreen s) {
                mc.gameMode.handleInventoryButtonClick(s.getMenu().containerId, com.fantasyweapons.forge.LegendaryCraftingMenu.FORGE_BUTTON);
            }
        }).wait(30).screenshot("forge_screen_forging");
        // an ordinary recipe still crafts at once while the table forges: a log on the grid
        b.run(mc -> {
            if (!(mc.screen instanceof com.fantasyweapons.client.gui.LegendaryCraftingScreen s)) return;
            var menu = s.getMenu();
            for (int i = com.fantasyweapons.forge.LegendaryCraftingMenu.INV_START; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).getItem().is(net.minecraft.world.item.Items.OAK_LOG)) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, com.fantasyweapons.forge.LegendaryCraftingMenu.GRID_START + 40, 1,
                            net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                    break;
                }
            }
        }).wait(10).screenshot("forge_screen_planks");
        // skip ahead (dev only): to the last stage, then done
        b.run(mc -> mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            if (level.getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 4)) instanceof com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity t) {
                try {
                    var f = com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity.class.getDeclaredField("progress");
                    f.setAccessible(true);
                    f.setInt(t, t.total() - 50);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        })).wait(10).screenshot("forge_screen_last_stage").wait(60).screenshot("forge_screen_forged");
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.LegendaryCraftingScreen s) {
                mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, com.fantasyweapons.forge.LegendaryCraftingMenu.OUTPUT, 0,
                        net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
            }
        }).wait(10).screenshot("forge_screen_taken").closeScreen().hud(false);
        b.run(mc -> {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).getItem() instanceof com.fantasyweapons.weapon.FantasyWeaponItem w) {
                    com.fantasyweapons.FantasyWeapons.LOGGER.warn("[forge] forged {}", w.definition().id());
                }
            }
        });
        // the moment after forging: the table still glowing
        b.viewAt(0.5, 1.8, 0.4, 0.5, 1.0, 4.5).wait(15).screenshot("forge_block_forged");
    }

    /** The Wayfarer's Codex: its icon, held shut, opened in first person, then read spread by spread with a page turning and a tooltip. */
    private static void codex(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/fill -6 -60 -6 6 -55 10 minecraft:air").cmd("/kill @e[type=item]").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:wayfarers_codex").wait(10).slot(0).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10).screenshot("codex_hotbar");
        b.viewAt(-1.6, 1.4, 1.6, 0, 1.0, 0).wait(10).screenshot("codex_third").playerView().wait(5).screenshot("codex_shut");
        b.run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(20).screenshot("codex_open_1s").wait(30)
                .screenshot("codex_open_2s").wait(25).screenshot("codex_open_4s").wait(25).screenshot("codex_read");
        // clicking "Legendary Ores" in the contents turns to that chapter
        b.run(mc -> com.fantasyweapons.client.guide.CodexPages.pinned = new float[]{1, 20, 49}).wait(3).run(mc -> {
            mc.screen.mouseClicked(0, 0, 0);
            com.fantasyweapons.client.guide.CodexPages.pinned = null;
        }).wait(16).screenshot("codex_clicked").run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("[codex] after the click: spread {}, ores at {}",
                com.fantasyweapons.client.guide.Codex.spread(), com.fantasyweapons.client.guide.Codex.book().spreadOf("ores")));
        for (String anchor : new String[]{"path", "ores", "realm_NETHER", "refinery", "table", "weapons", "weapon_voidfang", "armor", "mastery", "deeds"}) {
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf(anchor), false)).wait(3)
                    .screenshot("codex_" + anchor + "_turning").wait(2).screenshot("codex_" + anchor + "_turning2").wait(12).screenshot("codex_" + anchor);
        }
        // the Voidfang's second spread (its forging pattern and armor), with the mouse over a cell of the pattern
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("weapon_voidfang") + 1, false))
                .wait(16).screenshot("codex_voidfang_forging");
        b.run(mc -> com.fantasyweapons.client.guide.CodexPages.pinned = new float[]{0, 56, 60}).wait(4).screenshot("codex_tooltip");
        b.run(mc -> com.fantasyweapons.client.guide.CodexPages.pinned = null).closeScreen().wait(4).screenshot("codex_closing").wait(20)
                .screenshot("codex_closed");
    }

    /** A page turned (each tick of it), a spread with recipes held still, then the book closed (each tick of it). */
    private static void codexFlip(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/give @s fantasyweapons:wayfarers_codex").wait(10).slot(0).hud(true)
                .cmd("/tp @s 0 -60 0 0 0").wait(10).run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110)
                .run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("path"), false)).wait(20)
                .screenshot("flip_before");
        b.run(mc -> com.fantasyweapons.client.guide.Codex.next());
        // the turn held at points along it (the test display draws too few frames to catch a moving one)
        for (float t : new float[]{0.15f, 0.35f, 0.5f, 0.65f, 0.85f, 0.97f}) {
            b.run(mc -> com.fantasyweapons.client.guide.Codex.frozenFlip = t).wait(8).screenshot("flip_" + (int) (t * 100));
        }
        b.run(mc -> com.fantasyweapons.client.guide.Codex.frozenFlip = -1).wait(10).screenshot("flip_after");
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("refinery"), false)).wait(20)
                .screenshot("flip_still_a").wait(7).screenshot("flip_still_b");
        b.closeScreen();
        for (int i = 1; i <= 34; i += 2) b.wait(2).screenshot("close_" + (i < 10 ? "0" : "") + i);
        b.wait(20).screenshot("close_end");
    }

    /** One spread of the Codex (the path's first), for checking the pages quickly. */
    private static void codexOne(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/give @s fantasyweapons:wayfarers_codex").wait(10).slot(0).hud(true)
                .cmd("/tp @s 0 -60 0 0 0").wait(10).run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110)
                .run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("deeds"), false)).wait(16)
                .screenshot("codexone").closeScreen().wait(20);
    }

    /** The deeds: a deed's, a feat's and a legend's toasts, their chat lines, the advancements tab and the Codex's Deeds pages. */
    private static void deeds(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/advancement revoke @s from fantasyweapons:root").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:wayfarers_codex").wait(40).slot(0).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.cmd("/advancement grant @s only fantasyweapons:first_blood").wait(12).screenshot("deed_task").wait(30).screenshot("deed_task_settled");
        b.wait(110).cmd("/advancement grant @s only fantasyweapons:weapon_voidfang").wait(12).screenshot("deed_goal").wait(30).screenshot("deed_goal_settled");
        b.wait(130).cmd("/advancement grant @s only fantasyweapons:the_thirteen").wait(10).screenshot("deed_challenge").wait(30)
                .screenshot("deed_challenge_settled").wait(40).screenshot("deed_challenge_late");
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""))).wait(5).screenshot("deed_chat").closeScreen();
        // more deeds done, for the tab and the Codex
        for (String id : new String[]{"open_the_codex", "glimmer", "ore_solarite_crystal", "ore_void_crystal", "ore_infernite", "arcane_refinery",
                "first_core", "legendary_table", "first_forging", "level_10", "first_ability"}) {
            b.cmd("/advancement grant @s only fantasyweapons:" + id);
        }
        b.wait(20).run(mc -> {
            var advancements = mc.getConnection().getAdvancements();
            mc.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
            var root = advancements.getTree().get(com.fantasyweapons.FantasyWeapons.id("root"));
            if (root != null) advancements.setSelectedTab(root.holder(), true);
        }).wait(10).screenshot("deed_tab").closeScreen().wait(260);
        // the Codex: its tabs, and the Deeds chapter
        b.run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110).screenshot("deed_codex_contents");
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("deeds"), false)).wait(16)
                .screenshot("deed_codex_1");
        for (int i = 1; i <= 3; i++) {
            int n = i;
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("deeds") + n, false))
                    .wait(16).screenshot("deed_codex_" + (n + 1));
        }
        // a click on the Ores tab turns there
        b.run(mc -> com.fantasyweapons.client.guide.CodexPages.pinned = new float[]{1, 124, 24}).wait(4).screenshot("deed_codex_tab_hover").run(mc -> {
            mc.screen.mouseClicked(0, 0, 0);
            com.fantasyweapons.client.guide.CodexPages.pinned = null;
        }).wait(16).screenshot("deed_codex_tab_clicked").closeScreen().wait(30);
    }

    /** Every ore item drawn twice in a row, then once each between other items: for checking items drawn in a screen. */
    private static void oreIcons(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").wait(20).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("ores")) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                super.render(g, mx, my, pt);
                int i = 0;
                for (var m : com.fantasyweapons.material.Material.values()) {
                    var stack = new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.ORES.get(m).get());
                    g.renderFakeItem(stack, 20 + i * 20, 20);
                    g.renderFakeItem(stack, 20 + i * 20, 40);
                    g.renderFakeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE), 20 + i * 20, 60);
                    g.renderFakeItem(stack, 20 + i * 20, 80);
                    g.drawString(font, "x", 20 + i * 20, 100, 0xFFFFFF);
                    g.renderFakeItem(stack, 20 + i * 20, 110);
                    i++;
                }
            }
        })).wait(10).screenshot("oreicons").closeScreen();
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("rows")) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                super.render(g, mx, my, pt);
                var mats = com.fantasyweapons.material.Material.values();
                for (int col = 0; col < 5; col++) {
                    int x = 10 + col * 80;
                    if (col == 0) g.enableScissor(x - 2, 0, x + 70, 300);
                    for (int i = 0; i < mats.length; i++) {
                        int y = 10 + i * 17;
                        var stack = new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.ORES.get(mats[i]).get());
                        if (col != 3) com.fantasyweapons.client.hud.UiDraw.rect(g, x, y, x + 70, y + 16, 0x26000000);
                        g.renderFakeItem(stack, x + 2, y);
                        if (col == 2) com.fantasyweapons.client.hud.UiDraw.rect(g, x + 2, y, x + 18, y + 16, 0x30101010);
                        if (col != 1 && col != 4) com.fantasyweapons.client.hud.UiDraw.text(g, "row " + i, x + 21, y + 3, 0xFFFFFFFF, 0.62f, true);
                        if (col == 4) g.drawString(font, "r" + i, x + 21, y + 3, 0xFFFFFF, true);
                    }
                    if (col == 0) g.disableScissor();
                }
            }
        })).wait(10).screenshot("orerows").closeScreen();
        b.cmd("/fw quest reset").wait(10).run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen())).wait(5)
                .run(mc -> clickPanel(mc, 416, 236, 15, 30 + 25 + 5)).wait(10).screenshot("qs_notoast");
        b.cmd("/fw quest complete hunter_50").wait(20).screenshot("qs_toast").wait(200).screenshot("qs_after_toast").closeScreen();
    }

    /** The inventory's journal and class buttons, and gear tooltips telling whether the player's class can use it. */
    private static void gear(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").cmd("/time set noon").cmd("/clear @s").cmd("/fw class set none").cmd("/fw quest reset")
                .cmd("/fw quest complete first_page").cmd("/give @s fantasyweapons:voidfang").cmd("/give @s fantasyweapons:solaris_chestplate")
                .wait(30).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(10).screenshot("gear_inventory");
        b.run(mc -> {
            var screen = (net.minecraft.client.gui.screens.inventory.InventoryScreen) mc.screen;
            screen.getRecipeBookComponent().toggleVisibility();
        }).wait(10).screenshot("gear_inventory_book").closeScreen().wait(5);
        java.util.function.Consumer<String> tooltips = name -> b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("tips")) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                super.render(g, mx, my, pt);
                g.renderTooltip(font, new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.WEAPONS.get("voidfang").get()), 4, 14);
                g.renderTooltip(font, new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.ARMOR.get(com.fantasyweapons.armor.ArmorSet.SOLARIS)
                        .get(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE).get()), width / 2 + 4, 14);
            }
        })).wait(10).screenshot(name).closeScreen().wait(5);
        tooltips.accept("gear_tips_none");
        b.cmd("/fw class set assassin").wait(10);
        tooltips.accept("gear_tips_assassin");
    }

    /** Clicks the open screen at gui coordinates measured from a centred panel {@code w} by {@code h}. */
    private static void clickPanel(net.minecraft.client.Minecraft mc, int w, int h, int x, int y) {
        int left = (mc.getWindow().getGuiScaledWidth() - w) / 2, top = (mc.getWindow().getGuiScaledHeight() - h) / 2;
        if (mc.screen != null) mc.screen.mouseClicked(left + x, top + y, 0);
    }

    private static void classScreen(ScreenshotDirector.Builder b, java.util.function.Consumer<com.fantasyweapons.client.gui.ClassScreen> action) {
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.ClassScreen c) action.accept(c);
        });
    }

    /** The class menu a page at a time; then the HUD, a toast and the menus at GUI scales 2, 3 and 4. */
    /** Impact frames: an ultimate's four stills and its snap back, then an ordinary ability's flash, each pinned in place. */
    private static void impact(ScreenshotDirector.Builder b) {
        String[][] casts = {{"solaris", "celestial_inferno", "10,100,170,240,300,420"}, {"voidfang", "rift_tear", "10,70,150"}};
        for (String[] c : casts) {
            var a = com.fantasyweapons.weapon.Weapons.get(c[0]).ability(c[1]);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + c[0] + " 100").wait(130).slot(0).cmd("/fw points 200").cmd("/fw cooldowns").hud(true);
            for (int i = 0; i < 3; i++) {
                b.cmd("/summon minecraft:husk " + (i - 1) * 2.5 + " -60 6 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            }
            b.wait(10).camera(CameraType.FIRST_PERSON).look(0, 12).select(c[1]).wait(5)
                    .run(mc -> com.fantasyweapons.client.fx.ImpactFrames.debugElapsed = 10L);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp().wait(3);
            for (String t : c[2].split(",")) {
                long ms = Long.parseLong(t);
                b.run(mc -> com.fantasyweapons.client.fx.ImpactFrames.debugElapsed = ms).wait(10).screenshot("impact_" + c[1] + "_" + t);
            }
            b.run(mc -> com.fantasyweapons.client.fx.ImpactFrames.debugElapsed = null).wait(60);
        }
    }

    private static void classPages(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").cmd("/time set noon").cmd("/clear @s").cmd("/fw class set none").cmd("/fw quest reset").wait(20).hud(true)
                .cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.ClassScreen())).wait(12).screenshot("cp_assassin");
        for (var pc : new com.fantasyweapons.playerclass.PlayerClass[]{com.fantasyweapons.playerclass.PlayerClass.BERSERKER,
                com.fantasyweapons.playerclass.PlayerClass.PALADIN, com.fantasyweapons.playerclass.PlayerClass.MAGE}) {
            classScreen(b, c -> c.devPage(pc));
            b.wait(3).screenshot("cp_" + pc.id() + "_turning").wait(12).screenshot("cp_" + pc.id());
        }
        classScreen(b, c -> c.devPage(com.fantasyweapons.playerclass.PlayerClass.PALADIN));
        b.wait(12);
        classScreen(b, c -> c.devChoose());
        b.wait(6).screenshot("cp_confirm");
        classScreen(b, c -> c.devChoose());
        b.wait(20).screenshot("cp_chosen").closeScreen().wait(10);
        b.cmd("/fw class set assassin").cmd("/give @s fantasyweapons:voidfang").cmd("/fw quest complete first_page").wait(5).slot(0);
        for (int scale : new int[]{2, 3, 4}) {
            int s = scale;
            b.run(mc -> {
                mc.options.guiScale().set(s);
                mc.resizeDisplay();
            }).cmd("/fw quest complete " + (s == 2 ? "unleashed" : s == 3 ? "hunter_50" : "into_the_nether")).wait(30).screenshot("scale" + s + "_hud");
            b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen())).wait(12).screenshot("scale" + s + "_journal");
            b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.ClassScreen())).wait(12).screenshot("scale" + s + "_classes").closeScreen().wait(5);
        }
        b.run(mc -> {
            mc.options.guiScale().set(0);
            mc.resizeDisplay();
        }).wait(5);
    }

    private static void journal(ScreenshotDirector.Builder b, java.util.function.Consumer<com.fantasyweapons.client.gui.QuestScreen> action) {
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.QuestScreen q) action.accept(q);
        });
    }

    /** The class menu offered on first joining, the quests' toast, the Quest Journal's map, and the class menu. */
    private static void quests(ScreenshotDirector.Builder b) {
        // a world never joined since the class menu came: it opens by itself
        b.wait(70).screenshot("class_first_join").closeScreen().wait(5);
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/fw quest reset").cmd("/fw class set none").wait(20).hud(true)
                .cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.cmd("/fw quest complete first_page").wait(12).screenshot("quest_toast").wait(30).screenshot("quest_toast_settled").wait(120);
        b.cmd("/fw quest claim first_page").cmd("/give @s minecraft:diamond_pickaxe").cmd("/fw quest complete prospect_void_crystal")
                .cmd("/fw quest complete prospect_solarite_crystal").cmd("/fw quest claim prospect_solarite_crystal")
                .cmd("/fw quest complete hunter_50").cmd("/fw quest claim hunter_50").cmd("/fw quest complete unleashed").wait(260);
        b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen())).wait(10).screenshot("quest_map_first");
        journal(b, q -> q.devHover("arcane_engineering"));
        b.wait(4).screenshot("quest_map_hover");
        journal(b, q -> {
            com.fantasyweapons.client.gui.QuestScreen.devMouse = new int[]{0, 0};
            q.devSelect("diamond_pick");
        });
        b.wait(14).screenshot("quest_page_claim");
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.ORES));
        b.wait(8).screenshot("quest_ores");
        journal(b, q -> q.devExpand("ore_void_crystal"));
        b.wait(14).screenshot("quest_ores_open");
        journal(b, q -> q.devSelect("prospect_void_crystal"));
        b.wait(14).screenshot("quest_ores_page");
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.WEAPONS));
        b.wait(8).screenshot("quest_weapons");
        journal(b, q -> q.devExpand("weapon_solaris"));
        b.wait(14).screenshot("quest_weapons_open");
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.HUNTS));
        b.wait(8).screenshot("quest_hunts");
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.MASTERY));
        b.wait(8).screenshot("quest_mastery");
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.ARMOR));
        b.wait(8).screenshot("quest_armor");
        b.run(mc -> com.fantasyweapons.client.gui.QuestScreen.devMouse = null).closeScreen().wait(10);
        // the class menu: no class yet, then the Assassin's seal asked twice, then the wait
        b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.ClassScreen())).wait(10).screenshot("class_menu");
        classScreen(b, c -> c.devChoose());
        b.wait(4).screenshot("class_confirm");
        classScreen(b, c -> c.devChoose());
        b.wait(20).screenshot("class_chosen").closeScreen().wait(10);
    }

    /** Every spread of the Codex in turn, for checking that each page's writing fits. */
    private static void codexAll(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/give @s fantasyweapons:wayfarers_codex").wait(10).slot(0).hud(true)
                .cmd("/tp @s 0 -60 0 0 0").wait(10).run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110);
        for (int i = 0; i < 60; i++) {
            int spread = i;
            b.run(mc -> {
                if (com.fantasyweapons.client.guide.Codex.book() != null && spread < com.fantasyweapons.client.guide.Codex.book().spreads()) {
                    com.fantasyweapons.client.guide.Codex.turnTo(spread, false);
                }
            }).wait(12).run(mc -> {
                if (com.fantasyweapons.client.guide.Codex.book() != null && spread < com.fantasyweapons.client.guide.Codex.book().spreads()) {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, String.format(java.util.Locale.ROOT, "fw_codexall_%02d.png", spread),
                            mc.getMainRenderTarget(), msg -> { });
                }
            });
        }
    }

    /** A Voidfang infused with two Void Cores in the inventory's crafting grid: the tooltips, the grid and the level-up. */
    private static void infuse(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").cmd("/clear @s").cmd("/fw give voidfang 5").wait(140).cmd("/give @s fantasyweapons:void_core 2")
                .cmd("/give @s fantasyweapons:voidfang_chestplate").wait(10).slot(0).hud(true);
        b.run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getInventory().getItem(0)))).wait(10).screenshot("infuse_weapon_tooltip").closeScreen();
        b.run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getInventory().getItem(1)))).wait(10).screenshot("infuse_core_tooltip").closeScreen();
        b.run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getInventory().getItem(2)))).wait(10).screenshot("infuse_armor_tooltip").closeScreen();
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(10).run(mc -> {
            int id = mc.player.inventoryMenu.containerId;
            var pickup = net.minecraft.world.inventory.ClickType.PICKUP;
            mc.gameMode.handleInventoryMouseClick(id, 36, 0, pickup, mc.player); // the weapon...
            mc.gameMode.handleInventoryMouseClick(id, 1, 0, pickup, mc.player); // ...into the grid
            mc.gameMode.handleInventoryMouseClick(id, 37, 0, pickup, mc.player); // the cores...
            mc.gameMode.handleInventoryMouseClick(id, 2, 1, pickup, mc.player); // ...one each into the grid
            mc.gameMode.handleInventoryMouseClick(id, 3, 1, pickup, mc.player);
        }).wait(10).screenshot("infuse_grid");
        b.run(mc -> mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 0, 0,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player)).wait(5).closeScreen().wait(12).screenshot("infuse_levelup").wait(70).screenshot("infuse_banner").wait(40);
        b.run(mc -> {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).getItem() instanceof com.fantasyweapons.weapon.FantasyWeaponItem) {
                    com.fantasyweapons.FantasyWeapons.LOGGER.warn("[infuse] crafted weapon now level {}",
                            com.fantasyweapons.weapon.FantasyWeaponItem.data(inv.getItem(i)).level());
                }
            }
        });
    }

    private static void rigid(ScreenshotDirector.Builder b) {
        b.run(mc -> com.fantasyweapons.client.render.WeaponGeoModel.rigidStats = new java.util.TreeMap<>());
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            String w = def.id();
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 100").wait(130).slot(0).cmd("/fw points 300");
            b.playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 10).wait(10);
            for (int i = 0; i < 3; i++) b.swing().wait(14);
            b.wait(30);
            for (var a : def.castables()) {
                b.cmd("/fw cooldowns").select(a.id()).wait(6).abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp().wait(70);
            }
        }
        b.run(mc -> {
            var stats = com.fantasyweapons.client.render.WeaponGeoModel.rigidStats;
            stats.forEach((k, v) -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("[rigid] {} {}", String.format("%.4f", v), k));
        });
    }

    /** Each form of a weapon ("weapon,...") held in third person (side, front, back) and in first person. */
    private static void forms(ScreenshotDirector.Builder b, String list) {
        for (String w : list.split(",")) {
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            b.cmd("/clear @s").cmd("/fw give " + w + " 100").wait(130).slot(0).hud(false).look(0, 0);
            var order = new java.util.ArrayList<>(def.forms());
            java.util.Collections.reverse(order);
            for (var form : order) {
                b.playerView().cmd("/fw cooldowns").hud(true).camera(CameraType.FIRST_PERSON).wait(5).form(form.id()).wait(80).hud(false);
                b.viewFrom(4.2, 0.5, 0.6).wait(4).screenshot("form_" + w + "_" + form.id() + "_right");
                b.viewFrom(0.8, 0.5, 4.2).wait(4).screenshot("form_" + w + "_" + form.id() + "_front");
                b.viewAt(1.4, 1.5, -3.6, 0, 1.2, 2.5).wait(4).screenshot("form_" + w + "_" + form.id() + "_back");
                b.playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 10).wait(4).screenshot("form_" + w + "_" + form.id() + "_fp").look(0, 0);
            }
        }
        b.playerView();
    }

    /** The weapon HUD of each weapon ("weapon,...") at level 1 (nothing awakened) and level 100. */
    private static void hud(ScreenshotDirector.Builder b, String list) {
        for (String w : list.split(",")) {
            for (int lvl : new int[]{1, 100}) {
                b.cmd("/clear @s").cmd("/fw give " + w + " " + lvl).wait(40).slot(0).playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 30)
                        .wait(60).screenshot("hud_" + w + "_" + lvl);
            }
        }
    }

    /** Each of a weapon's three attacks in first person ("weapon,..."), a frame every tick. */
    private static void fpSwings(ScreenshotDirector.Builder b, String list) {
        for (String w : list.split(",")) {
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 1").wait(40).slot(0)
                    .playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 5).wait(30);
            for (int v = 0; v < 3; v++) {
                int variant = v;
                b.run(mc -> com.fantasyweapons.client.anim.AnimTracker.debugVariant(mc.player, variant)).wait(2).swing();
                for (int i = 0; i < 16; i++) b.wait(1).screenshot("fps_" + w + "_v" + v + "_" + String.format("%02d", i));
                b.wait(30);
            }
            b.run(mc -> com.fantasyweapons.client.anim.AnimTracker.debugVariant(mc.player, -1));
        }
    }

    /** First-person orientation calibration: the held weapon in a grid of fixed yaw / pitch / twist poses. */
    private static void fpCalibrate(ScreenshotDirector.Builder b, String w) {
        b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(40).slot(0).playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 5).wait(30);
        float[][] poses = {{0, 0.15f, 0, 0, 0, 0}, {0, 0.15f, 0, 180, 0, 0}, {0, 0.15f, 0, 90, 0, 0}, {0, 0.15f, 0, -90, 0, 0}, {0, 0.15f, 0, 0, 45, 0},
                {0, 0.15f, 0, 180, 45, 0}, {0, 0.15f, 0, 90, 45, 0}, {0, 0.15f, 0, -90, 45, 0}, {0, 0.15f, 0, 0, 89, 0}, {0, 0.15f, 0, 44.6f, 55.6f, 0}};
        int n = 0;
        for (float[] p : poses) {
            int idx = n++;
            b.run(mc -> com.fantasyweapons.client.render.FirstPersonAttacks.debugPose = p).wait(3)
                    .screenshot(String.format("cal_%s_%02d_y%d_e%d_r%d", w, idx, (int) p[3], (int) p[4], (int) p[5]));
        }
        b.run(mc -> com.fantasyweapons.client.render.FirstPersonAttacks.debugPose = null);
    }

    /** Big area abilities ("weapon/ability,..."), seen from afar and from underneath (looking up). */
    private static void wide(ScreenshotDirector.Builder b, String list) {
        for (String key : list.split(",")) {
            String[] u = key.split("/");
            var a = com.fantasyweapons.weapon.Weapons.get(u[0]).ability(u[1]);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + u[0] + " 100").wait(130).slot(0).cmd("/fw points 200").hud(false);
            for (int i = 0; i < 5; i++) {
                b.cmd("/summon minecraft:husk " + (i - 2) * 2 + " -60 " + (4 + (i % 2) * 2)
                        + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            }
            b.wait(20).camera(CameraType.FIRST_PERSON).look(0, 30).select(u[1]).wait(5).viewFrom(24, 7, 21, 6);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int i = 0; i < 6; i++) b.wait(i < 2 ? 6 : 14).screenshot("wide_" + u[1] + "_far" + i);
            b.playerView().camera(CameraType.FIRST_PERSON).look(0, -65).wait(10).screenshot("wide_" + u[1] + "_under").wait(20).screenshot("wide_" + u[1] + "_under2");
            b.look(0, 20).wait(100);
        }
        b.playerView();
    }

    /** Gravebite: the lantern swinging as the weapon is swung and turned, Grave Chains coiling, and the 3D ghosts. */
    private static void gravebite(ScreenshotDirector.Builder b) {
        b.cmd("/fw give gravebite 100").wait(130).slot(0).cmd("/fw points 200").hud(false).camera(CameraType.FIRST_PERSON).look(0, 0).wait(5);
        b.viewFrom(-2.6, 0.9, 1.4, 1.5).wait(10).screenshot("gb_lantern_idle").swing().wait(3).screenshot("gb_lantern_swing1").wait(3)
                .screenshot("gb_lantern_swing2").wait(4).screenshot("gb_lantern_swing3");
        for (int i = 0; i < 4; i++) b.look(i * 45 + 45, 0).wait(2).screenshot("gb_lantern_turn" + i);
        b.look(0, 0).wait(30);
        String[] abilities = {"grave_chains", "soul_volley", "soul_harvest", "deaths_maw", "legion_of_the_damned"};
        for (String id : abilities) {
            var a = com.fantasyweapons.weapon.Weapons.get("gravebite").ability(id);
            b.cmd("/kill @e[type=minecraft:husk]").cmd("/kill @e[type=minecraft:item]").wait(5);
            b.cmd("/summon minecraft:husk 0.5 -60 5.5 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            b.cmd("/summon minecraft:husk 3 -60 7 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            b.wait(5).playerView().camera(CameraType.FIRST_PERSON).look(0, 10).cmd("/fw cooldowns").select(id).wait(5).viewAt(6.5, 2.2, 1.5, 1.5, 0.8, 6);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int i = 0; i < 6; i++) b.wait(i < 3 ? 4 : 8).screenshot("gb_" + id + "_" + i);
            b.wait(40);
        }
        b.playerView();
    }

    /** The Grave Chains visual alone (client side) on a dummy that does not die, every few ticks, close up. */
    private static void graveChains(ScreenshotDirector.Builder b) {
        b.cmd("/fw give gravebite 1").wait(30).slot(0).hud(false).look(0, 0).camera(CameraType.FIRST_PERSON);
        b.cmd("/summon minecraft:husk 0.5 -60 5.5 {NoAI:1b,Invulnerable:1b}").wait(10);
        b.viewAt(3.2, 1.8, 2.6, 0.5, 0.9, 5.5);
        b.run(mc -> {
            var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, mc.player.getBoundingBox().inflate(12)).get(0);
            com.fantasyweapons.client.fx.FxDispatcher.dispatch(com.fantasyweapons.network.FxPayload.of(com.fantasyweapons.network.FxIds.GRAVEBITE_CHAINS)
                    .caster(mc.player.getId()).pos(husk.position()).scale(3f).power(70).entities(husk.getId()).seed(1234L).build());
        });
        for (int i = 0; i < 12; i++) b.wait(i < 8 ? 3 : 8).screenshot("gc_" + String.format("%02d", i));
        b.playerView();
    }

    /** Abilities ("weapon/ability,...") cast at two fresh dummies, watched from close by, over about three seconds. */
    private static void near(ScreenshotDirector.Builder b, String list) {
        for (String key : list.split(",")) {
            String[] u = key.split("/");
            var a = com.fantasyweapons.weapon.Weapons.get(u[0]).ability(u[1]);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + u[0] + " 100").wait(130).slot(0).cmd("/fw points 200").hud(false);
            b.cmd("/summon minecraft:husk 0.5 -60 5.5 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            b.cmd("/summon minecraft:husk 3 -60 7 {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            b.wait(10).camera(CameraType.FIRST_PERSON).look(0, 25).select(u[1]).wait(5).viewAt(8, 4.5, 0.5, 1, 0.3, 6);
            b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int i = 0; i < 8; i++) b.wait(i < 4 ? 5 : 10).screenshot("near_" + u[1] + "_" + i);
            b.viewAt(3.5, 1.4, 3, 1, 0.4, 6).wait(2).screenshot("near_" + u[1] + "_close");
            b.playerView().wait(60);
        }
        b.playerView();
    }

    /** Every weapon's swing: from behind the player (third person) and through its eyes, a frame every two ticks. */
    private static void swings(ScreenshotDirector.Builder b, java.util.List<String> only) {
        var all = com.fantasyweapons.weapon.Weapons.all();
        b.hud(false).look(0, 0);
        for (var def : all) {
            String w = def.id();
            if (only != null && !only.contains(w)) continue;
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).wait(30);
            b.viewAt(1.6, 1.3, -3.4, 0, 1.2, 2.5).wait(3).swing();
            for (int i = 0; i < 6; i++) b.wait(2).screenshot("sw_" + w + "_tp" + i);
            b.wait(30).viewAt(0.4, 4.5, -1.2, 0, 1, 1.5).wait(3).swing();
            for (int i = 0; i < 6; i++) b.wait(2).screenshot("sw_" + w + "_top" + i);
            b.wait(30).playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(3).swing();
            for (int i = 0; i < 5; i++) b.wait(2).screenshot("sw_" + w + "_fp" + i);
            b.hud(false).wait(25);
        }
        b.playerView();
    }

    /** Every castable ability of every weapon, at release, mid-way and late, from a three-quarter view. */
    private static void allAbilities(ScreenshotDirector.Builder b) {
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            String w = def.id();
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 100").wait(130).slot(0).cmd("/fw points 300").hud(false);
            for (var a : def.castables()) {
                b.cmd("/kill @e[type=!player]").wait(3);
                for (int i = 0; i < 3; i++) {
                    b.cmd("/summon minecraft:husk " + (i - 1) * 2.5 + " -60 " + (6 + (i % 2) * 1.5)
                            + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
                }
                b.playerView().camera(CameraType.FIRST_PERSON).look(0, 12).cmd("/fw cooldowns").select(a.id()).wait(6).viewAt(7.5, 3.2, -1.5, 0, 0.8, 4);
                b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
                boolean ult = a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE;
                b.wait(3).screenshot("all_" + w + "_" + a.id() + "_a").wait(8).screenshot("all_" + w + "_" + a.id() + "_b").wait(14)
                        .screenshot("all_" + w + "_" + a.id() + "_c");
                if (ult) b.wait(50).screenshot("all_" + w + "_" + a.id() + "_d");
                b.wait(ult ? 120 : 30);
            }
        }
        b.playerView();
    }

    /** Through the player's own eyes: each weapon idle, swung, and every ability charged and released. */
    private static void fpAll(ScreenshotDirector.Builder b, java.util.List<String> only) {
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            String w = def.id();
            if (only != null && !only.contains(w)) continue;
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/fw give " + w + " 100").wait(130).slot(0).cmd("/fw points 300");
            b.playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 12).wait(5).screenshot("fp_" + w + "_idle");
            if (def.hasForms()) {
                b.form(def.forms().get(1).id()).wait(30).screenshot("fp_" + w + "_idle_form");
                b.swing().wait(4).screenshot("fp_" + w + "_swing_form").wait(20);
                b.form(def.forms().get(0).id()).wait(30);
            }
            b.swing().wait(4).screenshot("fp_" + w + "_swing").wait(20);
            for (var a : def.castables()) {
                b.cmd("/kill @e[type=!player]").wait(3);
                for (int i = 0; i < 3; i++) {
                    b.cmd("/summon minecraft:husk " + (i - 1) * 2.5 + " -60 " + (6 + (i % 2) * 1.5)
                            + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
                }
                b.cmd("/fw cooldowns").select(a.id()).wait(6).abilityDown().wait(Math.max(2, a.chargeTicks() / 2)).screenshot("fp_" + w + "_" + a.id() + "_0")
                        .wait(Math.max(1, a.chargeTicks() / 2 + 1)).abilityUp();
                boolean ult = a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE;
                b.wait(3).screenshot("fp_" + w + "_" + a.id() + "_1").wait(8).screenshot("fp_" + w + "_" + a.id() + "_2");
                if (ult) b.wait(30).screenshot("fp_" + w + "_" + a.id() + "_3");
                b.wait(ult ? 140 : 30);
            }
        }
        b.playerView();
    }

    /** Abilities ("weapon/ability,...") seen from behind the player, every 4 ticks for six seconds after the release. */
    private static void after(ScreenshotDirector.Builder b, String list) {
        String last = "";
        for (String key : list.split(",")) {
            String[] u = key.split("/");
            var a = com.fantasyweapons.weapon.Weapons.get(u[0]).ability(u[1]);
            if (!u[0].equals(last)) b.cmd("/clear @s").cmd("/fw give " + u[0] + " 100").wait(130).slot(0).cmd("/fw points 300");
            last = u[0];
            b.cmd("/kill @e[type=!player]").hud(false).playerView().camera(CameraType.FIRST_PERSON).look(0, 10).cmd("/fw cooldowns").select(u[1]).wait(6);
            b.viewAt(1.4, 1.5, -3.6, 0, 1.2, 2.5).abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int i = 0; i < 30; i++) {
                b.wait(4);
                if (i % 2 == 0) b.viewAt(1.4, 1.5, -3.6, 0, 1.2, 2.5).screenshot("af_" + u[1] + "_" + String.format("%02d", i));
                else b.viewAt(-2.6, 1.6, 3.4, 0, 1.2, 0).screenshot("af_" + u[1] + "_" + String.format("%02d", i));
            }
            b.wait(40);
        }
        b.playerView();
    }

    /** Abilities ("weapon/ability,...") through the player's eyes, every 3 ticks from the release for four seconds. */
    private static void fpAfter(ScreenshotDirector.Builder b, String list) {
        String last = "";
        for (String key : list.split(",")) {
            String[] u = key.split("/");
            var a = com.fantasyweapons.weapon.Weapons.get(u[0]).ability(u[1]);
            if (!u[0].equals(last)) b.cmd("/clear @s").cmd("/fw give " + u[0] + " 100").wait(130).slot(0).cmd("/fw points 300");
            last = u[0];
            b.cmd("/kill @e[type=!player]").playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 10).cmd("/fw cooldowns").select(u[1]).wait(10);
            if (u.length > 2) b.run(mc -> com.fantasyweapons.client.render.WeaponGeoModel.debugBone = u[2].replace('+', ','));
            b.screenshot("fa_" + u[1] + "_pre").wait(30).screenshot("fa_" + u[1] + "_pre2").wait(60).screenshot("fa_" + u[1] + "_pre3").abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
            for (int i = 0; i < 28; i++) b.wait(3).screenshot("fa_" + u[1] + "_" + String.format("%02d", i));
            b.run(mc -> com.fantasyweapons.client.render.WeaponGeoModel.debugBone = null).wait(40);
        }
        b.playerView();
    }

    /** Each weapon's three attacks, third person from behind and from above, and first person, at mid-swing. */
    private static void combos(ScreenshotDirector.Builder b, java.util.List<String> only) {
        for (var def : com.fantasyweapons.weapon.Weapons.all()) {
            String w = def.id();
            if (only != null && !only.contains(w)) continue;
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).wait(30).look(0, 0);
            int dur = Math.round(20f / def.weaponClass().attackSpeed()) + 4;
            for (int v = 0; v < 3; v++) {
                int vv = v;
                b.run(mc -> com.fantasyweapons.client.anim.AnimTracker.debugVariant(mc.player, vv)).hud(false);
                b.viewAt(1.6, 1.3, -3.4, 0, 1.2, 2.5).wait(2).swing().wait(Math.max(2, dur / 5)).screenshot("cb_" + w + "_v" + v + "_tp").wait(dur);
                b.viewAt(0.4, 4.5, -1.2, 0, 1, 1.5).wait(2).swing().wait(Math.max(2, dur / 5)).screenshot("cb_" + w + "_v" + v + "_top").wait(dur);
                b.playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(2).swing().wait(Math.max(2, dur / 6)).screenshot("cb_" + w + "_v" + v + "_fp").wait(dur);
            }
            b.run(mc -> com.fantasyweapons.client.anim.AnimTracker.debugVariant(mc.player, -1));
        }
        b.playerView();
    }

    /** Gravebite's ghosts close up: the Legion host circling, and a Soul Volley, from the side. */
    private static void ghosts(ScreenshotDirector.Builder b) {
        b.cmd("/fw give gravebite 100").wait(130).slot(0).cmd("/fw points 300").hud(false).camera(CameraType.FIRST_PERSON).look(0, 0);
        b.cmd("/summon minecraft:husk 0.5 -60 9 {NoAI:1b,Invulnerable:1b}").wait(5);
        b.cmd("/fw cooldowns").select("legion_of_the_damned").wait(5).viewAt(4.5, 2.2, 2.5, 0, 1.4, 0);
        var a = com.fantasyweapons.weapon.Weapons.get("gravebite").ability("legion_of_the_damned");
        b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
        for (int i = 0; i < 6; i++) b.wait(6).screenshot("gh_legion_" + i);
        b.viewAt(2.2, 1.9, 1.2, 0, 1.6, 0).wait(2).screenshot("gh_legion_close");
        b.wait(120).playerView().camera(CameraType.FIRST_PERSON).look(0, 0).cmd("/fw cooldowns").select("soul_volley").wait(5).viewAt(4, 1.8, 3.5, 0, 1.4, 4);
        var v = com.fantasyweapons.weapon.Weapons.get("gravebite").ability("soul_volley");
        b.abilityDown().wait(Math.max(2, v.chargeTicks() + 1)).abilityUp();
        for (int i = 0; i < 5; i++) b.wait(2).screenshot("gh_volley_" + i);
        b.playerView();
    }
}
