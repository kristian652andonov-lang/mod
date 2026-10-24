package com.fantasyweapons.mixin;

import com.fantasyweapons.client.guide.Codex;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Wayfarer's Codex is read in both hands (its model brings its own), so while it is up before the eyes whatever the
 * other hand holds, a shield say, would be drawn over the open pages: that hand goes down out of sight as the book comes
 * up, as it would to swap items, and comes back up once the book is lowered again.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void fantasyweapons$codexOtherHandAway(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
                                                    ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int light,
                                                    CallbackInfo ci) {
        if (hand != Codex.hand() && Codex.raised() >= 1) ci.cancel();
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float fantasyweapons$codexOtherHandDown(float equippedProgress, @Local(argsOnly = true) InteractionHand hand) {
        return hand == Codex.hand() ? equippedProgress : Math.max(equippedProgress, Codex.raised());
    }
}
