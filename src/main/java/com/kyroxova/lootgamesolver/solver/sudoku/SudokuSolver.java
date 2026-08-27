package com.kyroxova.lootgamesolver.solver.sudoku;

import java.util.ArrayList;
import java.util.List;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGameSolver;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

/** Candidate propagation with a minimum-remaining-values backtracking fallback. */
public final class SudokuSolver implements MiniGameSolver<SudokuBoard> {

    public boolean canSolve(SudokuBoard board) {
        return isValid(board);
    }

    public SolveResult solve(SudokuBoard board) {
        if (!isValid(board)) return SolveResult.none(SolveResult.Status.INVALID, "Sudoku has contradictory givens");
        SudokuBoard solved = board.copy();
        if (!solveRecursive(solved)) return SolveResult.none(SolveResult.Status.INVALID, "Sudoku has no solution");
        List<SolverAction> actions = new ArrayList<SolverAction>();
        for (int row = 0; row < SudokuBoard.SIZE; row++) {
            for (int col = 0; col < SudokuBoard.SIZE; col++) {
                if (board.get(row, col) == 0) {
                    int targetVal = solved.get(row, col);
                    int playerVal = board.getPlayerValue(row, col);
                    if (playerVal != targetVal) {
                        actions.add(
                            new SolverAction(SolverAction.Type.SET_VALUE, new CellPosition(col, row), targetVal, 1D));
                    }
                }
            }
        }
        return actions.isEmpty() ? SolveResult.none(SolveResult.Status.SOLVED, "Sudoku solved")
            : new SolveResult(
                SolveResult.Status.GUARANTEED_SAFE,
                actions,
                1D,
                "Sudoku solved by constraints/backtracking");
    }

    private boolean solveRecursive(SudokuBoard board) {
        int bestRow = -1, bestCol = -1, bestMask = 0, bestCount = 10;
        for (int row = 0; row < 9; row++) for (int col = 0; col < 9; col++) if (board.get(row, col) == 0) {
            int mask = candidates(board, row, col);
            int count = Integer.bitCount(mask);
            if (count == 0) return false;
            if (count < bestCount) {
                bestRow = row;
                bestCol = col;
                bestMask = mask;
                bestCount = count;
                if (count == 1) break;
            }
        }
        if (bestRow < 0) return true;
        for (int digit = 1; digit <= 9; digit++) if ((bestMask & (1 << digit)) != 0) {
            board.set(bestRow, bestCol, digit);
            if (solveRecursive(board)) return true;
            board.set(bestRow, bestCol, 0);
        }
        return false;
    }

    private boolean isValid(SudokuBoard board) {
        for (int row = 0; row < 9; row++) for (int col = 0; col < 9; col++) {
            int v = board.get(row, col);
            if (v != 0) {
                board.set(row, col, 0);
                boolean ok = (candidates(board, row, col) & (1 << v)) != 0;
                board.set(row, col, v);
                if (!ok) return false;
            }
        }
        return true;
    }

    private int candidates(SudokuBoard board, int row, int col) {
        int mask = 0x3FE;
        for (int i = 0; i < 9; i++) {
            mask &= ~(1 << board.get(row, i));
            mask &= ~(1 << board.get(i, col));
        }
        int baseRow = row / 3 * 3, baseCol = col / 3 * 3;
        for (int r = baseRow; r < baseRow + 3; r++)
            for (int c = baseCol; c < baseCol + 3; c++) mask &= ~(1 << board.get(r, c));
        return mask;
    }
}
