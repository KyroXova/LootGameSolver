package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperCell;

public class MinesweeperActionGuardTest {
    @Test
    public void blocksCompletedBoardsAndInvalidOrPreviouslyClickedCells() {
        MinesweeperBoard board = new MinesweeperBoard(2, 2, 1);
        SolverAction reveal = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(0, 0), 0, 1D);
        assertTrue(MinesweeperActionGuard.valid(board, reveal));
        board.set(0, 0, MinesweeperCell.revealed(1));
        assertFalse(MinesweeperActionGuard.valid(board, reveal));
        SolverAction outOfBounds = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(4, 4), 0, 1D);
        assertFalse(MinesweeperActionGuard.valid(board, outOfBounds));
        board.set(1, 0, MinesweeperCell.revealed(1));
        board.set(0, 1, MinesweeperCell.revealed(1));
        assertTrue(MinesweeperActionGuard.complete(board));
        SolverAction flag = new SolverAction(SolverAction.Type.FLAG, new CellPosition(1, 1), 0, 1D);
        assertFalse(MinesweeperActionGuard.valid(board, flag));
    }
}
