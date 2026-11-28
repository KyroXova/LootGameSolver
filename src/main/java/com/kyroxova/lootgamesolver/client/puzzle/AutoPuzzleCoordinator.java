package com.kyroxova.lootgamesolver.client.puzzle;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.ClientHooks;
import com.kyroxova.lootgamesolver.client.loot.RewardChestLooter;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

public final class AutoPuzzleCoordinator {

    public enum State {
        IDLE,
        OPENING,
        POSITIONING,
        POSITIONING_NEXT_STAGE,
        REVEALING,
        REVEALING_NEXT_STAGE,
        SOLVING,
        FAILED
    }

    private State state = State.IDLE;
    private int[] autoPuzzlePosition;
    private int[] floorActivatorPosition;
    private boolean floorActivatorClicked;
    private boolean clearingPositionFoliage;
    private int puzzleWaitTicks;
    private int masterClickAttempts;
    private int centerClickAttempts;
    private boolean minesweeperOpeningClickSent;
    private int activeMinesweeperLevel = -1;
    private int lastMasterClickTick = -40;
    private int lastCenterClickTick = -40;
    private World puzzleWorld;
    private int[] rewardCenter;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public boolean isSolving() {
        return state == State.SOLVING;
    }

    public boolean isActive() {
        return state != State.IDLE && state != State.FAILED;
    }

    public int[] getRewardCenter() {
        return rewardCenter;
    }

    public void setRewardCenter(int[] center) {
        this.rewardCenter = center;
    }

    public int[] getAutoPuzzlePosition() {
        return autoPuzzlePosition;
    }

    public void reset() {
        state = State.IDLE;
        autoPuzzlePosition = null;
        floorActivatorPosition = null;
        floorActivatorClicked = false;
        clearingPositionFoliage = false;
        puzzleWaitTicks = 0;
        masterClickAttempts = 0;
        centerClickAttempts = 0;
        minesweeperOpeningClickSent = false;
        activeMinesweeperLevel = -1;
        lastMasterClickTick = -40;
        lastCenterClickTick = -40;
        puzzleWorld = null;
        rewardCenter = null;
    }

    public void startFromExploration(int[] puzzlePos, World world, PuzzleCompletionGate puzzleCompletion) {
        autoPuzzlePosition = puzzlePos;
        rewardCenter = puzzlePos == null ? null : puzzlePos.clone();
        floorActivatorPosition = null;
        floorActivatorClicked = false;
        state = State.OPENING;
        puzzleWaitTicks = 0;
        masterClickAttempts = 0;
        centerClickAttempts = 0;
        minesweeperOpeningClickSent = false;
        activeMinesweeperLevel = -1;
        lastMasterClickTick = -40;
        lastCenterClickTick = -40;
        puzzleWorld = world;
        puzzleCompletion.begin();
    }

    public void startManual(int[] boardCenter, int[] floor, World world, PuzzleCompletionGate puzzleCompletion) {
        autoPuzzlePosition = boardCenter != null ? boardCenter.clone() : null;
        rewardCenter = boardCenter != null ? boardCenter.clone() : null;
        floorActivatorPosition = floor;
        floorActivatorClicked = false;
        state = State.OPENING;
        puzzleWaitTicks = 0;
        masterClickAttempts = 0;
        centerClickAttempts = 0;
        minesweeperOpeningClickSent = false;
        activeMinesweeperLevel = -1;
        lastMasterClickTick = -40;
        lastCenterClickTick = -40;
        puzzleWorld = world;
        puzzleCompletion.begin();
    }

    public void checkSolvingStage(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration,
        SolverSession session, PuzzleCompletionGate puzzleCompletion, PuzzleTileNavigator tileNavigator) {
        if (state != State.SOLVING || puzzleCompletion.won(mc.theWorld != null)) return;
        Object game = puzzleBridge.nearbyGame(true);
        int level = puzzleBridge.currentMinesweeperLevel(game);
        boolean nextLevel = activeMinesweeperLevel >= 1 && level > activeMinesweeperLevel && level <= 4;
        boolean ready = puzzleBridge.canOpenMinesweeperBoard(game);
        boolean retryReady = ready && (level == activeMinesweeperLevel || puzzleCompletion.stageCompleted());
        if (nextLevel || retryReady) {
            tileNavigator.stop();
            session.cancel(nextLevel ? "Minesweeper stage advanced" : "Minesweeper attempt reset");
            if (level >= 1) activeMinesweeperLevel = level;
            puzzleCompletion.clearStageCompleted();
            int[] center = puzzleBridge.gameCenter(game);
            state = !withinInteractionReach(mc, center) && exploration != null
                && center != null
                && exploration.moveToPuzzleCenter(mc, center) ? State.POSITIONING_NEXT_STAGE
                    : State.REVEALING_NEXT_STAGE;
            puzzleWaitTicks = 0;
            centerClickAttempts = 0;
            minesweeperOpeningClickSent = false;
            lastCenterClickTick = -40;
            if (exploration != null) exploration.setAutomationStatus(
                nextLevel ? "Opening Minesweeper stage " + level : "Reopening Minesweeper attempt");
        }
    }

    private static boolean withinInteractionReach(Minecraft mc, int[] position) {
        if (position == null || mc.thePlayer == null || mc.playerController == null) return false;
        double dx = position[0] + 0.5D - mc.thePlayer.posX;
        double dy = position[1] + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double dz = position[2] + 0.5D - mc.thePlayer.posZ;
        double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance());
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public void tick(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration, SolverSession session,
        PuzzleCompletionGate puzzleCompletion, RewardChestLooter rewardLooter, ClientHooks hooks) {
        if (state == State.IDLE) return;
        if (mc.theWorld == null || mc.thePlayer == null || (puzzleWorld != null && puzzleWorld != mc.theWorld)) {
            reset();
            return;
        }
        if (state == State.OPENING) tickOpening(mc, puzzleBridge, exploration, session, hooks);
        else if (state == State.POSITIONING) tickPositioning(mc, puzzleBridge, exploration);
        else if (state == State.POSITIONING_NEXT_STAGE) tickPositioningNextStage(exploration);
        else if (state == State.REVEALING) tickRevealing(mc, puzzleBridge, exploration, session, hooks);
        else if (state == State.REVEALING_NEXT_STAGE)
            tickRevealingNextStage(mc, puzzleBridge, exploration, session, hooks);
        else if (state == State.SOLVING) tickSolving(mc, exploration, session, puzzleCompletion, rewardLooter, hooks);
    }

    private void tickOpening(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration, SolverSession session,
        ClientHooks hooks) {
        puzzleWaitTicks++;
        if (hooks.isExplorationDriven() && exploration != null
            && autoPuzzlePosition != null
            && !exploration.inTargetRoom(mc, autoPuzzlePosition)) {
            if (exploration.navigating() || exploration.enterTargetRoom(mc, autoPuzzlePosition)) {
                exploration.setAutomationStatus("Entering puzzle room");
                puzzleWaitTicks = 0;
            } else if (puzzleWaitTicks > 200) {
                state = State.FAILED;
                exploration.setAutomationStatus("No safe entrance to puzzle room");
            }
            return;
        }
        int[] activator = puzzleBridge.nearbyFloorActivator();
        if (activator != null && exploration != null) {
            rewardCenter = activator.clone();
            floorActivatorPosition = activator;
            floorActivatorClicked = false;
            if (withinInteractionReach(mc, activator) && puzzleBridge.hasClearView(activator)) {
                state = State.REVEALING;
                puzzleWaitTicks = 0;
                return;
            }
            if (exploration.moveToPuzzleCenter(mc, activator)) {
                state = State.POSITIONING;
                puzzleWaitTicks = 0;
                return;
            }
        }
        Object game = puzzleBridge.gameAtMaster(autoPuzzlePosition);
        if (game == null) game = puzzleBridge.nearbyGame(true);
        if (game != null) {
            tickOpeningWithGame(mc, puzzleBridge, exploration, session, hooks, game);
            return;
        }
        tickOpeningMaster(mc, puzzleBridge, exploration);
    }

    private void tickOpeningWithGame(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration,
        SolverSession session, ClientHooks hooks, Object game) {
        int[] detectedCenter = puzzleBridge.gameCenter(game);
        if (detectedCenter != null) rewardCenter = detectedCenter;
        if (puzzleBridge.detect(true) != null) {
            startPuzzleSolver(mc, puzzleBridge, session, hooks);
            return;
        }
        if (withinInteractionReach(mc, detectedCenter)) {
            state = State.REVEALING;
            puzzleWaitTicks = 0;
            return;
        }
        if (detectedCenter != null && exploration != null && exploration.moveToPuzzleCenter(mc, detectedCenter)) {
            state = State.POSITIONING;
            puzzleWaitTicks = 0;
            return;
        }
        state = State.FAILED;
        if (exploration != null) exploration.setAutomationStatus("Could not reach the board center; explorer held");
    }

    private void tickOpeningMaster(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration) {
        if (autoPuzzlePosition != null && LootGamesBridge
            .visibleMasterFace(mc, autoPuzzlePosition[0], autoPuzzlePosition[1], autoPuzzlePosition[2]) == null) {
            if (exploration != null && !exploration.navigating()) {
                exploration.moveToPuzzleCenter(mc, autoPuzzlePosition);
            }
            if (exploration != null && exploration.navigating()) {
                exploration.setAutomationStatus("Walking to puzzle center");
                puzzleWaitTicks = 0;
                return;
            }
            if (puzzleWaitTicks > 200) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Could not reach puzzle master");
            }
            return;
        }
        boolean masterInteractionSettled = masterClickAttempts > 0 && puzzleWaitTicks - lastMasterClickTick >= 20;
        if (autoPuzzlePosition != null && masterClickAttempts < 6
            && puzzleWaitTicks - lastMasterClickTick >= 20
            && puzzleBridge.activateMaster(autoPuzzlePosition[0], autoPuzzlePosition[1], autoPuzzlePosition[2])) {
            masterClickAttempts++;
            lastMasterClickTick = puzzleWaitTicks;
            if (exploration != null) exploration.setAutomationStatus("Opening LootGames master");
        }
        if (masterInteractionSettled && puzzleWaitTicks <= 200 && exploration != null)
            exploration.setAutomationStatus("Waiting for LootGames to create the puzzle");
        if (puzzleWaitTicks > 200) {
            state = State.FAILED;
            if (exploration != null)
                exploration.setAutomationStatus("Opened puzzle could not be located; explorer held");
        }
    }

    private void tickPositioning(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration) {
        if (exploration != null && exploration.consumePuzzleCenterArrival()) {
            enterRevealingState();
            return;
        }
        if (exploration != null && exploration.navigating()) return;
        int[] center = floorActivatorPosition != null ? floorActivatorPosition
            : rewardCenter != null ? rewardCenter : autoPuzzlePosition;
        if (center != null && puzzleBridge.clearMinesweeperObstruction(center)) {
            if (puzzleBridge.foliageClearingFailed()) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Could not clear foliage");
            } else {
                clearingPositionFoliage = true;
                if (exploration != null) exploration.setAutomationStatus("Clearing foliage");
            }
            return;
        }
        if (clearingPositionFoliage && exploration != null) {
            clearingPositionFoliage = false;
            if (exploration.moveToPuzzleCenter(mc, center)) return;
        }
        if (center != null && mc.thePlayer != null
            && mc.thePlayer.getDistanceSq(center[0] + 0.5D, center[1] + 0.5D, center[2] + 0.5D) <= 9.0D) {
            enterRevealingState();
            return;
        }
        state = State.FAILED;
        if (exploration != null) exploration.setAutomationStatus("Could not reach the board center; explorer held");
    }

    private void enterRevealingState() {
        state = State.REVEALING;
        puzzleWaitTicks = 0;
        centerClickAttempts = 0;
        lastCenterClickTick = -40;
    }

    private void tickPositioningNextStage(Explorer exploration) {
        if ((exploration != null && exploration.consumePuzzleCenterArrival()) || exploration == null
            || !exploration.navigating()) {
            state = State.REVEALING_NEXT_STAGE;
            puzzleWaitTicks = 0;
            lastCenterClickTick = -40;
        }
    }

    private void tickRevealing(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration, SolverSession session,
        ClientHooks hooks) {
        puzzleWaitTicks++;
        DetectedGame revealed = puzzleBridge.detect(true);
        Object game = puzzleBridge.nearbyGame(true);
        Object activeGame = game != null ? game : revealed == null ? null : revealed.game;
        if (floorActivatorPosition != null && !handleFloorActivatorReveal(puzzleBridge, exploration, activeGame)) {
            return;
        }
        if (revealed != null && centerClickAttempts > 0) {
            if (puzzleWaitTicks - lastCenterClickTick >= 20) startPuzzleSolver(mc, puzzleBridge, session, hooks);
            return;
        }
        if (centerClickAttempts < 4 && activeGame != null
            && puzzleWaitTicks - lastCenterClickTick >= (centerClickAttempts == 0 ? 1 : 20)
            && puzzleBridge.activateBoardCenter(activeGame)) {
            centerClickAttempts++;
            lastCenterClickTick = puzzleWaitTicks;
            if (exploration != null) exploration.setAutomationStatus("Revealing board");
        }
        if (puzzleBridge.isClearingFoliage()) {
            puzzleWaitTicks--;
            if (exploration != null) exploration.setAutomationStatus("Clearing foliage");
            return;
        }
        if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
            state = State.FAILED;
            if (exploration != null) exploration.setAutomationStatus("Board reveal failed");
        }
    }

    private boolean handleFloorActivatorReveal(LootGamesBridge puzzleBridge, Explorer exploration, Object activeGame) {
        if (!floorActivatorClicked) {
            if (puzzleBridge.activateFloorActivator(floorActivatorPosition)) {
                floorActivatorClicked = true;
                lastCenterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Opening floor activator");
            }
            if (puzzleBridge.isClearingFoliage()) {
                puzzleWaitTicks--;
                if (exploration != null) exploration.setAutomationStatus("Clearing foliage");
                return false;
            }
            if (puzzleWaitTicks > 200 && !floorActivatorClicked) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Could not click floor activator");
            }
            return false;
        }
        if (activeGame == null) {
            if (puzzleWaitTicks > lastCenterClickTick + 200) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Floor activator did not open puzzle");
            }
            return false;
        }
        floorActivatorPosition = null;
        centerClickAttempts = 0;
        lastCenterClickTick = puzzleWaitTicks - 20;
        return true;
    }

    private void tickRevealingNextStage(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration,
        SolverSession session, ClientHooks hooks) {
        puzzleWaitTicks++;
        Object game = puzzleBridge.nearbyGame(true);
        DetectedGame board = puzzleBridge.detect(true);
        Object activeGame = game != null ? game : board == null ? null : board.game;
        if (centerClickAttempts > 0 && board != null) {
            if (puzzleWaitTicks - lastCenterClickTick >= 20) startPuzzleSolver(mc, puzzleBridge, session, hooks);
            return;
        }
        if (centerClickAttempts < 4 && puzzleBridge.canOpenMinesweeperBoard(activeGame)
            && puzzleWaitTicks - lastCenterClickTick >= (centerClickAttempts == 0 ? 1 : 20)
            && puzzleBridge.activateBoardCenter(activeGame)) {
            centerClickAttempts++;
            lastCenterClickTick = puzzleWaitTicks;
            if (exploration != null) exploration.setAutomationStatus("Revealing Minesweeper board");
        }
        if (puzzleBridge.isClearingFoliage()) {
            puzzleWaitTicks--;
            if (exploration != null) exploration.setAutomationStatus("Clearing foliage");
            return;
        }
        if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
            state = State.FAILED;
            if (exploration != null) exploration.setAutomationStatus("Stage reveal failed");
        }
    }

    private void tickSolving(Minecraft mc, Explorer exploration, SolverSession session,
        PuzzleCompletionGate puzzleCompletion, RewardChestLooter rewardLooter, ClientHooks hooks) {
        if (puzzleCompletion.lost()) {
            session.cancel("LootGames reported a loss; explorer held");
            state = State.FAILED;
            if (exploration != null) exploration.setAutomationStatus("Puzzle lost; explorer held");
            return;
        }
        if (!puzzleCompletion.won(mc.theWorld != null)) {
            if (!session.isActive()) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Solver stopped: " + session.getStatus());
            }
            return;
        }
        hooks.recordPuzzleSolved();
        com.kyroxova.lootgamesolver.core.MiniGame completedGame = session.getGame();
        session.cancel("Puzzle solved");
        hooks.releaseMovementKeys(mc);
        puzzleCompletion.clear();
        state = State.IDLE;
        int[] solvedPos = rewardCenter != null ? rewardCenter : autoPuzzlePosition;
        int[] room = exploration == null ? null : exploration.verifiedRoomCenter(mc);
        int[] cluster = ClientHooks.nearbyRewardCenter(mc, room);
        if (cluster != null) solvedPos = cluster;
        if (exploration != null) exploration.markStructureSolved(mc, solvedPos);
        if (hooks.isExplorationDriven() && exploration != null) {
            exploration.prepareRewardCollection(mc, solvedPos);
            hooks.ensureInventoryBaseline(mc);
            rewardLooter.start(mc, solvedPos, completedGame);
            hooks.setRewardCenter(solvedPos);
            hooks.setRewardPreCleanupPending(true);
            hooks.setRewardReturnPending(true);
        } else {
            hooks.setRewardReturnPending(false);
            hooks.setRewardPreCleanupPending(false);
        }
        autoPuzzlePosition = null;
    }

    public void startPuzzleSolver(Minecraft mc, LootGamesBridge puzzleBridge, SolverSession session,
        ClientHooks hooks) {
        if (hooks != null && hooks.getExploration() != null
            && hooks.getExploration()
                .navigating()) {
            hooks.getExploration()
                .stop(mc, "Solving puzzle in progress");
        }
        if (state != State.REVEALING_NEXT_STAGE) {
            Object game = puzzleBridge.nearbyGame(true);
            int level = puzzleBridge.currentMinesweeperLevel(game);
            if (level >= 1) activeMinesweeperLevel = level;
        }
        if (state != State.REVEALING_NEXT_STAGE || hooks.getInventoryBaseline() == null)
            hooks.captureInventoryBaseline(mc);
        session.start(ActionPlanner.Mode.SOLVE);
        if (session.isActive()) state = State.SOLVING;
        else state = State.FAILED;
    }

}
