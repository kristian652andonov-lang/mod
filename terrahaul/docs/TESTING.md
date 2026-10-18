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

## Dedicated server (`runServer --nogui`, `level-type=terrahaul:terrahaul`, seed 12345)
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
- **Real GPUs and drivers:** only Mesa's software renderer was used. Shader behaviour (sway, fog)
  on NVIDIA, AMD, Intel or macOS drivers is untested.
- **Multiplayer with several players, or over a real network:** only one client on the same
  machine was connected.
- **Other mods:** Sodium, Embeddium, Iris, Oculus, Distant Horizons, biome mods and structure mods
  were not installed. The compatibility notes are based on how those mods work, not on tests.
- **Long play sessions, many players, memory over time and world sizes beyond a few thousand
  blocks from spawn.**
- **Every seed:** results above are from seed 12345, with spot checks of other locations in that
  world.
