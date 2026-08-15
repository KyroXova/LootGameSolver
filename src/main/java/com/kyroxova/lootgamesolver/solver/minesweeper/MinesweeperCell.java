package com.kyroxova.lootgamesolver.solver.minesweeper;

import com.kyroxova.lootgamesolver.core.GameCell;

public final class MinesweeperCell implements GameCell {

    private final CellState state;
    private final int number;
    private final boolean questionMarked;

    private MinesweeperCell(CellState state, int number, boolean questionMarked) {
        this.state = state;
        this.number = number;
        this.questionMarked = questionMarked;
    }

    public static MinesweeperCell unknown() {
        return new MinesweeperCell(CellState.UNKNOWN, -1, false);
    }

    public static MinesweeperCell question() {
        return new MinesweeperCell(CellState.UNKNOWN, -1, true);
    }

    public static MinesweeperCell flagged() {
        return new MinesweeperCell(CellState.FLAGGED, -1, false);
    }

    public static MinesweeperCell revealed(int number) {
        if (number < 0 || number > 8) throw new IllegalArgumentException("Minesweeper number must be 0..8");
        return new MinesweeperCell(CellState.REVEALED, number, false);
    }

    public CellState getState() {
        return state;
    }

    public int getNumber() {
        return number;
    }

    public boolean isQuestionMarked() {
        return questionMarked;
    }
}
