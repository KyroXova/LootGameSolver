package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class ExcavationPathfinder {

    interface Terrain {

        int cost(int x, int y, int z, int nx, int ny, int nz);
    }

    private static final class Node implements Comparable<Node> {

        final int x, y, z, cost, remaining;
        final Node parent;
        final int bends;

        Node(int x, int y, int z, int cost, int gx, int gy, int gz, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
            this.parent = parent;
            bends = parent == null ? 0
                : parent.bends + (parent.parent != null
                    && (x - parent.x != parent.x - parent.parent.x || z - parent.z != parent.z - parent.parent.z) ? 1
                        : 0);
            remaining = Math.max(0, Math.abs(x - gx) + Math.abs(z - gz) - 2) + Math.abs(y - gy);
        }

        public int compareTo(Node other) {
            int order = Integer.compare(cost + 4 * remaining, other.cost + 4 * other.remaining);
            if (order == 0) order = Integer.compare(bends, other.bends);
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
    private final boolean horizon;
    private Node best;

    ExcavationPathfinder(int x, int y, int z, int gx, int gy, int gz) {
        this(x, y, z, gx, gy, gz, false);
    }

    ExcavationPathfinder(int x, int y, int z, int gx, int gy, int gz, boolean horizon) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.gx = gx;
        this.gy = gy;
        this.gz = gz;
        this.horizon = horizon;
        best = new Node(x, y, z, 0, gx, gy, gz, null);
        open.add(best);
        costs.put(key(x, y, z), 0);
    }

    boolean advance(Terrain terrain, int limit, long deadline) {
        if (done) return true;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int expanded = 0;
        while (!open.isEmpty() && visited.size() < 4096 && expanded < limit && System.nanoTime() < deadline) {
            Node node = open.remove();
            long nodeKey = key(node.x, node.y, node.z);
            Integer bestCost = costs.get(nodeKey);
            if (bestCost == null || node.cost != bestCost.intValue() || !visited.add(nodeKey)) continue;
            expanded++;
            if (node.remaining < best.remaining || (node.remaining == best.remaining && node.y < best.y)) best = node;
            if (node.remaining == 0) {
                finish(node);
                return true;
            }
            if (horizon && visited.size() >= 96
                && best.y <= y - 2
                && best.remaining <= Math.max(0, Math.abs(x - gx) + Math.abs(z - gz) - 2) + Math.abs(y - gy)) {
                finish(best);
                return true;
            }
            for (int[] direction : directions) for (int dy = -1; dy <= 1; dy++) {
                int nx = node.x + direction[0], ny = node.y + dy, nz = node.z + direction[1];
                if (Math.abs(nx - x) > 40 || Math.abs(nz - z) > 40 || ny < gy - 2 || ny > y + 4) continue;
                boolean cutsReturnStair = false;
                for (Node ancestor = node; ancestor != null; ancestor = ancestor.parent) {
                    if (ancestor.x == nx && ancestor.z == nz
                        && ancestor.y - 1 >= ny
                        && ancestor.y - 1 <= Math.max(node.y, ny) + 1) {
                        cutsReturnStair = true;
                        break;
                    }
                }
                // Existing tunnels may revisit an X/Z column at another height.
                if (horizon && cutsReturnStair) continue;
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
        if (done && horizon && best.parent != null && best.remaining < new Node(x, y, z, 0, gx, gy, gz, null).remaining)
            finish(best);
        return done;
    }

    int expandedNodes() {
        return visited.size();
    }

    private void finish(Node node) {
        for (Node step = node; step.parent != null; step = step.parent) path.add(new int[] { step.x, step.y, step.z });
        Collections.reverse(path);
        done = true;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }
}
