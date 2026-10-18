# Terrahaul

An Overworld terrain-generation overhaul for **Minecraft Java Edition 1.21.1** on **NeoForge**
(21.1.x).

Terrahaul adds a new world type, **Terrahaul**. It has a much taller world (Y −512 to 2031) and
continental-scale terrain built on a custom density-function model:

- vast plains, plateaus and valleys;
- five styles of mountains whose rare peaks rise towards the build limit;
- large rivers, lakes, fjords, coastlines and waterfalls;
- canyons and geological layers.

Vanilla trees are replaced by procedural trees in every species, with ground detail added. On the
client, the wind animates falling leaves, petals and dust and sways vegetation.

> Terrahaul changes **new worlds created with the Terrahaul world type**. Existing worlds and
> worlds of other types keep their terrain. Tree replacement, ground details and saplings apply
> to all Overworld-like worlds; each can be turned off in the config.

## Features

### World and terrain (Terrahaul world type)
- **Expanded height:** Y −512 to 2031 (2544 blocks, 159 sections). This is set through a custom
  dimension type (`terrahaul:overworld`) and noise settings, not by patching vanilla. Lighting,
  heightmaps, structures and the build limit all use the same range.
- **Continental terrain:** large continents, oceans, archipelagos and mushroom islands. A
  pure-Java terrain model (simplex noise with analytic derivatives, domain warping, eroded fractal
  noise) produces height, cliffs and water levels per column, and Minecraft's noise router samples
  it through custom density functions.
- **Mountains** in five styles:
  - **alpine:** sharp ridges;
  - **ancient:** rounded and worn;
  - **walls:** long ranges with steep faces;
  - **volcanic:** cones with craters, lava and volcanic rock;
  - **meadow:** gentle high pastures.

  Most terrain is moderate; colossal peaks are rare and approach Y 1900+.
- **Plains, plateaus, valleys, hills, windswept uplands and wetlands.**
- **Water:**
  - large meandering rivers and dry riverbeds;
  - lakes of seven kinds: forest, mountain, great, marsh, crater, plateau and glacial;
  - coastlines with beaches and sea cliffs, fjords, and waterfalls at real cliff sites.

  All water is placed with containment checks, so there is no floating water and no flooding.
- **Deep oceans:**
  - ocean floors lie around Y −24..30 and abyssal plains around Y −60..−160, with rolling relief,
    mid-ocean ridges and seamounts;
  - **deep-sea trenches** are long rifts with near-vertical walls that drop to about
    Y −230..−340. Their floors are lined with magma, blackstone and basalt and carry
    **hydrothermal vent chimneys**;
  - ocean monuments are lowered onto the sea floor instead of floating.
- **Cliff overhangs:** rock ledges jut out just below the rims of cliffs, canyon walls and sea
  cliffs, and alcoves are carved into cliff faces.
- **Canyons and ravines** with layered walls, plus natural **arches, rock spires, hoodoos and sea
  stacks**.
- **Material fades:** surface materials blend into each other with speckled gradients instead of
  hard layers:
  - dirt → coarse dirt → gravel → weathered rock → bedrock;
  - sand → sandstone → rock and snow → calcite → rock;
  - sea floors: sand → gravel → clay and tuff with depth;
  - strata bands with fuzzy edges.

  At the surface, grass breaks up into stone as slopes steepen, snow patches thicken towards the
  snow line, and sand creeps inland behind beaches. Fades reach at most 15 blocks below the
  nearest exposed surface, measured sideways too, so cliff and mountain faces get them as well.
- **Trails:** winding dirt paths with frayed coarse-dirt and gravel edges cross plains, forests
  and hills, meet at junctions and end in the open. They avoid water, cliffs, steep mountains and
  deserts.
- **Geological provinces** (granite, dark/andesite, limestone, sedimentary, volcanic, temperate)
  decide the exposed rock. They show as strata bands on cliffs, scree, gravel coasts and river beds.
- **Coherent vanilla biomes:** the terrain model outputs the climate parameters (continentalness,
  erosion, weirdness, temperature, humidity, depth) that the vanilla multi-noise biome source
  expects. All biomes are vanilla, so biome-based mods and data packs keep working.
- **Underground:** vanilla caves (cheese, spaghetti and noodle caves, plus carvers) extend into
  the deep zone. Deepslate starts at Y 8 as in vanilla, bedrock lies at Y −512 and lava lakes sit
  at Y −502.
- **Ores at vanilla depths:** vanilla anchors many ores to the world bottom. Terrahaul resolves
  them against the vanilla frame (Y −64..319) so diamonds, redstone and so on stay where players
  expect. It also adds resource layers for the deep zone (diamonds, redstone, gold, iron, lapis,
  geodes, lava springs, glow lichen) and the mountain cores above Y 320 (coal, iron, copper, gold,
  emerald in mountain biomes, springs).
- **Structures:** villages, pillager outposts, mansions and temples are steered to flat, dry ground
  within their normal spacing regions. Strongholds and mineshafts stay within the vanilla depth
  range.

### Vegetation
- **Procedural trees** for every vanilla Overworld species: oak, birch, spruce, pine, fir,
  jungle, acacia, dark oak, mangrove, cherry and azalea, plus swamp oak and dead trees. Each comes
  in several sizes (young, small, medium, large, giant) and has:
  - roots and buttresses, and trunks anchored down slopes;
  - leaf distances computed so leaves never decay;
  - vines, cocoa, bee nests and mangrove propagules.
- Saplings grown by players produce the same trees.
- **Forests have layers and clearings:** tree density follows noise, giving clearings and
  thickets. Trees thin out towards an altitude- and climate-dependent tree line and avoid cliffs.
- **Fallen logs and dead trees** appear in forests.
- *Pale oak is not part of Minecraft 1.21.1* (it was added in 1.21.4), so there is no pale oak
  tree.

### Ground detail and geology
- New decorative blocks with their own models and textures: pebbles (10 stone types), rocks,
  branches (8 woods, mossy variants), twigs, pine cones, leaf litter (9 kinds) and shrubs (4).
- Each block has loot, tags, recipes (pebbles → cobble-type blocks, branches → sticks), compost
  values and fuel values.
- Boulders and boulder fields, mossy rocks, ferns and mushrooms are placed per biome group.

### Client animation (visual only; never affects the world or other players)
- **Wind system:** direction drifts, strength swells, gusts and weather (rain, thunder) add to it,
  and altitude strengthens it.
- **Falling leaves, needles and cherry petals:** custom particles rendered as real, two-sided,
  tumbling quads (not camera-facing sprites). They:
  - rock like pendulums, spin and drift with the wind;
  - land flat on the ground or float on water, then fade;
  - shed from canopy undersides and are tinted by the leaves' biome colour.

  An optional autumn mode adds heavy, coloured leaf fall.
- **Dust** blows across deserts, badlands and savannas in strong wind.
- **Vegetation sway:** grass, ferns, flowers, saplings, crops and bushes bend with the wind
  through a cutout core-shader override. Plants in caves stay still, and the two halves of tall
  plants bend together.
- **Higher clouds** in Terrahaul worlds (Y 448 by default) so clouds no longer cut through hills.

### Finder command
`/terrahaul find <landform> [radius]` locates landforms using the terrain model, so it does not
need to generate chunks. The result is a clickable teleport suggestion. Landforms:

| Group | Landforms |
|---|---|
| Rivers | `river`, `great_river`, `dry_river`, `stream` |
| Lakes | `lake`, `forest_lake`, `mountain_lake`, `great_lake`, `marsh_lake`, `crater_lake`, `plateau_lake`, `glacial_lake` |
| Sea and coast | `ocean`, `deep_ocean`, `trench`, `beach`, `sea_cliff`, `island`, `mushroom_island`, `fjord`, `sea_stack` |
| Mountains | `mountain`, `alpine_peaks`, `ancient_mountains`, `mountain_wall`, `colossal_peak`, `highest_peak`, `volcano` |
| Lowlands and uplands | `meadow`, `plateau`, `valley`, `hills`, `plains`, `windswept_hills`, `wetland` |
| Canyons and formations | `canyon`, `waterfall`, `arch`, `spire`, `hoodoo` |
| Paths | `trail` |

The other commands are:
- `/terrahaul find biome <biome or #tag> [radius]` finds the nearest biome.
- `/terrahaul info` describes the terrain at your position (height, landform, geology, climate).

All of these need permission level 2. Searches run off the server thread.

## Requirements
- Minecraft Java Edition **1.21.1**
- **NeoForge 21.1.x** (built against 21.1.262; any 21.1 release with the same APIs should work)
- Java 21
- Terrahaul must be installed on **both the client and the server**: it adds blocks, particles and
  a world type.

## Installing
1. Install NeoForge for 1.21.1.
2. Put `terrahaul-1.0.0.jar` (a prebuilt copy is in [`dist/`](dist/), or build it yourself, see
   below) into the `mods` folder (client and server).
3. **Singleplayer:** on the Create World screen, *World Type* defaults to **Terrahaul**. You can
   switch back to Default in the World tab; turn off `selectTerrahaulByDefault` in the client
   config to stop the pre-selection.
4. **Dedicated server:** set `level-type=terrahaul\:terrahaul` in `server.properties` **before**
   the world is first created, then start the server. The world type is fixed once the world
   exists.

See [docs/BUILDING.md](docs/BUILDING.md) for building from source and
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for compatibility notes and known limitations.

## Configuration
Configs are generated in `config/`. They can also be edited in-game (Mods → Terrahaul → Config).

**`terrahaul-common.toml` (world generation; affects newly generated chunks only):**

| Key | Default | Meaning |
|---|---|---|
| `replaceVanillaTrees` | `true` | Use Terrahaul's procedural trees instead of vanilla trees (restart) |
| `groundDetails` | `true` | Pebbles, rocks, branches, twigs, litter, bushes, fallen logs, boulders (restart) |
| `geologicalFormations` | `true` | Arches, spires, hoodoos, sea stacks (restart) |
| `oreDepthFix` | `true` | Keep vanilla ores at vanilla depths and add deep/high resource layers (layers need a restart) |
| `terrainAwareStructures` | `true` | Steer surface structures to flat, dry ground (set before creating a world) |
| `saplingsGrowTerrahaulTrees` | `true` | Player-grown saplings produce Terrahaul trees |
| `trails` | `true` | Lay dirt trails across gentle ground |
| `oceanVents` | `true` | Hydrothermal vent chimneys in trenches and on mid-ocean ridges (restart) |
| `treeSizeVariation` | `1.0` | Chance that large/giant tree sizes are kept (0 = uniform small trees) |
| `forestDensity` | `1.0` | Multiplier for tree counts (0.1–3) |
| `vegetationDensity` | `1.0` | Multiplier for bushes and low vegetation (0–3) |
| `groundDetailDensity` | `1.0` | Multiplier for pebbles, branches, twigs, cones, litter (0–3) |
| `rockDensity` | `1.0` | Multiplier for rocks and boulders (0–3) |

**`terrahaul-client.toml` (visuals only):**

| Key | Default | Meaning |
|---|---|---|
| `selectTerrahaulByDefault` | `true` | Pre-select the Terrahaul world type |
| `cloudHeight` | `448` | Cloud layer height in Terrahaul worlds (192–2000) |
| `animations.quality` | `HIGH` | `OFF`, `LOW`, `MEDIUM`, `HIGH`: range and amount of animation |
| `animations.fallingLeaves` | `true` | Falling leaves and needles |
| `animations.fallingLeafDensity` | `1.0` | Multiplier for falling leaves (0–4) |
| `animations.cherryPetals` | `true` | Cherry petals |
| `animations.dust` | `true` | Wind-blown dust in dry biomes |
| `animations.autumnLeafFall` | `false` | Heavy, autumn-coloured leaf fall in oak, birch and dark oak forests |
| `animations.vegetationSway` | `true` | Plants sway in the wind (resource reload/restart to fully apply) |
| `animations.windStrength` | `1.0` | Wind strength multiplier for all animations (0–3) |

## Building from source
```
cd terrahaul
./gradlew build
```
The JAR is written to `build/libs/terrahaul-1.0.0.jar`. Details, run configurations and the data
generators are in [docs/BUILDING.md](docs/BUILDING.md).

## Project layout
```
terrahaul/
  build.gradle, settings.gradle, gradle.properties   ModDevGradle build
  src/main/java/com/terrahaul/
    Terrahaul.java                 mod entry point (common)
    client/                        client entry point, wind, particles, sway, sky effects, colours
    command/                       /terrahaul find and info
    config/                        config spec and data pack condition
    blocks/, registry/             decorative blocks, registries
    mixin/                         generation and compatibility hooks (see COMPATIBILITY.md)
    worldgen/terrain/              terrain model, finder, geology
    worldgen/density/              density functions binding the model to the noise router
    worldgen/surface/, water/      surface rule, rivers/lakes/waterfalls
    worldgen/trees/, features/     procedural trees, ground detail, boulders, formations
    worldgen/placement/, structure/ placement modifiers, terrain-aware structures
    dev/, client/dev/              automated server test and client screenshot harness
  src/main/resources/              assets (models, textures, shaders), data (worldgen, biome modifiers)
  src/devtools/                    offline map renderer and probes (not shipped)
  tools/                           Python generators for worldgen JSON, features and assets
  docs/                            building, compatibility/limitations, testing
```

## License
MIT
