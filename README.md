# AI Girlfriend Companion (NeoForge 1.21.1)

A companion who follows you, talks with you, remembers what you do together, needs food and sleep, gets scared
of monsters, has daily moods, and shares affectionate, synchronized animations with you: holding hands, hugs,
cuddles, kisses, sitting together, carrying her, lap sitting, sleeping side by side. All romantic content is
affectionate and non-explicit.

* Minecraft **1.21.1**, NeoForge **21.1.262** or newer (21.1.x)
* Java **21**
* Required dependency: **GeckoLib 4.9.x** for NeoForge 1.21.1 (`geckolib-neoforge-1.21.1-4.9.3.jar` or newer 4.x)

## Installing

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Put `aigirlfriend-<version>.jar` (from `build/libs/`) and the GeckoLib jar into the `mods` folder.
3. Start the game. Find the **Companion Spawn Egg** in the *AI Girlfriend* creative tab (or `/give @s aigirlfriend:girlfriend_spawn_egg`).
4. Right-click her to claim her. Press **G** while looking at her (or near her) to open the interaction wheel.

The mod is needed on both the server and every client. Players without it cannot join a server that has it.

## Building from source

```
./gradlew build                 # jar in build/libs/
./gradlew runClient             # dev client
./gradlew runGameTestServer     # automated acceptance tests (headless)
./gradlew runAnimTest           # dev only: scripted visual test, see "Animation test harness"
```

## Controls

| Key (default) | Action |
|---|---|
| **G** | Interaction wheel (talk, follow/wait/sit, hug, kiss, cuddle, hold hands, sit together, carry, pet, lap, gifts...) |
| **U** | Status screen (mood and why, needs, relationship, memories, requests) |
| **Y** | Talk to her (free text) |
| **H** | Hold hands / let go |
| **N** | Sit down on the block you stand on (chairs, stairs, slabs, carpets, any block) / stand up |
| unbound | Cancel interaction, hug, kiss, cuddle, carry, pet, lap sit (bind them in Controls) |

* **Right-click her**: claim, feed (hold food), give a gift (hold any item she likes), or sit on a chair/stair/slab/block by right-clicking it (if `sitOnRightClick` is on).
* **Sneak** cancels the current interaction. Carrying and lap sitting end when you sneak.
* Chatting near her (`listenToChat`) lets her answer you without opening the talk screen.

## What she does

* **Follows** you, avoids lava/fire/drops, opens doors, teleports back when stuck or far away, follows you through portals and dimensions.
* **Fear and protection**: notices hostile mobs, hides behind you, panics when close, thanks you when you defeat the threat. With `faintInsteadOfDeath` she faints instead of dying and recovers when you stay with her.
* **Needs**: hunger, energy, attention. She asks for food, gets sleepy at night, and is lonely when ignored.
* **Daily mood** (15 moods) evaluated every morning from what happened yesterday, with the causes shown in the status screen.
* **Relationship stages**: Acquaintance -> Friendship -> Growing affection -> Romantic -> Deep trust -> Partnership. Gains come from varied, meaningful interaction with diminishing returns for repeating the same thing.
* **In a romantic relationship she takes the initiative**: she hugs you from behind, kisses your cheek, leans on you, pets your hair, sits on your lap when you sit, cuddles when you sit together, and at bedtime takes your hand and leads you to bed. She does not ask first (toggle with `romanticInitiative`, `leadToBed`).
* **Sleep**: sleeps in a bed at night (in sleepwear if `autoSleepwear`), sleeps beside you when you sleep, sits up in bed and stretches when she wakes, then greets you.
* **Memory**: remembers gifts, meals, rescues, places, conversations and milestones (bounded, merged, saved with her).
* **Optional cycle** (on by default, non-explicit): a configurable world-time cycle (default 3 Minecraft days with 1 menstrual day) that changes energy, hunger and mood sensitivity and makes her seek comfort. The status display can be hidden (`showCycleStatus`, `allowStatusDisplay`).
* **Outfits**: everyday, beach and sleepwear (`/gf` wheel entry or automatic sleepwear).

## Dialogue and AI

She has a real language model built in: by default (`ai.brain = BUILTIN`) the mod runs a small open model
(Qwen2.5 Instruct, Apache-2.0) inside the game with its own pure-Java inference engine, so you can talk to her
about anything (questions, jokes, feelings, Minecraft) with no account, key or extra program.

* **First use**: the model is downloaded once from huggingface.co (checksum verified, resumable) to
  `<game dir>/aigirlfriend/models/`. `builtinModel = AUTO` picks **SMART** (Qwen2.5 1.5B, 1.1 GB, much better
  answers) on computers with 8+ CPU threads and **SMALL** (Qwen2.5 0.5B, 0.7 GB, faster but simpler) otherwise.
  While it downloads she answers with scripted lines and tells you the progress. Set `builtinAutoDownload = false`
  to download only with `/gf ai download`. `CUSTOM` + `builtinModelPath` loads any Qwen2-family ChatML GGUF file.
* **Speed**: replies take a few seconds on a gaming PC (she shows "..." while thinking); it runs on background
  threads (`builtinThreads`, default half of your cores) and never blocks the game. The conversation is cached,
  so each reply only processes your new message.
* **Talking**: in normal chat, everything you say near her is for her when no other player is around or you
  talked in the last minute; otherwise include her name. The talk screen (keybind) works too.
* Her character, relationship stage, mood and its causes, needs, the place, time, weather, recent events,
  memories and promises are given to the model each turn; the model only writes speech. Gameplay effects
  (commands like "follow me", hugs, promises, apologies, insults) are decided by the mod, never by the model.
  Replies are checked (no "as an AI", links, commands or explicit content; repeats are retried) and fall back to
  scripted lines on any problem.

Other brains (`ai.brain`):

| `brain` | Endpoint example | Key |
|---|---|---|
| `BUILTIN` (default) | in the game | none |
| `OLLAMA` | `http://localhost:11434` | none |
| `OPENAI_COMPATIBLE` (LM Studio, llama.cpp server, vLLM, OpenRouter, ...) | `http://localhost:1234/v1` | optional |
| `ANTHROPIC` (Claude) | `https://api.anthropic.com` | required |
| `OFF` | scripted lines only | none |

* **API keys are never stored in the config.** Put them in the environment variable named by `apiKeyEnvVar`
  (default `AIGIRLFRIEND_API_KEY`) or in `config/aigirlfriend/secrets.properties` as `api_key=...`. That file is
  read only on the server; keep it private and do not distribute it.
* `/gf ai` shows the brain status (download progress, loading, ready).

## Commands

`/aigirlfriend` (alias `/gf`): `info`, `recall`, `restore`, `name <name>`, `memories`, `ai`, `ai download`, and (operators)
`debug stage|mood|evaluate_mood|needs|cycle|request|faint|reset_cooldowns`.

## Configuration guide

Common (server) config `config/aigirlfriend-common.toml`, grouped by section:

* **general**: `maxCompanionsPerPlayer` (1), `restoreIdentityOnRespawn`, `names`, `outfitVariants`, `autoSleepwear`.
* **personality**: default trait values (affection, shyness, playfulness, independence, confidence, sensitivity) and `randomVariation`.
* **ai**: `brain`, `builtinModel`, `builtinModelPath`, `builtinAutoDownload`, `builtinThreads`, `builtinContextTokens`, `endpoint`, `model`, `apiKeyEnvVar`, `timeoutSeconds`, `maxResponseChars`, `maxTokens`, `temperature`, `anthropicEffort`, `maxConcurrentRequests`, `minSecondsBetweenRequests`, `retries`, `useForAmbientLines`, `cacheSize`, `contextMemories`.
* **dialogue**: `frequency`, `minSecondsBetweenLines`, `listenToChat`, `chatListenRadius`, `speechRadius`, `speechInChat`, `conversationHistory`.
* **memory**: `maxMemories`.
* **follow**: `followStartDistance`, `followStopDistance`, `movementSpeed`, `teleportRecovery`, `teleportDistance`, `stuckSeconds`, `followThroughDimensions`, `maxSafeFall`.
* **danger**: `fearRadius`, `hostilesTargetHer`, `maxHealth`, `criticalHealthFraction`, `faintInsteadOfDeath`, `faintSeconds`, `environmentalRescue`, ...
* **needs**: hunger/fatigue/attention rates, `lonelyThreshold`, food and drink lists.
* **relationship**: `progressionMultiplier`, `negativeMultiplier`, `repeatDiminishing`, `moodSensitivity`.
* **cycle**: `enabled`, `clock` (`DAY_TIME` or `GAME_TIME`), `cycleLengthDays` (3), `menstrualDays` (1), phase lengths, multipliers, `allowStatusDisplay`.
* **interactions**: `maxInteractionDistance`, `cooldownMultiplier`, `romanticInitiative`, `initiativeMinSeconds`, `leadToBed`, `sitOnRightClick`, `sitOnAnyBlock`.
* **requests**: `enabled`, `maxPerDay`, gaps and expiry.

Client config `config/aigirlfriend-client.toml`: HUD (`showHud`, corner), `speechBubbles`, `showCycleStatus`,
`minimalUi`, `tooltips`, `animationSpeed`, `playerAnimations`, `lockMovement`, hand-pull, voice blips, sounds, and
**pose tuning** for custom models: `carryForward`, `carryHeight`, `carryYaw`, `lapForward`, `lapHeight`, `lapYaw`,
`seatHeight` (defaults are tuned in game for the bundled model).

## Asset contract (for custom models and animations)

Model `assets/aigirlfriend/geo/entity/girlfriend.geo.json` (GeckoLib/Bedrock), facing -Z, 32 px tall, rendered at
player scale (0.9375). Bones: `root > body > waist > chest > head`, `right_arm/left_arm` with `*_forearm`,
`right_leg/left_leg` with `*_shin`, optional `hair_front`, `hair_back`, `skirt_front`, `skirt_back`, `bust`.
Outfits are separate models `girlfriend_<everyday|beach|sleep>.geo.json` with matching bones and textures in
`textures/entity/girlfriend/`. Facial expressions are textures in `textures/entity/girlfriend/face/`.

Animations (`animations/entity/girlfriend.animation.json`) are looked up by name, with fallbacks so a partial set
still works:

* `move.*`: `idle`, `idle_tired`, `idle_sad`, `idle_scared`, `hide`, `walk`, `run`, `swim`, `crouch`
* `pose.*`: `sit`, `sit_ground`, `lap_sit`, `carried`, `lie_down` (played once), `sleep`, `wake_up` (sitting up in bed, held), `fainted`
* `interaction.*`: `hug`, `back_hug`, `cuddle`, `kiss`, `kiss_cheek`, `forehead_kiss`, `lean`, `hold_hands`, `lead_by_hand`, `sit_together`, `head_on_shoulder_left/right`, `cuddle_sitting_left/right`, `carried`, `lap_sit`, `pet_hair`, `pet_cheek`, `cuddle_bed`, `sleep_together` (optional `_initiate` and `_shy` variants)
* `action.*` (one-shot gestures that start and end at rest): `wave`, `wave_goodbye`, `yawn`, `stretch`, `eat`, `drink`, `look_at_animal`, `look_around`, `startled`, `celebrate`, `sad`, `reach_hand`, `gift_happy`, `shy`, `hurt`, `defend`, `talk`, `pout`, `blow_kiss`, `giggle`, `wake_greeting`, `relieved`, `wake_up`, `ask_help`

Lying clips lay her on her right side around the hips with the head toward model +X; the renderer aligns that to
the bed (and mirrors her when her partner is on her other side).

`tools/build_animations.py` builds the animation file from the character pack in `art/character` (renaming the
pack clips to the names above, applying the fixes found in game, and authoring the clips the pack does not have).
Player-side animations live in `assets/aigirlfriend/player_animations/player.animation.json`.

## Animation test harness (development)

`./gradlew runAnimTest` starts a dev client (source set `src/devtest`, not part of the release jar) that creates a
flat test world, spawns her and a mock "TestPlayer", plays every pose, gesture, expression, outfit and
interaction (including carry, lap, seats, beds and walking hand in hand) and saves screenshots plus an index of
the animation state to `run/animtest/screenshots/animtest/`. It runs headless with `xvfb-run` and software GL.
Set `-Daigirlfriend.animtest.only=<regex>` to run selected shots.

## Verification status

* `./gradlew build` compiles the mod; `./gradlew runGameTestServer` runs the acceptance GameTests (persistence,
  duplicate prevention, needs and daily mood causes, cycle phases, fear detection, hug/carry/lap mount and dismount,
  seats, sleep).
* Every pose, gesture, expression, outfit and two-character interaction has been played in game with the
  animation test harness and a mock player, reviewed from several camera angles, and fixed where it did not look
  natural (see the "in-game review" notes in `tools/build_animations.py`).

Known limitations:

* Lead-to-bed, swimming and crouch-walking were not covered by the scripted visual test.
* The AI providers (Ollama, OpenAI-compatible, Anthropic) have not been tested against a live model yet; when a
  request fails she falls back to the local dialogue, which always works.
* The mock player in the visual test does not move its body on its own, so multi-client testing with real
  players is still worth doing on a server.

## Troubleshooting

* **"Missing GeckoLib" / crash on start**: install GeckoLib 4.x for NeoForge 1.21.1.
* **She does not answer with AI**: check `/gf ai`. The built-in brain needs its one-time download (needs internet;
  `/gf ai download` retries it). For Ollama run `ollama serve` and `ollama pull llama3.1:8b`
  (or set `model`). For remote providers make sure the key is in the environment variable or `secrets.properties`
  on the **server**. Errors fall back to local lines, so she always talks.
* **Interaction does not start**: the action bar says why (stage too low, cooldown, too far, she is scared, no
  bed/seat nearby, you are riding...).
* **She is stuck or lost**: `/gf recall` brings her to you; `/gf restore` restores her if she was lost.
* **Custom model floats or clips while carried / on your lap / seated**: adjust the pose tuning values in the client config.
* **Too many lines / too few**: `dialogue.frequency` and `minSecondsBetweenLines`.
