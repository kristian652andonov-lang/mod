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
            case "l10n" -> translated(b);
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
                else if (name.equals("regalia")) regalia(b);
                else if (name.equals("ultcodex")) ultCodex(b);
                else if (name.startsWith("setfx")) setFx(b, name.length() > 6 ? java.util.List.of(name.substring(6).split(",")) : null);
                else if (name.equals("infuse")) infuse(b);
                else if (name.equals("fit")) fit(b);
                else if (name.equals("forge")) forge(b);
                else if (name.equals("codex")) codex(b);
                else if (name.equals("codexall")) codexAll(b);
                else if (name.equals("deeds")) deeds(b);
                else if (name.equals("quests")) quests(b);
                else if (name.equals("oreicons")) oreIcons(b);
                else if (name.equals("tools")) tools(b);
                else if (name.equals("upgrader")) upgrader(b);
                else if (name.equals("fusion")) fusion(b);
                else if (name.equals("godui")) godUi(b);
                else if (name.equals("icon")) icon(b, com.fantasyweapons.weapons.god.WeaponOfGod.ID);
                else if (name.equals("godperf")) godPerf(b);
                else if (name.equals("walls")) walls(b);
                else if (name.equals("toolbook")) toolBook(b);
                else if (name.equals("toolcodex")) bookPages(b);
                else if (name.equals("toolpowers")) toolPowers(b);
                else if (name.equals("oresight")) {
                    b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/give @s fantasyweapons:void_crystal_pickaxe")
                            .cmd("/fill -9 -60 6 9 -46 13 stone").cmd("/setblock 2 -57 9 fantasyweapons:solarite_ore").cmd("/setblock -2 -58 8 fantasyweapons:infernite_ore")
                            .cmd("/tp @s 0.5 -60 2.5 0 0").wait(30).slot(0).wait(5).hud(true);
                    b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.ToolAbility()));
                    b.wait(40).screenshot("oresight_40");
                }
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
                else if (name.equals("tinted")) tinted(b);
                else if (name.equals("codexflip")) codexFlip(b);
                else if (name.startsWith("forms:")) forms(b, name.substring(6));
                else if (name.startsWith("hud:")) hud(b, name.substring(4));
                else if (name.startsWith("fpswings:")) fpSwings(b, name.substring(9));
                else if (name.startsWith("fpcal:")) fpCalibrate(b, name.substring(6));
                else if (name.startsWith("after:")) after(b, name.substring(6));
                else if (name.startsWith("fpafter:")) fpAfter(b, name.substring(8));
                else if (name.startsWith("combos:")) combos(b, java.util.List.of(name.substring(7).split(",")));
                else if (name.equals("bettercombat")) betterCombat(b, null);
                else if (name.equals("punchy")) punchy(b);
                else if (name.equals("punchyarmor")) punchyArmor(b);
                else if (name.equals("codexshield")) codexShield(b);
                else if (name.startsWith("bettercombat:")) betterCombat(b, java.util.List.of(name.substring(13).split(",")));
                else if (name.startsWith("bcframes:")) betterCombatFrames(b, name.substring(9).split(","));
                else if (name.startsWith("bcability:")) betterCombatAbility(b, name.substring(10).split(","));
                else if (name.startsWith("shaderfx:")) shaderFx(b, name.substring(9).split(","));
                else if (name.equals("settings")) settingsMenu(b);
                else if (name.equals("magehud")) mageHud(b);
                else if (name.startsWith("empower:")) empower(b, name.substring(8));
                else if (name.equals("impacthit")) impactHit(b);
                else if (name.equals("oregen")) oreGen(b);
                else if (name.startsWith("wreck:")) wreck(b, name.substring(6));
                else if (name.equals("targetmap")) targetMap(b);
                else if (name.equals("worldforge")) worldforge(b);
                else if (name.startsWith("remote:")) remote(b, name.substring(7));
                else if (name.startsWith("god:")) god(b, name.substring(4));
                else if (name.startsWith("mp:")) multiplayer(b, name.substring(3));
                else if (name.equals("circles")) circles(b);
                else if (name.equals("scout")) scout(b);
                else if (name.startsWith("local:")) local(b, name.substring(6));
                else if (name.startsWith("mages:")) mages(b, name.substring(6).split(","));
                else if (name.startsWith("fphold:")) fpHold(b, name.substring(7).split(","));
                else if (name.startsWith("holds:")) {
                    // "holds:terrain,a,b" films them on the ground of the fwterrain world
                    String spec = name.substring(6);
                    holdsOnTerrain = spec.startsWith("terrain,");
                    if (holdsOnTerrain) spec = spec.substring(8);
                    holds(b, java.util.Arrays.stream(spec.split(",")).map(com.fantasyweapons.weapon.Weapons::get).toList());
                }
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

    /**
     * Naturally generated ores: fresh chunks of a crimson forest in the Nether are generated, their Bloodstone ores counted
     * (and whether each has its block entity), then the rock round one is cleared and it is looked at.
     */
    private static void oreGen(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode spectator").cmd("/time set noon").wait(5);
        b.run(mc -> {
            var server = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                var sp = server.getPlayerList().getPlayer(id);
                var found = nether.findClosestBiome3d(h -> h.is(net.minecraft.world.level.biome.Biomes.CRIMSON_FOREST), new net.minecraft.core.BlockPos(4000, 64, 4000),
                        6400, 32, 64);
                if (found == null || sp == null) {
                    com.fantasyweapons.FantasyWeapons.LOGGER.warn("oregen: no crimson forest found");
                    return;
                }
                net.minecraft.core.BlockPos c = found.getFirst();
                java.util.List<net.minecraft.core.BlockPos> ores = new java.util.ArrayList<>();
                int withEntity = 0, chunks = 0;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        var chunk = nether.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
                        chunks++;
                        for (int y = 10; y < 118; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    net.minecraft.core.BlockPos q = new net.minecraft.core.BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                                    if (!(chunk.getBlockState(q).getBlock() instanceof com.fantasyweapons.material.MaterialOreBlock)) continue;
                                    ores.add(q);
                                    if (chunk.getBlockEntity(q) != null) withEntity++;
                                }
                            }
                        }
                    }
                }
                com.fantasyweapons.FantasyWeapons.LOGGER.warn("oregen: {} chunks, {} ores, {} with their block entity", chunks, ores.size(), withEntity);
                if (ores.isEmpty()) return;
                net.minecraft.core.BlockPos ore = ores.get(0);
                // open a little cave round the ore and stand in it, looking at the ore
                for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(ore.offset(-3, -1, -3), ore.offset(3, 2, 3))) {
                    if (!q.equals(ore)) nether.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(ore.offset(-3, -2, -3), ore.offset(3, -2, 3))) {
                    nether.setBlock(q, net.minecraft.world.level.block.Blocks.NETHERRACK.defaultBlockState(), 2);
                }
                sp.teleportTo(nether, ore.getX() + 2.5, ore.getY() - 1, ore.getZ() + 2.5, 135, 15);
            });
        });
        b.wait(400).hud(false).screenshot("oregen_a").wait(40).screenshot("oregen_b");
        b.cmd("/gamemode creative");
    }

    /**
     * A remote ability of an ultimate weapon cast on a spot 230 blocks ahead, watched from a hill (fwterrain world):
     * {@code weapon/ability/ticks,ticks,...} with the ticks after the cast to capture.
     */
    private static void remote(ScreenshotDirector.Builder b, String spec) {
        String[] parts = spec.split("/");
        String[] look = parts.length > 3 ? parts[3].split(",") : new String[0];
        String weapon = parts[0], ability = parts[1];
        String[] times = parts.length > 2 ? parts[2].split(",") : new String[]{"20", "60", "100", "104", "112", "130", "160", "200", "240", "280"};
        b.wait(40).cmd("/fw class set " + (weapon.contains("book") ? "mage" : "berserker")).closeScreen().wait(5).closeScreen()
                .cmd("/gamemode creative").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/gamerule doMobSpawning false")
                .cmd("/clear @s").cmd("/fw give " + weapon + " 1").cmd("/tp @s 0 " + (look.length > 0 ? look[0] : "260") + " " + (look.length > 2 ? look[2] : "-300") + " 0 " + (look.length > 1 ? look[1] : "-12")).wait(20)
                .run(mc -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }).wait(300).slot(0).closeScreen().cmd("/fw cooldowns")
                .cmd("/tp @s 0 " + (look.length > 0 ? look[0] : "260") + " " + (look.length > 2 ? look[2] : "-300") + " 0 " + (look.length > 1 ? look[1] : "-12")).wait(2)
                .run(mc -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }).wait(40)
                .run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("remote: camera at {} flying {}", mc.player.position(), mc.player.getAbilities().flying))
                .hud(true).screenshot("remote_" + ability + "_before");
        double tx = parts.length > 4 ? Double.parseDouble(parts[4].split(",")[0]) : 0.5, tz = parts.length > 4 ? Double.parseDouble(parts[4].split(",")[1]) : 0.5;
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> DevMaps.dump(srv.overworld(), (int) tx, (int) tz, 300, new java.io.File(mc.gameDirectory, "screenshots/fw_remote_" + ability + "_map0.png")));
        }).wait(5);
        b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.CastAt(ability, tx, 80, tz)));
        int prev = 0;
        for (String t : times) {
            int at = Integer.parseInt(t.trim());
            b.wait(Math.max(1, at - prev)).screenshot("remote_" + ability + "_" + String.format("%03d", at));
            int when = at;
            b.run(mc -> {
                var ow = mc.getSingleplayerServer().overworld();
                StringBuilder col = new StringBuilder();
                for (int y = 70; y >= 20; y -= 5) col.append(y).append('=').append(ow.getBlockState(new net.minecraft.core.BlockPos(0, y, 0)).getBlock().getName().getString()).append(' ');
                com.fantasyweapons.FantasyWeapons.LOGGER.warn("remote: t={} column 0,0: {} | {}", when, col, com.fantasyweapons.world.wreck.WorldWreck.stats());
            });
            prev = at;
        }
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> DevMaps.dump(srv.overworld(), (int) tx, (int) tz, 300, new java.io.File(mc.gameDirectory, "screenshots/fw_remote_" + ability + "_map.png")));
        }).wait(10);
        b.run(mc -> {
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("remote: {}", com.fantasyweapons.world.wreck.WorldWreck.stats());
            var server = mc.getSingleplayerServer();
            var ow = server.overworld();
            int y = ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0);
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("remote: server top at 0,0 is y={} {} ; client top y={} {}", y, ow.getBlockState(new net.minecraft.core.BlockPos(0, y - 1, 0)),
                    mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0),
                    mc.level.getBlockState(new net.minecraft.core.BlockPos(0, mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0) - 1, 0)));
            // the client's blocks against the server's, chunk by chunk round the middle (on the server's thread, where its
            // chunks can be read)
            server.submit(() -> compare(mc, ow)).join();
        });
        if (parts.length > 5) {
            // views of the aftermath: x,y,z,yaw,pitch;... (no HUD, no hand)
            b.hud(false);
            String[] views = parts[5].split(";");
            for (int i = 0; i < views.length; i++) {
                String[] v = views[i].split(",");
                b.cmd("/tp @s " + v[0] + " " + v[1] + " " + v[2] + " " + v[3] + " " + v[4]).run(mc -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }).wait(70).screenshot("remote_" + ability + "_view" + i);
            }
            return;
        }
        b.cmd("/tp @s 0 215 -10 0 90").wait(80).screenshot("remote_" + ability + "_after_top")
                .cmd("/tp @s 0 140 -200 0 25").wait(60).screenshot("remote_" + ability + "_after_a")
                .cmd("/tp @s 190 120 0 90 15").wait(60).screenshot("remote_" + ability + "_after_b");
    }


    /**
     * A power of the Weapon of God (fwterrain world): {@code ability[@x,z]/distance/ticks,.../tick@vx,vy,vz[,lx,ly,lz];...[/aftermath x,y,z,yaw,pitch;...[/map radius]]}. The caster
     * stands on the ground {@code distance} blocks south of the target (0, 0) facing it and casts through the map; the
     * summoning is filmed from beside them (ticks under {@link com.fantasyweapons.weapons.god.DivineSummoning#TICKS}), the power
     * from a camera at {@code vx, vy, vz} looking at the target (or at {@code lx, ly, lz}); then views of what is left.
     */
    private static void god(ScreenshotDirector.Builder b, String spec) {
        String[] parts = spec.split("/");
        // name@x,z: filmed round that spot (fresh land far from the middle), every position below relative to it
        String ability = parts[0].contains("@") ? parts[0].substring(0, parts[0].indexOf('@')) : parts[0];
        double cx = 0, cz = 0;
        if (parts[0].contains("@")) {
            String[] c = parts[0].substring(parts[0].indexOf('@') + 1).split(",");
            cx = Double.parseDouble(c[0]);
            cz = Double.parseDouble(c[1]);
        }
        double ox = cx, oz = cz;
        double dist = parts.length > 1 ? Double.parseDouble(parts[1]) : 330;
        String[] times = parts.length > 2 ? parts[2].split(",") : new String[]{"20", "100", "190", "260", "340", "420"};
        // cameras: tick@x,y,z[,lookx,looky,lookz];... each taking over from its tick
        java.util.List<double[]> cams = new java.util.ArrayList<>();
        for (String c : (parts.length > 3 ? parts[3] : "200@90,150,-230").split(";")) {
            int at = Integer.parseInt(c.substring(0, c.indexOf('@')));
            String[] v = c.substring(c.indexOf('@') + 1).split(",");
            double[] cam = {at, ox + Double.parseDouble(v[0]), Double.parseDouble(v[1]), oz + Double.parseDouble(v[2]), ox + 0.5, 70, oz + 0.5};
            if (v.length > 5) {
                cam[4] = ox + Double.parseDouble(v[3]);
                cam[5] = Double.parseDouble(v[4]);
                cam[6] = oz + Double.parseDouble(v[5]);
            }
            cams.add(cam);
        }
        int mapRadius = parts.length > 5 ? Integer.parseInt(parts[5]) : 400;
        // a film before may have left the player over a hole to the void
        b.wait(20).run(mc -> {
            if (mc.player != null && !mc.player.isAlive()) mc.player.respawn();
        }).wait(40).closeScreen();
        b.wait(40).cmd("/fw class set berserker").closeScreen().wait(5).closeScreen()
                .cmd("/gamemode creative").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/gamerule doMobSpawning false")
                .cmd("/kill @e[type=!player]").cmd("/clear @s").cmd("/fw give " + com.fantasyweapons.weapons.god.WeaponOfGod.ID + " 1")
                .cmd("/tp @s " + (ox + 0.5) + " 200 " + (oz - dist + 0.5) + " 0 0").wait(400).slot(0).closeScreen();
        onServer(b, sp -> {
            var level = sp.serverLevel();
            int fz = (int) Math.floor(oz - dist), fx = (int) Math.floor(ox);
            // dry ground for the caster: along the line across, the nearest spot not under water
            for (int step = 0; step <= 48; step++) {
                int x = (int) Math.floor(ox) + (step % 2 == 0 ? 1 : -1) * (step + 1) / 2 * 8;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, fz);
                if (level.getFluidState(new net.minecraft.core.BlockPos(x, top - 1, fz)).isEmpty()) {
                    fx = x;
                    break;
                }
            }
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz);
            // a clearing round the caster, so the summoning can be seen
            net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int x = fx - 16; x <= fx + 16; x++) {
                for (int z = fz - 22; z <= fz + 16; z++) {
                    for (int yy = y - 2; yy < y + 30; yy++) {
                        var st = level.getBlockState(m.set(x, yy, z));
                        if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS) || (yy >= y && !st.isAir() && st.getFluidState().isEmpty()))
                            level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            sp.teleportTo(level, fx + 0.5, y, fz + 0.5, (float) Math.toDegrees(Math.atan2(-(ox - fx), oz - fz)), -8);
            DevMaps.dump(level, (int) ox, (int) oz, mapRadius, new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory,
                    "screenshots/fw_god_" + ability + "_map_before.png"));
        });
        // the land the power will touch, generated before it is cast (a fresh far place takes a while here)
        double reach = com.fantasyweapons.weapons.god.DivineSummoning.reach(com.fantasyweapons.weapon.Weapons.get(com.fantasyweapons.weapons.god.WeaponOfGod.ID)
                .ability(ability));
        java.util.List<net.minecraft.world.level.ChunkPos> region = com.fantasyweapons.weapons.ultimate.ShufflePlan.region(new net.minecraft.world.phys.Vec3(ox, 0, oz), reach);
        int[] loaded = {0};
        long[] asked = {0};
        b.until(mc -> {
            var srv = mc.getSingleplayerServer();
            long now = mc.level.getGameTime();
            if (now - asked[0] >= 20) {
                asked[0] = now;
                srv.execute(() -> {
                    var level = srv.overworld();
                    int n = 0;
                    for (var c : region) {
                        com.fantasyweapons.world.wreck.WorldWreck.hold(level, c.x, c.z);
                        if (level.getChunkSource().getChunkNow(c.x, c.z) != null) n++;
                    }
                    loaded[0] = n;
                    if (now % 400 < 20) com.fantasyweapons.FantasyWeapons.LOGGER.warn("god: land loaded {}/{}", n, region.size());
                });
            }
            return loaded[0] >= region.size();
        }, 12000);
        b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("god: land ready {}/{}", loaded[0], region.size()));
        b.wait(60).cmd("/fw cooldowns").hud(true).playerView().wait(5).screenshot("god_" + ability + "_before");
        b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.CastAt(ability, ox + 0.5, 80, oz + 0.5)));
        int prev = 0;
        double[] current = null;
        for (String tt : times) {
            int at = Integer.parseInt(tt.trim());
            b.wait(Math.max(1, at - prev));
            double[] cam = null;
            for (double[] c : cams) if (at >= c[0]) cam = c;
            if (at < com.fantasyweapons.weapons.god.DivineSummoning.TICKS && cam == null) {
                // the summoning, from beside the caster
                b.hud(false).viewAt(9, 4, -12, 0, 2.5, 0);
            } else if (cam != null && cam != current) {
                double[] c = cam;
                // the player (the summoning over) goes under a camera in the land loaded for the power, so the land
                // round it is sent and drawn
                if (at >= com.fantasyweapons.weapons.god.DivineSummoning.TICKS && Math.hypot(c[1] - ox, c[3] - oz) <= reach) {
                    b.cmd("/tp @s " + c[1] + " " + (c[2] - 40) + " " + c[3]).run(mc -> {
                        mc.player.getAbilities().flying = true;
                        mc.player.onUpdateAbilities();
                    });
                }
                b.hud(false).run(mc -> {
                    var stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
                    double ex = c[4] - c[1], ey = c[5] - (c[2] + stand.getEyeHeight()), ez = c[6] - c[3];
                    float yaw = (float) (Math.toDegrees(Math.atan2(ez, ex)) - 90);
                    float pitch = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
                    stand.moveTo(c[1], c[2], c[3], yaw, pitch);
                    stand.setYHeadRot(yaw);
                    stand.setInvisible(true);
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.setCameraEntity(stand);
                });
            }
            current = cam;
            b.screenshot("god_" + ability + "_" + String.format("%04d", at));
            int when = at;
            b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("god: t={} {}", when, com.fantasyweapons.world.wreck.WorldWreck.stats()));
            prev = at;
        }
        // everything the power destroys, landed
        b.until(mc -> com.fantasyweapons.world.wreck.WorldWreck.pending() == 0, 3600)
                .run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("god: done {}", com.fantasyweapons.world.wreck.WorldWreck.stats()));
        b.playerView().wait(100);
        if (parts.length > 4 && !parts[4].isEmpty()) {
            String[] views = parts[4].split(";");
            for (int i = 0; i < views.length; i++) {
                String[] v = views[i].split(",");
                b.cmd("/tp @s " + (ox + Double.parseDouble(v[0])) + " " + v[1] + " " + (oz + Double.parseDouble(v[2])) + " " + v[3] + " " + v[4]).run(mc -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }).wait(100).screenshot("god_" + ability + "_after" + i);
            }
        }
        // the land round the target from above, once it is all loaded again
        int[] back = {0};
        long[] askedAgain = {0};
        b.until(mc -> {
            var srv = mc.getSingleplayerServer();
            long now = mc.level.getGameTime();
            if (now - askedAgain[0] >= 20) {
                askedAgain[0] = now;
                srv.execute(() -> {
                    var level = srv.overworld();
                    int n = 0;
                    for (var c : region) {
                        com.fantasyweapons.world.wreck.WorldWreck.hold(level, c.x, c.z);
                        if (level.getChunkSource().getChunkNow(c.x, c.z) != null) n++;
                    }
                    back[0] = n;
                });
            }
            return back[0] >= region.size();
        }, 2400);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> DevMaps.dump(srv.overworld(), (int) ox, (int) oz, mapRadius, new java.io.File(mc.gameDirectory, "screenshots/fw_god_" + ability + "_map_after.png")));
        }).wait(10);
    }

    /** DEVELOPMENT ONLY: finds dry land for filming - logs spots on a grid with how much of the land within 600 blocks is sea. */
    private static void scout(ScreenshotDirector.Builder b) {
        onServer(b, sp -> {
            var level = sp.serverLevel();
            for (int gx = -12000; gx <= 12000; gx += 1500) {
                for (int gz = -12000; gz <= 12000; gz += 1500) {
                    int wet = 0, n = 0;
                    java.util.Map<String, Integer> kinds = new java.util.HashMap<>();
                    for (int dx = -600; dx <= 600; dx += 100) {
                        for (int dz = -600; dz <= 600; dz += 100) {
                            var biome = level.getBiome(new net.minecraft.core.BlockPos(gx + dx, 64, gz + dz));
                            String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("?");
                            n++;
                            if (name.contains("ocean") || name.contains("river") || name.contains("beach") || name.contains("swamp") || name.contains("frozen")) wet++;
                            kinds.merge(name, 1, Integer::sum);
                        }
                    }
                    if (wet <= n / 12) com.fantasyweapons.FantasyWeapons.LOGGER.warn("scout: {} {} wet {}/{} {}", gx, gz, wet, n, kinds);
                }
            }
        });
        b.wait(20);
    }

    /** DEVELOPMENT ONLY: counts the blocks in which the client's chunks round the middle differ from the server's. */
    private static void compare(net.minecraft.client.Minecraft mc, net.minecraft.server.level.ServerLevel ow) {
        int bad = 0, chunks = 0, first = 0;
        String example = "";
        for (int cx = -24; cx <= 24; cx += 3) {
            for (int cz = -24; cz <= 24; cz += 3) {
                var sc = ow.getChunkSource().getChunkNow(cx, cz);
                var cc = mc.level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (sc == null || cc == null) continue;
                chunks++;
                int here = 0;
                for (int i = 0; i < sc.getSectionsCount(); i++) {
                    var ss = sc.getSection(i).getStates();
                    var cs = cc.getSection(i).getStates();
                    for (int q = 0; q < 4096; q++) {
                        if (ss.get(q & 15, q >> 8, (q >> 4) & 15) != cs.get(q & 15, q >> 8, (q >> 4) & 15)) {
                            here++;
                            if (example.isEmpty()) example = "chunk " + cx + "," + cz + " section " + i + " at " + (q & 15) + "," + (q >> 8) + "," + ((q >> 4) & 15)
                                    + " server " + ss.get(q & 15, q >> 8, (q >> 4) & 15) + " client " + cs.get(q & 15, q >> 8, (q >> 4) & 15);
                        }
                    }
                }
                if (here > 0) first++;
                bad += here;
            }
        }
        com.fantasyweapons.FantasyWeapons.LOGGER.warn("remote: client vs server: {} chunks compared, {} differ, {} blocks differ; {}", chunks, first, bad, example);
    }

    /**
     * A local ability of an ultimate weapon (fwterrain world): {@code weapon/ability/ticks,.../x,y,z,yaw,pitch[/back]} -
     * stand there facing that way, charge, release and capture; "back" films it from behind the player. Then a top-down map.
     */
    private static void local(ScreenshotDirector.Builder b, String spec) {
        String[] parts = spec.split("/");
        String weapon = parts[0], ability = parts[1];
        String[] times = parts[2].split(",");
        String[] at = parts[3].split(",");
        boolean back = parts.length > 4 && parts[4].equals("back");
        var def = com.fantasyweapons.weapon.Weapons.get(weapon);
        var a = def.ability(ability);
        String tp = "/tp @s " + at[0] + " " + at[1] + " " + at[2] + " " + at[3] + " " + at[4];
        b.wait(40).cmd("/fw class set " + (def.weaponClass().magic() ? "mage" : "berserker")).closeScreen().wait(5).closeScreen()
                .cmd("/gamemode creative").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/gamerule doMobSpawning false")
                .cmd("/clear @s").cmd("/fw give " + weapon + " 1").cmd(tp).wait(300).slot(0).closeScreen().cmd("/fw cooldowns").cmd(tp).wait(40)
                .run(mc -> {
                    var srv = mc.getSingleplayerServer();
                    var pos = mc.player.blockPosition();
                    srv.execute(() -> DevMaps.dump(srv.overworld(), pos.getX(), pos.getZ(), 300, new java.io.File(mc.gameDirectory, "screenshots/fw_local_" + ability + "_map0.png")));
                }).wait(5)
                .hud(true).camera(back ? net.minecraft.client.CameraType.THIRD_PERSON_BACK : net.minecraft.client.CameraType.FIRST_PERSON)
                .select(a.id()).wait(4).screenshot("local_" + ability + "_before");
        b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
        if (parts.length > 4 && parts[4].startsWith("view=")) {
            // film it from a free camera at an offset from the player, looking at another offset
            double[] v = java.util.Arrays.stream(parts[4].substring(5).split(",")).mapToDouble(Double::parseDouble).toArray();
            b.viewAt(v[0], v[1], v[2], v[3], v[4], v[5]).camera(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
        if (parts.length > 4 && parts[4].startsWith("tp=")) {
            // watch it from somewhere else (the player is moved, so the terrain there is loaded and drawn)
            String[] v = parts[4].substring(3).split(",");
            String tp2 = "/tp @s " + v[0] + " " + v[1] + " " + v[2] + " " + v[3] + " " + v[4];
            // as a spectator: it neither falls nor holds the weapon in view
            b.wait(2).cmd("/gamemode spectator").cmd(tp2).wait(4).cmd(tp2);
        }
        int prev = 0;
        for (String t : times) {
            int when = Integer.parseInt(t.trim());
            b.wait(Math.max(1, when - prev)).screenshot("local_" + ability + "_" + String.format("%03d", when));
            prev = when;
        }
        b.wait(60).run(mc -> {
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("local: {}", com.fantasyweapons.world.wreck.WorldWreck.stats());
            var srv = mc.getSingleplayerServer();
            var pos = mc.player.blockPosition();
            srv.execute(() -> DevMaps.dump(srv.overworld(), pos.getX(), pos.getZ(), 300, new java.io.File(mc.gameDirectory, "screenshots/fw_local_" + ability + "_map.png")));
        }).wait(10).playerView().camera(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    /**
     * The Worldforge (fwterrain world): built on a platform in the air, filmed unfolding, its menu opened empty, with the
     * sword's rite chosen (ghosts), with the rite laid on the grid, and forging; then the forge at work.
     */
    private static void worldforge(ScreenshotDirector.Builder b) {
        // placed on the floor (y 126): the core is the bottom middle block
        net.minecraft.core.BlockPos core = new net.minecraft.core.BlockPos(0, 127, -150);
        b.wait(40).cmd("/fw class set berserker").closeScreen().wait(5).closeScreen().cmd("/gamemode creative").cmd("/time set 18000")
                .cmd("/gamerule doDaylightCycle false").cmd("/clear @s")
                .cmd("/tp @s 0.5 131 -137.5 180 8").wait(200)
                .cmd("/fill -14 127 -164 14 151 -132 air").cmd("/fill -14 126 -164 14 126 -132 polished_blackstone_bricks")
                .cmd("/tp @s 0.5 127 -137.5 180 6").wait(40).run(mc -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                }).wait(20).hud(false);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayer(id);
                var stack = new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.WORLDFORGE.get());
                // the forge rises in front of the player: they choose the foot of its stairs
                var foot = core.relative(net.minecraft.core.Direction.SOUTH, com.fantasyweapons.forge.WorldforgeBlock.REACH + 1);
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(foot), net.minecraft.core.Direction.UP, foot, false);
                var res = com.fantasyweapons.registry.ModItems.WORLDFORGE.get().place(new net.minecraft.world.item.context.BlockPlaceContext(sp.serverLevel(), sp,
                        net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit));
                com.fantasyweapons.FantasyWeapons.LOGGER.warn("worldforge: placed {} -> {}", res, sp.serverLevel().getBlockState(core));
            });
        });
        // the unfolding, watched from further off (spectating, so the camera never stands in the forge)
        b.cmd("/gamemode spectator").cmd("/tp @s 0.5 142 -112 180 18");
        int prev = 0;
        for (int t : new int[]{4, 60, 120, 180, 240, 300, 370}) {
            b.wait(t - prev).screenshot("worldforge_unfold_" + String.format("%03d", t));
            prev = t;
        }
        b.cmd("/gamemode creative");
        b.hud(true);
        java.util.function.Consumer<net.minecraft.client.Minecraft> open = mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(core.offset(0, 0, 10)), net.minecraft.core.Direction.SOUTH, core.offset(0, 0, 10), false));
        // the forge is opened from the foot of its front stairs
        b.cmd("/tp @s 0.5 127 -137.5 180 30").wait(10).run(open).wait(4).screenshot("worldforge_gui_fade").wait(16).screenshot("worldforge_gui_empty")
                .wait(37).screenshot("worldforge_gui_empty2").wait(29).screenshot("worldforge_gui_empty3");
        b.run(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, com.fantasyweapons.forge.WorldforgeMenu.CHOOSE)).wait(20)
                .screenshot("worldforge_gui_ghost");
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var level = srv.overworld();
                if (!(level.getBlockEntity(core) instanceof com.fantasyweapons.forge.WorldforgeBlockEntity forge)) return;
                var recipe = com.fantasyweapons.forge.WorldforgeRecipe.ordered(level).get(0).value();
                var at = com.fantasyweapons.forge.WorldforgeMenu.layout(recipe);
                for (int i = 0; i < at.length; i++) if (at[i] != null) forge.grid().setItem(i, at[i].getItems()[0].copy());
            });
        }).wait(20).screenshot("worldforge_gui_ready");
        b.run(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, com.fantasyweapons.forge.WorldforgeMenu.FORGE_BUTTON)).wait(60)
                .screenshot("worldforge_gui_forging").closeScreen().cmd("/gamemode spectator").cmd("/tp @s 0.5 142 -112 180 18").wait(10).hud(false);
        for (int t : new int[]{0, 40, 80}) b.wait(t == 0 ? 2 : 40).screenshot("worldforge_crafting_" + t);
        b.cmd("/tp @s 27.5 141 -128.5 135 18").wait(30).screenshot("worldforge_crafting_side");
        b.cmd("/tp @s 0.5 136 -141.5 180 10").wait(30).screenshot("worldforge_crafting_altar");
        b.cmd("/tp @s 0.5 132 -150.5 180 -70").wait(30).screenshot("worldforge_crafting_under");
        b.cmd("/gamemode creative").hud(true);
    }

    /** The remote-cast targeting map, opened by the ability key with the Spell Book of Absolute Domination (fwterrain world). */
    private static void targetMap(ScreenshotDirector.Builder b) {
        b.wait(40).cmd("/fw class set mage").closeScreen().wait(5).closeScreen()
                .cmd("/gamemode creative").cmd("/time set noon").cmd("/tp @s 0 120 0 0 30").cmd("/clear @s")
                .cmd("/fw give spell_book_of_absolute_domination 1").wait(260).slot(0).closeScreen().hud(true).wait(10).screenshot("tmap_hud");
        b.run(mc -> com.fantasyweapons.client.gui.TargetMapScreen.openFor(mc.player.getMainHandItem())).wait(20);
        b.run(mc -> org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() * 0.62, mc.getWindow().getScreenHeight() * 0.4))
                .wait(10).screenshot("tmap_open");
        b.run(mc -> {
            double gx = mc.getWindow().getGuiScaledWidth() * 0.62, gy = mc.getWindow().getGuiScaledHeight() * 0.4;
            mc.screen.mouseScrolled(gx, gy, 0, 1);
            mc.screen.mouseScrolled(gx, gy, 0, 1);
        }).wait(10).screenshot("tmap_zoom");
        b.run(mc -> mc.screen.mouseClicked(mc.getWindow().getGuiScaledWidth() * 0.62, mc.getWindow().getGuiScaledHeight() * 0.4, 0)).wait(20).screenshot("tmap_cast");
        b.cmd("/clear @s").cmd("/fw give great_sword_of_obliteration 1").wait(20).slot(0).cmd("/fw class set berserker").wait(10)
                .camera(net.minecraft.client.CameraType.THIRD_PERSON_FRONT).wait(10).screenshot("tmap_sword_3p")
                .camera(net.minecraft.client.CameraType.FIRST_PERSON).wait(10).screenshot("tmap_sword_1p");
    }

    /** World destruction: a shaft through real terrain, seen from above before, during and after (fwterrain world). */
    private static void wreck(ScreenshotDirector.Builder b, String radius) {
        b.wait(40).cmd("/fw class set mage").closeScreen().wait(5).closeScreen()
                .cmd("/gamemode spectator").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/tp @s 0 170 -70 0 40").wait(300)
                .closeScreen().hud(false).screenshot("wreck_before")
                .cmd("/execute positioned 0 100 40 run fw wreck shaft " + radius);
        for (int i = 0; i < 6; i++) b.wait(30).screenshot("wreck_" + i);
        b.cmd("/tp @s 0 120 40 0 90").wait(60).screenshot("wreck_down");
        b.cmd("/tp @s 0 20 40 0 30").wait(60).screenshot("wreck_inside");
        b.cmd("/time set midnight").wait(30).screenshot("wreck_night");
    }

    /** Impact frames fire on the hit, not the cast: Meteor Brand's meteor falls a moment after casting; every 2 ticks. */
    private static void impactHit(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/fw class set mage").cmd("/time set noon").cmd("/clear @s").cmd("/kill @e[type=!player]")
                .cmd("/tp @s 0 -60 0 0 0").cmd("/fw give cinderspire 100").wait(130).slot(0).cmd("/fw points 300").hud(true);
        summonDummies(b);
        var a = com.fantasyweapons.weapon.Weapons.get("cinderspire").ability("meteor_brand");
        b.playerView().camera(CameraType.FIRST_PERSON).look(0, 6).cmd("/fw cooldowns").select(a.id()).wait(6);
        b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
        for (int i = 0; i < 24; i++) b.wait(2).screenshot("ihit_" + String.format("%02d", i * 2));
    }

    /** Buffs and curses on the player: the status icons in the HUD and their visuals, in first and third person. */
    /**
     * The Empower panel of a weapon's menu: {@code weapon/ability}, at level 60 with its points spent elsewhere (a rank
     * that needs a higher level and more points), then at its last level with nothing spent, then fully empowered.
     */
    private static void empower(ScreenshotDirector.Builder b, String spec) {
        String[] parts = spec.split("/");
        String weapon = parts[0], ability = parts[1];
        b.wait(40).cmd("/fw class set " + (weapon.contains("book") || weapon.contains("staff") ? "mage" : "berserker")).closeScreen().wait(5).closeScreen()
                .cmd("/clear @s").cmd("/fw give " + weapon + " 1").wait(10).slot(0)
                .cmd("/fantasyweapons level 60").wait(10)
                .run(mc -> {
                    // spend the points so the next rank cannot be afforded
                    var srv = mc.getSingleplayerServer();
                    srv.execute(() -> {
                        var sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
                        var st = sp.getMainHandItem();
                        var d = com.fantasyweapons.weapon.FantasyWeaponItem.data(st);
                        st.set(com.fantasyweapons.registry.ModComponents.WEAPON_DATA.get(), d.withProgress(d.level(), d.exp(), d.totalExp(), 0));
                    });
                }).wait(10)
                .menu(ability).wait(30).screenshot("empower_" + ability + "_lv60").closeScreen()
                .cmd("/fantasyweapons level 100").wait(60)
                .menu(ability).wait(30).screenshot("empower_" + ability + "_lv100")
                .run(mc -> {
                    var srv = mc.getSingleplayerServer();
                    srv.execute(() -> {
                        var sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
                        var st = sp.getMainHandItem();
                        var def = ((com.fantasyweapons.weapon.FantasyWeaponItem) st.getItem()).definition();
                        var d = com.fantasyweapons.weapon.FantasyWeaponItem.data(st);
                        com.fantasyweapons.FantasyWeapons.LOGGER.warn("empower: level {} points {} cost of every rank {}", d.level(), d.masteryPoints(),
                                com.fantasyweapons.progression.ProgressionMath.upgradeCost(def));
                        for (var a : def.abilities()) {
                            int[] plan = com.fantasyweapons.ability.AbilityService.maxUpgrade(com.fantasyweapons.weapon.FantasyWeaponItem.data(st), a);
                            com.fantasyweapons.FantasyWeapons.LOGGER.warn("empower: {} can go {} ranks for {} points", a.id(), plan[0], plan[1]);
                        }
                    });
                }).wait(10);
    }

    private static void mageHud(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.run(mc -> {
            var server = mc.getSingleplayerServer();
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                com.fantasyweapons.status.StatusType[] types = {com.fantasyweapons.status.StatusType.VALOR, com.fantasyweapons.status.StatusType.SWIFT,
                        com.fantasyweapons.status.StatusType.HALO, com.fantasyweapons.status.StatusType.BARRIER, com.fantasyweapons.status.StatusType.MENDING,
                        com.fantasyweapons.status.StatusType.FROST_WARD, com.fantasyweapons.status.StatusType.WITHERING, com.fantasyweapons.status.StatusType.SOAKED};
                for (var t : types) com.fantasyweapons.status.StatusService.apply(sp, t, 600, 1, 1, sp.getUUID());
            });
        });
        b.wait(10).playerView().camera(CameraType.FIRST_PERSON).hud(true).look(0, 10).wait(10).screenshot("magehud_fp");
        b.cmd("/fw give lumen_psalter 20").wait(20).slot(0).wait(20).screenshot("magehud_fp_held");
        b.hud(false).viewFrom(2.2, 0.6, 3.2).wait(10).screenshot("magehud_tp").viewFrom(-2.6, 0.4, 2.4).wait(6).screenshot("magehud_tp2");
        b.playerView().cmd("/gamemode creative");
    }

    /** The mage spells that only heal, shield or bless: cast on a tamed wolf rather than at the dummies. */
    private static final java.util.Set<String> SUPPORT = java.util.Set.of("glacial_ward", "shatterguard", "tidal_spring", "bubble_aegis", "healing_rain",
            "tides_embrace", "lifebloom", "worldtrees_boon", "mending_vines", "spring_eternal", "blessing_of_valor", "hymn_of_swiftness",
            "halo_of_fortitude", "ascension", "sanctified_ground", "maelstrom_sanctuary", "everfrost_bastion", "dawnbringer");

    /**
     * Every spell of the given mage weapons ("all" for the twelve), cast from afar: offensive ones at three dummies eleven
     * blocks out, supportive ones on a wounded tamed wolf off to the left; then a left-click bolt.
     */
    private static void mages(ScreenshotDirector.Builder b, String[] weapons) {
        java.util.List<String> ids = weapons.length == 1 && weapons[0].equals("all")
                ? com.fantasyweapons.weapon.Weapons.focuses().stream().map(com.fantasyweapons.weapon.WeaponDefinition::id).toList()
                : java.util.List.of(weapons);
        b.cmd("/gamemode creative").cmd("/fw class set mage").cmd("/time set midnight").wait(5);
        for (String w : ids) {
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            b.cmd("/clear @s").cmd("/kill @e[type=!player]").cmd("/tp @s 0 -60 0 0 0").cmd("/fw give " + w + " 100").wait(130).slot(0)
                    .cmd("/fw points 300").hud(false);
            for (var a : def.castables()) {
                boolean support = SUPPORT.contains(a.id());
                b.cmd("/kill @e[type=!player]").wait(3);
                summonDummies(b);
                b.run(mc -> spawnAlly(mc, -3.5, 6.5));
                b.playerView().camera(CameraType.FIRST_PERSON).cmd("/fw cooldowns").wait(4);
                b.look(support ? 33.7f : 0, support ? 9.6f : 6).select(a.id()).wait(6).viewAt(8, 4.5, -3, -1, 0.5, 7);
                b.abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
                boolean ult = a.kind() == com.fantasyweapons.ability.AbilityKind.ULTIMATE;
                String n = "mage_" + w + "_" + a.id();
                b.wait(3).screenshot(n + "_a").wait(7).screenshot(n + "_b");
                // through the caster's own eyes too, where hard edges and cut-off columns show most
                b.playerView().hud(false).wait(4).screenshot(n + "_fp").viewAt(8, 4.5, -3, -1, 0.5, 7);
                b.wait(8).screenshot(n + "_c").wait(23).screenshot(n + "_d");
                if (ult) b.wait(35).screenshot(n + "_e").wait(50).screenshot(n + "_f");
                b.wait(ult ? 60 : 20);
            }
            b.cmd("/kill @e[type=!player]").wait(3);
            summonDummies(b);
            b.playerView().camera(CameraType.FIRST_PERSON).look(0, 6).wait(10).viewAt(5, 2.5, -2, 0, 1, 6);
            b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.CastBolt()));
            for (int i = 0; i < 6; i++) b.wait(2).screenshot("mage_" + w + "_bolt" + i);
            b.wait(20);
        }
        b.playerView();
    }

    private static void summonDummies(ScreenshotDirector.Builder b) {
        double[][] at = {{0.5, 11.5}, {-2, 12.5}, {3, 12.5}};
        for (double[] p : at) {
            b.cmd("/summon minecraft:husk " + p[0] + " -60 " + p[1] + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
        }
    }

    /** A wounded wolf tamed by the player (an ally for the healing and blessing spells). */
    private static void spawnAlly(net.minecraft.client.Minecraft mc, double x, double z) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            var wolf = net.minecraft.world.entity.EntityType.WOLF.create(sp.serverLevel());
            if (wolf == null) return;
            wolf.moveTo(x, -60, z, 180, 0);
            wolf.tame(sp);
            wolf.setNoAi(true);
            wolf.setHealth(wolf.getMaxHealth() * 0.4f);
            sp.serverLevel().addFreshEntity(wolf);
        });
    }

    /** First-person hold of each weapon: idle, the attack at three points, ability charge and cast. */
    private static void fpHold(ScreenshotDirector.Builder b, String[] weapons) {
        b.look(0, 0).playerView().camera(CameraType.FIRST_PERSON).hud(true);
        for (String w : weapons) {
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).wait(8);
            b.pin(-1, false, false, -1, -1, -1).wait(4).screenshot("fph_" + w + "_idle");
            for (float t : new float[]{0.16f, 0.34f, 0.6f}) b.pin(t, false, false, -1, -1, -1).wait(3).screenshot("fph_" + w + "_swing" + Math.round(t * 100));
            b.pin(-1, false, false, 0.9f, -1, -1).wait(3).screenshot("fph_" + w + "_charge");
            b.pin(-1, false, false, -1, 0.4f, -1).wait(3).screenshot("fph_" + w + "_cast");
            b.pin(-1, false, false, -1, -1, -1);
        }
    }

    private static boolean holdsOnTerrain;

    private static void holds(ScreenshotDirector.Builder b, java.util.List<com.fantasyweapons.weapon.WeaponDefinition> all) {
        b.wait(40).cmd("/fw class set mage").closeScreen().wait(5).closeScreen().cmd("/gamemode creative")
                .cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/clear @s");
        if (holdsOnTerrain) b.cmd("/tp @s 30.5 200 -230.5 0 0").wait(200);
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
     * The Codex's chapter on the ultimate weapons (contents, the Worldforge, the rites, each weapon's pages), its deeds and
     * the quest journal's group for them.
     */
    private static void ultCodex(ScreenshotDirector.Builder b) {
        b.run(mc -> {
            if (mc.player.isDeadOrDying()) mc.player.respawn();
        }).wait(40).cmd("/gamemode creative").cmd("/execute in minecraft:overworld run tp @s 0 -60 0 0 0").cmd("/time set noon").cmd("/clear @s")
                .cmd("/fw quest reset").cmd("/fw class set mage").cmd("/give @s fantasyweapons:wayfarers_codex").wait(20)
                .cmd("/advancement grant @s only fantasyweapons:worldforge").cmd("/advancement grant @s only fantasyweapons:first_rite")
                .cmd("/advancement grant @s only fantasyweapons:world_ender").wait(10).slot(0).hud(true).wait(10)
                .run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110);
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(0, false)).wait(16).screenshot("ult_codex_contents");
        for (int i = 0; i < 10; i++) {
            int n = i;
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("ultimates") + n, false))
                    .wait(16).screenshot("ult_codex_" + n);
        }
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreads() - 1, false)).wait(16)
                .screenshot("ult_codex_deeds_last");
        b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreads() - 2, false)).wait(16)
                .screenshot("ult_codex_deeds_prev");
        b.closeScreen().wait(10);
        b.cmd("/fw quest complete trophy_table").cmd("/fw quest claim trophy_table").cmd("/give @s fantasyweapons:worldforge").wait(40)
                .cmd("/fw quest claim raise_worldforge").wait(200);
        b.run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.QuestScreen())).wait(10);
        journal(b, q -> q.devChapter(com.fantasyweapons.quest.Chapter.WEAPONS));
        b.wait(8).screenshot("ult_quests");
        journal(b, q -> q.devExpand("ultimate_weapons"));
        b.wait(14).screenshot("ult_quests_open");
        journal(b, q -> q.devSelect("first_rite"));
        b.wait(14).screenshot("ult_quests_rite");
        b.closeScreen().wait(5).quit();
    }

    /** The Mage's six regalia on armor stands, front and back, then worn by the player, struck, and holding a staff. */
    private static void regalia(ScreenshotDirector.Builder b) {
        var sets = java.util.Arrays.stream(com.fantasyweapons.armor.ArmorSet.values()).filter(com.fantasyweapons.armor.ArmorSet::regalia).toList();
        b.run(mc -> {
            if (mc.player.isDeadOrDying()) mc.player.respawn();
        }).wait(40).cmd("/gamemode creative").cmd("/execute in minecraft:overworld run tp @s 0 -60 0 0 0").wait(40).cmd("/gamemode creative").cmd("/time set noon").hud(false).cmd("/kill @e[type=!player]")
                .cmd("/fill -16 -60 2 16 -56 10 minecraft:air").cmd("/clear @s").cmd("/fw class set mage");
        for (int i = 0; i < sets.size(); i++) {
            String id = "fantasyweapons:" + sets.get(i).id();
            b.cmd("/summon armor_stand " + (i * 2 - 5) + " -60 6 {Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,ArmorItems:[{id:\"" + id + "_boots\",count:1},{id:\""
                    + id + "_leggings\",count:1},{id:\"" + id + "_chestplate\",count:1},{id:\"" + id + "_helmet\",count:1}]}");
        }
        b.wait(30).closeScreen();
        b.viewAt(0.5, 2.0, -1.5, 0.5, 1.0, 6).wait(15).screenshot("reg_all");
        for (int g = 0; g < 3; g++) {
            double cx = -5 + g * 4 + 1;
            b.viewAt(cx + 0.5, 1.5, 2.6, cx + 0.5, 1.1, 6).wait(15).screenshot("reg_front_" + g);
            b.viewAt(cx + 0.5, 1.5, 9.4, cx + 0.5, 1.1, 6).wait(10).screenshot("reg_back_" + g);
        }
        b.cmd("/kill @e[type=armor_stand]").playerView().cmd("/tp @s 0 -60 2 0 0").cmd("/gamemode survival");
        String[] focus = {"cinderspire", "rimeheart", "codex_of_tides", "thornwake", "lumen_psalter", "umbral_codex"};
        for (int i = 0; i < sets.size(); i++) {
            String id = "fantasyweapons:" + sets.get(i).id();
            b.cmd("/clear @s").cmd("/item replace entity @s armor.head with " + id + "_helmet").cmd("/item replace entity @s armor.chest with " + id + "_chestplate")
                    .cmd("/item replace entity @s armor.legs with " + id + "_leggings").cmd("/item replace entity @s armor.feet with " + id + "_boots")
                    .cmd("/fw give " + focus[i] + " 1").wait(10).slot(0).wait(10).camera(CameraType.THIRD_PERSON_FRONT).wait(10)
                    .screenshot("reg_worn_" + sets.get(i).id()).camera(CameraType.THIRD_PERSON_BACK).wait(6).screenshot("reg_worn_back_" + sets.get(i).id());
            if (i == 0) b.camera(CameraType.THIRD_PERSON_FRONT).cmd("/damage @s 1 minecraft:generic").wait(4).screenshot("reg_hit");
        }
        b.camera(CameraType.FIRST_PERSON).cmd("/clear @s");
        for (var set : sets) for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) b.cmd("/give @s fantasyweapons:" + set.itemId(type));
        b.wait(20).run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(20).hud(true)
                .screenshot("reg_icons").closeScreen().hud(false);
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
                java.util.Map.entry("monolith", new int[]{2, 5}), java.util.Map.entry("infernochain", new int[]{4, 16}),
                java.util.Map.entry("pyromancer", new int[]{2, 6}), java.util.Map.entry("cryomancer", new int[]{2, 10}),
                java.util.Map.entry("tidemancer", new int[]{3, 10}), java.util.Map.entry("archdruid", new int[]{3, 14}),
                java.util.Map.entry("luminarch", new int[]{2, 5}), java.util.Map.entry("umbramancer", new int[]{3, 12}));
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
    /** Runs {@code action} with the single player on the server thread. */
    /** {@link #onServer}, the script waiting (up to five minutes) until the server has done it. */
    private static void onServerAndWait(ScreenshotDirector.Builder b, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        boolean[] done = {false};
        b.run(mc -> {
            done[0] = false;
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                try {
                    action.accept(srv.getPlayerList().getPlayers().get(0));
                } finally {
                    done[0] = true;
                }
            });
        });
        b.until(mc -> done[0], 6000);
    }

    private static void onServer(ScreenshotDirector.Builder b, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> action.accept(srv.getPlayerList().getPlayers().get(0)));
        });
    }

    /**
     * The legendary tools' powers in a built scene: a fully upgraded pickaxe's 13x13 outline on a stone wall, Ore Sight
     * showing ores buried behind it, the wall dug; Timberfall on a grove of oaks; Earthshaper on mounds of earth.
     */
    private static void toolPowers(ScreenshotDirector.Builder b) {
        String max = "[fantasyweapons:tool_upgrades={reach:6,fortune:10,efficiency:10}]";
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/clear @s")
                .cmd("/give @s fantasyweapons:void_crystal_pickaxe" + max).cmd("/give @s fantasyweapons:verdant_axe").cmd("/give @s fantasyweapons:titanstone_shovel" + max)
                .cmd("/fill -12 -60 -30 12 -40 30 air").cmd("/fill -9 -60 6 9 -46 13 stone")
                .cmd("/setblock 2 -55 9 fantasyweapons:solarite_ore").cmd("/setblock -4 -58 11 fantasyweapons:infernite_ore")
                .cmd("/setblock 5 -50 8 fantasyweapons:void_crystal_ore").cmd("/setblock -6 -52 12 fantasyweapons:frostheart_ore")
                .cmd("/setblock 0 -59 13 fantasyweapons:verdant_ore").wait(20)
                .cmd("/tp @s 0.5 -60 2.5 0 -8").wait(30).slot(0).wait(10).hud(true).screenshot("powers_outline");
        b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.ToolAbility()));
        b.wait(10).screenshot("powers_sight_10").wait(20).screenshot("powers_sight_30").wait(60).screenshot("powers_sight_90");
        b.viewAt(-4, 6, -2, 0, -4, 9).hud(false).wait(5).screenshot("powers_sight_above").playerView().hud(true);
        onServer(b, sp -> sp.gameMode.destroyBlock(new net.minecraft.core.BlockPos(0, -59, 6)));
        b.wait(20).screenshot("powers_dug");
        // a grove behind the player, felled by Timberfall
        b.cmd("/fill -12 -60 -14 12 -61 -2 grass_block").cmd("/place feature minecraft:oak -6 -59 -6").cmd("/place feature minecraft:oak 4 -59 -9")
                .cmd("/place feature minecraft:birch -9 -59 -12").cmd("/place feature minecraft:oak 9 -59 -5").cmd("/place feature minecraft:fancy_oak 0 -59 -13")
                .cmd("/tp @s 0.5 -60 2.5 180 5").wait(30).slot(1).wait(10).hud(false).screenshot("powers_grove");
        b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.ToolAbility()));
        b.wait(2).screenshot("powers_timber_2").wait(4).screenshot("powers_timber_6").wait(30).screenshot("powers_timber_36");
        // mounds of earth, levelled by Earthshaper
        b.cmd("/fill 6 -60 -4 13 -57 4 dirt").cmd("/fill -12 -60 -3 -7 -56 3 sand").cmd("/fill -4 -60 -9 3 -58 -6 gravel")
                .cmd("/tp @s 0.5 -60 0.5 0 30").wait(20).slot(2).wait(5).viewAt(0, 9, -12, 0, 0, 0).wait(5).screenshot("powers_mounds");
        b.run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.ToolAbility()));
        b.wait(3).screenshot("powers_earth_3").wait(30).screenshot("powers_earth_33").playerView();
    }

    /**
     * The Legendary Upgrading Table: built in front of the player with a Void Crystal pickaxe in its vise and Void Cores in
     * its cup, idle; its menu; an upgrade of the mining area from the menu, filmed through its pulses; the menu and the tool
     * after it.
     */
    private static void upgrader(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/tp @s 0.5 -60 -2.5 0 20").wait(30).hud(false);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var level = srv.overworld();
                var core = new net.minecraft.core.BlockPos(0, -60, 0);
                var block = com.fantasyweapons.registry.ModBlocks.UPGRADING_TABLE.get();
                level.setBlock(core, block.defaultBlockState().setValue(com.fantasyweapons.machine.MachineCoreBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
                block.placeParts(level, core, net.minecraft.core.Direction.NORTH);
                if (level.getBlockEntity(core) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.TOOL, new net.minecraft.world.item.ItemStack(
                            com.fantasyweapons.registry.ModItems.tool(com.fantasyweapons.material.Material.VOID_CRYSTAL, com.fantasyweapons.tool.ToolKind.PICKAXE)));
                    be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.CATALYST, new net.minecraft.world.item.ItemStack(
                            com.fantasyweapons.registry.ModItems.REFINED.get(com.fantasyweapons.material.Material.VOID_CRYSTAL).get(), 12));
                }
            });
        }).wait(30);
        b.viewAt(0.2, 1.1, -1.9, 0, 1.1, 0.5).wait(20).screenshot("upgrader_idle_front");
        b.viewAt(1.6, 1.6, -1.4, 0, 1.0, 0.5).wait(5).screenshot("upgrader_idle_side").playerView();
        b.hud(true).run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayers().get(0);
                if (srv.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 0)) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    sp.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
                }
            });
        }).wait(15).screenshot("upgrader_menu");
        b.run(mc -> {
            if (mc.player.containerMenu instanceof com.fantasyweapons.tool.UpgradingMenu m) mc.gameMode.handleInventoryButtonClick(m.containerId, 0);
        }).wait(40).screenshot("upgrader_menu_working").closeScreen().hud(false);
        b.viewAt(0.2, 1.1, -1.9, 0, 1.1, 0.5);
        int[] at = {60, 88, 110, 128, 168, 200};
        int prev = 40;
        for (int t : at) {
            b.wait(t - prev).screenshot("upgrader_working_" + t);
            prev = t;
        }
        b.wait(260 - prev).screenshot("upgrader_done").playerView().hud(true);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayers().get(0);
                if (srv.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 0)) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    sp.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
                }
            });
        }).wait(15).screenshot("upgrader_menu_after").closeScreen();
    }

    /**
     * The Weapon of God's fusion: the table built with the Great Sword of Obliteration in its vise and the Spell Book of
     * Absolute Domination in its cup; its menu offering the fusion; the fusion filmed through its minute; the menu after.
     */
    private static void fusion(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/tp @s 0.5 -60 -2.5 0 20").wait(30).hud(false);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var level = srv.overworld();
                var core = new net.minecraft.core.BlockPos(0, -60, 0);
                var block = com.fantasyweapons.registry.ModBlocks.UPGRADING_TABLE.get();
                level.setBlock(core, block.defaultBlockState().setValue(com.fantasyweapons.machine.MachineCoreBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
                block.placeParts(level, core, net.minecraft.core.Direction.NORTH);
                if (level.getBlockEntity(core) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.TOOL, new net.minecraft.world.item.ItemStack(
                            com.fantasyweapons.registry.ModItems.WEAPONS.get(com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration.ID).get()));
                    be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.CATALYST, new net.minecraft.world.item.ItemStack(
                            com.fantasyweapons.registry.ModItems.WEAPONS.get(com.fantasyweapons.weapons.domination.SpellBookOfDomination.ID).get()));
                }
            });
        }).wait(30);
        b.viewAt(0.2, 1.1, -1.9, 0, 1.1, 0.5).wait(20).screenshot("fusion_idle_front");
        b.viewAt(1.6, 1.6, -1.4, 0, 1.0, 0.5).wait(5).screenshot("fusion_idle_side").playerView();
        b.hud(true).run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayers().get(0);
                if (srv.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 0)) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    sp.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
                }
            });
        }).wait(15).screenshot("fusion_menu");
        b.run(mc -> {
            if (mc.player.containerMenu instanceof com.fantasyweapons.tool.UpgradingMenu m) {
                mc.gameMode.handleInventoryButtonClick(m.containerId, com.fantasyweapons.tool.UpgradingTableBlockEntity.FUSE);
            }
        }).wait(200).screenshot("fusion_menu_working").closeScreen().hud(false);
        b.viewAt(0.2, 1.3, -2.3, 0, 1.2, 0.5);
        int[] at = {320, 600, 900, 1150, 1199, 1203};
        int prev = 200;
        for (int t : at) {
            b.wait(t - prev).screenshot("fusion_working_" + t);
            prev = t;
        }
        b.wait(1240 - prev).screenshot("fusion_done").playerView().hud(true);
        b.run(mc -> {
            var srv = mc.getSingleplayerServer();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayers().get(0);
                if (srv.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -60, 0)) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                    sp.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
                }
            });
        }).wait(15).screenshot("fusion_menu_after").closeScreen();
    }

    /**
     * The Weapon of God held in first person, with its faces culled and then with every face drawn, and an empty hand,
     * each for ten seconds (the frame times are in the perf lines between the markers); then the hotbar icon, and the
     * charge pose from the front (the radiant edge, drawn translucent on its own).
     */
    private static void godPerf(ScreenshotDirector.Builder b) {
        String god = com.fantasyweapons.weapons.god.WeaponOfGod.ID;
        b.wait(40).cmd("/clear @s").cmd("/fw give " + god + " 1").cmd("/time set noon").wait(40).slot(0).look(0, 0)
                .playerView().camera(CameraType.FIRST_PERSON).hud(true).wait(100);
        b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("godperf: culled"));
        cpu(b, "culled");
        b.run(mc -> {
            com.fantasyweapons.client.render.WeaponRenderer.drawAllFaces = true;
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("godperf: all faces");
        });
        cpu(b, "all faces");
        b.run(mc -> com.fantasyweapons.client.render.WeaponRenderer.fullIcons = true);
        cpu(b, "all faces + full icon");
        b.run(mc -> {
            com.fantasyweapons.client.render.WeaponRenderer.drawAllFaces = false;
            com.fantasyweapons.client.render.WeaponRenderer.fullIcons = false;
        });
        b.run(mc -> {
            com.fantasyweapons.client.render.WeaponRenderer.drawAllFaces = false;
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("godperf: empty hand");
        }).slot(4).wait(200).run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("godperf: done")).slot(0).wait(20);
        b.screenshot("godperf_fp");
        b.hud(false).pin(-1, false, false, 0.8f, -1, -1).viewFrom(0.8, 0.5, 4.2).wait(20).screenshot("godperf_charge_front")
                .viewFrom(4.2, 0.5, 0.6).wait(5).screenshot("godperf_charge_side").pin(-1, false, false, -1, -1, -1).playerView();
    }

    /**
     * The Weapon of God's walls: three holes 40 blocks across cut into the land side by side - a shaft lined like Dimension
     * Breakage's (rainbow), one like the Combined Power of the 6 Gods' (shattered) and a crater like Trident from Heaven's
     * (golden) - in land with lakes and rivers, to see the walls and the water sealed off at them.
     */
    /** Logs the water beside open air (to the sides or below) round each of the wall holes, with where the first few are. */
    /** The water beside air before the wall holes were cut (to tell what the cut left from what was there). */
    private static final java.util.Set<Long> NATURAL_LEAKS = new java.util.HashSet<>();

    private static void waterProbe(ScreenshotDirector.Builder b, String when, int bx, int bz, int r, int step, int[] ys) {
        onServerAndWait(b, sp -> {
            var level = sp.serverLevel();
            net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
            net.minecraft.core.Direction[] dirs = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN};
            for (int i = 0; i < 3; i++) {
                int x0 = bx + i * step, z0 = bz, leaks = 0, flowing = 0;
                StringBuilder where = new StringBuilder();
                for (int x = x0 - r - 20; x <= x0 + r + 20; x++) {
                    for (int z = z0 - r - 20; z <= z0 + r + 20; z++) {
                        for (int y = ys[i] - 60; y <= ys[i] + 20; y++) {
                            var f = level.getFluidState(m.set(x, y, z));
                            if (!f.is(net.minecraft.tags.FluidTags.WATER)) continue;
                            if (!f.isSource()) flowing++;
                            for (var d : dirs) {
                                if (!level.getBlockState(m.set(x, y, z).move(d)).isAir()) continue;
                                long key = net.minecraft.core.BlockPos.asLong(x, y, z);
                                if (when.equals("before")) NATURAL_LEAKS.add(key);
                                else if (NATURAL_LEAKS.contains(key)) break;
                                if (leaks < 6) where.append(String.format(" [%d,%d,%d d=%.0f dy=%d open %s, %s]", x, y, z, Math.hypot(x - x0, z - z0), y - ys[i], d,
                                        level.getFluidState(m.set(x, y, z)).isSource() ? "still" : "flowing"));
                                leaks++;
                                break;
                            }
                        }
                    }
                }
                com.fantasyweapons.FantasyWeapons.LOGGER.warn("walls water {} hole {}: {} beside air, {} flowing;{}", when, i, leaks, flowing, where);
            }
        });
    }

    private static void walls(ScreenshotDirector.Builder b) {
        int bx = 2200, bz = 6100, r = 40, step = 160;
        int[] ys = new int[3];
        b.wait(20).run(mc -> {
            if (mc.player != null && !mc.player.isAlive()) mc.player.respawn();
        }).wait(40).closeScreen().cmd("/gamemode creative").cmd("/time set noon").cmd("/weather clear")
                .cmd("/tp @s " + (bx + step) + " 200 " + (bz - 90) + " 0 30").wait(300);
        onServerAndWait(b, sp -> {
            var level = sp.serverLevel();
            for (int i = 0; i < 3; i++) {
                int x0 = bx + i * step;
                for (int cx = (x0 - r - 30) >> 4; cx <= (x0 + r + 30) >> 4; cx++) for (int cz = (bz - r - 40) >> 4; cz <= (bz + r + 30) >> 4; cz++) level.getChunk(cx, cz);
                ys[i] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x0, bz - r);
            }
        });
        waterProbe(b, "before", bx, bz, r, step, ys);
        onServerAndWait(b, sp -> {
            var level = sp.serverLevel();
            com.fantasyweapons.world.wreck.Wall[] walls = {com.fantasyweapons.world.wreck.Wall.RAINBOW, com.fantasyweapons.world.wreck.Wall.SHATTERED,
                    com.fantasyweapons.world.wreck.Wall.GOLDEN};
            for (int i = 0; i < 3; i++) {
                int x0 = bx + i * step, z0 = bz;
                for (int cx = (x0 - r - 30) >> 4; cx <= (x0 + r + 30) >> 4; cx++) for (int cz = (z0 - r - 40) >> 4; cz <= (z0 + r + 30) >> 4; cz++) level.getChunk(cx, cz);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x0, z0 - r);
                ys[i] = y;
                net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(x0 + 0.5, y, z0 + 0.5);
                com.fantasyweapons.world.wreck.ChunkShaper shaper = i == 2 ? com.fantasyweapons.world.wreck.WreckShapes.sphere(c, r, walls[i])
                        : com.fantasyweapons.world.wreck.WreckShapes.shaft(c, r, walls[i]);
                for (int cx = (x0 - r - 30) >> 4; cx <= (x0 + r + 30) >> 4; cx++) {
                    for (int cz = (z0 - r - 30) >> 4; cz <= (z0 + r + 30) >> 4; cz++) {
                        com.fantasyweapons.world.wreck.WorldWreck.rewrite(level, level.getChunk(cx, cz), 7L + i, shaper);
                    }
                }
            }
        });
        // water left touching open air round each hole (it would pour in): before the cut, right after it, and once it has had
        // time to flow
        b.wait(2);
        waterProbe(b, "after", bx, bz, r, step, ys);
        b.wait(200);
        waterProbe(b, "later", bx, bz, r, step, ys);
        b.wait(20);
        String[] names = {"rainbow", "shattered", "golden"};
        for (int i = 0; i < 3; i++) {
            int x0 = bx + i * step, k = i;
            // from outside, over the pond, looking across the hole at its far wall
            onServerAndWait(b, sp -> {
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.teleportTo(sp.serverLevel(), x0 + 0.5, ys[k] + 22, bz - r - 30, 0, 28);
            });
            b.wait(80).hud(false).playerView().wait(5).screenshot("walls_" + names[i] + "_outside");
            // from inside the hole, looking back at the near wall where the pond was cut through
            onServerAndWait(b, sp -> sp.teleportTo(sp.serverLevel(), x0 + 0.5, ys[k] - 6, bz - 8, 180, 10));
            b.wait(60).screenshot("walls_" + names[i] + "_inside");
        }
    }

    /** Ten seconds of the weapons' drawing time on the render thread, logged as milliseconds per draw. */
    private static void cpu(ScreenshotDirector.Builder b, String label) {
        b.run(mc -> {
            com.fantasyweapons.client.render.WeaponRenderer.debugNanos = 0;
            com.fantasyweapons.client.render.WeaponRenderer.debugDraws = 0;
        }).wait(200).run(mc -> {
            long n = com.fantasyweapons.client.render.WeaponRenderer.debugNanos, d = Math.max(1, com.fantasyweapons.client.render.WeaponRenderer.debugDraws);
            com.fantasyweapons.FantasyWeapons.LOGGER.warn("godcpu: {} -> {} draws, {} ms per draw", label, d, String.format("%.3f", n / 1e6 / d));
        });
    }

    /**
     * A weapon's inventory icon drawn large, glow and all, on flat magenta, for making a picture of it: the screen rectangle
     * the icon's 16 pixels fill is logged.
     */
    private static void icon(ScreenshotDirector.Builder b, String weapon) {
        b.wait(40).cmd("/clear @s").cmd("/fw give " + weapon + " 1").wait(40).slot(0).hud(false);
        b.run(mc -> {
            com.fantasyweapons.client.render.WeaponRenderer.fullIcons = true;
            net.minecraft.world.item.ItemStack stack = mc.player.getMainHandItem().copy();
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("icon")) {
                @Override
                public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                    g.fill(0, 0, width, height, 0xFFFF00FF);
                    float s = Math.min(width, height) * 0.8f / 16f;
                    float x = width / 2f - 8 * s, y = height / 2f - 8 * s;
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0);
                    g.pose().scale(s, s, 1);
                    g.renderItem(stack, 0, 0);
                    g.pose().popPose();
                    double k = minecraft.getWindow().getGuiScale();
                    com.fantasyweapons.FantasyWeapons.LOGGER.warn("icon rect {} {} {}", Math.round(x * k), Math.round(y * k), Math.round(16 * s * k));
                }

                @Override
                public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                }
            });
        }).wait(20).screenshot("icon_" + weapon).closeScreen().run(mc -> com.fantasyweapons.client.render.WeaponRenderer.fullIcons = false);
    }

    /** The Weapon of God's tooltip, its Codex pages (and the rites page), and its quests. */
    /**
     * On a dedicated server (runClient -PfwServer=localhost against runWgtest), over a real network connection: each of
     * the Weapon of God's powers named ({@code a+b+c}) cast in turn on fresh land, while the connection is watched; a
     * disconnect is logged with its reason and filmed, and the client stops.
     */
    private static void multiplayer(ScreenshotDirector.Builder b, String spec) {
        // a+b+c[/distance from the caster to the target[/ticks watched after each cast]]
        String[] parts = spec.split("/");
        String[] abilities = parts[0].split("\\+");
        double dist = parts.length > 1 ? Double.parseDouble(parts[1]) : 300;
        int watch = parts.length > 2 ? Integer.parseInt(parts[2]) : 2400;
        double baseZ = parts.length > 3 ? Double.parseDouble(parts[3]) : 0;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen d && !mpStopping) {
                mpStopping = true;
                com.fantasyweapons.FantasyWeapons.LOGGER.error("mp: DISCONNECTED: {}", d.getNarrationMessage().getString());
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "fw_mp_disconnected.png", mc.getMainRenderTarget(), c -> {
                });
                mc.execute(mc::stop);
            }
        });
        b.wait(100).cmd("/fw class set berserker").closeScreen().wait(5).closeScreen()
                .cmd("/gamemode creative").cmd("/time set noon").cmd("/gamerule doDaylightCycle false").cmd("/gamerule doMobSpawning false")
                .cmd("/clear @s").cmd("/fw give " + com.fantasyweapons.weapons.god.WeaponOfGod.ID + " 1").wait(40).slot(0).closeScreen();
        for (int i = 0; i < abilities.length; i++) {
            String ability = abilities[i];
            double ox = 1200 * (i + 1), oz = baseZ;
            b.cmd("/tp @s " + (ox + 0.5) + " 200 " + (oz - dist + 0.5) + " 0 20").run(mc -> {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }).wait(600).cmd("/fw cooldowns").wait(20).screenshot("mp_" + ability + "_before")
                    .run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("mp: casting {}", ability))
                    .run(mc -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.fantasyweapons.network.C2SPayloads.CastAt(ability, ox + 0.5, 80, oz + 0.5)));
            for (int t = 1; t <= watch / 200; t++) {
                int at = t * 200;
                b.wait(200).run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("mp: {} t={} still connected", ability, at));
                if (t % 3 == 0) b.screenshot("mp_" + ability + "_" + at);
            }
        }
        b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("mp: all powers cast, still connected")).quit();
    }

    private static volatile boolean mpStopping;

    /** The magic circles hundreds of blocks across, drawn from their textures (as they were) and in lines, near and far. */
    private static void circles(ScreenshotDirector.Builder b) {
        b.wait(40).closeScreen().cmd("/gamemode creative").cmd("/time set midnight").cmd("/gamerule doDaylightCycle false").cmd("/kill @e[type=!player]")
                .cmd("/tp @s 0.5 160 0.5 0 30").run(mc -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }).hud(false).wait(100);
        b.run(mc -> {
            net.minecraft.world.phys.Vec3 c = mc.player.position().add(0, -40, 160);
            net.minecraft.world.phys.Vec3 up = new net.minecraft.world.phys.Vec3(0, 1, 0);
            com.fantasyweapons.client.vfx.VfxManager.add(new com.fantasyweapons.client.vfx.effects.DecalVfx(c, up, 150, com.fantasyweapons.client.vfx.Colors.argb(230, 0xB06CFF),
                    com.fantasyweapons.client.vfx.VfxTextures.GRAND_CIRCLE, 100000).spin(0.0005f).timing(0.001f, 0.001f).satellites(0));
            com.fantasyweapons.client.vfx.VfxManager.add(new com.fantasyweapons.client.vfx.effects.DecalVfx(c.add(-150, 1, -60), up, 40, com.fantasyweapons.client.vfx.Colors.argb(230, 0x7FE0FF),
                    com.fantasyweapons.client.vfx.VfxTextures.SUB_CIRCLE, 100000).timing(0.001f, 0.001f).satellites(0));
            com.fantasyweapons.client.vfx.VfxManager.add(new com.fantasyweapons.client.vfx.effects.DecalVfx(c.add(150, 1, -60), up, 40, com.fantasyweapons.client.vfx.Colors.argb(230, 0xFFD27F),
                    com.fantasyweapons.client.vfx.VfxTextures.RUNE_CIRCLE, 100000).timing(0.001f, 0.001f).satellites(0));
            com.fantasyweapons.client.vfx.VfxManager.add(new com.fantasyweapons.client.vfx.Vfx(100000) {
                @Override
                public void render(com.fantasyweapons.client.vfx.VfxContext ctx) {
                    var band = ctx.additive(com.fantasyweapons.client.vfx.VfxTextures.RUNE_BAND);
                    float step = (float) (Math.PI * 2 / 24);
                    for (int i = 0; i < 24; i++) {
                        ctx.runeBand(band, c.add(0, 0.5, 0), new net.minecraft.world.phys.Vec3(1, 0, 0), new net.minecraft.world.phys.Vec3(0, 0, 1), 156, 172, i * step, (i + 1) * step, 1, 6, i,
                                com.fantasyweapons.client.vfx.Colors.argb(230, 0xE9D9FF));
                    }
                }
            });
        });
        for (boolean lines : new boolean[]{false, true}) {
            String k = lines ? "lines" : "texture";
            b.run(mc -> com.fantasyweapons.client.vfx.MagicCircles.texturesOnly = !lines).wait(5);
            b.viewAt(0, 60, -20, 0, -40, 160).wait(10).screenshot("circles_" + k + "_far");
            b.viewAt(0, -25, 40, 0, -40, 200).wait(10).screenshot("circles_" + k + "_low");
            b.viewAt(-20, -30, 140, -40, -40, 230).wait(10).screenshot("circles_" + k + "_close");
            b.viewAt(-140, -30, 80, -150, -39, 100).wait(10).screenshot("circles_" + k + "_sub");
            b.viewAt(140, -30, 80, 150, -39, 100).wait(10).screenshot("circles_" + k + "_rune");
        }
        // the cost: the same view held with the textures, then with the lines (the perf probe logs each second)
        for (boolean lines : new boolean[]{false, true, false, true}) {
            b.run(mc -> com.fantasyweapons.client.vfx.MagicCircles.texturesOnly = !lines).viewAt(0, -25, 40, 0, -40, 200)
                    .run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.warn("circles: holding {}", lines ? "lines" : "textures")).wait(140);
        }
        b.run(mc -> com.fantasyweapons.client.vfx.MagicCircles.texturesOnly = false).playerView().quit();
    }

    private static void godUi(ScreenshotDirector.Builder b) {
        String god = com.fantasyweapons.weapons.god.WeaponOfGod.ID;
        b.wait(40).cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").cmd("/fw class set mage").closeScreen().wait(5).closeScreen()
                .cmd("/fw give " + god + " 100").wait(60).slot(0);
        b.run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getMainHandItem()))).wait(10).screenshot("godui_tooltip").closeScreen().wait(5);
        b.cmd("/clear @s").cmd("/give @s fantasyweapons:wayfarers_codex").wait(20).slot(0).hud(true).wait(10)
                .run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110);
        for (int i = 0; i < 2; i++) {
            int n = i;
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("ultimates") + n, false))
                    .wait(16).screenshot("godui_rites_" + n);
        }
        for (int i = 0; i < 9; i++) {
            int n = i;
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("weapon_" + god) + n, false))
                    .wait(16).screenshot("godui_codex_" + n);
        }
        b.closeScreen().wait(10);
    }

    /** The upgrading menu in survival (some steps too dear), mid-work and done; then the Codex's contents and tools chapter. */
    private static void toolBook(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").cmd("/time set noon").cmd("/clear @s").cmd("/experience set @s 12 levels").cmd("/tp @s 0.5 -60 -2.5 0 20").wait(30);
        onServer(b, sp -> {
            var level = sp.serverLevel();
            var core = new net.minecraft.core.BlockPos(0, -60, 0);
            var block = com.fantasyweapons.registry.ModBlocks.UPGRADING_TABLE.get();
            level.setBlock(core, block.defaultBlockState().setValue(com.fantasyweapons.machine.MachineCoreBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
            block.placeParts(level, core, net.minecraft.core.Direction.NORTH);
            if (level.getBlockEntity(core) instanceof com.fantasyweapons.tool.UpgradingTableBlockEntity be) {
                var tool = new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.tool(com.fantasyweapons.material.Material.SOLARITE,
                        com.fantasyweapons.tool.ToolKind.PICKAXE));
                tool.set(com.fantasyweapons.registry.ModComponents.TOOL_UPGRADES.get(), new com.fantasyweapons.tool.ToolUpgrades(1, 6, 10));
                be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.TOOL, tool);
                be.items().setStackInSlot(com.fantasyweapons.tool.UpgradingTableBlockEntity.CATALYST, new net.minecraft.world.item.ItemStack(
                        com.fantasyweapons.registry.ModItems.REFINED.get(com.fantasyweapons.material.Material.SOLARITE).get(), 5));
                sp.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
            }
        });
        b.wait(15).screenshot("toolbook_menu");
        b.run(mc -> {
            if (mc.player.containerMenu instanceof com.fantasyweapons.tool.UpgradingMenu m) mc.gameMode.handleInventoryButtonClick(m.containerId, 0);
        }).wait(60).screenshot("toolbook_working").wait(200).screenshot("toolbook_done").closeScreen();
        bookPages(b);
    }

    /** The Codex's contents and its tools chapter. */
    private static void bookPages(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/give @s fantasyweapons:wayfarers_codex").wait(10).slot(0).hud(true)
                .run(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)).wait(110).screenshot("toolbook_contents");
        for (int i = 0; i < 3; i++) {
            int k = i;
            b.run(mc -> com.fantasyweapons.client.guide.Codex.turnTo(com.fantasyweapons.client.guide.Codex.book().spreadOf("tools") + k, false)).wait(18)
                    .screenshot("toolbook_" + k);
        }
        b.closeScreen().wait(20);
    }

    /** The legendary tools: every icon beside vanilla tools, a tooltip, and a pickaxe, shovel and axe held in first and third person. */
    private static void tools(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/time set noon").cmd("/clear @s").wait(20).hud(true).cmd("/tp @s 0 -60 0 0 0").wait(10);
        b.run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("tools")) {
            @Override
            public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                super.render(g, mx, my, pt);
                var kinds = com.fantasyweapons.tool.ToolKind.values();
                net.minecraft.world.item.Item[] vanilla = {net.minecraft.world.item.Items.NETHERITE_PICKAXE, net.minecraft.world.item.Items.NETHERITE_SHOVEL,
                        net.minecraft.world.item.Items.NETHERITE_AXE};
                for (int k = 0; k < kinds.length; k++) {
                    int y = 12 + k * 22;
                    g.renderFakeItem(new net.minecraft.world.item.ItemStack(vanilla[k]), 6, y);
                    int i = 0;
                    for (var m : com.fantasyweapons.material.Material.values()) {
                        com.fantasyweapons.client.hud.UiDraw.rect(g, 30 + i * 20, y, 46 + i * 20, y + 16, 0x30FFFFFF);
                        g.renderFakeItem(new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.tool(m, kinds[k])), 30 + i * 20, y);
                        i++;
                    }
                }
                var stack = new net.minecraft.world.item.ItemStack(com.fantasyweapons.registry.ModItems.tool(com.fantasyweapons.material.Material.VOID_CRYSTAL,
                        com.fantasyweapons.tool.ToolKind.PICKAXE));
                g.renderTooltip(font, stack, 20, 100);
                var maxed = stack.copy();
                maxed.set(com.fantasyweapons.registry.ModComponents.TOOL_UPGRADES.get(), com.fantasyweapons.tool.ToolUpgrades.MAX);
                g.renderTooltip(font, maxed, width / 2 + 10, 100);
            }
        })).wait(10).screenshot("tools_icons").closeScreen().wait(5);
        String[] held = {"void_crystal_pickaxe", "solarite_shovel", "infernite_axe", "netherite_pickaxe", "netherite_shovel", "netherite_axe"};
        for (String id : held) b.cmd("/give @s " + (id.startsWith("netherite") ? "minecraft:" : "fantasyweapons:") + id);
        b.cmd("/tp @s 0 -60 0 0 10").wait(20);
        for (int i = 0; i < held.length; i++) {
            b.slot(i).wait(15).hud(true).camera(net.minecraft.client.CameraType.FIRST_PERSON).screenshot("tools_fp_" + held[i]).hud(false)
                    .viewFrom(-0.9, 0.5, 1.7, 1.0).wait(5).screenshot("tools_front_" + held[i])
                    .viewFrom(-1.9, 0.6, 0.1, 1.0).wait(5).screenshot("tools_side_" + held[i]).playerView();
        }
        b.hud(true).camera(net.minecraft.client.CameraType.FIRST_PERSON).slot(0).swing().wait(3).screenshot("tools_fp_swing");
    }

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
        String[][] casts = {{"solaris", "celestial_inferno", "40,130,210,300,420,540,620,700,780"}, {"voidfang", "rift_tear", "10,70,150"}};
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

    /** The ultimate weapons' long impact frames, tinted for the beam, the black hole and the fourth dimension, pinned in place. */
    private static void tinted(ScreenshotDirector.Builder b) {
        int[][] tints = {{0xFFB43A, 0xFFF6D6}, {0xFF9636, 0xFFF8EE}, {0x9B4DFF, 0x30E8FF}};
        String[] names = {"beam", "blackhole", "fourth"};
        b.wait(40).cmd("/fw class set mage").closeScreen().wait(5).closeScreen().cmd("/gamemode spectator").cmd("/time set noon")
                .cmd("/tp @s 0 110 -60 0 25").wait(120).closeScreen().hud(false);
        for (int k = 0; k < tints.length; k++) {
            int[] tint = tints[k];
            String n = names[k];
            b.run(mc -> com.fantasyweapons.client.fx.ImpactFrames.debugElapsed = 10L)
                    .run(mc -> com.fantasyweapons.client.fx.ImpactFrames.cataclysm(1f, null, tint[0], tint[1])).wait(4);
            for (long ms : new long[]{40, 200, 270, 370, 480, 590, 670, 770, 1030}) {
                b.run(mc -> com.fantasyweapons.client.fx.ImpactFrames.debugElapsed = ms).wait(6).screenshot("tinted_" + n + "_" + ms);
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

    /**
     * Better Combat (run with {@code -PwithBetterCombat}): each kind of weapon swings its combo at three dummies through
     * the real attack key, filmed from behind and in first person; every swing's damage on each dummy is logged
     * ({@code [bctest]}), with the tooltip check and the thrown-weapon switch.
     */
    private static void betterCombat(ScreenshotDirector.Builder b, java.util.List<String> only) {
        b.cmd("/gamemode creative").hud(false);
        String[] weapons = {"voidfang", "solaris", "doomcleaver", "soulreaper", "starforge", "aetherlance", "monolith", "infernochain",
                "void_crystal_axe", "rimeheart", "codex_of_tides"};
        for (String w : weapons) {
            if (only != null && !only.contains(w)) continue;
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            float speed = def != null ? def.weaponClass().attackSpeed() : 1.0f;
            int cooldown = Math.round(20f / speed);
            // on cleared ground away from the spawn, where earlier films left machines standing
            b.cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]").cmd("/clear @s")
                    .cmd(def != null ? "/fw give " + w + " 1" : "/give @s fantasyweapons:" + w).wait(12).slot(0);
            for (int i = 0; i < 3; i++) {
                b.cmd("/summon minecraft:husk " + (200.5 + (i - 1) * 1.4) + " -60 " + (i == 1 ? 203.5 : 203.1)
                        + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
            }
            b.playerView().camera(CameraType.THIRD_PERSON_BACK).look(0, 8).wait(cooldown + 20);
            b.run(mc -> {
                var held = mc.player.getMainHandItem();
                var lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, held);
                boolean range = lines.stream().anyMatch(l -> l.getString().contains("Attack Range"));
                com.fantasyweapons.FantasyWeapons.LOGGER.info("[bctest] {} drivenByBetterCombat={} tooltipLines={} attackRangeLine={}", w,
                        com.fantasyweapons.compat.bettercombat.BetterCombatCompat.drivesSwing(mc.player, held), lines.size(), range);
            });
            logDummies(b, w, "start");
            for (int k = 0; k < 3; k++) {
                b.click().wait(Math.max(1, cooldown / 4)).screenshot("bc_" + w + "_" + k + "_wind").wait(Math.max(1, cooldown / 4) + 2)
                        .screenshot("bc_" + w + "_" + k + "_hit").wait(cooldown / 2 + 4);
                logDummies(b, w, "swing" + k);
            }
            b.camera(CameraType.FIRST_PERSON).hud(true).wait(cooldown).click().wait(Math.max(1, cooldown / 4)).screenshot("bc_" + w + "_fp_wind")
                    .wait(Math.max(1, cooldown / 4) + 2).screenshot("bc_" + w + "_fp_hit").wait(cooldown).hud(false);
            logDummies(b, w, "fp");
            if (def != null && def.hasForms()) {
                // the other form swings the same combo (Infernochain's chainblade lashes its burning chain)
                String other = def.forms().get(def.forms().size() - 1).id();
                b.camera(CameraType.THIRD_PERSON_BACK).form(other).wait(40).click().wait(Math.max(1, cooldown / 2) + 2)
                        .screenshot("bc_" + w + "_" + other + "_hit").wait(3).screenshot("bc_" + w + "_" + other + "_after").wait(cooldown);
                logDummies(b, w, other);
            }
        }
        if (only != null) return;
        // a thrown weapon: Better Combat's swings are off while it flies, and on again once it is back in the hand
        b.cmd("/tp @s 200.5 -60 200.5 0 0").cmd("/kill @e[type=!player]").cmd("/clear @s").cmd("/fw give soulreaper 100").wait(20).slot(0).cmd("/fw points 200").cmd("/fw cooldowns")
                .camera(CameraType.THIRD_PERSON_BACK).look(0, 0).select(com.fantasyweapons.weapons.soulreaper.Soulreaper.REAPERS_THROW).wait(10);
        logCombatFlags(b, "before throw");
        b.abilityDown().wait(4).abilityUp().wait(8);
        logCombatFlags(b, "in flight");
        b.screenshot("bc_thrown").wait(100);
        logCombatFlags(b, "back in hand");
        b.camera(CameraType.FIRST_PERSON).playerView();
    }

    /**
     * Punchy (its jar in run/mods): the Wayfarer's Codex opened with the real use key and read, then a blade swung and a
     * tool held, all in first person; logs whether Punchy leaves each item to this mod ({@code [punchytest]}).
     */
    private static void punchy(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]")
                .cmd("/clear @s").cmd("/give @s fantasyweapons:wayfarers_codex").wait(12).slot(0).camera(CameraType.FIRST_PERSON).look(0, 10).hud(true).wait(30);
        logPunchy(b, "codex held");
        b.screenshot("punchy_codex_held").use();
        for (int s = 1; s <= 6; s++) {
            int sec = s;
            b.wait(20).screenshot("punchy_codex_" + s + "s").run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.info("[punchytest] codex at {}s: phase {}, screen {}",
                    sec, com.fantasyweapons.client.guide.Codex.phase(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
        }
        b.closeScreen().wait(30).screenshot("punchy_codex_closed");
        for (String w : new String[]{"voidfang", "solaris"}) {
            b.cmd("/clear @s").cmd("/fw give " + w + " 1").wait(12).slot(0).wait(40);
            logPunchy(b, w + " held");
            b.screenshot("punchy_" + w + "_held").swing();
            for (int f = 0; f < 4; f++) b.wait(3).screenshot("punchy_" + w + "_swing" + f);
            b.wait(30);
        }
        b.cmd("/clear @s").cmd("/give @s fantasyweapons:void_crystal_pickaxe").wait(12).slot(0).wait(30).screenshot("punchy_pickaxe_held");
        logPunchy(b, "pickaxe held");
    }

    /**
     * Punchy's armored first-person arms: an empty hand and a held weapon in several legendary chestplates (and a vanilla
     * one to compare), first person and from the side.
     */
    private static void punchyArmor(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]")
                .cmd("/clear @s").camera(CameraType.FIRST_PERSON).look(0, 25).hud(true).wait(20);
        for (String set : new String[]{"voidfang", "solaris", "doomcleaver", "pyromancer", "minecraft:iron"}) {
            String chest = set.startsWith("minecraft:") ? set + "_chestplate" : "fantasyweapons:" + set + "_chestplate";
            b.cmd("/clear @s").cmd("/item replace entity @s armor.chest with " + chest).slot(1).wait(30);
            String tag = set.replace("minecraft:", "vanilla_");
            b.screenshot("parmor_" + tag + "_empty").look(-35, 45).wait(6).screenshot("parmor_" + tag + "_empty_look").look(0, 25);
            b.cmd("/give @s fantasyweapons:voidfang").slot(0).wait(30).screenshot("parmor_" + tag + "_blade");
            b.slot(1).camera(CameraType.THIRD_PERSON_BACK).wait(10).screenshot("parmor_" + tag + "_back").camera(CameraType.FIRST_PERSON).wait(5);
        }
    }

    /**
     * The Codex read with something in the other hand: a shield beside it in the main hand's Codex, then a pickaxe in the
     * main hand beside an off-hand Codex. The other hand goes down as the book comes up and returns once it is lowered.
     */
    private static void codexShield(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode creative").cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]")
                .camera(CameraType.FIRST_PERSON).look(0, 10).hud(true);
        String[][] setups = {{"fantasyweapons:wayfarers_codex", "minecraft:shield", "MAIN_HAND", "shield"},
                {"fantasyweapons:void_crystal_pickaxe", "fantasyweapons:wayfarers_codex", "OFF_HAND", "offhand"}};
        for (String[] setup : setups) {
            var hand = net.minecraft.world.InteractionHand.valueOf(setup[2]);
            String tag = "codexshield_" + setup[3];
            b.cmd("/clear @s").cmd("/item replace entity @s hotbar.0 with " + setup[0]).cmd("/item replace entity @s weapon.offhand with " + setup[1])
                    .slot(0).wait(30).screenshot(tag + "_held").run(mc -> mc.gameMode.useItem(mc.player, hand));
            for (int f = 1; f <= 3; f++) b.wait(2).screenshot(tag + "_raise" + f);
            b.wait(100).screenshot(tag + "_read").closeScreen().wait(20).screenshot(tag + "_closing").wait(70).screenshot(tag + "_closed");
        }
    }

    /**
     * A weapon's first active ability charged and cast, in first and in third person, with the pose timelines logged
     * each frame: to compare the cast animation with Better Combat installed and without.
     */
    private static void betterCombatAbility(ScreenshotDirector.Builder b, String[] weapons) {
        b.cmd("/gamemode creative").hud(false);
        for (String w : weapons) {
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            if (def == null) continue;
            var a = def.abilities().stream().filter(x -> x.kind() == com.fantasyweapons.ability.AbilityKind.ACTIVE && x.requiredForm() == null)
                    .findFirst().orElse(null);
            if (a == null) continue;
            b.cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]").cmd("/clear @s")
                    .cmd("/fw give " + w + " 100").wait(20).slot(0).cmd("/fw points 200").cmd("/fw cooldowns").wait(150);
            for (CameraType view : new CameraType[]{CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
                String tag = "bcab_" + w + (view == CameraType.FIRST_PERSON ? "_fp" : "_tp");
                // the first-person hand is only drawn with the HUD showing
                b.camera(view).hud(view == CameraType.FIRST_PERSON).look(0, 8).cmd("/fw cooldowns").select(a.id()).wait(20).screenshot(tag + "_idle")
                        .abilityDown();
                int charge = Math.max(2, a.chargeTicks() + 1);
                for (int t = 0; t < charge; t += Math.max(1, charge / 3)) b.wait(Math.max(1, charge / 3)).screenshot(tag + "_charge" + t);
                b.abilityUp();
                for (int f = 0; f < 6; f++) {
                    int frame = f;
                    b.wait(2).screenshot(tag + "_cast" + f).run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.info(
                            "[bcability] {} frame {}: charge={} cast={} swing={}", tag, frame,
                            com.fantasyweapons.client.anim.AnimTracker.charge(mc.player, 0),
                            com.fantasyweapons.client.anim.AnimTracker.castProgress(mc.player, 0),
                            com.fantasyweapons.client.anim.AnimTracker.swingProgress(mc.player, 0)));
                }
                b.wait(30);
            }
        }
    }

    /**
     * Up to three active abilities of each weapon, cast at dummies and watched from the side: to compare the effects with
     * a shader pack on and off (run once with each).
     */
    private static void shaderFx(ScreenshotDirector.Builder b, String[] weapons) {
        b.cmd("/gamemode creative").cmd("/time set noon").hud(false);
        for (String w : weapons) {
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            if (def == null) continue;
            var picks = def.abilities().stream().filter(x -> x.kind() == com.fantasyweapons.ability.AbilityKind.ACTIVE && x.requiredForm() == null).limit(3).toList();
            b.cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]").cmd("/clear @s")
                    .cmd("/fw give " + w + " 100").wait(20).slot(0).cmd("/fw points 200").wait(150);
            for (var a : picks) {
                for (int i = 0; i < 3; i++) {
                    b.cmd("/summon minecraft:husk " + (200.5 + (i - 1) * 1.6) + " -60 " + (205.5 + (i % 2))
                            + " {NoAI:1b,Health:1000000f,attributes:[{id:\"minecraft:generic.max_health\",base:1000000d}]}");
                }
                String tag = "sfx_" + w + "_" + a.id();
                b.camera(CameraType.FIRST_PERSON).look(0, 10).cmd("/fw cooldowns").select(a.id()).wait(10).viewFrom(9.5, 3.5, 4.0)
                        .abilityDown().wait(Math.max(2, a.chargeTicks() + 1)).abilityUp();
                for (int t : new int[]{3, 8, 16, 30}) b.wait(t == 3 ? 3 : t == 8 ? 5 : t == 16 ? 8 : 14).screenshot(tag + "_" + t);
                b.playerView().wait(40).cmd("/kill @e[type=!player]");
            }
        }
    }

    /** The settings menu: the inventory with its buttons, then every section of the menu (three of the weapons' abilities). */
    private static void settingsMenu(ScreenshotDirector.Builder b) {
        b.cmd("/gamemode survival").hud(true).wait(10)
                .run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(10).screenshot("settings_inventory")
                // with the recipe book open, which moves the inventory aside
                .closeScreen().run(mc -> mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, true))
                .run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(10).screenshot("settings_inventory_book")
                .closeScreen().run(mc -> mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, false))
                .run(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))).wait(10)
                .run(mc -> mc.setScreen(new com.fantasyweapons.client.gui.SettingsScreen(mc.screen))).wait(10);
        for (int i = 0; i < 9; i++) {
            int section = i;
            b.run(mc -> {
                if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugShow(section, 0);
            }).wait(4).screenshot("settings_" + i);
        }
        // the lowest effect quality, made for weak computers
        b.run(mc -> {
            com.fantasyweapons.config.ClientConfig.VFX_QUALITY.set(com.fantasyweapons.config.ClientConfig.VfxQuality.LOW_END);
            if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugShow(0, 0);
        }).wait(4).screenshot("settings_low_end").run(mc -> com.fantasyweapons.config.ClientConfig.VFX_QUALITY.set(com.fantasyweapons.config.ClientConfig.VfxQuality.HIGH));
        for (int w : new int[]{3, 12}) {
            b.run(mc -> {
                if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugShow(s.debugSections() - 1, w);
            }).wait(4).screenshot("settings_abilities_" + w);
        }
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugLanguage();
        }).wait(4).screenshot("settings_language");
        // a click on Russian switches the game and the menu to it; then back to English
        b.run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugChooseLanguage("ru_ru");
        }).wait(240).screenshot("settings_language_ru").run(mc -> {
            if (mc.screen instanceof com.fantasyweapons.client.gui.SettingsScreen s) s.debugChooseLanguage("en_us");
        }).wait(240).screenshot("settings_language_back");
        b.closeScreen().cmd("/gamemode creative");
    }

    /** Every screen of the mod in turn, for checking a translation: the settings, the HUD and tome, tooltips, quests, machines, the Codex. */
    private static void translated(ScreenshotDirector.Builder b) {
        settingsMenu(b);
        b.cmd("/fw give voidfang 100").cmd("/fw give infernochain 60").wait(20).cmd("/fw points 200").camera(CameraType.FIRST_PERSON).hud(true).look(0, 5);
        b.slot(0).wait(20).screenshot("l10n_hud");
        var def = com.fantasyweapons.weapon.Weapons.get("voidfang");
        b.menu(def.castables().get(0).id()).wait(20).screenshot("l10n_tome").closeScreen().wait(5);
        b.menu("#core").wait(20).screenshot("l10n_tome_core").closeScreen().wait(5);
        b.slot(1).wait(20).screenshot("l10n_hud_chain");
        for (String item : new String[]{"fantasyweapons:voidfang_helmet", "fantasyweapons:void_crystal_pickaxe", "fantasyweapons:void_crystal",
                "fantasyweapons:void_core", "fantasyweapons:void_crystal_ore", "fantasyweapons:pyromancer_hood", "fantasyweapons:worldforge"}) {
            String tag = item.substring(item.indexOf(':') + 1);
            b.cmd("/clear @s").cmd("/give @s " + item).wait(10).slot(0)
                    .run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getMainHandItem()))).wait(8).screenshot("l10n_tip_" + tag).closeScreen().wait(4);
        }
        b.cmd("/clear @s").cmd("/fw give voidfang 40").wait(20).slot(0)
                .run(mc -> mc.setScreen(new TooltipPreviewScreen(mc.player.getMainHandItem()))).wait(8).screenshot("l10n_tip_voidfang").closeScreen().wait(4);
        quests(b);
        forge(b);
        upgrader(b);
        codexAll(b);
        // last, for it ends the run
        refinery(b);
    }

    /** What Punchy makes of the held items: whether each hand is left to this mod (on Punchy's blacklist). */
    private static void logPunchy(ScreenshotDirector.Builder b, String tag) {
        b.run(mc -> {
            String state;
            try {
                Class<?> config = Class.forName("punchy.config.PunchyConfig");
                var check = config.getMethod("isHandBlacklisted", net.minecraft.world.entity.player.Player.class, net.minecraft.world.InteractionHand.class);
                state = "main left to us=" + check.invoke(null, mc.player, net.minecraft.world.InteractionHand.MAIN_HAND)
                        + ", off left to us=" + check.invoke(null, mc.player, net.minecraft.world.InteractionHand.OFF_HAND);
            } catch (ReflectiveOperationException e) {
                state = "Punchy not installed";
            }
            com.fantasyweapons.FantasyWeapons.LOGGER.info("[punchytest] {}: {}", tag, state);
        });
    }

    /** One weapon's Better Combat combo frame by frame (every 3 ticks), from behind and from the side. */
    private static void betterCombatFrames(ScreenshotDirector.Builder b, String[] weapons) {
        b.cmd("/gamemode creative").hud(false);
        for (String w : weapons) {
            var def = com.fantasyweapons.weapon.Weapons.get(w);
            int cooldown = Math.round(20f / def.weaponClass().attackSpeed());
            b.cmd("/tp @s 200.5 -60 200.5 0 0").wait(10).cmd("/fill 190 -60 190 210 -50 212 air").cmd("/kill @e[type=!player]").cmd("/clear @s")
                    .cmd("/fw give " + w + " 1").wait(12).slot(0).camera(CameraType.THIRD_PERSON_BACK).look(0, 8).wait(cooldown + 20);
            for (int k = 0; k < 3; k++) {
                b.click();
                for (int f = 0; f * 3 < cooldown; f++) b.wait(f == 0 ? 1 : 3).screenshot("bcf_" + w + "_" + k + "_" + f);
                b.wait(6);
            }
        }
    }

    private static void logDummies(ScreenshotDirector.Builder b, String weapon, String tag) {
        b.run(mc -> {
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null) return;
                StringBuilder s = new StringBuilder();
                for (var h : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, sp.getBoundingBox().inflate(8))) {
                    s.append(String.format("x=%.1f taken=%.1f  ", h.getX(), h.getMaxHealth() - h.getHealth()));
                }
                com.fantasyweapons.FantasyWeapons.LOGGER.info("[bctest] {} {}: {}", weapon, tag, s);
            });
        });
    }

    private static void logCombatFlags(ScreenshotDirector.Builder b, String tag) {
        b.run(mc -> com.fantasyweapons.FantasyWeapons.LOGGER.info("[bctest] soulreaper {}: drivenByBetterCombat={}", tag,
                com.fantasyweapons.compat.bettercombat.BetterCombatCompat.drivesSwing(mc.player, mc.player.getMainHandItem())));
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
