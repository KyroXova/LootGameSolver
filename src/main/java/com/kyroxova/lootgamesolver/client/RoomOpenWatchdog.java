package com.kyroxova.lootgamesolver.client;

/** Bounds attempts to open an obstructed room without timing an active solver. */
final class RoomOpenWatchdog {

    enum Action {
        WAIT,
        RETRY,
        SKIP
    }

    private int ticks;
    private int[] room;

    Action tick(int[] currentRoom, boolean eligible) {
        if (!eligible || currentRoom == null) {
            reset();
            return Action.WAIT;
        }
        if (room == null || room[0] != currentRoom[0]
            || room[2] != currentRoom[2]
            || Math.abs(room[1] - currentRoom[1]) > 4) {
            room = currentRoom.clone();
            ticks = 0;
        }
        ticks++;
        if (ticks >= 600) return Action.SKIP;
        return ticks % 150 == 0 ? Action.RETRY : Action.WAIT;
    }

    void reset() {
        ticks = 0;
        room = null;
    }
}
