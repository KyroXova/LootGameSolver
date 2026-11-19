package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

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

    @Test
    public void completesSubcommandsAndHelpTopicsWithoutSuggestingWarpNames() {
        StorageCommand command = new StorageCommand();
        List<String> set = command.addTabCompletionOptions(null, new String[] { "se" });
        assertTrue(set.contains("setHome"));
        assertTrue(set.contains("setWarp"));
        assertTrue(set.contains("setChest"));
        assertTrue(set.contains("setStorage"));
        assertTrue(
            command.addTabCompletionOptions(null, new String[] { "caveMode", "" })
                .contains("on"));
        assertTrue(
            command.addTabCompletionOptions(null, new String[] { "caveMode", "" })
                .contains("off"));
        assertEquals(
            1,
            command.addTabCompletionOptions(null, new String[] { "help", "res" })
                .size());
        assertTrue(
            command.addTabCompletionOptions(null, new String[] { "setWarp", "" })
                .isEmpty());
        assertTrue(
            command.addTabCompletionOptions(null, new String[0])
                .isEmpty());
    }

    @Test
    public void describesEachCommandAndBothSetStorageForms() {
        StorageCommand command = new StorageCommand();
        for (String name : command.addTabCompletionOptions(null, new String[] { "" })) {
            assertTrue(
                name,
                StorageCommand.commandHelp(name)
                    .startsWith("/lgs "));
        }
        assertTrue(
            StorageCommand.commandHelp("SETSTORAGE")
                .contains("With no name"));
        assertTrue(
            StorageCommand.commandHelp("setStorage")
                .contains("with a name"));
        assertNull(StorageCommand.commandHelp("unknown"));
    }
}
