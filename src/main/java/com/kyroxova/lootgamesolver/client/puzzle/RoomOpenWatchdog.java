package com.kyroxova.lootgamesolver.client.puzzle;

/** Bounds attempts to open an obstructed room without timing an active solver. */
public final class RoomOpenWatchdog {

    public enum Action {
        WAIT,
        RETRY,
        SKIP
    }

    private int ticks;
    private int[] room;

    public Action tick(int[] currentRoom, boolean eligible) {
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

    public void reset() {
        ticks = 0;
        room = null;
    }

    public String status() {
        return "Retry " + Math.min(3, ticks / 150) + "/3: " + Math.max(0, (600 - ticks + 19) / 20) + "s";
    }
}
