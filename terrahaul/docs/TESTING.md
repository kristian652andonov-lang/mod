# Testing

This page records what was actually tested, how, and with what result. It also lists what was
**not** tested. Everything here was run in the development environment: a Linux container with
4 vCPUs, no GPU, Java 21, NeoForge 21.1.262. The tools are described in
[BUILDING.md](BUILDING.md); the devtool studies and audits list their modes in the Javadoc of
their classes.

## Build
- `./gradlew build` produces `build/libs/terrahaul-1.0.0.jar`. The main and devtools source sets
  compile without errors; the only compiler notes are deprecation notices in the development-only
  screenshot harness.
- The data generators (`tools/*.py`) were re-run and produced identical files apart from intended
  changes, which shows they are deterministic.
- **The built JAR in a standalone server:** NeoForge 21.1.262 was installed with the official
  installer (`--installServer`), `terrahaul-1.0.0.jar` was put into `mods/`, and
  `level-type=terrahaul\:terrahaul` was set. The server loaded the mod and its mixins, created a
  Terrahaul world (dimension type `terrahaul:overworld`), generated the spawn area in 17 s, ran
  for four minutes without an exception and stopped cleanly.

## Sandstone on beaches, no lava lakes at the surface (branch `wf/beach-lava`, seed 12345, dedicated server and client)
Two user requests: stones on beaches should be sandstone instead of stone and other rock from
inland, and lava lakes should not lie on the surface (in a forest, say) outside volcanoes.
- **New checks:** `AutoTest` counts Terrahaul's pebbles and knee-high rocks by material and by the
  block under them (sand, dry, under 1-5 blocks of water or deeper; red sand; other ground), the
  columns where rock stands on loose sand above water and under 1-5 blocks of water
  (`StonesOnSand`), and the lava within 48 blocks under the ground that is open to the sky or lit
  by daylight, inside and outside the model's volcanoes (`SurfaceLava`). The area specs
  `find:<target>@x/z,r` and `biome:<id>@x/z,r` take a search origin.
- **Method:** the same areas were generated from scratch (`-PnoStructures`) by a frozen copy of
  bf77c83 with only these checks added (before) and by this branch (after), in two sets, since one
  server with all 41 areas ran out of its 6 GB heap after 28. Set A (21 areas, 1,717 chunks): four
  sandy beaches (`find:beach` from 0/0, 3000/3000, -3000/2000 and 2000/-3000), a snowy beach, a
  stony shore, the sea stack nearest the origin (off the snowy beach at 4714, -2870), a dormant
  and an active volcano (`find:volcano`, `find:volcano@23000/10500`, crater lava at Y 429) and
  twelve inland areas (plains, forests, taiga, savanna, birch and dark forest). Set B (20 areas,
  1,620 chunks): four more `find:beach` searches (one found set A's beach at -2324, 2528 again),
  jungle, desert, badlands, swamp, meadow, windswept hills, old growth spruce taiga, sunflower
  plains, flower forest, snowy plains, cherry grove and savanna plateau, and the `hills`,
  `plateau`, `valley` and `mountain` landforms. Most beach sand of this seed lies in stony shore
  and forest biomes rather than in `beach`, which is why the rule tests the block under a stone.
- **Stones on dry sand:** before, 123 (set A) and 88 of 185 (set B) were stone, andesite, granite,
  diorite, tuff or mossy rock; the rest of set B's were the desert's and badlands' own sandstone.
  After, all 122 and 185 are sandstone (118 and 170 pebbles, 4 and 8 rocks), except 7 red
  sandstone pebbles in the badlands, which keep their colour. In the surf on sand (1-5 blocks of
  water) the 58 and 55 pebbles and rocks, grey before (44 of set B's 55), are all sandstone after.
  Under deeper water the 336 and 202 keep the rock of their province, as intended (5 more in each
  set are sandstone: river and beach underwater pebbles, which follow the rule at any depth). Pebbles on
  gravel, stone and soil (3,704 and 2,562 before, 3,711 and 2,571 after) are unchanged in kind, so
  stony shores, the rocky strip under coast cliffs and the volcanoes keep their stone.
- **Boulders and ledges in the surf:** columns of rock standing on sand under 1-5 blocks of water
  held no sandstone in set A before (1,994: mossy cobblestone, the algae coat, 940; andesite 872;
  stone 91; calcite 70) and 229 after (sandstone 142, smooth sandstone 87): the sea-floor boulders
  off sandy beaches. In set B sandstone went from 247 to 379 of about 1,470. The other columns
  are bedrock ledges: an offline count with the marine planner found 386, 589 and 264 ledge
  columns under at most 5 blocks of water in the three beach areas with the most, every one with
  a sandy shore by the boulders' test. Ledges are not changed (see COMPATIBILITY.md).
- **Rock standing on dry sand** (1,011 before and 1,008 after in set A, 1,249 in both in set B) is
  the surface rule's rocky strip at the foot of coast cliffs (878 columns of stone in the snowy
  beach around the sea stack), desert sandstone outcrops and badlands terracotta: terrain, kept.
- **Sea stack:** the one off the snowy beach is sandstone: in its area the andesite and tuff in
  the 15 blocks under the top fell by 562 and 578 blocks and sandstone and smooth sandstone rose
  by 640 and 638. Offline, on world seeds 12345, 42 and 1, 6 of 30 sea stacks within 11,500 blocks
  of the origin have a stronger beach than coast cliff within 48 blocks (the test the feature
  uses); those are the ones that turn to sandstone.
- **Lava at the surface outside volcanoes:** before, 19 lava blocks lay open to the sky and 242
  were lit by daylight in set A, in three of the twelve inland areas: a surface lava lake at Y 328
  on a taiga mountain (3124, -3046), lava under the canopy of a savanna forest (-3941, 243, 4824)
  and an underground lake opening onto a slope in plains (-3414, 103, 1015, five blocks under the
  ground, sky light 6); in set B 5 open and 22 lit in the cherry grove (8044, 131, -8032), and 47
  lit in the valley 470 blocks from the dormant volcano, whose wide outer zone counts as volcano
  but holds no crater lava. After: 0 open and 0 lit, outside volcanoes and in that outer zone, in
  all 41 areas. The forest's `lakes` step holds `terrahaul:lava_lakes/underground` and
  `terrahaul:surface_water`, where it held vanilla's two lava lakes before.
- **Option on:** with `surfaceLavaLakes = true` the forest's `lakes` step lists
  `minecraft:lake_lava_underground` and `minecraft:lake_lava_surface` again, and the taiga, plains
  and savanna areas above have the same lava as before, block for block in the counts (19 open,
  242 lit, the same example positions).
- **Unchanged:** the active volcano's 45,734 lava blocks open to the sky, and its area's top-down
  map and vertical section are identical before and after; the dormant volcano's map differs in 4
  pixels at the edge of the area. The top blocks and the 15 blocks under them are the same in
  both volcano areas. Walls of water: 0 in every area, before and after. Floating terrain: the
  same in every area but one (4 single blocks before, 3 after, in the valley). Unsupported blocks
  near the surface: 46 and 46 in set A, 69 and 70 in set B; the deep ones differ by a few soul
  fires and candles on sculk in an ancient city at Y -42 under the dormant volcano. Pebbles in the
  volcano areas differ by a handful (435 or 439 on other ground) from run to run of the same code:
  the first baseline run of the previous session gave the 435 this branch gives.
- **Screenshots** (client, software rendering) of the same camera positions in the saved worlds of
  set A, before with the old code and after with the new: on the beach under a stone slope at
  597, 200, a dark basalt shore and the snowy beach, the pebbles lying on the sand turned from grey
  and pink to sandstone, while those on the rock beside them stayed grey and pink; the sea stack
  off the snowy beach is sandstone; the lava pool on the taiga mountain at 3124, -3046 (seen from
  above and from the north-west) is gone. The grey ledges in the shallow water off the first beach
  are there in both. Trees in the background can differ between two shots for a reason that is not
  this change: two runs of the same baseline code already differ in their tree audit lines (tree
  placement depends on the order in which chunks are decorated).
- **Not checked:** red sand beaches (none in the areas; red sand appears in the badlands, where 4
  red sandstone pebbles lay on it); sea stacks off a beach in hot country (the stack in the areas
  is a cold one); sounds (none involved); the sea stack rule on seeds other than the three above;
  the lava check below 48 blocks under the ground (deep lava is not affected).

## Magma chambers and lava smoke (branch `wf/volcano-lava`, seed 12345, dedicated server and client)
The user asked to leave the volcanoes as they are, but to let the crater lava go down into a large
reservoir inside the volcano, and for smoke from the lava.
- **Offline (`chamberStudy`, world seed 12345):** the six volcanoes nearest the origin (all crater
  lakes, so plugged conduits) and the five nearest with lava in the crater (all five with an open
  conduit into the crater's lake once the vent test asked only that the throat lie in the lake;
  with the first, stricter test of open lava 1.5 blocks deep around the whole conduit, two of
  three had stayed plugged). For every cell of each cavity: lava beside or above the cavern's air
  0, falling lava not landing in lava 0, the lake, the fall and the conduit one body of lava (a few
  small pools in hollows of the shore stand apart), at least 28 blocks of rock over the cavity
  wherever the crater's lava does not lie over it, and the vent's columns all under the crater's
  lava: 0 problems in all eleven. Planning takes 4–118 ms per volcano, once.
- **AutoTest areas** (`-PnoStructures`): the chamber of the large lava crater at 23250 10511
  (r=8, 289 chunks; crater radius 234, chamber radius 89, conduit 179 blocks long), of a small
  lava crater at -22923 15816 (r=5) and of a crater-lake volcano at 6590 -5955 (r=7, plugged
  conduit). A baseline run of the same areas used the same build with the feature switched off
  (`-PdebugProps=terrahaul.debug.noMagmaChamber`).
- **New check** (`ChamberCheck`, in every area that a chamber reaches): the plan's cavity against
  the generated blocks, faces between the cavity and an open cell outside it (breaches), open
  cells inside the wall under the ground, and lava outside the cavity below the lowest crater
  floor in the chamber's box and sixteen blocks around it.
- **Results** (final run): cavity cells as planned in all three (lava 167,102 / 20,659 / 50,147,
  falling lava 4,068 / 1,560 / 3,795; the air cells that differ hold glow lichen and sculk veins
  grown on the chamber's walls afterwards). **Breaches 0, open cells in the wall 0** in all three.
  Lava below the crater floor outside the cavity: 124 / 373 / 436 blocks, **the same counts as in
  the baseline** (vanilla lava pools and springs in the caves of the cones), none of it in the
  wall; 4 of them in the small volcano lie 4–5 blocks from the cavity, just outside the wall, and
  are there in the baseline too. Caves meet the outside of the walls at 1,700–5,300 air cells.
- **Unchanged against the baseline:** floating terrain fragments (22 / 66 / 0 with chambers,
  22 / 85 / 0 without: the small crater's conduit took away some of the existing fragments on its
  lake bed), walls of water and lava (0), water fins (152 / 16 / 23, crust plates on the lava
  lakes). The 22 fragments of the large crater are pieces of its lake bed and crust over cave air,
  there in the baseline as well; they were not traced further.
- **Found and fixed:** the first run left 236 / 24 / 361 blocks that cannot stay (baseline 2 / 3 /
  4): glow lichen, vines and sculk veins (the crater-lake volcano's chamber lies in a deep dark
  biome) hanging in the chambers' air, all on chunk-border columns. A neighbouring chunk decorated
  earlier had grown them on rock that the later chunk then hollowed out. The feature now drops the
  face or the plant across the border, as a block update would (930 in the final run); the counts
  are back to 2 / 3 / 5 (the extra one is a carpet on a sculk vein in the ancient city 230 blocks
  below the crater-lake chamber, also in the run before; not traced).
- **Cost:** 3.1–3.4 ms per chunk that builds a chamber or conduit (255 chunks, 6,500 blocks
  placed per chunk); 0.005 ms per chunk elsewhere. Whole-area generation was 194 / 125 / 194
  ms/chunk in one run with chambers against 194 / 141 / 200 in the baseline, and 230 / 137 / 211
  in another run with chambers: run-to-run differences are larger than the feature's cost.
- **Screenshots** (client, software rendering, render distance 10, noon, night vision taken off
  with a new `cmd=` entry of the screenshot harness): inside the large chamber from its shore
  (the viewpoint chosen with `chamberStudy`) and from the dome (the lava lake, the fall from the
  conduit, pillars standing in the lava, the dome's ribs and magma veins), inside the small
  chamber beside its fall, both crater lakes with their smoke and the column, and a lava flow
  started with `setblock` on the small volcano's outer flank, smoke blowing off it with the wind.
  The first smoke was too sparse (dots); puffs and the column were made larger, denser and
  taller, young puffs fade in as they leave the lava, flowing lava keeps a share of each chunk's
  sample, and no smoke is spawned while the camera is deep underground (the chamber shots then
  showed 32–70 particles, against 980–1,500 at the craters). Flows started on the large crater's
  inner wall, beside its lake, showed little smoke of their own in the shot. Two camera positions
  failed and were dropped: one inside the large crater's cloud layer (its lake lies at Y 429, the
  clouds at 448) and one inside the crater wall.
- **Not tested:** Sodium, Iris and shader packs with the smoke (only read: the smoke uses the
  mist's render type and Sodium's particle fast path, see COMPATIBILITY.md); a dedicated server
  with a remote client; dry craters (only crater lakes and lava craters were among the volcanoes
  checked); the smoke's frame time on a real GPU; whether a dungeon or other feature of a chunk
  decorated after a chamber's chunk ever opens the wall (none did in these areas); lone lava blocks
  (thin wisps) in a screenshot.

## Floating plants (branch `wf/floating-plants`, seed 12345, dedicated server and client)
A user's screenshot showed pink petals and grass hanging over a cherry grove stream and in front
of its dirt banks.
- **New check:** `AutoTest` counts, in every area and at every height, the blocks whose
  `canSurvive` is false, and lower halves of double plants without their upper half, by type, with
  the block under them and a few positions (mushrooms and crops that fail only on light are
  counted apart). New area spec `water:<biome id>,r`: the nearest stream or river in a biome.
- **Areas** (r=4 each): a cherry grove stream (`water:minecraft:cherry_grove`), a cherry grove,
  `find:stream`, `river`, `great_river`, `lake`, `waterfall`, `cascade`, a forest and
  `find:mountain`.
- **Before** (c82f7e9): 1,156 such blocks, 1,091 of them plants. About two thirds stood on tree
  roots and trunk feet (bark blocks laid into the soil under grass and ferns that were already
  there); about 140 over air or water beside streams, cascades and pools (pink petals over the
  cherry grove stream among them); the rest on boulder rock, tall seagrass on underwater pebbles
  that had replaced its foot, and lone halves of tall grass, sunflowers and large ferns.
- **Causes:** vanilla's flower, grass and petal patches spread up to seven blocks into
  neighbouring chunks, which may not have dug their streams and pools yet; the surface water then
  carved, flooded or rebuilt the ground under them. Trees, boulders and fallen logs replaced the
  ground (or one half of a double plant) under plants placed earlier, in their own chunk and in
  finished neighbours. The underwater pebble patch accepted any block holding water, seagrass
  included. Each now clears what it undermines; the pebbles go only into water blocks; and a last
  feature (`terrahaul:plant_cleanup`) removes any plant of the chunk that still cannot stay.
- **After:** 89 blocks, 22 plants. Not plants, left alone on purpose: 65 dirt paths under leaves
  or roots (they would turn to dirt, not drop), an ancient city candle and a pointed dripstone.
  Plants: 12 snow layers on packed ice in an ice spikes biome and a grass tuft and a fern on sand
  beside a river (a later neighbour's vanilla ice spike and, most likely, a vanilla sand disk
  changed the ground after the chunk had finished), and 8 glow lichen deep in caves whose wall
  went after their chunk had finished (not traced further). With the last feature switched off
  the source fixes alone leave 110 blocks, 44 plants; the feature removed 26 plants in 1,389
  chunks.
- **Unchanged:** floating terrain fragments, walls of water (0), water fins and floating tree
  groups are the same in all ten areas. The stone block standing in the stream in the user's
  screenshot has its match at the cherry grove stream: a stone and cobblestone column on the bed,
  not floating.
- **Cost:** the last feature takes 0.30–0.41 ms per chunk; in a JFR profile all the new code is
  under 0.3% of the samples. Wall-clock ms/chunk varied a lot between JVM runs (1,209–1,426 summed
  over the ten areas) and was higher in the runs with the change; the extra samples were in
  vanilla noise filling and surface building, which the change does not touch, and with the C1
  compiler only (no C2 inlining variance) the four-area sum was 1,175 before and 1,202 after
  (+2%, within run-to-run noise).
- **Screenshots** (client, software rendering) of the cherry grove stream bank at the same three
  camera positions before and after: the petals over the water and the grass hanging in front of
  the banks are gone.

## Fourth round: pointed mountains and mountain vegetation (merged build, seed 12345)
This round merged the pointed mountains (`wf/pointed`: cusped crests and summits, crags on the
big faces, no domed tops), the mountain vegetation (`wf/mountain-flora`, see below) and the
underground structures kept below deep sea floors (see below). Both branches were tested on
their own before the merge; the merged build was tested once more in game, in a fresh world on
the dedicated server (`AutoTest`) and with the client (35 screenshots, Mesa llvmpipe, render
distance 10).
- **Build and data:** `compileJava compileDevtoolsJava` and `./gradlew build` pass; the three
  data generators (`datagen_worldgen.py`, `datagen_features.py`, `datagen_assets.py`) were re-run
  on the merged tree and changed nothing.
- **AutoTest areas:** the 17 areas of the previous release test, the trial chambers at 64 ~ 2864
  and 768 ~ 2368 and the stronghold at 4688 ~ 0 (all under deep ocean), and four random mountain
  sites of `domeSurvey` (sites 8, 16, 20 and 37 of world 12345, radius 4 around the summit).
  - Water walls: 0 in all 24 areas. Water fins (single water blocks at the surface): 8 and 16
    at the crater lake and volcano (5 in the previous release test), 1 at the great river.
  - Floating terrain: none that is real. The lake areas (0 and 2) reported 25 flat patches of
    clay, moss and gravel; the saved world shows them lying on two blocks of mud over sand.
    `AutoTest` did not count mud as ground (its collision box is lower than a full block); it
    does now. The other 6 hits are single blocks held by tree roots or trunks (dirt and rooted
    dirt under an oak root flare, stone and smooth basalt wrapped in spruce wood).
  - Trees: 0 floating trees and 0 decaying leaves, apart from two single spruce-wood blocks that
    touch their tree only at an edge (a root stub at -959 105 -622 and a branch inside a crown at
    -1204 215 435).
  - Underground structures, after the merge: both deep-sea trial chambers lie 13 blocks under
    the sea floor (cover by real blocks at least 13, no column under 12, nothing exposed), no
    water under them, 17 and 16 trial spawners and 18 and 15 vaults, all with their block
    entities; the stronghold also lies 13 under the floor. The held starts equal starts
    generated afresh. Every vanilla structure near spawn was located and its start was present.
  - Mountain sites (vegetation census of the flanks; site 16, a savanna range, has no flank
    columns in it): 45-85% of the flank columns more than 50 blocks below the tree line are
    under trees and 81-90% have soil; at site 37 the forest
    thins to 37% just below the line and 8% just above it, with 31% snow there. 1281 trunks
    stood on the flanks of the four sites; 18 of them (1.4%) read a rise of 6-7 blocks over 3
    around the foot, one stood in a snowfield. The model/height mismatch count is high on these
    sites (227-810 of 1296) because the forest canopy is part of the heightmap it compares.
  - No exception in the server log. A first run with the server heap lowered to 4.5 GB ran out
    of memory while generating the areas; with the default 6 GB it ran through.
- **Client screenshots** (in `run/screenshots/` of the test worktree; looked at by eye):
  - Pointed and forested: from the valley under the `find:mountain` range a sharp rock peak
    rises over spruce forest; `find:mountain_wall` is a sharp snowy peak; `domeSurvey` sites 16
    and 20 show pointed rock peaks with forest and acacias on their lower flanks; the cold grove
    shows pointed snowy peaks with snowy spruces climbing the flank; on the south flank near
    spawn the forest thins upwards towards snowy peaks.
  - Still rounded: seen from 150 blocks away and below, the summit that `/terrahaul find
    mountain` leads to (-352 460 -32) reads as a broad knob with regular snow contour stripes.
  - Deep ocean: open water where the trial chambers used to hang at 64 ~ 2864 and 768 ~ 2368.
  - Blocked views: the camera of `domeSurvey` site 8 and the fixed waterfall-pool camera are
    inside tree crowns now that forest grows there; the waterfall shots frame the stream but not
    a fall (the client counted falls up to 48 blocks tall in view).
- **Standalone server:** the built JAR in a fresh NeoForge 21.1.262 server world (seed 12345,
  `level-type=terrahaul:terrahaul`): ready in 20 s, `/terrahaul find mountain` answered in 47 ms
  (-352 460 -32), `/locate structure minecraft:trial_chambers` in 519 ms, clean stop, no error
  or exception in the log.
- **Not tested in this round:** other seeds in game; `wallAudit`, `fragmentAudit` and
  `finderAudit` on the merged tree (the merge does not change the terrain model's heights
  beyond the pointed branch, which ran them); a second in-game round after the `AutoTest` fix.

## Underground structures kept below deep sea floors (seed 12345, dedicated server and client)
Reported in game: trial chambers hung in the middle of deep oceans as huge blobs of stone with the
chambers inside. Vanilla starts them at a fixed height (Y −40 to −20; their corridors climb to
about Y 30) and encapsulates them in rock, but Terrahaul's deep-sea floor lies at Y −60 to −160.
The fix (`UndergroundStructures`, `StructureMixin`) moves every structure in the new tag
`terrahaul:keep_underground` (trial chambers, ancient cities, strongholds, mineshafts) down until
each piece lies at least 12 blocks under the terrain over and around it. It was checked with
`AutoTest` (new: `structure:` areas, `-Psurvey`, the underground report of
`UndergroundStructureCheck`; see BUILDING.md) on 98b91c0 (before) and with the fix (after), each
in a fresh world.
- **Survey** (structure starts generated as chunk generation does, measured against the
  generator's own height without structures; no chunks generated):

  | Structure | Starts | Before: top above the floor (all pieces in open water) | After | After: cover, min / median |
  |---|---|---|---|---|
  | Trial chambers (80 placement chunks nearest 768, 816) | 66 | 41 (41), up to 213 blocks above | 0 | 12 / 14 |
  | Strongholds (within 6000 blocks of 0, 0) | 9 | 2 (2), 193 and 134 blocks above | 0 | 13 / 56 |
  | Mineshafts (60 nearest 768, 816) | 60 | 47 (46) | 0 | 12 / 15 |
  | Ancient cities (within 4000 blocks of 768, 816) | 1 | 0, 134 blocks under land | 0 | unchanged |

  Starts that were already deep enough kept their vanilla height: the stronghold, mineshaft,
  ancient city and trial chambers that `AutoTest` locates near spawn (all under land) have the
  same boxes before and after. A second run on the saved world gave the same survey.
- **Generated blocks** at the located structures (`structure:` areas, 7x7 to 9x9 chunks plus the
  whole footprint):

  | Structure | Before | After |
  |---|---|---|
  | Trial chambers at 64 ~ 2864 (deep cold ocean, floor Y −162..−156) | Y −34..19, 177 blocks above the floor; water under the pieces in 3829 of 4320 columns; the only rock over it is its own shell | moved 190 down to Y −224..−171; 13 blocks of rock over it, no water under it; 17 trial spawners and 18 vaults, all with their block entities |
  | Trial chambers at 768 ~ 2368 (deep frozen ocean, floor Y −159..−150) | Y −43..26, 184 blocks above the floor; water under 3271 of 3785 columns | moved 197 down to Y −240..−171; 13 blocks of rock over it, no water under it; 16 trial spawners, 15 vaults |
  | Stronghold at 4688 ~ 0 (deep frozen ocean, floor Y −158..−142) | Y 15..39, 193 blocks above the floor; 24% of the three blocks around its pieces is sea water | moved 206 down to Y −191..−167; 13 blocks of rock over it |

  Around all three, before and after: 0 floating terrain fragments, 0 water walls, 0 water fins.
  After the move each trial chamber's pieces hold 17 water blocks, the templates' own (19 in the
  one near spawn); before, those at 768 ~ 2368 held 102, sea water come in through holes in the
  blob.
- **Caves:** the three blocks around the pieces are open (air, water or lava) for 0.1-0.4% of the
  moved trial chambers and 1.9% of the moved stronghold, against 1.3% and 2.5% for the trial
  chambers and stronghold that lie at their vanilla depth under land near spawn, so Terrahaul's
  deep caves do not cut them open more. Under the sea those caves can be flooded: 1.6% of the
  stronghold's surroundings is water, and 465 of its 4778 columns have water within 16 blocks
  below the pieces (under land: none). The x-ray screenshot shows the deep caves around it.
- **`/locate` and the start:** every structure in `AutoTest`'s list (villages, outpost, pyramid,
  swamp hut, mansion, stronghold, mineshaft, ancient city, trial chambers, monument) and the
  three deep-sea ones were located at the same positions before and after, and each start was
  present in its chunk.
- **Save and load:** the trial chambers at 768 ~ 2368 were started in a first server run that
  generated only the 3x3 chunks around the start chunk. After a restart the rest of it was
  generated from the start loaded from disk: its 220 piece boxes and junction heights equal those
  of the same start generated afresh, and the finished structure lies 13 blocks under the floor
  with no water in or under it, so the parts built before and after the restart line up.
- **Cost:** the survey's generation times include the move. Over the deep sea, where every column
  of the footprint is probed, a trial chamber took about 0.2 s longer (16.2 s before, 31.2 s and
  29.8 s after for the 66 starts) and a mineshaft about 0.47 s (0.05 s before, 28.1 s after for
  60), on a machine busy with two other Minecraft instances (load 10-15 on 4 cores). With one
  mineshaft per ~250 chunks, that is about 2 ms per chunk over the deep sea, where a chunk took
  140-250 ms. Columns whose terrain-model surface lies far above what the structure needs are not
  probed: over 135,469 sampled columns the model surface lay more than 4 / 8 / 16 / 24 / 32 blocks
  above the generator's height in 916 / 441 / 159 / 35 / 2 of them (at most 37.6), so the 32-block
  margin used costs at most a few blocks of cover in very rare columns.
- **Screenshots** (client, Mesa llvmpipe, render distance 10, night vision, from the same cameras
  in the before and after worlds): from the water beside where the chambers at 768 ~ 2368 hung,
  a blob of stone with holes before and open water after; from below the chambers at 64 ~ 2864,
  the blob's underside before and open water up to the sea ice after; the sea floor over the
  buried chambers is unchanged. Spectator x-ray views from inside the rock show the buried trial
  chambers and the stronghold (with the flooded caves beside it), and a camera inside the buried
  chambers shows a vault in its alcove at Y −217.
- **Not checked:** badlands mineshafts (not in the tag: vanilla places them to break out of the
  badlands surface); other seeds; trial chambers over the deepest trench floors, where a tall
  one cannot get its full cover above the 16-block margin at the world bottom (none of the
  surveyed structures came near it); `/place structure` (it goes through the same code).
## Mountain vegetation (branch `wf/mountain-flora`)
Forest, a ragged tree line, krummholz and alpine meadows on the mountain flanks, with soil under
them (see "Vegetation climbs the mountains" in the README). Tested in game on seed 12345, before
(at 98b91c0) and after, in the same areas and from the same camera spots.
- **AutoTest areas** `find:mountain,3;find:alpine_peaks,3;find:mountain_wall,2;
  find:ancient_mountains,2;biome:minecraft:meadow,2;biome:minecraft:grove,2` (both finder
  searches lead to the same massif). The report has a new census of the vegetated flanks (the
  columns where the zone function gives a flank share above 0.5), by distance from the tree
  line: the share of columns under trees, with a plant on the ground, and with soil, snow or rock
  as their ground. It also checks every tree trunk on the flanks for the steepest rise of the
  ground over three blocks around its foot and for snow under or around it. The "before" run is
  98b91c0 with only the census and the zone function copied in, so both report the same bands.

  | Area | Band | Trees before / after | Soil before / after | Rock before / after |
  |---|---|---|---|---|
  | `find:mountain` = `find:alpine_peaks` (ice spikes) | 50-150 above | 0.0% / 0.0% | 0.0% / 2.2% | 35.4% / 33.2% |
  | `find:mountain_wall` (snowy slopes) | 0-50 below | 0.0% / 12.5% | 8.2% / 82.7% | 32.1% / 15.3% |
  | `find:mountain_wall` | 0-50 above | 0.0% / 0.0% | 0.3% / 12.7% | 30.7% / 24.9% |
  | `find:ancient_mountains` (grove) | 0-50 above | 0.0% / 6.4% | 16.4% / 96.3% | 54.4% / 3.7% |
  | `find:ancient_mountains` | 50-150 above | 0.0% / 0.4% | 8.6% / 86.3% | 79.2% / 13.7% |
  | meadow valley | >100 below | 0.0% / 9.2% | 0.7% / 89.9% | 99.3% / 10.1% |
  | meadow valley | 50-100 below | 0.0% / 16.5% | 9.8% / 69.9% | 88.9% / 30.1% |
  | meadow valley | 0-50 below | 0.0% / 28.3% | 36.0% / 95.7% | 47.9% / 4.3% |
  | grove | 0-50 below (180 columns) | 16.7% / 8.3% | 29.4% / 100% | 0.0% / 0.0% |
  | grove | 0-50 above | 4.9% / 3.6% | 20.1% / 63.8% | 25.2% / 0.7% |
  | grove | 50-150 above | 1.3% / 0.6% | 28.0% / 74.0% | 29.3% / 2.9% |

  The rest of the ground in each band is snow. In the grove the old snowy forest climbed above
  the line on snow (two trunks stood in snowfields); it now thins out above the line into small
  trees and krummholz on soil and meadow. The census counts the edge chunks of each area, whose features depend on the
  order in which the server generated their neighbours, so two runs of the same code differ by a
  few tenths of a percent (seen before and after alike); the floating terrain, water wall and tree
  checks did not change between runs.

  - The `find:mountain` massif (an ice-spikes massif at 390-620 blocks, seed 12345) stays bare
    by design: it stands in a polar, dry climate, and all of its flanks lie above its tree line
    (0 trees before and after, 2% soil).
  - **Trunks on the flanks:** 57 after (9 before). None stands on snow or in a snowfield
    (before: 2 in the grove area stood in snowfields). In the finished world 3 of them read a
    rise of 6-7 blocks over three blocks around the foot, 63-67 degrees (before: 1 of 9, rise
    6). The placement filters allow at most 5 against the generation heightmap; why these few
    read steeper afterwards (likely boulders, fallen logs or root flares changing the ground
    next to the foot) was not traced block by block.
- **Chunk generation time** (AutoTest, "ms/chunk incl. neighbours", two runs each, interleaved
  base, new, base, new so both saw the same machine load; the machine was shared with other
  builds, load average 2-11 on 4 vCPUs), for the find:mountain, mountain_wall,
  ancient_mountains, meadow and grove areas:

  | Run | Base (ms/chunk) | New (ms/chunk) | Sum base / new |
  |---|---|---|---|
  | pair 1 (load 5-11) | 284, 289, 369, 369, 357 | 266, 281, 383, 397, 419 | 1667 / 1745 (+5%) |
  | pair 2 (load 2-5) | 226, 219, 302, 390, 285 | 210, 205, 274, 347, 277 | 1422 / 1313 (-8%) |

  No difference is measurable above the noise of the shared machine (about 8% either way).
- **Client screenshots** (Mesa llvmpipe, render distance 10, cameras 60-150 blocks from the
  flanks, nine spots: two views of the alpine-peaks massif, a mountain wall, two meadow valleys,
  a steep north-west flank, a cold grove, a west-facing and a south-facing flank). Reviewed by eye
  after five rounds of tuning, and once more with the final code in a fresh world (no exception
  in the server or client log apart from the container's missing sound device; no floating
  trees in the views or in the AutoTest tree check):
  - **Looked right:** conifer forest on the lower and middle flanks thinning out into scattered
    small trees and a ragged line, higher up gullies than on spurs; snowy grove forests climbing
    towards the summit; soil, grass, shrubs and flowers on the treads of steep ground with rock
    risers; sheer faces and summits keep their rock mosaic and snow patches.
  - **Found and fixed from the screenshots:** dirt risers made stepped meadows look brown and
    striped (the soil now covers only the treads there); forest soil showed through snowfields
    above the line (snow comes first above the line now); bare soil reached far up faces where
    the trees had already thinned out (the soil now follows the tree cover on steep ground).
  - **By design:** a 60-75 degree face (the north-west flank) stays rock with trees only above
    and below it; above the tree line in cold climates the meadow grass carries vanilla's snow
    layer, which shows as white-topped grass blocks.
- **Offline checks:** `treeStudy` krummholz (12 samples per size, flat and sloped ground): no
  floating wood, nothing clipped by the reach limit, at most 0.2% of the leaves cut off from
  wood; the sizes the krummholz feature uses (young, small, medium) are 2-6 blocks tall on
  average, 7 at most. `floraStudy bench` (the zone function for every column of 8x8 chunks after
  the model's columns are cached, valley levels included): 1.1 ms per chunk (4.2 us per column)
  at the find:mountain massif and 1.5 ms per chunk (5.8 us per column) at the ancient mountains,
  against 4.8 and 8.3 ms per chunk for the model's own heights. `floraStudy` survey, transects,
  summits and maps were used to tune the zones. The data generator was re-run and produced
  identical files.
- **Not tested:** other seeds in game (the zones were only surveyed offline on seed 12345);
  worlds of other world types (the mountain features do nothing outside Terrahaul worlds);
  other mods' biomes on the flanks (they get no mountain forest unless they are in one of the
  datagen's biome groups). The zones and the tree line were tuned on the mountain shapes of
  98b91c0; after the merge with the pointed mountains they were looked at once in game (see
  "Fourth round" above) but not tuned again.

## Third feedback round
This round merged: no floating icebergs or monument rooms over deep oceans; ocean monuments only
on flat sea floors; dry riverbeds with a gravel and sand wash; the water wall sealer; client
water effects (spray, mist, foam, sound); hoodoos ending in jagged points; lake floors and
freshwater life; the mountain rock mosaic with patchy snow; winding cascades; irregular craters;
bigger, detailed conifers; waterfalls laid on the slope; more trail ruins, signposts and lamp
posts; steep, sharp mountains; and rough, lopsided volcanoes.

The sections below record what was checked while each piece was developed, before the merge: a
few pieces were also run in game, most were checked only offline with the devtools (pure Java on
the terrain model and the feature plans). The merged build was then tested in game, which found
two bugs that the offline checks could not see; both are fixed and the fixes were tested in game.

### In-game test of the merged build (fresh world, seed 12345, dedicated server and client)
`runServer` AutoTest in a fresh world, 17 areas found with `/terrahaul find` (waterfall, cascade,
lake, crater lake, volcano, hoodoos, deep ocean, old-growth spruce and pine taiga, dry river,
great river, mountain, alpine peaks, mountain wall, broken house, path lights, signpost; 3 to 7
chunks around each), every vanilla structure located and checked, then 21 client screenshots
(Mesa llvmpipe software rendering, render distance 10).
- **Bug 1, found in the screenshots: a grid of one-block rock walls along the chunk borders of
  every lake.** The water wall sealer sealed the still-empty lake columns of a neighbour that had
  not been decorated yet; the neighbour's lake then formed around the rock. Fixed in 9d910a2: the
  sealer only writes into its own chunk and into neighbours that have finished their features.
  AutoTest now counts single blocks in open water at the surface ("water fins") and how many of
  them sit on chunk borders.
- **Bug 2, found by AutoTest and the screenshots: the first waterfall from spawn (-927 70 -817)
  sat on a thin floating stone shelf with air below** (9 floating fragments, 28 blocks). Terrahaul's
  own noise caves (the spaghetti tunnels of the cave router) ran under the pool, because the cave
  guard only knew the terrain model's lakes and rivers, not the waterfall and cascade plans; the
  devtools missed it because they did not evaluate the router's caves and used the seed directly
  as the model seed. Fixed in 2d355b5 (merged as a2963de): caves and carvers keep below what the
  plans build; FallStudy now evaluates the game's noise fill with the router's caves and fails on
  the old code at this site and about a third of the falls and cascades, and passes after.
- **Results on the final build** (a2963de, the same terrain as the shipped build): 0 water walls
  in all 17 areas; water fins 0-5 per area, all natural (crust islands and single rocks), 1 on a
  chunk border; floating terrain 0 everywhere except one cobblestone block at a broken house;
  0 floating trees and 0 decaying leaves; no exceptions; 90-270 ms per chunk including neighbours;
  every vanilla structure (villages, outpost, pyramid, swamp hut, mansion, stronghold, mineshaft,
  ancient city, trial chambers, monument) generates where `/locate` reports it.
- **Screenshots:** waterfall (now down a narrow cleft in its slope), lake with underwater plants
  and no grid, the volcano crater lake with rock islands, hoodoo points, the dry riverbed wash,
  old-growth pine and spruce taiga, a broken house, a lamp post and a signpost by trails, a
  mountain face and peak, a great river. Four shots were badly placed (sky or leaves), so the
  cascade, the alpine peaks and the wall from below were not seen in game.
- **`/locate` cost:** the cave guard (2d355b5) made far structure searches slower, because each
  height probe now checks the waterfall and cascade plans near it. `/locate structure
  minecraft:swamp_hut` (12,400 blocks away, fresh world): 9-10 s before the guard, about 30 s
  with it, about 20 s after the speed-up in 983942b. That speed-up was checked to leave the world
  byte-identical (guard levels, plan lists and plan shapes over 8192x8192 blocks on six seeds,
  shuffled and multi-threaded). Chunk generation itself is not slower.

### Checked in game before the merge
Each before the pieces were merged together, in a fresh world with seed 12345 unless noted:
- **Icebergs and ocean monuments (no floating ones):** two deep frozen ocean areas and an island
  showed no icebergs over deep water and no floating terrain. The monument at 720, 816 stood on
  the floor (rooms at Y −62) instead of leaving a 42x15x48 group of rooms at Y 40-54. This was
  before the flat-floor rule below, which may move or remove that monument. `AutoTest` gained
  `-PheightAt` (the generator's height estimate against the generated ground) and
  `-PbiomeFeatures` (a biome's features after biome modifiers).
- **Dry riverbeds:** the dry riverbed found by `/terrahaul find dry_river` showed as a band of
  gravel and sand through the badlands' red sand, with no change outside the channel and its
  banks.
- **Water wall sealer:** an `AutoTest` run of an earlier version over ten areas (seed and world
  not recorded) counted 0 water-wall faces in 8 areas and 3 faces below sea level in each of a
  great river and a river.
  The merged version also seals sideways near the bed and below every source block; it compiles
  but was not run in game before the merge.
- **Water effects:** two runs of the development client; see the subsection below.

### Checked offline only
- **Ocean monuments only on flat sea floors:** compiles. Nothing else was checked: neither how
  many monuments the 10-block rule removes nor whether `/locate` and the built monument agree in
  game.
- **Hoodoo points** (`formationStudy`):
  - `formationStudy check` on the first version of the points, before the tip fix measured in
    the next sub-bullet, and not re-run on the final shape (60 sites of each type on seeds
    12345, 42, 1 and -7777): 0 floating blocks; about 91% of hoodoo tops are points (top layer
    1-2 blocks, the next at most 6, the rest are nubs on fin crests and ledges), and none of the
    old capped profile (a wide layer over a narrow neck) is left: 4-5 flags per seed, all where
    two neighbouring tips touch or the ribs narrow a slender pillar well below its point; no sea
    stack head sits over its wave notch.
  - `formationStudy check_hoodoo` on the final points (60 fields on each of seeds 12345, 42, 1
    and -7777, 5 807 pillar tips, each measured from its own tip block): the single block of a tip
    stands on at most one more single block (before: 606 tips ended in a 1x1 post three layers
    tall and 6 in one four layers tall), no layer under a point is wider than the one under it
    on a one- or two-block neck (before: 312 small crosses and Ts), 0 floating blocks. Arches,
    spires and sea stacks are unchanged block for block (equal block hashes). Planning a field
    takes less than half as long as before the points were fixed (11.5 s against 26.0 s for the
    240 fields), and less than before the points were added at all.
- **Lake floors and lake life** (`seaFloorStudy` lake modes): before/after views from shared
  cameras, and checks of support, water holding, floor replacement and chunk seams; a `lakesurvey`
  of plants and objects by depth band and fixed `lakeview` cameras, with a diver and a boat view
  from a point nobody picked. The planner reproduces the surface rule's lake beds, so the
  devtool's plants match the game's. The commits record no counts for these checks; the one
  number recorded is the cost of the shoals, now at most 18 blocks above the floor: the worst
  chunk writes 4.2k blocks instead of 6.6k (`lakeperf`, which counts the blocks each chunk
  writes; its timings cover planning only).
- **Mountain rock mosaic and snow** (`fadeStudy flanks`): renders of mountain flanks in block
  colours, speckle and patch sizes, and the time of the material choice before and after. The
  commits record no numbers.
- **Steep, sharp mountains** (`mountainShapeStats`, new: slope bands, bench share and
  continuity, crest roundness, level tops). The commits record the faults found and fixed rather
  than before/after statistics: a river crossing a range was cut to full depth beside a crest
  and not on it, leaving walls 150-300 blocks high; ledges crowded into stripes every 15 blocks
  (now 20-90 blocks apart); an ancient range beside an alpine one had flanks of over 75 degrees
  (a 186-block slot on seed 42). The finder's summit climb now looks 64 blocks around instead of
  48, past the notches of sawtooth crests.
- **Winding cascades** (`waterStudy` cascade metrics: pools, drop per lip, split lips,
  sinuosity, sideways offset, turns, walls; `cascadeviews`). By construction a pool's rim is at
  most 3 blocks and its cut at most 4, and a plan that would leave more than two walls of 5 or
  more blocks is rejected. The commit records no measured values.
- **Waterfalls on the slope** (`fallStudy`, new, with `FallRender`). The old planner cut about
  9 500 blocks per fall, up to 48 deep. A site is now rejected if any column needs more than 10
  blocks cut (12 under water, 15 in the basin under a plunge's leap) or more than 3 built, or more
  cut in all than 1400 + 30 per block of drop. The `fallStudy` survey asserts the cut cap, no
  holes or gaps, the water invariants, no cut at the plan's bounds or far from the water, no
  outflow stopping on a slope, fans with ribs, plunges with a leap and no creek-like faces, and
  reports the share of outflows that join other water; its `order` mode fails if a plan depends
  on the order plans are made in. `waterStudy falls` checks each course from lip to pool, and
  `finderAudit` checks that the fall is in sight from the reported spot. The commits do not
  record the survey's counts.
- **Irregular craters** (`volcanoStudy craters` and `cratersurvey`, `waterStudy craters`), seeds
  12345 and 42, before -> after: variation of the crest radius (cv) 0.003-0.018 -> 0.065-0.127;
  standard deviation of the crest height 0.3-0.5 -> 2.8-23.7 blocks; shore radius cv
  0.006-0.056 -> 0.09-0.17; freeboard of crater lakes 7.9-28 blocks. Every crater lake and lava
  pool within 65 536 blocks (39 and 28 craters): source next to air 0, water above air 0, dry
  gaps 0, no bank built beside a crater's water. `waterStudy lakecheck`, `finderAudit`
  (crater_lake, volcano) and `wallAudit` around 14 craters: unchanged. Crater columns cost about
  5.8 µs instead of 5.3. The review then gave every crater a floor (a quarter had been floorless
  funnels); the review's commit records no new numbers.
- **Rough volcanoes** (`volcanoStudy views`: from the rim, from the flank, from above, in
  materials and in plain relief). After the review, crater floors, lake and lava levels and
  freeboard match the crater work above on seeds 1, 42 and 12345, and crater lakes keep their
  area to within a few percent. Barrancos are kept under 35 degrees and rills under 20 by
  construction (their depth follows the slope of their noise), and on the largest cones the fine
  roughness is capped at 6 blocks, so it does not add up to steps of more than three blocks
  around the rim. `volcano_thaw` was not checked offline or in game.
- **Bigger conifers** (`treeStudy` with 300 trees per conifer size; `treeStudy stand`, new: whole
  biomes planted from the data pack with the feature's own fit test). No wood is pruned or
  floats, no cell is clipped by the reach limit, at most 0.2% of leaves are dropped for being out
  of reach (none on small trees), and every other species builds exactly as before (same
  statistics, byte-identical renders). Over four seeds the canopy trees are 1.53x (pine) and
  1.47x (spruce) taller than before the round (measured before the stands' last retuning). Floor
  with sky light 7 or less: old-growth pine taiga 26% (28% before the bigger conifers),
  old-growth spruce taiga 5.3% (3.9% before). Over six seeds, canopy trees whose foot touches
  another trunk fell from 6-12% to 0 in every taiga, and no stem grows through roots, another
  trunk or a fallen log. Giant pine crowns are 27 blocks wide against 21 for large pines; medium
  pines 16.5 instead of 13.4. Windswept forests keep 1.74 oaks per chunk, as before. Saplings
  were not grown in game.
- **Trail ruins, signposts and lamp posts** (`ruinStudy`), per 1000 blocks of trail drawn on the
  ground, seeds 12345 / 42 / 1: broken houses 4.10 / 3.73 / 3.62 (before the round about 0.8-1.1),
  other trailside ruins 3.80 / 3.37 / 3.31, signposts 3.46 / 3.50 / 3.60 (before 0.55-0.78), lamp
  posts 8.70 / 8.83 / 8.25 (before 1.44-1.94; the earlier counts used the laid trail's length,
  which also counted trail not drawn on the ground). Houses: about 21% huts, 28% cottages, 16%
  two-room, 16% farmhouses and 14% two-storey; a fifth of stone houses are tower houses; 28% keep
  a real bed. The finder's standing points: 0 bad of 1553 / 1107 / 642 sites (at the old reported
  positions 1290 / 910 / 524 were bad). `ruinStudy findall`: 45 results per seed, 0 failed
  checks (plans rest on the ground, the point is safe to stand on, no nearer site skipped);
  `filtercheck`: 0 refused.

### Water effects: spray, mist, foam and sound (client, in game before the merge)
- **Build and dedicated server:** `compileJava` and `compileDevtoolsJava` pass. A dedicated server
  (`runServer`, fresh world, seed 12345, `-Pareas="find:waterfall,2;find:cascade,2"`) started,
  ran `AutoTest` and stopped cleanly. The new particle types register on both sides; everything
  else is in `com.terrahaul.client` and is only reached from the client entry point, so the
  server loads none of it. No world generation code changed.
- **First in-game run** (development client, Mesa llvmpipe, render distance 10, the world
  above): the effects found the falls near each screenshot (17 falls of 130 columns, tallest 25,
  widest 94, at the waterfall). The screenshots, aimed at the finder's point, showed little of
  the curtain. A review then replayed the effects against the saved world and found:
  - foam at the foot of every fall lay under the water as it is drawn. Minecraft draws the
    corners of a water surface at the full block next to water with water above it, so the pool
    rises towards the curtain; foam floated at the water's own height (8/9). That hid 72-100% of
    the whitewater churn and 40-69% of the pool foam;
  - Minecraft's particle shader drops everything under an opacity of 0.1, so a quarter to half of
    the mist was invisible and the rest had hard edges;
  - with Fabulous graphics, mist (drawn without depth writes) was laid behind the water layer;
  - the sound came from the middle of a fall (silent from the banks of a 94-wide step), and the
    loops were mastered at -16 to -18 dBFS, about 20 dB above Minecraft's ambient loops;
  - foam drifted with the wind at 0.1 x wind blocks per tick, small steps had little white water,
    and any running water open to the sky counted as rapids.
- **Fixes, checked offline:** foam now floats 0.02 blocks above the highest point of the drawn
  surface under it (the renderer's corner rule, the face split into two triangles, sampled at
  the centre and eight points around the patch). A replay of the spawn code on the saved world
  (waterfall at -683, -506 and cascade at -735, -694, 1347-2324 spawns per kind) puts 0% of
  the foam centres and 0.0-0.4% of the patch area under the drawn surface, against 41-100% and
  41-96% before. Mist has its own core shader (the particle shader with the cut-off at 1/255).
  The loops were regenerated at -30.2 dBFS (roar) and -31.2 dBFS (rush) RMS, with peaks at -14 to
  -17 dBFS; loop lengths are unchanged and the seams stay within the normal sample-to-sample
  step. `tools/datagen_water_fx.py` still regenerates byte-identical files.
- **Second in-game run** (same client and world, Fancy graphics, cameras placed by hand: in the
  plunge pool 13 and 6 blocks in front of the 7-wide, 25-high curtain, beside the pool, 75 blocks
  out, and in the cascade's lower pool 5 and 13 blocks from a 3-high step): the screenshots show
  a soft mist cloud at the foot of the fall without hard edges, dense white foam and churn on
  the pool in front of the curtain, and foam and splashes at the foot of both cascade steps.
  The effects logged 61 falls of 275 columns at the waterfall (the scan now reaches seven chunks
  at HIGH) and 347 cells of running water near drops; up to 767 particles were live. From 75
  blocks the fall shows a small white cloud at its foot.
- **Fabulous graphics: not checked in game.** The run switched to Fabulous for two more shots,
  but the screenshot harness only set the option without rebuilding the renderer, and the client
  crashed in vanilla code on the missing Fabulous render target. The harness now rebuilds the
  renderer after switching. Both mist shaders (`terrahaul:mist`, `terrahaul:mist_depth`) loaded,
  compiled and linked without a warning, but the depth pass's effect (mist in front of the pool
  and the curtain composited over the water) has not been seen.
- **Sounds:** the container has no audio device (OpenAL fails to open), so the loops were not
  heard and the sound controller did not run in game. The four Ogg files decode as mono 44.1 kHz
  with exact loop lengths (7.0, 5.8, 8.0 and 6.6 s).
- **Old falls only:** both in-game runs were made before this round's waterfalls and cascades
  were merged, on the old carved waterfall (the plunge pool and curtain above) and the old
  straight cascade. The effects have not been seen on the slope-laid falls (notch lip, tiered,
  horsetail, fan with ribs) or on the winding cascades.

## Second feedback round: fresh world, every new feature (seed 12345)
A dedicated server created a fresh Terrahaul world and `AutoTest` located 25 landforms with
`/terrahaul find`'s finder (search radius 30 000 blocks from 0,0), generated 7x7 chunks around
each (9x9 around spawn and the volcano) and inspected the blocks. The log had no exceptions.

- **Every finder target was found:** volcano, waterfall, cascade, overhang, alpine_peaks,
  colossal_peak, mountain_wall, trench, canyon, plateau, wetland, great_river, dry_river, lake,
  coral_reef, kelp_forest, trail_ruin, sunken_ruin, hoodoo, arch, fjord, sea_cliff, hills,
  path_lights and signpost. Most searches took under 0.3 s; overhang (3.1 s), mountain_wall
  (1.3 s) and coral_reef (1.3 s) were the slowest.
- **The wetland is a swamp:** all 784 sampled columns around the wetland result are swamp (the
  first run of this round, before marshes were restricted to warmer climates, found one next to
  snowfields in a cold taiga).
- **Floating terrain:** each area is scanned for groups of solid blocks near the surface that do
  not connect to the ground (60-block windows below every column's top, confirmed through the
  whole area). Groups made only of natural terrain blocks are counted; icebergs and structure
  pieces are listed apart.

  | Run | Terrain fragments in 26 areas | Blocks |
  |---|---|---|
  | Start of this round | about 550 (in nearly every land area; up to 44 blocks, e.g. a floating summit tip) | about 950 |
  | With the cleanup passes and the boulder fix | 0 that float freely | 0 |

  What the last run still lists are vanilla icebergs and their loose underwater ice (kept on
  purpose then; the third round removed the icebergs that hung over deep water), one packed-ice
  block on an ice-spikes peak, a few loose gravel and mossy cobblestone blocks under an ocean
  ruin (chunks under ocean ruins and shipwrecks are not cleaned), and a few blocks that rest
  against tree trunks or ruin slabs (the scan does not count wood or slabs as ground). Most
  fragments came from boulders: a bug scattered loose rock through each boulder's bounding box.
- **Trees:** 2 022 trees (wood groups) in the 26 areas: none floating (no wood group without
  ground below or beside it) and no leaves that would decay.
- **Structures:** villages, pillager outpost, desert pyramid, swamp hut, mansion, stronghold,
  mineshaft, ancient city, trial chambers and ocean monument were located and had a structure
  start, as before.
- **Chunk generation time** (ms per chunk, including neighbours, single runs on 4 vCPUs):

  | Area | ms/chunk | Area | ms/chunk |
  |---|---|---|---|
  | spawn plains | 141 | wetland | 131 |
  | volcano | 233 | great river | 144 |
  | waterfall | 145 | dry river | 224 |
  | cascade | 170 | lake | 175 |
  | overhang (Y ~1060) | 259 | coral reef | 133 |
  | alpine peaks | 180 | kelp forest | 119 |
  | colossal peak | 335 | trail ruin | 141 |
  | mountain wall | 227 | sunken ruin | 117 |
  | trench | 107 | hoodoo field | 175 |
  | canyon | 172 | arch | 136 |
  | plateau | 202 | fjord / sea cliff | 119 / 153 |

  The two cleanup passes cost a few percent (comparing the same areas before and after). The
  structure "beard" mixin (`BeardifierMixin`) took jigsaw-structure areas from about 15% of the
  profile to 3%.

### Client screenshots (software rendering, render distance 10)
The development client loaded a copy of the test world and took 32 screenshots, from finder
results and biome searches, plus 13 retakes from better viewpoints. Reviewed by eye:
- **Looked right:** the volcano crater lake in a tuff and scoria cone with dark radial streaks;
  the deep-sea trench (benched deepslate walls, magma floor, vent chimneys, a shipwreck); the
  coral reef (single-species colonies in patches, fans, seagrass on sand); a sunken ruin among
  kelp; a broken mud-brick cottage beside a gravel trail; a signpost and a stretch of broken
  path lights along trails; the hoodoo field; a curved overhang on a badlands butte; the wetland
  (pools, mud, lily pads, weeping trees); a dry riverbed in savanna; a savanna lake; a frozen
  great river; a mountain wall with a stream falling down its face; forests of oak, birch,
  jungle, dark oak and cherry.
- **Found and fixed from the screenshots:** a swamp biome search led to a snowy mountain (swamp
  biomes are now kept to the marshes); an arch lay against a steep slope (arches now need gentle
  ground).
- **Not judged:** the views of the alpine and colossal peaks were too far away or above the
  cloud layer to show the peak shapes well; the cascade view showed a single pool at the edge of
  a desert; the waterfall was partly hidden by jungle; a low view of the volcano's flanks ended
  inside the terrain, so the forested and burned flank zones were not seen in game (only in the
  `volcanoStudy` renders).

### Offline checks (devtools, no game)
Each part of this round was also checked with the pure-Java devtools on the terrain model:
- `wallAudit` over 16k x 16k blocks of seeds 12345, 1, 42 and -7777: no wall classes left that
  end in sheer cut-offs (trenches, fjords, coasts, mesas, canyons; numbers in the commit log).
- `finderAudit`: every grid, summit and overhang target is found from three origins on four seeds.
- `fragmentAudit`: no floating fragments in the model around overhangs, canyons, sea cliffs,
  plateaus and alpine peaks.
- `treeStudy`: every species and size, no wood left unattached before pruning.
- `ruinStudy`: about 1 ruin per km of trail, 0 floating cells in all plans; deterministic across
  threads and generation order.
- `formationStudy`, `fadeStudy`, `seaFloorStudy`, `waterStudy`, `volcanoStudy`, `peakStudy`:
  renders and statistics reviewed by eye.
- `SeedMixer.hash2`: no collisions among 361 201 cells (mirrored cells collided before, which made
  lakes, volcanoes and waterfalls depend on chunk generation order).

## Dedicated server, first release (`runServer --nogui`, `level-type=terrahaul:terrahaul`, seed 12345)
Each run created a fresh world and ran `AutoTest`:
- **Startup:** the server starts and reports `Done` in about 9 s with the spawn area generated.
- **World height:** the dimension type reports `min_y=-512`, `max_build=2032`,
  `height=2544` and 159 sections. The generator is a `NoiseBasedChunkGenerator` with
  `genDepth=2544`, `minY=-512` and sea level 63.
- **Lighting:** sky light is 15 two blocks and 600 blocks above the surface, and the block at
  the build limit is air.
- **Terrain model:** the model is bound to the world seed. Generated surface heights match the
  model except where features (trees, icebergs, structures) or 3D overhangs change them. In
  mountains, 0–61 of 1296–2704 columns differ by more than 12 blocks.
- **Landforms found and generated:**
  - mountains (surface up to Y 1031) and a colossal peak (Y 1260 in the generated area);
  - alpine peaks and a mountain wall;
  - rivers (water-filled channels with gravel, sand or cobble beds) and deep oceans;
  - trenches: the floor reached Y −306, lined with magma, blackstone, basalt and smooth basalt;
  - sea cliffs, canyons, trails (`dirt_path` top blocks in every land area tested), and plains.
- **Biomes:** surface biomes follow the terrain, for example:
  - frozen peaks, jagged peaks and snowy slopes on the colossal peak;
  - frozen and stony peaks on a mountain wall;
  - river biomes in river channels;
  - deep frozen ocean over the cold abyss. After the depth fix, cave biomes at the surface
  of the colossal peak dropped from 204 to 1 sampled column (and no deep dark).
- **Caves:** cave volume (air, water or lava) is about 4–14% in Y −512..−64 and 8–13% in
  Y −64..0 over land.
- **Ores** (counts per band; Terrahaul spawn area vs. a vanilla world with the same seed):

  | Ore | Terrahaul Y −64..0 | Terrahaul Y 0..64 | Vanilla Y −64..0 | Vanilla Y 0..64 |
  |---|---|---|---|---|
  | diamond | 7182 | 185 | 6666 | 187 |
  | redstone | 10266 | 854 | 9011 | 819 |
  | gold | 6214 | 1277 | 5871 | 1246 |
  | lapis | 3579 | 3455 | 3298 | 2977 |

  Vanilla ores keep their vanilla depths. The deep layer (Y −512..−64) has diamonds, redstone,
  gold, iron, lapis and copper. Above Y 320, mountains contain coal, iron, copper, gold and
  emerald.
- **Material fades:** a census of the 15 blocks below each column's top shows the soil profile
  (dirt, coarse dirt, gravel, weathered province rock, stone). It also shows the snow → calcite
  → stone fade on peaks, sedimentary strata on sea cliffs and magma/basalt/blackstone in
  trenches.
- **Structures:** villages (3 locations), a pillager outpost, desert pyramid, swamp hut, woodland
  mansion, stronghold, mineshaft, ancient city, trial chambers and ocean monument were each
  located with the vanilla locate logic. After generating the chunk, each had a valid structure
  start at the located position.
- **Terrain-aware structures:** same seed and same three village searches, with the option off
  and on (ground height span inside the village bounding box):

  | Village search | Off | On |
  |---|---|---|
  | near 0,0 | Y 77..249 (172) | Y 52..110 (58) |
  | near 3000,0 | Y 30..127 (97) | Y 63..100 (37) |
  | near 0,−3000 | Y 141..186 (45) | Y 141..186 (45) (vanilla's choice was already good) |

- **Performance** (generating each area to full status from the server thread, including
  neighbours):

  | World | Area | ms per chunk |
  |---|---|---|
  | Vanilla, same seed | spawn | 53.9 |
  | Terrahaul | spawn plains | 85.7–92.6 |
  | Terrahaul | deep ocean/trench | 105.6–129.3 |
  | Terrahaul | sea cliffs | 138.7 |
  | Terrahaul | canyon | 188.8 |
  | Terrahaul | mountains | 185.9–248.6 |

  These are single runs on a shared container, so treat them as rough figures.

## Client (`runClient` under Xvfb with Mesa llvmpipe software rendering)
- The client starts, loads the mod and shaders (no shader compile errors in the log) and loads a
  Terrahaul save through quick play.
- **Create World screen:** *World Type: Terrahaul* is pre-selected; the vanilla presets remain
  available.
- **Screenshots of landforms:** forest, birch forest, cherry grove, old-growth taiga, meadow,
  river, fjord, waterfall area, arch, spire, hoodoo and village surroundings were captured and
  reviewed.
- **Bug fixed from screenshots:** detail block models referenced textures directly from faces,
  which rendered as the magenta/black missing texture. This was fixed and verified with an
  automated check that every face uses a texture variable.
- **Vegetation sway:** two frames from the same camera position 10 s apart differ only on grass
  and plant blades (and drifting clouds); terrain and trunks are identical.
- **Falling leaves and petals:** particle counts rise in forests and cherry groves (up to about
  240 live particles in a cherry grove). Petals are visible in screenshots.
- **New terrain features, reviewed in screenshots:**
  - a deep-sea trench with sheer deepslate walls, a magma and blackstone floor, basalt vent
    chimneys and bubble columns;
  - canyon walls with overhanging ledges and arches;
  - winding dirt trails with junctions across hills;
  - fuzzy strata on sea cliffs and hillsides;
  - grass breaking into stone on steep slopes.

- **Tall-world fog:** the same camera in a deep gorge at the foot of the colossal peak was
  captured twice at render distance 10. Once used Terrahaul's fog shape and once used vanilla's
  cylinder fog (debug switch `-PvanillaFog`). With Terrahaul's fog, a snowy ridge several hundred
  blocks away vertically is visible; with vanilla's fog it is completely hidden. The rest of the
  frame is identical.
- **Screenshot harness lesson:** with software rendering, distant teleports need the
  "all sections rendered" state to hold for several seconds. Before that wait was added, distant
  shots showed only sky.

## Client memory and the high-render-distance crash
A player's game closed without any error in the log while playing at render distance 16 with
a 4 GB heap. The cause was found in Minecraft's chunk renderer: it creates five vertex buffers
(each with three OpenGL objects) for every 16-block section within render distance, whether
or not the section holds a block. A Terrahaul world is 159 sections tall instead of 24, so that
is about 2.6 million OpenGL objects at render distance 16 and 10 million at 32, almost all for
empty air. `VertexBufferMixin` now creates the OpenGL objects when a buffer is first bound.

Measured with the development client (`-PmemoryLog`, Mesa llvmpipe, `-Xmx6G`, quick play
into an existing save, live heap measured after a full garbage collection):

| World | Render distance | Before the fix | After the fix |
|---|---|---|---|
| Terrahaul | 32 | process killed by the OS (out of memory) within about 2 s of joining | ran to the end of the test: 2946 chunks loaded, process memory 7.6 GB |
| Terrahaul | 16 | – | 1021 chunks loaded, live heap 1.37 GB, process memory 4.5 GB |
| Vanilla, same machine | 16 | – | 1021 chunks loaded, live heap 0.56 GB, process memory 2.8 GB |

The software renderer keeps OpenGL objects in process memory; a hardware driver keeps them in
driver memory, which is why the player's game died without a Java error. The fix was not
tested on a real GPU.

## Client and dedicated server over the network
- A dedicated server (`runServer`, fresh Terrahaul world, offline mode) and a separate client
  process (`runClient -Pmultiplayer=localhost:25565`) were started.
- The client connected and the server logged `Dev joined the game`.
- On the client, the world had the Terrahaul height range (Y −512..2032) and the Terrahaul sky
  effects.
- All chunks within 6 chunks were loaded and rendered. The screenshot shows terrain, trees,
  shrubs and pebbles with correct textures, and ambient particles were running.
- The client then disconnected cleanly. The registry sync of Terrahaul's blocks, items and
  particle types was accepted.

## Not tested
- **The third round's merged build in game** (the run is in progress; see the third round
  above). Until it is recorded, the lake floors and lake life, the trail ruins, the waterfalls
  and cascades, the craters and volcanoes (and `volcano_thaw`), the mountain shapes, the rock
  mosaic and its snow, the bigger conifers and the hoodoo points have been checked offline only;
  the flat-floor rule for ocean monuments and the final water wall sealer have only been
  compiled; and no chunk generation times have been taken for any of them.
- **Real GPUs and drivers:** only Mesa's software renderer was used. Shader behaviour (sway, fog,
  the mist shaders) on NVIDIA, AMD, Intel or macOS drivers is untested.
- **Multiplayer with several players, or over a real network:** only one client on the same
  machine was connected.
- **Other mods:** Sodium, Embeddium, Iris, Oculus, Distant Horizons, biome mods and structure mods
  were not installed. The compatibility notes are based on how those mods work, not on tests.
- **Long play sessions, many players, memory over time and world sizes beyond a few thousand
  blocks from spawn.**
- **Every seed:** results above are from seed 12345, with spot checks of other locations in that
  world. The offline devtools covered seeds 1, 42 and -7777 as well, but in game only seed 12345
  was generated.
- **The new features with other mods' features:** the floating-terrain cleanup was only run with
  vanilla and Terrahaul features. A mod that places natural stone, dirt or sand as a floating
  shape on purpose before the `fluid_springs` step would have it removed.
- **Sign text, chest loot and archaeology in ruins** were not opened in game; the ruins were only
  checked for floating blocks and seen in screenshots. The third round's broken houses and other
  trailside ruins were not checked in game before the merge.
- **The water effects on this round's slope-laid waterfalls and winding cascades** (they were
  only seen on the old carved waterfall and cascade), **with Fabulous graphics** (the mist depth
  pass) and with renderers that draw water another way (Sodium, Embeddium).
- **Water sounds on a real audio device**, and the water effects' cost per tick on a busy client
  (the scanner's budgets are fixed, but they were not profiled).
