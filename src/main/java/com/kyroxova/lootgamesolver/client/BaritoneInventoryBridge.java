package com.kyroxova.lootgamesolver.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

/** Uses the optional Baritone inventory API while retaining standalone operation. */
final class BaritoneInventoryBridge {

    private BaritoneInventoryBridge() {}

    static boolean useHotbarItem(Minecraft mc, int slot) {
        Class<?> api;
        try {
            api = Class.forName("baritone.api.BaritoneAPI");
        } catch (ClassNotFoundException absent) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            return true;
        } catch (LinkageError failure) {
            return false;
        }
        try {
            Object provider = api.getMethod("getProvider")
                .invoke(null);
            Class<?> providerApi = Class.forName("baritone.api.IBaritoneProvider");
            Object baritone = providerApi.getMethod("getPrimaryBaritone")
                .invoke(provider);
            Class<?> baritoneApi = Class.forName("baritone.api.IBaritone");
            Object inventory = baritoneApi.getMethod("getInventoryControl")
                .invoke(baritone);
            Class<?> inventoryApi = Class.forName("baritone.api.utils.IInventoryControl");
            Method use = inventoryApi.getMethod("useHotbarItem", int.class);
            return "ATTEMPTED".equals(String.valueOf(use.invoke(inventory, slot)));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }

    static boolean quickMove(Minecraft mc, int slot) {
        Class<?> api;
        try {
            api = Class.forName("baritone.api.BaritoneAPI");
        } catch (ClassNotFoundException absent) {
            mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot, 0, 1, mc.thePlayer);
            return true;
        } catch (LinkageError failure) {
            return false;
        }
        try {
            Object provider = api.getMethod("getProvider")
                .invoke(null);
            Object baritone = Class.forName("baritone.api.IBaritoneProvider")
                .getMethod("getPrimaryBaritone")
                .invoke(provider);
            Object inventory = Class.forName("baritone.api.IBaritone")
                .getMethod("getInventoryControl")
                .invoke(baritone);
            Class<?> inventoryApi = Class.forName("baritone.api.utils.IInventoryControl");
            Object snapshot = inventoryApi.getMethod("snapshotOpenContainer")
                .invoke(inventory);
            if (snapshot == null) return false;
            Class<?> snapshotType = Class.forName("baritone.api.utils.IInventoryControl$ContainerSnapshot");
            Class<?> clickType = Class.forName("baritone.api.utils.IInventoryControl$Click");
            Object quickMove = clickType.getField("QUICK_MOVE")
                .get(null);
            Object result = inventoryApi.getMethod("clickContainerSlot", snapshotType, int.class, clickType, int.class)
                .invoke(inventory, snapshot, slot, quickMove, 0);
            return "ATTEMPTED".equals(String.valueOf(result));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }
}
