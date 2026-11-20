package com.kyroxova.lootgamesolver.client.storage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;

/**
 * Cleanroom reflection adapter for Binnie's Multi-Page Compartment containers
 * (binnie.core.machines.storage.WindowCompartment) without adding a compile-time dependency.
 */
public final class BinnieCompartmentAdapter {

    private static final String CONTAINER_CLASS = "binnie.core.craftgui.minecraft.ContainerCraftGUI";
    private static final String WINDOW_CLASS = "binnie.core.machines.storage.WindowCompartment";
    private static final String MACHINE_CLASS = "binnie.core.machines.Machine";
    private static final String COMP_INV_CLASS = "binnie.core.machines.storage.ComponentCompartmentInventory";

    private final Container container;
    private final Object window;
    private final Object compInventory;
    private final int tabCount;
    private int visitedTabsCount = 1;
    private int targetTab = -1;
    private int syncWaitTicks = 0;

    private BinnieCompartmentAdapter(Container container, Object window, Object compInventory, int tabCount) {
        this.container = container;
        this.window = window;
        this.compInventory = compInventory;
        this.tabCount = Math.max(1, tabCount);
    }

    public static BinnieCompartmentAdapter detect(Container container) {
        if (container == null) return null;
        if (!CONTAINER_CLASS.equals(
            container.getClass()
                .getName()))
            return null;

        try {
            Field windowField = getField(container.getClass(), "window");
            if (windowField == null) return null;
            windowField.setAccessible(true);
            Object window = windowField.get(container);
            if (window == null || !WINDOW_CLASS.equals(
                window.getClass()
                    .getName()))
                return null;

            Method getInvMethod = window.getClass()
                .getMethod("getInventory");
            IInventory machineInv = (IInventory) getInvMethod.invoke(window);

            Object compInv = null;
            int count = 1;

            if (machineInv != null) {
                try {
                    Class<?> machineClass = Class.forName(MACHINE_CLASS);
                    Method getMachineMethod = machineClass.getMethod("getMachine", Object.class);
                    Object machine = getMachineMethod.invoke(null, machineInv);
                    if (machine != null) {
                        Method getInterfaceMethod = machine.getClass()
                            .getMethod("getInterface", Class.class);
                        Class<?> compInvClass = Class.forName(COMP_INV_CLASS);
                        compInv = getInterfaceMethod.invoke(machine, compInvClass);
                        if (compInv != null) {
                            Method getTabNumber = compInv.getClass()
                                .getMethod("getTabNumber");
                            count = (Integer) getTabNumber.invoke(compInv);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (count <= 1) {
                try {
                    Field panelsField = getField(window.getClass(), "panels");
                    if (panelsField != null) {
                        panelsField.setAccessible(true);
                        java.util.Map<?, ?> panels = (java.util.Map<?, ?>) panelsField.get(window);
                        if (panels != null && panels.size() > 1) {
                            count = panels.size();
                        }
                    }
                } catch (Throwable ignored) {}
            }

            return new BinnieCompartmentAdapter(container, window, compInv, count);
        } catch (Throwable t) {
            return null;
        }
    }

    public IInventory getMachineInventory() {
        try {
            Method getInvMethod = window.getClass()
                .getMethod("getInventory");
            return (IInventory) getInvMethod.invoke(window);
        } catch (Throwable t) {
            return null;
        }
    }

    public IInventory getWindowInventory() {
        try {
            Method getWinInvMethod = window.getClass()
                .getMethod("getWindowInventory");
            return (IInventory) getWinInvMethod.invoke(window);
        } catch (Throwable t) {
            return null;
        }
    }

    public int getTabCount() {
        return tabCount;
    }

    public int getCurrentTab() {
        try {
            Field curTabField = getField(window.getClass(), "currentTab");
            if (curTabField != null) {
                curTabField.setAccessible(true);
                return curTabField.getInt(window);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public int[] getSlotsForCurrentTab() {
        if (compInventory == null) return null;
        try {
            Method getSlotsMethod = compInventory.getClass()
                .getMethod("getSlotsForTab", int.class);
            return (int[]) getSlotsMethod.invoke(compInventory, getCurrentTab());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isSlotOnCurrentTab(int slotIndex) {
        int[] slots = getSlotsForCurrentTab();
        if (slots == null) return true;
        for (int s : slots) {
            if (s == slotIndex) return true;
        }
        return false;
    }

    public boolean canSwitchToNextTab() {
        return visitedTabsCount < tabCount;
    }

    public boolean switchToNextTab(Minecraft mc) {
        if (!canSwitchToNextTab()) return false;
        visitedTabsCount++;
        int next = (getCurrentTab() + 1) % tabCount;
        return switchToTab(mc, next);
    }

    public boolean switchToTab(Minecraft mc, int nextTab) {
        if (nextTab < 0 || nextTab >= tabCount || mc == null || mc.thePlayer == null) return false;
        try {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("i", (byte) nextTab);

            Method sendAction = window.getClass()
                .getMethod("sendClientAction", String.class, NBTTagCompound.class);
            sendAction.invoke(window, "tab-change", tag);

            Method receiveGuiNBT = window.getClass()
                .getMethod("receiveGuiNBT", Side.class, EntityPlayer.class, String.class, NBTTagCompound.class);
            receiveGuiNBT.invoke(window, Side.CLIENT, mc.thePlayer, "tab-change", tag);

            Field curTabField = getField(window.getClass(), "currentTab");
            if (curTabField != null) {
                curTabField.setAccessible(true);
                curTabField.setInt(window, nextTab);
            }

            try {
                Method updateTabs = window.getClass()
                    .getMethod("updateTabs");
                updateTabs.invoke(window);
            } catch (Throwable ignored) {}

            targetTab = nextTab;
            syncWaitTicks = 12;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isSyncing() {
        if (targetTab < 0) return false;
        if (getCurrentTab() == targetTab) {
            targetTab = -1;
            return false;
        }
        if (--syncWaitTicks <= 0) {
            targetTab = -1;
            return false;
        }
        return true;
    }

    public int getVisitedTabsCount() {
        return visitedTabsCount;
    }

    private static Field getField(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
