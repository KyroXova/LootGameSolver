package com.kyroxova.lootgamesolver.client;

import java.util.List;

final class NavigationPathGeometry {

    private NavigationPathGeometry() {}

    static int firstForwardIndex(List<int[]> route, double x, double z) {
        if (route.size() < 2) return 0;
        int[] first = route.get(0), next = route.get(1);
        double dx = next[0] - first[0], dz = next[2] - first[2];
        return (x - first[0] - 0.5D) * dx + (z - first[2] - 0.5D) * dz >= 0D ? 1 : 0;
    }
}
