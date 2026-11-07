package com.kyroxova.lootgamesolver.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OpenExitSearch {

    interface Terrain extends GroundPathfinder.Terrain {

        boolean exit(int x, int y, int z);
    }

    private static final class Node {

        final int x, y, z;
        final Node previous;

        Node(int x, int y, int z, Node previous) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.previous = previous;
        }
    }

    private final Terrain terrain;
    private final ArrayDeque<Node> pending = new ArrayDeque<Node>();
    private final Set<String> visited = new HashSet<String>();
    private final int startX, startY, startZ;
    private Node result;

    OpenExitSearch(Terrain terrain, int x, int y, int z) {
        this.terrain = terrain;
        startX = x;
        startY = y;
        startZ = z;
        pending.add(new Node(x, y, z, null));
        visited.add(key(x, y, z));
    }

    boolean advance(int limit, long deadline) {
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        int count = 0;
        while (!pending.isEmpty() && count++ < limit && visited.size() < 32768 && System.nanoTime() < deadline) {
            Node node = pending.removeFirst();
            if (node.previous != null && terrain.exit(node.x, node.y, node.z)) {
                result = node;
                return true;
            }
            for (int[] d : directions) for (int dy = -1; dy <= 1; dy++) {
                int x = node.x + d[0], y = node.y + dy, z = node.z + d[1];
                if (Math.abs(x - startX) > 64 || Math.abs(z - startZ) > 64 || Math.abs(y - startY) > 32) continue;
                String key = key(x, y, z);
                if (visited.contains(key) || !terrain.stand(x, y, z)) continue;
                if (dy > 0 && !terrain.clear(node.x, node.y + 2, node.z)) continue;
                if (dy < 0 && !terrain.clear(x, node.y + 1, z)) continue;
                if (d[0] != 0 && d[1] != 0
                    && (dy != 0 || !terrain.stand(x, node.y, node.z)
                        || !terrain.stand(node.x, node.y, z)
                        || !terrain.clear(x, node.y + 1, node.z)
                        || !terrain.clear(node.x, node.y + 1, z)))
                    continue;
                visited.add(key);
                pending.addLast(new Node(x, y, z, node));
            }
        }
        return pending.isEmpty() || visited.size() >= 32768;
    }

    List<int[]> path() {
        List<int[]> path = new ArrayList<int[]>();
        for (Node n = result; n != null && n.previous != null; n = n.previous) path.add(new int[] { n.x, n.y, n.z });
        Collections.reverse(path);
        return path;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
