package com.kyroxova.lootgamesolver.solver.sudoku;

import com.kyroxova.lootgamesolver.core.GameBoard;

/** Mutable 9x9 solver model; zero is an empty cell. */
public final class SudokuBoard implements GameBoard {

    public static final int SIZE = 9;
    private final int[][] values = new int[SIZE][SIZE];

    public SudokuBoard() {}

    public SudokuBoard(int[][] source) {
        if (source.length != SIZE) throw new IllegalArgumentException("Sudoku needs 9 rows");
        for (int row = 0; row < SIZE; row++) {
            if (source[row].length != SIZE) throw new IllegalArgumentException("Sudoku needs 9 columns");
            for (int col = 0; col < SIZE; col++) set(row, col, source[row][col]);
        }
    }

    public int getWidth() {
        return SIZE;
    }

    public int getHeight() {
        return SIZE;
    }

    public int get(int row, int col) {
        return values[row][col];
    }

    public void set(int row, int col, int value) {
        if (value < 0 || value > 9) throw new IllegalArgumentException("Digit must be 0..9");
        values[row][col] = value;
    }

    public SudokuBoard copy() {
        return new SudokuBoard(values);
    }
}
