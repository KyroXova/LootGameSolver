package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Local completion history, separated by world/server and dimension. */
final class SolvedStructureStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static final class Data {

        Map<String, Set<String>> worlds = new HashMap<String, Set<String>>();
    }

    private final File file;
    private Data data = new Data();

    SolvedStructureStore(File file) {
        this.file = file;
        if (file.isFile() && file.length() < 4_000_000L)
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null && loaded.worlds != null) data = loaded;
            } catch (IOException | RuntimeException ignored) {}
    }

    boolean contains(String world, int dimension, int x, int y, int z) {
        Set<String> positions = data.worlds.get(world);
        return positions != null && positions.contains(key(dimension, x, y, z));
    }

    void mark(String world, int dimension, int[] origin, int[] master) throws IOException {
        Set<String> positions = data.worlds.get(world);
        if (positions == null) {
            positions = new HashSet<String>();
            data.worlds.put(world, positions);
        }
        positions.add(key(dimension, origin[0], origin[1], origin[2]));
        if (master != null) positions.add(key(dimension, master[0], master[1], master[2]));
        Files.createDirectories(
            file.getParentFile()
                .toPath());
        File pending = new File(file.getParentFile(), file.getName() + ".pending");
        Files.write(
            pending.toPath(),
            GSON.toJson(data)
                .getBytes(StandardCharsets.UTF_8));
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

    private static String key(int dimension, int x, int y, int z) {
        return dimension + ":" + x + ":" + y + ":" + z;
    }
}
