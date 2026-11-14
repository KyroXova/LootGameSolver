package com.kyroxova.lootgamesolver.client;

import java.lang.reflect.Method;
import java.util.Optional;

/** Owns one custom goal without cancelling a goal subsequently set by another caller. */
final class BaritoneNavigationBridge {

    enum Start {
        STARTED,
        ABSENT,
        BUSY,
        FAILED
    }

    enum State {
        RUNNING,
        STOPPED,
        LOST
    }

    private Object process;
    private Object goal;
    private Class<?> processApi;
    private Class<?> goalApi;

    Start start(int x, int y, int z) {
        Class<?> api;
        try {
            api = Class.forName("baritone.api.BaritoneAPI");
        } catch (ClassNotFoundException absent) {
            return Start.ABSENT;
        } catch (LinkageError failure) {
            return Start.FAILED;
        }
        try {
            Object provider = api.getMethod("getProvider")
                .invoke(null);
            Class<?> providerApi = Class.forName("baritone.api.IBaritoneProvider");
            Object baritone = providerApi.getMethod("getPrimaryBaritone")
                .invoke(provider);
            Class<?> baritoneApi = Class.forName("baritone.api.IBaritone");
            Object manager = baritoneApi.getMethod("getPathingControlManager")
                .invoke(baritone);
            Class<?> managerApi = Class.forName("baritone.api.pathing.calc.IPathingControlManager");
            Optional<?> owner = (Optional<?>) managerApi.getMethod("mostRecentInControl")
                .invoke(manager);
            Object candidate = baritoneApi.getMethod("getCustomGoalProcess")
                .invoke(baritone);
            Class<?> baseProcessApi = Class.forName("baritone.api.process.IBaritoneProcess");
            if (owner.isPresent() || (Boolean) baseProcessApi.getMethod("isActive")
                .invoke(candidate)) return Start.BUSY;
            Class<?> customApi = Class.forName("baritone.api.process.ICustomGoalProcess");
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Object next = Class.forName("baritone.api.pathing.goals.GoalBlock")
                .getConstructor(int.class, int.class, int.class)
                .newInstance(x, y, z);
            customApi.getMethod("setGoalAndPath", goalType)
                .invoke(candidate, next);
            process = candidate;
            goal = next;
            processApi = customApi;
            goalApi = goalType;
            return Start.STARTED;
        } catch (ReflectiveOperationException | LinkageError failure) {
            return Start.FAILED;
        }
    }

    State state() {
        if (process == null) return State.STOPPED;
        try {
            return processApi.getMethod("getGoal")
                .invoke(process) == goal ? State.RUNNING : State.LOST;
        } catch (ReflectiveOperationException | LinkageError failure) {
            return State.LOST;
        }
    }

    void stop() {
        if (process == null) return;
        try {
            if (processApi.getMethod("getGoal")
                .invoke(process) == goal) {
                Method setGoal = processApi.getMethod("setGoal", goalApi);
                setGoal.invoke(process, new Object[] { null });
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {} finally {
            process = null;
            goal = null;
        }
    }
}
