package com.kyroxova.lootgamesolver.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

final class ExplorerStructureDiscovery {

    private ExplorerStructureDiscovery() {}

    static TileEntity find(Minecraft mc, SavedStructureFinder savedFinder, SolvedStructureStore solved,
        String worldId) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        TileEntity target = null;
        double nearest = Double.MAX_VALUE;
        if (savedFinder != null) for (int[] position : savedFinder.positions()) {
            if (solved.contains(worldId, mc.theWorld.provider.dimensionId, position[0], position[1], position[2]))
                continue;
            double distance = mc.thePlayer.getDistanceSq(position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
            if (distance < nearest) {
                if (mc.theWorld.blockExists(position[0], position[1], position[2])
                    && !(mc.theWorld.getTileEntity(position[0], position[1], position[2]) instanceof TileEntity))
                    continue;
                nearest = distance;
                target = new TileEntity();
                target.xCoord = position[0];
                target.yCoord = position[1];
                target.zCoord = position[2];
            }
        }
        for (Object entry : mc.theWorld.loadedTileEntityList) {
            if (!(entry instanceof TileEntity)) continue;
            TileEntity tile = (TileEntity) entry;
            if (tile.isInvalid() || !tile.getClass()
                .getName()
                .startsWith("ru.timeconqueror.lootgames.")) continue;
            if (solved.contains(worldId, mc.theWorld.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord))
                continue;
            try {
                boolean unopened = tile.getClass()
                    .getName()
                    .equals("ru.timeconqueror.lootgames.common.block.tile.PuzzleMasterTile");
                Object game = unopened ? tile
                    : tile.getClass()
                        .getMethod("getGame")
                        .invoke(tile);
                if (game == null) continue;
                double distance = mc.thePlayer
                    .getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D, tile.zCoord + 0.5D);
                if (distance < nearest) {
                    nearest = distance;
                    target = tile;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return target;
    }

    static double labelHeight(Minecraft mc, TileEntity target) {
        double label = target.yCoord + 3D;
        if (mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
            for (int y = Math.min(254, mc.theWorld.getHeightValue(target.xCoord, target.zCoord)); y
                >= target.yCoord; y--) {
                Block block = mc.theWorld.getBlock(target.xCoord, y, target.zCoord);
                if (block.isNormalCube() && block.getMaterial() != Material.leaves
                    && block.getMaterial() != Material.wood) {
                    label = y + 3D;
                    break;
                }
            }
        }
        return label;
    }
}
