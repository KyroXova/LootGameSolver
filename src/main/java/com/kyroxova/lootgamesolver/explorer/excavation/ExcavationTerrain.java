package com.kyroxova.lootgamesolver.explorer.excavation;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.*;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.DigToolSelector;
import com.kyroxova.lootgamesolver.explorer.trail.ContactHazards;

public final class ExcavationTerrain {

    private final java.util.Set<Long> returnSupports = new java.util.HashSet<Long>();
    private final java.util.Map<String, Boolean> structureTools = new java.util.HashMap<String, Boolean>();

    public void clearSupports() {
        returnSupports.clear();
    }

    public void clearToolCache() {
        structureTools.clear();
    }

    public void protectSupport(long key) {
        returnSupports.add(key);
    }

    public boolean excavatable(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z) || w.getTileEntity(x, y, z) != null) return false;
        if (returnSupports.contains(supportKey(x, y, z))) return false;
        net.minecraft.block.Block block = w.getBlock(x, y, z);
        float hardness = block.getBlockHardness(w, x, y, z);
        net.minecraft.block.material.Material material = block.getMaterial();
        boolean structure = block.getClass()
            .getName()
            .equals("eu.usrv.legacylootgames.blocks.DungeonBrick")
            || block.getClass()
                .getName()
                .equals("eu.usrv.legacylootgames.blocks.DungeonLightSource");
        if (structure) {
            if (hardness < 0 || hardness > 10) return false;
            String toolKey = block.getClass()
                .getName() + ":"
                + w.getBlockMetadata(x, y, z);
            Boolean harvestable = structureTools.get(toolKey);
            if (harvestable == null) {
                harvestable = DigToolSelector.canHarvest(Minecraft.getMinecraft(), block, w.getBlockMetadata(x, y, z));
                structureTools.put(toolKey, harvestable);
            }
            return harvestable;
        }
        // Falling blocks in the target column are cleared from the bottom up.
        return hardness >= 0 && hardness <= 10
            && !material.isLiquid()
            && block != Blocks.fire
            && block != Blocks.cactus
            && block != Blocks.portal;
    }

    public int excavationCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        for (int by = ny - 1; by <= ny + 1; by++) if (isQuicksand(w, nx, by, nz)) return -1;
        if (!w.blockExists(nx, ny - 1, nz) || !w.getBlock(nx, ny - 1, nz)
            .isNormalCube()) return -1;
        boolean overhead = ny > y && !passable(w, x, y + 2, z);
        if (overhead && !overheadClear(w, x, y, z)) return -1;
        int clearanceTop = Math.max(y, ny) + 1;
        if (!w.blockExists(nx, clearanceTop + 1, nz) || clearanceTop > y + 4) return -1;

        int cost = 1 + Math.abs(ny - y) + (overhead ? 2 : 0);
        int firstBreak = Integer.MAX_VALUE, lastBreak = Integer.MIN_VALUE;
        for (int by = ny; by <= clearanceTop; by++) {
            if (passable(w, nx, by, nz)) continue;
            if (!excavatable(w, nx, by, nz)) return -1;
            firstBreak = Math.min(firstBreak, by);
            lastBreak = Math.max(lastBreak, by);
            net.minecraft.block.Block block = w.getBlock(nx, by, nz);
            cost += Math.max(1, (int) Math.ceil(block.getBlockHardness(w, nx, by, nz) / 2F));
            if (block.getMaterial() == net.minecraft.block.material.Material.wood) cost += 4;
        }
        return surroundingHazardsClear(w, nx, ny, Math.max(y, ny) + 2, nz, firstBreak, lastBreak) ? cost : -1;
    }

    private boolean overheadClear(World w, int x, int y, int z) {
        if (w.getBlock(x, y + 2, z) instanceof net.minecraft.block.BlockFalling || !excavatable(w, x, y + 2, z))
            return false;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int by = y + 1; by <= y + 3; by++) {
                    net.minecraft.block.Block block = w.getBlock(x + ox, by, z + oz);
                    if (!w.blockExists(x + ox, by, z + oz) || block.getMaterial()
                        .isLiquid() || (ox == 0 && oz == 0 && block instanceof net.minecraft.block.BlockFalling))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean surroundingHazardsClear(World w, int nx, int ny, int topY, int nz, int firstBreak,
        int lastBreak) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int by = ny - 1; by <= topY; by++) {
                    if (!w.blockExists(nx + ox, by, nz + oz)) return false;
                    net.minecraft.block.Block block = w.getBlock(nx + ox, by, nz + oz);
                    if (ContactHazards.dangerous(block, w.getBlockMetadata(nx + ox, by, nz + oz))) return false;
                    if ((block.getMaterial()
                        .isLiquid() && by >= firstBreak - 1
                        && by <= lastBreak + 1) || block == Blocks.fire
                        || block == Blocks.cactus
                        || block == Blocks.portal) return false;
                }
            }
        }
        return true;
    }
}
