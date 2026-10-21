# Unfinished work (paused 2026-10-21)

Work was paused when the weekly usage limit ran low. Everything finished is
already on this branch. This folder only holds the two unfinished items so
they survive the cloud container being reclaimed. None of it is built into
the mod.

## State

| Item | State | Where |
|---|---|---|
| Trial chambers, strongholds, mineshafts and ancient cities floating in deep oceans | **Done**, merged (5b6b8f6) and checked in game | this branch |
| Pointed mountains ("these look rounded") | **Unfinished.** The survey is done, and the reshape was started. It compiles, but it has not been measured or checked in game. | `patches/pointed/` |
| Trees and plants on bald mountains | **Unfinished.** Mostly built and tuned (5 in-game iterations). It compiles, but the final numbers, review and final in-game check are not done. | `patches/mountain-flora/` |

The last released jar is still `terrahaul/dist/terrahaul-1.0.0.jar` at 98b91c0.
It does not have the trial chamber fix yet.

## Resuming

```sh
git checkout -b wf/pointed 98b91c0 && git am patches/pointed/*.patch
git checkout -b wf/mountain-flora 98b91c0 && git am patches/mountain-flora/*.patch
```

Both patch sets are made against 98b91c0, and each applies cleanly there.
When both are merged into this branch, `pointed` merges cleanly. After it,
`mountain-flora` conflicts in:
- `build.gradle`, `docs/BUILDING.md` and `dev/AutoTest.java` (with the trial
  chamber fix);
- `worldgen/terrain/TerrainModel.java` (with `pointed`).

Remaining steps:
1. **Pointed.** Finish the reshape, then measure with DomeSurvey (sites in
   `notes/pointed-survey-result.md`). Run wallAudit and fragmentAudit, take
   in-game shots of random mountains, then commit.
2. **Mountain vegetation.** Take the final numbers, update `docs/TESTING.md`,
   and do a short in-game check.
3. **Release.** Merge both into this branch, then compile and run datagen.
   Run one in-game test: deep-ocean trial chambers, random mountains and
   vegetation. Then build the jar into `terrahaul/dist`, test it on a
   standalone server, commit and push.

`notes/*-task.md` are the full task descriptions the two builds were working
from.

## Tools

These are copies of scratch scripts that lived outside the repository. Their
paths point at the old session's scratch folder, so adjust them before use.
- **`ingame.sh <TAG>`:** runs AutoTest and client screenshots from a separate
  worktree.
- **`wwlib.py`:** reads blocks from a saved world (`World(region_dir).block(x, y, z)`).
- **`wait_impl.sh`:** waits for a workflow agent to finish.
- **`feedback-items-workflow.js`:** the workflow script used for each feedback
  item.
