package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemTool;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.Vec3;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;
import com.kyroxova.lootgamesolver.core.MiniGame;

final class RewardChestLooter {
    private final List<int[]> chests = new ArrayList<int[]>();
    private final List<RewardLootPolicy.Entry> discovered = new ArrayList<RewardLootPolicy.Entry>();
    private List<RewardLootPolicy.Entry> queue = new ArrayList<RewardLootPolicy.Entry>();
    private final List<ItemStack> rewardTypes = new ArrayList<ItemStack>();
    private final Map<Long, ItemStack> scanned = new HashMap<Long, ItemStack>();
    private final boolean[] initiallyOccupied = new boolean[36];
    private final boolean[] backpackTried = new boolean[36];
    private final InventoryToolTransfer backpackTransfer = new InventoryToolTransfer();
    private int[] center;
    private int[] chestView;
    private final List<int[]> failedChestViews = new ArrayList<int[]>();
    private int failedViewChest = -1;
    private MiniGame gameType;
    private int ticks, index, waiting, openWait, pendingSlot = -1, pendingCount;
    private int queueIndex, backpackSlot = -1, oldSlot = -1;
    private int packPendingSlot = -1, packPendingCount;
    private float backpackOldYaw, backpackOldPitch;
    private boolean active, opened, collecting, packing, packOpening, packMoved, packingExisting;
    private boolean existingInventoryPackRequested;
    private boolean backpackAimSaved;
    private boolean backpackAiming;
    private boolean temporaryBag, temporaryBagUsed, restoringBag;
    private int bagSource, bagHotbar;
    private ItemStack bagDisplaced;
    private String status = "";

    void start(Minecraft mc, int[] position, MiniGame type) {
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
        packingExisting = false;
        existingInventoryPackRequested = false;
        queueIndex = 0;
        backpackSlot = -1;
        oldSlot = -1;
        packPendingSlot = -1;
        backpackAimSaved = false;
        backpackAiming = false;
        temporaryBag = false;
        temporaryBagUsed = false;
        restoringBag = false;
        bagDisplaced = null;
        discovered.clear();
        queue.clear();
        rewardTypes.clear();
        scanned.clear();
        for (int i = 0; i < 36; i++) {
            initiallyOccupied[i] = mc.thePlayer.inventory.getStackInSlot(i) != null;
            backpackTried[i] = false;
        }
        active = true;
        chests.clear();
        status = "Waiting for reward chests";
    }

    boolean active() { return active; }
    boolean completed() { return "Reward chests collected".equals(status); }
    String status() { return status; }

    void cancel(Minecraft mc) {
        backpackTransfer.cancel(mc);
        if ((opened || packing) && mc.thePlayer != null && mc.currentScreen instanceof GuiContainer)
            mc.thePlayer.closeScreen();
        if (mc.thePlayer != null && oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
        restoreBackpackAim(mc);
        active = false;
        opened = false;
    }

    void tick(Minecraft mc, ExplorationBeta exploration) {
        if (!active || mc.thePlayer == null || mc.theWorld == null) return;
        if (exploration != null && exploration.navigating()) {
            status = "Moving toward reward chests";
            return;
        }
        if (chestView != null) {
            Vec3 eye = Vec3.createVectorHelper(mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
            if (mc.thePlayer.getDistanceSq(chestView[0] + 0.5D, chestView[1] + 1D, chestView[2] + 0.5D) > 2.25D
                || index < chests.size() && visibleChest(mc, eye, chests.get(index)) == null)
                failedChestViews.add(chestView);
            chestView = null;
        }
        if (exploration != null) exploration.consumePuzzleCenterArrival();
        if (backpackTransfer.active()) {
            if (!backpackTransfer.tick(mc)) { status = "Backpack transfer interrupted"; active = false; }
            else if (!backpackTransfer.active() && restoringBag) {
                temporaryBag = false;
                temporaryBagUsed = false;
                restoringBag = false;
                backpackSlot = -1;
            }
            return;
        }
        if (temporaryBag && temporaryBagUsed && !packOpening && mc.currentScreen == null) {
            if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                restoringBag = true;
                status = "Restoring hotbar";
            } else {
                status = "Hotbar changed; bag kept";
                active = false;
            }
            return;
        }
        if (center == null) { status = "Reward center unavailable"; active = false; return; }
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
            chests.sort((a, b) -> Integer.compare(
                Math.abs(a[0] - center[0]) + Math.abs(a[2] - center[2]),
                Math.abs(b[0] - center[0]) + Math.abs(b[2] - center[2])));
            while (chests.size() > 4) chests.remove(chests.size() - 1);
            if (chests.isEmpty()) {
                status = "Waiting for reward chests";
                return;
            }
        }
        if (packing) { tickBackpack(mc); return; }
        if (!collecting && index >= chests.size()) {
            queue = RewardLootPolicy.ordered(discovered);
            collecting = true;
            queueIndex = 0;
            opened = false;
        }
        if (collecting) {
            if (queueIndex >= queue.size()) {
                if (opened && mc.currentScreen instanceof GuiChest) mc.thePlayer.closeScreen();
                opened = false;
                if (compactBackpackHotbar(mc)) return;
                if (!existingInventoryPackRequested && hasSafeExistingItems(mc)) {
                    existingInventoryPackRequested = true;
                    packing = true;
                    packOpening = false;
                    packMoved = false;
                    backpackSlot = -1;
                    for (int i = 0; i < backpackTried.length; i++) backpackTried[i] = false;
                    status = "Packing leftover inventory items";
                    return;
                }
                status = "Reward chests collected";
                active = false;
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
        if (waiting > 0) { waiting--; return; }
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
                for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                    ItemStack stack = chest.getStackInSlot(slot);
                    if (stack == null) continue;
                    discovered.add(new RewardLootPolicy.Entry(index, slot, probability(mc, stack, pos)));
                    rewardTypes.add(stack.copy());
                    scanned.put(slotKey(index, slot), stack.copy());
                }
                mc.thePlayer.closeScreen();
                opened = false;
                openWait = 0;
                index++;
                waiting = 4;
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
                    for (int i = 0; i < backpackTried.length; i++) backpackTried[i] = false;
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
            pendingSlot = best;
            pendingCount = stack.stackSize;
            mc.playerController.windowClick(container.windowId, best, 0, 1, mc.thePlayer);
            waiting = 5;
            status = "Collecting reward items";
            return;
        }
        if (mc.currentScreen != null) { status = "Close the open screen to collect rewards"; return; }
        if (opened) {
            if (++openWait > 40) { status = "Retrying reward chest"; opened = false; openWait = 0; }
            return;
        }
        double dx = pos[0] + 0.5D - mc.thePlayer.posX, dz = pos[2] + 0.5D - mc.thePlayer.posZ;
        double dy = pos[1] + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (dx * dx + dy * dy + dz * dz > 16D || horizontal > 2.5D) {
            if (exploration != null && moveToChestView(mc, exploration, pos))
                status = "Moving toward reward chests";
            else status = "Finding a safe position beside the reward chest";
            waiting = 10;
            return;
        }
        Vec3 eye = Vec3.createVectorHelper(mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
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
            if (exploration != null && moveToChestView(mc, exploration, pos))
                status = "Moving toward reward chests";
            else status = "Finding a clear view of the reward chest";
            waiting = 10;
            return;
        }
        double hitX = hit.hitVec.xCoord - eye.xCoord, hitY = hit.hitVec.yCoord - eye.yCoord;
        double hitZ = hit.hitVec.zCoord - eye.zCoord;
        float targetYaw = (float) (Math.atan2(hitZ, hitX) * 180D / Math.PI) - 90F;
        float targetPitch = (float) (-Math.atan2(hitY, Math.hypot(hitX, hitZ)) * 180D / Math.PI);
        if (!turnToward(mc, targetYaw, targetPitch)) {
            status = "Facing reward chest";
            return;
        }
        if (mc.thePlayer.isSneaking()) {
            status = "Release sneak to open reward chest";
            return;
        }
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
            pos[0], pos[1], pos[2], hit.sideHit, hit.hitVec);
        opened = true;
        waiting = 12;
        status = "Opening reward chest";
    }

    private static net.minecraft.util.MovingObjectPosition visibleChest(Minecraft mc, Vec3 eye, int[] pos) {
        double[] offsets = { 0.2D, 0.5D, 0.8D };
        for (double y : offsets) for (double x : offsets) for (double z : offsets) {
            Vec3 point = Vec3.createVectorHelper(pos[0] + x, pos[1] + y, pos[2] + z);
            if (eye.squareDistanceTo(point) > 16D) continue;
            net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
            if (hit != null && hit.blockX == pos[0] && hit.blockY == pos[1] && hit.blockZ == pos[2]) return hit;
        }
        return null;
    }

    private boolean moveToChestView(Minecraft mc, ExplorationBeta exploration, int[] chest) {
        int playerFloorY = (int) Math.floor(mc.thePlayer.boundingBox.minY) - 1;
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        for (int floorY = Math.min(playerFloorY, chest[1] - 1); floorY <= Math.max(playerFloorY, chest[1]); floorY++)
        for (int radius = 1; radius <= 4; radius++) for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = chest[0] + dx, z = chest[2] + dz;
                if (dx * dx + dz * dz > 4) continue;
                if (center != null && Math.abs(x - center[0]) <= 1 && Math.abs(z - center[2]) <= 1) continue;
                boolean failed = false;
                for (int[] view : failedChestViews)
                    if (view[0] == x && view[1] == floorY && view[2] == z) { failed = true; break; }
                if (failed || !mc.theWorld.blockExists(x, floorY, z)
                    || !mc.theWorld.getBlock(x, floorY, z).getMaterial().isSolid()
                    || !mc.theWorld.isAirBlock(x, floorY + 1, z)
                    || !mc.theWorld.isAirBlock(x, floorY + 2, z)) continue;
                Vec3 eye = Vec3.createVectorHelper(x + 0.5D, floorY + 1D + mc.thePlayer.getEyeHeight(), z + 0.5D);
                if (visibleChest(mc, eye, chest) != null) positions.add(new int[] { x, floorY, z });
            }
        positions.sort((a, b) -> {
            int da = Math.abs(a[0] - chest[0]) + Math.abs(a[2] - chest[2]);
            int db = Math.abs(b[0] - chest[0]) + Math.abs(b[2] - chest[2]);
            return da != db ? Integer.compare(da, db) : Double.compare(
                mc.thePlayer.getDistanceSq(a[0] + 0.5D, a[1] + 1D, a[2] + 0.5D),
                mc.thePlayer.getDistanceSq(b[0] + 0.5D, b[1] + 1D, b[2] + 0.5D));
        });
        for (int[] position : positions) if (exploration.moveToRewardView(mc, position)) {
            chestView = position;
            return true;
        }
        failedChestViews.clear();
        return false;
    }

    private void tickBackpack(Minecraft mc) {
        if (waiting > 0) { waiting--; return; }
        if (backpackAiming) {
            if (mc.currentScreen != null || backpackSlot < 0
                || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
                backpackAiming = false;
                restoreBackpackAim(mc);
                return;
            }
            if (!turnToward(mc, backpackOldYaw, -90F)) { status = "Facing clear space"; return; }
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            if (temporaryBag && backpackSlot == bagHotbar) temporaryBagUsed = true;
            backpackAiming = false;
            packOpening = true;
            openWait = 0;
            waiting = 5;
            return;
        }
        Container container = mc.thePlayer.openContainer;
        String name = container.getClass().getName();
        boolean isBackpack = name.startsWith("de.eydamos.backpack.inventory.container.")
            || name.startsWith("de.eydamos.guiadvanced.")
            || name.startsWith("com.darkona.adventurebackpack.inventory.Container");
        if (packOpening && mc.currentScreen instanceof GuiChest) {
            // Exclude chest containers from backpack transfers, including shared package names.
            mc.thePlayer.closeScreen();
            restoreBackpackAim(mc);
            packOpening = false;
            openWait = 0;
            waiting = 5;
            status = "Avoiding chest while opening backpack";
            return;
        }
        if (packOpening && mc.currentScreen instanceof GuiContainer && isBackpack) {
            restoreBackpackAim(mc);
            if (packPendingSlot >= 0) {
                ItemStack remaining = container.getSlot(packPendingSlot).getStack();
                if (remaining == null || remaining.stackSize < packPendingCount) {
                    packMoved = true;
                    if (packingExisting) {
                        packingExisting = false;
                        packPendingSlot = -1;
                        waiting = 3;
                    }
                }
                else {
                    mc.thePlayer.closeScreen();
                    packOpening = false;
                    if (oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
                    oldSlot = -1;
                    if (backpackSlot >= 0 && backpackSlot < backpackTried.length) backpackTried[backpackSlot] = true;
                    backpackSlot = -1;
                    packPendingSlot = -1;
                    packingExisting = false;
                    if (packMoved) { waiting = 5; openWait = 0; }
                    return;
                }
                packPendingSlot = -1;
            }
            for (Object obj : container.inventorySlots) {
                Slot slot = (Slot) obj;
                int playerSlot = slot.getSlotIndex();
                if (slot.inventory != mc.thePlayer.inventory || playerSlot < 0 || playerSlot >= 36
                    || initiallyOccupied[playerSlot] || InventoryProtection.essential(slot.getStack())
                    || !isReward(slot.getStack())) continue;
                if (!slot.canTakeStack(mc.thePlayer)) continue;
                packPendingSlot = slot.slotNumber;
                packPendingCount = slot.getStack().stackSize;
                mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, 1, mc.thePlayer);
                waiting = 5;
                return;
            }
            // Free a main-inventory slot while leaving the hotbar and equipment untouched.
            for (Object obj : container.inventorySlots) {
                Slot slot = (Slot) obj;
                int playerSlot = slot.getSlotIndex();
                ItemStack stack = slot.getStack();
                if (slot.inventory != mc.thePlayer.inventory || playerSlot < 9 || playerSlot >= 36
                    || !safeToStore(stack) || isReservedBuildingStack(mc, playerSlot, stack)
                    || !slot.canTakeStack(mc.thePlayer)) continue;
                packingExisting = true;
                packPendingSlot = slot.slotNumber;
                packPendingCount = stack.stackSize;
                mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, 1, mc.thePlayer);
                waiting = 5;
                return;
            }
            mc.thePlayer.closeScreen();
            packOpening = false;
            if (oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
            oldSlot = -1;
            if (packMoved) {
                if (backpackSlot >= 0 && backpackSlot < backpackTried.length) backpackTried[backpackSlot] = true;
                backpackSlot = -1;
                waiting = 5;
                openWait = 0;
                if (collecting && queueIndex < queue.size()) {
                    packing = false;
                    packMoved = false;
                    status = "Resuming reward collection";
                }
                return;
            }
            if (backpackSlot >= 0 && backpackSlot < backpackTried.length) backpackTried[backpackSlot] = true;
            backpackSlot = -1;
        }
        if (packOpening) {
            if (++openWait > 40) {
                restoreBackpackAim(mc);
                if (oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
                oldSlot = -1;
                if (backpackSlot >= 0 && backpackSlot < backpackTried.length) backpackTried[backpackSlot] = true;
                backpackSlot = -1;
                packOpening = false;
                openWait = 0;
                waiting = 5;
            }
            return;
        }
        if (mc.currentScreen != null) { status = "Close the open screen to use a backpack"; return; }
        if (backpackSlot < 0 || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
            backpackSlot = -1;
            if (temporaryBag && !temporaryBagUsed && isBackpack(mc.thePlayer.inventory.getStackInSlot(bagHotbar)))
                backpackSlot = bagHotbar;
            for (int i = 0; backpackSlot < 0 && i < 36; i++) if (!backpackTried[i] && isBackpack(mc.thePlayer.inventory.getStackInSlot(i))) {
                backpackSlot = i;
                break;
            }
        }
        if (backpackSlot < 0) {
            if (collecting && queueIndex >= queue.size()) status = "Reward chests collected";
            else status = "Inventory and backpacks full; rewards remain in chest";
            active = false;
            return;
        }
        if (backpackSlot >= 9) {
            int selected = backpackSlot;
            backpackTried[selected] = true;
            backpackSlot = -1;
            if (backpackTransfer.start(mc, selected)) {
                bagSource = selected;
                bagHotbar = backpackTransfer.hotbarSlot();
                ItemStack displaced = mc.thePlayer.inventory.getStackInSlot(bagHotbar);
                bagDisplaced = displaced == null ? null : displaced.copy();
                temporaryBag = true;
                temporaryBagUsed = false;
                return;
            }
            waiting = 2;
            return;
        }
        if (oldSlot < 0) oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = backpackSlot;
        mc.playerController.updateController();
        backpackOldYaw = mc.thePlayer.rotationYaw;
        backpackOldPitch = mc.thePlayer.rotationPitch;
        backpackAimSaved = true;
        packMoved = false;
        backpackAiming = true;
    }

    private static boolean turnToward(Minecraft mc, float yaw, float pitch) {
        float yawDelta = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        float pitchDelta = pitch - mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw += Math.max(-12F, Math.min(12F, yawDelta));
        mc.thePlayer.rotationPitch += Math.max(-12F, Math.min(12F, pitchDelta));
        return Math.abs(yawDelta) < 1F && Math.abs(pitchDelta) < 1F;
    }

    private void restoreBackpackAim(Minecraft mc) {
        if (!backpackAimSaved || mc.thePlayer == null) return;
        mc.thePlayer.rotationYaw = backpackOldYaw;
        mc.thePlayer.rotationPitch = backpackOldPitch;
        backpackAimSaved = false;
    }

    private boolean compactBackpackHotbar(Minecraft mc) {
        int first = -1;
        for (int i = 0; i < 9; i++) {
            if (!InventoryProtection.bag(mc.thePlayer.inventory.getStackInSlot(i))) continue;
            if (first < 0) { first = i; continue; }
            for (int slot = 9; slot < 36; slot++) {
                if (mc.thePlayer.inventory.getStackInSlot(slot) == null
                    && backpackTransfer.clearHotbar(mc, i, slot)) {
                    status = "Organizing backpacks";
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReward(ItemStack stack) {
        if (stack == null) return false;
        for (ItemStack reward : rewardTypes) if (stack.isItemEqual(reward) && ItemStack.areItemStackTagsEqual(stack, reward)) return true;
        return false;
    }

    private static boolean isBackpack(ItemStack stack) {
        return InventoryProtection.bag(stack);
    }

    private static boolean safeToStore(ItemStack stack) {
        return stack != null && !InventoryProtection.essential(stack);
    }

    private static boolean hasSafeExistingItems(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return false;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (safeToStore(stack) && !isReservedBuildingStack(mc, i, stack)) return true;
        }
        return false;
    }

    private static boolean isReservedBuildingStack(Minecraft mc, int slot, ItemStack stack) {
        if (mc == null || stack == null) return false;
        String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        if (!TrashPolicy.buildingReserve(id)) return false;
        int largest = -1, largestSize = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(i);
            if (candidate == null) continue;
            String candidateId = (String) Item.itemRegistry.getNameForObject(candidate.getItem());
            if (TrashPolicy.buildingReserve(candidateId) && candidate.stackSize > largestSize) {
                largest = i;
                largestSize = candidate.stackSize;
            }
        }
        return slot == largest;
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
            Object rewards = configs.getField("REWARDS").get(null);
            Object stages = rewards.getClass().getField(field).get(rewards);
            Object reward = stages.getClass().getMethod("getStage" + stage).invoke(stages);
            String table = (String) reward.getClass().getMethod("getLootTable", net.minecraft.world.World.class)
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
