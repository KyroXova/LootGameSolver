package com.kyroxova.lootgamesolver.client.puzzle;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RoomOpenWatchdogTest {

    @Test
    public void retriesThreeTimesThenSkipsAfterThirtySeconds() {
        RoomOpenWatchdog watchdog = new RoomOpenWatchdog();
        int[] room = { 12, 40, 24 };
        for (int tick = 1; tick <= 600; tick++) {
            RoomOpenWatchdog.Action expected = tick == 600 ? RoomOpenWatchdog.Action.SKIP
                : tick % 150 == 0 ? RoomOpenWatchdog.Action.RETRY : RoomOpenWatchdog.Action.WAIT;
            assertEquals("tick " + tick, expected, watchdog.tick(room, true));
            if (tick == 150) assertEquals("Retry 1/3: 23s", watchdog.status());
        }
    }

    @Test
    public void solvingOrChangingRoomsClearsTheTimer() {
        RoomOpenWatchdog watchdog = new RoomOpenWatchdog();
        int[] room = { 12, 40, 24 };
        for (int tick = 0; tick < 149; tick++) watchdog.tick(room, true);
        watchdog.tick(room, false);
        assertEquals(RoomOpenWatchdog.Action.WAIT, watchdog.tick(room, true));
        for (int tick = 0; tick < 148; tick++) watchdog.tick(room, true);
        assertEquals(RoomOpenWatchdog.Action.WAIT, watchdog.tick(new int[] { 100, 40, 24 }, true));
    }
}
