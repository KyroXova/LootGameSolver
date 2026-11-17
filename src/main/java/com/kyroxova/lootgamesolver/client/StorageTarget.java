package com.kyroxova.lootgamesolver.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;

final class StorageTarget {

    final int dimension;
    final int x, y, z;
    final UUID entity;

    private StorageTarget(int dimension, int x, int y, int z, UUID entity) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entity = entity;
    }

    static StorageTarget parse(String target) {
        if (!StorageTripPolicy.validTarget(target)) return null;
        String[] fields = target.split(":");
        int dimension = Integer.parseInt(fields[1]);
        return "block".equals(fields[0])
            ? new StorageTarget(
                dimension,
                Integer.parseInt(fields[2]),
                Integer.parseInt(fields[3]),
                Integer.parseInt(fields[4]),
                null)
            : new StorageTarget(dimension, 0, 0, 0, UUID.fromString(fields[2]));
    }

    double[] loadedPosition(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.theWorld.provider.dimensionId != dimension) return null;
        if (entity == null) {
            if (!mc.theWorld.blockExists(x, y, z) || !(mc.theWorld.getTileEntity(x, y, z) instanceof IInventory))
                return null;
            return new double[] { x + 0.5D, y + 0.5D, z + 0.5D };
        }
        for (Object value : mc.theWorld.loadedEntityList) {
            if (value instanceof Entity && value instanceof IInventory
                && entity.equals(((Entity) value).getUniqueID())) {
                Entity found = (Entity) value;
                return new double[] { found.posX, found.posY, found.posZ };
            }
        }
        return null;
    }

    IInventory loadedInventory(Minecraft mc) {
        if (loadedPosition(mc) == null) return null;
        if (entity == null) return (IInventory) mc.theWorld.getTileEntity(x, y, z);
        for (Object value : mc.theWorld.loadedEntityList) {
            if (value instanceof Entity && value instanceof IInventory && entity.equals(((Entity) value).getUniqueID()))
                return (IInventory) value;
        }
        return null;
    }
}
