package com.kyroxova.lootgamesolver.client;

import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.solver.minesweeper.CellState;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperBoard;

final class MinesweeperActionGuard {

    private MinesweeperActionGuard() {}

    static boolean complete(MinesweeperBoard board) {
        int hidden = 0;
        for (int y = 0; y < board.getHeight(); y++) for (int x = 0; x < board.getWidth(); x++) {
            if (board.get(x, y)
                .getState() != CellState.REVEALED) hidden++;
        }
        return hidden == board.getMineCount();
    }

    static boolean valid(MinesweeperBoard board, SolverAction action) {
        if (action == null || !board.contains(action.position) || complete(board)) return false;
        CellState state = board.get(action.position)
            .getState();
        return state != CellState.REVEALED && state != CellState.FLAGGED;
    }
}
