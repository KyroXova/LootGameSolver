package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActionAckWatchdogTest {

    @Test
    public void waitsForBoardAcknowledgementBeforeTimeout() {
        assertEquals(ActionAckWatchdog.Decision.WAIT, ActionAckWatchdog.decide(true, false, 1_000L, 2_499L, 1_500));
    }

    @Test
    public void stopsInsteadOfRepeatingAnUnacknowledgedClick() {
        assertEquals(ActionAckWatchdog.Decision.STOP, ActionAckWatchdog.decide(true, false, 1_000L, 2_500L, 1_500));
    }

    @Test
    public void visibleSudokuDigitChangeAcknowledgesTheClick() {
        assertEquals(ActionAckWatchdog.Decision.SEND, ActionAckWatchdog.decide(true, true, 1_000L, 1_001L, 1_500));
    }

    @Test
    public void noPendingClickCanBeSentImmediately() {
        assertEquals(ActionAckWatchdog.Decision.SEND, ActionAckWatchdog.decide(false, false, 0L, 10_000L, 1_500));
    }
}
