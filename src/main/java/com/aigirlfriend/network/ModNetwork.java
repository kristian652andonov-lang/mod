package com.aigirlfriend.network;

import com.aigirlfriend.network.payload.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static final String VERSION = "1";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        // client -> server (always validated server side)
        r.playToServer(OpenMenuRequestPayload.TYPE, OpenMenuRequestPayload.STREAM_CODEC, ServerPayloadHandler::openMenu);
        r.playToServer(PerformActionPayload.TYPE, PerformActionPayload.STREAM_CODEC, ServerPayloadHandler::performAction);
        r.playToServer(TalkPayload.TYPE, TalkPayload.STREAM_CODEC, ServerPayloadHandler::talk);
        r.playToServer(StatusRequestPayload.TYPE, StatusRequestPayload.STREAM_CODEC, ServerPayloadHandler::statusRequest);
        r.playToServer(RequestResponsePayload.TYPE, RequestResponsePayload.STREAM_CODEC, ServerPayloadHandler::requestResponse);
        r.playToServer(SitPayload.TYPE, SitPayload.STREAM_CODEC, ServerPayloadHandler::sit);
        r.playToServer(CancelInteractionPayload.TYPE, CancelInteractionPayload.STREAM_CODEC, ServerPayloadHandler::cancel);
        r.playToServer(OutfitPayload.TYPE, OutfitPayload.STREAM_CODEC, ServerPayloadHandler::outfit);
        // server -> client
        r.playToClient(OpenMenuPayload.TYPE, OpenMenuPayload.STREAM_CODEC, (p, ctx) -> ClientBridge.Holder.instance.openMenu(p));
        r.playToClient(StatusPayload.TYPE, StatusPayload.STREAM_CODEC, (p, ctx) -> ClientBridge.Holder.instance.status(p));
        r.playToClient(SpeechPayload.TYPE, SpeechPayload.STREAM_CODEC, (p, ctx) -> ClientBridge.Holder.instance.speech(p));
    }
}
