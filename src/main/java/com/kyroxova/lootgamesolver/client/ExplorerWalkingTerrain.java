package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.*;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

final class ExplorerWalkingTerrain implements GroundPathfinder.Terrain {

    private final World world;
    private final Set<Long> blocked;
    private final boolean surfaceStart, protectedJourney, structureApproach;
    private final int goalX, goalZ;
    private final BooleanSupplier movingToCenter;
    private final Supplier<TileEntity> target;

    ExplorerWalkingTerrain(World world, Set<Long> blocked, boolean surfaceStart, boolean protectedJourney,
        boolean structureApproach, int goalX, int goalZ, BooleanSupplier movingToCenter, Supplier<TileEntity> target) {
        this.world = world;
        this.blocked = blocked;
        this.surfaceStart = surfaceStart;
        this.protectedJourney = protectedJourney;
        this.structureApproach = structureApproach;
        this.goalX = goalX;
        this.goalZ = goalZ;
        this.movingToCenter = movingToCenter;
        this.target = target;
    }

    public boolean stand(int x, int y, int z) {
        if (blocked.contains(supportKey(x, y, z)) || !world.blockExists(x, y, z)
            || !NavigationSurfacePolicy.allow(
                surfaceStart,
                protectedJourney,
                structureApproach,
                world.getHeightValue(x, z),
                y,
                x,
                z,
                goalX,
                goalZ))
            return false;
        if (safe(world, x, y, z)) return true;
        TileEntity center = target.get();
        return movingToCenter.getAsBoolean() && center != null
            && x == center.xCoord
            && y == center.yCoord + 1
            && z == center.zCoord
            && puzzleCenterStandable(world, x, y, z);
    }

    public boolean clear(int x, int y, int z) {
        return passable(world, x, y, z);
    }

    public boolean checkpoint(int x, int y, int z) {
        return solidCheckpoint(world, x, y, z);
    }

    public int extraCost(int x, int y, int z) {
        return world.getBlock(x, y, z)
            .getMaterial() == Material.water ? 12 : 0;
    }

    public int transitionCost(int x, int y, int z, int nx, int ny, int nz) {
        int cost = Math.abs(ny - y) == 1 ? 4 : 0;
        if (world.getBlock(nx, ny - 1, nz)
            .getMaterial()
            .isLiquid()) cost += 8;
        return cost;
    }

    public boolean canTransition(int x, int y, int z, int nx, int ny, int nz) {
        boolean waterAtFeet = world.getBlock(x, y, z)
            .getMaterial() == Material.water;
        boolean waterBelow = world.getBlock(x, y - 1, z)
            .getMaterial() == Material.water;
        boolean destinationWater = world.getBlock(nx, ny, nz)
            .getMaterial() == Material.water
            || world.getBlock(nx, ny - 1, nz)
                .getMaterial() == Material.water;
        if ((waterAtFeet || waterBelow) && !destinationWater && ny > (waterAtFeet ? y + 1 : y)) return false;
        return NavigationSurfacePolicy.reachableRise(walkSurfaceY(world, x, y, z), walkSurfaceY(world, nx, ny, nz));
    }
}
