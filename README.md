# Fantasy Weapons

A NeoForge mod for Minecraft 1.21.1 that adds 13 legendary RPG weapons. Each weapon levels up on its own, unlocks a
skill tree of abilities, and has custom poses, animations, VFX, HUD and sounds. All gameplay is server-authoritative.

The weapon models, textures and animations are the artist's original GeckoLib assets (with the artist's updated, higher
resolution textures) and are used unchanged. That
includes Infernochain's **Transform**, **Chainblade attack** and **Retraction** animations, and Eclipse Reaper's
**Transform**.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.200 or newer (developed on 21.1.255) |
| GeckoLib | 4.9.3 (required dependency) |
| Java | 21 |

Build the jar with `./gradlew build`. It is written to `build/libs/fantasyweapons-<version>.jar`.

## Controls

All keys can be rebound under *Controls → Fantasy Weapons*.

| Key | Action |
|---|---|
| **R** (hold) | Charge the selected ability; release to cast. Releasing too early fizzles it. |
| **G** | Cycle the selected ability (sneak + G cycles backwards). |
| **F** | Switch form or mode (Infernochain sword ↔ chainblade, Eclipse Reaper light ↔ dark). |
| **K** | Open the weapon progression menu (skill tree, upgrades, stats, model preview). |
| Attack | Melee with the weapon. Sneak-attack at full strength for a heavy attack. |

## Weapons

Every weapon has five or six abilities, unlocked by weapon level and upgraded with mastery points.

| Weapon | Element / class | Abilities (unlock order) |
|---|---|---|
| **Voidfang** | Void longsword | Void Slash · Void Blink · Void Mark *(passive)* · Rift Tear · Void Execution · **Void Dimension** |
| **Solaris** | Solar greatsword | Radiant Slash · Solar Burst · Sunfire *(passive)* · Supernova · **Celestial Inferno** |
| **Frostrend** | Ice longsword | Frost Slash · Ice Spikes · Frostbite *(passive)* · Glacial Domain · **Absolute Zero** |
| **Doomcleaver** | Blood battle axe | Crimson Cleave · Blood Rage · Bloodthirst *(passive)* · Sanguine Leap · **Crimson Apocalypse** |
| **Stormbreaker** | Lightning battle axe | Chain Lightning · Tempest Spin · Static Charge *(passive)* · Thunderstrike · **Wrath of the Storm** |
| **Gravebite** | Necromancy battle axe | Soul Volley · Grave Chains · Soul Harvest *(passive)* · Death's Maw · **Legion of the Damned** |
| **Soulreaper** | Soul scythe | Reaper's Throw · Soul Rend · Soul Siphon *(passive)* · Reaping Whirl · **Death's Toll** |
| **Bloomfall** | Nature scythe | Thorn Sweep · Entangling Roots · Venom Bloom *(passive)* · Overgrowth · **Wrath of the Wild** |
| **Eclipse Reaper** | Celestial scythe (light/dark modes) | Eclipse Disc · Solar Flare *(light)* · Umbral Vortex *(dark)* · Equilibrium *(passive)* · **Total Eclipse** |
| **Starforge** | Cosmic warhammer | Gravity Slam · Meteor Strike · Gravity Well *(passive)* · Event Horizon · **Starfall** |
| **Aetherlance** | Energy lance | Aether Bolt · Piercing Charge · Aether Resonance *(passive)* · Celestial Barrage · **Judgement Ray** |
| **Monolith** | Earth colossal greatsword | Earthshatter · Seismic Fissure · Mountain's Weight *(passive)* · Tectonic Slam · **Worldbreaker** |
| **Infernochain** | Fire transforming chainblade | Inferno Lash *(form-aware)* · Hellhook · Overheat *(passive)* · Cinder Cyclone *(chainblade)* · **Drake's Wrath** |

The bold ability in each row is the ultimate, unlocked at level 100.

## Progression

- **Per-weapon EXP and levels (1–100).** Kills with the weapon (melee, abilities and damage over time) grant EXP,
  scaled by how strong the mob is.
- **Mastery points.** One per level, plus milestone bonuses. They unlock and upgrade abilities in the skill tree.
  Upgrades are validated on the server.
- **Damage scales with level and mastery.** Ability damage also scales with ability level and charge.
- **Custom status effects.** These don't use vanilla potions or particles, and appear on the HUD:
  - Void Mark, Solar Burn, Frostbite, Frozen, Berserker, Soul Drain, Nature Poison
  - Eclipse Light / Darkness, Gravity Bound, Inferno Overheat, Rooted, Staggered, Seared

## Configuration

- **`config/fantasyweapons-server.toml`**: EXP curve and sources, damage scaling, per-weapon base damage, and every
  ability parameter (damage, charge, cooldown, radius, …). The server syncs these values to clients.
- **`config/fantasyweapons-client.toml`**: VFX quality, distortion, screen shake, VFX instance cap, and HUD layout and
  notifications.

## Commands

Requires operator permission level 2. `/fw` is an alias for `/fantasyweapons`.

| Command | Effect |
|---|---|
| `/fw give <weapon> [level]` | Give a weapon, optionally at a level |
| `/fw level <n>` · `/fw exp <n>` · `/fw points <n>` | Set level / add EXP / set mastery points (held weapon) |
| `/fw reset` · `/fw cooldowns` · `/fw info` | Reset progression / clear cooldowns / print weapon data |

## Ground split API (Monolith)

Monolith's Earthshatter, Seismic Fissure, Tectonic Slam and Worldbreaker report every crack they open to
`com.fantasyweapons.api.GroundSplitAbility.execute(Context)`. The mod itself **never modifies terrain**. The default
handler does nothing, and a GameTest verifies that the arena blocks stay untouched.

You can plug in a ground-split system in either of two ways:

```java
// 1) install the implementation
GroundSplitAbility.setHandler(ctx -> MyGroundSplit.split(ctx.level(), ctx.origin(), ctx.direction(),
        ctx.length(), ctx.width(), ctx.depth()));

// 2) or listen to the cancellable event (fired before the handler)
NeoForge.EVENT_BUS.addListener((GroundSplitAbility.GroundSplitEvent e) -> {
    if (isProtected(e.context().level(), e.context().origin())) e.setCanceled(true);
});
```

`Context` carries:

- the player, level and weapon stack
- the crack's origin, horizontal direction, length, half-width and suggested depth
- the ability id and ability level

## Development

| Task | Command |
|---|---|
| Run the client | `./gradlew runClient` |
| Run all GameTests | `./gradlew runGameTestServer` |
| Scripted screenshot run | `./gradlew runClient -PfwDevtest=<script>` (screenshots land in `run/screenshots`) |
| Regenerate VFX textures, status icons and UI glyphs | `cd tools && python3 gen_textures.py` |
| Regenerate ability icons | `cd tools && python3 gen_icons.py [weapon …]` |

- **GameTests.** There are 66 tests. They cover the EXP curve, damage scaling, codecs, level-ups, server-side upgrade
  validation, fizzles, kill credit, the death dissolve and wall-safe teleports. They also cast every ability of every
  weapon against dummies, and check the ground-split hook and chainblade melee.
- **Screenshot scripts.** Available scripts:
  - `weapon:<id>` casts every ability of a weapon.
  - `ability:<weapon>/<ability>` captures one ability frame by frame.
  - `poses`, `plant`, `infernochain` and `menus` capture poses, Monolith's plant, Infernochain and the menus.
