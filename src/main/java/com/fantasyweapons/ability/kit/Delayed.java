package com.fantasyweapons.ability.kit;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Runs an action on the server after a delay (meteor impacts, sequenced eruptions...). Cancelled if the owner leaves. */
public final class Delayed extends AreaEffect {
    public interface Action {
        void run(ServerPlayer owner, ItemStack weapon);
    }

    private final Action action;

    private Delayed(AbilityContext ctx, int delay, Action action) {
        super(ctx.level(), ctx.player(), ctx.data().idOrNil(), ctx.player().position(), Math.max(1, delay));
        this.action = action;
    }

    public static void schedule(AbilityContext ctx, int delay, Action action) {
        if (delay <= 0) {
            action.run(ctx.player(), ctx.stack());
            return;
        }
        AreaEffectManager.add(new Delayed(ctx, delay, action));
    }

    @Override
    protected void tick(ServerPlayer owner) {
    }

    @Override
    protected void onEnd(@Nullable ServerPlayer owner) {
        if (owner != null) action.run(owner, weapon(owner));
    }
}
