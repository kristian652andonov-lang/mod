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
}
