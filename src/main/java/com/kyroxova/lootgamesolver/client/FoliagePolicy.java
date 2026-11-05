package com.kyroxova.lootgamesolver.client;

/** Only remove a visible, soft plant in the occupied or intended excavation column. */
final class FoliagePolicy {

    static boolean canClear(boolean plant, boolean tile, float hardness, int x, int y, int z, int playerX, int footY,
        int playerZ, int nextX, int nextZ) {
        return plant && !tile
            && hardness >= 0
            && hardness <= 1F
            && y >= footY - 1
            && y <= footY + 2
            && ((x == playerX && z == playerZ) || (x == nextX && z == nextZ));
    }
}
