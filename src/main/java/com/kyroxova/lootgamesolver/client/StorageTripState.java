package com.kyroxova.lootgamesolver.client;

/** Orders a storage trip; side effects and confirmations are handled by the caller. */
final class StorageTripState {

    enum Phase {
        IDLE,
        WAIT_RETURN_WARP,
        WAIT_STORAGE,
        DEPOSITING,
        WAIT_RETURN,
        READY_TO_RESUME,
        FAILED
    }

    private Phase phase = Phase.IDLE;

    Phase phase() {
        return phase;
    }

    boolean begin(boolean configured, boolean checkpointSaved) {
        if (phase != Phase.IDLE || !configured || !checkpointSaved) return false;
        phase = Phase.WAIT_RETURN_WARP;
        return true;
    }

    boolean resumeAtStorage(boolean configured, boolean checkpointSaved) {
        if (phase != Phase.IDLE || !configured || !checkpointSaved) return false;
        phase = Phase.DEPOSITING;
        return true;
    }

    void returnWarpResult(boolean confirmed) {
        if (phase == Phase.WAIT_RETURN_WARP) phase = confirmed ? Phase.WAIT_STORAGE : Phase.FAILED;
    }

    void storageArrival(boolean verified) {
        if (phase == Phase.WAIT_STORAGE) phase = verified ? Phase.DEPOSITING : Phase.FAILED;
    }

    void depositResult(boolean confirmed, boolean bagsEmpty, boolean itemsStored) {
        if (phase == Phase.DEPOSITING) phase = confirmed && bagsEmpty && itemsStored ? Phase.WAIT_RETURN : Phase.FAILED;
    }

    void returnAfterDepositFailure() {
        if (phase == Phase.DEPOSITING) phase = Phase.WAIT_RETURN;
    }

    void returnArrival(boolean verified) {
        if (phase == Phase.WAIT_RETURN) phase = verified ? Phase.READY_TO_RESUME : Phase.FAILED;
    }

    void fail() {
        if (phase != Phase.IDLE) phase = Phase.FAILED;
    }

    boolean reset() {
        if (phase != Phase.FAILED && phase != Phase.READY_TO_RESUME) return false;
        phase = Phase.IDLE;
        return true;
    }
}
