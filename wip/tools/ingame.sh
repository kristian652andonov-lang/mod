#!/bin/bash
# full in-game test in the separate worktree: fresh-world server AutoTest, then client screenshots
cd /home/user/mod/.claude/worktrees/ingame/terrahaul || exit 1
TAG=${1:-full}
mkdir -p build run
rm -rf run/world
timeout 5400 ./gradlew --no-daemon runServer -Pautotest=build/at-$TAG -Pareas="find:waterfall,3;find:cascade,3;find:lake,3;find:crater_lake,3;find:volcano,3;find:hoodoo,3;find:deep_ocean,3;biome:minecraft:old_growth_spruce_taiga,3;biome:minecraft:old_growth_pine_taiga,2;find:dry_river,2;find:great_river,2;find:mountain,3;find:alpine_peaks,3;find:mountain_wall,2;find:broken_house,2;find:path_lights,2;find:signpost,1" > build/at-$TAG.log 2>&1
echo "SERVER EXIT $?" >> build/at-$TAG.log
mkdir -p run/saves && rm -rf run/saves/$TAG && cp -r run/world run/saves/$TAG && rm -f run/saves/$TAG/session.lock
rm -f run/screenshots/terrahaul_*.png
SHOTS="waterfall_pool@-930,78,-818,-79,-25;waterfall@find:waterfall,40,15;waterfall_far@find:waterfall,80,35;cascade@find:cascade,30,25;lake@find:lake,45,14;lakefloor@find:lake,8,-4;craterlake@find:crater_lake,90,40;volcano@find:volcano,150,60;volcano_rim@find:volcano,30,15;hoodoo@find:hoodoo,40,14;pinetaiga@biome:minecraft:old_growth_pine_taiga,22,6;sprucetaiga@biome:minecraft:old_growth_spruce_taiga,22,6;dryriver@find:dry_river,35,12;mountain@find:mountain,150,30;mountain_low@find:mountain,140,-120;alpine_low@find:alpine_peaks,160,-200;wall_low@find:mountain_wall,120,-60;house@find:broken_house,14,6;lights@find:path_lights,16,4;sign@find:signpost,10,4;greatriver@find:great_river,80,30"
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" timeout 5400 ./gradlew --no-daemon runClient -Pquickplay=$TAG -Pscreenshots="$SHOTS" > build/client-$TAG.log 2>&1
echo "CLIENT EXIT $?" >> build/client-$TAG.log
