package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StorageDepositCoordinatorTest {

    @Test
    public void partialDepositResumesBackpackUnloadingBeforeFinalDeposit() {
        assertTrue(
            StorageDepositCoordinator.shouldResumeUnloading(MarkedStorageDeposit.Result.FULL_OR_UNAVAILABLE, false, 1));
    }

    @Test
    public void noProgressAndFinalDepositDoNotResumeUnloading() {
        assertFalse(
            StorageDepositCoordinator.shouldResumeUnloading(MarkedStorageDeposit.Result.FULL_OR_UNAVAILABLE, false, 0));
        assertFalse(
            StorageDepositCoordinator.shouldResumeUnloading(MarkedStorageDeposit.Result.FULL_OR_UNAVAILABLE, true, 5));
        assertFalse(StorageDepositCoordinator.shouldResumeUnloading(MarkedStorageDeposit.Result.FAILED, false, 5));
    }
}
