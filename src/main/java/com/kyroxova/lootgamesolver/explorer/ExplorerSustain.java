package com.kyroxova.lootgamesolver.explorer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.kyroxova.lootgamesolver.client.storage.InventoryToolTransfer;

public final class ExplorerSustain {

    private final InventoryToolTransfer transfer = new InventoryToolTransfer();
    private boolean eating;
    private int eatingTicks, restoreSlot = -1, automaticSlot = -1, cooldown;
    private int mineDelayTicks;
    private int manualDelayTicks;
    private int lastObservedSlot = -1;

    public void notifyBlockMined() {
        mineDelayTicks = 50;
    }

    public boolean busy() {
        return eating || transfer.active();
    }

    public boolean tick(Minecraft mc, boolean walking, boolean moving) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return false;
        if (!walking) {
            stop(mc);
            return false;
        }
        updateSlotDelays(mc);
        if (transfer.active()) return tickFoodTransfer(mc);
        if (eating) return tickEating(mc);
        if (cooldown > 0) cooldown--;
        if (mc.currentScreen != null || mc.thePlayer.isUsingItem()) return false;
        if (needsFood(mc) && cooldown == 0) return tryStartEating(mc);
        if (mineDelayTicks == 0 && manualDelayTicks == 0) return tryEquipHealingAxe(mc);
        return false;
    }

    private void updateSlotDelays(Minecraft mc) {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        if (lastObservedSlot >= 0 && currentSlot != lastObservedSlot && currentSlot != automaticSlot) {
            manualDelayTicks = 40;
        }
        lastObservedSlot = currentSlot;
        if (manualDelayTicks > 0) manualDelayTicks--;
        if (mineDelayTicks > 0) mineDelayTicks--;
    }

    private boolean tickFoodTransfer(Minecraft mc) {
        if (!transfer.tick(mc)) cooldown = 100;
        else if (!transfer.active()) automaticSlot = mc.thePlayer.inventory.currentItem;
        return true;
    }

    private boolean tickEating(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer.getFoodStats()
            .getFoodLevel() >= 20 || ++eatingTicks > 80 || (eatingTicks > 2 && !mc.thePlayer.isUsingItem())) {
            releaseUse(mc);
            eating = false;
            cooldown = 20;
            return false;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        return true;
    }

    private static boolean needsFood(Minecraft mc) {
        int foodLevel = mc.thePlayer.getFoodStats()
            .getFoodLevel();
        return foodLevel <= 14 || (mc.thePlayer.shouldHeal() && foodLevel < 20);
    }

    private boolean tryStartEating(Minecraft mc) {
        int food = findFood(mc, 0, 9);
        if (food < 0) {
            int inventoryFood = findFood(mc, 9, 36);
            if (inventoryFood >= 0) {
                if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
                if (transfer.start(mc, inventoryFood)) return true;
            }
        }
        if (food >= 0) {
            select(mc, food);
            eating = true;
            eatingTicks = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            return true;
        }
        cooldown = 100;
        return false;
    }

    private boolean tryEquipHealingAxe(Minecraft mc) {
        if (isHealingAxe(mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem))) return false;
        for (int i = 0; i < 9; i++) {
            if (isHealingAxe(mc.thePlayer.inventory.getStackInSlot(i))) {
                select(mc, i);
                return false;
            }
        }
        if (cooldown != 0) return false;
        for (int i = 9; i < 36; i++) {
            if (isHealingAxe(mc.thePlayer.inventory.getStackInSlot(i))) {
                if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
                return transfer.start(mc, i);
            }
        }
        return false;
    }

    private static int findFood(Minecraft mc, int start, int end) {
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0
                && stack.getItem() instanceof ItemFood
                && stack.getItemUseAction() == EnumAction.eat
                && safeFood(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())), stack.getItemDamage()))
                return i;
        }
        return -1;
    }

    public static boolean safeFood(String id, int metadata) {
        String name = id.toLowerCase(java.util.Locale.ROOT);
        if (name.equals("minecraft:golden_apple") || name.equals("minecraft:rotten_flesh")
            || name.equals("minecraft:spider_eye")
            || name.equals("minecraft:poisonous_potato")
            || name.equals("minecraft:chicken")
            || name.equals("minecraft:fish") && metadata == 3) return false;
        return !name.contains("poison") && !name.contains("rotten")
            && !name.contains("spider_eye")
            && !name.contains("golden_apple");
    }

    public static boolean isHealingAxe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String className = stack.getItem()
            .getClass()
            .getName();
        if (className.equals("com.rwtema.extrautils.item.ItemHealingAxe")) return true;
        String name = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()))
            .toLowerCase(java.util.Locale.ROOT);
        if (name.contains("healingaxe") || name.contains("healing_axe")) return true;
        try {
            String unloc = stack.getItem()
                .getUnlocalizedName(stack);
            if (unloc != null && (unloc.toLowerCase(java.util.Locale.ROOT)
                .contains("healingaxe")
                || unloc.toLowerCase(java.util.Locale.ROOT)
                    .contains("healing_axe"))) {
                return true;
            }
        } catch (Throwable ignored) {
            com.kyroxova.lootgamesolver.LootGameSolver.LOG.debug("Could not inspect stack unlocalized name", ignored);
        }
        return false;
    }

    private void select(Minecraft mc, int slot) {
        if (slot == mc.thePlayer.inventory.currentItem) return;
        if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
        automaticSlot = slot;
        mc.thePlayer.inventory.currentItem = slot;
        mc.playerController.updateController();
    }

    private static void releaseUse(Minecraft mc) {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        boolean physical = key < 0 ? key >= -100 && key + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(key + 100)
            : key > 0 && key < 256 && Keyboard.isKeyDown(key);
        KeyBinding.setKeyBindState(key, physical);
    }

    public void stop(Minecraft mc) {
        if (eating && mc.gameSettings != null) releaseUse(mc);
        eating = false;
        transfer.cancel(mc);
        if (mc.thePlayer != null && restoreSlot >= 0 && mc.thePlayer.inventory.currentItem == automaticSlot) {
            mc.thePlayer.inventory.currentItem = restoreSlot;
            if (mc.playerController != null) mc.playerController.updateController();
        }
        restoreSlot = -1;
        automaticSlot = -1;
        cooldown = 0;
    }
}
