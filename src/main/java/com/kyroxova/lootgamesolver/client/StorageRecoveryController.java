package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;

/** Coordinates one configured storage trip around a saved reward checkpoint. */
final class StorageRecoveryController {

    enum Result {
        WORKING,
        RETURNED,
        FAILED
    }

    private final StorageTripRunner trip = new StorageTripRunner();
    private PendingLootStore.Checkpoint checkpoint;
    private InventoryBaseline baseline;
    private StorageDepositCoordinator deposit;
    private BogoDropOffBridge.Session bogo;
    private boolean dropOffChecked;
    private int dropOffWait;
    private int dropOffSettle;

    boolean start(Minecraft mc, PendingLootStore.Checkpoint saved, InventoryBaseline baseline) {
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
        if (!trip.start(mc, true)) return false;
        checkpoint = saved;
        this.baseline = baseline;
        return true;
    }

    void onChat(IChatComponent message) {
        if (message == null) return;
        String key = message instanceof ChatComponentTranslation ? ((ChatComponentTranslation) message).getKey() : null;
        String expected = StatCollector
            .translateToLocalFormatted("serverutilities.lang.warps.set", StorageWarpFeedback.RETURN_WARP);
        trip.onChat(key, message.getUnformattedText(), expected);
        for (IChatComponent sibling : message.getSiblings()) onChat(sibling);
    }

    Result tick(Minecraft mc) {
        trip.tick(mc);
        StorageTripState.Phase phase = trip.phase();
        if (phase == StorageTripState.Phase.FAILED) {
            if (bogo != null) bogo.close();
            if (deposit != null) deposit.cancel(mc);
            return Result.FAILED;
        }
        if (phase == StorageTripState.Phase.READY_TO_RESUME) return Result.RETURNED;
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
                        trip.fail();
                        return Result.FAILED;
                    }
                } else {
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7e[LootGameSolver] Nearby drop-off skipped to protect carried gear; using marked storage."));
                }
            }
        }
        if (bogo != null) {
            int response = bogo.response();
            if (response < 0 || ++dropOffWait > 600) {
                bogo.close();
                bogo = null;
                trip.fail();
                return Result.FAILED;
            }
            if (response == 0) return Result.WORKING;
            if (++dropOffSettle < 5) return Result.WORKING;
            bogo.close();
            bogo = null;
        }
        if (deposit == null) deposit = new StorageDepositCoordinator(LootGameSolverConfig.storageTargets, baseline);
        StorageDepositCoordinator.Result result = deposit.tick(mc);
        if (result == StorageDepositCoordinator.Result.FAILED) {
            trip.fail();
            return Result.FAILED;
        }
        if (result == StorageDepositCoordinator.Result.COMPLETE) trip.depositFinished(mc, true, true, true);
        else trip.depositProgress();
        return Result.WORKING;
    }

    PendingLootStore.Checkpoint checkpoint() {
        return checkpoint;
    }

    void cancel() {
        trip.fail();
    }

    String status() {
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
