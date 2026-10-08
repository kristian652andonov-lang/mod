package com.fantasyweapons.client;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.client.anim.AnimTracker;
import com.fantasyweapons.client.anim.WeaponPoses;
import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.ClientChargeFx;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.GenericFx;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.hud.Notifications;
import com.fantasyweapons.client.hud.WeaponHud;
import com.fantasyweapons.client.input.InputHandler;
import com.fantasyweapons.client.input.KeyBindings;
import com.fantasyweapons.client.render.FWShaders;
import com.fantasyweapons.client.render.WeaponClientExtensions;
import com.fantasyweapons.client.render.WeaponRenderer;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.network.ProgressionEventPayload;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

import java.util.function.Consumer;

/** Client-only bootstrap: renderers, shaders, HUD layers, input, VFX ticking and the client side of SidedHooks. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        SidedHooks.Holder.instance = new Hooks();
        FxDispatcher.init();

        modBus.addListener(KeyBindings::register);
        modBus.addListener(FWShaders::register);
        modBus.addListener(ClientSetup::registerLayers);
        modBus.addListener(ClientSetup::registerExtensions);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(com.fantasyweapons.registry.ModEntities.THROWN_WEAPON.get(),
                        com.fantasyweapons.client.render.ThrownWeaponRenderer::new));

        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.input.InputHandler::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(VfxManager::render);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.SkyDarkness::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.SkyDarkness::onFogColor);
        // fantasy weapons' tooltips: a dark page edged in the weapon's own colour instead of the vanilla purple
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderTooltipEvent.Color e) -> {
            int[] c = com.fantasyweapons.weapon.FantasyWeaponItem.tooltipColors(e.getItemStack());
            if (c != null) {
                e.setBorderStart(c[0]);
                e.setBorderEnd(c[1]);
                e.setBackground(c[2]);
            }
        });
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeCameraAngles e) -> CameraShake.apply(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clearAll());
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Pre<?, ?> e) -> {
            WeaponPoses.setRendering(e.getEntity());
            float spin = AnimTracker.spinAngle(e.getEntity(), e.getPartialTick());
            SPIN_PUSHED.push(spin != 0);
            if (spin != 0) {
                e.getPoseStack().pushPose();
                e.getPoseStack().mulPose(com.mojang.math.Axis.YP.rotation(spin));
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?, ?> e) -> {
            if (!SPIN_PUSHED.isEmpty() && SPIN_PUSHED.pop()) e.getPoseStack().popPose();
            WeaponPoses.setRendering(null);
        });
        com.fantasyweapons.client.devtest.ScreenshotDirector.initIfRequested();
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, FantasyWeapons.id("screen_fx"), ScreenFx::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, FantasyWeapons.id("weapon_hud"), WeaponHud::render);
        event.registerAboveAll(FantasyWeapons.id("notifications"), Notifications::render);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        Item[] items = ModItems.WEAPONS.values().stream().map(h -> (Item) h.get()).toArray(Item[]::new);
        event.registerItem(new WeaponClientExtensions(), items);
    }

    /** Whether each in-flight living-entity render pushed a spin rotation (popped again in Post). */
    private static final java.util.ArrayDeque<Boolean> SPIN_PUSHED = new java.util.ArrayDeque<>();

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        InputHandler.preTick();
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        InputHandler.postTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        VfxManager.tick();
        FxScheduler.tick();
        CameraShake.tick();
        ScreenFx.tick();
        ClientChargeFx.tick();
        AnimTracker.tick();
        com.fantasyweapons.client.fx.InfernochainFx.tick();
        if (mc.player != null) {
            StatusEffects own = mc.player.getExistingDataOrNull(ModAttachments.STATUS);
            if (own != null) own.clientTick();
        }
    }

    private static void clearAll() {
        VfxManager.clear();
        FxScheduler.clear();
        ClientChargeFx.clear();
        Notifications.clear();
        AnimTracker.clear();
        com.fantasyweapons.client.fx.FxProjectiles.clear();
        com.fantasyweapons.client.render.ChainTracker.clear();
        com.fantasyweapons.client.fx.InfernochainFx.clear();
    }

    /** Client implementation of the common → client bridge. */
    static final class Hooks implements SidedHooks {
        @Override
        public void onWeaponSwing(LivingEntity entity, ItemStack stack, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof FantasyWeaponItem item)) return;
            GenericFx.onSwing(entity, stack);
            var form = item.definition().form(FantasyWeaponItem.data(stack));
            String trigger = entity.isCrouching() ? "heavy_attack" : form != null ? "attack:" + form.id() : "attack";
            item.triggerAnim(entity, GeoItem.getId(stack), FantasyWeaponItem.CONTROLLER, trigger);
        }

        @Override
        public void provideRenderer(FantasyWeaponItem item, Consumer<Object> consumer) {
            consumer.accept(new GeoRenderProvider() {
                private WeaponRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                    if (renderer == null) renderer = new WeaponRenderer();
                    return renderer;
                }
            });
        }

        @Override
        public void handleFx(FxPayload payload) {
            FxDispatcher.dispatch(payload);
        }

        @Override
        public void onThrownWeapon(com.fantasyweapons.entity.ThrownWeaponEntity entity) {
            com.fantasyweapons.client.fx.ThrownWeaponFx.attach(entity);
        }

        @Override
        public void handleProgression(ProgressionEventPayload payload) {
            Notifications.handle(payload);
        }
    }
}
