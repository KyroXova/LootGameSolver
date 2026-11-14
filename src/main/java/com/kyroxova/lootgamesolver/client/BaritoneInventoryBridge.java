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
}
