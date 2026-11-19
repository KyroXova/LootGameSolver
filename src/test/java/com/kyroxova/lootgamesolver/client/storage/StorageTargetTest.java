package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import org.junit.Test;

public class StorageTargetTest {

    @Test
    public void parsesOnlyValidInventoryTargets() {
        StorageTarget block = StorageTarget.parse("block:-1:10:64:-20");
        assertNotNull(block);
        assertEquals(-1, block.dimension);
        assertEquals(-20, block.z);
        assertNull(StorageTarget.parse("block:0:1:999:3"));
        assertNotNull(StorageTarget.parse("entity:0:00000000-0000-0000-0000-000000000001"));
        assertNull(StorageTarget.parse("entity:0:not-a-uuid"));
    }
}
