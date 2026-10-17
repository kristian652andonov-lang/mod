package com.aigirlfriend.ai;

import com.aigirlfriend.companion.ConversationHistory;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.Emotion;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueContext;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.ai.local.LocalChat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds her prompts: a compact system prompt (stable character definition) and user prompt (current situation)
 * for external providers, and a multi-turn chat for the built-in model whose system prompt and earlier turns stay
 * identical between requests, so they are only processed once.
 */
public final class PromptBuilder {
    private PromptBuilder() {}

    /**
     * Messages for the built-in model: a stable persona, the recent conversation as alternating turns, and the
     * player's new message with a short note about the situation. Earlier turns are rendered exactly as they were
     * when first sent (situation notes included), so the model's cache of them stays valid and only the new
     * message has to be read.
     */
    public static List<LocalChat.Message> chat(DialogueContext c, @Nullable String playerMessage) {
        String key = c.name + "|" + c.playerName;
        List<ConversationHistory.Line> lines = new ArrayList<>(c.history);
        long nowLine = -1;
        // the current message was already added to the history
        if (playerMessage != null && !lines.isEmpty() && lines.get(lines.size() - 1).fromPlayer()
                && lines.get(lines.size() - 1).text().equals(playerMessage)) {
            nowLine = lines.remove(lines.size() - 1).gameTime();
        }
        boolean[] noted = new boolean[1];
        List<LocalChat.Message> out = turns(c, key, lines, noted);
        LocalChat.Role last = out.size() > 1 ? out.get(out.size() - 1).role() : LocalChat.Role.SYSTEM;
        String pending = "";
        if (last == LocalChat.Role.USER) pending = out.remove(out.size() - 1).text() + "\n"; // player lines she has not answered yet
        String situation = situation(c);
        // repeat the situation only when it changed (and is still visible in the window)
        String note = noted[0] && situation.equals(LAST_NOTE.get(key)) ? "" : "(" + situation + ")\n";
        if (playerMessage != null) {
            LAST_NOTE.put(key, situation);
            if (nowLine >= 0 && !note.isEmpty()) NOTES.put(key + "|" + nowLine, note);
            out.add(new LocalChat.Message(LocalChat.Role.USER, pending + note + c.playerName + ": " + playerMessage));
        } else {
            out.add(new LocalChat.Message(LocalChat.Role.USER, pending + note + "(" + c.playerName + " is listening. " + c.name
                    + " says something on her own: " + describe(c) + ")"));
        }
        return out;
    }

    /** The conversation so far without a new message: what {@link #chat} will start with next time (for read-ahead). */
    public static List<LocalChat.Message> chatSoFar(DialogueContext c) {
        return turns(c, c.name + "|" + c.playerName, new ArrayList<>(c.history), new boolean[1]);
    }

    private static List<LocalChat.Message> turns(DialogueContext c, String key, List<ConversationHistory.Line> all, boolean[] noted) {
        List<LocalChat.Message> out = new ArrayList<>();
        out.add(new LocalChat.Message(LocalChat.Role.SYSTEM, persona(c)));
        LocalChat.Role last = null;
        StringBuilder turn = new StringBuilder();
        for (ConversationHistory.Line l : window(key, all)) {
            LocalChat.Role role = l.fromPlayer() ? LocalChat.Role.USER : LocalChat.Role.ASSISTANT;
            String note = l.fromPlayer() ? NOTES.getOrDefault(key + "|" + l.gameTime(), "") : "";
            noted[0] |= !note.isEmpty();
            String text = l.fromPlayer() ? note + c.playerName + ": " + l.text() : l.text();
            if (role != last && last != null) {
                out.add(new LocalChat.Message(last, turn.toString()));
                turn.setLength(0);
            }
            if (!turn.isEmpty()) turn.append(role == LocalChat.Role.USER ? "\n" : " ");
            turn.append(text);
            last = role;
        }
        if (last != null) out.add(new LocalChat.Message(last, turn.toString()));
        return out;
    }

    /** Situation notes as they were sent with each player line, and the latest one per conversation. */
    private static final Map<String, String> NOTES = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 256;
        }
    });
    private static final Map<String, String> LAST_NOTE = new ConcurrentHashMap<>();

    private static final Map<String, Long> WINDOW_START = new ConcurrentHashMap<>();

    /**
     * The earlier turns shown to the built-in model. The window keeps the same first line while it grows and then
     * jumps forward in one step (instead of sliding every turn), so the cached prompt prefix stays valid.
     */
    private static List<ConversationHistory.Line> window(String key, List<ConversationHistory.Line> lines) {
        if (lines.isEmpty()) return lines;
        Long start = WINDOW_START.get(key);
        int from = -1;
        if (start != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).gameTime() == start) {
                    from = i;
                    break;
                }
            }
        }
        if (from < 0 || lines.size() - from > 14) {
            from = Math.max(0, lines.size() - 6);
            WINDOW_START.put(key, lines.get(from).gameTime());
        }
        return lines.subList(from, lines.size());
    }

    /** Stable character definition for the built-in model (changes only with name, relationship or personality). */
    private static String persona(DialogueContext c) {
        String n = c.name, p = c.playerName;
        return "You are " + n + ", a young woman who lives in a Minecraft world with " + p + ". Your relationship: " + stageDescription(c) + ". "
                + "Personality: " + c.personality.describe() + ". You love " + c.favoriteActivity + ". You get scared of monsters, especially at night.\n"
                + "You are a real person, not an assistant: never say you are an AI, a bot, a program or a language model, and never offer help like an assistant.\n"
                + "Reply to " + p + " as " + n + ", like talking out loud: 1 or 2 short, natural sentences, warm and casual. Have your own opinions, "
                + "feelings and ideas, suggest things, tease a little, and only sometimes ask a question back. Answer what " + p + " actually said; "
                + "if they ask something factual, answer it simply and correctly in your own words. Lines in parentheses describe the situation: "
                + "use them, never repeat them. Keep romance sweet and innocent. No emojis, no lists, no stage directions.\n"
                + "Example:\n" + p + ": what should we do?\n" + n + ": Let's build a little cottage by the river, I already picked flowers for the garden!";
    }

    /** One-line, third-person description of the moment for the built-in model. */
    private static String situation(DialogueContext c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.partOfDay).append(", ").append(c.weather).append(", in a ").append(c.biome).append(". ");
        sb.append(c.name).append(" feels ").append(c.mood.word());
        if (!c.moodCauses.isEmpty()) sb.append(" (").append(String.join("; ", c.moodCauses)).append(")");
        if (c.emotion != Emotion.NONE) sb.append(", right now ").append(c.emotion.name().toLowerCase());
        if (c.hunger >= 55f) sb.append(", hungry");
        if (c.energy <= 35f) sb.append(", sleepy");
        if (c.healthFraction < 0.5f) sb.append(", hurt");
        sb.append('.');
        if (c.fear > 0.4f) sb.append(" She is frightened").append(c.threat != null ? " of a " + c.threat : "").append('.');
        if (c.protectedRecently) sb.append(" ").append(c.playerName).append(" just protected her from monsters.");
        if (c.giftRecently) sb.append(" ").append(c.playerName).append(" just gave her a gift.");
        if (c.fedRecently) sb.append(" ").append(c.playerName).append(" just fed her.");
        if (c.request != null) sb.append(" She asked them to ").append(c.request.toLowerCase().replace('_', ' ')).append('.');
        if (!c.memories.isEmpty()) {
            // memories are written from her side ("you" = the player), so quote them
            sb.append(" Her memories, in her words: \"").append(String.join("\", \"", c.memories.subList(0, Math.min(3, c.memories.size())))).append("\".");
        }
        if (!c.openPromises.isEmpty()) sb.append(" Promises not kept yet, in her words: ").append(String.join("; ", c.openPromises)).append('.');
        return sb.toString();
    }

    public static String system(DialogueContext c) {
        int maxChars = GFConfig.AI_MAX_RESPONSE_CHARS.get();
        return "You are " + c.name + ", a young woman living in a Minecraft world as the companion of " + c.playerName + ". "
                + "Your relationship with them: " + stageDescription(c) + ". "
                + "Your personality: " + c.personality.describe() + ". Your core identity stays the same whatever your mood. "
                + "You love " + c.favoriteActivity + ". You get scared of monsters and rely on " + c.playerName + " for protection.\n"
                + "Rules:\n"
                + "- Speak only as " + c.name + ", in first person, casual and natural, like talking out loud. 1-2 short sentences, under " + maxChars + " characters.\n"
                + "- React to the situation and your feelings given below; vary your wording and sometimes ask a follow-up question.\n"
                + "- Only mention past events listed under 'Memories'. Never invent shared memories.\n"
                + "- Never mention being an AI, a model, a game, mods, stats, numbers or 'relationship stages'.\n"
                + "- You cannot run commands or change the world; just talk.\n"
                + "- Keep affection sweet and non-explicit (no sexual content).\n"
                + "- No emojis, no lists, no narration of what you will say. Light *actions* like *smiles* are fine, at most one.\n"
                + "- Optionally start with one emotion tag in square brackets from: joy, love, sadness, fear, embarrassment, annoyance, surprise, relief, pride, worry, tiredness, discomfort.";
    }

    private static String stageDescription(DialogueContext c) {
        return switch (c.stage) {
            case ACQUAINTANCE -> "you only recently met and are still a bit unsure of each other";
            case FRIENDSHIP -> "you are friends";
            case GROWING_AFFECTION -> "you have a crush on them and things are getting closer, but you are not officially together";
            case ROMANTIC -> "you are their girlfriend and in love";
            case DEEP_TRUST -> "you are their girlfriend, deeply in love and trust them completely";
            case PARTNERSHIP -> "you are their devoted long-term partner and plan your whole future together";
        };
    }

    public static String user(DialogueContext c, @Nullable String playerMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Situation: ").append(playerMessage != null ? c.playerName + " says to you: \"" + playerMessage + "\"" : describe(c)).append('\n');
        sb.append("Time: day ").append(c.day).append(", ").append(c.clock).append(" (").append(c.partOfDay).append("), weather: ").append(c.weather)
                .append(", place: ").append(c.biome).append(".\n");
        sb.append("You feel: mood ").append(c.mood.word());
        if (!c.moodCauses.isEmpty()) sb.append(" (because: ").append(String.join("; ", c.moodCauses)).append(")");
        if (c.emotion != Emotion.NONE) sb.append(", right now ").append(c.emotion.name().toLowerCase());
        sb.append(". Hunger: ").append(c.hungerWord()).append(". Energy: ").append(c.energyWord())
                .append(". Feeling ").append(c.attentionWord()).append(".");
        if (c.healthFraction < 0.5f) sb.append(" You are hurt.");
        if (c.sick) sb.append(" You feel unwell.");
        if (c.fear > 0.4f) sb.append(" You are frightened").append(c.threat != null ? " of a nearby " + c.threat : "").append(".");
        else if (c.fear > 0.15f) sb.append(" You feel uneasy.");
        if (c.cycle == CyclePhase.MENSTRUAL) sb.append(" You are on your period: mildly crampy and tired, you appreciate comfort, warmth and snacks (mention only if it fits).");
        sb.append('\n');
        sb.append("Together for ").append(c.daysTogether).append(" days.");
        if (c.protectedRecently) sb.append(" They protected you from monsters a moment ago.");
        if (c.giftRecently) sb.append(" They just gave you a gift.");
        if (c.fedRecently) sb.append(" They just fed you.");
        if (c.scaredRecently && c.fear < 0.15f) sb.append(" You had a scare a little while ago.");
        if (c.favoriteRevealed != null) sb.append(" They know you love ").append(c.favoriteRevealed).append(".");
        if (c.request != null) sb.append(" You asked them: ").append(c.request.toLowerCase().replace('_', ' ')).append(".");
        sb.append('\n');
        if (!c.memories.isEmpty()) {
            sb.append("Memories (the only past events you may mention):\n");
            for (String m : c.memories) sb.append("- ").append(m).append('\n');
        }
        if (c.reminisceSummary() != null) sb.append("Bring up this memory: ").append(c.reminisceSummary()).append('\n');
        if (!c.openPromises.isEmpty()) sb.append("Open promises they made: ").append(String.join("; ", c.openPromises)).append('\n');
        if (!c.history.isEmpty()) {
            sb.append("Recent conversation:\n");
            for (ConversationHistory.Line l : c.history.subList(Math.max(0, c.history.size() - 8), c.history.size())) sb.append(l.fromPlayer() ? c.playerName : c.name).append(": ").append(l.text()).append('\n');
        }
        sb.append("Reply as ").append(c.name).append(" now.");
        return sb.toString();
    }

    private static String describe(DialogueContext c) {
        DialogueTrigger t = c.trigger;
        String v = c.vars.isEmpty() ? "" : " Details: " + c.vars;
        String s = switch (t) {
            case FIRST_MEETING -> "You just met " + c.playerName + " for the first time. Introduce yourself.";
            case GREETING -> "A player greets you.";
            case RETURN_AFTER_AWAY -> c.playerName + " just came back after being away for " + c.var("away", "a while") + ". You missed them.";
            case WAVE_GOODBYE -> c.playerName + " is leaving. Say goodbye.";
            case HUNGRY, STARVING -> "You are hungry and want to ask for food.";
            case FED -> c.playerName + " just fed you " + c.var("item", "food") + ".";
            case FULL -> c.playerName + " offered you food but you are full.";
            case TIRED, EXHAUSTED -> "You are tired and want to rest" + (c.hasHome ? " or go home" : "") + ".";
            case BEDTIME -> "It is late and you want to go to bed" + (c.romantic() ? " together" : "") + ".";
            case LEAD_TO_BED -> "You take their hand and lead them to bed because it is late.";
            case GOODNIGHT -> "You are lying in bed saying goodnight" + (c.flag("waiting") ? " and inviting them to join you" : "") + ".";
            case GOOD_MORNING -> "You just woke up" + (c.flag("together") ? " next to them" : "") + ".";
            case SLEEP_DECLINED -> "They did not come to bed with you.";
            case SCARED -> "A " + c.var("mob", "monster") + " appeared and frightened you. Ask them to stay close.";
            case ASK_PROTECTION -> "Monsters are still near. Ask them to protect you.";
            case PROTECTED -> "They just killed a " + c.var("mob", "monster") + " that threatened you. You are relieved and grateful.";
            case DANGER_OVER -> "The monsters are gone. You are relieved but shaken.";
            case CELEBRATE -> "You and they just beat the monsters together. Celebrate.";
            case HURT -> "A " + c.var("mob", "monster") + " just hurt you.";
            case DEFENDED -> "You shoved a " + c.var("mob", "monster") + " away from you.";
            case HURT_BY_OWNER -> c.playerName + " just hit you. You are hurt and upset.";
            case CRITICAL -> "You are badly injured and need help.";
            case FAINT_RECOVERED -> "You just woke up after fainting" + (c.flag("owner_here") ? " and they stayed by your side" : "") + ".";
            case IGNORED, LONELY -> "They have been busy and ignoring you for a while. You want attention, gently.";
            case HAPPY -> "Say something cheerful about the moment.";
            case AFFECTIONATE -> "Say something affectionate to them.";
            case SMALL_TALK -> "Make light small talk about the surroundings or ask them something.";
            case REMINISCE -> "You remember something you did together.";
            case MORNING -> "It's a new day. Tell them how you feel today and why.";
            case CYCLE_DISCOMFORT -> "You are on your period and feel a bit crampy/tired. Mention it lightly and maybe ask for comfort.";
            case WEATHER_RAIN -> "It's raining.";
            case WEATHER_THUNDER -> "There is a thunderstorm.";
            case NIGHT_FALLS -> "Night is falling.";
            case LOOK_AT_ANIMAL -> "You notice a cute " + c.var("animal", "animal") + ".";
            case GIFT_LOVED -> "They gave you " + c.var("item", "a gift") + " and you absolutely love it.";
            case GIFT_LIKED -> "They gave you " + c.var("item", "a gift") + " and you like it.";
            case GIFT_OKAY -> "They gave you " + c.var("item", "a gift") + "; it's okay.";
            case GIFT_DISLIKED -> "They offered you " + c.var("item", "something") + " but you don't like " + c.var("category", "that") + "; politely give it back.";
            case GIFT_CONFUSED -> "They handed you " + c.var("item", "something") + " and you don't know what it's for.";
            case MILESTONE -> "Your relationship just grew deeper (" + c.var("stage", "closer") + "). Express it.";
            case RENAMED -> "They gave you a new name: " + c.name + ".";
            case HOME_SET -> "You just decided this place is your home together.";
            case COMMAND_FOLLOW -> "They ask you to follow them.";
            case COMMAND_WAIT -> "They ask you to wait here.";
            case COMMAND_SIT -> "They ask you to sit down.";
            case COMMAND_STAY_SAFE -> "They ask you to stay back and keep safe.";
            case COMMAND_HOME -> c.flag("nohome") ? "They ask you to go home but you don't have a home yet." : "They ask you to go home.";
            case STAND_UP -> "You stand up.";
            case INTERACTION_START -> "You are sharing a " + c.var("action_words", "moment") + " with them.";
            case INTERACTION_SHY -> "They started a " + c.var("action", "hug").toLowerCase().replace('_', ' ') + " and you are flustered.";
            case INITIATIVE -> "You decided on your own to " + c.var("action", "hug").toLowerCase().replace('_', ' ') + " them. Say something sweet while doing it (don't ask permission).";
            case INTERACTION_REFUSED -> "They pulled away from your " + c.var("action", "hug").toLowerCase().replace('_', ' ') + ".";
            case INTERACTION_COOLDOWN -> "They want another kiss/hug right away; tease them gently.";
            case CARRIED -> "They picked you up and are carrying you.";
            case PUT_DOWN -> "They put you down after carrying you.";
            case SIT_TOGETHER -> "You sit down next to them.";
            case REQUEST_ISSUED -> "Ask them for this: " + c.var("request", "").toLowerCase().replace('_', ' ') + (c.var("category", "").isEmpty() ? "" : " (" + c.var("category", "") + ")") + ".";
            case REQUEST_ACCEPTED -> "They agreed to help with your request.";
            case REQUEST_DECLINED -> "They declined your request.";
            case REQUEST_DONE -> "They did what you asked. Thank them.";
            case REQUEST_FAILED -> "They promised to help with your request but didn't.";
            default -> "Say something fitting.";
        };
        return s + v;
    }
}
