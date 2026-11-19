package com.kyroxova.lootgamesolver.explorer.structure;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerRoomCoordinator {

    public boolean startRoomExitSearch(Minecraft mc, Explorer explorer) {
        return explorer.roomExit.startSearch(mc, explorer);
    }

    public boolean startNextRoomExitSearch(Minecraft mc, Explorer explorer) {
        return explorer.roomExit.startNextSearch(mc, explorer);
    }

    public boolean startRoomEntrySearch(Minecraft mc, Explorer explorer) {
        return explorer.roomEntry.startSearch(mc, explorer);
    }

    public boolean startNextRoomEntrySearch(Minecraft mc, Explorer explorer) {
        return explorer.roomEntry.startNextSearch(mc, explorer);
    }

    public void appendRoomExitWaypoint(Explorer explorer, int[] exit, int offset) {
        explorer.roomExit.appendWaypoint(explorer, exit, offset);
    }

    public boolean clearRoomExitObstruction(Minecraft mc, Explorer explorer, int[] next, int x, int y, int z) {
        return explorer.roomExit.clearObstruction(mc, explorer, next, x, y, z);
    }

    public boolean insideDungeonRoom(Minecraft mc, Explorer explorer) {
        return explorer.roomExit.isInsideDungeonRoom(mc, explorer);
    }

    public boolean clearableExitColumn(World world, Explorer explorer, int x, int y, int z) {
        return RoomExit.clearableExitColumn(world, explorer, x, y, z);
    }

    public boolean planRoofEscape(Minecraft mc, Explorer explorer) {
        return explorer.roomExit.planRoofEscape(mc, explorer);
    }

    public boolean inDungeonRoom(Minecraft mc, Explorer explorer) {
        return mc != null && mc.thePlayer != null && mc.theWorld != null && insideDungeonRoom(mc, explorer);
    }

    public boolean inTargetRoom(Minecraft mc, Explorer explorer, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null || position.length != 3)
            return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        return bounds != null && position[0] > bounds[0]
            && position[0] < bounds[1]
            && position[2] > bounds[2]
            && position[2] < bounds[3]
            && explorer.roomExit.isInsideRoom(mc, explorer, position[1]);
    }

    public boolean enterTargetRoom(Minecraft mc, Explorer explorer, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null || position.length != 3)
            return false;
        if (inTargetRoom(mc, explorer, position) || explorer.walking) return true;
        explorer.target = new TileEntity();
        explorer.target.xCoord = position[0];
        explorer.target.yCoord = position[1];
        explorer.target.zCoord = position[2];
        explorer.knownStructure = position.clone();
        if (startRoomEntrySearch(mc, explorer)) return true;
        if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) {
            explorer.walking = true;
            explorer.searching = false;
            return true;
        }
        explorer.status = "No safe route into the puzzle room";
        return false;
    }

    public int[] verifiedRoomCenter(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld == null
            || explorer.baritoneCoordinator.surfaceMove
            || explorer.baritoneCoordinator.returnMove
            || explorer.returning) return null;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.theWorld.canBlockSeeTheSky(x, y, z)) return null;
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return null;
        return new int[] { (bounds[0] + bounds[1]) >> 1, y - 1, (bounds[2] + bounds[3]) >> 1 };
    }

    public void skipUnopenableRoom(Minecraft mc, Explorer explorer, int[] center) {
        if (center == null) return;
        explorer.stop(mc, "Skipping unopenable room");
        explorer.knownStructure = center.clone();
        try {
            explorer.blockedStructures.mark(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, center, center);
        } catch (java.io.IOException e) {
            explorer.status = "Could not save blocked room";
            return;
        }
        explorer.segmentX = mc.thePlayer.posX;
        explorer.segmentZ = mc.thePlayer.posZ;
        if (!explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer, false)) {
            explorer.stop(mc, "No safe surface destination found");
        }
    }
}
