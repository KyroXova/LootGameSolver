package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded three-dimensional ground search, allowing one-block steps and detours. */
public final class GroundPathfinder {

    public interface Terrain {

        boolean stand(int x, int y, int z);

        boolean clear(int x, int y, int z);
    }

    private static final class Node implements Comparable<Node> {

        final int x, y, z, cost;
        final double remaining;
        final Node parent;

        Node(int x, int y, int z, int cost, int gx, int gz, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
            this.parent = parent;
            remaining = Math.abs(x - gx) + Math.abs(z - gz);
        }

        public int compareTo(Node other) {
            return Double.compare(cost + remaining, other.cost + other.remaining);
        }
    }

    public List<int[]> find(Terrain terrain, int x, int y, int z, int gx, int gz, int radius) {
        List<int[]> result = new ArrayList<int[]>();
        if (!terrain.stand(x, y, z)) return result;
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Set<String> visited = new HashSet<String>();
        Node start = new Node(x, y, z, 0, gx, gz, null), best = start;
        open.add(start);
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!open.isEmpty() && visited.size() < 4096) {
            Node node = open.remove();
            if (!visited.add(node.x + ":" + node.y + ":" + node.z)) continue;
            if (node.remaining < best.remaining) best = node;
            if (node.remaining <= 1) {
                best = node;
                break;
            }
            for (int[] direction : directions) for (int dy = -1; dy <= 1; dy++) {
                int nx = node.x + direction[0], ny = node.y + dy, nz = node.z + direction[1];
                if (Math.abs(nx - x) > radius || Math.abs(nz - z) > radius || Math.abs(ny - y) > 12) continue;
                if (!terrain.stand(nx, ny, nz)) continue;
                if (dy > 0 && !terrain.clear(node.x, node.y + 2, node.z)) continue;
                if (dy < 0 && !terrain.clear(nx, node.y + 1, nz)) continue;
                open.add(new Node(nx, ny, nz, node.cost + 1 + Math.abs(dy), gx, gz, node));
            }
        }
        if (best.remaining >= start.remaining) return result;
        for (Node node = best; node.parent != null; node = node.parent)
            result.add(new int[] { node.x, node.y, node.z });
        Collections.reverse(result);
        return result;
    }
}
