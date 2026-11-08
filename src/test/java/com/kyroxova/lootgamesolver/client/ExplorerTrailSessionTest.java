package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExplorerTrailSessionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void persistsAndRemovesRouteForTheSelectedStructure() throws Exception {
        File file = new File(folder.getRoot(), "trails.json");
        ExplorerTrailSession session = new ExplorerTrailSession(file);
        session.worldId = "test";
        session.dimension = 0;
        session.target = new int[] { 12, 45, 67 };
        session.points.record(1, 64, 2);
        session.points.record(2, 64, 2);
        session.dirty = true;
        assertTrue(session.persist(true, 1));
        assertEquals(
            2,
            new AccessTrailStore(file).load("test", 0, 12, 45, 67)
                .size());
        assertTrue(session.removePersisted());
        assertTrue(
            new AccessTrailStore(file).load("test", 0, 12, 45, 67)
                .isEmpty());
    }
}
