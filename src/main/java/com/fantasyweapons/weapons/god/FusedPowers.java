package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import com.fantasyweapons.weapons.domination.DominationAbilities;
import com.fantasyweapons.weapons.domination.SpellBookOfDomination;
import com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration;
import com.fantasyweapons.weapons.obliteration.ObliterationAbilities;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Weapon of God's six powers of the elements: each is the Great Sword's and the Spell Book's power of one element
 * cast together on the same place, greater than either and timed to meet - the beam and the slashes, the broken
 * dimensions and the black hole, the dragon and the eruption, the earthquake and the canyons, the roots and the thorns,
 * the ice rain and the ice age. The parts are those powers themselves, cast with the Weapon of God's settings.
 */
public final class FusedPowers {
    private FusedPowers() {
    }

    private static WeaponDefinition sword() {
        return Weapons.get(GreatSwordOfObliteration.ID);
    }

    private static WeaponDefinition book() {
        return Weapons.get(SpellBookOfDomination.ID);
    }

    /** A calamity's power with some of its settings changed ({@code name, value} pairs), to be cast as a part. */
    private static AbilityDefinition part(WeaponDefinition from, String id, Object... overrides) {
        return from.ability(id).variant(overrides);
    }

    /** The fused power's cast, as a cast of {@code part}: from where the caster stood, landing on the same spot. */
    static AbilityContext as(AbilityContext ctx, AbilityDefinition part, Vec3 at) {
        return new AbilityContext(ctx.player(), ctx.level(), ctx.stack(), ctx.item(), ctx.weapon(), ctx.data(), part, ctx.abilityLevel(), ctx.charge(),
                ctx.weaponDamage(), ctx.mastery(), at, ctx.aim());
    }

    /** As {@link #as}, but for a power that rises round the caster: it rises round {@code at} instead, facing {@code look}. */
    static AbilityContext standingAt(AbilityContext ctx, AbilityDefinition part, Vec3 at, Vec3 look) {
        return new AbilityContext(ctx.player(), ctx.level(), ctx.stack(), ctx.item(), ctx.weapon(), ctx.data(), part, ctx.abilityLevel(), ctx.charge(),
                ctx.weaponDamage(), ctx.mastery(), at, new AbilityContext.Aim(at.add(0, 1.6, 0), look.normalize(), at));
    }

    /** The way from the caster to {@code at}, level (or where they look, if it is right under them). */
    static Vec3 sight(AbilityContext ctx, Vec3 at) {
        Vec3 d = new Vec3(at.x - ctx.feet().x, 0, at.z - ctx.feet().z);
        if (d.lengthSqr() < 1) d = new Vec3(ctx.look().x, 0, ctx.look().z);
        if (d.lengthSqr() < 1e-4) d = new Vec3(0, 0, 1);
        return d.normalize();
    }

    /** Runs {@code then} {@code ticks} from now, while the caster is still in the world. */
    static void later(AbilityContext ctx, Vec3 at, int ticks, Runnable then) {
        AreaEffectManager.add(new Later(ctx, at, ticks, then));
    }

    private static final class Later extends AreaEffect {
        private final int ticks;
        private final Runnable then;

        Later(AbilityContext ctx, Vec3 at, int ticks, Runnable then) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, Math.max(1, ticks) + 1);
            this.ticks = Math.max(1, ticks);
            this.then = then;
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age != ticks) return;
            try {
                then.run();
            } catch (RuntimeException e) {
                com.fantasyweapons.FantasyWeapons.LOGGER.error("A part of a power of the Weapon of God failed", e);
            }
            finished = true;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // light: the Beam of Grant Execution and the Great Slash
    // ------------------------------------------------------------------------------------------------------------

    /** The beam falls; the moment it bites, the Great Slashes burst out of it in every direction. */
    public static boolean finalDawn(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        int delay = (int) Math.round(ctx.param("delay") * 20), beam = (int) Math.round(ctx.param("duration") * 20);
        DominationAbilities.grantExecution(as(ctx, part(book(), SpellBookOfDomination.GRANT_EXECUTION,
                "radius", ctx.param("radius"), "delay", ctx.param("delay"), "duration", ctx.param("duration")), at));
        AbilityDefinition slash = part(sword(), GreatSwordOfObliteration.GREAT_SLASH, "length", ctx.param("length"), "width", ctx.param("width"));
        int n = Math.max(1, (int) Math.round(ctx.param("slashes")));
        Vec3 sight = sight(ctx, at);
        later(ctx, at, delay + 4, () -> {
            for (int k = 0; k < n; k++) {
                Vec3 dir = sight.yRot((float) (Math.PI / 4 + k * Math.PI * 2 / n));
                ObliterationAbilities.greatSlash(standingAt(ctx, slash, at.add(0, 2, 0), dir));
            }
        });
        Kit.active(ctx, delay + beam + 60);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // void: 4th Dimension Ascension and the Ultramassive Blackhole
    // ------------------------------------------------------------------------------------------------------------

    /** Space breaks over the target; then the black hole tears open over the broken land and eats it. */
    public static boolean abyss(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        double shatterDelay = 6;
        ObliterationAbilities.fourthDimension(as(ctx, part(sword(), GreatSwordOfObliteration.FOURTH_DIMENSION,
                "radius", ctx.param("shatter_radius"), "delay", shatterDelay), at));
        AbilityDefinition hole = part(book(), SpellBookOfDomination.BLACKHOLE, "radius", ctx.param("radius"), "duration", ctx.param("duration"), "pull", 1.9);
        int broken = (int) (shatterDelay * 20) + 30;
        later(ctx, at, broken, () -> DominationAbilities.blackhole(as(ctx, hole, at)));
        Kit.active(ctx, broken + (int) Math.round(ctx.param("duration") * 20));
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // fire: the Arch Dragon of Hell and the Earthsplitting Eruption
    // ------------------------------------------------------------------------------------------------------------

    /** The ground at the target cracks and glows while the dragon flies there; it dives in as the earth erupts. */
    public static boolean hellfire(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        double radius = ctx.param("radius"), flight = ctx.param("flight");
        ObliterationAbilities.archDragon(as(ctx, part(sword(), GreatSwordOfObliteration.ARCH_DRAGON,
                "radius", radius, "flight", flight, "range", 1024), at));
        DominationAbilities.eruption(as(ctx, part(book(), SpellBookOfDomination.ERUPTION,
                "radius", radius * 0.8, "delay", flight, "depth", 32), at));
        Kit.active(ctx, (int) Math.round(flight * 20) + 80);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // earth: the Grand Earthquake and Tectonic Breakage
    // ------------------------------------------------------------------------------------------------------------

    /** The earthquake breaks the land outward from the target; canyons tear across it, crossing there. */
    public static boolean breaking(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        Vec3 sight = sight(ctx, at);
        ObliterationAbilities.grandEarthquake(standingAt(ctx, part(sword(), GreatSwordOfObliteration.GRAND_EARTHQUAKE,
                "radius", ctx.param("radius"), "safe_radius", 10, "duration", 10), at, sight));
        int n = Math.max(1, (int) Math.round(ctx.param("canyons")));
        for (int k = 0; k < n; k++) {
            AbilityDefinition canyon = part(book(), SpellBookOfDomination.TECTONIC, "length", ctx.param("length"), "width", 64, "duration", 16,
                    "turn", k * 180.0 / n);
            later(ctx, at, 50 + k * 25, () -> DominationAbilities.tectonic(as(ctx, canyon, at)));
        }
        Kit.active(ctx, 50 + n * 25 + 16 * 20);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // nature: the Roots of the Great Tree and the Thornspire Cataclysm
    // ------------------------------------------------------------------------------------------------------------

    /** The roots burst out and seize everything; the thorns spiral out among them. */
    public static boolean worldTree(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        double radius = ctx.param("radius");
        DominationAbilities.greatTree(as(ctx, part(book(), SpellBookOfDomination.GREAT_TREE,
                "radius", radius, "roots", ctx.param("roots"), "duration", 10), at));
        AbilityDefinition thorns = part(sword(), GreatSwordOfObliteration.THORNSPIRE, "radius", radius, "safe_radius", 8, "spires", ctx.param("spires"));
        Vec3 sight = sight(ctx, at);
        later(ctx, at, 30, () -> ObliterationAbilities.thornspire(standingAt(ctx, thorns, at, sight)));
        Kit.active(ctx, 30 + 160);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // ice: Ice Rain and the Ice Age
    // ------------------------------------------------------------------------------------------------------------

    /** The storm cloud rains spikes of ice over the target; while they fall the sea rises and the wave rolls out. */
    public static boolean winter(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        double radius = ctx.param("radius");
        ObliterationAbilities.iceRain(standingAt(ctx, part(sword(), GreatSwordOfObliteration.ICE_RAIN,
                "radius", radius * 0.85, "safe_radius", 0, "duration", 16, "spikes", ctx.param("spikes")), at, sight(ctx, at)));
        AbilityDefinition wave = part(book(), SpellBookOfDomination.ICE_AGE, "radius", radius, "height", 48, "speed", 1.3);
        later(ctx, at, 70, () -> DominationAbilities.iceAge(as(ctx, wave, at)));
        Kit.active(ctx, 70 + DominationAbilities.SURGE + (int) (radius / 1.3));
        return true;
    }
}
