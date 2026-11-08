package com.kyroxova.lootgamesolver.client;

final class RoomOpeningGeometry {

    private RoomOpeningGeometry() {}

    static int[] positions(int edge, boolean xSide, int along, int outwardX, int outwardZ, int y) {
        int wallX = xSide ? edge : along;
        int wallZ = xSide ? along : edge;
        return new int[] { wallX - outwardX, y, wallZ - outwardZ, wallX + outwardX, y, wallZ + outwardZ, wallX, y,
            wallZ };
    }
}
