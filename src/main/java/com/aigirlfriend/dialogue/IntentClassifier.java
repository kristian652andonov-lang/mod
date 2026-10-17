package com.aigirlfriend.dialogue;

import com.aigirlfriend.dialogue.LocalDialogueGenerator.Intent;

import java.util.Locale;
import java.util.regex.Pattern;

/** Lightweight keyword/pattern intent detection for player messages (used for game effects and offline replies). */
public final class IntentClassifier {
    private IntentClassifier() {}

    public enum Command { NONE, FOLLOW, WAIT, SIT, HOME, STAY_SAFE }

    public enum AffectionAsk { NONE, HUG, KISS, HOLD_HANDS, CUDDLE }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final Pattern GREETING = p("^(hi|hey|hello|heya|hiya|yo|sup|howdy|good (afternoon|evening))\\b");
    private static final Pattern FAREWELL = p("\\b(bye|goodbye|see you|see ya|cya|gotta go|i have to go|later)\\b");
    private static final Pattern HOW_ARE_YOU = p("how are you|how('?re| are) you (feeling|doing)|how do you feel|you ok(ay)?\\??|are you ok(ay)?|what'?s up with you|how'?s it going");
    private static final Pattern WHY_MOOD = p("why (are|r) you (so )?(sad|happy|upset|grumpy|angry|quiet|lonely|worried|scared|tired|mad)|what'?s wrong|what happened");
    private static final Pattern LOVE = p("\\bi (really )?(love|adore) you\\b|\\bilu\\b|\\bily\\b|love you");
    private static final Pattern COMPLIMENT = p("you('?re| are| look) (so |really |very )?(cute|pretty|beautiful|gorgeous|lovely|amazing|adorable|sweet|smart|kind|the best)|i like your|you'?re mine");
    private static final Pattern APOLOGY = p("\\b(sorry|apologi[sz]e|my bad|forgive me)\\b");
    private static final Pattern THANKS = p("\\b(thanks|thank you|thx|ty)\\b");
    private static final Pattern ASK_HUNGRY = p("are you hungry|you hungry|want (some |something to )?(food|eat)|did you eat|need food");
    private static final Pattern ASK_TIRED = p("are you tired|you tired|are you sleepy|need (a )?rest|want to (sleep|rest)");
    private static final Pattern ASK_BODY = p("\\b(period|cramps?|time of the month|menstrua\\w*|feeling (sick|unwell|ill)|does (it|your (tummy|stomach)) hurt|are you hurt|are you sick)\\b");
    private static final Pattern ASK_FAVORITE = p("what('?s| is) your favou?rite|what do you like|what (gift|present)s? do you like|do you like");
    private static final Pattern ASK_REMEMBER = p("do you remember|remember (when|the time|that)|what do you remember|our first");
    private static final Pattern ASK_ABOUT = p("tell me about yourself|who are you|what are you|where are you from|what'?s your story");
    private static final Pattern ASK_RELATIONSHIP = p("what are we|are we (dating|together|friends)|do you (like|love) me|will you be my|be my girlfriend|are you my girlfriend");
    private static final Pattern PROMISE_KEPT = p("(kept|keeping) (my|the) promise|i did (it|what i promised)|promise kept");
    private static final Pattern PROMISE = p("\\bi promise\\b|\\bpromise (you|i will|i'?ll|to)\\b|\\btomorrow (we|i) (will|'?ll)|\\bi'?ll (take|bring|build|show) you\\b|\\bnext time (we|i)\\b");
    private static final Pattern PLAN = p("\\b(let'?s|lets|wanna|want to|shall we|how about we)\\b");
    private static final Pattern INSULT = p("\\b(stupid|idiot|dumb|ugly|annoying|hate you|shut up|useless|go away|loser)\\b");
    private static final Pattern JOKE = p("tell me a joke|say something funny|make me laugh|know any jokes");
    private static final Pattern GOODNIGHT = p("good ?night|nighty|sleep (well|tight)|going to (bed|sleep)|time (for|to) (bed|sleep)");
    private static final Pattern GOOD_MORNING = p("good ?morning|morning!?$|rise and shine");
    private static final Pattern MISS_YOU = p("i (missed|miss) you");
    private static final Pattern YES = p("^(yes|yeah|yep|sure|ok|okay|of course|alright|fine|definitely|absolutely)\\b");
    private static final Pattern NO = p("^(no|nope|nah|not now|maybe later|no thanks)\\b");
    private static final Pattern CMD_FOLLOW = p("follow me|come (with me|along|on)|let'?s go\\b|stay close|come here");
    private static final Pattern CMD_WAIT = p("wait (here|for me|there)|stay (here|there|put)|don'?t move");
    private static final Pattern CMD_SIT = p("sit (down|here|with me)|take a seat|have a seat");
    private static final Pattern CMD_HOME = p("go home|head home|return home|wait (for me )?at home|go back home");
    private static final Pattern CMD_SAFE = p("stay (back|safe)|keep (back|away|safe)|hide|stay behind me");
    private static final Pattern ASK_HUG = p("(can|may|could) i (have a |get a )?hug|hug me|give me a hug|come here for a hug|let me hug you|want a hug");
    private static final Pattern ASK_KISS = p("(can|may|could) i (have a |get a )?kiss|kiss me|give me a kiss|let me kiss you");
    private static final Pattern ASK_HANDS = p("hold (my )?hands?|take my hand|give me your hand");
    private static final Pattern ASK_CUDDLE = p("cuddle|snuggle");

    public static Intent classify(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (PROMISE_KEPT.matcher(s).find()) return Intent.PROMISE_KEPT;
        if (JOKE.matcher(s).find()) return Intent.JOKE;
        if (INSULT.matcher(s).find() && !s.contains("not ")) return Intent.INSULT;
        if (ASK_RELATIONSHIP.matcher(s).find()) return Intent.ASK_RELATIONSHIP;
        if (LOVE.matcher(s).find()) return Intent.LOVE;
        if (MISS_YOU.matcher(s).find()) return Intent.MISS_YOU;
        if (APOLOGY.matcher(s).find()) return Intent.APOLOGY;
        if (ASK_BODY.matcher(s).find()) return Intent.ASK_FEELING_BODY;
        if (WHY_MOOD.matcher(s).find()) return Intent.ASK_MOOD_WHY;
        if (HOW_ARE_YOU.matcher(s).find()) return Intent.HOW_ARE_YOU;
        if (ASK_HUNGRY.matcher(s).find()) return Intent.ASK_HUNGRY;
        if (ASK_TIRED.matcher(s).find()) return Intent.ASK_TIRED;
        if (ASK_REMEMBER.matcher(s).find()) return Intent.ASK_REMEMBER;
        if (ASK_FAVORITE.matcher(s).find()) return Intent.ASK_FAVORITE;
        if (ASK_ABOUT.matcher(s).find()) return Intent.ASK_ABOUT_HER;
        if (PROMISE.matcher(s).find()) return Intent.PROMISE;
        if (GOODNIGHT.matcher(s).find()) return Intent.GOODNIGHT;
        if (GOOD_MORNING.matcher(s).find()) return Intent.GOOD_MORNING;
        if (COMPLIMENT.matcher(s).find()) return Intent.COMPLIMENT;
        if (command(s) != Command.NONE) return Intent.COMMAND;
        if (THANKS.matcher(s).find()) return Intent.THANKS;
        if (GREETING.matcher(s).find()) return Intent.GREETING;
        if (FAREWELL.matcher(s).find()) return Intent.FAREWELL;
        if (YES.matcher(s).find()) return Intent.YES;
        if (NO.matcher(s).find()) return Intent.NO;
        if (PLAN.matcher(s).find()) return Intent.PLAN;
        if (s.endsWith("?")) return Intent.QUESTION;
        return Intent.STATEMENT;
    }

    public static Command command(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (CMD_HOME.matcher(s).find()) return Command.HOME;
        if (CMD_WAIT.matcher(s).find()) return Command.WAIT;
        if (CMD_SIT.matcher(s).find()) return Command.SIT;
        if (CMD_SAFE.matcher(s).find()) return Command.STAY_SAFE;
        if (CMD_FOLLOW.matcher(s).find()) return Command.FOLLOW;
        return Command.NONE;
    }

    public static AffectionAsk affection(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (ASK_KISS.matcher(s).find()) return AffectionAsk.KISS;
        if (ASK_HUG.matcher(s).find()) return AffectionAsk.HUG;
        if (ASK_HANDS.matcher(s).find()) return AffectionAsk.HOLD_HANDS;
        if (ASK_CUDDLE.matcher(s).find() && (s.contains("can") || s.contains("let") || s.contains("want") || s.endsWith("?"))) return AffectionAsk.CUDDLE;
        return AffectionAsk.NONE;
    }

    /** Things the player tells about themselves, worth remembering ("my favorite color is blue"). */
    private static final Pattern PERSONAL = p("\\b(my favou?rite \\w+ is|i (really )?(like|love|enjoy|hate) (to )?\\w+|my name is|i'?m from|my birthday)\\b");

    public static boolean isPersonalFact(String raw) {
        return raw.length() >= 10 && PERSONAL.matcher(raw).find() && !LOVE.matcher(raw).find();
    }
}
