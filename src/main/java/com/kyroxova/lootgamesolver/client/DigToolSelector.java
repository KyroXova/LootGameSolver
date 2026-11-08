package com.kyroxova.lootgamesolver.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;

final class DigToolSelector {

    static boolean canHarvest(Minecraft mc, Block block, int metadata) {
        if (mc.thePlayer == null) return false;
        if (mc.thePlayer.capabilities.isCreativeMode) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || stack.stackSize <= 0) continue;
            if (stack.isItemStackDamageable() && stack.getMaxDamage() - stack.getItemDamage() < 10) continue;
            if (ForgeHooks.canToolHarvestBlock(block, metadata, stack)) return true;
        }
        return false;
    }

    static int bestSlot(float[] speeds, boolean[] usable) {
        int best = -1;
        float speed = 0F;
        for (int i = 0; i < speeds.length; i++) {
            if (usable[i] && speeds[i] > speed) {
                best = i;
                speed = speeds[i];
            }
        }
        return best;
    }

    static int equip(Minecraft mc, Block block, int metadata) {
        if (mc.thePlayer.capabilities.isCreativeMode) return mc.thePlayer.inventory.currentItem;
        float[] speeds = new float[36];
        boolean[] usable = new boolean[36];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || stack.stackSize <= 0) continue;
            if (stack.isItemStackDamageable() && stack.getMaxDamage() - stack.getItemDamage() < 10) continue;
            try {
                usable[i] = block.getHarvestTool(metadata) == null
                    || ForgeHooks.canToolHarvestBlock(block, metadata, stack);
                speeds[i] = stack.getItem()
                    .getDigSpeed(stack, block, metadata);
            } catch (RuntimeException ignored) {
                usable[i] = false;
            }
        }
        int selected = bestSlot(speeds, usable);
        if (selected < 0) return mc.thePlayer.inventory.currentItem;
        return selected;
    }
}
