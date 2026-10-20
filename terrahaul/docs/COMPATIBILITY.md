# Compatibility and limitations

This page lists how Terrahaul interacts with Minecraft, NeoForge, other mods and data packs, and
the limitations that are known today. Statements about testing are kept separate: see
[TESTING.md](TESTING.md) for what was actually run.

## What Terrahaul changes, and where

| Area | How | Scope |
|---|---|---|
| World type `terrahaul:terrahaul` | Data: world preset, dimension type `terrahaul:overworld` (Y −512..2031), noise settings `terrahaul:overworld`, density functions | Only worlds created with this type |
| Terrain | Custom density function types `terrahaul:terrain`, `terrahaul:column_bounded`; surface rule `terrahaul:terrain_surface` (runs before the vanilla rule) | Terrahaul worlds |
| Trees | Biome modifiers remove the vanilla tree features and add `terrahaul:procedural_tree` features; sapling growth event | All Overworld biomes, all worlds (config `replaceVanillaTrees`) |
| Ground detail | Biome modifiers add features | All Overworld biomes, all worlds (config `groundDetails`) |
| Water, waterfalls, formations, ocean vents, volcano vegetation, ruins | Features that read the terrain model; they do nothing elsewhere. Volcano vegetation uses the placement filter `terrahaul:volcano_zone` | Terrahaul worlds |
| Floating terrain cleanup | Features `terrahaul:terrain_cleanup` (the `raw_generation` step) and `terrahaul:terrain_cleanup_late` (`fluid_springs`) in every Overworld biome. They remove small groups of natural rock, soil and sand that touch only air or water, in the chunk and its neighbours, and do nothing outside Terrahaul worlds or in chunks under a shipwreck or ocean ruin | Terrahaul worlds |
| Sea floor (coral reefs, rocks, kelp, seagrass, grottos) | Biome modifiers remove vanilla's coral, kelp, seagrass, sea pickle and sand/clay/gravel disk features from the ocean biomes, add Terrahaul's sea-floor features, and add each removed vanilla feature back wrapped in `terrahaul:vanilla_fallback`, which places it only outside Terrahaul worlds | Ocean biomes; other worlds keep vanilla oceans |
| Extra ore layers | Biome modifiers with the `terrahaul:terrahaul_only` placement filter | Terrahaul worlds (config `oreDepthFix`) |
| Sky effects | Dimension effects `terrahaul:overworld`: vanilla Overworld sky with a configurable cloud height | Terrahaul worlds, client |
| Vegetation sway | Overrides the core shader `minecraft:shaders/core/rendertype_cutout` and wraps plant block models at model bake | Client |
| Terrain fog | Overrides the shader include `minecraft:shaders/include/fog.glsl` (adds a fog shape 2); client mixin `FogShapeMixin` reports shape 2 instead of the cylinder in Terrahaul worlds | Client; shapes 0 and 1 are unchanged |

### Mixins
All mixins are in `com.terrahaul.mixin`. Except for the client-agnostic structure and placement
hooks noted below, every one first checks that the chunk generator is a Terrahaul generator and
otherwise leaves vanilla behaviour untouched.

| Mixin | Target | Purpose |
|---|---|---|
| `NoiseBasedChunkGeneratorMixin` | `createFluidPicker`, `doFill` | Terrahaul fluid levels (lava at Y −502); skip noise filling of empty cells high above the terrain |
| `NoiseChunkMixin` | preliminary surface, deep materials | Closed-form preliminary surface; deepslate/bedrock placed during the noise fill with vanilla's random sequences, except in the ~17 blocks below the surface, where the surface rule lays sediments and fades and then turns the rest into deepslate |
| `NoiseBasedAquiferMixin` | `computeSubstance` | Air far above the terrain; open air below sea level becomes water (fills river channels) |
| `SurfaceSystemMixin` | `buildSurface` | Surface rules only scan down to just below the surface instead of to Y −512 (performance) |
| `StrongholdStructureMixin`, `MineshaftStructureMixin` | `getMinY` calls | Keep strongholds and mineshafts at vanilla depths |
| `WorldDimensionsMixin` | `isStableOverworld` | Treat the Terrahaul preset as a stable (non-experimental) Overworld so no "experimental settings" warning is shown |
| `HeightRangePlacementMixin` | `HeightRangePlacement.getPositions` | In Terrahaul worlds with `oreDepthFix`, resolve height anchors against the vanilla Y −64..319 frame |
| `RandomSpreadStructurePlacementMixin`, `StructurePlacementAccessor` | `getPotentialStructureChunk` | Terrain-aware choice of the structure chunk for vanilla surface structure sets (identified by salt) in Terrahaul worlds |
| `ChunkGeneratorMixin` | `createState` | Registers a Terrahaul world's terrain for the structure placement above |
| `OceanMonumentStructureMixin` | `OceanMonumentStructure.generatePieces` | In Terrahaul worlds, lowers monuments (built at a fixed Y 39) onto the much deeper sea floor |
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
- All world generation runs on the server. The client parts (wind, particles, sway, clouds,
  colours, the Create World pre-selection) live in `com.terrahaul.client` and are only loaded on
  the physical client; the dedicated server never loads client classes.
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
  desert pyramids, jungle temples, swamp huts, igloos) get terrain-aware placement. A data pack
  that changes their salt turns this off for that set.
- **Ocean feature mods / data packs:** in ocean biomes Terrahaul removes vanilla's coral, kelp,
  seagrass, sea pickle and disk features by id and re-adds them for non-Terrahaul worlds. A data
  pack that edits those vanilla configured features still affects other worlds; in Terrahaul
  worlds the Terrahaul sea floor is used instead. Ocean features of other mods under other ids
  generate in both.
- **Tree mods / data packs:** Terrahaul removes vanilla tree features by their ids
  (`minecraft:trees_*` and friends). Trees added by other mods under other ids still generate
  next to Terrahaul's trees. Set `replaceVanillaTrees=false` to keep vanilla trees.
- **Resource packs:** a resource pack that replaces `shaders/core/rendertype_cutout.*` replaces
  Terrahaul's sway shader. Sway stops, but nothing else breaks. Leaf colours follow the pack's
  block colours.
- **Sodium / Embeddium / Iris / Oculus:** these replace the chunk renderer and do not use vanilla
  core shaders. Terrahaul detects them and does not enable vegetation sway. Falling leaves,
  petals, dust, the water spray, mist and foam, wind and clouds still work because they use the
  normal particle, sound and sky pipelines. Shader packs replace sky and cloud rendering.
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
- **Foam and the water surface:** foam floats on the water surface as vanilla's liquid renderer
  draws it (corner heights raised to the full block next to falling water, the top face split
  into two triangles along the north-west to south-east diagonal). A renderer that draws water
  another way (Sodium, Embeddium) was not tested; foam is lifted 0.02 blocks over the highest
  point of the surface under it, which covers small differences in how the face is split.
- **Mods that override `rendertype_cutout`:** whichever resource pack is higher wins; see above.
- **Packs or mods that override `shaders/include/fog.glsl`:** their version falls back to the
  vanilla cylinder fog for Terrahaul's shape 2. Valleys far below high peaks are then fogged as
  in vanilla.
- **Distant Horizons and similar LOD mods:** not tested. Very tall terrain may need a larger LOD
  vertical range.

## Known limitations
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
- **Snow, ice and frozen rivers:** these follow the vanilla biome temperature, including altitude
  cooling. Snow layers come from vanilla's freeze step and so stop where vanilla puts them.
- **Deep oceans:** abyssal plains at Y −60..−160 and trenches down to about Y −340 hold far
  more water than vanilla oceans. That takes more memory and disk per ocean chunk, and the trench
  floors are pitch dark. Drowned and other ocean mobs spawn by vanilla rules.
- **Trails** are laid by the surface rule on the top block. They are not connected to villages
  and do not cross rivers (they end at the banks). Trees and plants do not grow on the path
  blocks; a fallen log or boulder can occasionally lie across a trail.
- **Ruins, signposts and path lights** are features, not structures: `/locate` does not find
  them (use `/terrahaul find trail_ruin`), and they have no structure bounding box, so mobs and
  advancements do not treat them as structures. Their chests use vanilla loot tables. They avoid
  the start chunks of vanilla surface structures, but structures from other mods can overlap
  them.
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
