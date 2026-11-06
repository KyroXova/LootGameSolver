package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusTextTest {

    @Test
    public void explorerStatusesUseShortConsistentLabels() {
        assertEquals("Walk: Forward", StatusText.explorer("Following checked route"));
        assertEquals("Next Structure", StatusText.explorer("Puzzle solved; searching for the next structure"));
        assertEquals("Waiting: Reroute", StatusText.explorer("Checking existing route: 325 positions"));
        assertEquals("Stage Complete", StatusText.explorer("Puzzle solved"));
        assertEquals("Walk: Exit", StatusText.explorer("Following saved access route out"));
        assertEquals("Walk: Exit", StatusText.explorer("Returning along saved access route"));
        assertEquals("Entrance: Blocked", StatusText.explorer("No safe surface dig entrance in range"));
        assertEquals("Stop: Low Air", StatusText.explorer("Stopped: low air underwater"));
    }

    @Test
    public void solverStatusesKeepStageAndActionMeaning() {
        assertEquals("Waiting: Board", StatusText.solver("Waiting for Sudoku board update..."));
        assertEquals("Solve: Flag", StatusText.solver("Flagging (4 remaining)"));
        assertEquals("Stage: Watch", StatusText.solver("Watching sequence playback..."));
        assertEquals("Stage Complete", StatusText.solver("All Sudoku numbers confirmed. Waiting for next stage..."));
    }

    @Test
    public void unknownStatusesAreBoundedForTheHud() {
        String status = StatusText.explorer("A long unfamiliar navigation status that must remain compact");
        assertTrue(status.length() <= 24);
    }
}
