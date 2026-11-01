package com.kyroxova.lootgamesolver.solver.gol;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolveResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameOfLightSolverTest {

    @Test
    public void handlesWaitingStartStage() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_START, null);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.status());
        assertEquals(
            1,
            result.actions()
                .size());
        assertEquals(
            new CellPosition(1, 1),
            result.actions()
                .get(0).position());
    }

    @Test
    public void handlesWaitingForSequenceStage() {
        List<CellPosition> seq = new ArrayList<CellPosition>();
        seq.add(new CellPosition(0, 0));
        seq.add(new CellPosition(2, 2));

        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, seq);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.status());
        assertEquals(
            2,
            result.actions()
                .size());
        assertEquals(
            new CellPosition(0, 0),
            result.actions()
                .get(0).position());
        assertEquals(
            new CellPosition(2, 2),
            result.actions()
                .get(1).position());
    }

    @Test
    public void handlesShowSequenceStage() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_SHOW_SEQUENCE, null);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.status());
        assertTrue(
            result.actions()
                .isEmpty());
    }

    @Test
    public void handlesExpandingBoardCenterStart() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_START, null, 5);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.status());
        assertEquals(
            1,
            result.actions()
                .size());
        assertEquals(
            new CellPosition(2, 2),
            result.actions()
                .get(0).position());
    }

    @Test
    public void handlesCompletedSequence() {
        GameOfLightBoard board = new GameOfLightBoard(GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, null, 3, true);
        SolveResult result = new GameOfLightSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.status());
        assertTrue(
            result.actions()
                .isEmpty());
        assertEquals("Sequence complete! Waiting for next round...", result.message());
    }
}
