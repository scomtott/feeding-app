package com.example.springboot.homeassistant.websocket;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.socket.client.WebSocketConnectionManager;

class HomeAssistantWebSocketWatchdogTest {

    private HomeAssistantWebSocketHandler handler;
    private WebSocketConnectionManager connectionManager;
    private HomeAssistantWebSocketWatchdog watchdog;

    @BeforeEach
    void setUp() {
        handler = mock(HomeAssistantWebSocketHandler.class);
        connectionManager = mock(WebSocketConnectionManager.class);
        watchdog = new HomeAssistantWebSocketWatchdog(handler, connectionManager);
    }

    @Test
    void reconnectsWhenThereIsNoOpenSession() {
        when(handler.isConnected()).thenReturn(false);

        watchdog.check();

        assertReconnected();
    }

    @Test
    void reconnectsWhenConnectedButNotSubscribed() {
        when(handler.isConnected()).thenReturn(true);
        when(handler.isSubscribed()).thenReturn(false);

        watchdog.check();

        assertReconnected();
    }

    @Test
    void reconnectsWhenThePreviousPingWentUnanswered() {
        when(handler.isConnected()).thenReturn(true);
        when(handler.isSubscribed()).thenReturn(true);
        when(handler.isPingOutstanding()).thenReturn(true);

        watchdog.check();

        assertReconnected();
    }

    @Test
    void pingsWhenEverythingLooksHealthy() {
        when(handler.isConnected()).thenReturn(true);
        when(handler.isSubscribed()).thenReturn(true);
        when(handler.isPingOutstanding()).thenReturn(false);

        watchdog.check();

        verify(handler).sendPing();
        verifyNoInteractions(connectionManager);
    }

    /**
     * The order is the whole point of this class.
     *
     * <p>{@code ConnectionManagerSupport.start()} is a no-op unless its {@code running} flag is
     * clear, and a dropped socket never clears that flag - only {@code stop()} does. A reconnect
     * that called {@code start()} alone would look correct, be reviewed as correct, and silently do
     * nothing, leaving the integration just as dead as it was.
     */
    private void assertReconnected() {
        InOrder inOrder = inOrder(connectionManager);
        inOrder.verify(connectionManager).stop();
        inOrder.verify(connectionManager).start();

        verify(handler, never()).sendPing();
    }
}
