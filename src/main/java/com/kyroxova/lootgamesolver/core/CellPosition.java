package com.kyroxova.lootgamesolver.core;

public final class CellPosition implements Comparable<CellPosition> {

    public final int x;
    public final int y;

    public CellPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int compareTo(CellPosition other) {
        int byY = y - other.y;
        return byY != 0 ? byY : x - other.x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CellPosition && ((CellPosition) other).x == x && ((CellPosition) other).y == y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
