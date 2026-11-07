package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusTextTest {

    @Test
    public void explorerStatusesUseShortConsistentLabels() {
        assertEquals("Following Route", StatusText.explorer("Following checked route"));
        assertEquals("Searching Structures", StatusText.explorer("Puzzle solved; searching for the next structure"));
        assertEquals("Planning Route", StatusText.explorer("Checking existing route: 325 positions"));
        assertEquals("Planning Route", StatusText.explorer("Waiting for a safe route, then retrying"));
        assertEquals("Stage Complete", StatusText.explorer("Puzzle solved"));
        assertEquals("Following Exit", StatusText.explorer("Following saved access route out"));
        assertEquals("Following Exit", StatusText.explorer("Returning along saved access route"));
        assertEquals("Entrance Blocked", StatusText.explorer("No safe surface dig entrance in range"));
        assertEquals("Stop: Low Air", StatusText.explorer("Stopped: low air underwater"));
    }

    @Test
    public void solverStatusesKeepStageAndActionMeaning() {
        assertEquals("Waiting: Board", StatusText.solver("Waiting for Sudoku board update..."));
        assertEquals("Solve: Flag", StatusText.solver("Flagging (4 remaining)"));
        assertEquals("Stage: Watch", StatusText.solver("Watching sequence playback..."));
        assertEquals("Stage Complete", StatusText.solver("All Sudoku numbers confirmed. Waiting for next stage..."));
        assertEquals("Stop: No Ack", StatusText.solver("Action timed out; solver stopped"));
    }

    @Test
    public void unknownStatusesAreBoundedForTheHud() {
        String status = StatusText.explorer("A long unfamiliar navigation status that must remain compact");
        assertTrue(status.length() <= 36);
    }
}
