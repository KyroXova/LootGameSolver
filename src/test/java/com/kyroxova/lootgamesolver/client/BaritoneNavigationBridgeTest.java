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
        boolean paused;

        public Object getGoal() {
            return current;
        }

        public Object mostRecentGoal() {
            return recent;
        }

        public void setPaused(boolean paused) {
            this.paused = paused;
        }
    }

    public static class Pathing {

        boolean cancelled;

        public void cancelEverything() {
            cancelled = true;
        }
    }

    @Test
    public void stoppingAnOldRequestDoesNotCancelTheNewOwner() throws Exception {
        Goal oldGoal = new Goal();
        Process process = new Process();
        process.current = new Goal();
        Pathing pathing = new Pathing();
        BaritoneNavigationBridge bridge = new BaritoneNavigationBridge();
        bridge.process = process;
        bridge.processApi = Process.class;
        bridge.goal = oldGoal;
        bridge.pathing = pathing;
        bridge.pathingApi = Pathing.class;
        bridge.stop();
        assertFalse(pathing.cancelled);
        assertTrue(process.current != oldGoal);
    }

    @Test
    public void pauseOnlyChangesTheOwnedGoal() throws Exception {
        Goal goal = new Goal();
        Process process = new Process();
        process.current = goal;
        BaritoneNavigationBridge bridge = new BaritoneNavigationBridge();
        bridge.process = process;
        bridge.processApi = Process.class;
        bridge.goal = goal;
        bridge.setPaused(true);
        assertTrue(process.paused);
        bridge.setPaused(false);
        assertFalse(process.paused);
        process.current = new Goal();
        bridge.setPaused(true);
        assertFalse(process.paused);
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
