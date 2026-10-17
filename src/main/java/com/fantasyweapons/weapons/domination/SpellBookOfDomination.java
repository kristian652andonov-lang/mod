package com.fantasyweapons.weapons.domination;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.ability.AbilityText;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.Rarity;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;

import static com.fantasyweapons.ability.AbilityText.Upgrades;

/**
 * THE SPELL BOOK OF ABSOLUTE DOMINATION — the mage's ultimate weapon, bound on the Worldforge from every
 * staff and spellbook. Its six spells of six powers are all cast from afar, through the targeting map, and each one
 * remakes the world where it falls (see {@link com.fantasyweapons.world.wreck.WorldWreck}).
 * <pre>
 *              [THE BEAM OF GRANT EXECUTION] (light)
 *              /                            \
 *   [THE ULTRAMASSIVE BLACKHOLE] (void)   [THE EARTHSPLITTING ERUPTION] (fire)
 *              |                            |
 *   [TECTONIC BREAKAGE] (earth)          [THE ROOTS OF THE GREAT TREE] (nature)
 *              \                            /
 *                     [THE ICE AGE] (ice)
 * </pre>
 */
public final class SpellBookOfDomination {
    public static final String ID = "spell_book_of_absolute_domination";
    public static final String GRANT_EXECUTION = "beam_of_grant_execution";
    public static final String BLACKHOLE = "ultramassive_blackhole";
    public static final String ERUPTION = "earthsplitting_eruption";
    public static final String TECTONIC = "tectonic_breakage";
    public static final String GREAT_TREE = "roots_of_the_great_tree";
    public static final String ICE_AGE = "the_ice_age";

    private SpellBookOfDomination() {
    }

    public static WeaponDefinition create() {
        return WeaponDefinition.builder(ID)
                .name("The Spell Book of Absolute Domination", "The Last Word")
                .lore("Every spell ever written was bound into one book, and the book learned that it no longer had to ask. It does not cast; it commands. Light, void, fire, stone, root and ice kneel when it opens, and the land below obeys or is erased.",
                        "the Codex of Endings")
                .element(Element.DOMINION)
                .rarity(Rarity.CALAMITY)
                .geo("codex_omega")
                .type(WeaponClass.TOME)
                .damage(18f, 1.8f)
                .crit(0.15f, 2.2f)
                .theme(0xF5C542, 0xFFFFFF)
                .ability(AbilityDefinition.builder(GRANT_EXECUTION, AbilityKind.ULTIMATE)
                        .name("The Beam of Grant Execution")
                        .description("Choose a place on the map. An enormous magic circle opens so high in the sky that it fades into the blue, ringed by thirty-two lesser circles and triangles, and a beam of white and golden light falls from it. Everything within 128 blocks is erased, down to the bottom of the world.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(2, 0)
                        .standard(50.0, 0, 180.0)
                        .remote(32)
                        .param("radius", 128, "Radius erased by the beam")
                        .param("delay", 6, "Seconds the circle takes to open before the beam falls")
                        .param("duration", 5, "Seconds the beam burns")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::grantExecution)
                        .build())
                .ability(AbilityDefinition.builder(BLACKHOLE, AbilityKind.ULTIMATE)
                        .name("The Ultramassive Blackhole")
                        .description("Choose a place on the map and tear a black hole open above it. It drags in everything within 256 blocks, creatures, players and the ground itself, block by block, and what it swallows is deleted from the world.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(1, 1, GRANT_EXECUTION)
                        .standard(50.0, 0, 300.0)
                        .remote(32)
                        .param("radius", 256, "Radius the black hole swallows")
                        .param("duration", 16, "Seconds the black hole feeds")
                        .param("pull", 1.6, "How hard it drags creatures in")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("DURATION", AbilityText.param("duration", "s", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::blackhole)
                        .build())
                .ability(AbilityDefinition.builder(ERUPTION, AbilityKind.ULTIMATE)
                        .name("The Earthsplitting Eruption")
                        .description("Choose a place on the map. The ground splits open in enormous glowing cracks, and then the earth itself explodes upward in a colossal eruption. When the fire settles, nothing is left on top but lava, magma and coal.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(3, 1, GRANT_EXECUTION)
                        .standard(48.0, 0, 180.0)
                        .remote(32)
                        .param("radius", 128, "Radius of the eruption")
                        .param("delay", 6, "Seconds the ground cracks before it erupts")
                        .param("depth", 24, "How deep the eruption blasts the ground")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::eruption)
                        .build())
                .ability(AbilityDefinition.builder(TECTONIC, AbilityKind.ULTIMATE)
                        .name("Tectonic Breakage")
                        .description("Choose a place on the map and break the plates of the world. The land splits in two and an enormous canyon slowly tears open all the way down to bedrock, filling with lava from below while the broken ground tumbles in.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(1, 2, BLACKHOLE)
                        .standard(45.0, 0, 240.0)
                        .remote(32)
                        .param("length", 320, "Length of the canyon")
                        .param("width", 56, "Width of the canyon when fully open")
                        .param("duration", 18, "Seconds the canyon takes to open")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("LENGTH", AbilityText.param("length", "m", 0))
                        .stat("WIDTH", AbilityText.param("width", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::tectonic)
                        .build())
                .ability(AbilityDefinition.builder(GREAT_TREE, AbilityKind.ULTIMATE)
                        .name("The Roots of the Great Tree")
                        .description("Choose a place on the map. The roots of the tree that holds up the world burst out of the ground, enormous, and seize and crush everything within 128 blocks. They never go back: a tangle of giant roots stays there forever.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(3, 2, ERUPTION)
                        .standard(46.0, 0, 200.0)
                        .remote(32)
                        .param("radius", 128, "Radius the roots seize")
                        .param("roots", 40, "Giant roots that break out")
                        .param("duration", 8, "Seconds the roots crush what they hold")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("ROOTS", AbilityText.param("roots", "", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::greatTree)
                        .build())
                .ability(AbilityDefinition.builder(ICE_AGE, AbilityKind.ULTIMATE)
                        .name("The Ice Age")
                        .description("Choose a place on the map. An enormous tsunami rises there and rolls out in every direction, destroying everything in sight, and wherever it passes it freezes: the land, the water and every living thing in its wake are turned to ice.")
                        .unlock(1).levels(5, 20).cost(3, 2)
                        .node(2, 3, TECTONIC, GREAT_TREE)
                        .standard(46.0, 0, 240.0)
                        .remote(32)
                        .param("radius", 160, "How far the tsunami rolls")
                        .param("height", 40, "Height of the wave")
                        .param("speed", 1.2, "Blocks the wave rolls per tick")
                        .stat("DAMAGE", AbilityText.damage())
                        .stat("RADIUS", AbilityText.param("radius", "m", 0))
                        .stat("WAVE", AbilityText.param("height", "m", 0))
                        .stat("RANGE", AbilityText.param("remote_range", " chunks", 0))
                        .stat("COOLDOWN", AbilityText.cooldown())
                        .upgrades(Upgrades.create().damagePerLevel(0.18).cooldownPerLevel(0.05).build())
                        .animations("spell_cast", "spell_activation", "recharge")
                        .executor(DominationAbilities::iceAge)
                        .build())
                .build();
    }
}
