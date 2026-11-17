package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;

/** Alternates marked storage and bag unloading until all withdrawn contents are stored. */
final class StorageDepositCoordinator {

    enum Result {
        WORKING,
        COMPLETE,
        FAILED
    }

    private enum Phase {
        DEPOSIT,
        UNLOAD,
        FINAL_DEPOSIT,
        COMPLETE,
        FAILED
    }

    private final String[] targets;
    private final InventoryBaseline baseline;
    private final StorageLootLedger ledger = new StorageLootLedger();
    private final BackpackStorageUnload bags;
    private MarkedStorageDeposit deposit;
    private Phase phase = Phase.DEPOSIT;
    private boolean needsSpace;

    StorageDepositCoordinator(String[] targets, InventoryBaseline baseline) {
        this.targets = targets.clone();
        this.baseline = baseline;
        bags = new BackpackStorageUnload(baseline, ledger);
    }

    Result tick(Minecraft mc) {
        if (phase == Phase.COMPLETE) return Result.COMPLETE;
        if (phase == Phase.FAILED) return Result.FAILED;
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return fail(mc);
        if (phase == Phase.UNLOAD) {
            BackpackStorageUnload.Result result = bags.tick(mc);
            if (result == BackpackStorageUnload.Result.FAILED) return fail(mc);
            if (result == BackpackStorageUnload.Result.NEED_DEPOSIT) {
                needsSpace = true;
                phase = Phase.DEPOSIT;
            } else if (result == BackpackStorageUnload.Result.DONE) phase = Phase.FINAL_DEPOSIT;
            return Result.WORKING;
        }
        if (deposit == null && !VerifiedStorageTransfer.hasMovable(mc, ledger, baseline)) {
            if (needsSpace || (phase == Phase.FINAL_DEPOSIT && !ledger.empty())) return fail(mc);
            if (phase == Phase.FINAL_DEPOSIT) {
                phase = Phase.COMPLETE;
                return Result.COMPLETE;
            }
            phase = Phase.UNLOAD;
            return Result.WORKING;
        }
        if (deposit == null) deposit = new MarkedStorageDeposit(mc, targets, ledger, baseline);
        MarkedStorageDeposit.Result result = deposit.tick(mc);
        if (result == MarkedStorageDeposit.Result.WORKING) return Result.WORKING;
        if (result != MarkedStorageDeposit.Result.DONE || (needsSpace && deposit.moved() == 0)) return fail(mc);
        deposit = null;
        needsSpace = false;
        if (phase == Phase.FINAL_DEPOSIT) {
            if (!ledger.empty()) return fail(mc);
            phase = Phase.COMPLETE;
            return Result.COMPLETE;
        }
        phase = Phase.UNLOAD;
        return Result.WORKING;
    }

    private Result fail(Minecraft mc) {
        if (deposit != null) deposit.cancel(mc);
        bags.cancel(mc);
        phase = Phase.FAILED;
        return Result.FAILED;
    }

    void cancel(Minecraft mc) {
        fail(mc);
    }
}
