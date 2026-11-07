package com.kyroxova.lootgamesolver.client;

import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

final class ContactHazards {
    private static final java.util.Map<Block, Boolean> CACHE = new java.util.IdentityHashMap<Block, Boolean>();
    private ContactHazards() {}

    static boolean dangerous(Block block, int metadata) {
        if (block == null) return false;
        if (block == Blocks.cactus || block == Blocks.fire) return true;
        String type = block.getClass().getName();
        if (type.equals("biomesoplenty.common.blocks.BlockBOPPlant")) return metadata == 5 || metadata == 12;
        if (type.equals("biomesoplenty.common.blocks.BlockBOPFlower")) return metadata == 2;
        if (type.equals("biomesoplenty.common.blocks.BlockBOPFoliage")) return metadata == 7;
        Boolean cached = CACHE.get(block);
        if (cached != null) return cached;
        String name = type.toLowerCase(Locale.ROOT);
        boolean hazard = name.contains("berrybush") || name.contains("thorn") || name.contains("bramble")
            || name.contains("spike") || name.contains("beartrap") || name.contains("punji")
            || name.contains("witherrose") || name.contains("saguaroblock") || name.contains("heatsand")
            || name.contains("blocktfhedge") || name.contains("blocktfknightmetal")
            || name.contains("blocktaintfibres") || name.contains("blockfluxgas")
            || name.contains("blockfluxgoo") || name.contains("blockfluiddeath")
            || name.contains("blockpoisonfluid") || name.contains("blockwitchcactus")
            || name.contains("blocklaserbeam");
        CACHE.put(block, hazard);
        return hazard;
    }
}
