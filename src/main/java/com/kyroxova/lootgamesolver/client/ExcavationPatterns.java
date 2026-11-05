package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

/** Explicit cardinal excavation layouts. Width includes both outer corners. */
final class ExcavationPatterns {

    static List<int[]> spiral(int x, int y, int z, int goalY, int width, int orientation) {
        List<int[]> path = new ArrayList<int[]>();
        int dy = Integer.signum(goalY - y);
        if (dy == 0) return path;
        int[][] directions = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
        int run = Math.max(2, Math.min(3, width - 1));
        for (int step = 0; step < Math.min(64, Math.abs(goalY - y)); step++) {
            int[] direction = directions[(orientation + step / run) % 4];
            x += direction[0];
            z += direction[1];
            path.add(new int[] { x, y + dy * (step + 1), z });
        }
        return path;
    }

    static List<int[]> straight(int x, int y, int z, int gx, int gy, int gz) {
        List<int[]> path = new ArrayList<int[]>();
        int dy = Integer.signum(gy - y);
        int dx = Integer.signum(gx - x), dz = Integer.signum(gz - z);
        if (Math.abs(gx - x) >= Math.abs(gz - z)) dz = 0;
        else dx = 0;
        if (dx == 0 && dz == 0) return path;
        int length = dy == 0 ? Math.min(12, Math.max(0, Math.abs(gx - x) + Math.abs(gz - z) - 2))
            : Math.min(64, Math.abs(gy - y));
        for (int step = 0; step < length; step++) {
            if (x == gx) {
                dx = 0;
                dz = Integer.signum(gz - z);
            }
            if (z == gz) {
                dz = 0;
                dx = Integer.signum(gx - x);
            }
            if (dx == 0 && dz == 0) break;
            x += dx;
            z += dz;
            path.add(new int[] { x, y + dy * (step + 1), z });
        }
        return path;
    }

    static List<int[]> checked(List<int[]> proposal, ExcavationPathfinder.Terrain terrain, int x, int y, int z) {
        List<int[]> accepted = new ArrayList<int[]>();
        List<int[]> ancestors = new ArrayList<int[]>();
        ancestors.add(new int[] { x, y, z });
        for (int[] step : proposal) {
            if (!ExcavationPolicy.beside(x, z, step[0], step[2]) || Math.abs(step[1] - y) > 1) break;
            boolean cuts = false;
            for (int[] previous : ancestors) if (previous[0] == step[0] && previous[2] == step[2]
                && previous[1] - 1 >= step[1]
                && previous[1] - 1 <= Math.max(y, step[1]) + 1) cuts = true;
            if (cuts || terrain.cost(x, y, z, step[0], step[1], step[2]) < 0) break;
            accepted.add(step);
            ancestors.add(step);
            x = step[0];
            y = step[1];
            z = step[2];
        }
        return accepted;
    }
}
