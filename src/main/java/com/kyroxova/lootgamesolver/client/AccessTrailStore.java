package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Persistent, world-specific entrance routes so a client restart does not lose the tunnel back out. */
final class AccessTrailStore {

    private static final long MAX_FILE_SIZE = 24_000_000L;
    private static final int MAX_POINTS_PER_ROUTE = 16_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static final class Data {

        Map<String, List<int[]>> trails = new HashMap<String, List<int[]>>();
    }

    private final File file;
    private Data data = new Data();

    AccessTrailStore(File file) {
        this.file = file;
        if (file.isFile() && file.length() < MAX_FILE_SIZE)
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null && loaded.trails != null) data = loaded;
            } catch (IOException | RuntimeException ignored) {}
    }

    List<int[]> load(String world, int dimension, int x, int y, int z) {
        List<int[]> saved = data.trails.get(key(world, dimension, x, y, z));
        List<int[]> copy = new ArrayList<int[]>();
        if (saved == null) return copy;
        for (int[] p : saved) {
            if (!AccessTrail.validPoint(p) || !copy.isEmpty() && AccessTrail.same(copy.get(copy.size() - 1), p)
                || !copy.isEmpty() && !AccessTrail.adjacent(copy.get(copy.size() - 1), p[0], p[1], p[2])) break;
            if (copy.size() >= MAX_POINTS_PER_ROUTE) break;
            copy.add(new int[] { p[0], p[1], p[2] });
        }
        return copy;
    }

    void save(String world, int dimension, int x, int y, int z, List<int[]> points) throws IOException {
        if (points == null || points.size() < 2 || points.size() > MAX_POINTS_PER_ROUTE) return;
        List<int[]> safeCopy = new ArrayList<int[]>(points.size());
        int[] previous = null;
        for (int[] p : points) {
            if (!AccessTrail.validPoint(p)
                || previous != null && (previous[0] == p[0] && previous[1] == p[1] && previous[2] == p[2]
                    || !AccessTrail.adjacent(previous, p[0], p[1], p[2])))
                return;
            safeCopy.add(new int[] { p[0], p[1], p[2] });
            previous = p;
        }
        data.trails.put(key(world, dimension, x, y, z), safeCopy);
        write();
    }

    void remove(String world, int dimension, int x, int y, int z) throws IOException {
        if (data.trails.remove(key(world, dimension, x, y, z)) != null) write();
    }

    private void write() throws IOException {
        Files.createDirectories(
            file.getParentFile()
                .toPath());
        File pending = new File(file.getParentFile(), file.getName() + ".pending");
        byte[] bytes = GSON.toJson(data)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_SIZE) throw new IOException("Access route storage limit reached");
        Files.write(pending.toPath(), bytes);
        try {
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String key(String world, int dimension, int x, int y, int z) {
        return (world == null ? "unknown" : world) + "|" + dimension + ":" + x + ":" + y + ":" + z;
    }
}
