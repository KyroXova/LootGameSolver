package com.kyroxova.lootgamesolver.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

/** Uses the optional Baritone inventory API while retaining standalone operation. */
public final class BaritoneInventoryBridge {

    private BaritoneInventoryBridge() {}

    public static boolean useHotbarItem(Minecraft mc, int slot) {
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

    public static boolean quickMove(Minecraft mc, int slot) {
        return click(mc, slot, "QUICK_MOVE", 0);
    }

    public static boolean swapOpenContainer(Minecraft mc, int slot, int hotbar) {
        return click(mc, slot, "HOTBAR_SWAP", hotbar);
    }

    public static boolean leftClick(Minecraft mc, int slot) {
        return click(mc, slot, "LEFT", 0);
    }

    private static boolean click(Minecraft mc, int slot, String kind, int button) {
        Class<?> api;
        try {
            api = Class.forName("baritone.api.BaritoneAPI");
        } catch (ClassNotFoundException absent) {
            mc.playerController.windowClick(
                mc.thePlayer.openContainer.windowId,
                slot,
                "HOTBAR_SWAP".equals(kind) ? button : 0,
                "QUICK_MOVE".equals(kind) ? 1 : "HOTBAR_SWAP".equals(kind) ? 2 : 0,
                mc.thePlayer);
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
            Object clickTypeValue = clickType.getField(kind)
                .get(null);
            Object result = inventoryApi.getMethod("clickContainerSlot", snapshotType, int.class, clickType, int.class)
                .invoke(inventory, snapshot, slot, clickTypeValue, button);
            return "ATTEMPTED".equals(String.valueOf(result));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }
}
