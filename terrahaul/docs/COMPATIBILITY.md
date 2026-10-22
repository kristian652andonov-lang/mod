# Compatibility and limitations

This page lists how Terrahaul interacts with Minecraft, NeoForge, other mods and data packs, and
the limitations that are known today. Statements about testing are kept separate: see
[TESTING.md](TESTING.md) for what was actually run.

## What Terrahaul changes, and where

| Area | How | Scope |
|---|---|---|
| World type `terrahaul:terrahaul` | Data: world preset, dimension type `terrahaul:overworld` (Y −512..2031), noise settings `terrahaul:overworld`, density functions | Only worlds created with this type |
| Terrain | Custom density function types `terrahaul:terrain`, `terrahaul:column_bounded`; surface rule `terrahaul:terrain_surface` (runs before the vanilla rule) | Terrahaul worlds |
| Trees | Biome modifiers remove the vanilla tree features and add `terrahaul:procedural_tree` features; sapling growth event. The `terrahaul:sapling/*` configured features set the optional `procedural_tree` field `"sapling": true` (default `false`), so a tree grown from a sapling replaces only air, leaves, water and plants, as vanilla trees do | All Overworld biomes, all worlds (config `replaceVanillaTrees`) |
| Ground detail | Biome modifiers add features | All Overworld biomes, all worlds (config `groundDetails`) |
| Water, waterfalls, formations, ocean vents, volcano vegetation, ruins | Features that read the terrain model; they do nothing elsewhere. Volcano vegetation uses the placement filter `terrahaul:volcano_zone` | Terrahaul worlds |
| Lake floors | Features `terrahaul:lake_floor` (`local_modifications` step; its biome modifier is skipped when `groundDetails` is off) and `terrahaul:lake_life` (`vegetal_decoration`) in every Overworld biome. They are planned from the terrain model's lakes; the floor replaces only lake water (and the air just above it where a rock, a tower stump or a pier post rises out of the water) and the bed's sediment, and the life places vanilla blocks in and on the water and on the shore (seagrass, tall seagrass, kelp, lily pads, small dripleaf, big dripleaf with its stem in the water and its leaf just above it, sugar cane, moss carpets). Chests in lake wrecks and drowned buildings use vanilla loot tables (`minecraft:chests/shipwreck_supply`, `minecraft:chests/underwater_ruin_small`) | Terrahaul worlds |
| Water wall sealer | Feature `terrahaul:water_wall_sealer` in the `top_layer_modification` step of every Overworld biome. It looks at the still water (and still lava) above the ground of the chunk's columns and the ring around them, and fills any air (or plant water would wash away) below a source block with rock like the bed. Beside a source block it does the same only at or above sea level, within 12 blocks of the bed, and where the ground of the neighbouring column is lower than the source | Terrahaul worlds |
| Snow on volcanoes | Feature `terrahaul:volcano_thaw` (`top_layer_modification`, after vanilla's `freeze_top_layer`; placement filters `terrahaul:terrahaul_only` and `terrahaul:volcano_zone`) takes vanilla's snow layers off bare volcanic ground where no snow may lie (hot craters, active lava flows, ash and scoria patches) | Terrahaul worlds |
| Floating terrain cleanup | Features `terrahaul:terrain_cleanup` (the `raw_generation` step) and `terrahaul:terrain_cleanup_late` (`fluid_springs`) in every Overworld biome. They remove small groups of natural rock, soil and sand that touch only air or water, in the chunk and its neighbours, and do nothing outside Terrahaul worlds or in chunks under a shipwreck or ocean ruin | Terrahaul worlds |
| Sea floor (coral reefs, rocks, kelp, seagrass, grottos) | Biome modifiers remove vanilla's coral, kelp, seagrass, sea pickle and sand/clay/gravel disk features from the ocean biomes, add Terrahaul's sea-floor features, and add each removed vanilla feature back wrapped in `terrahaul:vanilla_fallback`, which places it only outside Terrahaul worlds | Ocean biomes; other worlds keep vanilla oceans |
| Icebergs | A biome modifier removes `minecraft:iceberg_packed` and `minecraft:iceberg_blue` from frozen and deep frozen oceans; they are added back wrapped in `terrahaul:vanilla_fallback` with the new optional field `terrahaul_max_depth: 22`: unchanged in other worlds, and in Terrahaul worlds placed only where the sea floor at the origin and 16 blocks around it is at most 22 blocks below sea level. `SurfaceSystemMixin` skips vanilla's frozen-ocean ice pillars (the surface pass's iceberg step) in Terrahaul worlds | Frozen ocean biomes |
| Ocean monuments | `RandomSpreadStructurePlacementMixin` picks the flattest deep candidate chunk in each monument region (structure set salt 10387313); `OceanMonumentStructureMixin` drops sites whose floor still varies by more than 10 blocks and lowers the building and its rooms onto the floor | Terrahaul worlds (the choice of the flattest chunk: config `terrainAwareStructures`) |
| Underground structures | `StructureMixin` moves every structure in the structure tag `terrahaul:keep_underground` (trial chambers, ancient cities, strongholds and ordinary mineshafts) down as a whole, when its structure start is created, until each piece lies at least 12 blocks under the terrain over and around it (the generator's `OCEAN_FLOOR_WG` height on an 8-block grid). Structures already that deep keep their vanilla height; none is moved lower than 16 blocks above the world bottom. The start's chunk does not change, so `/locate` and eyes of ender lead to it as before | Terrahaul worlds; data packs can add structures to the tag |
| Extra ore layers | Biome modifiers with the `terrahaul:terrahaul_only` placement filter | Terrahaul worlds (config `oreDepthFix`) |
| Sky effects | Dimension effects `terrahaul:overworld`: vanilla Overworld sky with a configurable cloud height | Terrahaul worlds, client |
| Vegetation sway | Overrides the core shader `minecraft:shaders/core/rendertype_cutout` and wraps plant block models at model bake | Client |
| Terrain fog | Overrides the shader include `minecraft:shaders/include/fog.glsl` (adds a fog shape 2); client mixin `FogShapeMixin` reports shape 2 instead of the cylinder in Terrahaul worlds | Client; shapes 0 and 1 are unchanged |
| Water effects | Particle types `terrahaul:spray`, `terrahaul:mist` and `terrahaul:foam` (registered on both sides, spawned only on the client) with sprites `textures/particle/spray_0..3`, `mist_0..3`, `foam_0..3`; sound events `terrahaul:ambient.waterfall` and `terrahaul:ambient.rapids`, defined in `assets/terrahaul/sounds.json` (four Ogg loops under `sounds/ambient/`, with subtitles) and not in the sound event registry; core shaders `terrahaul:mist` and `terrahaul:mist_depth`; client config section `[water]`. No mixin | Client, all worlds |

### Mixins
All mixins are in `com.terrahaul.mixin`. Except for the client-agnostic structure and placement
hooks noted below, every one first checks that the chunk generator is a Terrahaul generator and
otherwise leaves vanilla behaviour untouched.

| Mixin | Target | Purpose |
|---|---|---|
| `NoiseBasedChunkGeneratorMixin` | `createFluidPicker`, `doFill` | Terrahaul fluid levels (lava at Y −502); skip noise filling of empty cells high above the terrain |
| `NoiseChunkMixin` | preliminary surface, deep materials | Closed-form preliminary surface; deepslate/bedrock placed during the noise fill with vanilla's random sequences, except in the ~17 blocks below the surface, where the surface rule lays sediments and fades and then turns the rest into deepslate |
| `NoiseBasedAquiferMixin` | `computeSubstance` | Air far above the terrain; open air below sea level becomes water (fills river channels) |
| `SurfaceSystemMixin` | `buildSurface` (the scan depth and the `frozenOceanExtension` call) | Surface rules only scan down to just below the surface instead of to Y −512 (performance). Frozen oceans get no ice pillars from the surface pass: their keels reach at most about 60 blocks below sea level, so over Terrahaul's deep oceans they hung about a hundred blocks above the floor. Sea ice and the iceberg features are kept (see Icebergs above) |
| `StrongholdStructureMixin`, `MineshaftStructureMixin` | `getMinY` calls | Keep strongholds and mineshafts at vanilla depths |
| `StructureMixin` | `Structure.generate` (wraps `StructurePiecesBuilder.build`) | In Terrahaul worlds, moves the pieces of a structure in `terrahaul:keep_underground` (and, for jigsaw pieces, their junctions, which the terrain adaptation reads) below the terrain before the structure start is made from them; see Underground structures above. `/locate` only asks for the generation point, which is unchanged |
| `WorldDimensionsMixin` | `isStableOverworld` | Treat the Terrahaul preset as a stable (non-experimental) Overworld so no "experimental settings" warning is shown |
| `HeightRangePlacementMixin` | `HeightRangePlacement.getPositions` | In Terrahaul worlds with `oreDepthFix`, resolve height anchors against the vanilla Y −64..319 frame |
| `RandomSpreadStructurePlacementMixin`, `StructurePlacementAccessor` | `getPotentialStructureChunk` | Terrain-aware choice of the structure chunk in Terrahaul worlds for the vanilla surface structure sets (flat, dry ground) and the ocean monument set (the flattest deep sea floor under the 58x58 footprint), identified by salt |
| `ChunkGeneratorMixin` | `createState` | Registers a Terrahaul world's terrain for the structure placement above |
| `OceanMonumentStructureMixin` | `OceanMonumentStructure.findGenerationPoint`, `generatePieces` (wraps `StructurePiecesBuilder.addPiece`), `regeneratePiecesAfterLoad` | In Terrahaul worlds: no monument where the sea floor, sampled on a 5x5 grid over the 58x58 footprint, varies by more than 10 blocks (checked while finding the generation point, so `/locate` agrees); otherwise the building and its rooms, built at a fixed Y 39, are lowered onto the lowest floor under the footprint. A monument rebuilt after its structure start is loaded from disk (vanilla rebuilds it at Y 39) is moved back to its saved height; that hook runs in all worlds and changes nothing where the heights already agree |
| `MonumentBuildingAccessor` | `OceanMonumentPieces.MonumentBuilding.childPieces` | Accessor for the monument's rooms, which the building keeps in a list of its own, so they move with it |
| `BeardifierMixin` | `Beardifier.compute` | Returns 0 for density samples above or below every structure piece and jigsaw junction in the chunk (where vanilla's sum is 0 anyway), instead of looping over them. Applies in all worlds; results are identical to vanilla |
| `WorldCarverMixin` | `WorldCarver.carveBlock` | In Terrahaul worlds, cave and ravine carvers do not cut within 14 blocks below the surface of nearby lakes and elevated rivers, so carved notches cannot drain a lake into a wall of water |
| `client.VertexBufferMixin` (client only) | `VertexBuffer` constructor, `bind`, `upload`, `uploadIndexBuffer`, `close` | Creates a vertex buffer's OpenGL objects on first use instead of in the constructor. Minecraft builds five buffers per render section in range; in a 159-section-tall world that is millions of OpenGL objects for empty air, which exhausted graphics memory. Applies in all worlds (lazy creation does not change behaviour) |
| `client.RenderSectionMixin` (client only) | `SectionRenderDispatcher.RenderSection` constructor, `releaseBuffers`, `setOrigin` | Gives each render section a map that creates a layer's vertex buffer only when the layer gets geometry (all worlds). In Terrahaul worlds it also frees the buffers of a section moved more than render distance + 2 sections from the camera height, so travelling at different heights does not keep stale meshes in video memory |
| `client.FogShapeMixin` (client only) | `FogShape.getIndex` | In Terrahaul worlds the terrain fog weighs vertical distance at 15%, so valleys stay visible from high peaks (vanilla's cylinder fog counts vertical distance fully) |

Access transformers (`META-INF/accesstransformer.cfg`) widen `SurfaceRules.Context`, its fields
and `CreateWorldScreen.recreated`.

## Multiplayer and dedicated servers
- The mod must be installed on **both sides**. It registers blocks, items, particle types and a
  data pack condition, and NeoForge's registry sync rejects clients without them.
- All world generation runs on the server. The client parts (wind, particles, sway, the water
  effects and their sounds, clouds, colours, the Create World pre-selection) live in
  `com.terrahaul.client` and are only loaded on the physical client; the dedicated server never
  loads client classes.
- The wind is computed independently on each client. It is purely visual and sends no packets,
  so two players may see different leaf motion.
- Dedicated servers create a Terrahaul world with `level-type=terrahaul\:terrahaul` in
  `server.properties` before the world is first generated.
- `/terrahaul find` searches run on a background thread with a radius limit (30 000 blocks), so
  they do not stall the server tick.

## Other mods and data packs
- **Biome mods and data packs:** Terrahaul uses the vanilla multi-noise biome source with the
  vanilla Overworld parameter list, so mods that add biomes through that list or through region
  APIs can work. Their biomes are placed by the climate values Terrahaul's model produces. Mods
  that replace the Overworld noise settings or biome source entirely will conflict with the
  Terrahaul world type, but not with vanilla worlds.
- **Other terrain mods** (for example ones that replace `minecraft:overworld` noise settings) do
  not affect Terrahaul worlds, which have their own noise settings. Do not expect them to combine.
- **Feature mods:** features added by biome modifiers generate normally. Features that assume a
  Y −64 world bottom via `above_bottom` anchors are kept at vanilla depths by the
  `oreDepthFix` mixin when they use `minecraft:height_range` placement. Features with custom
  placement code that reads `getMinBuildHeight()` directly will use Y −512.
- **Structure mods:** structures generate. Jigsaw structures project to the surface heightmap and
  work at any height. Only the vanilla surface structure sets (villages, outposts, mansions,
  desert pyramids, jungle temples, swamp huts, igloos) and the ocean monuments get terrain-aware
  placement. A data pack that changes their salt turns this off for that set; the monuments'
  10-block flatness check and their lowering onto the floor apply in Terrahaul worlds whatever
  the salt and the `terrainAwareStructures` option. A structure meant to lie underground at a
  fixed depth (as trial chambers are) can float in Terrahaul's deep oceans, whose floors lie far
  below vanilla's; adding it to the structure tag `terrahaul:keep_underground` keeps it under
  the terrain. Its pieces must move with `StructurePiece.move` (vanilla's and jigsaw pieces do)
  and must not be rebuilt from scratch when the world loads them.
- **Ocean feature mods / data packs:** in ocean biomes Terrahaul removes vanilla's coral, kelp,
  seagrass, sea pickle and disk features by id and re-adds them for non-Terrahaul worlds; in
  frozen oceans it does the same with the two iceberg features, which Terrahaul worlds keep over
  shallow water only. A data pack that edits those vanilla configured features still affects
  other worlds (and Terrahaul's shallow-water icebergs); in Terrahaul worlds the Terrahaul sea
  floor is used instead. Ocean features of other mods under other ids generate in both.
- **Features that leave water beside air:** in Terrahaul worlds the water wall sealer runs last
  (`top_layer_modification`) and fills air below still water (or lava) above the ground with
  rock, and air beside it within the limits given under Known limitations. A mod feature that
  leaves an open pocket beside still water on purpose, before that step, gets it filled; flowing
  water and blocks placed after world generation are not touched.
- **Lake and aquatic plant mods:** Terrahaul's lake floors only replace lake water and bed
  sediment in Terrahaul lakes (and air just above the water for a few rocks and posts), and its
  lake plants are vanilla blocks; plants of other mods added under their own ids still generate.
- **Tree mods / data packs:** Terrahaul removes vanilla tree features by their ids
  (`minecraft:trees_*` and friends). Trees added by other mods under other ids still generate
  next to Terrahaul's trees. Set `replaceVanillaTrees=false` to keep vanilla trees.
- **Resource packs:** a resource pack that replaces `shaders/core/rendertype_cutout.*` replaces
  Terrahaul's sway shader. Sway stops, but nothing else breaks. Leaf colours follow the pack's
  block colours.
- **Sodium** (tested with 0.8.13 and 0.6.13 for 1.21.1): supported. Sodium draws terrain with its own
  shaders, read from its own jar, so Terrahaul edits two of them as Sodium loads them
  (`SodiumShaders`, through a mixin on Sodium's shader loader): its fog include learns
  Terrahaul's tall-world fog shape, so valleys stay visible from high peaks, and its terrain
  vertex shader gets the vegetation sway, copied from Terrahaul's `rendertype_cutout.vsh`. The
  log says "Added Terrahaul's ... to Sodium's ... shader" for each. If a later Sodium version
  changes the lines these edits look for, the edit is skipped with a warning and Sodium's shader
  is used unchanged (sway is then switched off before any plant is marked). Terrahaul's
  `pack.mcmeta` tells Sodium that its vanilla shader overrides are meant to be ignored, so
  Sodium does not warn about them.
- **Iris** (tested: Iris 1.8.12 with Sodium 0.6.13, without a shader pack and with
  Complementary Reimagined r5.9.3): works. Each Iris version is built for one Sodium line:
  Iris 1.8.12 needs Sodium 0.6.x and crashes on its own when the world loads with Sodium 0.8
  (`ClassNotFoundException: ...SodiumGameOptions$PerformanceSettings` in
  `mixins.iris.compat.sodium.json`); Sodium 0.8 needs Iris 1.8.14 or later. Terrahaul's edits of
  Sodium's shaders match Sodium 0.6.13 as well, so without a shader pack the tall-world fog works
  as with Sodium alone. Vegetation sway stays off whenever Iris is installed, because a shader
  pack can be switched on at any time and draws terrain with its own shaders. With a shader pack
  the pack draws terrain, sky, fog and clouds; Iris passes Terrahaul's fog to it as an ordinary
  cylinder. When a pack is switched on Iris rebuilds every chunk, so for a moment parts of the
  terrain are missing.
- **Embeddium / Oculus:** these replace the chunk renderer. Terrahaul does not enable vegetation
  sway with them, and the tall-world fog only reaches what they draw with vanilla shaders.
  Falling leaves, petals, dust, the water spray, mist and foam, wind and clouds still work
  because they use the normal particle, sound and sky pipelines.
- **Water effects** need no mixin: they read the client's own copy of the world, so they also
  appear on falls built by players, by vanilla or by other mods, and on servers. Water of other
  mods counts when its fluid is in the `minecraft:water` fluid tag. Mist is drawn in its own
  particle render type without depth writes; a mod whose custom particle render type does not set
  the depth mask itself could be drawn after it without depth writes too. That render type uses
  Terrahaul's own core shader (`terrahaul:mist`, Minecraft's particle shader without its 0.1
  opacity cut-off); a resource pack that breaks it makes the mist fall back to the vanilla shader
  with harder edges. With Fabulous graphics a second pass (`terrahaul:mist_depth`) writes the
  depth of the mist in front of the water into the particle layer, so the mist is not laid
  behind the pool and the curtain; where the mist itself is behind water, the water is laid over
  it. Shader packs (Iris, Oculus) draw particles their own way: mist then uses the pack's particle
  shader and the Fabulous pass does not run.
- **Water sounds:** the roar and rush play in the Ambient/Environment sound category. A resource
  pack can replace them through `terrahaul:ambient.waterfall` and `terrahaul:ambient.rapids` in
  `sounds.json`. An ambient sound mod with its own waterfall sounds would play alongside them;
  turn off `waterSounds` to keep only one.
- **Foam and the water surface:** foam floats on the water surface as vanilla's liquid renderer
  draws it (corner heights raised to the full block next to falling water, the top face split
  into two triangles along the north-west to south-east diagonal). A renderer that draws water
  another way (Embeddium) was not tested; foam is lifted 0.02 blocks over the highest
  point of the surface under it, which covers small differences in how the face is split.
- **Mods that override `rendertype_cutout`:** whichever resource pack is higher wins; see above.
- **Packs or mods that override `shaders/include/fog.glsl`:** their version falls back to the
  vanilla cylinder fog for Terrahaul's shape 2. Valleys far below high peaks are then fogged as
  in vanilla.
- **Distant Horizons and similar LOD mods:** not tested. Very tall terrain may need a larger LOD
  vertical range.

## Known limitations
- **`/locate` for distant structures is slower** in Terrahaul worlds: about 20 s instead of 9-10 s
  for a swamp hut 12,400 blocks away (it runs on the server thread). Each height probe also checks
  the waterfall and cascade plans near it, so caves never open under their water. Chunk generation
  is not affected; nearby structures are found as fast as before.
- **Pale oak** does not exist in Minecraft 1.21.1 (it arrived in 1.21.4), so it cannot be
  redesigned.
- **Existing worlds are not converted.** The terrain only appears in worlds created with the
  Terrahaul world type. Changing worldgen config mid-world makes new chunks differ from old ones
  (for example tree density at the border).
- **Height:** Y −512..2031 is close to the engine's limit (a dimension may span at most 4064
  blocks, with min_y between −2032 and 2031). Peaks are soft-capped below 2000; very rare peaks
  come close to the build limit, so there is little room to build on top of them.
- **Performance:** with Y −512..2031 every chunk column is 6.6× taller than vanilla. Terrahaul
  skips empty air and clamps the noise fill to the actual terrain, but chunk generation is still
  slower than vanilla, and tall mountains cost the most because their columns are solid for
  1000+ blocks. Measured on the development machine (4 vCPUs, dedicated test server; see
  TESTING.md):

  | World | Area | ms per chunk |
  |---|---|---|
  | Vanilla | spawn area | about 54 |
  | Terrahaul | lowland, plains, wetlands, rivers | about 130–175 |
  | Terrahaul | oceans, reefs, trenches, sea cliffs | about 105–155 |
  | Terrahaul | mountains, canyons, plateaus, volcanoes | about 170–260 |
  | Terrahaul | colossal peaks | about 335 |

  These figures are from the second feedback round. The third round's additions (lake floors,
  the larger family of trail ruins, the rougher volcanoes and steeper mountains, the bigger
  conifers, the water wall sealer) have not been timed in game yet; see TESTING.md.
  Pre-generating the world, for example with a pre-generation mod, is recommended for servers.
  World saves are also larger: deep caves and tall mountains store more non-empty sections.
- **Clouds** are a single flat layer (vanilla rendering). With the default height of 448, high
  mountains still pierce the cloud layer. That is intended, but it looks like flat clouds cutting
  through the peak.
- **Vegetation sway** is limited to plant models drawn exclusively in the cutout layer. Leaves,
  vines, sugar cane, bamboo, kelp and seagrass do not sway. Changing `vegetationSway` fully applies
  after a resource reload (F3+T) or restart; turning it off also takes effect instantly in the
  shader.
- **Plant sway encoding:** swaying plant models carry their sway height in the vertex colour alpha
  (restored to opaque by the shader). If another mod's or pack's shader replaces
  `rendertype_cutout`, those plants are drawn with an alpha of 0.62–1.0. Cutout rendering
  ignores this, but a translucent-capable replacement shader could make plant tips slightly
  transparent.
- **Terrain-aware structures** choose the best of eight candidate chunks inside each region. In
  very rugged regions none may be flat, so a village can still sit on a slope. The structure's
  own biome rules always apply, so a region may also end up without the structure, as in vanilla.
- **Ocean monuments** are skipped where even the flattest candidate has a floor that varies by
  more than 10 blocks under the footprint, so a Terrahaul world has fewer monuments than a
  vanilla one (how many fewer was not measured). With `terrainAwareStructures` off, each monument
  region keeps vanilla's random chunk instead of its flattest one while the 10-block check still
  applies, so even fewer monuments remain. A monument still floods its footprint from the lowest
  of its floor samples up to sea level, so it can stand in a pit about 10 blocks deep (deeper
  where the floor rises between the samples).
- **Underground structures** in `terrahaul:keep_underground` are never moved below 16 blocks
  above the world bottom (Y −496). A trial chamber can be about 70 blocks tall, so over the
  deepest trench floors (below about Y −410) one would keep less than its 12 blocks of cover or
  reach into the trench; none of the structures surveyed came near this (see TESTING.md). Moved
  under the sea, a structure can meet caves flooded by the sea's aquifers, as vanilla structures
  under vanilla oceans do. Badlands mineshafts are not in the tag: vanilla places them to break
  out of the badlands surface. Moving a structure probes the terrain over its footprint, which
  made a mineshaft over the deep sea about half a second slower to generate.
- **Snow, ice and frozen rivers:** these follow the vanilla biome temperature, including altitude
  cooling. Snow layers come from vanilla's freeze step and so stop where vanilla puts them,
  except on volcanoes, where `volcano_thaw` takes them off bare hot or ash-covered ground. On
  mountains the surface rule also lays snow blocks in patches, from the temperature and the slope
  on snowy and stony peaks and from the biome elsewhere (none in jungles and savannas).
- **Deep oceans:** abyssal plains at Y −60..−160 and trenches down to about Y −340 hold far
  more water than vanilla oceans. That takes more memory and disk per ocean chunk, and the trench
  floors are pitch dark. Drowned and other ocean mobs spawn by vanilla rules. Frozen oceans keep
  their sea ice but have no tall ice pillars, and icebergs only over shallow water, so frozen seas
  over deep water carry only flat sea ice.
- **Water wall sealer:** where a cave or ravine opens beside or under still water, the open cells
  next to the water are filled with rock like the water's bed (sandstone under sand, the bed's
  own rock, otherwise stone), so a cave can end in a rock plug under a lake or the sea. It only
  looks at source blocks above the ground of each column; still water deeper inside a cave
  system is not checked. Openings below the water are always sealed, but openings at its side
  only at or above sea level, within 12 blocks of the water's bed, and where the ground of the
  neighbouring column is lower than the water: a cave that opens into the side of deep water
  below sea level, more than 12 blocks above the bed and behind higher ground (in an underwater
  cliff or the deep wall of a lake, for example), is not sealed. Still lava is sealed the same
  way, so crater lava lakes get rock plugs too.
- **Trails** are laid by the surface rule on the top block. They are not connected to villages
  and do not cross rivers (they end at the banks). Trees and plants do not grow on the path
  blocks; a fallen log or boulder can occasionally lie across a trail.
- **Ruins, signposts and path lights** are features, not structures: `/locate` does not find
  them (use `/terrahaul find path_ruin`, or a target per kind such as `broken_house` or
  `watchtower`), and they have no structure bounding box, so mobs and advancements do not treat
  them as structures. Their chests use vanilla loot tables. They avoid the start chunks of
  vanilla surface structures, but structures from other mods can overlap them.
- **Lake floors:** kelp stands in for freshwater pondweed and seagrass for lake grasses, as
  Minecraft has no freshwater plants of its own. Where the biome will freeze a lake, nothing grows in
  or on its top water block (no lily pads or dripleaf, kelp kept two blocks under), so the ice
  has no holes.
- **Volcano biomes:** volcano flanks take the biome that the climate gives them. In cold mountain
  regions that can be a snowy or stony-peaks biome, so the forest zones there are sparse.
- **Floating terrain cleanup:** only groups made entirely of natural terrain blocks are removed;
  a group touching wood, leaves, plants, ice or any block placed by a structure or another mod's
  feature is kept. Rock floating beside water is kept by the second pass, so a rare floating piece
  can remain at a lake or river shore.
- **Overhangs** come from the 3D terrain density. They are interpolated on Minecraft's
  4×8×4-block noise cells, so ledges are at least a few blocks thick, not thin slabs.
- **Spawn:** the world spawn is chosen by vanilla's spawn search over Terrahaul terrain. On rare
  seeds it can be on a hill or near water.
- The `/terrahaul find` command is approximate for features that depend on block placement after
  generation (for example a waterfall whose lip is changed by a tree). It reports the site the
  terrain model chose.
