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
        assertEquals("Ascending to Surface", StatusText.explorer("Following saved access route out"));
        assertEquals("Ascending to Surface", StatusText.explorer("Returning along saved access route"));
        assertEquals("Entrance Blocked", StatusText.explorer("No safe surface dig entrance in range"));
        assertEquals("Low Air Underwater", StatusText.explorer("Stopped: low air underwater"));
        assertEquals("Pre-routing to Puzzle", StatusText.explorer("Pre-routing to structure above dungeon"));
        assertEquals("Clearing Foliage", StatusText.explorer("Clearing room foliage"));
        assertEquals(
            "Bags Full or Unavailable",
            StatusText.explorer("Inventory and backpacks full; rewards remain in chest"));
    }

    @Test
    public void solverStatusesKeepStageAndActionMeaning() {
        assertEquals("Waiting for Board", StatusText.solver("Waiting for Sudoku board update..."));
        assertEquals("Flagging Tile", StatusText.solver("Flagging (4 remaining)"));
        assertEquals("Watching Sequence", StatusText.solver("Watching sequence playback..."));
        assertEquals("Stage Complete", StatusText.solver("All Sudoku numbers confirmed. Waiting for next stage..."));
        assertEquals("Action Timed Out", StatusText.solver("Action timed out; solver stopped"));
        assertEquals("Moving to Number", StatusText.solver("Navigating to Setting Number 4"));
        assertEquals("Setting Number", StatusText.solver("Setting 4 (1 remaining)"));
    }

    @Test
    public void unknownStatusesAreBoundedForTheHud() {
        String status = StatusText.explorer("A long unfamiliar navigation status that must remain compact");
        assertTrue(status.length() <= 24);
    }
}
