package com.kyroxova.lootgamesolver.solver.minesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

public class MinesweeperSolverTest {

    @Test
    public void missingBoardIsReportedWithoutThrowing() {
        MinesweeperSolver solver = new MinesweeperSolver();

        assertEquals(false, solver.canSolve(null));
        assertEquals(
            SolveResult.Status.INVALID,
            solver.solve(null)
                .getStatus());
    }

    @Test
    public void identifiesMineWhenAllUnknownNeighborsAreRequired() {
        MinesweeperBoard board = new MinesweeperBoard(2, 2, 1);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.flagged());
        board.set(0, 1, MinesweeperCell.unknown());
        board.set(1, 1, MinesweeperCell.unknown());
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertTrue(has(result, SolverAction.Type.REVEAL, 0, 1));
        assertTrue(has(result, SolverAction.Type.REVEAL, 1, 1));
    }

    @Test
    public void identifiesDeterministicMine() {
        MinesweeperBoard board = new MinesweeperBoard(2, 1, 1);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.unknown());
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_MINE, result.getStatus());
        assertTrue(has(result, SolverAction.Type.FLAG, 1, 0));
    }

    @Test
    public void enumeratesOverlappingConstraints() {
        MinesweeperBoard board = new MinesweeperBoard(3, 3, 2);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.revealed(1));
        board.set(2, 0, MinesweeperCell.revealed(1));
        board.set(0, 1, MinesweeperCell.unknown());
        board.set(1, 1, MinesweeperCell.unknown());
        board.set(2, 1, MinesweeperCell.unknown());
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertTrue(has(result, SolverAction.Type.REVEAL, 2, 1));
    }

    @Test
    public void handlesSolvedBoard() {
        MinesweeperBoard board = new MinesweeperBoard(2, 2, 1);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.flagged());
        board.set(0, 1, MinesweeperCell.revealed(1));
        board.set(1, 1, MinesweeperCell.revealed(1));
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.SOLVED, result.getStatus());
    }

    @Test
    public void rejectsContradictoryBoard() {
        MinesweeperBoard board = new MinesweeperBoard(2, 1, 1);
        board.set(0, 0, MinesweeperCell.revealed(2));
        assertEquals(
            SolveResult.Status.INVALID,
            new MinesweeperSolver().solve(board)
                .getStatus());
    }

    @Test
    public void rejectsLocalFlagDeductionThatExceedsGlobalMineCount() {
        MinesweeperBoard board = new MinesweeperBoard(2, 1, 0);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.unknown());

        assertEquals(
            SolveResult.Status.INVALID,
            new MinesweeperSolver().solve(board)
                .getStatus());
    }

    @Test
    public void selectsSafestUnconstrainedCellWhenNoCluesRemain() {
        MinesweeperBoard board = new MinesweeperBoard(2, 2, 1);
        board.set(0, 0, MinesweeperCell.unknown());
        board.set(1, 0, MinesweeperCell.unknown());
        board.set(0, 1, MinesweeperCell.unknown());
        board.set(1, 1, MinesweeperCell.unknown());
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.PROBABILISTIC, result.getStatus());
        assertEquals(
            1,
            result.getActions()
                .size());
        assertEquals(
            SolverAction.Type.REVEAL,
            result.getActions()
                .get(0).type);
        assertEquals(0.75D, result.getBestSafetyProbability(), 0.000001D);
    }

    @Test
    public void usesGlobalMineCountToResolveDisconnectedChoicePatterns() {
        // The two clues permit either (safe, mine, safe) or (mine, safe, mine).
        // One adjacent flag and the total mine count allow only the first pattern.
        MinesweeperBoard board = new MinesweeperBoard(3, 2, 2);
        board.set(0, 0, MinesweeperCell.revealed(2));
        board.set(1, 0, MinesweeperCell.flagged());
        board.set(2, 0, MinesweeperCell.revealed(2));

        SolveResult result = new MinesweeperSolver().solve(board);

        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertTrue(has(result, SolverAction.Type.REVEAL, 0, 1));
        assertTrue(has(result, SolverAction.Type.FLAG, 1, 1));
        assertTrue(has(result, SolverAction.Type.REVEAL, 2, 1));
    }

    @Test
    public void rejectsFrontierSolutionsThatConflictWithTheGlobalMineCount() {
        MinesweeperBoard board = new MinesweeperBoard(3, 2, 0);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(2, 0, MinesweeperCell.revealed(1));

        assertEquals(
            SolveResult.Status.INVALID,
            new MinesweeperSolver().solve(board)
                .getStatus());
    }

    @Test
    public void doesNotTreatOversizedFrontierAsUnconstrainedForGuessing() {
        MinesweeperBoard board = new MinesweeperBoard(20, 20, 100);
        for (int y = 2; y <= 16; y += 2) for (int x = 2; x <= 16; x += 2) board.set(x, y, MinesweeperCell.revealed(2));

        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.getStatus());
        assertTrue(
            result.getActions()
                .isEmpty());
    }

    @Test
    public void solvesOneTwoOnePattern() {
        // Row 0: 1, 2, 1
        // Row 1: ?, ?, ?
        // 1-2-1 pattern against straight wall requires:
        // (0, 1) = MINE (FLAG)
        // (1, 1) = SAFE (REVEAL)
        // (2, 1) = MINE (FLAG)
        MinesweeperBoard board = new MinesweeperBoard(3, 2, 2);
        board.set(0, 0, MinesweeperCell.revealed(1));
        board.set(1, 0, MinesweeperCell.revealed(2));
        board.set(2, 0, MinesweeperCell.revealed(1));
        board.set(0, 1, MinesweeperCell.unknown());
        board.set(1, 1, MinesweeperCell.unknown());
        board.set(2, 1, MinesweeperCell.unknown());

        SolveResult result = new MinesweeperSolver().solve(board);
        assertTrue(
            result.getStatus() == SolveResult.Status.GUARANTEED_SAFE
                || result.getStatus() == SolveResult.Status.GUARANTEED_MINE);
        assertTrue(has(result, SolverAction.Type.FLAG, 0, 1));
        assertTrue(has(result, SolverAction.Type.REVEAL, 1, 1));
        assertTrue(has(result, SolverAction.Type.FLAG, 2, 1));
    }

    @Test
    public void solvesLargeConnectedFrontier() {
        // Test a large connected board with consistent 1-1-1 repeating frontier (mines at 0, 3, 6, 9, 12)
        int size = 13;
        MinesweeperBoard board = new MinesweeperBoard(size, 4, 10);
        for (int x = 0; x < size; x++) {
            board.set(x, 0, MinesweeperCell.revealed(0));
            board.set(x, 1, MinesweeperCell.revealed(1));
            board.set(x, 2, MinesweeperCell.unknown());
        }
        // Set specific known flag at (0, 2)
        board.set(0, 2, MinesweeperCell.flagged());

        SolveResult result = new MinesweeperSolver().solve(board);
        // Since (0, 2) is flagged, (1, 2) and (2, 2) must be safe, (3, 2) must be flagged, etc.
        assertTrue(
            result.getStatus() == SolveResult.Status.GUARANTEED_SAFE
                || result.getStatus() == SolveResult.Status.GUARANTEED_MINE);
        assertTrue(has(result, SolverAction.Type.REVEAL, 1, 2));
        assertTrue(has(result, SolverAction.Type.REVEAL, 2, 2));
        assertTrue(has(result, SolverAction.Type.FLAG, 3, 2));
    }

    private boolean has(SolveResult result, SolverAction.Type type, int x, int y) {
        for (SolverAction action : result.getActions())
            if (action.type == type && action.position.equals(new CellPosition(x, y))) return true;
        return false;
    }
}
