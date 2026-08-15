package com.kyroxova.lootgamesolver.minecraft;

import java.util.Collections;
import java.util.Map;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperBoard;
import com.kyroxova.lootgamesolver.solver.sudoku.SudokuBoard;

/** A fresh, client-synchronised board snapshot plus its opaque LootGames object. */
public final class DetectedGame {

    public final MiniGame type;
    public final Object game;
    public final MinesweeperBoard minesweeper;
    public final SudokuBoard sudoku;
    public final int[][] sudokuPlayerValues;
    public final boolean[][][] sudokuNotes;
    public final GameOfLightBoard gol;
    public final String signature;
    public final Map<CellPosition, int[]> blockPositions;

    private DetectedGame(MiniGame type, Object game, MinesweeperBoard minesweeper, SudokuBoard sudoku,
        int[][] sudokuPlayerValues, boolean[][][] sudokuNotes, GameOfLightBoard gol, String signature,
        Map<CellPosition, int[]> blockPositions) {
        this.type = type;
        this.game = game;
        this.minesweeper = minesweeper;
        this.sudoku = sudoku;
        this.sudokuPlayerValues = sudokuPlayerValues;
        this.sudokuNotes = sudokuNotes;
        this.gol = gol;
        this.signature = signature;
        this.blockPositions = blockPositions != null ? blockPositions : Collections.<CellPosition, int[]>emptyMap();
    }

    public static DetectedGame minesweeper(Object game, MinesweeperBoard board, String signature,
        Map<CellPosition, int[]> blockPositions) {
        return new DetectedGame(MiniGame.MINESWEEPER, game, board, null, null, null, null, signature, blockPositions);
    }

    public static DetectedGame sudoku(Object game, SudokuBoard board, int[][] playerValues, boolean[][][] sudokuNotes,
        String signature, Map<CellPosition, int[]> blockPositions) {
        return new DetectedGame(
            MiniGame.SUDOKU,
            game,
            null,
            board,
            playerValues,
            sudokuNotes,
            null,
            signature,
            blockPositions);
    }

    public static DetectedGame gameOfLight(Object game, GameOfLightBoard board, String signature,
        Map<CellPosition, int[]> blockPositions) {
        return new DetectedGame(MiniGame.GAME_OF_LIGHT, game, null, null, null, null, board, signature, blockPositions);
    }
}
