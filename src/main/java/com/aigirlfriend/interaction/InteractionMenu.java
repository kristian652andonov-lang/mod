package com.aigirlfriend.interaction;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.FoodHelper;
import com.aigirlfriend.companion.GiftCategory;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.network.StatusSnapshot;
import com.aigirlfriend.network.payload.OpenMenuPayload;
import com.aigirlfriend.seat.SeatManager;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Builds the list of currently valid wheel options and executes chosen ones, always re-validating server side. */
public final class InteractionMenu {
    private InteractionMenu() {}

    public static void open(GirlfriendEntity gf, ServerPlayer player) {
        List<ActionType> actions = validActions(gf, player);
        int[] ids = actions.stream().mapToInt(Enum::ordinal).toArray();
        com.aigirlfriend.network.Net.send(player, new OpenMenuPayload(gf.getId(), ids, StatusSnapshot.of(gf, player, true)));
        gf.getLookControl().setLookAt(player, 30f, 30f);
    }

    public static List<ActionType> validActions(GirlfriendEntity gf, ServerPlayer player) {
        List<ActionType> list = new ArrayList<>();
        if (!gf.isOwnedBy(player)) return list;
        InteractionManager im = gf.interactions();
        InteractionType cur = im.current();
        CommandMode cmd = gf.getCommand();
        boolean fainted = gf.isFainted();

        list.add(ActionType.TALK);
        list.add(ActionType.STATUS);
        if (fainted) return list;
        if (cmd != CommandMode.FOLLOW) list.add(ActionType.FOLLOW);
        if (cmd != CommandMode.WAIT) list.add(ActionType.WAIT);
        if (!gf.isSeated() && cur == null) list.add(ActionType.SIT);
        if (gf.isSeated() && (cur == null || !cur.isSeated())) list.add(ActionType.STAND_UP);
        if (cmd != CommandMode.STAY_SAFE) list.add(ActionType.STAY_SAFE);
        if (gf.data().home != null && cmd != CommandMode.HOME) list.add(ActionType.RETURN_HOME);
        list.add(ActionType.SET_HOME);

        addIfValid(list, gf, player, ActionType.HUG, InteractionType.HUG);
        addIfValid(list, gf, player, ActionType.CUDDLE, InteractionType.CUDDLE);
        addIfValid(list, gf, player, ActionType.KISS, InteractionType.KISS);
        addIfValid(list, gf, player, ActionType.KISS_CHEEK, InteractionType.KISS_CHEEK);
        addIfValid(list, gf, player, ActionType.FOREHEAD_KISS, InteractionType.FOREHEAD_KISS);
        addIfValid(list, gf, player, ActionType.BACK_HUG, InteractionType.BACK_HUG);
        addIfValid(list, gf, player, ActionType.LEAN, InteractionType.LEAN_ON);
        if (cur == InteractionType.HOLD_HANDS || cur == InteractionType.LEAD_TO_BED) list.add(ActionType.LET_GO);
        else addIfValid(list, gf, player, ActionType.HOLD_HANDS, InteractionType.HOLD_HANDS);
        if (cur == null || !cur.isSeated()) addIfValid(list, gf, player, ActionType.SIT_TOGETHER, InteractionType.SIT_TOGETHER);
        if (cur != null && cur.isSeated()) {
            if (cur != InteractionType.HEAD_ON_SHOULDER) addIfValid(list, gf, player, ActionType.HEAD_ON_SHOULDER, InteractionType.HEAD_ON_SHOULDER);
            if (cur != InteractionType.CUDDLE_SITTING) addIfValid(list, gf, player, ActionType.CUDDLE_SITTING, InteractionType.CUDDLE_SITTING);
        }
        if (cur == InteractionType.CARRY) list.add(ActionType.PUT_DOWN);
        else addIfValid(list, gf, player, ActionType.PICK_UP, InteractionType.CARRY);
        addIfValid(list, gf, player, ActionType.PET_HAIR, InteractionType.PET_HAIR);
        addIfValid(list, gf, player, ActionType.PET_CHEEK, InteractionType.PET_CHEEK);
        if (cur == InteractionType.LAP_SIT) list.add(ActionType.GET_UP);
        else addIfValid(list, gf, player, ActionType.LAP_SIT, InteractionType.LAP_SIT);

        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            if (FoodHelper.isFood(held, gf) || FoodHelper.isDrink(held)) list.add(ActionType.FEED);
            else if (GiftCategory.of(held) != null) list.add(ActionType.GIVE_GIFT);
        }
        if ((WorldClock.isNight(gf.level()) || gf.level().isThundering()) && gf.data().relationship.stage().atLeast(InteractionType.SLEEP_TOGETHER.stage)
                && cur != InteractionType.SLEEP_TOGETHER && cur != InteractionType.LEAD_TO_BED && im.findBedNear(player, 24) != null) {
            list.add(ActionType.SLEEP_TOGETHER);
        }
        if (cur != InteractionType.CUDDLE_BED) addIfValid(list, gf, player, ActionType.CUDDLE_BED, InteractionType.CUDDLE_BED);
        if (cur != null && cur != InteractionType.HOLD_HANDS && cur != InteractionType.CARRY && cur != InteractionType.LEAD_TO_BED && cur != InteractionType.LAP_SIT) list.add(ActionType.STOP);
        return list;
    }

    private static void addIfValid(List<ActionType> list, GirlfriendEntity gf, ServerPlayer player, ActionType action, InteractionType type) {
        if (gf.interactions().current() == type) return;
        if (gf.interactions().check(type, player, false) == null) list.add(action);
    }

    public static void perform(GirlfriendEntity gf, ServerPlayer player, ActionType action) {
        if (!validActions(gf, player).contains(action)) {
            open(gf, player);
            return;
        }
        InteractionManager im = gf.interactions();
        switch (action) {
            case TALK, STATUS -> gf.getLookControl().setLookAt(player, 30f, 30f);
            case FOLLOW -> gf.controller().command(CommandMode.FOLLOW, player);
            case WAIT -> gf.controller().command(CommandMode.WAIT, player);
            case SIT -> gf.controller().command(CommandMode.SIT, player);
            case STAND_UP -> {
                SeatManager.standUp(gf);
                gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
                if (gf.getCommand() == CommandMode.SIT) gf.controller().command(CommandMode.FOLLOW, player);
                else gf.controller().say(com.aigirlfriend.dialogue.DialogueTrigger.STAND_UP);
            }
            case STAY_SAFE -> gf.controller().command(CommandMode.STAY_SAFE, player);
            case RETURN_HOME -> gf.controller().command(CommandMode.HOME, player);
            case SET_HOME -> gf.controller().setHome(gf.blockPosition());
            case HUG -> im.start(InteractionType.HUG, player, false);
            case CUDDLE -> im.start(InteractionType.CUDDLE, player, false);
            case KISS -> im.start(InteractionType.KISS, player, false);
            case KISS_CHEEK -> im.start(InteractionType.KISS_CHEEK, player, false);
            case FOREHEAD_KISS -> im.start(InteractionType.FOREHEAD_KISS, player, false);
            case BACK_HUG -> im.start(InteractionType.BACK_HUG, player, false);
            case LEAN -> im.start(InteractionType.LEAN_ON, player, false);
            case HOLD_HANDS -> im.start(InteractionType.HOLD_HANDS, player, false);
            case SIT_TOGETHER -> im.start(InteractionType.SIT_TOGETHER, player, false);
            case HEAD_ON_SHOULDER -> im.start(InteractionType.HEAD_ON_SHOULDER, player, false);
            case CUDDLE_SITTING -> im.start(InteractionType.CUDDLE_SITTING, player, false);
            case PICK_UP -> im.start(InteractionType.CARRY, player, false);
            case CUDDLE_BED -> im.start(InteractionType.CUDDLE_BED, player, false);
            case PET_HAIR -> im.start(InteractionType.PET_HAIR, player, false);
            case PET_CHEEK -> im.start(InteractionType.PET_CHEEK, player, false);
            case LAP_SIT -> im.start(InteractionType.LAP_SIT, player, false);
            case LET_GO, PUT_DOWN, STOP, GET_UP -> im.stop(InteractionManager.StopReason.CANCELLED_PLAYER);
            case GIVE_GIFT, FEED -> {
                ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (FoodHelper.isDrink(held)) gf.controller().onDrink(player, held);
                else if (FoodHelper.isFood(held, gf)) gf.controller().onFeed(player, held);
                else gf.controller().onGift(player, held);
            }
            case SLEEP_TOGETHER -> gf.controller().requestBedtime(player);
        }
    }
}
