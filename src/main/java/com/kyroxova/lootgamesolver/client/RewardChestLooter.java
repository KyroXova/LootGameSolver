package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.RewardChestView.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.Vec3;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.ExplorerSustain;
import com.kyroxova.lootgamesolver.explorer.MiningAimGate;

final class RewardChestLooter {

    private final List<int[]> chests = new ArrayList<int[]>();
    private final List<RewardLootPolicy.Entry> discovered = new ArrayList<RewardLootPolicy.Entry>();
    private List<RewardLootPolicy.Entry> queue = new ArrayList<RewardLootPolicy.Entry>();
    private final Map<Long, ItemStack> scanned = new HashMap<Long, ItemStack>();
    private InventoryBaseline baseline;
    private final boolean[] backpackTried = new boolean[36];
    private final boolean[] slotRejectedForCurrentBag = new boolean[36];
    private final InventoryToolTransfer backpackTransfer = new InventoryToolTransfer();
    private final MiningAimGate chestAim = new MiningAimGate();
    private final LootedChestStore lootedChests = new LootedChestStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "looted-chests.json"));
    private final PendingLootStore pendingLoot = new PendingLootStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "pending-loot.json"));
    private int[] center;
    private int[] chestView;
    private final List<int[]> failedChestViews = new ArrayList<int[]>();
    private int failedViewChest = -1;
    private MiniGame gameType;
    private int ticks, index, waiting, openWait, pendingSlot = -1, pendingCount;
    private int queueIndex, backpackSlot = -1, oldSlot = -1;
    private int packPendingSlot = -1, packPendingCount;
    private ItemStack packPendingStack;
    private int packPendingBagCount;
    private int backpackTransferAttempts;
    private int backpackAimTicks;
    private float backpackOldYaw;
    private boolean active, opened, collecting, packing, packOpening, packMoved;
    private boolean existingInventoryPackRequested;
    private boolean packingOnly;
    private boolean backpackAimSaved;
    private boolean backpackAiming;
    private boolean temporaryBag, temporaryBagUsed, restoringBag;
    private int bagSource, bagHotbar;
    private int preferredBagSlot = -1;
    private ItemStack bagDisplaced;
    private String status = "";

    void setBaseline(InventoryBaseline value) {
        baseline = value;
    }

    void start(Minecraft mc, int[] position, MiniGame type) {
        if (baseline == null) baseline = InventoryBaseline.capture(mc);
        center = position == null ? null : position.clone();
        chestView = null;
        failedChestViews.clear();
        failedViewChest = -1;
        gameType = type;
        ticks = 0;
        index = 0;
        waiting = 0;
        pendingSlot = -1;
        openWait = 0;
        opened = false;
        collecting = false;
        packing = false;
        packOpening = false;
        packMoved = false;
        existingInventoryPackRequested = false;
        packingOnly = false;
        queueIndex = 0;
        backpackSlot = -1;
        backpackTransferAttempts = 0;
        oldSlot = -1;
        packPendingSlot = -1;
        packPendingStack = null;
        backpackAimSaved = false;
        backpackAiming = false;
        backpackAimTicks = 0;
        chestAim.reset();
        temporaryBag = false;
        temporaryBagUsed = false;
        restoringBag = false;
        bagDisplaced = null;
        preferredBagSlot = -1;
        discovered.clear();
        queue.clear();
        scanned.clear();
        for (int i = 0; i < 36; i++) {
            backpackTried[i] = false;
            slotRejectedForCurrentBag[i] = false;
        }
        active = true;
        chests.clear();
        status = "Waiting for reward chests";
    }

    boolean active() {
        return active;
    }

    boolean startInventoryPacking(Minecraft mc) {
        if (baseline == null || !hasPackableItems(mc)) return false;
        start(mc, null, MiniGame.UNKNOWN);
        packingOnly = true;
        packing = true;
        status = "Packing inventory";
        return true;
    }

    boolean completed() {
        return "Reward chests collected".equals(status);
    }

    String status() {
        return status;
    }

    PendingLootStore.Checkpoint pendingCheckpoint() {
        return pendingLoot.load();
    }

    void cancel(Minecraft mc) {
        backpackTransfer.cancel(mc);
        if ((opened || packing) && mc.thePlayer != null && mc.currentScreen instanceof GuiContainer)
            mc.thePlayer.closeScreen();
        if (temporaryBag && temporaryBagUsed && mc.thePlayer != null && !restoringBag) {
            backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced);
        }
        if (mc.thePlayer != null && oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
        restoreBackpackAim(mc);
        active = false;
        opened = false;
    }

    void tick(Minecraft mc, Explorer exploration) {
        if (!active || mc.thePlayer == null || mc.theWorld == null) return;
        if (exploration != null && exploration.navigating()) {
            status = "Moving toward reward chests";
            return;
        }
        if (chestView != null) {
            Vec3 eye = Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
            if (mc.thePlayer.getDistanceSq(chestView[0] + 0.5D, chestView[1] + 1D, chestView[2] + 0.5D) > 2.25D
                || index < chests.size() && visibleChest(mc, eye, chests.get(index)) == null)
                failedChestViews.add(chestView);
            chestView = null;
        }
        if (exploration != null) exploration.consumePuzzleCenterArrival();
        if (backpackTransfer.active()) {
            if (!backpackTransfer.tick(mc)) {
                if (bagSource >= 0 && bagSource < backpackTried.length) backpackTried[bagSource] = true;
                if (bagHotbar >= 0 && bagHotbar < backpackTried.length) backpackTried[bagHotbar] = true;
                temporaryBag = false;
                temporaryBagUsed = false;
                restoringBag = false;
                backpackSlot = -1;
                waiting = 10;
                status = "Trying another backpack";
            } else if (!backpackTransfer.active() && restoringBag) {
                temporaryBag = false;
                temporaryBagUsed = false;
                restoringBag = false;
                backpackSlot = -1;
            }
            return;
        }
        boolean structureFinished = (!packing && !collecting && index >= chests.size()) || (packingOnly && !packing);
        if (temporaryBag && temporaryBagUsed && !packOpening && mc.currentScreen == null && structureFinished) {
            if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                restoringBag = true;
                status = "Restoring hotbar";
            } else {
                status = "Hotbar changed; bag kept";
                temporaryBag = false;
                temporaryBagUsed = false;
                restoringBag = false;
                backpackSlot = -1;
            }
            return;
        }
        if (packingOnly) {
            if (packing) tickBackpack(mc, exploration);
            else {
                active = false;
                status = "Inventory packing finished";
            }
            return;
        }
        if (center == null) {
            status = "Reward center unavailable";
            active = false;
            return;
        }
        if (++ticks < 30) return;
        if (chests.isEmpty() && index == 0) {
            int[] cluster = ClientHooks.nearbyRewardCenter(mc);
            if (cluster != null) center = cluster;
            for (Object obj : mc.theWorld.loadedTileEntityList) {
                if (!(obj instanceof TileEntityChest)) continue;
                TileEntity tile = (TileEntity) obj;
                int dx = tile.xCoord - center[0], dz = tile.zCoord - center[2];
                // Include the nearby chest cluster because the reported board center varies by stage.
                if (Math.abs(dx) + Math.abs(dz) <= 3 && Math.abs(tile.yCoord - center[1]) <= 5)
                    chests.add(new int[] { tile.xCoord, tile.yCoord, tile.zCoord });
            }
            chests.sort(
                (a, b) -> Integer.compare(
                    Math.abs(a[0] - center[0]) + Math.abs(a[2] - center[2]),
                    Math.abs(b[0] - center[0]) + Math.abs(b[2] - center[2])));
            while (chests.size() > 4) chests.remove(chests.size() - 1);
            if (chests.isEmpty()) {
                status = "Waiting for reward chests";
                return;
            }
        }
        if (packing) {
            tickBackpack(mc, exploration);
            return;
        }
        while (!collecting && index < chests.size()
            && lootedChests.contains(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(index)))
            index++;
        if (!collecting && index >= chests.size()) {
            if (opened && mc.currentScreen instanceof GuiChest) mc.thePlayer.closeScreen();
            opened = false;
            if (compactBackpackHotbar(mc)) return;
            if (!existingInventoryPackRequested && hasPackableItems(mc)) {
                existingInventoryPackRequested = true;
                packing = true;
                packOpening = false;
                packMoved = false;
                backpackSlot = -1;
                for (int i = 0; i < backpackTried.length; i++) {
                    backpackTried[i] = false;
                    slotRejectedForCurrentBag[i] = false;
                }
                status = "Packing leftover inventory items";
                return;
            }
            status = "Reward chests collected";
            PendingLootStore.Checkpoint checkpoint = pendingLoot.load();
            if (checkpoint != null && checkpoint.world.equals(Explorer.worldIdentity(mc))
                && checkpoint.dimension == mc.theWorld.provider.dimensionId
                && java.util.Arrays.equals(checkpoint.center, center)) pendingLoot.clear();
            active = false;
            return;
        }
        if (collecting) {
            if (queueIndex >= queue.size()) {
                if (!opened || !(mc.currentScreen instanceof GuiChest)) {
                    status = "Rechecking reward chest";
                    collecting = false;
                    return;
                }
                if (!(mc.thePlayer.openContainer instanceof ContainerChest)) {
                    status = "Rechecking reward chest";
                    collecting = false;
                    return;
                }
                IInventory checked = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
                for (int slot = 0; slot < checked.getSizeInventory(); slot++) {
                    if (checked.getStackInSlot(slot) != null) {
                        collecting = false;
                        status = "Checking remaining items";
                        return;
                    }
                }
                if (!lootedChests
                    .mark(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(index))) {
                    status = "Could not save chest progress";
                    return;
                }
                mc.thePlayer.closeScreen();
                opened = false;
                collecting = false;
                index++;
                openWait = 0;
                waiting = 4;
                status = "Chest collected";
                return;
            }
            int nextChest = queue.get(queueIndex).chest;
            if (opened && index != nextChest && mc.currentScreen instanceof GuiChest) {
                mc.thePlayer.closeScreen();
                opened = false;
                openWait = 0;
                waiting = 4;
            }
            index = nextChest;
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        int[] pos = chests.get(index);
        if (failedViewChest != index) {
            failedViewChest = index;
            failedChestViews.clear();
        }
        if (mc.currentScreen instanceof GuiChest && opened && mc.thePlayer.openContainer instanceof ContainerChest) {
            ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
            IInventory chest = container.getLowerChestInventory();
            if (chest.getSizeInventory() != 27
                || !(mc.theWorld.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityChest)) {
                mc.thePlayer.closeScreen();
                opened = false;
                openWait = 0;
                waiting = 5;
                status = "Unexpected chest screen; looting paused";
                return;
            }
            if (!collecting) {
                discovered.clear();
                for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                    ItemStack stack = chest.getStackInSlot(slot);
                    if (stack == null) continue;
                    discovered.add(new RewardLootPolicy.Entry(index, slot, probability(mc, stack, pos)));
                    scanned.put(slotKey(index, slot), stack.copy());
                }
                queue = RewardLootPolicy.ordered(discovered);
                queueIndex = 0;
                collecting = true;
                return;
            }
            if (pendingSlot >= 0) {
                ItemStack after = chest.getStackInSlot(pendingSlot);
                if (after != null && after.stackSize >= pendingCount) {
                    mc.thePlayer.closeScreen();
                    opened = false;
                    pendingSlot = -1;
                    packing = true;
                    packOpening = false;
                    packMoved = false;
                    backpackSlot = -1;
                    for (int i = 0; i < backpackTried.length; i++) {
                        backpackTried[i] = false;
                        slotRejectedForCurrentBag[i] = false;
                    }
                    status = "Moving collected rewards into a backpack";
                    return;
                }
                pendingSlot = -1;
                if (after == null) queueIndex++;
                else return;
            }
            if (queueIndex >= queue.size() || queue.get(queueIndex).chest != index) {
                mc.thePlayer.closeScreen();
                opened = false;
                openWait = 0;
                waiting = 4;
                return;
            }
            int best = queue.get(queueIndex).slot;
            if (best < 0 || best >= chest.getSizeInventory() || chest.getStackInSlot(best) == null) {
                queueIndex++;
                return;
            }
            ItemStack stack = chest.getStackInSlot(best);
            ItemStack expected = scanned.get(slotKey(index, best));
            if (expected == null || !expected.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(expected, stack)) {
                status = "Reward chest changed; looting stopped";
                cancel(mc);
                return;
            }
            int beforeCount = stack.stackSize;
            if (!BaritoneInventoryBridge.quickMove(mc, best)) {
                status = "Reward transfer unavailable";
                return;
            }
            pendingSlot = best;
            pendingCount = beforeCount;
            waiting = 5;
            status = "Collecting reward items";
            return;
        }
        if (mc.currentScreen != null) {
            status = "Close the open screen to collect rewards";
            return;
        }
        if (opened) {
            if (++openWait > 40) {
                status = "Retrying reward chest";
                opened = false;
                openWait = 0;
            }
            return;
        }
        double dx = pos[0] + 0.5D - mc.thePlayer.posX, dz = pos[2] + 0.5D - mc.thePlayer.posZ;
        double dy = pos[1] + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance());
        if (dx * dx + dy * dy + dz * dz > reach * reach || horizontal > 2.8D) {
            if (exploration != null && moveToChestView(mc, exploration, pos, center, failedChestViews))
                status = "Moving toward reward chests";
            else status = "Finding a safe position beside the reward chest";
            waiting = 10;
            return;
        }
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        net.minecraft.util.MovingObjectPosition hit = visibleChest(mc, eye, pos);
        if (hit == null || hit.blockX != pos[0] || hit.blockY != pos[1] || hit.blockZ != pos[2]) {
            if (!collecting) {
                for (int i = index + 1; i < chests.size(); i++) {
                    if (visibleChest(mc, eye, chests.get(i)) != null) {
                        java.util.Collections.swap(chests, index, i);
                        status = "Opening a visible reward chest";
                        return;
                    }
                }
            }
            if (exploration != null && moveToChestView(mc, exploration, pos, center, failedChestViews))
                status = "Moving toward reward chests";
            else status = "Finding a clear view of the reward chest";
            waiting = 10;
            return;
        }
        double hitX = hit.hitVec.xCoord - eye.xCoord, hitY = hit.hitVec.yCoord - eye.yCoord;
        double hitZ = hit.hitVec.zCoord - eye.zCoord;
        float targetYaw = (float) (Math.atan2(hitZ, hitX) * 180D / Math.PI) - 90F;
        float targetPitch = (float) (-Math.atan2(hitY, Math.hypot(hitX, hitZ)) * 180D / Math.PI);
        if (!turnToward(mc, exploration, targetYaw, targetPitch)) {
            chestAim.reset();
            status = "Facing reward chest";
            return;
        }
        net.minecraft.util.MovingObjectPosition aimed = mc.objectMouseOver;
        boolean matches = aimed != null
            && aimed.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            && aimed.blockX == pos[0]
            && aimed.blockY == pos[1]
            && aimed.blockZ == pos[2];
        if (!chestAim.ready(
            pos[0],
            pos[1],
            pos[2],
            matches,
            Math.sqrt(eye.squareDistanceTo(hit.hitVec)),
            Math.min(4D, mc.playerController.getBlockReachDistance()),
            net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw),
            targetPitch - mc.thePlayer.rotationPitch)) {
            status = "Facing reward chest";
            return;
        }
        if (mc.thePlayer.isSneaking()) {
            status = "Release sneak to open reward chest";
            return;
        }
        if (!com.kyroxova.lootgamesolver.minecraft.BaritoneBlockBridge
            .click(mc, pos[0], pos[1], pos[2], aimed.sideHit, aimed.hitVec, false)) {
            status = "Chest interaction unavailable";
            return;
        }
        chestAim.reset();
        if (exploration != null) exploration.clearAim();
        opened = true;
        waiting = 12;
        status = "Opening reward chest";
    }

    private boolean moveToChestView(Minecraft mc, Explorer exploration, int[] chest, int[] roomCenter,
        List<int[]> failedViews) {
        chestView = RewardChestView.findAndMove(mc, exploration, chest, roomCenter, failedViews);
        return chestView != null;
    }

    private void tickBackpack(Minecraft mc, Explorer exploration) {
        if (waiting > 0) {
            waiting--;
            return;
        }
        if (backpackAiming) {
            if (mc.currentScreen != null || backpackSlot < 0
                || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
                backpackAiming = false;
                restoreBackpackAim(mc, exploration);
                return;
            }
            if (!turnToward(mc, exploration, backpackOldYaw, -90F)) {
                backpackAimTicks = 0;
                status = "Facing clear space";
                return;
            }
            if (++backpackAimTicks < 2) return;
            if (!BaritoneInventoryBridge.useHotbarItem(mc, backpackSlot)) {
                backpackAiming = false;
                restoreBackpackAim(mc, exploration);
                status = "Backpack interaction unavailable";
                return;
            }
            if (temporaryBag && backpackSlot == bagHotbar) temporaryBagUsed = true;
            backpackAiming = false;
            restoreBackpackAim(mc, exploration);
            packOpening = true;
            java.util.Arrays.fill(slotRejectedForCurrentBag, false);
            openWait = 0;
            waiting = 5;
            return;
        }
        Container container = mc.thePlayer.openContainer;
        String name = container.getClass()
            .getName();
        boolean isBackpack = name.startsWith("de.eydamos.backpack.") || name.startsWith("de.eydamos.guiadvanced.")
            || name.startsWith("com.darkona.adventurebackpack.")
            || name.startsWith("forestry.")
            || name.toLowerCase(java.util.Locale.ROOT)
                .contains("backpack")
            || name.toLowerCase(java.util.Locale.ROOT)
                .contains("bag")
            || (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiChest)
                && !(mc.thePlayer.openContainer instanceof ContainerChest)
                && mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer);
        if (packOpening && mc.currentScreen instanceof GuiChest) {
            // Exclude chest containers from backpack transfers, including shared package names.
            mc.thePlayer.closeScreen();
            restoreBackpackAim(mc);
            packOpening = false;
            int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
            if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
            backpackSlot = -1;
            openWait = 0;
            waiting = 5;
            status = "Avoiding chest while opening backpack";
            return;
        }
        if (packOpening && mc.currentScreen instanceof GuiContainer && isBackpack) {
            if (packPendingSlot >= 0) {
                Slot pending = packPendingSlot < container.inventorySlots.size() ? container.getSlot(packPendingSlot)
                    : null;
                ItemStack remaining = pending == null ? null : pending.getStack();
                int bagCount = backpackItemCount(container, mc, packPendingStack);
                boolean sourceChanged = remaining == null || !remaining.isItemEqual(packPendingStack)
                    || !ItemStack.areItemStackTagsEqual(remaining, packPendingStack)
                    || remaining.stackSize < packPendingCount;
                if (sourceChanged && bagCount > packPendingBagCount) {
                    packMoved = true;
                } else if (sourceChanged) {
                    java.util.Arrays.fill(slotRejectedForCurrentBag, true);
                } else {
                    if (pending != null && pending.inventory == mc.thePlayer.inventory) {
                        int playerSlot = pending.getSlotIndex();
                        if (playerSlot >= 0 && playerSlot < slotRejectedForCurrentBag.length) {
                            slotRejectedForCurrentBag[playerSlot] = true;
                        }
                    }
                }
                packPendingSlot = -1;
                packPendingStack = null;
            }
            for (Object obj : container.inventorySlots) {
                Slot slot = (Slot) obj;
                int playerSlot = slot.getSlotIndex();
                ItemStack stack = slot.getStack();
                if (slot.inventory != mc.thePlayer.inventory || playerSlot < 0
                    || playerSlot >= 36
                    || slotRejectedForCurrentBag[playerSlot]
                    || !isPackable(mc, playerSlot, stack)) continue;
                if (!slot.canTakeStack(mc.thePlayer)) continue;
                if (!backpackHasSpace(container, mc, stack)) {
                    slotRejectedForCurrentBag[playerSlot] = true;
                    continue;
                }
                int beforeCount = stack.stackSize;
                packPendingStack = stack.copy();
                packPendingBagCount = backpackItemCount(container, mc, stack);
                if (!BaritoneInventoryBridge.quickMove(mc, slot.slotNumber)) {
                    status = "Backpack transfer unavailable";
                    return;
                }
                packPendingSlot = slot.slotNumber;
                packPendingCount = beforeCount;
                waiting = 5;
                return;
            }
            mc.thePlayer.closeScreen();
            restoreBackpackAim(mc);
            packOpening = false;
            if (oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
            oldSlot = -1;
            int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
            if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
            if (packMoved) {
                preferredBagSlot = temporaryBag ? bagHotbar : backpackSlot;
            } else if (preferredBagSlot == attemptedSlot) {
                preferredBagSlot = -1;
            }
            backpackSlot = -1;
            waiting = 5;
            openWait = 0;
            if (collecting && queueIndex < queue.size()) {
                packing = false;
                packMoved = false;
                status = "Resuming reward collection";
            } else if (!hasPackableItems(mc)) {
                packing = false;
                packMoved = false;
            }
            return;
        }
        if (packOpening) {
            if (++openWait > 40) {
                restoreBackpackAim(mc);
                if (oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
                oldSlot = -1;
                int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
                if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
                backpackSlot = -1;
                packOpening = false;
                openWait = 0;
                waiting = 5;
            }
            return;
        }
        restoreBackpackAim(mc);
        if (mc.currentScreen != null) {
            status = "Close the open screen to use a backpack";
            return;
        }
        if (backpackSlot < 0 || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
            backpackSlot = -1;
            if (temporaryBag && !backpackTried[bagSource]
                && isBackpack(mc.thePlayer.inventory.getStackInSlot(bagHotbar))) backpackSlot = bagHotbar;
            if (backpackSlot < 0 && preferredBagSlot >= 0
                && preferredBagSlot < 36
                && !backpackTried[preferredBagSlot]
                && isBackpack(mc.thePlayer.inventory.getStackInSlot(preferredBagSlot))) backpackSlot = preferredBagSlot;
            for (int i = 0; backpackSlot < 0 && i < 36; i++)
                if (!backpackTried[i] && isBackpack(mc.thePlayer.inventory.getStackInSlot(i))) {
                    backpackSlot = i;
                    break;
                }
        }
        if (backpackSlot < 0) {
            if (temporaryBag && temporaryBagUsed && !restoringBag) {
                if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                    restoringBag = true;
                    status = "Restoring hotbar";
                    return;
                }
                status = "Hotbar changed; bag kept";
                temporaryBag = false;
                temporaryBagUsed = false;
            }
            if (packingOnly) status = "No available backpack; items kept";
            else if (!collecting && index >= chests.size()) status = "Reward chests collected";
            else status = "Inventory and backpacks full; rewards remain in chest";
            if (hasPackableItems(mc) || collecting) {
                boolean saved = center == null || !collecting
                    || pendingLoot.save(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, center, gameType);
                mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] Inventory or backpacks are full or unavailable. Please make space."
                            + (collecting ? " Uncollected rewards remain in the chest." : "")
                            + (saved ? "" : " Could not save the loot checkpoint.")));
            }
            packing = false;
            active = false;
            return;
        }
        if (backpackSlot >= 9) {
            int selected = backpackSlot;
            boolean hotbarFull = true;
            for (int i = 0; i < 9; i++) {
                if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                    hotbarFull = false;
                    break;
                }
            }
            if (hotbarFull && temporaryBag && temporaryBagUsed && bagHotbar >= 0 && !restoringBag) {
                if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                    restoringBag = true;
                    status = "Making space in hotbar";
                    return;
                }
            }
            backpackSlot = -1;
            if (backpackTransfer.start(mc, selected, baseline)) {
                backpackTransferAttempts = 0;
                bagSource = selected;
                bagHotbar = backpackTransfer.hotbarSlot();
                ItemStack displaced = mc.thePlayer.inventory.getStackInSlot(bagHotbar);
                bagDisplaced = displaced == null ? null : displaced.copy();
                temporaryBag = true;
                temporaryBagUsed = false;
                return;
            }
            if (++backpackTransferAttempts >= 20) {
                backpackTried[selected] = true;
                backpackTransferAttempts = 0;
                status = "Backpack unavailable";
            }
            waiting = 2;
            return;
        }
        if (oldSlot < 0) oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = backpackSlot;
        mc.playerController.updateController();
        packMoved = false;
        if (!BaritoneInventoryBridge.useHotbarItem(mc, backpackSlot)) {
            status = "Backpack interaction unavailable";
            waiting = 5;
            return;
        }
        if (temporaryBag && backpackSlot == bagHotbar) temporaryBagUsed = true;
        packOpening = true;
        java.util.Arrays.fill(slotRejectedForCurrentBag, false);
        openWait = 0;
        waiting = 5;
    }

    private static boolean turnToward(Minecraft mc, Explorer exploration, float yaw, float pitch) {
        if (exploration != null) {
            exploration.look(yaw, pitch, true);
        } else {
            float yawDelta = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            float pitchDelta = pitch - mc.thePlayer.rotationPitch;
            float yawStep = Math.max(-6F, Math.min(6F, yawDelta));
            float pitchStep = Math.max(-6F, Math.min(6F, pitchDelta));
            mc.thePlayer.setAngles(yawStep / 0.15F, -pitchStep / 0.15F);
        }
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        float pitchError = pitch - mc.thePlayer.rotationPitch;
        return Math.abs(yawError) < 2.5F && Math.abs(pitchError) < 2.5F;
    }

    private void restoreBackpackAim(Minecraft mc) {
        restoreBackpackAim(mc, null);
    }

    private void restoreBackpackAim(Minecraft mc, Explorer exploration) {
        if (!backpackAimSaved || mc.thePlayer == null) return;
        backpackAimSaved = false;
        if (exploration != null) exploration.clearAim();
    }

    private boolean compactBackpackHotbar(Minecraft mc) {
        // Keep backpacks in the hotbar while in structure / looting instead of repeatedly moving them back and forth
        return false;
    }

    private static boolean isBackpack(ItemStack stack) {
        return InventoryProtection.bag(stack);
    }

    private static boolean backpackHasSpace(Container container, Minecraft mc, ItemStack stack) {
        for (Object obj : container.inventorySlots) {
            Slot slot = (Slot) obj;
            if (slot.inventory == mc.thePlayer.inventory || !slot.isItemValid(stack)) continue;
            ItemStack existing = slot.getStack();
            if (existing == null) return true;
            if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize < Math.min(existing.getMaxStackSize(), slot.getSlotStackLimit())) return true;
        }
        return false;
    }

    private static int backpackItemCount(Container container, Minecraft mc, ItemStack stack) {
        if (stack == null) return 0;
        int count = 0;
        for (Object obj : container.inventorySlots) {
            Slot slot = (Slot) obj;
            ItemStack existing = slot.getStack();
            if (slot.inventory != mc.thePlayer.inventory && existing != null
                && existing.isItemEqual(stack)
                && ItemStack.areItemStackTagsEqual(existing, stack)) count += existing.stackSize;
        }
        return count;
    }

    private boolean isPackable(Minecraft mc, int slotIndex, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return false;
        if (temporaryBag && slotIndex == bagSource) return false;
        if (InventoryProtection.bag(stack)) return false;
        if (ExplorerSustain.isHealingAxe(stack)) return false;
        if (InventoryProtection.essential(stack)) return false;
        if (isReservedBuildingStack(mc, slotIndex, stack)) return false;
        String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        if (id != null && id.toLowerCase(java.util.Locale.ROOT)
            .contains("torch")) return false;
        return true;
    }

    private boolean hasPackableItems(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isPackable(mc, i, stack)) return true;
        }
        return false;
    }

    private static boolean isReservedBuildingStack(Minecraft mc, int slot, ItemStack stack) {
        if (mc == null || stack == null) return false;
        return slot == TrashCleanup.findBuildingReserveSlot(mc);
    }

    private static long slotKey(int chest, int slot) {
        return ((long) chest << 32) | (slot & 0xffffffffL);
    }

    private double probability(Minecraft mc, ItemStack stack, int[] chest) {
        if (!mc.isSingleplayer()) return Double.NaN;
        int dx = chest[0] - center[0], dz = chest[2] - center[2];
        int stage = stageAt(dx, dz);
        if (stage == 0 || gameType == null) return Double.NaN;
        String field = gameType == MiniGame.GAME_OF_LIGHT ? "rewardsGol"
            : gameType == MiniGame.MINESWEEPER ? "rewardsMinesweeper"
                : gameType == MiniGame.SUDOKU ? "rewardsSudoku" : null;
        if (field == null) return Double.NaN;
        try {
            Class<?> configs = Class.forName("ru.timeconqueror.lootgames.common.config.LGConfigs");
            Object rewards = configs.getField("REWARDS")
                .get(null);
            Object stages = rewards.getClass()
                .getField(field)
                .get(rewards);
            Object reward = stages.getClass()
                .getMethod("getStage" + stage)
                .invoke(stages);
            String table = (String) reward.getClass()
                .getMethod("getLootTable", net.minecraft.world.World.class)
                .invoke(reward, mc.theWorld);
            WeightedRandomChestContent[] entries = ChestGenHooks.getItems(table, new Random(0L));
            return RewardWeighting.probability(stack, entries);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    static int stageAt(int dx, int dz) {
        if (dx == 0 && dz == -1) return 1;
        if (dx == 1 && dz == 0) return 2;
        if (dx == 0 && dz == 1) return 3;
        if (dx == -1 && dz == 0) return 4;
        return 0;
    }
}
