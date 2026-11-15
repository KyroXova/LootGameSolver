package com.kyroxova.lootgamesolver.client;

import java.util.Locale;

final class TrashPolicy {

    private TrashPolicy() {}

    static boolean buildingReserve(String id) {
        return "minecraft:dirt".equals(id) || "minecraft:cobblestone".equals(id);
    }

    static boolean discardOre(String id, boolean isOre, boolean all, String[] configured) {
        if (!isOre || id == null) return false;
        if (all) return true;
        if (configured != null) for (String candidate : configured) {
            if (id.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    static boolean discard(String id, int metadata, boolean ore, boolean blockItem) {
        if (id == null) return false;
        String name = id.toLowerCase(Locale.ROOT);
        return ore || buildingReserve(name)
            || name.equals("minecraft:stone")
            || name.equals("minecraft:gravel")
            || name.equals("minecraft:sand")
            || name.equals("minecraft:sandstone")
            || name.equals("minecraft:snow")
            || name.equals("minecraft:snow_layer")
            || name.equals("minecraft:snowball")
            || name.equals("minecraft:cake")
            || name.equals("minecraft:cake_block")
            || (name.equals("lootgames:lootgamesdungeonlight") && metadata == 1)
            || name.startsWith("openmodularturrets:hardwall");
    }
}
