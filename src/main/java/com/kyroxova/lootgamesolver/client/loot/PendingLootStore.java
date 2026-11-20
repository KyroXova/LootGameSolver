package com.kyroxova.lootgamesolver.client.loot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.kyroxova.lootgamesolver.core.MiniGame;

public final class PendingLootStore {

    public static final class Checkpoint {

        public String world;
        public int dimension;
        public int[] center;
        public MiniGame game;
    }

    private final File file;

    public PendingLootStore(File file) {
        this.file = file;
    }

    public boolean save(String world, int dimension, int[] center, MiniGame game) {
        return save(world, dimension, center, game, false);
    }

    public boolean save(String world, int dimension, int[] center, MiniGame game, boolean overwrite) {
        if (world == null || center == null || center.length != 3) return false;
        Checkpoint existing = load();
        if (!overwrite && existing != null
            && (!world.equals(existing.world) || dimension != existing.dimension
                || !java.util.Arrays.equals(center, existing.center)))
            return false;
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.world = world;
        checkpoint.dimension = dimension;
        checkpoint.center = center.clone();
        checkpoint.game = game == null ? MiniGame.UNKNOWN : game;
        try {
            Files.createDirectories(
                file.getParentFile()
                    .toPath());
            File pending = new File(file.getParentFile(), file.getName() + ".pending");
            Files.write(
                pending.toPath(),
                new Gson().toJson(checkpoint)
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
                        new Gson().toJson(checkpoint)
                            .getBytes(StandardCharsets.UTF_8));
                    try {
                        Files.deleteIfExists(pending.toPath());
                    } catch (Exception ignored) {}
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Checkpoint load() {
        if (!file.isFile() || file.length() > 16384) return null;
        try {
            Checkpoint value = new Gson()
                .fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Checkpoint.class);
            if (value == null || value.world == null || value.center == null || value.center.length != 3) return null;
            if (value.game == null) value.game = MiniGame.UNKNOWN;
            return value;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public boolean clear() {
        try {
            if (file.exists()) Files.write(file.toPath(), "null".getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
