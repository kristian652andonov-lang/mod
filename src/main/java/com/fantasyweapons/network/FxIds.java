package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.resources.ResourceLocation;

/** Identifiers of every custom effect the server can ask clients to play. */
public final class FxIds {
    private FxIds() {
    }

    // ---- generic ----
    public static final ResourceLocation MELEE_HIT = FantasyWeapons.id("melee_hit");
    public static final ResourceLocation DAMAGE_NUMBER = FantasyWeapons.id("damage_number");
    public static final ResourceLocation LEVEL_UP = FantasyWeapons.id("level_up");
    public static final ResourceLocation FORM_SWITCH = FantasyWeapons.id("form_switch");
    public static final ResourceLocation ABILITY_FIZZLE = FantasyWeapons.id("ability_fizzle");
    public static final ResourceLocation STATUS_BURST = FantasyWeapons.id("status_burst");
    public static final ResourceLocation DEATH_DISSOLVE = FantasyWeapons.id("death_dissolve");

    // ---- voidfang ----
    public static final ResourceLocation VOID_SLASH = FantasyWeapons.id("voidfang/void_slash");
    public static final ResourceLocation VOID_BLINK = FantasyWeapons.id("voidfang/void_blink");
    public static final ResourceLocation VOID_RIFT = FantasyWeapons.id("voidfang/rift");
    public static final ResourceLocation VOID_RIFT_COLLAPSE = FantasyWeapons.id("voidfang/rift_collapse");
    public static final ResourceLocation VOID_MARK_POP = FantasyWeapons.id("voidfang/mark_pop");
    public static final ResourceLocation VOID_EXECUTION = FantasyWeapons.id("voidfang/void_execution");
    public static final ResourceLocation VOID_DIMENSION = FantasyWeapons.id("voidfang/void_dimension");
    public static final ResourceLocation VOID_DIMENSION_STRIKE = FantasyWeapons.id("voidfang/dimension_strike");
    public static final ResourceLocation VOID_DIMENSION_COLLAPSE = FantasyWeapons.id("voidfang/dimension_collapse");

    // ---- solaris ----
    public static final ResourceLocation SOLARIS_RADIANT_SLASH = FantasyWeapons.id("solaris/radiant_slash");
    public static final ResourceLocation SOLARIS_SOLAR_BURST = FantasyWeapons.id("solaris/solar_burst");
    public static final ResourceLocation SOLARIS_SUPERNOVA = FantasyWeapons.id("solaris/supernova");
    public static final ResourceLocation SOLARIS_SUPERNOVA_IMPACT = FantasyWeapons.id("solaris/supernova_impact");
    public static final ResourceLocation SOLARIS_INFERNO = FantasyWeapons.id("solaris/inferno");
    public static final ResourceLocation SOLARIS_INFERNO_STRIKE = FantasyWeapons.id("solaris/inferno_strike");
    public static final ResourceLocation SOLARIS_INFERNO_COLLAPSE = FantasyWeapons.id("solaris/inferno_collapse");

    // ---- frostrend ----
    public static final ResourceLocation FROSTREND_FROST_SLASH = FantasyWeapons.id("frostrend/frost_slash");
    public static final ResourceLocation FROSTREND_ICE_SPIKES = FantasyWeapons.id("frostrend/ice_spikes");
    public static final ResourceLocation FROSTREND_FREEZE = FantasyWeapons.id("frostrend/freeze");
    public static final ResourceLocation FROSTREND_GLACIAL_DOMAIN = FantasyWeapons.id("frostrend/glacial_domain");
    public static final ResourceLocation FROSTREND_ABSOLUTE_ZERO = FantasyWeapons.id("frostrend/absolute_zero");
    public static final ResourceLocation FROSTREND_SHATTER = FantasyWeapons.id("frostrend/shatter");
    public static final ResourceLocation FROSTREND_ABSOLUTE_ZERO_END = FantasyWeapons.id("frostrend/absolute_zero_end");

    // ---- doomcleaver ----
    public static final ResourceLocation DOOMCLEAVER_CLEAVE = FantasyWeapons.id("doomcleaver/cleave");
    public static final ResourceLocation DOOMCLEAVER_RAGE = FantasyWeapons.id("doomcleaver/rage");
    public static final ResourceLocation DOOMCLEAVER_FEED = FantasyWeapons.id("doomcleaver/feed");
    public static final ResourceLocation DOOMCLEAVER_LEAP = FantasyWeapons.id("doomcleaver/leap");
    public static final ResourceLocation DOOMCLEAVER_LEAP_LAND = FantasyWeapons.id("doomcleaver/leap_land");
    public static final ResourceLocation DOOMCLEAVER_APOCALYPSE = FantasyWeapons.id("doomcleaver/apocalypse");
    public static final ResourceLocation DOOMCLEAVER_DRAIN = FantasyWeapons.id("doomcleaver/drain");
    public static final ResourceLocation DOOMCLEAVER_APOCALYPSE_END = FantasyWeapons.id("doomcleaver/apocalypse_end");

    // ---- stormbreaker ----
    public static final ResourceLocation STORMBREAKER_CHAIN = FantasyWeapons.id("stormbreaker/chain");
    public static final ResourceLocation STORMBREAKER_ARCS = FantasyWeapons.id("stormbreaker/arcs");
    public static final ResourceLocation STORMBREAKER_SPIN = FantasyWeapons.id("stormbreaker/spin");
    public static final ResourceLocation STORMBREAKER_STRIKE_WARN = FantasyWeapons.id("stormbreaker/strike_warn");
    public static final ResourceLocation STORMBREAKER_STRIKE = FantasyWeapons.id("stormbreaker/strike");
    public static final ResourceLocation STORMBREAKER_WRATH = FantasyWeapons.id("stormbreaker/wrath");
    public static final ResourceLocation STORMBREAKER_BOLT = FantasyWeapons.id("stormbreaker/bolt");
    public static final ResourceLocation STORMBREAKER_WRATH_END = FantasyWeapons.id("stormbreaker/wrath_end");

    // ---- gravebite ----
    public static final ResourceLocation GRAVEBITE_SOUL_VOLLEY = FantasyWeapons.id("gravebite/soul_volley");
    public static final ResourceLocation GRAVEBITE_SOUL_HIT = FantasyWeapons.id("gravebite/soul_hit");
    public static final ResourceLocation GRAVEBITE_HARVEST = FantasyWeapons.id("gravebite/harvest");
    public static final ResourceLocation GRAVEBITE_CHAINS = FantasyWeapons.id("gravebite/chains");
    public static final ResourceLocation GRAVEBITE_MAW = FantasyWeapons.id("gravebite/maw");
    public static final ResourceLocation GRAVEBITE_LEGION = FantasyWeapons.id("gravebite/legion");
    public static final ResourceLocation GRAVEBITE_LEGION_LAUNCH = FantasyWeapons.id("gravebite/legion_launch");
    public static final ResourceLocation GRAVEBITE_LEGION_END = FantasyWeapons.id("gravebite/legion_end");

    // ---- soulreaper ----
    public static final ResourceLocation SOULREAPER_REAP = FantasyWeapons.id("soulreaper/reap");
    public static final ResourceLocation SOULREAPER_CATCH = FantasyWeapons.id("soulreaper/catch");
    public static final ResourceLocation SOULREAPER_REND = FantasyWeapons.id("soulreaper/rend");
    public static final ResourceLocation SOULREAPER_TOLL = FantasyWeapons.id("soulreaper/toll");

    // ---- bloomfall ----
    public static final ResourceLocation BLOOMFALL_THORN_SWEEP = FantasyWeapons.id("bloomfall/thorn_sweep");
    public static final ResourceLocation BLOOMFALL_ROOTS = FantasyWeapons.id("bloomfall/roots");
    public static final ResourceLocation BLOOMFALL_SPORES = FantasyWeapons.id("bloomfall/spores");
    public static final ResourceLocation BLOOMFALL_OVERGROWTH = FantasyWeapons.id("bloomfall/overgrowth");
    public static final ResourceLocation BLOOMFALL_WRATH = FantasyWeapons.id("bloomfall/wrath");
    public static final ResourceLocation BLOOMFALL_WRATH_END = FantasyWeapons.id("bloomfall/wrath_end");

    // ---- eclipse reaper ----
    public static final ResourceLocation ECLIPSE_DISC_HIT = FantasyWeapons.id("eclipse/disc_hit");
    public static final ResourceLocation ECLIPSE_RESONANCE = FantasyWeapons.id("eclipse/resonance");
    public static final ResourceLocation ECLIPSE_SOLAR_FLARE = FantasyWeapons.id("eclipse/solar_flare");
    public static final ResourceLocation ECLIPSE_UMBRAL_VORTEX = FantasyWeapons.id("eclipse/umbral_vortex");
    public static final ResourceLocation ECLIPSE_TOTAL = FantasyWeapons.id("eclipse/total");
    public static final ResourceLocation ECLIPSE_BEAM = FantasyWeapons.id("eclipse/beam");
    public static final ResourceLocation ECLIPSE_TOTAL_END = FantasyWeapons.id("eclipse/total_end");

    // ---- starforge ----
    public static final ResourceLocation STARFORGE_WELL = FantasyWeapons.id("starforge/well");
    public static final ResourceLocation STARFORGE_SLAM = FantasyWeapons.id("starforge/slam");
    public static final ResourceLocation STARFORGE_SLAM_IMPACT = FantasyWeapons.id("starforge/slam_impact");
    public static final ResourceLocation STARFORGE_METEOR = FantasyWeapons.id("starforge/meteor");
    public static final ResourceLocation STARFORGE_METEOR_IMPACT = FantasyWeapons.id("starforge/meteor_impact");
    public static final ResourceLocation STARFORGE_HORIZON = FantasyWeapons.id("starforge/horizon");
    public static final ResourceLocation STARFORGE_HORIZON_END = FantasyWeapons.id("starforge/horizon_end");
    public static final ResourceLocation STARFORGE_STARFALL = FantasyWeapons.id("starforge/starfall");

    // ---- aetherlance ----
    /** An ability was cast: impact frames on the caster's screen (and an ultimate's on those nearby). level 1 = ultimate, seed = colour. */
    public static final ResourceLocation IMPACT_FRAME = FantasyWeapons.id("impact_frame");
    public static final ResourceLocation AETHERLANCE_THRUST = FantasyWeapons.id("aetherlance/thrust");
    public static final ResourceLocation AETHERLANCE_BOLT = FantasyWeapons.id("aetherlance/bolt");
    public static final ResourceLocation AETHERLANCE_PIERCE = FantasyWeapons.id("aetherlance/pierce");
    public static final ResourceLocation AETHERLANCE_BOLT_END = FantasyWeapons.id("aetherlance/bolt_end");
    public static final ResourceLocation AETHERLANCE_CHARGE = FantasyWeapons.id("aetherlance/charge");
    public static final ResourceLocation AETHERLANCE_RAY = FantasyWeapons.id("aetherlance/ray");

    // Monolith
    public static final ResourceLocation MONOLITH_PLANT = FantasyWeapons.id("monolith/plant");
    public static final ResourceLocation MONOLITH_SHATTER = FantasyWeapons.id("monolith/shatter");
    public static final ResourceLocation MONOLITH_FISSURE = FantasyWeapons.id("monolith/fissure");
    public static final ResourceLocation MONOLITH_AFTERSHOCK = FantasyWeapons.id("monolith/aftershock");
    public static final ResourceLocation MONOLITH_LEAP = FantasyWeapons.id("monolith/leap");
    public static final ResourceLocation MONOLITH_SLAM = FantasyWeapons.id("monolith/slam");
    public static final ResourceLocation MONOLITH_WORLDBREAKER = FantasyWeapons.id("monolith/worldbreaker");
    public static final ResourceLocation MONOLITH_ERUPTION = FantasyWeapons.id("monolith/eruption");

    // Infernochain
    public static final ResourceLocation INFERNO_LASH = FantasyWeapons.id("infernochain/lash");
    public static final ResourceLocation INFERNO_HOOK = FantasyWeapons.id("infernochain/hook");
    public static final ResourceLocation INFERNO_HOOK_END = FantasyWeapons.id("infernochain/hook_end");
    public static final ResourceLocation INFERNO_CYCLONE = FantasyWeapons.id("infernochain/cyclone");
    public static final ResourceLocation INFERNO_MELTDOWN = FantasyWeapons.id("infernochain/meltdown");
    public static final ResourceLocation INFERNO_DRAKE = FantasyWeapons.id("infernochain/drake");
    public static final ResourceLocation INFERNO_DRAKE_END = FantasyWeapons.id("infernochain/drake_end");

    // ---- full-set armor abilities ----
    public static final ResourceLocation SET_RIFT_ECHO = FantasyWeapons.id("armor/rift_echo");
    public static final ResourceLocation SET_RIFT_PRIMED = FantasyWeapons.id("armor/rift_primed");
    public static final ResourceLocation SET_DAWN_BURST = FantasyWeapons.id("armor/dawn_burst");
    public static final ResourceLocation SET_WINTER_CHILL = FantasyWeapons.id("armor/winter_chill");
    public static final ResourceLocation SET_FRENZY = FantasyWeapons.id("armor/frenzy");
    public static final ResourceLocation SET_FRENZY_PULSE = FantasyWeapons.id("armor/frenzy_pulse");
    public static final ResourceLocation SET_STORM_BOLT = FantasyWeapons.id("armor/storm_bolt");
    public static final ResourceLocation SET_GRAVE_ALLY = FantasyWeapons.id("armor/grave_ally");
    public static final ResourceLocation SET_SOUL_HARVEST = FantasyWeapons.id("armor/soul_harvest");
    public static final ResourceLocation SET_SOUL_RELEASE = FantasyWeapons.id("armor/soul_release");
    public static final ResourceLocation SET_GROVE = FantasyWeapons.id("armor/grove");
    public static final ResourceLocation SET_GROVE_PULSE = FantasyWeapons.id("armor/grove_pulse");
    public static final ResourceLocation SET_ECLIPSE_WAVE = FantasyWeapons.id("armor/eclipse_wave");
    public static final ResourceLocation SET_GRAVITY_WELL = FantasyWeapons.id("armor/gravity_well");
    public static final ResourceLocation SET_PRECISION_BOLT = FantasyWeapons.id("armor/precision_bolt");
    public static final ResourceLocation SET_PRECISION_BOLT_END = FantasyWeapons.id("armor/precision_bolt_end");
    public static final ResourceLocation SET_SHOCKWAVE = FantasyWeapons.id("armor/shockwave");
    public static final ResourceLocation SET_HELLFIRE_TRAIL = FantasyWeapons.id("armor/hellfire_trail");

    // ---- the mage's weapons (client side: MageFx) ----
    /** A green number over a healed ally. power = health restored. */
    public static final ResourceLocation HEAL_NUMBER = FantasyWeapons.id("heal_number");
    /** A staff or spellbook's bolt: pos start, dir velocity, power range, level element, scale 1 staff / 0 book, seed. */
    public static final ResourceLocation MAGE_BOLT = FantasyWeapons.id("mage/bolt");
    public static final ResourceLocation MAGE_BOLT_HIT = FantasyWeapons.id("mage/bolt_hit");
    /** A blessing or ward carried from the caster's focus to an ally (pos focus, point target, level = which spell). */
    public static final ResourceLocation MAGE_BLESS = FantasyWeapons.id("mage/bless");
    public static final ResourceLocation MAGE_WARD_SHATTER = FantasyWeapons.id("mage/ward_shatter");
    public static final ResourceLocation MAGE_BUBBLE_POP = FantasyWeapons.id("mage/bubble_pop");
    public static final ResourceLocation MAGE_SHARD_BLOCK = FantasyWeapons.id("mage/shard_block");
    public static final ResourceLocation MAGE_ASCENSION_RAY = FantasyWeapons.id("mage/ascension_ray");
    public static final ResourceLocation MAGE_SPRING_SAVE = FantasyWeapons.id("mage/spring_save");
    public static final ResourceLocation MAGE_BANE_BLOOM = FantasyWeapons.id("mage/bane_bloom");
    public static final ResourceLocation MAGE_PLAGUE_SPREAD = FantasyWeapons.id("mage/plague_spread");
    // fire: Cinderspire, Pyre Codex
    public static final ResourceLocation CINDER_BRAND = FantasyWeapons.id("cinderspire/brand");
    public static final ResourceLocation CINDER_METEOR = FantasyWeapons.id("cinderspire/meteor");
    public static final ResourceLocation CINDER_COMBUST = FantasyWeapons.id("cinderspire/combust");
    public static final ResourceLocation CINDER_GEYSER = FantasyWeapons.id("cinderspire/geyser");
    public static final ResourceLocation CINDER_VOLCANO = FantasyWeapons.id("cinderspire/volcano");
    public static final ResourceLocation CINDER_BOMB = FantasyWeapons.id("cinderspire/bomb");
    public static final ResourceLocation CINDER_ERUPTION = FantasyWeapons.id("cinderspire/eruption");
    public static final ResourceLocation PYRE_CHAIN = FantasyWeapons.id("pyre_codex/chain");
    public static final ResourceLocation PYRE_WILDFIRE = FantasyWeapons.id("pyre_codex/wildfire");
    public static final ResourceLocation PYRE_WALL = FantasyWeapons.id("pyre_codex/wall");
    public static final ResourceLocation PYRE_PHOENIX = FantasyWeapons.id("pyre_codex/phoenix");
    public static final ResourceLocation PYRE_PHOENIX_CRASH = FantasyWeapons.id("pyre_codex/phoenix_crash");
    // ice: Rimeheart, Frostbound Grimoire
    public static final ResourceLocation RIME_RAMPART = FantasyWeapons.id("rimeheart/rampart");
    public static final ResourceLocation RIME_BASTION = FantasyWeapons.id("rimeheart/bastion");
    public static final ResourceLocation FROST_TOMB = FantasyWeapons.id("frostbound_grimoire/tomb");
    public static final ResourceLocation FROST_TOMB_SHATTER = FantasyWeapons.id("frostbound_grimoire/tomb_shatter");
    public static final ResourceLocation FROST_WINTER = FantasyWeapons.id("frostbound_grimoire/winter");
    public static final ResourceLocation FROST_WINTER_FREEZE = FantasyWeapons.id("frostbound_grimoire/winter_freeze");
    // water: Tidecaller, Codex of Tides
    public static final ResourceLocation TIDE_SPRING = FantasyWeapons.id("tidecaller/spring");
    public static final ResourceLocation TIDE_RIPTIDE = FantasyWeapons.id("tidecaller/riptide");
    public static final ResourceLocation TIDE_MAELSTROM = FantasyWeapons.id("tidecaller/maelstrom");
    public static final ResourceLocation TIDES_RAIN = FantasyWeapons.id("codex_of_tides/rain");
    public static final ResourceLocation TIDES_EMBRACE = FantasyWeapons.id("codex_of_tides/embrace");
    public static final ResourceLocation TIDES_BREAK = FantasyWeapons.id("codex_of_tides/break");
    // nature: Thornwake, Verdant Almanac
    public static final ResourceLocation THORN_LIFEBLOOM = FantasyWeapons.id("thornwake/lifebloom");
    public static final ResourceLocation THORN_PULSE = FantasyWeapons.id("thornwake/pulse");
    public static final ResourceLocation THORN_GROVE = FantasyWeapons.id("thornwake/grove");
    public static final ResourceLocation THORN_WORLDTREE = FantasyWeapons.id("thornwake/worldtree");
    public static final ResourceLocation VERDANT_VINES = FantasyWeapons.id("verdant_almanac/vines");
    public static final ResourceLocation VERDANT_POLLEN = FantasyWeapons.id("verdant_almanac/pollen");
    public static final ResourceLocation VERDANT_SPRING = FantasyWeapons.id("verdant_almanac/spring");
    // light: Sunwarden, Lumen Psalter
    public static final ResourceLocation SUN_SANCTIFY = FantasyWeapons.id("sunwarden/sanctify");
    public static final ResourceLocation SUN_DAWN = FantasyWeapons.id("sunwarden/dawn");
    public static final ResourceLocation LUMEN_HYMN = FantasyWeapons.id("lumen_psalter/hymn");
    public static final ResourceLocation LUMEN_ASCENSION = FantasyWeapons.id("lumen_psalter/ascension");
    // void: Gloamreach, Umbral Codex
    public static final ResourceLocation GLOAM_WITHER = FantasyWeapons.id("gloamreach/wither");
    public static final ResourceLocation GLOAM_MIASMA = FantasyWeapons.id("gloamreach/miasma");
    public static final ResourceLocation GLOAM_DIRGE = FantasyWeapons.id("gloamreach/dirge");
    public static final ResourceLocation UMBRAL_HEX = FantasyWeapons.id("umbral_codex/hex");
    public static final ResourceLocation UMBRAL_SEED = FantasyWeapons.id("umbral_codex/seed");
    public static final ResourceLocation UMBRAL_CONTAGION = FantasyWeapons.id("umbral_codex/contagion");
    public static final ResourceLocation UMBRAL_PLAGUE = FantasyWeapons.id("umbral_codex/plague");

    // ---- the ultimate weapons ----
    /** The Beam of Grant Execution: pos = ground at the target, scale = radius, power = delay ticks, level = beam ticks. */
    public static final ResourceLocation GRANT_EXECUTION = FantasyWeapons.id("domination/grant_execution");
    /** Impact frames of the ultimate weapons' world-breaking blows, for those near: pos = where it lands, scale = how near. */
    public static final ResourceLocation CATACLYSM_FRAME = FantasyWeapons.id("ultimate/cataclysm_frame");
    /** The Ultramassive Blackhole: pos = the hole, dir = the ground under it, scale = reach, power = collapse tick, level = form ticks. */
    public static final ResourceLocation BLACKHOLE = FantasyWeapons.id("domination/blackhole");
    /** The Earthsplitting Eruption: pos = ground at the target, scale = radius, power = delay ticks, level = crater depth. */
    public static final ResourceLocation ERUPTION = FantasyWeapons.id("domination/eruption");
    /** Tectonic Breakage: pos = ground at the target, dir = along the canyon, scale = length, power = width, level = ticks. */
    public static final ResourceLocation TECTONIC = FantasyWeapons.id("domination/tectonic");
    /** The Roots of the Great Tree: pos = target, scale = radius, power = ticks, points = 4 control points per root, entities = root thickness x10. */
    public static final ResourceLocation GREAT_TREE = FantasyWeapons.id("domination/great_tree");
    /** The roots seize these creatures (entities), until power ticks have passed. */
    public static final ResourceLocation GREAT_TREE_GRAB = FantasyWeapons.id("domination/great_tree_grab");
    /** The Ice Age: pos = ground at the target, scale = radius, power = wave height, level = speed x100 (blocks per tick). */
    public static final ResourceLocation ICE_AGE = FantasyWeapons.id("domination/ice_age");
}
