package com.aigirlfriend.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Gameplay + AI configuration. Lives in {@code config/aigirlfriend-common.toml} so it is easy to find and edit.
 * Every gameplay rule is evaluated on the logical server only, so clients never need these values directly.
 */
public final class GFConfig {
    public static final ModConfigSpec SPEC;

    // ---- general ----
    public static final ModConfigSpec.IntValue MAX_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue RESTORE_IDENTITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMES;
    public static final ModConfigSpec.IntValue OUTFIT_COUNT;
    public static final ModConfigSpec.BooleanValue AUTO_SLEEPWEAR;

    // ---- personality ----
    public static final ModConfigSpec.DoubleValue TRAIT_AFFECTION;
    public static final ModConfigSpec.DoubleValue TRAIT_SHYNESS;
    public static final ModConfigSpec.DoubleValue TRAIT_PLAYFULNESS;
    public static final ModConfigSpec.DoubleValue TRAIT_INDEPENDENCE;
    public static final ModConfigSpec.DoubleValue TRAIT_CONFIDENCE;
    public static final ModConfigSpec.DoubleValue TRAIT_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue TRAIT_RANDOMNESS;

    // ---- ai ----
    public static final ModConfigSpec.EnumValue<Brain> AI_BRAIN;
    public static final ModConfigSpec.EnumValue<BuiltinModel> AI_BUILTIN_MODEL;
    public static final ModConfigSpec.ConfigValue<String> AI_BUILTIN_PATH;
    public static final ModConfigSpec.BooleanValue AI_BUILTIN_DOWNLOAD;
    public static final ModConfigSpec.IntValue AI_BUILTIN_THREADS;
    public static final ModConfigSpec.IntValue AI_BUILTIN_CONTEXT;
    public static final ModConfigSpec.ConfigValue<String> AI_ENDPOINT;
    public static final ModConfigSpec.ConfigValue<String> AI_MODEL;
    public static final ModConfigSpec.ConfigValue<String> AI_API_KEY_ENV;
    public static final ModConfigSpec.IntValue AI_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue AI_MAX_RESPONSE_CHARS;
    public static final ModConfigSpec.IntValue AI_MAX_TOKENS;
    public static final ModConfigSpec.DoubleValue AI_TEMPERATURE;
    public static final ModConfigSpec.ConfigValue<String> AI_ANTHROPIC_EFFORT;
    public static final ModConfigSpec.IntValue AI_MAX_CONCURRENT;
    public static final ModConfigSpec.IntValue AI_MIN_SECONDS_BETWEEN;
    public static final ModConfigSpec.IntValue AI_RETRIES;
    public static final ModConfigSpec.BooleanValue AI_FOR_AMBIENT;
    public static final ModConfigSpec.IntValue AI_CACHE_SIZE;
    public static final ModConfigSpec.IntValue AI_CONTEXT_MEMORIES;

    // ---- dialogue ----
    public static final ModConfigSpec.DoubleValue DIALOGUE_FREQUENCY;
    public static final ModConfigSpec.IntValue DIALOGUE_MIN_SECONDS;
    public static final ModConfigSpec.BooleanValue LISTEN_TO_CHAT;
    public static final ModConfigSpec.DoubleValue CHAT_LISTEN_RADIUS;
    public static final ModConfigSpec.DoubleValue SPEECH_RADIUS;
    public static final ModConfigSpec.BooleanValue SPEECH_IN_CHAT;
    public static final ModConfigSpec.IntValue HISTORY_LENGTH;

    // ---- memory ----
    public static final ModConfigSpec.IntValue MAX_MEMORIES;

    // ---- follow ----
    public static final ModConfigSpec.DoubleValue FOLLOW_START;
    public static final ModConfigSpec.DoubleValue FOLLOW_STOP;
    public static final ModConfigSpec.DoubleValue MOVE_SPEED;
    public static final ModConfigSpec.BooleanValue TELEPORT_RECOVERY;
    public static final ModConfigSpec.DoubleValue TELEPORT_DISTANCE;
    public static final ModConfigSpec.IntValue STUCK_SECONDS;
    public static final ModConfigSpec.BooleanValue FOLLOW_DIMENSIONS;
    public static final ModConfigSpec.IntValue MAX_SAFE_FALL;

    // ---- fear / health ----
    public static final ModConfigSpec.DoubleValue FEAR_RADIUS;
    public static final ModConfigSpec.BooleanValue HOSTILES_TARGET_HER;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue CRITICAL_HEALTH;
    public static final ModConfigSpec.IntValue DEFENSE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue DEFENSE_DAMAGE;
    public static final ModConfigSpec.BooleanValue FAINT_INSTEAD_OF_DEATH;
    public static final ModConfigSpec.IntValue FAINT_SECONDS;
    public static final ModConfigSpec.BooleanValue ENVIRONMENT_RESCUE;
    public static final ModConfigSpec.IntValue REGEN_SECONDS;

    // ---- needs ----
    public static final ModConfigSpec.BooleanValue NEEDS_USE_DAYTIME;
    public static final ModConfigSpec.DoubleValue HUNGER_PER_DAY;
    public static final ModConfigSpec.DoubleValue FATIGUE_PER_DAY;
    public static final ModConfigSpec.DoubleValue SLEEP_RECOVERY_PER_DAY;
    public static final ModConfigSpec.DoubleValue ATTENTION_DECAY_PER_DAY;
    public static final ModConfigSpec.DoubleValue PROXIMITY_ATTENTION_CAP;
    public static final ModConfigSpec.DoubleValue LONELY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue AFFECTIONATE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue FOOD_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOOD_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOOD_EXTRA;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DRINKS;

    // ---- relationship / mood ----
    public static final ModConfigSpec.DoubleValue PROGRESSION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NEGATIVE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue REPEAT_DIMINISHING;
    public static final ModConfigSpec.DoubleValue MOOD_SENSITIVITY;
    public static final ModConfigSpec.BooleanValue MOOD_RECOVERY;

    // ---- cycle ----
    public static final ModConfigSpec.BooleanValue CYCLE_ENABLED;
    public static final ModConfigSpec.EnumValue<CycleClockMode> CYCLE_CLOCK;
    public static final ModConfigSpec.DoubleValue CYCLE_LENGTH_DAYS;
    public static final ModConfigSpec.DoubleValue CYCLE_MENSTRUAL_DAYS;
    public static final ModConfigSpec.DoubleValue CYCLE_FOLLICULAR_DAYS;
    public static final ModConfigSpec.DoubleValue CYCLE_OVULATION_DAYS;
    public static final ModConfigSpec.DoubleValue CYCLE_FATIGUE_MULT;
    public static final ModConfigSpec.DoubleValue CYCLE_HUNGER_MULT;
    public static final ModConfigSpec.DoubleValue CYCLE_MOOD_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue CYCLE_COMFORT_SEEKING;
    public static final ModConfigSpec.BooleanValue CYCLE_SHOW_IN_STATUS;

    // ---- interactions ----
    public static final ModConfigSpec.DoubleValue INTERACTION_DISTANCE;
    public static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ROMANTIC_INITIATIVE;
    public static final ModConfigSpec.IntValue INITIATIVE_MIN_SECONDS;
    public static final ModConfigSpec.BooleanValue LEAD_TO_BED;
    public static final ModConfigSpec.BooleanValue SIT_ON_RIGHT_CLICK;
    public static final ModConfigSpec.BooleanValue SIT_ON_ANY_BLOCK;

    // ---- requests ----
    public static final ModConfigSpec.BooleanValue REQUESTS_ENABLED;
    public static final ModConfigSpec.IntValue REQUESTS_PER_DAY;
    public static final ModConfigSpec.IntValue REQUEST_GAP_TICKS;
    public static final ModConfigSpec.IntValue REQUEST_EXPIRY_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("General settings").push("general");
        MAX_PER_PLAYER = b.comment("How many companions a single player may own at once (prevents duplicates).")
                .defineInRange("maxCompanionsPerPlayer", 1, 1, 8);
        RESTORE_IDENTITY = b.comment("If a companion is lost (death, missing entity), the next spawn egg the owner uses restores her identity, memories and relationship instead of creating a stranger.")
                .define("restoreIdentityOnRespawn", true);
        NAMES = b.comment("Random names used when a new companion is created. Players can rename her afterwards with a name tag.")
                .defineListAllowEmpty("names", List.of("Aria", "Luna", "Mia", "Elena", "Sofia", "Hana", "Lily", "Nora", "Iris", "Clara", "Yuna", "Ivy"), () -> "Aria", o -> o instanceof String s && !s.isBlank() && s.length() <= 24);
        OUTFIT_COUNT = b.comment("Number of outfits that can be picked (everyday, beach, sleep).")
                .defineInRange("outfitVariants", 3, 1, 3);
        AUTO_SLEEPWEAR = b.comment("She changes into her sleep outfit when she goes to bed and back into what she wore when she wakes up.")
                .define("autoSleepwear", true);
        b.pop();

        b.comment("Base personality. Each new companion gets these values with some random variation.").push("personality");
        TRAIT_AFFECTION = b.comment("How openly loving she is.").defineInRange("affection", 0.7, 0.0, 1.0);
        TRAIT_SHYNESS = b.comment("How easily she gets flustered.").defineInRange("shyness", 0.5, 0.0, 1.0);
        TRAIT_PLAYFULNESS = b.comment("How teasing and playful she is.").defineInRange("playfulness", 0.6, 0.0, 1.0);
        TRAIT_INDEPENDENCE = b.comment("How much she enjoys doing her own thing.").defineInRange("independence", 0.4, 0.0, 1.0);
        TRAIT_CONFIDENCE = b.comment("How brave and self-assured she is.").defineInRange("confidence", 0.45, 0.0, 1.0);
        TRAIT_SENSITIVITY = b.comment("How strongly events affect her feelings.").defineInRange("sensitivity", 0.6, 0.0, 1.0);
        TRAIT_RANDOMNESS = b.comment("Random variation applied to each trait when she is created.").defineInRange("randomVariation", 0.2, 0.0, 0.5);
        b.pop();

        b.comment("Her conversational brain. BUILTIN runs a small open language model (Qwen2.5 Instruct, Apache-2.0) inside the game",
                "on your CPU: no setup or account, works offline after a one-time download, never blocks the game. The scripted",
                "dialogue is always used as a fallback. API keys for external providers are NEVER stored in this file: put the key",
                "in the environment variable named by apiKeyEnvVar, or in config/aigirlfriend/secrets.properties as api_key=...").push("ai");
        AI_BRAIN = b.comment("BUILTIN (default), OLLAMA (local server), OPENAI_COMPATIBLE (LM Studio, llama.cpp server, OpenAI, OpenRouter, ...),",
                "ANTHROPIC, or OFF (scripted lines only, no network).").defineEnum("brain", Brain.BUILTIN);
        AI_BUILTIN_MODEL = b.comment("Built-in model: AUTO (SMART on computers with 12+ CPU threads, else SMALL), SMALL (Qwen2.5 0.5B, 0.7 GB",
                "download, fast but simple), SMART (Qwen2.5 1.5B, 1.1 GB, much better answers, about 3x slower), or CUSTOM (any",
                "Qwen2-family ChatML GGUF file at builtinModelPath).").defineEnum("builtinModel", BuiltinModel.AUTO);
        AI_BUILTIN_PATH = b.comment("GGUF file for builtinModel = CUSTOM (absolute, or relative to the game directory).").define("builtinModelPath", "");
        AI_BUILTIN_DOWNLOAD = b.comment("Download the built-in model automatically the first time she needs it (one time, from huggingface.co,",
                "checksum verified, saved in <game dir>/aigirlfriend/models). If false, use /gf ai download.").define("builtinAutoDownload", true);
        AI_BUILTIN_THREADS = b.comment("CPU threads used while she is thinking (0 = automatic: half of your cores).").defineInRange("builtinThreads", 0, 0, 64);
        AI_BUILTIN_CONTEXT = b.comment("Conversation memory of the built-in model in tokens (more = longer chats remembered, more RAM).")
                .defineInRange("builtinContextTokens", 2048, 512, 8192);
        AI_ENDPOINT = b.comment("Base URL of the provider. Ollama: http://localhost:11434  OpenAI-compatible: http://localhost:1234/v1 or https://api.openai.com/v1  Anthropic: https://api.anthropic.com")
                .define("endpoint", "http://localhost:11434");
        AI_MODEL = b.comment("Model name, e.g. llama3.1:8b for Ollama, or claude-opus-5-5 for Anthropic.").define("model", "llama3.1:8b");
        AI_API_KEY_ENV = b.comment("Name of the environment variable holding the API key (local providers usually need none).").define("apiKeyEnvVar", "AIGIRLFRIEND_API_KEY");
        AI_TIMEOUT_SECONDS = b.comment("Hard time limit for one request. Requests never block the game thread.").defineInRange("timeoutSeconds", 15, 2, 120);
        AI_MAX_RESPONSE_CHARS = b.comment("Replies longer than this are trimmed at a sentence boundary.").defineInRange("maxResponseChars", 260, 40, 1000);
        AI_MAX_TOKENS = b.comment("Max output tokens requested from the provider.").defineInRange("maxTokens", 1024, 32, 8192);
        AI_TEMPERATURE = b.comment("Sampling temperature (ignored for providers that do not accept it).").defineInRange("temperature", 0.85, 0.0, 2.0);
        AI_ANTHROPIC_EFFORT = b.comment("Anthropic effort level: low, medium, high. Low is best for short chat replies.").define("anthropicEffort", "low");
        AI_MAX_CONCURRENT = b.comment("Max simultaneous AI requests for the whole server.").defineInRange("maxConcurrentRequests", 2, 1, 16);
        AI_MIN_SECONDS_BETWEEN = b.comment("Minimum seconds between AI requests for one companion.").defineInRange("minSecondsBetweenRequests", 3, 0, 120);
        AI_RETRIES = b.comment("How many times an invalid/failed response is retried before using local dialogue.").defineInRange("retries", 1, 0, 3);
        AI_FOR_AMBIENT = b.comment("Also use AI for lines she says on her own (more requests).").define("useForAmbientLines", false);
        AI_CACHE_SIZE = b.comment("Number of recent AI replies cached for reuse in identical situations.").defineInRange("cacheSize", 64, 0, 1024);
        AI_CONTEXT_MEMORIES = b.comment("How many memories are included in the AI context.").defineInRange("contextMemories", 6, 0, 24);
        b.pop();

        b.comment("Dialogue behaviour").push("dialogue");
        DIALOGUE_FREQUENCY = b.comment("Multiplier for how often she talks on her own. 0 = only when spoken to.").defineInRange("frequency", 1.0, 0.0, 4.0);
        DIALOGUE_MIN_SECONDS = b.comment("Minimum seconds between lines she starts herself.").defineInRange("minSecondsBetweenLines", 75, 10, 3600);
        LISTEN_TO_CHAT = b.comment("Talk to her through normal chat. When no other player is near, or you talked in the last minute,",
                "everything you say nearby is for her; otherwise include her name (or start with @name).").define("listenToChat", true);
        CHAT_LISTEN_RADIUS = b.defineInRange("chatListenRadius", 20.0, 2.0, 128.0);
        SPEECH_RADIUS = b.comment("Players within this distance see her speech.").defineInRange("speechRadius", 24.0, 4.0, 128.0);
        SPEECH_IN_CHAT = b.comment("Also print her lines in the chat log.").define("speechInChat", true);
        HISTORY_LENGTH = b.comment("Conversation lines remembered word-for-word for context.").defineInRange("conversationHistory", 12, 2, 64);
        b.pop();

        b.push("memory");
        MAX_MEMORIES = b.comment("Maximum long-term memories kept per companion. Least important/oldest are forgotten first.").defineInRange("maxMemories", 64, 8, 512);
        b.pop();

        b.comment("Following and navigation").push("follow");
        FOLLOW_START = b.comment("She starts walking to you when farther than this.").defineInRange("followStartDistance", 6.0, 2.0, 32.0);
        FOLLOW_STOP = b.comment("Comfortable distance she settles at.").defineInRange("followStopDistance", 2.5, 1.0, 16.0);
        MOVE_SPEED = b.defineInRange("movementSpeed", 0.3, 0.1, 0.6);
        TELEPORT_RECOVERY = b.comment("Teleport to the owner when hopelessly separated or stuck.").define("teleportRecovery", true);
        TELEPORT_DISTANCE = b.defineInRange("teleportDistance", 28.0, 8.0, 128.0);
        STUCK_SECONDS = b.comment("Seconds without progress before stuck recovery kicks in.").defineInRange("stuckSeconds", 6, 2, 60);
        FOLLOW_DIMENSIONS = b.comment("Follow the owner through portals when close to them.").define("followThroughDimensions", true);
        MAX_SAFE_FALL = b.comment("Highest drop she is willing to path down.").defineInRange("maxSafeFall", 3, 1, 10);
        b.pop();

        b.comment("Fear, danger and health").push("danger");
        FEAR_RADIUS = b.defineInRange("fearRadius", 16.0, 4.0, 48.0);
        HOSTILES_TARGET_HER = b.comment("Hostile mobs may attack her (she is vulnerable and needs protection).").define("hostilesTargetHer", true);
        MAX_HEALTH = b.defineInRange("maxHealth", 16.0, 4.0, 100.0);
        CRITICAL_HEALTH = b.comment("Fraction of max health below which she retreats and asks for help.").defineInRange("criticalHealthFraction", 0.35, 0.05, 0.9);
        DEFENSE_COOLDOWN = b.comment("Seconds between her defensive shoves.").defineInRange("defensiveCooldownSeconds", 8, 1, 120);
        DEFENSE_DAMAGE = b.defineInRange("defensiveDamage", 1.0, 0.0, 10.0);
        FAINT_INSTEAD_OF_DEATH = b.comment("Lethal damage knocks her out instead of killing her. She is not invincible: she cannot act while fainted and is upset afterwards.")
                .define("faintInsteadOfDeath", true);
        FAINT_SECONDS = b.defineInRange("faintSeconds", 30, 5, 600);
        ENVIRONMENT_RESCUE = b.comment("Rescue her from unfair environmental deaths (void, suffocation, lava while following) by moving her to safety.").define("environmentalRescue", true);
        REGEN_SECONDS = b.comment("Seconds per half-heart of natural regeneration while calm and fed.").defineInRange("regenSeconds", 8, 1, 300);
        b.pop();

        b.comment("Hunger, energy and attention (0-100 scales)").push("needs");
        NEEDS_USE_DAYTIME = b.comment("Measure needs with the world day clock, so skipping the night by sleeping counts as time passing.").define("useDayTimeClock", true);
        HUNGER_PER_DAY = b.comment("Hunger gained per Minecraft day.").defineInRange("hungerPerDay", 55.0, 0.0, 500.0);
        FATIGUE_PER_DAY = b.comment("Energy lost per Minecraft day spent awake (activity increases this).").defineInRange("fatiguePerDay", 60.0, 0.0, 500.0);
        SLEEP_RECOVERY_PER_DAY = b.comment("Energy restored per Minecraft day of sleep (a skipped night restores roughly half of this).").defineInRange("sleepRecoveryPerDay", 220.0, 1.0, 2000.0);
        ATTENTION_DECAY_PER_DAY = b.defineInRange("attentionDecayPerDay", 45.0, 0.0, 500.0);
        PROXIMITY_ATTENTION_CAP = b.comment("Just being near you can raise attention up to this value, but not higher.").defineInRange("proximityAttentionCap", 45.0, 0.0, 100.0);
        LONELY_THRESHOLD = b.defineInRange("lonelyThreshold", 22.0, 0.0, 100.0);
        AFFECTIONATE_THRESHOLD = b.defineInRange("affectionateThreshold", 72.0, 0.0, 100.0);
        FOOD_MULTIPLIER = b.comment("Hunger removed per food nutrition point.").defineInRange("foodNutritionMultiplier", 6.0, 0.5, 50.0);
        FOOD_BLACKLIST = b.comment("Food items she refuses.").defineListAllowEmpty("foodBlacklist",
                List.of("minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato", "minecraft:pufferfish", "minecraft:chorus_fruit"), () -> "minecraft:apple", o -> o instanceof String);
        FOOD_EXTRA = b.comment("Extra items she treats as food, as id=nutrition (e.g. minecraft:sugar=1).").defineListAllowEmpty("extraFoods", List.of(), () -> "minecraft:sugar=1", o -> o instanceof String);
        DRINKS = b.comment("Items she can drink (potions are always drinkable).").defineListAllowEmpty("drinks",
                List.of("minecraft:potion", "minecraft:milk_bucket", "minecraft:honey_bottle"), () -> "minecraft:potion", o -> o instanceof String);
        b.pop();

        b.comment("Relationship and mood").push("relationship");
        PROGRESSION_MULTIPLIER = b.comment("Scales all affection/trust gains.").defineInRange("progressionMultiplier", 1.0, 0.05, 20.0);
        NEGATIVE_MULTIPLIER = b.comment("Scales all affection/trust losses.").defineInRange("negativeMultiplier", 1.0, 0.0, 5.0);
        REPEAT_DIMINISHING = b.comment("How strongly repeating the same interaction on the same day loses value (0 = never).").defineInRange("repeatDiminishing", 0.6, 0.0, 5.0);
        MOOD_SENSITIVITY = b.comment("Scales how strongly events move her mood.").defineInRange("moodSensitivity", 1.0, 0.1, 3.0);
        MOOD_RECOVERY = b.comment("Allow a bad daily mood to be lifted during the day by good treatment.").define("allowMidDayRecovery", true);
        b.pop();

        b.comment("Optional, non-explicit menstrual-cycle simulation. Uses world time, survives reloads.").push("cycle");
        CYCLE_ENABLED = b.define("enabled", true);
        CYCLE_CLOCK = b.comment("DAY_TIME: measured by the world day counter (sleeping through the night advances it).",
                "GAME_TIME: measured by ticks actually simulated (sleeping does not skip it).").defineEnum("clock", CycleClockMode.DAY_TIME);
        CYCLE_LENGTH_DAYS = b.comment("Total cycle length in Minecraft days.").defineInRange("cycleLengthDays", 3.0, 1.0, 60.0);
        CYCLE_MENSTRUAL_DAYS = b.comment("Length of the menstrual phase in days.").defineInRange("menstrualDays", 1.0, 0.1, 30.0);
        CYCLE_FOLLICULAR_DAYS = b.comment("Follicular phase length (the remaining time after menstrual/follicular/ovulation is the luteal phase).").defineInRange("follicularDays", 0.75, 0.0, 30.0);
        CYCLE_OVULATION_DAYS = b.defineInRange("ovulationDays", 0.25, 0.0, 30.0);
        CYCLE_FATIGUE_MULT = b.comment("Fatigue multiplier during the menstrual phase.").defineInRange("menstrualFatigueMultiplier", 1.25, 1.0, 3.0);
        CYCLE_HUNGER_MULT = b.defineInRange("menstrualHungerMultiplier", 1.1, 1.0, 3.0);
        CYCLE_MOOD_SENSITIVITY = b.comment("Extra mood sensitivity during the menstrual phase (added to 1.0).").defineInRange("menstrualMoodSensitivity", 0.15, 0.0, 1.0);
        CYCLE_COMFORT_SEEKING = b.comment("How much more she seeks comfort, warmth and rest (0 disables the behaviour change).").defineInRange("comfortSeeking", 1.0, 0.0, 3.0);
        CYCLE_SHOW_IN_STATUS = b.comment("Allow owners to see the cycle phase in the status screen (they must also enable it client-side).").define("allowStatusDisplay", true);
        b.pop();

        b.comment("Interactions and sitting").push("interactions");
        INTERACTION_DISTANCE = b.defineInRange("maxInteractionDistance", 4.5, 2.0, 12.0);
        COOLDOWN_MULTIPLIER = b.comment("Scales every interaction cooldown.").defineInRange("cooldownMultiplier", 1.0, 0.0, 10.0);
        ROMANTIC_INITIATIVE = b.comment("Once romantic, she starts hugs, kisses, hand-holding and cuddles herself instead of asking.").define("romanticInitiative", true);
        INITIATIVE_MIN_SECONDS = b.comment("Minimum seconds between affection she starts herself.").defineInRange("initiativeMinSeconds", 150, 20, 3600);
        LEAD_TO_BED = b.comment("At bedtime she takes your hand and leads you to bed (romantic stage+). Sneak to let go.").define("leadToBed", true);
        SIT_ON_RIGHT_CLICK = b.comment("Right-click stairs, slabs, carpets or beds with an empty hand to sit.").define("sitOnRightClick", true);
        SIT_ON_ANY_BLOCK = b.comment("The sit keybind lets you sit on top of any block you look at.").define("sitOnAnyBlock", true);
        b.pop();

        b.comment("Requests she makes").push("requests");
        REQUESTS_ENABLED = b.define("enabled", true);
        REQUESTS_PER_DAY = b.defineInRange("maxPerDay", 2, 0, 12);
        REQUEST_GAP_TICKS = b.comment("Minimum ticks between requests.").defineInRange("minGapTicks", 7000, 600, 240000);
        REQUEST_EXPIRY_TICKS = b.defineInRange("expiryTicks", 12000, 1200, 240000);
        b.pop();

        SPEC = b.build();
    }

    private GFConfig() {}

    public enum Brain { BUILTIN, OLLAMA, OPENAI_COMPATIBLE, ANTHROPIC, OFF }

    public enum BuiltinModel { AUTO, SMALL, SMART, CUSTOM }

    public enum CycleClockMode { DAY_TIME, GAME_TIME }
}
