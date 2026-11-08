package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolverAction;

public class GameOfLightOverlayTest {

    @Test
    public void groupsRepeatedPositionsAndKeepsOriginalSequenceNumbers() {
        CellPosition a = new CellPosition(0, 1), b = new CellPosition(2, 1);
        SolverAction first = new SolverAction(SolverAction.Type.REVEAL, a, 0, 1D);
        SolverAction second = new SolverAction(SolverAction.Type.REVEAL, b, 0, 1D);
        Map<CellPosition, String> labels = WorldOverlayRenderer.sequenceLabels(Arrays.asList(first, second, first), 1);
        assertEquals("#2, #4", labels.get(a));
        assertEquals("#3", labels.get(b));
        assertEquals(2, labels.size());
        labels = WorldOverlayRenderer.sequenceLabels(Arrays.asList(second, first), 2);
        assertEquals("#4", labels.get(a));
        assertEquals("#3", labels.get(b));
    }
}
