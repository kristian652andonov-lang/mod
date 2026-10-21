# Terrahaul

An Overworld terrain-generation overhaul for **Minecraft Java Edition 1.21.1** on **NeoForge**
(21.1.x).

Terrahaul adds a new world type, **Terrahaul**. It has a much taller world (Y −512 to 2031) and
continental-scale terrain built on a custom density-function model:

- vast plains, plateaus and valleys;
- five styles of mountains, from pointed alpine horns to rare colossal massifs;
- great rivers, lakes, fjords, coastlines, waterfalls and cascades;
- canyons, rock formations, living volcanoes and geological layers;
- coral reefs, sea grottos and ruins along trails and on the sea floor.

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
  - **alpine:** pointed horn peaks with three to five meandering arêtes, cusped cols between
    them and faces that steepen towards the summit, cut by broad glacial troughs;
  - **ancient:** lower and more forested, but still steep with sharp, jagged crests;
  - **walls:** long escarpments with buttresses and couloirs on the face, whose crest climbs to
    pointed summits and drops into notches along its length;
  - **volcanic:** cones with craters, lava flows and ecological zones (see below);
  - **meadow:** gentle high pastures between steeper slopes.

  Nothing is rounded: every steep flank is cut by deep V-shaped gullies between knife-edge spurs,
  ridgelines are sawtooth with notches and small sub-peaks, and the steep slopes (often 45-70
  degrees) climb, level out briefly on small, scattered ledges and shoulders, and climb again.
  Valley floors between the mountains stay flat.

  Most terrain is moderate. **Colossal massifs** are rare: broad pyramids with long arêtes and
  radiating valleys whose summits approach Y 1500–1900.
- **Plains, plateaus, valleys, hills, windswept uplands and wetlands.**
- **Water:**
  - meandering rivers graded smoothly from source to mouth (no stepped water), in valleys that
    fade out instead of ending in walls;
  - **great rivers** 100–200 blocks wide that stay at sea level far inland;
  - dry riverbeds where a river runs into an arid climate;
  - lakes of seven kinds: forest, mountain, great, marsh, crater, plateau and glacial, each
    enclosed by its rim (crater lakes sit inside the crater, not above its flank);
  - **waterfalls**: curtain falls over cliff lips into wide plunge pools in natural bowls, with
    gorges that widen downstream, mossy rock and mist; and **cascades**, chains of small falls
    and pools down forested slopes. Every fall is fed by a stream or a spring pool;
  - coastlines with beaches and sea cliffs (a wave-cut face, a bench and a slope), and fjords
    with U-shaped troughs and hanging valleys.

  All water is placed with containment checks, so there is no floating water and no flooding.
- **Deep oceans:**
  - ocean floors lie around Y −24..30 and abyssal plains around Y −60..−160, with rolling relief,
    mid-ocean ridges and seamounts;
  - **deep-sea trenches** are long rifts with steep, benched walls that drop to about
    Y −230..−340. Their floors are lined with magma, blackstone and basalt and carry
    **hydrothermal vent chimneys**;
  - islands and submerged banks stand on shelves that slope into the sea floor, so no slab of
    sea floor ends in a wall;
  - ocean monuments are lowered onto the sea floor instead of floating.
- **Cliff overhangs:** rock ledges jut out just below the rims of cliffs, canyon walls and sea
  cliffs, and alcoves are carved into cliff faces. Some cliff tops carry **big curved
  overhangs** (a flat top, a quarter-circle underside and a lip) that run along the cliff.
- **No floating terrain:** overhangs are part of the columns beneath them and caves keep a guard
  below the surface. A cleanup pass, run before and again after the features that carve terrain,
  removes any small piece of rock or soil that still touches nothing but air or water.
- **Canyons** with walls stepped into world-wide strata (cliff bands over talus benches) and
  buttes; most are dry, some carry a stream.
- **Rock formations:** natural **arches** with lopsided, sagging, eroded openings; **hoodoo
  fields** of 14–45 beaded pillars, each worn to a jagged point; **rock spires** and **sea stacks**.
- **Material fades:** surface materials change in coherent single-colour patches and bands
  instead of speckle:
  - soil, then weathered rock, then one continuous strata field of level beds, so cliff and
    canyon walls show beds without vertical seams;
  - sand over sandstone in deserts and on beaches; one sediment per lake and per stretch of river;
  - a smooth, wavy deepslate boundary between Y 0 and 8.

  At the surface, grass gives way to rock as slopes steepen, snow patches thicken towards the
  snow line, and sand creeps inland behind beaches. Fades reach at most 15 blocks below the
  nearest exposed surface, measured sideways too, so cliff and mountain faces get them as well.
- **Volcanoes** are rough, lopsided cones: one flank concave and long, the other full, cut by
  V-shaped barrancos with sharp ridges between them, with tongues of blocky lava rock running
  down from notches of the rim and one or two small cinder cones of dark scoria and ash, red and
  brown around their summit craters, on the flanks. They have ecological zones: a bare cone of ash, tuff and scoria in radial streaks;
  lava flows that end in lobes in the forest; a pioneer belt of grass, shrubs and young trees;
  forested flanks where animals spawn; and **burn scars** of scorched soil and ash with burned
  snags, charred logs and regrowth. Their **craters** are irregular: lobed and scalloped rims
  with notches, breaches and higher shoulders, bays where the wall slumped, walls of changing
  steepness with ledges, uneven floors with island cones and sometimes a ridge cutting off a
  second pool, all in dark volcanic rock with oxidised patches. Lava lakes carry plates of
  crust and a shore of magma. Crater lakes and lava pools always lie below the lowest point of
  the rim. Snow lies on the cones in patches broken up by ash and scoria, never in a hot crater.
- **Wetlands** only in temperate and warm climates, with shallow pools between hummocks and mud
  flats.
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

### Underwater
- **Coral reefs** laid out like real fringing reefs: a sandy lagoon with coral heads and
  microatolls, a reef flat with pools and sand channels, a rough reef front with spurs and
  grooves, and a fore reef with bommies, table corals, staghorn thickets, pillar corals and sea
  fan gardens. Each colony is one species and each stretch of reef has two or three, so reefs
  read as patches of colour. Oval patch reefs lie on wide sandy shelves.
- **Sea-floor rocks and boulders**, kelp forests and seagrass meadows that follow depth and
  climate.
- **Sea grottos:** caves at the foot of underwater ridges, some running right through the ridge
  as arches to swim through.

### Ruins and trailside structures
- **Broken houses** beside the trails, every few hundred blocks: a hut, a cottage, a two-room
  house, a farmhouse with a barn or shed, a two-storey house whose upper floor fell in, or a stone
  tower house; built as a timber frame in temperate land, a log cabin in the cold, of stone in the
  uplands, of mud brick or sandstone in hot dry land and raised on stilts in the jungle; with the
  roof fallen in, a wall collapsed into rubble, burned out, down to its foundations and chimney,
  or overgrown. Inside lie a chest with village loot, barrels, a bed or its rotten frame, a hearth, cobwebs;
  outside the remains of a fence, a well, a garden patch.
- **Other trailside ruins**, each in a few variants: collapsed watchtowers, abandoned wells, broken
  wagons and carts with their spilled load, wayside shrines, old campsites, field walls with a
  ruined gateway, the abutments of lost bridges where a trail fords a stream, ruined chapels.
- **Signposts** at most trail junctions and ends, and direction posts and milestones along long
  trails, whose signs name real destinations with distances (lakes, volcanoes, waterfalls,
  formations, ruins).
- **Broken path lights:** long lit sections of trail with lamp posts every 20-odd blocks, in five
  regional styles, many bent, fallen or without their lantern.
- **Sunken ruins** on the sea floor: a colonnaded temple, a city gate, a drowned street quarter and
  a tower stump, partly buried and overgrown, with chests and archaeology sites.

### Vegetation
- **Procedural trees** for every vanilla Overworld species: oak, birch, spruce, pine, fir,
  jungle, acacia, dark oak, mangrove, cherry and azalea, plus swamp oak, dead trees and burned
  snags. Each comes in several sizes (young, small, medium, large, giant, ancient). Crowns are
  grown as real branch systems that reach every leaf cluster, with tapered limbs:
  - oaks, including bonsai-like oaks with cloud pads; weeping swamp oaks on flared bases with
    vines; birches with short tufted branches;
  - spruces and firs as dense, layered cones of whorled branches, each with its own drooping
    spray of needles, hanging curtains on old spruces and dead branches on their bare lower
    stems; Scots pines with rounded needle cushions on upswept, forking limbs, from medium size
    on under a flat-topped crown (an umbrella on old trees) on a tall, bare, slightly crooked
    stem. Old-growth conifers grow 30 to 60 blocks tall, rare ancient ones up to 90; their
    stands hold fewer, bigger trees, so the forest floor stays light, and a conifer's stem may
    grow up through the branches of its neighbours;
  - jungle umbrellas on buttress roots, acacia umbrellas, heavy dark oak domes, mangroves on prop
    and aerial roots, and cherries with a blossom dome over thick gnarled limbs;
  - lobed root flares, and trunks anchored down slopes;
  - every piece of wood is connected to the trunk, and leaf distances are computed so leaves
    never decay;
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
- **Waterfalls, cascades and rapids** (any falling water, vanilla's and player-made too):
  - fine spray drifts off the curtain's open sides and drops fly from the lip;
  - the water splashes up white where it lands, on every step of a cascade and higher for taller
    falls;
  - soft mist rolls off the foot and drifts with the wind; falls 14 blocks and taller raise a
    large, slowly billowing cloud that is still seen from across a valley;
  - the pool churns white at the foot of every fall and step, foam floats away on the pool and
    down the stream, and the water running into and out of a drop carries flecks of foam;
  - tall falls roar and cascades and rapids rush, from the nearest part of the water, louder for
    bigger falls and about as loud as Minecraft's own ambient sounds.

  Each effect has its own option (`[water]` in the client config); the particle count follows the
  animation quality and Minecraft's Particles setting.
- **Vegetation sway:** grass, ferns, flowers, saplings, crops and bushes bend with the wind
  through a cutout core-shader override. Plants in caves stay still, and the two halves of tall
  plants bend together.
- **Higher clouds** in Terrahaul worlds (Y 448 by default) so clouds no longer cut through hills.

### Finder command
`/terrahaul find <landform> [radius]` locates landforms using the terrain model, so it does not
need to generate chunks. The result is a clickable teleport suggestion; for ruins, signposts and
path lights it is a safe spot to stand beside them (on the trail in front of a ruin, never inside
a wall or down a well). Landforms:

| Group | Landforms |
|---|---|
| Rivers | `river`, `great_river`, `dry_river`, `stream` |
| Lakes | `lake`, `forest_lake`, `mountain_lake`, `great_lake`, `marsh_lake`, `crater_lake`, `plateau_lake`, `glacial_lake` |
| Sea and coast | `ocean`, `deep_ocean`, `trench`, `beach`, `sea_cliff`, `island`, `mushroom_island`, `fjord`, `sea_stack` |
| Mountains | `mountain`, `alpine_peaks`, `ancient_mountains`, `mountain_wall`, `colossal_peak`, `highest_peak`, `volcano` |
| Lowlands and uplands | `meadow`, `plateau`, `valley`, `hills`, `plains`, `windswept_hills`, `wetland` |
| Canyons and formations | `canyon`, `waterfall`, `cascade`, `overhang`, `arch`, `spire`, `hoodoo` |
| Underwater | `coral_reef`, `patch_reef`, `kelp_forest`, `seagrass_meadow`, `rocky_seabed`, `sea_grotto`, `sunken_ruin` |
| Paths and ruins | `trail`, `path_ruin`, `broken_house` (also `ruined_house`, `trail_ruin`), `watchtower`, `abandoned_well`, `wagon`, `shrine`, `campsite`, `field_walls`, `old_bridge`, `ruined_chapel`, `signpost`, `path_lights` |

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
| `trails` | `true` | Lay dirt trails across gentle ground, with trail ruins, signposts and path lights |
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
    worldgen/surface/, water/      surface rule, rivers/lakes, waterfalls and cascades
    worldgen/trees/, features/     procedural trees, ground detail, boulders, formations
    worldgen/features/marine/      coral reefs, sea-floor rocks, kelp and seagrass, grottos
    worldgen/ruins/                trail ruins, signposts, path lights, sunken ruins
    worldgen/placement/, structure/ placement modifiers, terrain-aware structures
    dev/, client/dev/              automated server test and client screenshot harness
  src/main/resources/              assets (models, textures, shaders), data (worldgen, biome modifiers)
  src/devtools/                    offline map renderer and probes (not shipped)
  tools/                           Python generators for worldgen JSON, features and assets
  docs/                            building, compatibility/limitations, testing
```

## License
MIT
