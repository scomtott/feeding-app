package com.example.springboot.homeassistant.websocket.messages;

/**
 * A client-initiated keepalive.
 *
 * <p>Home Assistant answers {@code {"type":"ping","id":N}} with {@code {"type":"pong","id":N}}, which
 * is the only reliable way to tell a live socket from a half-open one: after a network blip a socket
 * can still report itself open while delivering nothing, and such a socket fires no close callback.
 */
public record HaWsPingRequest(int id, String type) {
}
