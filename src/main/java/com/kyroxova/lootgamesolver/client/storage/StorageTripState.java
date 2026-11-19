package com.kyroxova.lootgamesolver.client.storage;

/** Orders a storage trip; side effects and confirmations are handled by the caller. */
public final class StorageTripState {

    public enum Phase {
        IDLE,
        WAIT_RETURN_WARP,
        WAIT_STORAGE,
        DEPOSITING,
        WAIT_RETURN,
        READY_TO_RESUME,
        FAILED
    }

    private Phase phase = Phase.IDLE;

    public Phase phase() {
        return phase;
    }

    public boolean begin(boolean configured, boolean checkpointSaved) {
        if (phase != Phase.IDLE || !configured || !checkpointSaved) return false;
        phase = Phase.WAIT_RETURN_WARP;
        return true;
    }

    public boolean resumeAtStorage(boolean configured, boolean checkpointSaved) {
        if (phase != Phase.IDLE || !configured || !checkpointSaved) return false;
        phase = Phase.DEPOSITING;
        return true;
    }

    public void returnWarpResult(boolean confirmed) {
        if (phase == Phase.WAIT_RETURN_WARP) phase = confirmed ? Phase.WAIT_STORAGE : Phase.FAILED;
    }

    public void storageArrival(boolean verified) {
        if (phase == Phase.WAIT_STORAGE) phase = verified ? Phase.DEPOSITING : Phase.FAILED;
    }

    public void depositResult(boolean confirmed, boolean bagsEmpty, boolean itemsStored) {
        if (phase == Phase.DEPOSITING) phase = confirmed && bagsEmpty && itemsStored ? Phase.WAIT_RETURN : Phase.FAILED;
    }

    public void returnAfterDepositFailure() {
        if (phase == Phase.DEPOSITING) phase = Phase.WAIT_RETURN;
    }

    public void returnArrival(boolean verified) {
        if (phase == Phase.WAIT_RETURN) phase = verified ? Phase.READY_TO_RESUME : Phase.FAILED;
    }

    public void fail() {
        if (phase != Phase.IDLE) phase = Phase.FAILED;
    }

    public boolean reset() {
        if (phase != Phase.FAILED && phase != Phase.READY_TO_RESUME) return false;
        phase = Phase.IDLE;
        return true;
    }
}
