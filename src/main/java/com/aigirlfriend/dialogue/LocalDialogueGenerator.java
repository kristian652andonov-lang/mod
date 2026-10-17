package com.aigirlfriend.dialogue;

import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.companion.RequestType;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Offline dialogue. Lines are composed from conditional templates (filtered by mood, needs, relationship,
 * personality, time and weather), then varied with personality-specific openers, pet names, follow-up
 * questions, need remarks and real memories. Recently used templates are avoided, so she does not loop.
 * <p>
 * This always works, needs no network and is used whenever the AI provider is disabled, busy or failing.
 */
public final class LocalDialogueGenerator {
    private record T(String text, Predicate<DialogueContext> cond, float weight) {}

    private static final Map<DialogueTrigger, List<T>> LINES = new EnumMap<>(DialogueTrigger.class);
    private static final Map<Intent, List<T>> REPLIES = new EnumMap<>(Intent.class);
    private static final Map<UUID, Deque<Integer>> RECENT = new HashMap<>();
    private static final Predicate<DialogueContext> ANY = c -> true;

    // ---- predicates ----
    private static Predicate<DialogueContext> romantic() { return DialogueContext::romantic; }
    private static Predicate<DialogueContext> notRomantic() { return c -> !c.romantic(); }
    private static Predicate<DialogueContext> early() { return c -> c.stage.ordinal() <= RelationshipStage.FRIENDSHIP.ordinal(); }
    private static Predicate<DialogueContext> close() { return c -> c.stage.atLeast(RelationshipStage.GROWING_AFFECTION); }
    private static Predicate<DialogueContext> deep() { return c -> c.stage.atLeast(RelationshipStage.DEEP_TRUST); }
    private static Predicate<DialogueContext> shy() { return c -> c.personality.shyness > 0.6f; }
    private static Predicate<DialogueContext> playful() { return c -> c.personality.playfulness > 0.6f; }
    private static Predicate<DialogueContext> bold() { return c -> c.personality.confidence > 0.6f; }
    private static Predicate<DialogueContext> independent() { return c -> c.personality.independence > 0.6f; }
    private static Predicate<DialogueContext> sensitive() { return c -> c.personality.sensitivity > 0.6f; }
    private static Predicate<DialogueContext> mood(Mood... moods) {
        return c -> {
            for (Mood m : moods) if (c.mood == m) return true;
            return false;
        };
    }
    private static Predicate<DialogueContext> flag(String key) { return c -> c.flag(key); }
    private static Predicate<DialogueContext> night() { return c -> c.night; }
    private static Predicate<DialogueContext> day() { return c -> !c.night; }
    private static Predicate<DialogueContext> hungry() { return c -> c.hunger >= 55; }
    private static Predicate<DialogueContext> tired() { return c -> c.energy <= 35; }

    private static void add(DialogueTrigger t, String... texts) {
        for (String s : texts) LINES.computeIfAbsent(t, k -> new ArrayList<>()).add(new T(s, ANY, 1f));
    }

    private static void add(DialogueTrigger t, Predicate<DialogueContext> cond, String... texts) {
        for (String s : texts) LINES.computeIfAbsent(t, k -> new ArrayList<>()).add(new T(s, cond, 1.6f));
    }

    private static void reply(Intent i, String... texts) {
        for (String s : texts) REPLIES.computeIfAbsent(i, k -> new ArrayList<>()).add(new T(s, ANY, 1f));
    }

    private static void reply(Intent i, Predicate<DialogueContext> cond, String... texts) {
        for (String s : texts) REPLIES.computeIfAbsent(i, k -> new ArrayList<>()).add(new T(s, cond, 1.6f));
    }

    static {
        // ------------------------------------------------------------ meeting & greetings
        add(DialogueTrigger.FIRST_MEETING,
                "Oh! Hi there. I'm {name}. I don't really know anyone around here... can I stay with you for a while?",
                "Hello! I'm {name}. This world is so big, it feels nice to finally meet someone.",
                "Um, hi! I'm {name}. You look like you know your way around. Mind if I tag along?");
        add(DialogueTrigger.GREETING,
                "Oh, hello! Nice to meet you.",
                "Hi there! Are you a friend of {owner}?",
                "Hey! Lovely {time}, isn't it?");
        add(DialogueTrigger.RETURN_AFTER_AWAY,
                "I missed you! Where have you been? I was starting to wonder when you'd come back.",
                "There you are! It's been {away}. I kept looking around for you.",
                "You're back! I'm so happy to see you.");
        add(DialogueTrigger.RETURN_AFTER_AWAY, romantic(),
                "Finally! Come here, I missed you so much.",
                "{player}! I missed you. Don't disappear for {away} again, okay?",
                "You were gone for {away}... I kept thinking about you the whole time.");
        add(DialogueTrigger.RETURN_AFTER_AWAY, mood(Mood.LONELY, Mood.SAD),
                "You're back... I was getting really lonely without you.");
        add(DialogueTrigger.RETURN_AFTER_AWAY, independent(),
                "Oh, welcome back! I kept myself busy, but it's nicer with you here.");
        add(DialogueTrigger.WAVE_GOODBYE,
                "Bye for now! Come back soon, okay?",
                "See you later! I'll be right here.",
                "Take care out there!");
        add(DialogueTrigger.WAVE_GOODBYE, romantic(),
                "Don't be gone too long, {player}. I'll miss you.",
                "Be careful! Come back to me safe, okay?");

        // ------------------------------------------------------------ needs
        add(DialogueTrigger.HUNGRY,
                "Could we find something to eat? I'm getting really hungry.",
                "My stomach just growled... do you have any food?",
                "I think it's snack time. Do you have anything to eat?",
                "Hey, um... I'm getting kind of hungry.");
        add(DialogueTrigger.HUNGRY, playful(),
                "If I don't eat soon I might start nibbling on the grass. Food, please?");
        add(DialogueTrigger.HUNGRY, mood(Mood.IRRITABLE),
                "Sorry if I'm grumpy. I think I'm just really hungry.");
        add(DialogueTrigger.STARVING,
                "I'm so hungry it's hard to keep walking... please, anything to eat?",
                "I really need food. I'm starting to feel weak.",
                "Can we please stop and eat? I'm starving.");
        add(DialogueTrigger.FED,
                "Mmm, thank you! That was {item_good}.",
                "Thanks! I feel so much better now.",
                "Yum! You always know what I need.");
        add(DialogueTrigger.FED, flag("was_hungry"),
                "Oh, thank you! I was so hungry. That {item} was perfect.",
                "You're a lifesaver. I really needed that.");
        add(DialogueTrigger.FED, romantic(),
                "Thank you, {player}. Feeding me, huh? You spoil me.");
        add(DialogueTrigger.FULL,
                "I'm full, thank you! Maybe later?",
                "I couldn't eat another bite right now. Save it for later?");
        add(DialogueTrigger.DRINK,
                "Thank you! That was refreshing.",
                "Ah, that hit the spot.");
        add(DialogueTrigger.TIRED,
                "I'm getting a bit tired... could we rest soon?",
                "Phew, I'm kind of worn out.",
                "Can we take a little break? My legs are tired.");
        add(DialogueTrigger.TIRED, night(),
                "It's getting late... I'm getting sleepy.");
        add(DialogueTrigger.EXHAUSTED,
                "I'm exhausted... could we go home and rest? I can barely keep my eyes open.",
                "I really need to sleep. Can we head home?",
                "I'm so tired I could fall asleep standing up.");
        add(DialogueTrigger.BEDTIME,
                "It's late... we should get some sleep soon.",
                "Bedtime, I think. Shall we find a bed?");
        add(DialogueTrigger.BEDTIME, romantic(),
                "Come on, it's late. Let's go to bed together.",
                "I'm getting sleepy... you're coming to bed with me, right?");
        add(DialogueTrigger.LEAD_TO_BED,
                "Come on, sleepyhead. Bed time. I'm not letting go of your hand.",
                "This way! Let's go to bed, it's late.",
                "Follow me~ you need sleep just as much as I do.",
                "No more adventures tonight. Come to bed with me.");
        add(DialogueTrigger.GOODNIGHT,
                "Goodnight. Sleep well.",
                "Night night. See you in the morning.");
        add(DialogueTrigger.GOODNIGHT, flag("waiting"),
                "Come lie down with me. It's warm here.",
                "I saved you a spot. Come here, {player}.");
        add(DialogueTrigger.GOODNIGHT, flag("together"),
                "Goodnight, {player}. I'm glad you're here next to me.",
                "Mm... sleep well. I'll be right here.",
                "Sweet dreams. Don't steal the blanket.");
        add(DialogueTrigger.GOOD_MORNING,
                "Good morning! *stretches* I slept so well.",
                "Morning! Ready for a new day?",
                "Mmh... good morning.");
        add(DialogueTrigger.GOOD_MORNING, flag("together"),
                "Good morning, {player}. Waking up next to you is the best.",
                "Morning... five more minutes? Okay, okay, I'm up.",
                "Mm, good morning. You were so warm.");
        add(DialogueTrigger.SLEEP_DECLINED,
                "Okay... I'll go to sleep then. Don't stay up too late.",
                "Not tired yet? Alright. Goodnight then.");
        add(DialogueTrigger.SLEEP_DECLINED, flag("nobed"),
                "I'd love to, but there's no bed nearby. Maybe we should make one?");
        add(DialogueTrigger.SLEEP_DECLINED, playful(),
                "Fine, stay up then! But I'm keeping the warm side of the bed.");

        // ------------------------------------------------------------ fear & protection
        add(DialogueTrigger.SCARED,
                "Wait! There's something over there! Can you stay close to me?",
                "There's a {mob}! Please don't go too far.",
                "Did you hear that? I think there's a {mob} nearby...",
                "I don't like this. There are monsters nearby.");
        add(DialogueTrigger.SCARED, bold(),
                "Heads up, there's a {mob}! Stay sharp.");
        add(DialogueTrigger.ASK_PROTECTION,
                "Can you deal with that {mob}? I don't want to get hurt.",
                "Please don't go too far. There are monsters nearby.",
                "I think we should find somewhere safer.",
                "Stay with me, please! That {mob} is getting closer.");
        add(DialogueTrigger.PROTECTED,
                "That was scary... I'm really glad you were here with me.",
                "You got it! Thank you for protecting me.",
                "My hero... thank you. I was so scared of that {mob}.");
        add(DialogueTrigger.PROTECTED, romantic(),
                "You were amazing. I feel so safe with you, {player}.");
        add(DialogueTrigger.DANGER_OVER,
                "Are they gone? Can we stay together for a little while?",
                "Phew... I think it's safe now.",
                "That was too close. Let's be careful.");
        add(DialogueTrigger.CELEBRATE,
                "We did it! They're gone!",
                "Yay! Nothing can beat us when we're together.",
                "Ha! Take that! ...okay, my heart is still racing.");
        add(DialogueTrigger.HURT,
                "Ow! That {mob} hurt me!",
                "Ah! Help!",
                "Ouch... please help me!");
        add(DialogueTrigger.DEFENDED,
                "Get away from me!",
                "Back off!",
                "Leave me alone, you stupid {mob}!");
        add(DialogueTrigger.CRITICAL,
                "I'm hurt really badly... please help me.",
                "I can't keep going like this... I need to get away and rest.",
                "Please... I need help. I'm really hurt.");
        add(DialogueTrigger.HURT_BY_OWNER,
                "Ow! Why did you hit me?",
                "Hey! That hurt... what was that for?",
                "That really hurt. I thought you cared about me...");
        add(DialogueTrigger.HURT_BY_OWNER, bold(),
                "Excuse me?! Don't do that again.");
        add(DialogueTrigger.FAINT_RECOVERED,
                "Ugh... my head. What happened?",
                "I... I blacked out. I'm okay now, I think.");
        add(DialogueTrigger.FAINT_RECOVERED, flag("owner_here"),
                "You stayed with me... thank you. I'm okay now.",
                "I woke up and you were right here. Thank you.");

        // ------------------------------------------------------------ moods & small talk
        add(DialogueTrigger.HAPPY,
                "Today feels wonderful. I just want to stay here with you.",
                "I'm in such a good mood today!",
                "Isn't it nice out here? I love days like this.",
                "Hehe, I don't know why, but I can't stop smiling.");
        add(DialogueTrigger.HAPPY, day(), "The {biome} is so pretty in the {time}.");
        add(DialogueTrigger.HAPPY, mood(Mood.ENERGETIC), "I have so much energy today! Let's go somewhere new!");
        add(DialogueTrigger.HAPPY, mood(Mood.PLAYFUL), "Race you to that tree! ...Just kidding. Unless?");
        add(DialogueTrigger.AFFECTIONATE,
                "I really like being with you, you know that?",
                "You make everything feel safe.",
                "Hey... thanks for always being here.");
        add(DialogueTrigger.AFFECTIONATE, romantic(),
                "I love you, {player}.",
                "Every day with you is my favorite day.",
                "Come here. I just want to be close to you.",
                "I'm so lucky to have you.");
        add(DialogueTrigger.AFFECTIONATE, shy(), "I... um. I really like you. That's all!");
        add(DialogueTrigger.LONELY,
                "You've been busy for a while. Could we spend a little time together?",
                "I feel a bit lonely today.",
                "Do you have a minute for me?");
        add(DialogueTrigger.IGNORED,
                "You've been busy for a while. Could we spend a little time together?",
                "Hey... you haven't really talked to me in a while.",
                "Am I being annoying? I just miss you a little.");
        add(DialogueTrigger.IGNORED, independent(), "I'll entertain myself, but... talk to me sometime, okay?");
        add(DialogueTrigger.SMALL_TALK,
                "What do you want to do next?",
                "Do you ever wonder what's past the horizon?",
                "I like the sound of the wind out here.",
                "What's your favorite place we've been to so far?",
                "Have you been building anything lately?",
                "If you could live anywhere in this world, where would it be?");
        add(DialogueTrigger.SMALL_TALK, night(),
                "The stars are so bright tonight.",
                "Look at the moon. Isn't it beautiful?");
        add(DialogueTrigger.SMALL_TALK, day(), "It's a good {time} for {activity}, don't you think?");
        add(DialogueTrigger.SMALL_TALK, c -> c.hasHome, "I like our home. It feels like it's really ours.");
        add(DialogueTrigger.SMALL_TALK, playful(), "Bet I can spot a diamond before you do.");
        add(DialogueTrigger.REMINISCE,
                "Do you remember when {memory}? I still think about that.",
                "I was just thinking about when {memory}.",
                "Remember when {memory}? That was really something.");
        add(DialogueTrigger.REMINISCE, romantic(),
                "Hey... remember when {memory}? I'll never forget that.");
        add(DialogueTrigger.MORNING,
                "Good morning! I feel {mood_word} today. {cause}.",
                "Morning! Honestly? I'm feeling {mood_word}. {cause}.");
        add(DialogueTrigger.MORNING, mood(Mood.LONELY, Mood.SAD, Mood.WORRIED, Mood.FRIGHTENED, Mood.IRRITABLE),
                "Morning... I'm not feeling great. {cause}.",
                "Hey. I'm a bit {mood_word} today. {cause}. Maybe you can help?");
        add(DialogueTrigger.CYCLE_DISCOMFORT,
                "I have some cramps today... could we take it a little easy?",
                "I'm on my period, so I'm a bit tired. Some warmth and a snack would be nice.",
                "Today's one of those days. Could you stay close to me?");
        add(DialogueTrigger.CYCLE_DISCOMFORT, romantic(), "Could I have a hug? My tummy hurts a little today.");
        add(DialogueTrigger.CYCLE_DISCOMFORT, independent(), "Just a little crampy today. I'm fine, really.");
        add(DialogueTrigger.WEATHER_RAIN,
                "It's raining... want to find some shelter?",
                "I kind of like the sound of the rain.",
                "We're getting soaked!");
        add(DialogueTrigger.WEATHER_THUNDER,
                "That thunder is so loud... can we go inside?",
                "I don't like thunderstorms. Please stay close.");
        add(DialogueTrigger.NIGHT_FALLS,
                "It's getting dark. Monsters come out at night... let's be careful.",
                "Night already? Time flies when I'm with you.");
        add(DialogueTrigger.NIGHT_FALLS, c -> c.hasHome, "It's getting dark. Should we head home?");
        add(DialogueTrigger.LOOK_AT_ANIMAL,
                "Aww, look at that {animal}!",
                "Hehe, that {animal} is so cute.",
                "Do you think that {animal} has a name?");

        // ------------------------------------------------------------ gifts
        add(DialogueTrigger.GIFT_LOVED,
                "Oh my gosh, a {item}! I love it! Thank you so much!",
                "You remembered I like {category}! This is perfect.",
                "For me? I love it! I'm keeping this forever.");
        add(DialogueTrigger.GIFT_LOVED, shy(), "F-for me? ...Thank you. I really love it.");
        add(DialogueTrigger.GIFT_LIKED,
                "Aww, thank you! That's really sweet of you.",
                "A {item}? Thank you, I like it!",
                "That's so thoughtful. Thank you!");
        add(DialogueTrigger.GIFT_OKAY,
                "Oh, a {item}. Thanks!",
                "Thank you! I'll find a use for it.");
        add(DialogueTrigger.GIFT_DISLIKED,
                "Oh... thank you, but I'm not really into {category}. You can keep it.",
                "That's sweet, but {category} aren't really my thing. Keep it, okay?");
        add(DialogueTrigger.GIFT_DISLIKED, playful(), "Ew, no thank you! Hehe, nice try though.");
        add(DialogueTrigger.GIFT_CONFUSED,
                "A {item}? What would I do with that?",
                "Um... thanks? I'm not sure what to do with a {item}.");

        // ------------------------------------------------------------ relationship
        add(DialogueTrigger.MILESTONE,
                "You know... I feel like we've become really close.",
                "Being with you makes me happy. Really happy.");
        add(DialogueTrigger.MILESTONE, c -> c.var("stage", "").contains("romantic"),
                "I... I think I'm in love with you, {player}.",
                "Hey... can I be yours? Because I really want to be.");
        add(DialogueTrigger.MILESTONE, c -> c.var("stage", "").contains("trust"),
                "I trust you with everything. I hope you know that.");
        add(DialogueTrigger.MILESTONE, c -> c.var("stage", "").contains("partnership"),
                "I want to spend every single day with you. Forever, okay?");
        add(DialogueTrigger.MILESTONE, c -> c.var("stage", "").contains("friendship"),
                "I'm really glad I met you. I think we're friends now!");
        add(DialogueTrigger.RENAMED,
                "{name}? I love it! That's my name now.",
                "{name}... it sounds nice when you say it.");
        add(DialogueTrigger.HOME_SET,
                "Home sweet home! I like it here.",
                "Our home... I like the sound of that.");

        // ------------------------------------------------------------ commands
        add(DialogueTrigger.COMMAND_FOLLOW, "Right behind you!", "Okay, lead the way!", "Coming!");
        add(DialogueTrigger.COMMAND_FOLLOW, romantic(), "Where you go, I go.");
        add(DialogueTrigger.COMMAND_WAIT, "Okay, I'll wait here.", "I'll be right here. Don't take too long!");
        add(DialogueTrigger.COMMAND_WAIT, mood(Mood.NEEDY, Mood.LONELY), "Okay... come back soon, okay?");
        add(DialogueTrigger.COMMAND_SIT, "I'll sit down for a bit.", "Good idea, my feet could use a rest.");
        add(DialogueTrigger.STAND_UP, "Okay, I'm up!", "Alright, let's go.");
        add(DialogueTrigger.COMMAND_STAY_SAFE, "Okay, I'll stay back and keep out of danger.", "I'll keep my distance from the monsters. Be careful!");
        add(DialogueTrigger.COMMAND_HOME, "I'll head home. See you there!", "Going home! I'll wait for you.");
        add(DialogueTrigger.COMMAND_HOME, flag("nohome"), "Home? We don't have one yet... want to make one here?");

        // ------------------------------------------------------------ interactions
        add(DialogueTrigger.INTERACTION_START,
                "Hehe.",
                "Mm...",
                "I like this.");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("HUG"), "Aww, come here!", "I needed a hug.");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").contains("KISS"), "Mwah~", "Hehe... again?");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("HOLD_HANDS"), "Your hand is warm.", "Don't let go, okay?");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").contains("CUDDLE"), "So comfy...", "Let's just stay like this for a while.");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("FOREHEAD_KISS"), "That's so sweet...");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("PET_HAIR"), "Hehe... that feels nice~", "Keep doing that. Please?", "*closes her eyes happily*");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("PET_CHEEK"), "*leans into your hand*", "Your hand is so warm...", "Mm... I like when you do that.");
        add(DialogueTrigger.INTERACTION_START, c -> c.var("action", "").equals("LAP_SIT"), "Is this okay? Hehe, too late~", "Your lap is the comfiest seat in the world.");
        add(DialogueTrigger.INTERACTION_SHY,
                "W-wait, I... okay. Hehe.",
                "Oh! I... um... *blushes*",
                "You're making me blush...");
        add(DialogueTrigger.INITIATIVE,
                "C'mere.",
                "Hehe, gotcha.",
                "I just wanted to be close to you.",
                "Don't mind me~");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("HOLD_HANDS"), "Your hand looked lonely.", "*takes your hand* There. Much better.");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("BACK_HUG"), "Surprise hug!", "Guess who~");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").contains("KISS"), "Just one kiss. Okay, maybe two.", "*kisses you* Hehe.");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("HEAD_ON_SHOULDER"), "*rests her head on your shoulder* Comfy.", "Can I lean on you for a bit?");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").contains("CUDDLE"), "Cuddle time. No arguments.", "Scoot over~");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("SIT_TOGETHER"), "Mind if I sit with you?", "Room for one more?");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("LEAN_ON"), "I'm a little tired... let me lean on you.");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("LAP_SIT"), "*plops down on your lap* Mine now.", "Move over~ actually, don't. I'll just sit here.", "*curls up on your lap* Comfy.");
        add(DialogueTrigger.INITIATIVE, c -> c.var("action", "").equals("PET_HAIR"), "*nudges her head under your hand* Pet me?", "Head pats. Now. Please~");
        add(DialogueTrigger.INTERACTION_REFUSED,
                "Oh... okay. Maybe later.",
                "Not now? That's fine.",
                "Hmph. Fine, but you owe me one.");
        add(DialogueTrigger.INTERACTION_REFUSED, sensitive(), "Oh... sorry. I didn't mean to bother you.");
        add(DialogueTrigger.INTERACTION_COOLDOWN,
                "Hehe, give me a second to breathe!",
                "Again already? You're so sweet. In a moment, okay?");
        add(DialogueTrigger.CARRIED,
                "Whoa! Hehe, don't drop me!",
                "Wheee! Okay, this is nice.",
                "*wraps her arms around your neck* My hero.");
        add(DialogueTrigger.PUT_DOWN, "Thanks for the ride!", "Aww, already?");
        add(DialogueTrigger.SIT_TOGETHER, "Let's sit here for a while.", "It's nice just sitting together.", "Comfy?");

        // ------------------------------------------------------------ requests
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.BRING_FOOD.name()),
                "Could you bring me something to eat? I'm getting hungry.");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.EXPLORE_TOGETHER.name()),
                "Let's go exploring together! I want to see something new.");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.REACH_SAFETY.name()),
                "It's dark and scary out here... can we get somewhere safe? Somewhere with light and a roof?");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.TALK_TOGETHER.name()),
                "Can we just talk for a bit? I want to hear about your day.");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.PREFERRED_GIFT.name()),
                "You know what would make me really happy? Some {category}. Just saying~");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.HOME_BEFORE_NIGHT.name()),
                "It'll be dark soon. Can we get home before night falls?");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.PROTECT_ME.name()),
                "Will you protect me tonight? The monsters make me nervous.");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.SIT_BY_FIRE.name()),
                "I'm a bit cold. Could we sit by a fire together for a while?");
        add(DialogueTrigger.REQUEST_ISSUED, c -> c.var("request", "").equals(RequestType.COMFORT_ME.name()),
                "I'm not feeling great... could I have a hug?");
        add(DialogueTrigger.REQUEST_ACCEPTED, "Really? Thank you!", "Yay! You're the best.", "Okay! I'm counting on you.");
        add(DialogueTrigger.REQUEST_DECLINED, "Oh, okay. Maybe another time.", "That's alright. Don't worry about it.");
        add(DialogueTrigger.REQUEST_DECLINED, sensitive(), "Oh... okay. It's fine, really.");
        add(DialogueTrigger.REQUEST_DECLINED, independent(), "No problem! I'll manage.");
        add(DialogueTrigger.REQUEST_DONE,
                "You did it! Thank you, that means a lot to me.",
                "Thank you! You always come through for me.",
                "That was perfect. Thank you!");
        add(DialogueTrigger.REQUEST_FAILED, "You said you'd help... it's okay. Maybe next time.", "Oh well. I was looking forward to that.");

        // ============================================================ replies to the player
        reply(Intent.GREETING, "Hi! What's up?", "Hey you!", "Hello! I'm right here.");
        reply(Intent.GREETING, romantic(), "Hi, {player}~", "Hey, love. What's on your mind?");
        reply(Intent.FAREWELL, "Bye! Come back soon.", "See you later!");
        reply(Intent.FAREWELL, romantic(), "Bye... I'll miss you.");
        reply(Intent.HOW_ARE_YOU,
                "I'm feeling {mood_word} today. {cause}.",
                "Honestly? {mood_word}. {cause}.");
        reply(Intent.HOW_ARE_YOU, hungry(), "I'm okay, but I'm really hungry. {cause}.");
        reply(Intent.HOW_ARE_YOU, tired(), "A bit tired, to be honest. Otherwise {mood_word}.");
        reply(Intent.HOW_ARE_YOU, c -> c.scaredRecently, "Still a little shaken from those monsters, but I'm okay with you here.");
        reply(Intent.ASK_MOOD_WHY, "{cause}. That's why.", "I think it's because... {cause_lower}.");
        reply(Intent.LOVE, early(), "O-oh! That's... wow. I'm really flattered.", "You can't just say things like that! ...hehe.");
        reply(Intent.LOVE, close(), "I... I really like you too.", "You're making my heart race...");
        reply(Intent.LOVE, romantic(), "I love you too, {player}. So much.", "I love you more!", "I love you. Always.");
        reply(Intent.COMPLIMENT, "Aww, thank you!", "You think so? That's sweet of you.", "Stop it, you're making me blush.");
        reply(Intent.COMPLIMENT, shy(), "M-me? ...Thank you.");
        reply(Intent.COMPLIMENT, bold(), "I know~ But thanks for noticing.");
        reply(Intent.APOLOGY, "It's okay. Thank you for saying sorry.", "Apology accepted. Just... be nicer, okay?");
        reply(Intent.APOLOGY, flag("resolved"), "Thank you for apologizing. I forgive you. Let's put it behind us.");
        reply(Intent.THANKS, "You're welcome!", "Anytime!", "Of course!");
        reply(Intent.ASK_HUNGRY, hungry(), "Yes! I'm really hungry. Do you have food?");
        reply(Intent.ASK_HUNGRY, c -> c.hunger < 55, "I'm {hunger_word}, thanks for asking.");
        reply(Intent.ASK_TIRED, tired(), "Yes... I could really use some rest.");
        reply(Intent.ASK_TIRED, c -> c.energy > 35, "I'm {energy_word}! I can keep going.");
        reply(Intent.ASK_FEELING_BODY, c -> c.cycle == CyclePhase.MENSTRUAL, "I'm on my period, so I'm a bit crampy and tired. A hug and some food would help.", "It's that time of the month. Nothing serious, I just want to take it easy.");
        reply(Intent.ASK_FEELING_BODY, c -> c.cycle != CyclePhase.MENSTRUAL && c.healthFraction < 0.6f, "I'm a bit hurt, but I'll recover.");
        reply(Intent.ASK_FEELING_BODY, c -> c.cycle != CyclePhase.MENSTRUAL && c.healthFraction >= 0.6f, "I feel fine physically! {energy_word_cap}.");
        reply(Intent.ASK_FAVORITE, c -> c.favoriteRevealed != null, "You know I love {fav}! And {activity} with you.");
        reply(Intent.ASK_FAVORITE, c -> c.favoriteRevealed == null, "Hmm, that's a secret. Maybe you'll find out if you pay attention~", "I love {activity}. As for gifts... you'll have to guess.");
        reply(Intent.ASK_REMEMBER, c -> c.reminisceSummary() != null, "Of course I remember. {memory_cap}.", "How could I forget? {memory_cap}.");
        reply(Intent.ASK_REMEMBER, c -> c.reminisceSummary() == null, "Hmm, I'm not sure. We haven't had that many adventures yet.");
        reply(Intent.ASK_ABOUT_HER, "I'm {name}. I like {activity}, and I get scared of monsters easily. And I like you.", "Me? I'm just a girl who wandered into this world and found you.");
        reply(Intent.ASK_RELATIONSHIP, early(), "We're friends, right? I'd like that.", "I'm still getting to know you. But I like where this is going.");
        reply(Intent.ASK_RELATIONSHIP, close(), "I think... we're more than friends. Right?");
        reply(Intent.ASK_RELATIONSHIP, romantic(), "You're my partner, silly. My favorite person in the world.");
        reply(Intent.PROMISE, "I'm holding you to that!", "Promise? Okay. I'll remember.", "Really? You promise? Yay!");
        reply(Intent.PROMISE_KEPT, "You kept your promise! Thank you.", "I knew you would. Thank you for keeping your word.");
        reply(Intent.PLAN, "Yes! Let's do it.", "Sounds fun! I'm in.", "Lead the way!");
        reply(Intent.PLAN, tired(), "Sounds nice, but I'm tired... maybe after a rest?");
        reply(Intent.INSULT, "That's mean...", "Why would you say that?", "That really hurts, you know.");
        reply(Intent.INSULT, bold(), "Wow. Rude.");
        reply(Intent.JOKE, "Why did the creeper break up with the skeleton? Because it found someone more explosive! ...Sorry.",
                "What's a ghast's favorite country? The Nether-lands! Hehe.",
                "Why don't endermen like selfies? They hate being looked at!");
        reply(Intent.GOODNIGHT, "Goodnight! Sleep tight.", "Night! Don't let the phantoms bite.");
        reply(Intent.GOODNIGHT, romantic(), "Goodnight, love. Come to bed with me?");
        reply(Intent.GOOD_MORNING, "Good morning! Did you sleep well?", "Morning, sunshine!");
        reply(Intent.MISS_YOU, "I missed you too!", "Aww, I'm right here now.");
        reply(Intent.MISS_YOU, romantic(), "I missed you more. Come here.");
        reply(Intent.COMMAND, "Okay!", "Got it!", "Sure thing!");
        reply(Intent.ASK_AFFECTION_OK, "Of course. Come here.", "I'd love that.", "Hehe, okay.");
        reply(Intent.ASK_AFFECTION_TOO_SOON, "Maybe when we know each other a little better...", "Hehe, slow down! We just met.", "I... I'm not ready for that yet. Sorry.");
        reply(Intent.YES, "Yay!", "Great!", "Okay!");
        reply(Intent.NO, "Oh, okay.", "Alright, no problem.");
        reply(Intent.QUESTION, "Hmm, good question. What do you think?", "I'm not sure... what made you think of that?", "Ooh, let me think about that.");
        reply(Intent.QUESTION, c -> c.night, "Ask me again in the morning, I'm half asleep.");
        reply(Intent.STATEMENT, "Mhm, I'm listening.", "Really? Tell me more.", "Hehe, I like when you talk to me.", "That's interesting!", "I see what you mean.");
        reply(Intent.STATEMENT, romantic(), "I could listen to you all day.");
    }

    private LocalDialogueGenerator() {}

    public static String line(DialogueContext c, UUID speaker, RandomSource r) {
        List<T> pool = LINES.getOrDefault(c.trigger, LINES.get(DialogueTrigger.SMALL_TALK));
        String base = pick(pool, c, speaker, r);
        if (base == null) base = "...";
        String text = fill(base, c, r);
        return decorate(text, c, r, true);
    }

    public static String reply(DialogueContext c, Intent intent, UUID speaker, RandomSource r) {
        List<T> pool = REPLIES.getOrDefault(intent, REPLIES.get(Intent.STATEMENT));
        String base = pick(pool, c, speaker, r);
        if (base == null) base = pick(REPLIES.get(Intent.STATEMENT), c, speaker, r);
        String text = fill(base, c, r);
        return decorate(text, c, r, intent == Intent.STATEMENT || intent == Intent.QUESTION || intent == Intent.HOW_ARE_YOU);
    }

    private static String pick(List<T> pool, DialogueContext c, UUID speaker, RandomSource r) {
        if (pool == null || pool.isEmpty()) return null;
        Deque<Integer> recent = RECENT.computeIfAbsent(speaker, k -> new ArrayDeque<>());
        List<T> ok = new ArrayList<>();
        float total = 0;
        for (T t : pool) {
            if (!t.cond.test(c)) continue;
            if (recent.contains(t.text.hashCode()) && pool.size() > 2) continue;
            ok.add(t);
            total += t.weight;
        }
        if (ok.isEmpty()) {
            for (T t : pool) if (t.cond.test(c)) { ok.add(t); total += t.weight; }
        }
        if (ok.isEmpty()) return null;
        float p = r.nextFloat() * total;
        T chosen = ok.get(ok.size() - 1);
        for (T t : ok) {
            p -= t.weight;
            if (p <= 0) { chosen = t; break; }
        }
        recent.addLast(chosen.text.hashCode());
        while (recent.size() > 24) recent.removeFirst();
        return chosen.text;
    }

    private static String petName(DialogueContext c, RandomSource r) {
        if (!c.romantic() || c.personality.shyness > 0.75f && r.nextBoolean()) return c.playerName;
        String[] names = c.stage.atLeast(RelationshipStage.DEEP_TRUST)
                ? new String[]{"love", "darling", "sweetheart", "my love", c.playerName, "dear"}
                : new String[]{"love", c.playerName, "sweetie", c.playerName};
        return names[r.nextInt(names.length)];
    }

    private static String fill(String s, DialogueContext c, RandomSource r) {
        String memory = c.reminisceSummary();
        String cause = c.moodCauses.isEmpty() ? "I just feel that way" : c.moodCauses.get(0);
        String out = s
                .replace("{name}", c.name)
                .replace("{player}", petName(c, r))
                .replace("{owner}", c.playerName)
                .replace("{time}", c.partOfDay)
                .replace("{clock}", c.clock)
                .replace("{biome}", c.biome)
                .replace("{weather}", c.weather)
                .replace("{mood_word}", c.mood.word())
                .replace("{cause_lower}", cause.substring(0, 1).toLowerCase() + cause.substring(1))
                .replace("{cause}", cause)
                .replace("{hunger_word}", c.hungerWord())
                .replace("{energy_word_cap}", cap(c.energyWord()))
                .replace("{energy_word}", c.energyWord())
                .replace("{activity}", c.favoriteActivity)
                .replace("{fav}", c.favoriteRevealed == null ? "pretty things" : c.favoriteRevealed)
                .replace("{memory_cap}", memory == null ? "We've had some good times" : cap(memory))
                .replace("{memory}", memory == null ? "we first met" : memory)
                .replace("{away}", c.var("away", "a while"))
                .replace("{mob}", c.var("mob", c.threat == null ? "monster" : c.threat))
                .replace("{animal}", c.var("animal", "animal"))
                .replace("{category}", c.var("category", "that"))
                .replace("{item_good}", r.nextBoolean() ? "delicious" : "so good");
        String item = c.var("item", "gift");
        out = out.replace("{item}", item.toLowerCase());
        return out;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Personality and context flavour: openers, need remarks, follow-up questions. */
    private static String decorate(String text, DialogueContext c, RandomSource r, boolean allowFollowUp) {
        StringBuilder sb = new StringBuilder();
        if (r.nextFloat() < 0.22f && !text.startsWith("*")) {
            String opener = switch (c.archetype) {
                case "shy" -> r.nextBoolean() ? "Um... " : "Uhm, ";
                case "playful" -> r.nextBoolean() ? "Hehe, " : "Ooh, ";
                case "bold" -> r.nextBoolean() ? "Okay, " : "Alright, ";
                case "gentle" -> "Mm, ";
                case "independent" -> "Well, ";
                default -> "Hey, ";
            };
            if (c.mood == Mood.SLEEPY && r.nextBoolean()) opener = "*yawns* ";
            sb.append(opener).append(Character.toLowerCase(text.charAt(0))).append(text.substring(1));
        } else {
            sb.append(text);
        }
        boolean needsTrigger = c.trigger == DialogueTrigger.HUNGRY || c.trigger == DialogueTrigger.STARVING || c.trigger == DialogueTrigger.FED
                || c.trigger == DialogueTrigger.TIRED || c.trigger == DialogueTrigger.EXHAUSTED || c.trigger == DialogueTrigger.FULL;
        boolean urgent = c.fear > 0.3f || c.trigger.name().startsWith("HURT") || c.trigger == DialogueTrigger.CRITICAL || c.trigger == DialogueTrigger.SCARED;
        if (!needsTrigger && !urgent && r.nextFloat() < 0.25f) {
            if (c.hunger >= 60) sb.append(" ...Also, I'm getting hungry.");
            else if (c.energy <= 25) sb.append(" I'm kind of sleepy, though.");
            else if (c.sick) sb.append(" I don't feel very well, though.");
        }
        if (allowFollowUp && !urgent && r.nextFloat() < 0.28f && c.trigger != DialogueTrigger.REPLY) {
            String[] followUps = c.night
                    ? new String[]{" Should we find somewhere safe for the night?", " Are you sleepy yet?", " Want to look at the stars with me?"}
                    : new String[]{" What should we do next?", " Want to go exploring?", " How's your day going?", " Is there anything you need help with?"};
            sb.append(followUps[r.nextInt(followUps.length)]);
        }
        String s = sb.toString().trim();
        if (c.romantic() && (c.mood == Mood.AFFECTIONATE || c.mood == Mood.PLAYFUL) && r.nextFloat() < 0.2f && s.endsWith(".")) {
            s = s.substring(0, s.length() - 1) + "~";
        }
        return s;
    }

    /** Detected meaning of something the player said. */
    public enum Intent {
        GREETING, FAREWELL, HOW_ARE_YOU, ASK_MOOD_WHY, LOVE, COMPLIMENT, APOLOGY, THANKS, ASK_HUNGRY, ASK_TIRED,
        ASK_FEELING_BODY, ASK_FAVORITE, ASK_REMEMBER, ASK_ABOUT_HER, ASK_RELATIONSHIP, PROMISE, PROMISE_KEPT, PLAN,
        INSULT, JOKE, GOODNIGHT, GOOD_MORNING, MISS_YOU, COMMAND, ASK_AFFECTION_OK, ASK_AFFECTION_TOO_SOON,
        YES, NO, QUESTION, STATEMENT
    }
}
