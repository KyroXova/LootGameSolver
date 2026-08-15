package com.kyroxova.lootgamesolver.solver.minesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

public class MinesweeperSolverTest {

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
        MinesweeperBoard board = new MinesweeperBoard(3, 2, 2);
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
    public void handlesNoDeterministicMoveWithoutProbability() {
        MinesweeperBoard board = new MinesweeperBoard(2, 2, 1);
        board.set(0, 0, MinesweeperCell.unknown());
        board.set(1, 0, MinesweeperCell.unknown());
        board.set(0, 1, MinesweeperCell.unknown());
        board.set(1, 1, MinesweeperCell.unknown());
        SolveResult result = new MinesweeperSolver().solve(board);
        assertEquals(SolveResult.Status.NO_MOVE, result.getStatus());
    }

    private boolean has(SolveResult result, SolverAction.Type type, int x, int y) {
        for (SolverAction action : result.getActions())
            if (action.type == type && action.position.equals(new CellPosition(x, y))) return true;
        return false;
    }
}
