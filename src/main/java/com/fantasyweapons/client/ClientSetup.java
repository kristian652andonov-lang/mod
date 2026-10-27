package com.fantasyweapons.client;

import com.fantasyweapons.client.render.material.MaterialItemRenderers;
import com.fantasyweapons.client.render.material.MaterialOreRenderer;
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
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) -> {
            e.register(com.fantasyweapons.registry.ModMenus.REFINERY.get(), com.fantasyweapons.client.gui.RefineryScreen::new);
            e.register(com.fantasyweapons.registry.ModMenus.LEGENDARY_CRAFTING.get(), com.fantasyweapons.client.gui.LegendaryCraftingScreen::new);
            e.register(com.fantasyweapons.registry.ModMenus.WORLDFORGE.get(), com.fantasyweapons.client.gui.WorldforgeScreen::new);
            e.register(com.fantasyweapons.registry.ModMenus.UPGRADING.get(), com.fantasyweapons.client.gui.UpgradingScreen::new);
        });
        modBus.addListener(ClientSetup::registerExtensions);
        modBus.addListener(com.fantasyweapons.client.perf.AtlasAnimations::onModelsBaked);
        // in Punchy's hand the Wayfarer's Codex stays shut: leave the Codex to its own animation
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(com.fantasyweapons.compat.punchy.PunchyCompat::sync));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == com.fantasyweapons.config.ClientConfig.SPEC) com.fantasyweapons.compat.punchy.PunchyCompat.sync();
        });
        modBus.addListener(com.fantasyweapons.client.particle.ForgeParticles::register);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) -> {
            e.registerReloadListener(new com.fantasyweapons.client.render.FrameStripTextures());
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> com.fantasyweapons.client.render.QuadCull.clear());
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                com.fantasyweapons.client.fx.ImpactFrames.reset();
                com.fantasyweapons.client.fx.BlockVoxels.reset();
                com.fantasyweapons.client.fx.ultimate.Worldburner.reset();
            });
        });
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(com.fantasyweapons.registry.ModEntities.THROWN_WEAPON.get(),
                    com.fantasyweapons.client.render.ThrownWeaponRenderer::new);
            e.registerBlockEntityRenderer(com.fantasyweapons.registry.ModBlockEntities.MATERIAL_ORE.get(), ctx -> new MaterialOreRenderer());
            e.registerBlockEntityRenderer(com.fantasyweapons.registry.ModBlockEntities.ARCANE_REFINERY.get(),
                    ctx -> new com.fantasyweapons.client.render.machine.MachineRenderers.Block<>("arcane_refinery"));
            e.registerBlockEntityRenderer(com.fantasyweapons.registry.ModBlockEntities.LEGENDARY_CRAFTING_TABLE.get(),
                    ctx -> new com.fantasyweapons.client.render.machine.MachineRenderers.Table());
            e.registerBlockEntityRenderer(com.fantasyweapons.registry.ModBlockEntities.WORLDFORGE.get(),
                    ctx -> new com.fantasyweapons.client.render.machine.MachineRenderers.Worldforge());
            e.registerBlockEntityRenderer(com.fantasyweapons.registry.ModBlockEntities.UPGRADING_TABLE.get(),
                    ctx -> new com.fantasyweapons.client.render.machine.MachineRenderers.UpgradingTable());
        });

        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.input.InputHandler::onInteractionKey);
        if (com.fantasyweapons.compat.bettercombat.BetterCombatCompat.loaded()) com.fantasyweapons.compat.bettercombat.BetterCombatClient.init();
        // nothing of the HUD over the Codex while it is read
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Pre e) -> {
            if (com.fantasyweapons.client.guide.Codex.reading()) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.ImpactFrames::render);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.ImpactFrames::fov);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.ImpactFrames::warm);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.gui.InventoryButtons::onInit);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTickPost);
        // the square a legendary tool digs: outlined, and cracking all together
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.tool.AreaMiningClient::onHighlight);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.tool.AreaMiningClient::tick);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.tool.OreSight::render);
        NeoForge.EVENT_BUS.addListener(VfxManager::render);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.SkyDarkness::onRenderStage);
        NeoForge.EVENT_BUS.addListener(com.fantasyweapons.client.fx.SkyDarkness::onFogColor);
        // fantasy weapons' tooltips: a dark page edged in the weapon's own colour instead of the vanilla purple
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderTooltipEvent.Color e) -> {
            int[] c = com.fantasyweapons.weapon.FantasyWeaponItem.tooltipColors(e.getItemStack());
            if (c == null) c = materialTooltipColors(e.getItemStack());
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

    /** Legendary materials' tooltips are edged in the material's own glow colour. */
    private static int[] materialTooltipColors(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof com.fantasyweapons.material.MaterialItem m) return com.fantasyweapons.material.MaterialLore.tooltipColors(m.material());
        if (stack.getItem() instanceof com.fantasyweapons.material.MaterialOreItem o) return com.fantasyweapons.material.MaterialLore.tooltipColors(o.material());
        if (stack.getItem() instanceof com.fantasyweapons.tool.LegendaryTool t) return com.fantasyweapons.material.MaterialLore.tooltipColors(t.material());
        return null;
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, FantasyWeapons.id("screen_fx"), ScreenFx::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, FantasyWeapons.id("weapon_hud"), WeaponHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, FantasyWeapons.id("set_hud"), com.fantasyweapons.client.hud.SetHud::render);
        event.registerAboveAll(FantasyWeapons.id("notifications"), Notifications::render);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        Item[] items = ModItems.WEAPONS.values().stream().map(h -> (Item) h.get()).toArray(Item[]::new);
        event.registerItem(new WeaponClientExtensions(), items);
        event.registerBlock(new com.fantasyweapons.client.particle.MachineBreakParticles(), com.fantasyweapons.registry.ModBlocks.ARCANE_REFINERY.get(),
                com.fantasyweapons.registry.ModBlocks.REFINERY_PART.get(), com.fantasyweapons.registry.ModBlocks.LEGENDARY_CRAFTING_TABLE.get(),
                com.fantasyweapons.registry.ModBlocks.LEGENDARY_CRAFTING_TABLE_PART.get(), com.fantasyweapons.registry.ModBlocks.WORLDFORGE.get(),
                com.fantasyweapons.registry.ModBlocks.WORLDFORGE_PART.get(), com.fantasyweapons.registry.ModBlocks.UPGRADING_TABLE.get(),
                com.fantasyweapons.registry.ModBlocks.UPGRADING_TABLE_PART.get());
    }

    /** Whether each in-flight living-entity render pushed a spin rotation (popped again in Post). */
    private static final java.util.ArrayDeque<Boolean> SPIN_PUSHED = new java.util.ArrayDeque<>();

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        InputHandler.preTick();
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        com.fantasyweapons.client.render.FrameStripTextures.tick();
        com.fantasyweapons.client.gui.ClassScreen.tickPending(Minecraft.getInstance());
        InputHandler.postTick();
        Minecraft mc = Minecraft.getInstance();
        com.fantasyweapons.client.guide.Codex.tick(mc);
        if (mc.level == null || mc.isPaused()) return;
        VfxManager.tick();
        com.fantasyweapons.client.wreck.WreckSync.tick();
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
        com.fantasyweapons.client.fx.ArmorFx.clear();
        com.fantasyweapons.client.render.ChainTracker.clear();
        com.fantasyweapons.client.fx.InfernochainFx.clear();
        com.fantasyweapons.client.tool.OreSight.clear();
    }

    /** Client implementation of the common → client bridge. */
    static final class Hooks implements SidedHooks {
        @Override
        public void handleOreSight(com.fantasyweapons.tool.OreSightPayload payload) {
            com.fantasyweapons.client.tool.OreSight.show(payload);
        }

        @Override
        public String abilityKeyName() {
            return com.fantasyweapons.client.input.KeyBindings.ABILITY.getTranslatedKeyMessage().getString();
        }

        @Override
        public void handleWreck(com.fantasyweapons.network.WreckPayload payload) {
            com.fantasyweapons.client.wreck.WreckSync.apply(payload);
        }

        @Override
        public void openCodex(InteractionHand hand) {
            com.fantasyweapons.client.guide.Codex.use(hand);
        }

        @Override
        public int codexPhase() {
            return com.fantasyweapons.client.guide.Codex.phase();
        }

        @Override
        public void onWeaponSwing(LivingEntity entity, ItemStack stack, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof FantasyWeaponItem item)) return;
            GenericFx.onSwing(entity, stack);
            var form = item.definition().form(FantasyWeaponItem.data(stack));
            String trigger = entity.isCrouching() ? FantasyWeaponItem.formTrigger("heavy_attack", form) : form != null ? "attack:" + form.id() : "attack";
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
        public void provideItemRenderer(Item item, Consumer<Object> consumer) {
            consumer.accept(new GeoRenderProvider() {
                private BlockEntityWithoutLevelRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                    if (renderer == null) {
                        if (item instanceof com.fantasyweapons.material.MaterialItem m) renderer = MaterialItemRenderers.material(m);
                        else if (item instanceof com.fantasyweapons.material.MaterialOreItem o) renderer = MaterialItemRenderers.ore(o);
                        else if (item instanceof com.fantasyweapons.tool.LegendaryTool t) renderer = MaterialItemRenderers.tool(t);
                        else if (item instanceof com.fantasyweapons.refinery.ArcaneRefineryItem) {
                            renderer = com.fantasyweapons.client.render.machine.MachineRenderers.item("arcane_refinery");
                        } else if (item instanceof com.fantasyweapons.forge.LegendaryCraftingTableItem) {
                            renderer = com.fantasyweapons.client.render.machine.MachineRenderers.item("legendary_crafting_table");
                        } else if (item instanceof com.fantasyweapons.tool.UpgradingTableItem) {
                            renderer = com.fantasyweapons.client.render.machine.MachineRenderers.item("legendary_upgrading_table");
                        } else if (item instanceof com.fantasyweapons.forge.WorldforgeItem) {
                            renderer = com.fantasyweapons.client.render.machine.MachineRenderers.item("worldforge_core");
                        } else if (item instanceof com.fantasyweapons.guide.WayfarersCodexItem) {
                            renderer = new com.fantasyweapons.client.guide.CodexRenderer();
                        }
                    }
                    return renderer;
                }
            });
        }

        @Override
        public void provideArmorRenderer(com.fantasyweapons.armor.FantasyArmorItem item, Consumer<Object> consumer) {
            consumer.accept(new GeoRenderProvider() {
                private com.fantasyweapons.client.render.armor.ArmorRenderers.Worn worn;
                private com.fantasyweapons.client.render.armor.ArmorRenderers.Icon icon;

                @Override
                public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                    if (icon == null) icon = new com.fantasyweapons.client.render.armor.ArmorRenderers.Icon(item.set(), item.getType());
                    return icon;
                }

                @Override
                public <T extends LivingEntity> net.minecraft.client.model.HumanoidModel<?> getGeoArmorRenderer(T entity, net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<T> original) {
                    if (worn == null) worn = new com.fantasyweapons.client.render.armor.ArmorRenderers.Worn(item.set());
                    return worn;
                }
            });
        }

        @Override
        public net.minecraft.world.entity.player.Player clientPlayer() {
            return Minecraft.getInstance().player;
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

        @Override
        public void handleOpenClasses() {
            com.fantasyweapons.client.gui.ClassScreen.openWhenReady();
        }

        @Override
        public void handleQuestCompleted(String id) {
            com.fantasyweapons.quest.Quest q = com.fantasyweapons.quest.Quests.get(id);
            if (q == null) return;
            var payload = new com.fantasyweapons.network.DeedPayload(com.fantasyweapons.FantasyWeapons.id("quest/" + id),
                    net.minecraft.network.chat.Component.literal(q.title()).withColor(q.chapter().color()),
                    net.minecraft.network.chat.Component.literal(com.fantasyweapons.text.Tr.f("Claim your reward in the Quest Journal (%s)",
                            com.fantasyweapons.client.input.KeyBindings.QUESTS.getTranslatedKeyMessage().getString())), q.icon(), net.minecraft.advancements.AdvancementType.GOAL.ordinal());
            Minecraft.getInstance().getToasts().addToast(new com.fantasyweapons.client.hud.DeedToast(payload, "QUEST COMPLETE"));
        }

        @Override
        public void handleDeed(com.fantasyweapons.network.DeedPayload payload) {
            Minecraft.getInstance().getToasts().addToast(new com.fantasyweapons.client.hud.DeedToast(payload));
        }
    }
}
