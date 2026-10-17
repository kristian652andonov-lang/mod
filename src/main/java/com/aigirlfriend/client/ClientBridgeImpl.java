package com.aigirlfriend.client;

import com.aigirlfriend.client.screen.InteractionWheelScreen;
import com.aigirlfriend.client.screen.StatusScreen;
import com.aigirlfriend.client.screen.TalkScreen;
import com.aigirlfriend.client.voice.VoiceManager;
import com.aigirlfriend.network.ClientBridge;
import com.aigirlfriend.network.payload.OpenMenuPayload;
import com.aigirlfriend.network.payload.SpeechPayload;
import com.aigirlfriend.network.payload.StatusPayload;
import net.minecraft.client.Minecraft;

public final class ClientBridgeImpl implements ClientBridge {
    @Override
    public void openMenu(OpenMenuPayload payload) {
        ClientState.putStatus(payload.entityId(), payload.status());
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof InteractionWheelScreen) {
            mc.setScreen(new InteractionWheelScreen(payload.entityId(), payload.actions()));
        }
    }

    @Override
    public void status(StatusPayload payload) {
        ClientState.putStatus(payload.entityId(), payload.status());
    }

    @Override
    public void speech(SpeechPayload payload) {
        ClientState.putSpeech(payload.entityId(), payload.text(), payload.emotion(), payload.typing(), payload.duration());
        if (!payload.typing()) VoiceManager.speak(payload.entityId(), payload.text(), payload.emotion());
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StatusScreen || mc.screen instanceof TalkScreen) {
            // screens read the shared state every frame; nothing else to do
        }
    }
}
