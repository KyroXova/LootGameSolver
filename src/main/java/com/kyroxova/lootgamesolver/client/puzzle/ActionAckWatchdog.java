package com.kyroxova.lootgamesolver.client.puzzle;

public final class ActionAckWatchdog {

    public enum Decision {
        SEND,
        WAIT,
        STOP
    }

    private ActionAckWatchdog() {}

    public static Decision decide(boolean pending, boolean acknowledgedProgress, long sentAt, long now, int timeoutMs) {
        if (!pending || acknowledgedProgress) return Decision.SEND;
        return now - sentAt >= timeoutMs ? Decision.STOP : Decision.WAIT;
    }
}
