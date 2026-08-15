package com.kyroxova.lootgamesolver.solver.minesweeper;

import java.util.ArrayList;
import java.util.List;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.GameBoard;

public final class MinesweeperBoard implements GameBoard {

    private final int width;
    private final int height;
    private final int mineCount;
    private final MinesweeperCell[][] cells;

    public MinesweeperBoard(int width, int height, int mineCount) {
        if (width <= 0 || height <= 0 || mineCount < 0 || mineCount >= width * height)
            throw new IllegalArgumentException("Invalid board dimensions or mine count");
        this.width = width;
        this.height = height;
        this.mineCount = mineCount;
        this.cells = new MinesweeperCell[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) cells[y][x] = MinesweeperCell.unknown();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMineCount() {
        return mineCount;
    }

    public boolean contains(CellPosition p) {
        return p.x >= 0 && p.y >= 0 && p.x < width && p.y < height;
    }

    public MinesweeperCell get(int x, int y) {
        return cells[y][x];
    }

    public MinesweeperCell get(CellPosition p) {
        return get(p.x, p.y);
    }

    public void set(int x, int y, MinesweeperCell cell) {
        cells[y][x] = cell;
    }

    public List<CellPosition> neighbors(CellPosition p) {
        List<CellPosition> result = new ArrayList<CellPosition>(8);
        for (int y = p.y - 1; y <= p.y + 1; y++) for (int x = p.x - 1; x <= p.x + 1; x++) {
            CellPosition candidate = new CellPosition(x, y);
            if (!(x == p.x && y == p.y) && contains(candidate)) result.add(candidate);
        }
        return result;
    }

    public List<CellPosition> positions() {
        List<CellPosition> result = new ArrayList<CellPosition>(width * height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) result.add(new CellPosition(x, y));
        return result;
    }
}
