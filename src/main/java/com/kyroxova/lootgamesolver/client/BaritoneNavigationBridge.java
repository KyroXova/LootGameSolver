package com.kyroxova.lootgamesolver.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;

/** Owns one custom goal without cancelling a goal subsequently set by another caller. */
public final class BaritoneNavigationBridge {

    public enum Start {
        STARTED,
        ABSENT,
        BUSY,
        FAILED
    }

    public enum State {
        RUNNING,
        STOPPED,
        LOST
    }

    private Object process;
    private Object goal;
    private Class<?> processApi;
    private Class<?> goalApi;
    private Object pathing;
    private Class<?> pathingApi;
    private Object inputHandler;
    private Class<?> inputHandlerApi;
    private Object clickLeft;
    private Object clickRight;
    private Object clickSprint;

    public Start start(int x, int y, int z) {
        return start(x, y, z, 0, false);
    }

    public Start startWithoutBreaking(int x, int y, int z) {
        return start(x, y, z, 0, true);
    }

    public Start startAdjacent(int x, int y, int z) {
        return start(x, y, z, 1, false);
    }

    public Start startAdjacentWithoutBreaking(int x, int y, int z) {
        return start(x, y, z, 1, true, true);
    }

    public Start startHorizontal(int x, int z) {
        return start(x, 0, z, 2, false);
    }

    private Start start(int x, int y, int z, int kind, boolean forbidBreaking) {
        return start(x, y, z, kind, forbidBreaking, false);
    }

    private Start start(int x, int y, int z, int kind, boolean forbidBreaking, boolean preserveActiveGoal) {
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
            Object candidatePathing = baritoneApi.getMethod("getPathingBehavior")
                .invoke(baritone);
            Object candidateInput = baritoneApi.getMethod("getInputOverrideHandler")
                .invoke(baritone);
            Class<?> candidateInputApi = Class.forName("baritone.api.utils.IInputOverrideHandler");
            Class<?> inputType = Class.forName("baritone.api.utils.input.Input");
            Object candidateClick = inputType.getField("CLICK_LEFT")
                .get(null);
            Object candidateRightClick = inputType.getField("CLICK_RIGHT")
                .get(null);
            Object candidateSprint = null;
            try {
                candidateSprint = inputType.getField("SPRINT")
                    .get(null);
            } catch (ReflectiveOperationException ignored) {}
            Class<?> candidatePathingApi = Class.forName("baritone.api.behavior.IPathingBehavior");
            Object manager = baritoneApi.getMethod("getPathingControlManager")
                .invoke(baritone);
            Class<?> managerApi = Class.forName("baritone.api.pathing.calc.IPathingControlManager");
            Optional<?> owner = (Optional<?>) managerApi.getMethod("mostRecentInControl")
                .invoke(manager);
            Object candidate = baritoneApi.getMethod("getCustomGoalProcess")
                .invoke(baritone);
            Class<?> baseProcessApi = Class.forName("baritone.api.process.IBaritoneProcess");
            if (owner.isPresent() && owner.get() != candidate) return Start.BUSY;
            if ((Boolean) baseProcessApi.getMethod("isActive")
                .invoke(candidate)) {
                if (preserveActiveGoal) return Start.BUSY;
                try {
                    candidatePathingApi.getMethod("cancelEverything")
                        .invoke(candidatePathing);
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
            Class<?> customApi = Class.forName("baritone.api.process.ICustomGoalProcess");
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Object next;
            if (kind == 1) {
                Class<?> posType = Class.forName("baritone.api.utils.BetterBlockPos");
                Object pos = posType.getConstructor(int.class, int.class, int.class)
                    .newInstance(x, y, z);
                next = Class.forName("baritone.api.pathing.goals.GoalGetToBlock")
                    .getConstructor(posType)
                    .newInstance(pos);
            } else if (kind == 2) {
                next = Class.forName("baritone.api.pathing.goals.GoalXZ")
                    .getConstructor(int.class, int.class)
                    .newInstance(x, z);
            } else {
                next = Class.forName("baritone.api.pathing.goals.GoalBlock")
                    .getConstructor(int.class, int.class, int.class)
                    .newInstance(x, y, z);
            }
            customApi.getMethod(forbidBreaking ? "setGoalAndPathWithoutBreaking" : "setGoalAndPath", goalType)
                .invoke(candidate, next);
            try {
                candidatePathingApi.getMethod("setNavigationChatOverride", Boolean.class)
                    .invoke(candidatePathing, false);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            try {
                candidatePathingApi
                    .getMethod("setNavigationRenderOverride", Boolean.class, Boolean.class, Boolean.class)
                    .invoke(candidatePathing, false, false, false);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            process = candidate;
            goal = next;
            processApi = customApi;
            goalApi = goalType;
            pathing = candidatePathing;
            pathingApi = candidatePathingApi;
            inputHandler = candidateInput;
            inputHandlerApi = candidateInputApi;
            clickLeft = candidateClick;
            clickRight = candidateRightClick;
            clickSprint = candidateSprint;
            return Start.STARTED;
        } catch (ReflectiveOperationException | LinkageError failure) {
            return Start.FAILED;
        }
    }

    public void renderOverlay(boolean visible) {
        if (pathing == null) return;
        try {
            pathingApi.getMethod("setNavigationRenderOverride", Boolean.class, Boolean.class, Boolean.class)
                .invoke(pathing, false, visible, false);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    public void setPaused(boolean paused) {
        if (process == null || processApi == null) return;
        try {
            if (processApi.getMethod("getGoal")
                .invoke(process) == goal) {
                processApi.getMethod("setPaused", boolean.class)
                    .invoke(process, paused);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    public boolean isPathing() {
        if (pathing == null || pathingApi == null) return false;
        try {
            return (Boolean) pathingApi.getMethod("isPathing")
                .invoke(pathing);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }

    public boolean isBreaking() {
        if (inputHandler == null || clickLeft == null) return false;
        try {
            try {
                Object executor = pathingApi.getMethod("getCurrent")
                    .invoke(pathing);
                if (executor != null && (Boolean) Class.forName("baritone.api.pathing.path.IPathExecutor")
                    .getMethod("isAimingForInteraction")
                    .invoke(executor)) return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            Method forced = inputHandlerApi.getMethod("isInputForcedDown", clickLeft.getClass());
            return (Boolean) forced.invoke(inputHandler, clickLeft)
                || (clickRight != null && (Boolean) forced.invoke(inputHandler, clickRight));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public List<int[]> route() {
        List<int[]> result = new ArrayList<>();
        if (pathing == null) return result;
        try {
            Object executor = pathingApi.getMethod("getCurrent")
                .invoke(pathing);
            if (executor == null) return result;
            Class<?> executorApi = Class.forName("baritone.api.pathing.path.IPathExecutor");
            Object path = executorApi.getMethod("getPath")
                .invoke(executor);
            int index = (Integer) executorApi.getMethod("getPosition")
                .invoke(executor);
            List<?> positions = (List<?>) Class.forName("baritone.api.pathing.calc.IPath")
                .getMethod("positions")
                .invoke(path);
            Class<?> positionType = Class.forName("baritone.api.utils.BetterBlockPos");
            Method x = positionType.getMethod("getX"), y = positionType.getMethod("getY"),
                z = positionType.getMethod("getZ");
            for (int i = Math.max(0, index); i < positions.size() && result.size() < 96; i++) {
                Object pos = positions.get(i);
                result.add(new int[] { (Integer) x.invoke(pos), (Integer) y.invoke(pos), (Integer) z.invoke(pos) });
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            result.clear();
        }
        return result;
    }

    public boolean completed() {
        if (process == null) return false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || goal == null || goalApi == null) return false;
            return completedAt(
                process,
                processApi,
                goal,
                goalApi,
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }

    static boolean completedAt(Object process, Class<?> processApi, Object goal, Class<?> goalApi, int x, int y, int z)
        throws ReflectiveOperationException {
        return processApi.getMethod("getGoal")
            .invoke(process) == null
            && processApi.getMethod("mostRecentGoal")
                .invoke(process) == goal
            && (Boolean) goalApi.getMethod("isInGoal", int.class, int.class, int.class)
                .invoke(goal, x, y, z);
    }

    public State state() {
        if (process == null) return State.STOPPED;
        try {
            return processApi.getMethod("getGoal")
                .invoke(process) == goal ? State.RUNNING : State.LOST;
        } catch (ReflectiveOperationException | LinkageError failure) {
            return State.LOST;
        }
    }

    public void setSprinting(boolean sprint) {
        if (inputHandler != null && inputHandlerApi != null && clickSprint != null) {
            try {
                inputHandlerApi.getMethod("setInputForceState", clickSprint.getClass(), boolean.class)
                    .invoke(inputHandler, clickSprint, sprint);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
    }

    public void stop() {
        if (inputHandler != null && inputHandlerApi != null && clickSprint != null) {
            try {
                inputHandlerApi.getMethod("setInputForceState", clickSprint.getClass(), boolean.class)
                    .invoke(inputHandler, clickSprint, false);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        try {
            if (process != null && processApi != null) {
                if (processApi.getMethod("getGoal")
                    .invoke(process) == goal) {
                    Class.forName("baritone.api.process.IBaritoneProcess")
                        .getMethod("onLostControl")
                        .invoke(process);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        if (pathing != null && pathingApi != null) {
            try {
                pathingApi.getMethod("cancelEverything")
                    .invoke(pathing);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            try {
                pathingApi.getMethod("setNavigationRenderOverride", Boolean.class, Boolean.class, Boolean.class)
                    .invoke(pathing, false, false, false);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
            try {
                pathingApi.getMethod("setNavigationChatOverride", Boolean.class)
                    .invoke(pathing, (Boolean) null);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        process = null;
        goal = null;
        processApi = null;
        goalApi = null;
        pathing = null;
        pathingApi = null;
        inputHandler = null;
        inputHandlerApi = null;
        clickLeft = null;
        clickRight = null;
        clickSprint = null;
    }

}
