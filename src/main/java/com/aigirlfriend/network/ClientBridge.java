package com.aigirlfriend.network;

import com.aigirlfriend.network.payload.OpenMenuPayload;
import com.aigirlfriend.network.payload.SpeechPayload;
import com.aigirlfriend.network.payload.StatusPayload;

/**
 * Indirection for client-bound packet handling so common code never loads client classes on a dedicated
 * server. The client installs a real implementation during client setup.
 */
public interface ClientBridge {
    void openMenu(OpenMenuPayload payload);

    void status(StatusPayload payload);

    void speech(SpeechPayload payload);

    ClientBridge NOOP = new ClientBridge() {
        @Override
        public void openMenu(OpenMenuPayload payload) {}

        @Override
        public void status(StatusPayload payload) {}

        @Override
        public void speech(SpeechPayload payload) {}
    };

    final class Holder {
        public static ClientBridge instance = NOOP;

        private Holder() {}
    }
}
