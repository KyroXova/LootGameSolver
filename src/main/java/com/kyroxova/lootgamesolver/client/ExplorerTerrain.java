package com.kyroxova.lootgamesolver.client;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

final class ExplorerTerrain {

    private static final java.util.Map<net.minecraft.block.Block, Boolean> QUICKSAND_BLOCKS = new java.util.IdentityHashMap<net.minecraft.block.Block, Boolean>();

    private ExplorerTerrain() {}

    static boolean passable(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z)
            .getMaterial() != net.minecraft.block.material.Material.leaves
            && !world.getBlock(x, y, z)
                .getMaterial()
                .isLiquid()
            && world.getBlock(x, y, z)
                .getCollisionBoundingBoxFromPool(world, x, y, z) == null;
    }

    static boolean puzzleCenterStandable(World world, int x, int y, int z) {
        if (!world.blockExists(x, y - 1, z) || !passable(world, x, y, z) || !passable(world, x, y + 1, z)) return false;
        net.minecraft.block.Block floor = world.getBlock(x, y - 1, z);
        net.minecraft.util.AxisAlignedBB support = floor.getCollisionBoundingBoxFromPool(world, x, y - 1, z);
        if (support == null || support.maxY < y - 0.05D
            || floor.getMaterial()
                .isLiquid())
            return false;
        return world
            .func_147461_a(
                net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(x + 0.19D, y + 0.01D, z + 0.19D, x + 0.81D, y + 1.81D, z + 0.81D))
            .isEmpty();
    }

    static boolean dryShore(World world, int x, int y, int z) {
        if (!solidCheckpoint(world, x, y, z)) return false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 1; dy++) {
            if (!world.blockExists(x + dx, y + dy, z + dz) || world.getBlock(x + dx, y + dy, z + dz)
                .getMaterial()
                .isLiquid()) return false;
        }
        return true;
    }

    static boolean isFoliage(net.minecraft.block.Block block) {
        net.minecraft.block.material.Material material = block.getMaterial();
        return block == Blocks.snow_layer || block instanceof net.minecraft.block.BlockBush
            || material == net.minecraft.block.material.Material.plants
            || material == net.minecraft.block.material.Material.vine
            || material == net.minecraft.block.material.Material.leaves;
    }

    static boolean safe(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.leaves
            || world.getBlock(x, y + 1, z)
                .getMaterial() == net.minecraft.block.material.Material.leaves)
            return false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block nearby = world.getBlock(x + dx, y + dy, z + dz);
                if (OilAvoidance.isOilBlock(nearby)) return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block block = world.getBlock(x + dx, y + dy, z + dz);
                if (ContactHazards.dangerous(block, world.getBlockMetadata(x + dx, y + dy, z + dz))) return false;
                if (isQuicksand(world, x + dx, y + dy, z + dz)) return false;
                if ((block.getMaterial()
                    .isLiquid() && block.getMaterial() != net.minecraft.block.material.Material.water)
                    || block == Blocks.fire
                    || block == Blocks.cactus
                    || block == Blocks.web
                    || block == Blocks.portal) return false;
            }
        }
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y + 1, z)
            && passable(world, x, y + 2, z)) return true;
        if (world.getBlock(x, y - 1, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y, z)
            && passable(world, x, y + 1, z)) return true;
        double surfaceY = walkSurfaceY(world, x, y, z);
        if (surfaceY < y) return false;
        return world
            .func_147461_a(
                net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(x + 0.19D, surfaceY + 0.01D, z + 0.19D, x + 0.81D, surfaceY + 1.81D, z + 0.81D))
            .isEmpty();
    }

    static boolean isQuicksand(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return false;
        net.minecraft.block.Block block = world.getBlock(x, y, z);
        if ("biomesoplenty.common.blocks.BlockMud".equals(
            block.getClass()
                .getName()))
            return world.getBlockMetadata(x, y, z) == 1;
        Boolean quicksand = QUICKSAND_BLOCKS.get(block);
        if (quicksand == null) {
            String id = String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(block))
                .toLowerCase(java.util.Locale.ROOT);
            quicksand = id.contains("quicksand");
            QUICKSAND_BLOCKS.put(block, quicksand);
        }
        return quicksand;
    }

    static boolean solidCheckpoint(World world, int x, int y, int z) {
        return world.blockExists(x, y - 1, z) && world.blockExists(x, y, z)
            && !world.getBlock(x, y, z)
                .getMaterial()
                .isLiquid()
            && !world.getBlock(x, y - 1, z)
                .getMaterial()
                .isLiquid()
            && walkSurfaceY(world, x, y, z) >= y
            && safe(world, x, y, z);
    }

    static double walkSurfaceY(World world, int x, int y, int z) {
        net.minecraft.block.Block at = world.getBlock(x, y, z);
        net.minecraft.util.AxisAlignedBB box = at.getCollisionBoundingBoxFromPool(world, x, y, z);
        if (at instanceof net.minecraft.block.BlockStairs) return y + 0.5D;
        if (box != null && box.maxY > y
            && box.maxY < y + 1D
            && box.minX < x + 0.5D
            && box.maxX > x + 0.5D
            && box.minZ < z + 0.5D
            && box.maxZ > z + 0.5D) return box.maxY;
        net.minecraft.block.Block below = world.getBlock(x, y - 1, z);
        if (below.isNormalCube()) return y;
        net.minecraft.util.AxisAlignedBB belowBox = below.getCollisionBoundingBoxFromPool(world, x, y - 1, z);
        if (belowBox != null && belowBox.maxY >= y
            && belowBox.minX < x + 0.5D
            && belowBox.maxX > x + 0.5D
            && belowBox.minZ < z + 0.5D
            && belowBox.maxZ > z + 0.5D) return y;
        return -1D;
    }

    static boolean isDungeonWall(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return false;
        String name = world.getBlock(x, y, z)
            .getClass()
            .getName();
        return name.endsWith(".DungeonBrick") || name.endsWith(".DungeonLightSource") || name.endsWith(".DungeonWall");
    }
}
