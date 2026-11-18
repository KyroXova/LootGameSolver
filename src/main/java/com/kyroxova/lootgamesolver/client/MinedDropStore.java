package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.Gson;

final class MinedDropStore {

    private static final class Data {

        List<MinedDropTracker.SavedEntry> entries;
    }

    private final File file;

    MinedDropStore(File directory, String world, String player) {
        String key = UUID.nameUUIDFromBytes((world + "\n" + player).getBytes(StandardCharsets.UTF_8))
            .toString();
        file = new File(directory, "mined-drops-" + key + ".json");
    }

    void load(MinedDropTracker tracker) {
        if (!file.isFile() || file.length() > 1024 * 1024) return;
        try {
            Data data = new Gson()
                .fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Data.class);
            if (data == null || data.entries == null || data.entries.size() > 4096) return;
            for (MinedDropTracker.SavedEntry entry : data.entries) {
                if (entry == null || entry.item == null || entry.total > 2304) continue;
                Object item = Item.itemRegistry.getObject(entry.item);
                if (item instanceof Item)
                    tracker.restore(new ItemStack((Item) item, 1, entry.metadata), entry.total, entry.mined);
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    boolean save(MinedDropTracker tracker) {
        Data data = new Data();
        data.entries = tracker.snapshot();
        try {
            Files.createDirectories(
                file.getParentFile()
                    .toPath());
            File pending = new File(file.getParentFile(), file.getName() + ".pending");
            Files.write(
                pending.toPath(),
                new Gson().toJson(data)
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
            tracker.saved();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
