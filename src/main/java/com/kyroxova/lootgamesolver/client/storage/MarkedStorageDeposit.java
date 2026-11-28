package com.kyroxova.lootgamesolver.client.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.client.VisibleBlockInteraction;

/** Opens marked, reachable inventories and verifies each deposit before continuing. */
public final class MarkedStorageDeposit {

    public enum Result {
        WORKING,
        DONE,
        FULL_OR_UNAVAILABLE,
        FAILED
    }

    private final String[] targets;
    private final World world;
    private final StorageLootLedger ledger;
    private final InventoryBaseline baseline;
    private final Map<String, List<ItemStack>> rejectedTypesByTarget;
    private final boolean[] attempted;
    private int currentTargetIndex = -1;
    private int wait, oldSlot = -1, moved;
    private StorageTarget opening;
    private StorageTarget approach;
    private final BaritoneNavigationBridge navigation = new BaritoneNavigationBridge();
    private int approachTicks, approachRetries, lastApproachIndex = -1;
    private int aimIndex = -1, aimTicks;
    private IInventory inventory;
    private VerifiedStorageTransfer transfer;

    public MarkedStorageDeposit(Minecraft mc, String[] targets, StorageLootLedger ledger, InventoryBaseline baseline) {
        this(mc, targets, ledger, baseline, new java.util.HashMap<String, List<ItemStack>>());
    }

    public MarkedStorageDeposit(Minecraft mc, String[] targets, StorageLootLedger ledger, InventoryBaseline baseline,
        Map<String, List<ItemStack>> rejectedTypesByTarget) {
        world = mc.theWorld;
        this.targets = targets.clone();
        this.ledger = ledger;
        this.baseline = baseline;
        this.rejectedTypesByTarget = rejectedTypesByTarget != null ? rejectedTypesByTarget
            : new java.util.HashMap<String, List<ItemStack>>();
        this.attempted = new boolean[this.targets.length];
    }

    public Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld != world
            || mc.playerController == null
            || mc.thePlayer.inventory.getItemStack() != null
            || mc.thePlayer.hurtTime > 0) return Result.FAILED;
        if (approach != null) {
            Result approachResult = tickApproach(mc);
            if (approachResult != null) return approachResult;
        }
        if (transfer != null) return tickTransfer(mc);
        if (opening != null) {
            Result openingResult = tickOpening(mc);
            if (openingResult != null) return openingResult;
        }
        if (mc.currentScreen != null || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
            return Result.FAILED;
        return selectAndOpenNextTarget(mc);
    }

    private Result tickApproach(Minecraft mc) {
        if (mc.currentScreen != null || ++approachTicks > 400
            || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
            || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
            || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
            || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindRight)) return Result.FAILED;
        if (navigation.state() == BaritoneNavigationBridge.State.RUNNING) return Result.WORKING;
        boolean reached = navigation.completed();
        navigation.stop();
        approach = null;
        if (reached && ++approachRetries <= 2 && currentTargetIndex >= 0 && currentTargetIndex < attempted.length) {
            attempted[currentTargetIndex] = false;
        }
        return null;
    }

    private Result tickTransfer(Minecraft mc) {
        VerifiedStorageTransfer.Result result = transfer.tick(mc);
        if (result == VerifiedStorageTransfer.Result.WORKING) return Result.WORKING;
        if (result == VerifiedStorageTransfer.Result.FAILED) return Result.FAILED;
        if (result == VerifiedStorageTransfer.Result.FULL_OR_REJECTED && currentTargetIndex >= 0
            && currentTargetIndex < targets.length) {
            rememberRejectedTypes(targets[currentTargetIndex], transfer.rejectedTypes());
        }
        moved += transfer.moved();
        mc.thePlayer.closeScreen();
        transfer = null;
        inventory = null;
        return result == VerifiedStorageTransfer.Result.DONE ? Result.DONE : Result.WORKING;
    }

    private Result tickOpening(Minecraft mc) {
        if (mc.currentScreen instanceof GuiContainer) return attachTransferToOpenContainer(mc);
        if (mc.currentScreen != null) return Result.FAILED;
        if (++wait < 40) return Result.WORKING;
        opening = null;
        inventory = null;
        restoreSlot(mc);
        return null;
    }

    private Result attachTransferToOpenContainer(Minecraft mc) {
        IInventory opened = inventory;
        if (mc.thePlayer.openContainer instanceof ContainerChest) {
            IInventory chest = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
            if (chest instanceof InventoryLargeChest && ((InventoryLargeChest) chest).isPartOfLargeChest(inventory))
                opened = chest;
            else if (vanillaChestView(mc, opening, inventory, chest)) opened = chest;
        }
        IInventory binnieWindowInv = null;
        BinnieCompartmentAdapter binnie = BinnieCompartmentAdapter.detect(mc.thePlayer.openContainer);
        if (binnie != null) {
            IInventory machineInv = binnie.getMachineInventory();
            if (machineInv != null && (machineInv == inventory || opening.loadedInventory(mc) == machineInv))
                opened = machineInv;
            binnieWindowInv = binnie.getWindowInventory();
        }
        boolean matching = false;
        for (Object value : mc.thePlayer.openContainer.inventorySlots) {
            IInventory slotInventory = ((Slot) value).inventory;
            if (slotInventory == opened) matching = true;
            else if (slotInventory != mc.thePlayer.inventory
                && (binnieWindowInv == null || slotInventory != binnieWindowInv)) return Result.FAILED;
        }
        if (!matching || opening.loadedInventory(mc) != inventory) return Result.FAILED;
        transfer = new VerifiedStorageTransfer(mc.thePlayer.openContainer, opened, ledger, baseline);
        opening = null;
        restoreSlot(mc);
        return Result.WORKING;
    }

    private Result selectAndOpenNextTarget(Minecraft mc) {
        while (true) {
            int bestIndex = findClosestTargetIndex(mc);
            if (bestIndex < 0) break;
            attempted[bestIndex] = true;
            currentTargetIndex = bestIndex;
            StorageTarget target = StorageTarget.parse(targets[bestIndex]);
            IInventory candidate = target == null ? null : target.loadedInventory(mc);
            double[] pos = target == null ? null : target.loadedPosition(mc);
            if (candidate == null || pos == null) continue;
            double distance = mc.thePlayer.getDistanceSq(pos[0], pos[1], pos[2]);
            if (distance > 6.25D) {
                if (distance <= 64D && approach(target, pos)) return Result.WORKING;
                continue;
            }
            int empty = findInteractionHotbarSlot(mc, target);
            if (empty < 0) continue;
            Result interact = tryOpenTarget(mc, bestIndex, target, candidate, pos, empty);
            if (interact != null) return interact;
        }
        return Result.FULL_OR_UNAVAILABLE;
    }

    private int findClosestTargetIndex(Minecraft mc) {
        int bestIndex = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < targets.length; i++) {
            if (attempted[i] || rejectedCurrentBatch(targets[i], mc)) continue;
            StorageTarget target = StorageTarget.parse(targets[i]);
            if (target == null) continue;
            IInventory candidate = target.loadedInventory(mc);
            double[] pos = target.loadedPosition(mc);
            if (candidate == null || pos == null) continue;
            double distSq = mc.thePlayer.getDistanceSq(pos[0], pos[1], pos[2]);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findInteractionHotbarSlot(Minecraft mc, StorageTarget target) {
        for (int i = 0; i < 9; i++) {
            if (mc.thePlayer.inventory.getStackInSlot(i) == null) return i;
        }
        if (target.entity != null || mc.thePlayer.isSneaking()) return -1;
        net.minecraft.block.Block block = world.getBlock(target.x, target.y, target.z);
        if (!(block instanceof net.minecraft.block.BlockChest) && !"cpw.mods.ironchest.BlockIronChest".equals(
            block.getClass()
                .getName()))
            return -1;
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && (stack.getItem()
                .getClass() == net.minecraft.item.Item.class
                || stack.getItem()
                    .getClass() == net.minecraft.item.ItemBlock.class))
                return i;
        }
        return -1;
    }

    private Result tryOpenTarget(Minecraft mc, int bestIndex, StorageTarget target, IInventory candidate, double[] pos,
        int empty) {
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        MovingObjectPosition hit = world.rayTraceBlocks(eye, Vec3.createVectorHelper(pos[0], pos[1], pos[2]));
        boolean blockBlocked = target.entity == null
            && (hit == null || hit.blockX != target.x || hit.blockY != target.y || hit.blockZ != target.z);
        boolean entityBlocked = target.entity != null
            && (!(candidate instanceof Entity) || !mc.thePlayer.canEntityBeSeen((Entity) candidate));
        if (blockBlocked || entityBlocked) return approach(target, pos) ? Result.WORKING : null;

        if (aimIndex != bestIndex) {
            aimIndex = bestIndex;
            aimTicks = 0;
        }
        Vec3 aimPoint = target.entity == null ? hit.hitVec
            : Vec3.createVectorHelper(pos[0], ((Entity) candidate).posY + ((Entity) candidate).height * 0.5D, pos[2]);
        double ax = aimPoint.xCoord - eye.xCoord, ay = aimPoint.yCoord - eye.yCoord, az = aimPoint.zCoord - eye.zCoord;
        float yaw = (float) Math.toDegrees(Math.atan2(az, ax)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(ay, Math.hypot(ax, az)));
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        float pitchError = pitch - mc.thePlayer.rotationPitch;
        if (Math.abs(yawError) > 2F || Math.abs(pitchError) > 2F) {
            mc.thePlayer.setAngles(
                Math.max(-6F, Math.min(6F, yawError)) / 0.15F,
                -Math.max(-6F, Math.min(6F, pitchError)) / 0.15F);
        }
        double reach = Math.min(4D, mc.playerController.getBlockReachDistance());
        MovingObjectPosition crosshair = mc.objectMouseOver;
        Vec3 look = mc.thePlayer.getLook(1F);
        MovingObjectPosition ray = world
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        boolean visible = mc.renderViewEntity == mc.thePlayer && (target.entity == null
            ? VisibleBlockInteraction.matches(crosshair, ray, eye, target.x, target.y, target.z, reach)
            : crosshair != null && crosshair.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && crosshair.entityHit == candidate
                && crosshair.hitVec != null
                && eye.squareDistanceTo(crosshair.hitVec) <= reach * reach);
        if (!visible || Math.abs(yawError) > 2F || Math.abs(pitchError) > 2F) {
            if (++aimTicks <= 20) {
                attempted[bestIndex] = false;
                return Result.WORKING;
            }
            aimIndex = -1;
            return !visible && approach(target, pos) ? Result.WORKING : null;
        }
        aimIndex = -1;
        oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = empty;
        mc.playerController.updateController();
        boolean sent = sendTargetInteraction(mc, target, candidate, crosshair);
        if (!sent) {
            restoreSlot(mc);
            return null;
        }
        opening = target;
        inventory = candidate;
        wait = 0;
        return Result.WORKING;
    }

    private boolean sendTargetInteraction(Minecraft mc, StorageTarget target, IInventory candidate,
        MovingObjectPosition crosshair) {
        if (target.entity == null) {
            boolean sent = mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                target.x,
                target.y,
                target.z,
                crosshair.sideHit,
                crosshair.hitVec);
            mc.thePlayer.swingItem();
            return sent;
        }
        mc.playerController.interactWithEntitySendPacket(mc.thePlayer, (Entity) candidate);
        return true;
    }

    private void rememberRejectedTypes(String target, List<ItemStack> rejected) {
        List<ItemStack> excluded = rejectedTypesByTarget.get(target);
        if (excluded == null) {
            excluded = new ArrayList<ItemStack>();
            rejectedTypesByTarget.put(target, excluded);
        }
        for (ItemStack stack : rejected) {
            if (VerifiedStorageTransfer.containsItemType(excluded, stack)) continue;
            ItemStack type = stack.copy();
            type.stackSize = 1;
            excluded.add(type);
        }
    }

    private boolean rejectedCurrentBatch(String target, Minecraft mc) {
        List<ItemStack> excluded = rejectedTypesByTarget.get(target);
        if (excluded == null || excluded.isEmpty()) return false;
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        boolean hasMovableItems = false;
        for (int index = 9; index < 36; index++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(index);
            if (!VerifiedStorageTransfer.movable(index, stack, reserve, ledger, baseline)) continue;
            hasMovableItems = true;
            if (!VerifiedStorageTransfer.containsItemType(excluded, stack)) return false;
        }
        return hasMovableItems;
    }

    public void cancel(Minecraft mc) {
        if (transfer != null) transfer.cancel();
        navigation.stop();
        if (mc != null && mc.thePlayer != null) restoreSlot(mc);
    }

    public int moved() {
        return moved;
    }

    public static boolean vanillaChestView(Minecraft mc, StorageTarget target, IInventory marked, IInventory opened) {
        if (!(opened instanceof net.minecraft.inventory.InventoryBasic) || target == null
            || mc == null
            || mc.theWorld == null) return false;
        if (marked instanceof net.minecraft.entity.item.EntityMinecartChest)
            return target.entity != null && opened.getSizeInventory() == marked.getSizeInventory()
                && java.util.Objects.equals(opened.getInventoryName(), marked.getInventoryName());
        if (!(marked instanceof net.minecraft.tileentity.TileEntityChest) || target.entity != null) return false;
        if (vanillaChestSizes(marked, opened, false))
            return java.util.Objects.equals(opened.getInventoryName(), marked.getInventoryName());
        if (!vanillaChestSizes(marked, opened, true)) return false;
        int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] offset : offsets) {
            int x = target.x + offset[0], z = target.z + offset[1];
            if (mc.theWorld.blockExists(x, target.y, z)
                && mc.theWorld.getBlock(x, target.y, z) == mc.theWorld.getBlock(target.x, target.y, target.z)
                && mc.theWorld.getTileEntity(x, target.y, z) instanceof net.minecraft.tileentity.TileEntityChest) {
                IInventory neighbor = (IInventory) mc.theWorld.getTileEntity(x, target.y, z);
                String name = opened.getInventoryName();
                if ((marked.hasCustomInventoryName() && java.util.Objects.equals(name, marked.getInventoryName()))
                    || (neighbor.hasCustomInventoryName()
                        && java.util.Objects.equals(name, neighbor.getInventoryName()))
                    || (!marked.hasCustomInventoryName() && !neighbor.hasCustomInventoryName()
                        && "container.chestDouble".equals(name)))
                    return true;
            }
        }
        return false;
    }

    public static boolean vanillaChestSizes(IInventory marked, IInventory opened, boolean adjacent) {
        return marked instanceof net.minecraft.tileentity.TileEntityChest
            && opened instanceof net.minecraft.inventory.InventoryBasic
            && (opened.getSizeInventory() == marked.getSizeInventory()
                || (adjacent && opened.getSizeInventory() == marked.getSizeInventory() * 2));
    }

    private boolean approach(StorageTarget target, double[] pos) {
        if (lastApproachIndex != currentTargetIndex) {
            lastApproachIndex = currentTargetIndex;
            approachRetries = 0;
        }
        if (approachRetries > 2) return false;
        BaritoneNavigationBridge.Start started = navigation
            .startAdjacentWithoutBreaking((int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2]));
        if (started != BaritoneNavigationBridge.Start.STARTED) return false;
        approach = target;
        approachTicks = 0;
        return true;
    }

    private void restoreSlot(Minecraft mc) {
        if (oldSlot >= 0) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            if (mc.playerController != null) mc.playerController.updateController();
            oldSlot = -1;
        }
    }
}
