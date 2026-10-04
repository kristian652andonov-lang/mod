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
}
