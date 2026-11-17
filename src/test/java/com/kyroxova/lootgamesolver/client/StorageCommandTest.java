package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StorageCommandTest {

    @Test
    public void warpNamesCannotInjectCommands() {
        assertTrue(StorageCommand.validWarpName("storage_1"));
        assertFalse(StorageCommand.validWarpName("storage /home"));
        assertFalse(StorageCommand.validWarpName(""));
        assertFalse(StorageCommand.validWarpName(null));
        assertFalse(StorageCommand.validWarpName("RETURNLOOTING"));
    }
}
