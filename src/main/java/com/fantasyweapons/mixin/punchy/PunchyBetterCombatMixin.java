package com.fantasyweapons.mixin.punchy;

import com.fantasyweapons.compat.bettercombat.BetterCombatClient;
import com.fantasyweapons.compat.bettercombat.BetterCombatCompat;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With a shader pack on (or a trident in hand), Punchy hides its first-person arms and their items while a Better Combat
 * attack plays, leaving the attack to Better Combat's player model. The weapons' attacks are not drawn on that model
 * (see FirstPersonModeMixin) but in the first-person hand, so for them the arm and the weapon vanished for the whole
 * attack: Punchy is told no such attack is playing, and its arm swings the weapon as it does without shaders. Only
 * applied with Punchy installed (see PunchyMixinPlugin).
 */
@Mixin(targets = "punchy.compat.BetterCombatCompat", remap = false)
public abstract class PunchyBetterCombatMixin {
    @Inject(method = "isAttackVisualActive", at = @At("HEAD"), cancellable = true)
    private static void fantasyweapons$weaponsSwingInHand(LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (BetterCombatCompat.loaded() && BetterCombatClient.swingsInHand()) cir.setReturnValue(false);
    }
}
