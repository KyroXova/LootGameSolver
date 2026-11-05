package com.kyroxova.lootgamesolver.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Reads existing region files without opening them for writing or generating chunks. */
final class SavedStructureFinder {

    private volatile List<int[]> positions = Collections.emptyList();
    private volatile boolean cancelled;

    SavedStructureFinder(final File regionDirectory) {
        Thread worker = new Thread(new Runnable() {

            public void run() {
                scan(regionDirectory);
            }
        }, "LootGames save search");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    void cancel() {
        cancelled = true;
    }

    List<int[]> positions() {
        return positions;
    }

    private void scan(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        List<int[]> found = new ArrayList<int[]>();
        for (File file : files) {
            if (cancelled) return;
            if (!file.getName()
                .matches("r\\.-?\\d+\\.-?\\d+\\.mca")) continue;
            try (RandomAccessFile region = new RandomAccessFile(file, "r")) {
                if (region.length() < 8192) continue;
                for (int i = 0; i < 1024 && !cancelled; i++) {
                    try {
                        region.seek(i * 4L);
                        int location = region.readInt();
                        int sectors = location & 255;
                        long offset = (location >>> 8) * 4096L;
                        if (sectors == 0 || offset < 8192 || offset + 5 > region.length()) continue;
                        region.seek(offset);
                        int length = region.readInt();
                        int compression = region.readUnsignedByte();
                        if (length <= 1 || length > sectors * 4096 - 4 || offset + 4 + length > region.length())
                            continue;
                        byte[] bytes = new byte[length - 1];
                        region.readFully(bytes);
                        InputStream raw = new ByteArrayInputStream(bytes);
                        if (compression != 1 && compression != 2) continue;
                        try (DataInputStream data = new DataInputStream(
                            compression == 1 ? new GZIPInputStream(raw) : new InflaterInputStream(raw))) {
                            NBTTagCompound chunk = CompressedStreamTools.read(data)
                                .getCompoundTag("Level");
                            NBTTagList tiles = chunk.getTagList("TileEntities", 10);
                            for (int t = 0; t < tiles.tagCount(); t++) {
                                NBTTagCompound tile = tiles.getCompoundTagAt(t);
                                String id = tile.getString("id");
                                if (id.equals("LOOTGAMES_MASTER_TE") || id.equals("gol_master")
                                    || id.equals("ms_master")
                                    || id.equals("sdk_master")) {
                                    found.add(
                                        new int[] { tile.getInteger("x"), tile.getInteger("y"), tile.getInteger("z") });
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // A concurrent save or damaged chunk must not abort the remaining search.
                    }
                }
                positions = Collections.unmodifiableList(new ArrayList<int[]>(found));
            } catch (IOException ignored) {}
        }
    }
}
