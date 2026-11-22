package com.kyroxova.lootgamesolver.client.storage;

import net.minecraft.client.Minecraft;

/** Alternates marked storage and bag unloading until all withdrawn contents are stored. */
public final class StorageDepositCoordinator {

    public enum Result {
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
    private final java.util.Map<String, java.util.List<net.minecraft.item.ItemStack>> rejectedTypesByTarget = new java.util.HashMap<String, java.util.List<net.minecraft.item.ItemStack>>();
    private MarkedStorageDeposit deposit;
    private Phase phase = Phase.DEPOSIT;
    private boolean needsSpace;

    public StorageDepositCoordinator(String[] targets, InventoryBaseline baseline) {
        this.targets = targets.clone();
        this.baseline = baseline;
        bags = new BackpackStorageUnload(baseline, ledger);
    }

    public Result tick(Minecraft mc) {
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
        if (deposit == null) deposit = new MarkedStorageDeposit(mc, targets, ledger, baseline, rejectedTypesByTarget);
        MarkedStorageDeposit.Result result = deposit.tick(mc);
        if (result == MarkedStorageDeposit.Result.WORKING) return Result.WORKING;
        if (shouldResumeUnloading(result, phase == Phase.FINAL_DEPOSIT, deposit.moved())) {
            deposit = null;
            needsSpace = false;
            phase = Phase.UNLOAD;
            return Result.WORKING;
        }
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

    static boolean shouldResumeUnloading(MarkedStorageDeposit.Result result, boolean finalDeposit, int moved) {
        return result == MarkedStorageDeposit.Result.FULL_OR_UNAVAILABLE && !finalDeposit && moved > 0;
    }

    private Result fail(Minecraft mc) {
        if (deposit != null) deposit.cancel(mc);
        bags.cancel(mc);
        phase = Phase.FAILED;
        return Result.FAILED;
    }

    public void cancel(Minecraft mc) {
        fail(mc);
    }
}
