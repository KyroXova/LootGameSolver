package com.kyroxova.lootgamesolver.client;

/** Deterministic expanding rings, bounded relative to the starting position. */
public final class ProgressiveSearch {

    private final int x;
    private final int z;
    private final int limit;
    private int index;

    public ProgressiveSearch(int x, int z, int limit) {
        this.x = x;
        this.z = z;
        this.limit = limit;
    }

    public int[] next() {
        int radius = 16 * (index / 8 + 1);
        if (radius > limit) return null;
        int direction = index++ % 8;
        int[][] directions = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
        return new int[] { x + directions[direction][0] * radius, z + directions[direction][1] * radius };
    }
}
