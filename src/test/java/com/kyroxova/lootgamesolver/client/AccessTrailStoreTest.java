package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccessTrailStoreTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void entranceRouteSurvivesStoreReloadAndIsScopedToWorldAndStructure() throws Exception {
        File path = new File(temporary.getRoot(), "access-trails.json");
        AccessTrailStore store = new AccessTrailStore(path);
        List<int[]> route = Arrays.asList(new int[] { 4, 70, 9 }, new int[] { 5, 70, 9 }, new int[] { 6, 69, 9 });
        store.save("save:world", 0, 20, 30, 40, route);

        AccessTrailStore reloaded = new AccessTrailStore(path);
        List<int[]> loaded = reloaded.load("save:world", 0, 20, 30, 40);
        assertEquals(3, loaded.size());
        assertArrayEquals(route.get(0), loaded.get(0));
        assertTrue(
            reloaded.load("save:other", 0, 20, 30, 40)
                .isEmpty());
        assertTrue(
            reloaded.load("save:world", 0, 21, 30, 40)
                .isEmpty());

        reloaded.remove("save:world", 0, 20, 30, 40);
        assertTrue(
            new AccessTrailStore(path).load("save:world", 0, 20, 30, 40)
                .isEmpty());
    }
}
