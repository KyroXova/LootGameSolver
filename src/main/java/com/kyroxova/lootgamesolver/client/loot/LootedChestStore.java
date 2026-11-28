package com.kyroxova.lootgamesolver.client.loot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kyroxova.lootgamesolver.LootGameSolver;

public final class LootedChestStore {

    private final File file;
    private final Set<String> entries;

    public LootedChestStore(File file) {
        this.file = file;
        Set<String> loaded = null;
        if (file.isFile() && file.length() < 4_000_000L) try {
            loaded = new Gson().fromJson(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                new TypeToken<Set<String>>() {}.getType());
        } catch (IOException | RuntimeException ignored) {
            LootGameSolver.LOG.debug("Could not load looted chest store from {}", file, ignored);
        }
        entries = loaded == null ? new HashSet<String>() : loaded;
    }

    private String key(String world, int dimension, int[] pos) {
        return world + "|" + dimension + ":" + pos[0] + ":" + pos[1] + ":" + pos[2];
    }

    public boolean contains(String world, int dimension, int[] pos) {
        return entries.contains(key(world, dimension, pos));
    }

    public boolean mark(String world, int dimension, int[] pos) {
        String key = key(world, dimension, pos);
        if (!entries.add(key)) return true;
        try {
            Files.createDirectories(
                file.getParentFile()
                    .toPath());
            File pending = new File(file.getParentFile(), file.getName() + ".pending");
            Files.write(
                pending.toPath(),
                new Gson().toJson(entries)
                    .getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(
                    pending.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                try {
                    Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception fallback) {
                    Files.write(
                        file.toPath(),
                        new Gson().toJson(entries)
                            .getBytes(StandardCharsets.UTF_8));
                    try {
                        Files.deleteIfExists(pending.toPath());
                    } catch (Exception ignored) {
                        LootGameSolver.LOG.debug("Could not delete pending looted chest file {}", pending, ignored);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            entries.remove(key);
            return false;
        }
    }
}
