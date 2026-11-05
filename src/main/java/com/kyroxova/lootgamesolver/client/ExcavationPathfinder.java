package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Incremental search: each edge is a supported, cardinal walk, stair or excavation. */
final class ExcavationPathfinder {

    interface Terrain {

        int cost(int x, int y, int z, int nx, int ny, int nz);
    }

    private static final class Node implements Comparable<Node> {

        final int x, y, z, cost, remaining;
        final Node parent;

        Node(int x, int y, int z, int cost, int gx, int gy, int gz, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
            this.parent = parent;
            remaining = Math.max(0, Math.abs(x - gx) + Math.abs(z - gz) - 2) + Math.abs(y - gy);
        }

        public int compareTo(Node other) {
            int order = Integer.compare(cost + remaining, other.cost + other.remaining);
            return order != 0 ? order : Integer.compare(remaining, other.remaining);
        }
    }

    private final PriorityQueue<Node> open = new PriorityQueue<Node>();
    private final Map<Long, Integer> costs = new HashMap<Long, Integer>();
    private final Set<Long> visited = new HashSet<Long>();
    final List<int[]> path = new ArrayList<int[]>();
    final List<int[]> rejected = new ArrayList<int[]>();
    private final int x, y, z, gx, gy, gz;
    private boolean done;

    ExcavationPathfinder(int x, int y, int z, int gx, int gy, int gz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.gx = gx;
        this.gy = gy;
        this.gz = gz;
        open.add(new Node(x, y, z, 0, gx, gy, gz, null));
        costs.put(key(x, y, z), 0);
    }

    boolean advance(Terrain terrain, int limit, long deadline) {
        if (done) return true;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int expanded = 0;
        while (!open.isEmpty() && visited.size() < 4096 && expanded < limit && System.nanoTime() < deadline) {
            Node node = open.remove();
            if (!visited.add(key(node.x, node.y, node.z))) continue;
            expanded++;
            if (node.remaining == 0) {
                for (Node step = node; step.parent != null; step = step.parent)
                    path.add(new int[] { step.x, step.y, step.z });
                Collections.reverse(path);
                done = true;
                return true;
            }
            for (int[] direction : directions) for (int dy = -1; dy <= 1; dy++) {
                int nx = node.x + direction[0], ny = node.y + dy, nz = node.z + direction[1];
                if (Math.abs(nx - x) > 40 || Math.abs(nz - z) > 40 || ny < gy - 2 || ny > y + 4) continue;
                long key = key(nx, ny, nz);
                if (visited.contains(key)) continue;
                int edge = terrain.cost(node.x, node.y, node.z, nx, ny, nz);
                if (edge < 0) {
                    if (node.parent == null && rejected.size() < 12)
                        rejected.add(new int[] { nx, ny, nz, node.x, node.y, node.z });
                    continue;
                }
                int cost = node.cost + edge;
                Integer previous = costs.get(key);
                if (previous != null && previous <= cost) continue;
                costs.put(key, cost);
                open.add(new Node(nx, ny, nz, cost, gx, gy, gz, node));
            }
        }
        done = open.isEmpty() || visited.size() >= 4096;
        return done;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }
}
