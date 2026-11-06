package com.kyroxova.lootgamesolver.client;

/** Decides whether an unacknowledged board click can continue, must wait, or must stop. */
final class ActionAckWatchdog {

    enum Decision {
        SEND,
        WAIT,
        STOP
    }

    private ActionAckWatchdog() {}

    static Decision decide(boolean pending, boolean acknowledgedProgress, long sentAt, long now, int timeoutMs) {
        if (!pending || acknowledgedProgress) return Decision.SEND;
        return now - sentAt >= timeoutMs ? Decision.STOP : Decision.WAIT;
    }
}
