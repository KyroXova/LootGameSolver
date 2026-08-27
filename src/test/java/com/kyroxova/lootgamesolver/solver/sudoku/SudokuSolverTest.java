package com.kyroxova.lootgamesolver.solver.sudoku;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.SolveResult;

public class SudokuSolverTest {

    @Test
    public void solvesStandardPuzzle() {
        int[][] puzzle = { { 5, 3, 0, 0, 7, 0, 0, 0, 0 }, { 6, 0, 0, 1, 9, 5, 0, 0, 0 }, { 0, 9, 8, 0, 0, 0, 0, 6, 0 },
            { 8, 0, 0, 0, 6, 0, 0, 0, 3 }, { 4, 0, 0, 8, 0, 3, 0, 0, 1 }, { 7, 0, 0, 0, 2, 0, 0, 0, 6 },
            { 0, 6, 0, 0, 0, 0, 2, 8, 0 }, { 0, 0, 0, 4, 1, 9, 0, 0, 5 }, { 0, 0, 0, 0, 8, 0, 0, 7, 9 } };
        SolveResult result = new SudokuSolver().solve(new SudokuBoard(puzzle));
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        assertEquals(
            51,
            result.getActions()
                .size());
    }

    @Test
    public void handlesAlreadySolvedPuzzle() {
        int[][] solved = { { 5, 3, 4, 6, 7, 8, 9, 1, 2 }, { 6, 7, 2, 1, 9, 5, 3, 4, 8 }, { 1, 9, 8, 3, 4, 2, 5, 6, 7 },
            { 8, 5, 9, 7, 6, 1, 4, 2, 3 }, { 4, 2, 6, 8, 5, 3, 7, 9, 1 }, { 7, 1, 3, 9, 2, 4, 8, 5, 6 },
            { 9, 6, 1, 5, 3, 7, 2, 8, 4 }, { 2, 8, 7, 4, 1, 9, 6, 3, 5 }, { 3, 4, 5, 2, 8, 6, 1, 7, 9 } };
        SolveResult result = new SudokuSolver().solve(new SudokuBoard(solved));
        assertEquals(SolveResult.Status.SOLVED, result.getStatus());
        assertEquals(
            0,
            result.getActions()
                .size());
    }

    @Test
    public void rejectsDuplicateGiven() {
        int[][] puzzle = new int[9][9];
        puzzle[0][0] = 1;
        puzzle[0][1] = 1;
        assertEquals(
            SolveResult.Status.INVALID,
            new SudokuSolver().solve(new SudokuBoard(puzzle))
                .getStatus());
    }

    @Test
    public void rejectsUnsolvablePuzzle() {
        // Given that leads to no valid completion
        int[][] puzzle = new int[9][9];
        puzzle[0][0] = 1;
        puzzle[0][1] = 2;
        puzzle[0][2] = 3;
        puzzle[0][3] = 4;
        puzzle[0][4] = 5;
        puzzle[0][5] = 6;
        puzzle[0][6] = 7;
        puzzle[0][7] = 8;
        puzzle[1][0] = 9;
        puzzle[1][1] = 8;
        puzzle[1][2] = 7;
        puzzle[1][3] = 6;
        puzzle[1][4] = 5;
        puzzle[1][5] = 4;
        puzzle[1][6] = 3;
        puzzle[1][7] = 2;
        puzzle[0][8] = 9;
        puzzle[1][8] = 1;
        puzzle[2][8] = 9; // Contradiction in column 8
        assertEquals(
            SolveResult.Status.INVALID,
            new SudokuSolver().solve(new SudokuBoard(puzzle))
                .getStatus());
    }

    @Test
    public void testOptimalClickPlanning() {
        // Forward cases (0 -> 1..5)
        com.kyroxova.lootgamesolver.client.SolverSession.ClickPlan p1 = com.kyroxova.lootgamesolver.client.SolverSession
            .calculateOptimalClicks(0, 1);
        assertEquals(1, p1.clicks);
        org.junit.Assert.assertFalse(p1.useSneak);

        com.kyroxova.lootgamesolver.client.SolverSession.ClickPlan p5 = com.kyroxova.lootgamesolver.client.SolverSession
            .calculateOptimalClicks(0, 5);
        assertEquals(5, p5.clicks);
        org.junit.Assert.assertFalse(p5.useSneak);

        // Backward / Sneak cases (0 -> 6..9)
        com.kyroxova.lootgamesolver.client.SolverSession.ClickPlan p6 = com.kyroxova.lootgamesolver.client.SolverSession
            .calculateOptimalClicks(0, 6);
        assertEquals(4, p6.clicks);
        org.junit.Assert.assertTrue(p6.useSneak);

        com.kyroxova.lootgamesolver.client.SolverSession.ClickPlan p9 = com.kyroxova.lootgamesolver.client.SolverSession
            .calculateOptimalClicks(0, 9);
        assertEquals(1, p9.clicks);
        org.junit.Assert.assertTrue(p9.useSneak);

        // Non-zero start value
        com.kyroxova.lootgamesolver.client.SolverSession.ClickPlan pFrom2To9 = com.kyroxova.lootgamesolver.client.SolverSession
            .calculateOptimalClicks(2, 9);
        assertEquals(3, pFrom2To9.clicks);
        org.junit.Assert.assertTrue(pFrom2To9.useSneak);
    }

    @Test
    public void handlesWrongPlayerValue() {
        int[][] puzzle = { { 5, 3, 0, 0, 7, 0, 0, 0, 0 }, { 6, 0, 0, 1, 9, 5, 0, 0, 0 }, { 0, 9, 8, 0, 0, 0, 0, 6, 0 },
            { 8, 0, 0, 0, 6, 0, 0, 0, 3 }, { 4, 0, 0, 8, 0, 3, 0, 0, 1 }, { 7, 0, 0, 0, 2, 0, 0, 0, 6 },
            { 0, 6, 0, 0, 0, 0, 2, 8, 0 }, { 0, 0, 0, 4, 1, 9, 0, 0, 5 }, { 0, 0, 0, 0, 8, 0, 0, 7, 9 } };
        SudokuBoard board = new SudokuBoard(puzzle);
        // Correct value for (0, 2) is 4. Set a wrong player value of 1.
        board.setPlayerValue(0, 2, 1);

        SolveResult result = new SudokuSolver().solve(board);
        assertEquals(SolveResult.Status.GUARANTEED_SAFE, result.getStatus());
        // Should still generate actions for all 51 open cells (including the cell with wrong value 1)
        assertEquals(
            51,
            result.getActions()
                .size());

        // Find action for (col 2, row 0) and verify it specifies correct target 4
        com.kyroxova.lootgamesolver.core.SolverAction action = null;
        for (com.kyroxova.lootgamesolver.core.SolverAction a : result.getActions()) {
            if (a.position.x == 2 && a.position.y == 0) {
                action = a;
                break;
            }
        }
        org.junit.Assert.assertNotNull(action);
        assertEquals(4, action.value);
    }
}
