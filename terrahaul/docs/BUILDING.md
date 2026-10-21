# Building, running and regenerating data

## Prerequisites
- JDK 21. Gradle downloads the toolchain-independent parts; set `JAVA_HOME` to a JDK 21 if the
  default `java` is older.
- Internet access for the first build: Gradle 8.14.3, ModDevGradle 2.0.148, NeoForge 21.1.262,
  Parchment mappings and the Minecraft 1.21.1 artifacts are downloaded and cached.
- Optional, only to regenerate data and assets: Python 3.10+ with Pillow (`pip install pillow`);
  the water effect sounds also need numpy and `ffmpeg` with libvorbis.

## Build the mod JAR
```
cd terrahaul
./gradlew build
```
Output: `build/libs/terrahaul-1.0.0.jar`. This is the only file players and servers need.

The first build decompiles and recompiles Minecraft with NeoForge's patches and Terrahaul's access
transformer. That takes a few minutes; later builds are incremental.

## Run configurations
ModDevGradle generates run configurations (IntelliJ: *Sync Gradle*; or use the Gradle tasks):

| Task | What it does |
|---|---|
| `./gradlew runClient` | Starts a development client with the mod |
| `./gradlew runServer` | Starts a dedicated server in `run/` (`--nogui`). `run/server.properties` uses `level-type=terrahaul\:terrahaul` |

Both use `run/` as the game directory.

### Automated server test
```
./gradlew runServer -Pautotest=<output dir> -Pareas="0,0,8;find:mountain,6;biome:minecraft:plains,5"
```
After the server starts, `com.terrahaul.dev.AutoTest` does the following, then halts the server:
- generates each area (`x,z,radiusInChunks`, `find:<landform>,r` or `biome:<id>,r`) to full
  status, measuring the time;
- compares generated heights with the terrain model;
- counts ores per depth band and cave volume;
- renders a top-down map and a vertical section of each area to PNG;
- locates and generates villages, outposts, temples, mansions, strongholds, mineshafts, ancient
  cities, trial chambers and monuments;
- writes `autotest-report.txt`.

Two more reports can be added:
- `-PheightAt="x,z;x,z"` compares, at each point, the generator's height estimate (what
  structure placement sees) with the generated ground and the terrain model;
- `-PbiomeFeatures=<biome id>` lists the biome's features per generation step after all biome
  modifiers.

Add `-Pjfr=<file.jfr>` to record a Java Flight Recorder profile. Without `-Pautotest` the server
runs normally.

### Client screenshot test
```
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient -Pquickplay=<save name> -Pscreenshots="forest@biome:minecraft:forest,14,1.7;falls@find:waterfall,40,10"
```
`com.terrahaul.client.dev.ClientScreenshotTest` does the following, then closes the client:
1. Loads the singleplayer save.
2. For each shot, picks a viewpoint with a clear line of sight to the found landform or biome.
3. Waits until the chunks are loaded and rendered.
4. Saves `run/screenshots/terrahaul_<name>.png` and logs the particle count.

A shot of `name@same` reuses the previous camera, which is useful to compare animation frames.
The player gets night vision so underwater shots (trenches, sea floors) are readable.

Two more client checks:
- `-PcreateWorldCheck` opens the Create World screen from the title screen. It logs the selected
  world type and the available presets, saves `terrahaul_create_world.png` and exits.
- `-Pmultiplayer=localhost:25565` joins a running dedicated server over the network. It logs
  the dimension height and loaded chunks, saves `terrahaul_multiplayer.png`, disconnects and
  exits.
The save must already exist. Copy a world created by the server test into `run/saves/`.
`xvfb-run` and Mesa's software renderer allow running it on a machine without a GPU.

## Development tools (not shipped)
The `devtools` source set renders the terrain model without launching Minecraft:
```
./gradlew renderMap -Pargs="<seed> <centerX> <centerZ> <sizeBlocks> <blocksPerPixel> <out.png> [relief|climate|class|perspective|section]"
./gradlew probe -Pargs="<seed> <x> <z> ..."
```
The other tasks of the `terrahaul` Gradle group run the same way, with their arguments in
`-Pargs="..."`: `volcanoStudy`, `waterStudy`, `fallStudy`, `peakStudy`, `formationStudy`,
`fadeStudy`, `treeStudy`, `seaFloorStudy`, `ruinStudy`, `wallAudit`, `finderAudit`,
`fragmentAudit`, `transect`, `snapshot`, `wetlandStats`, `mountainShapeStats` and
`floraStudy`. Each class in `src/devtools/java/com/terrahaul/devtools/` lists its modes and
arguments in its Javadoc (for example `fallStudy survey`, `treeStudy stand`, `seaFloorStudy
lakesurvey`, `volcanoStudy cratersurvey`, `formationStudy check_hoodoo`).

The game does not use the world seed as the terrain model's seed: it derives the model seed from
the world seed (`TerrainModels.seedFrom`). `fallStudy` and `waterStudy` take the world seed a
player types and derive the model seed the same way; `model:N` gives a model seed directly. The
terrain they check is the world's noise fill with the noise router's caves (`GameWorld` and
`RouterTerrain` in the devtools, which read Minecraft's noise classes and data from the Gradle
classpath); vanilla carvers and structures are not included. The other tools take the terrain
model seed, so their "seed 12345" is not the world a player gets with seed 12345.

## Regenerating data and assets
Most JSON under `src/main/resources` is generated by the scripts in `tools/` and committed, so a
normal build does not need Python. Re-run a script only after editing it:

| Script | Generates |
|---|---|
| `tools/datagen_worldgen.py <vanilla data/minecraft dir>` | Dimension type, noise settings, density functions, noises, world preset and its tag |
| `tools/datagen_features.py <vanilla data/minecraft dir>` | Configured/placed features, tree configurations, biome modifiers (trees, details, formations, water, resource layers) |
| `tools/datagen_assets.py` | Block states, block/item models, procedural textures, particle sprites, language entries, loot tables, tags, recipes, data maps |
| `tools/datagen_water_fx.py` | Spray, mist and foam sprites and particle descriptions; the procedural waterfall and rapids loops (Ogg Vorbis) and `sounds.json`. Needs numpy and `ffmpeg` with libvorbis |

The vanilla `data/minecraft` directory is needed because the worldgen scripts copy a few vanilla
definitions: the Overworld surface rule, spawn target, cave expressions and the feature lists
that are removed or adapted. Extract it from the Minecraft 1.21.1 client JAR:
```
unzip -q ~/.minecraft/versions/1.21.1/1.21.1.jar 'data/minecraft/*' -d /tmp/mc
python3 tools/datagen_worldgen.py /tmp/mc/data/minecraft
python3 tools/datagen_features.py /tmp/mc/data/minecraft
python3 tools/datagen_assets.py
```
The scripts are deterministic: textures use fixed random seeds, so re-running them without
changes produces identical files.

## Installing the built JAR
See the README: copy the JAR into `mods/` on both client and server. For a dedicated server, set
`level-type=terrahaul\:terrahaul` before the world is first generated.
