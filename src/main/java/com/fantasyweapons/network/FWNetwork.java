package com.fantasyweapons.network;

import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.ability.AbilityService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FWNetwork {
    public static final String VERSION = "1";

    private FWNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);

        r.playToServer(C2SPayloads.AbilityKey.TYPE, C2SPayloads.AbilityKey.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleAbilityKey((ServerPlayer) ctx.player(), p.pressed())));
        r.playToServer(C2SPayloads.CastBolt.TYPE, C2SPayloads.CastBolt.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> com.fantasyweapons.magic.MageBolts.cast((ServerPlayer) ctx.player())));
        r.playToServer(C2SPayloads.CycleAbility.TYPE, C2SPayloads.CycleAbility.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleCycle((ServerPlayer) ctx.player(), p.direction())));
        r.playToServer(C2SPayloads.CastAt.TYPE, C2SPayloads.CastAt.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleCastAt((ServerPlayer) ctx.player(), p.ability(), new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z()))));
        r.playToServer(C2SPayloads.SwitchForm.TYPE, C2SPayloads.SwitchForm.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleSwitchForm((ServerPlayer) ctx.player())));
        r.playToServer(C2SPayloads.UpgradeAbility.TYPE, C2SPayloads.UpgradeAbility.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleUpgrade((ServerPlayer) ctx.player(), p.slot(), p.weapon(), p.ability(), p.toMax())));
        r.playToServer(C2SPayloads.SelectAbility.TYPE, C2SPayloads.SelectAbility.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> AbilityService.handleSelect((ServerPlayer) ctx.player(), p.slot(), p.weapon(), p.ability())));

        r.playToClient(FxPayload.TYPE, FxPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> SidedHooks.get().handleFx(p)));
        r.playToClient(ProgressionEventPayload.TYPE, ProgressionEventPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> SidedHooks.get().handleProgression(p)));
        r.playToClient(DeedPayload.TYPE, DeedPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> SidedHooks.get().handleDeed(p)));
        r.playToServer(QuestPayloads.Claim.TYPE, QuestPayloads.Claim.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> com.fantasyweapons.quest.QuestService.claim((ServerPlayer) ctx.player(), p.quest())));
        r.playToServer(QuestPayloads.ClaimAll.TYPE, QuestPayloads.ClaimAll.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> com.fantasyweapons.quest.QuestService.claimAll((ServerPlayer) ctx.player())));
        r.playToServer(QuestPayloads.ChooseClass.TYPE, QuestPayloads.ChooseClass.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> com.fantasyweapons.playerclass.ClassService.choose((ServerPlayer) ctx.player(),
                        com.fantasyweapons.playerclass.PlayerClass.byId(p.playerClass()))));
        r.playToClient(QuestPayloads.OpenClasses.TYPE, QuestPayloads.OpenClasses.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> SidedHooks.get().handleOpenClasses()));
        r.playToClient(QuestPayloads.Completed.TYPE, QuestPayloads.Completed.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> SidedHooks.get().handleQuestCompleted(p.quest())));
    }
}
