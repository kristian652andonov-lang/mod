# Testing

This page records what was actually tested, how, and with what result. It also lists what was
**not** tested. Everything here was run in the development environment: a Linux container with
4 vCPUs, no GPU, Java 21, NeoForge 21.1.262. The tools are described in
[BUILDING.md](BUILDING.md).

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

## Third feedback round (merged build at a55aed0)
This round merged: no floating icebergs or monument rooms over deep oceans; ocean monuments only
on flat sea floors; dry riverbeds with a gravel and sand wash; the water wall sealer; client
water effects (spray, mist, foam, sound); hoodoos ending in jagged points; lake floors and
freshwater life; the mountain rock mosaic with patchy snow; winding cascades; irregular craters;
bigger, detailed conifers; waterfalls laid on the slope; more trail ruins, signposts and lamp
posts; steep, sharp mountains; and rough, lopsided volcanoes.

**Status: the full in-game test of the merged build is still running and is not recorded here.
Its results will be added as a separate section.** Until then, what is written below is what
was checked while each piece was developed, before the merge: a few pieces were also run in
game, most were checked only offline with the devtools (pure Java on the terrain model and the
feature plans; no Minecraft, no generated chunks). None of the offline checks shows how a piece
looks or performs in a generated world, and none was repeated on the merged build. The numbers
are the ones recorded in the commit messages; where a commit names a check but records no
result, that is said.

### Checked in game before the merge
Each before the pieces were merged together, in a fresh world with seed 12345:
- **Icebergs and ocean monuments (no floating ones):** two deep frozen ocean areas and an island
  showed no icebergs over deep water and no floating terrain. The monument at 720, 816 stood on
  the floor (rooms at Y −62) instead of leaving a 42x15x48 group of rooms at Y 40-54. This was
  before the flat-floor rule below, which may move or remove that monument. `AutoTest` gained
  `-PheightAt` (the generator's height estimate against the generated ground) and
  `-PbiomeFeatures` (a biome's features after biome modifiers).
- **Dry riverbeds:** the dry riverbed found by `/terrahaul find dry_river` showed as a band of
  gravel and sand through the badlands' red sand, with no change outside the channel and its
  banks.
- **Water wall sealer:** an `AutoTest` run of an earlier version over ten areas counted 0
  water-wall faces in 8 areas and 3 faces below sea level in each of a great river and a river.
  The merged version also seals sideways near the bed and below every source block; it compiles
  but was not run in game before the merge.
- **Water effects:** two runs of the development client; see the subsection below.

### Checked offline only
- **Ocean monuments only on flat sea floors:** compiles. Nothing else was checked: neither how
  many monuments the 10-block rule removes nor whether `/locate` and the built monument agree in
  game.
- **Hoodoo points** (`formationStudy`):
  - `formationStudy check` (60 sites of each type on seeds 12345, 42, 1 and -7777): 0 floating
    blocks; about 91% of hoodoo tops are points (top layer 1-2 blocks, the next at most 6, the rest
    are nubs on fin crests and ledges), and none of the old capped profile (a wide layer over a
    narrow neck) is left: 4-5 flags per seed, all where two neighbouring tips touch or the ribs
    narrow a slender pillar well below its point; no sea stack head sits over its wave notch.
  - `formationStudy check_hoodoo` on the hoodoo points (60 fields on each of seeds 12345, 42, 1
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
  9 500 blocks per fall, up to 48 deep. A site is now rejected if it needs more than 10 blocks cut
  or 3 built anywhere, or more cut in all than 1400 + 30 per block of drop. The `fallStudy`
  survey asserts the cut cap, no holes or gaps, the water invariants, no cut at the plan's bounds
  or far from the water, no outflow stopping on a slope, fans with ribs, plunges with a leap and
  no creek-like faces, and reports the share of outflows that join other water; its `order`
  mode fails if a plan depends on the order plans are made in. `waterStudy falls` checks each
  course from lip to pool, and `finderAudit` checks that the fall is in sight from the reported
  spot. The commits do not record the survey's counts.
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
- **The water effects with Fabulous graphics** (the mist depth pass) and with renderers that
  draw water another way (Sodium, Embeddium).
- **Water sounds on a real audio device**, and the water effects' cost per tick on a busy client
  (the scanner's budgets are fixed, but they were not profiled).
