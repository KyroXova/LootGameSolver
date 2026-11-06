package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;

public class GameOfLightAcknowledgementTest {

    private static DetectedGame snapshot(int currentSymbol, String stage) {
        GameOfLightBoard board = new GameOfLightBoard(
            stage,
            Collections.singletonList(new CellPosition(1, 1)),
            3,
            false,
            currentSymbol);
        return DetectedGame.gameOfLight(null, board, stage + ":" + currentSymbol, null);
    }

    @Test
    public void waitsForServerToAcceptEachSymbolAndDoesNotAdvanceOnRetry() {
        LootGamesBridge bridge = new LootGamesBridge();
        SolverAction action = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(1, 1), 0, 1D);
        DetectedGame beforeClick = snapshot(0, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE);

        bridge.recordSubmittedAction(beforeClick);
        assertFalse(bridge.actionComplete(beforeClick, action));

        // A retry of the same pending click must not move the acknowledgement target.
        bridge.recordSubmittedAction(beforeClick);
        assertFalse(bridge.actionComplete(beforeClick, action));

        assertTrue(bridge.actionComplete(snapshot(1, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE), action));
    }

    @Test
    public void stageTransitionCompletesFinalSymbol() {
        LootGamesBridge bridge = new LootGamesBridge();
        SolverAction action = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(1, 1), 0, 1D);
        bridge.recordSubmittedAction(snapshot(0, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE));

        assertTrue(bridge.actionComplete(snapshot(0, GameOfLightBoard.STAGE_SHOW_SEQUENCE), action));
    }
}
