package com.kyroxova.lootgamesolver.client;

final class FoliagePolicy {

    static boolean canClear(boolean plant, boolean tile, float hardness, int x, int y, int z, int playerX, int footY,
        int playerZ, int nextX, int nextZ) {
        return canClear(plant, tile, hardness, x, y, z, playerX, footY, playerZ, nextX, nextZ, false);
    }

    static boolean canClear(boolean plant, boolean tile, float hardness, int x, int y, int z, int playerX, int footY,
        int playerZ, int nextX, int nextZ, boolean stackedSnow) {
        return plant && !tile
            && hardness >= 0
            && hardness <= 1F
            && y >= footY - 1
            && y <= footY + (stackedSnow ? 8 : 2)
            && ((x == playerX && z == playerZ) || (x == nextX && z == nextZ));
    }
}
