package com.fantasyweapons.guide;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.SidedHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Wayfarer's Codex: the mod's guide, a book every player is given when they first join. Using it lifts it into view
 * and opens it (the artist's animation, in first person), and its pages are then read and turned right there in the
 * hands: text, pictures and recipes are drawn on the book itself (see the client's codex classes).
 */
public class WayfarersCodexItem extends Item implements GeoItem {
    private static final RawAnimation CLOSED = RawAnimation.begin().thenLoop("animation.wayfarers_codex.closed");
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.wayfarers_codex.open");
    private static final RawAnimation HELD = RawAnimation.begin().thenLoop("animation.wayfarers_codex.held");
    /** The opening played backwards (the client's model makes it from the artist's opening). */
    public static final String CLOSE_ANIMATION = "animation.wayfarers_codex.close";
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlay(CLOSE_ANIMATION);
    /** How much faster the book closes than it opens. */
    public static final double CLOSE_SPEED = 4;
    /** The reading states, as {@link SidedHooks#codexPhase()} reports them. */
    public static final int CLOSED_PHASE = 0, OPENING_PHASE = 1, READING_PHASE = 2, CLOSING_PHASE = 3;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WayfarersCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) SidedHooks.get().openCodex(hand);
        else if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.fantasyweapons.advancement.ModTriggers.CODEX_OPENED.get().trigger(server, "");
            com.fantasyweapons.quest.QuestService.event(server, "codex");
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ink = 0xD8C9A8, faded = 0x9C8862, gold = 0xF2C46B;
        tooltip.add(Component.literal(Tr.s("Every road to a legend, written down")).withStyle(s -> s.withColor(ink).withItalic(true)));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(Tr.s("✦ Ores, the Refinery, the Crafting Table")).withStyle(s -> s.withColor(gold)));
        tooltip.add(Component.literal(Tr.s("✦ Every weapon, armor set and recipe")).withStyle(s -> s.withColor(gold)));
        tooltip.add(Component.literal(Tr.s("⚒ Use to open and read")).withStyle(s -> s.withColor(faded)));
    }

    /** In first person the book opens and is held open while it is read; everywhere else it stays shut. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            ItemDisplayContext ctx = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
            int phase = ctx != null && ctx.firstPerson() ? SidedHooks.get().codexPhase() : CLOSED_PHASE;
            // opening plays from its first frame; closing plays it backwards (the arms bring the book down and away), then
            // eases into the shut book
            state.getController().transitionLength(phase == CLOSED_PHASE ? 6 : 0);
            state.getController().setAnimationSpeed(phase == CLOSING_PHASE ? CLOSE_SPEED : 1);
            return state.setAndContinue(phase == READING_PHASE ? HELD : phase == OPENING_PHASE ? OPEN : phase == CLOSING_PHASE ? CLOSE : CLOSED);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        SidedHooks.get().provideItemRenderer(this, provider -> consumer.accept((GeoRenderProvider) provider));
    }
}
