package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import org.junit.Test;

public class StorageTransferPolicyTest {

    @Test
    public void confirmsOnlyMatchingObservedTransfers() {
        assertTrue(StorageTransferPolicy.confirmed(32, 12, 4, 24));
        assertFalse(StorageTransferPolicy.confirmed(32, 0, 4, 4));
        assertFalse(StorageTransferPolicy.confirmed(32, 12, 4, 23));
        assertFalse(StorageTransferPolicy.confirmed(32, 32, 4, 4));
        assertFalse(StorageTransferPolicy.confirmed(-1, 0, 0, 1));
    }
}
