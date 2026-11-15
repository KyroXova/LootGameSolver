package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BaritoneNavigationBridgeTest {

    public static class Goal {

        public boolean isInGoal(int x, int y, int z) {
            return x == 4 && y == 8 && z == 12;
        }
    }

    public static class Process {

        Object current;
        Object recent;

        public Object getGoal() {
            return current;
        }

        public Object mostRecentGoal() {
            return recent;
        }
    }

    @Test
    public void completionRequiresOwnedGoalAndActualArrival() throws Exception {
        Goal goal = new Goal();
        Process process = new Process();
        process.recent = goal;
        assertFalse(BaritoneNavigationBridge.completedAt(process, Process.class, goal, Goal.class, 3, 8, 12));
        assertTrue(BaritoneNavigationBridge.completedAt(process, Process.class, goal, Goal.class, 4, 8, 12));
        process.current = goal;
        assertFalse(BaritoneNavigationBridge.completedAt(process, Process.class, goal, Goal.class, 4, 8, 12));
        process.current = null;
        process.recent = new Goal();
        assertFalse(BaritoneNavigationBridge.completedAt(process, Process.class, goal, Goal.class, 4, 8, 12));
    }
}
