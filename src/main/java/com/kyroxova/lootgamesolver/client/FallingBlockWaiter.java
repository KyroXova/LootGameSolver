package com.kyroxova.lootgamesolver.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;

final class FallingBlockWaiter {

    private int[] position;
    private Block type;
    private int settleTicks;

    void record(Block block, int x, int y, int z) {
        position = new int[] { x, y, z };
        type = block;
        settleTicks = 0;
    }

    void reset() {
        position = null;
        type = null;
        settleTicks = 0;
    }

    boolean waiting(Minecraft mc, Runnable releaseMining) {
        if (position == null) return false;
        int x = position[0], y = position[1], z = position[2];
        boolean falling = false;
        for (Object value : mc.theWorld.loadedEntityList) {
            if (!(value instanceof EntityFallingBlock)) continue;
            Entity block = (Entity) value;
            if ((int) Math.floor(block.posX) == x && (int) Math.floor(block.posZ) == z && block.posY >= y - 2) {
                falling = true;
                break;
            }
        }
        if (!falling && settleTicks == 0 && mc.theWorld.getBlock(x, y, z) == type) return false;
        releaseMining.run();
        settleTicks = falling ? 1 : settleTicks + 1;
        if (settleTicks < 8) return true;
        reset();
        return false;
    }
}
