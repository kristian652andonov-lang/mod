package com.fantasyweapons.mixin;

import com.fantasyweapons.client.perf.AtlasAnimations;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hands the atlas each of the mod's animated textures' animation through {@link AtlasAnimations}, which can pause it. */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {
    @Inject(method = "createTicker", at = @At("RETURN"), cancellable = true, require = 0)
    private void fantasyweapons$ticker(CallbackInfoReturnable<TextureAtlasSprite.Ticker> cir) {
        TextureAtlasSprite.Ticker ticker = cir.getReturnValue();
        if (ticker != null) cir.setReturnValue(AtlasAnimations.wrap((TextureAtlasSprite) (Object) this, ticker));
    }
}
