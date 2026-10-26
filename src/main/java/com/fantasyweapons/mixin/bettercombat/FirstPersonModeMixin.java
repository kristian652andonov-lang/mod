package com.fantasyweapons.mixin.bettercombat;

import com.fantasyweapons.compat.bettercombat.BetterCombatClient;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better Combat's attacks with one of the weapons are not drawn on the player model in first person: the weapon stays in
 * the first-person hand and swings there, from its rest back to its rest (see BetterCombatClient). Only applied with
 * Better Combat installed (see BetterCombatMixinPlugin).
 */
@Mixin(targets = "net.bettercombat.client.compat.FirstPersonAnimationCompatibility", remap = false)
public abstract class FirstPersonModeMixin {
    @Inject(method = "firstPersonMode", at = @At("HEAD"), cancellable = true)
    private static void fantasyweapons$weaponsSwingInHand(CallbackInfoReturnable<FirstPersonMode> cir) {
        if (BetterCombatClient.swingsInHand()) cir.setReturnValue(FirstPersonMode.NONE);
    }
}
