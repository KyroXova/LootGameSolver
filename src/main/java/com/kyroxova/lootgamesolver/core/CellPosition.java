package com.kyroxova.lootgamesolver.core;

public record CellPosition(int x, int y) implements Comparable<CellPosition> {

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
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
