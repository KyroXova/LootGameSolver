package com.kyroxova.lootgamesolver.solver.gol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolveResult;

public class GameOfLightSolverTest {

    @Test
    public void handlesWaitingStartStage() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_START, null);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertEquals(
            1,
            result.getActions()
                .size());
        assertEquals(
            new CellPosition(1, 1),
            result.getActions()
                .get(0).position);
    }

    @Test
    public void handlesWaitingForSequenceStage() {
        List<CellPosition> seq = new ArrayList<CellPosition>();
        seq.add(new CellPosition(0, 0));
        seq.add(new CellPosition(2, 2));

        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, seq);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertEquals(
            2,
            result.getActions()
                .size());
        assertEquals(
            new CellPosition(0, 0),
            result.getActions()
                .get(0).position);
        assertEquals(
            new CellPosition(2, 2),
            result.getActions()
                .get(1).position);
    }

    @Test
    public void handlesShowSequenceStage() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_SHOW_SEQUENCE, null);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.getStatus());
        assertTrue(
            result.getActions()
                .isEmpty());
    }

    @Test
    public void handlesExpandingBoardCenterStart() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_START, null, 5);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertEquals(
            1,
            result.getActions()
                .size());
        assertEquals(
            new CellPosition(2, 2),
            result.getActions()
                .get(0).position);
    }

    @Test
    public void handlesCompletedSequence() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, null, 3, true);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.getStatus());
        assertTrue(
            result.getActions()
                .isEmpty());
        assertEquals("Sequence complete! Waiting for next round...", result.getMessage());
    }
}
