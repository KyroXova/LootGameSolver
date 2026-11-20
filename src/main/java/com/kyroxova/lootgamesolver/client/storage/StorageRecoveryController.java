package com.kyroxova.lootgamesolver.client.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.kyroxova.lootgamesolver.client.loot.PendingLootStore;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;

/** Coordinates one configured storage trip around a saved reward checkpoint. */
public final class StorageRecoveryController {

    public enum Result {
        WORKING,
        RETURNED,
        RETURNED_UNSTORED,
        FAILED
    }

    private final StorageTripRunner trip = new StorageTripRunner();
    private final ReturnWarpPositioner positioner = new ReturnWarpPositioner();

    public void pause(boolean paused) {
        positioner.pause(paused);
    }

    private boolean positioning, cancelled;
    private String failureReason;
    private PendingLootStore.Checkpoint checkpoint;
    private InventoryBaseline baseline;
    private StorageDepositCoordinator deposit;
    private BogoDropOffBridge.Session bogo;
    private boolean dropOffChecked;
    private boolean returnWithoutDeposit;
    private int dropOffWait;
    private int dropOffSettle;

    public boolean start(Minecraft mc, PendingLootStore.Checkpoint saved, InventoryBaseline baseline) {
        if (mc == null || mc.theWorld == null
            || mc.thePlayer == null
            || saved == null
            || !Explorer.worldIdentity(mc)
                .equals(saved.world)
            || mc.theWorld.provider.dimensionId != saved.dimension
            || saved.center == null
            || saved.center.length != 3
            || mc.thePlayer.getDistanceSq(saved.center[0] + 0.5D, saved.center[1] + 0.5D, saved.center[2] + 0.5D)
                > 256D)
            return false;
        if (!StorageTripPolicy.destinationConfigured(
            LootGameSolverConfig.storageEnabled,
            LootGameSolverConfig.storageWorld,
            Explorer.worldIdentity(mc),
            LootGameSolverConfig.storageDestination,
            LootGameSolverConfig.storageTargets) || mc.currentScreen != null) return false;
        checkpoint = saved;
        this.baseline = baseline;
        positioning = positioner.start(mc, saved.center);
        if (!positioning) failureReason = "No clear supported 3x3 area was found for the return warp.";
        return true;
    }

    public boolean resumeAtStorage(Minecraft mc, PendingLootStore.Checkpoint saved, InventoryBaseline baseline) {
        if (!trip.resumeAtStorage(mc, saved)) return false;
        checkpoint = saved;
        this.baseline = baseline;
        return true;
    }

    public void onChat(IChatComponent message) {
        if (message == null) return;
        String key = message instanceof ChatComponentTranslation ? ((ChatComponentTranslation) message).getKey() : null;
        String expected = StatCollector
            .translateToLocalFormatted("serverutilities.lang.warps.set", StorageWarpFeedback.RETURN_WARP);
        trip.onChat(key, message.getUnformattedText(), expected);
        for (IChatComponent sibling : message.getSiblings()) onChat(sibling);
    }

    public Result tick(Minecraft mc) {
        if (cancelled) return Result.FAILED;
        if (failureReason != null) return Result.FAILED;
        if (positioning) {
            if (mc == null || mc.theWorld == null
                || mc.thePlayer == null
                || !checkpoint.world.equals(Explorer.worldIdentity(mc))
                || checkpoint.dimension != mc.theWorld.provider.dimensionId) {
                positioner.stop();
                return Result.FAILED;
            }
            ReturnWarpPositioner.Result result = positioner.tick(mc);
            if (result == ReturnWarpPositioner.Result.FAILED) {
                positioner.stop();
                failureReason = "Could not reach a clear 3x3 area for the return warp.";
                return Result.FAILED;
            }
            if (result == ReturnWarpPositioner.Result.WORKING) return Result.WORKING;
            positioning = false;
            if (!trip.start(mc, true)) return Result.FAILED;
        }
        trip.tick(mc);
        StorageTripState.Phase phase = trip.phase();
        if (phase == StorageTripState.Phase.FAILED) {
            if (bogo != null) bogo.close();
            if (deposit != null) deposit.cancel(mc);
            return Result.FAILED;
        }
        if (phase == StorageTripState.Phase.READY_TO_RESUME)
            return returnWithoutDeposit ? Result.RETURNED_UNSTORED : Result.RETURNED;
        if (phase != StorageTripState.Phase.DEPOSITING) return Result.WORKING;
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return Result.WORKING;
        if (!dropOffChecked && mc.currentScreen != null) return Result.WORKING;
        if (!dropOffChecked) {
            dropOffChecked = true;
            if (BogoDropOffBridge.available()) {
                if (BogoDropOffBridge.safeToRequest(mc)) {
                    bogo = new BogoDropOffBridge.Session();
                    if (!bogo.start()) {
                        bogo = null;
                    }
                }
            }
        }
        if (bogo != null) {
            int response = bogo.response();
            if (response < 0 || ++dropOffWait > 600) {
                bogo.close();
                bogo = null;
            } else if (response == 0) {
                return Result.WORKING;
            } else if (++dropOffSettle < 5) {
                return Result.WORKING;
            } else {
                bogo.close();
                bogo = null;
            }
        }
        if (deposit == null) deposit = new StorageDepositCoordinator(LootGameSolverConfig.storageTargets, baseline);
        StorageDepositCoordinator.Result result = deposit.tick(mc);
        if (result == StorageDepositCoordinator.Result.FAILED) {
            if (returnAfterDepositFailure(mc)) return Result.WORKING;
            trip.fail();
            return Result.FAILED;
        }
        if (result == StorageDepositCoordinator.Result.COMPLETE) trip.depositFinished(mc, true, true, true);
        else trip.depositProgress();
        return Result.WORKING;
    }

    private boolean returnAfterDepositFailure(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.inventory.getItemStack() != null) return false;
        if (deposit != null) deposit.cancel(mc);
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) mc.thePlayer.closeScreen();
        if (mc.currentScreen != null || !trip.returnAfterDepositFailure(mc)) return false;
        returnWithoutDeposit = true;
        return true;
    }

    public PendingLootStore.Checkpoint checkpoint() {
        return checkpoint;
    }

    public String failureReason() {
        return failureReason;
    }

    public void cancel() {
        positioner.stop();
        positioning = false;
        cancelled = true;
        trip.fail();
    }

    public String status() {
        if (positioning) return "Finding clear return position";
        switch (trip.phase()) {
            case WAIT_RETURN_WARP:
                return "Setting return warp";
            case WAIT_STORAGE:
                return "Traveling to storage";
            case DEPOSITING:
                return "Depositing items";
            case WAIT_RETURN:
                return "Returning to rewards";
            default:
                return "Storage recovery";
        }
    }
}
