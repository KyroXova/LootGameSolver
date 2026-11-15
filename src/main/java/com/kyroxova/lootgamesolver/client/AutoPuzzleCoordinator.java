package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

final class AutoPuzzleCoordinator {

    enum State {
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
    private int puzzleWaitTicks;
    private int masterClickAttempts;
    private int centerClickAttempts;
    private boolean minesweeperOpeningClickSent;
    private int activeMinesweeperLevel = -1;
    private int lastMasterClickTick = -40;
    private int lastCenterClickTick = -40;
    private World puzzleWorld;
    private int[] rewardCenter;

    State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    boolean isIdle() {
        return state == State.IDLE;
    }

    boolean isSolving() {
        return state == State.SOLVING;
    }

    boolean isActive() {
        return state != State.IDLE && state != State.FAILED;
    }

    int[] getRewardCenter() {
        return rewardCenter;
    }

    void setRewardCenter(int[] center) {
        this.rewardCenter = center;
    }

    int[] getAutoPuzzlePosition() {
        return autoPuzzlePosition;
    }

    void reset() {
        state = State.IDLE;
        autoPuzzlePosition = null;
        floorActivatorPosition = null;
        floorActivatorClicked = false;
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

    void startFromExploration(int[] puzzlePos, World world, PuzzleCompletionGate puzzleCompletion) {
        autoPuzzlePosition = puzzlePos;
        floorActivatorPosition = null;
        floorActivatorClicked = false;
        state = State.OPENING;
        puzzleWaitTicks = 0;
        masterClickAttempts = 0;
        centerClickAttempts = 0;
        minesweeperOpeningClickSent = false;
        lastMasterClickTick = -40;
        lastCenterClickTick = -40;
        puzzleWorld = world;
        puzzleCompletion.begin();
    }

    void startManual(int[] boardCenter, int[] floor, World world, PuzzleCompletionGate puzzleCompletion) {
        autoPuzzlePosition = boardCenter != null ? boardCenter.clone() : null;
        rewardCenter = boardCenter != null ? boardCenter.clone() : null;
        floorActivatorPosition = floor;
        floorActivatorClicked = false;
        state = State.OPENING;
        puzzleWaitTicks = 0;
        masterClickAttempts = 0;
        centerClickAttempts = 0;
        minesweeperOpeningClickSent = false;
        lastMasterClickTick = -40;
        lastCenterClickTick = -40;
        puzzleWorld = world;
        puzzleCompletion.begin();
    }

    void checkSolvingStage(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration, SolverSession session,
        PuzzleCompletionGate puzzleCompletion) {
        if (state != State.SOLVING || puzzleCompletion.won(mc.theWorld != null)) return;
        Object game = puzzleBridge.nearbyGame(true);
        int level = puzzleBridge.currentMinesweeperLevel(game);
        boolean nextLevel = activeMinesweeperLevel >= 1 && level > activeMinesweeperLevel && level <= 4;
        boolean retryReady = level == activeMinesweeperLevel && puzzleBridge.canOpenMinesweeperBoard(game);
        if (nextLevel || retryReady) {
            session.cancel(nextLevel ? "Minesweeper stage advanced" : "Minesweeper attempt reset");
            if (nextLevel) activeMinesweeperLevel = level;
            int[] center = puzzleBridge.gameCenter(game);
            state = exploration != null && center != null && exploration.moveToPuzzleCenter(mc, center)
                ? State.POSITIONING_NEXT_STAGE
                : State.REVEALING_NEXT_STAGE;
            puzzleWaitTicks = 0;
            centerClickAttempts = 0;
            minesweeperOpeningClickSent = false;
            lastCenterClickTick = -40;
            if (exploration != null) exploration.setAutomationStatus(
                nextLevel ? "Opening Minesweeper stage " + level : "Reopening Minesweeper attempt");
        }
    }

    void tick(Minecraft mc, LootGamesBridge puzzleBridge, Explorer exploration, SolverSession session,
        PuzzleCompletionGate puzzleCompletion, RewardChestLooter rewardLooter, ClientHooks hooks) {
        if (state == State.IDLE) return;
        if (mc.theWorld == null || mc.thePlayer == null || (puzzleWorld != null && puzzleWorld != mc.theWorld)) {
            reset();
            return;
        }

        if (state == State.OPENING) {
            puzzleWaitTicks++;
            int[] activator = puzzleBridge.nearbyFloorActivator();
            if (activator != null && exploration != null) {
                rewardCenter = activator.clone();
                floorActivatorPosition = activator;
                floorActivatorClicked = false;
                if (exploration.moveToPuzzleCenter(mc, activator)) {
                    state = State.POSITIONING;
                    puzzleWaitTicks = 0;
                    return;
                }
            }
            Object game = puzzleBridge.gameAtMaster(autoPuzzlePosition);
            if (game == null) game = puzzleBridge.nearbyGame(true);
            boolean masterInteractionSettled = masterClickAttempts > 0 && puzzleWaitTicks - lastMasterClickTick >= 20;
            if (game != null) {
                int[] detectedCenter = puzzleBridge.gameCenter(game);
                if (detectedCenter != null) rewardCenter = detectedCenter;
                if (puzzleBridge.detect(true) != null) {
                    startPuzzleSolver(mc, puzzleBridge, session, hooks);
                    return;
                }
                int[] center = detectedCenter;
                if (center != null && exploration != null && exploration.moveToPuzzleCenter(mc, center)) {
                    state = State.POSITIONING;
                    puzzleWaitTicks = 0;
                    return;
                }
                state = State.FAILED;
                if (exploration != null)
                    exploration.setAutomationStatus("Could not reach the board center; explorer held");
                return;
            }
            if (autoPuzzlePosition != null && masterClickAttempts < 4
                && puzzleWaitTicks - lastMasterClickTick >= 40
                && puzzleBridge.activateMaster(autoPuzzlePosition[0], autoPuzzlePosition[1], autoPuzzlePosition[2])) {
                masterClickAttempts++;
                lastMasterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Opening LootGames master");
            }
            if (game == null && masterInteractionSettled && puzzleWaitTicks <= 200 && exploration != null)
                exploration.setAutomationStatus("Waiting for LootGames to create the puzzle");
            if (puzzleWaitTicks > 200) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus(
                    game == null ? "Opened puzzle could not be located; explorer held"
                        : "Puzzle did not open; explorer held");
            }
            return;
        }

        if (state == State.POSITIONING) {
            if (exploration != null && exploration.consumePuzzleCenterArrival()) {
                state = State.REVEALING;
                puzzleWaitTicks = 0;
                centerClickAttempts = 0;
                lastCenterClickTick = -40;
            } else if (exploration == null || !exploration.navigating()) {
                int[] center = floorActivatorPosition != null ? floorActivatorPosition
                    : rewardCenter != null ? rewardCenter : autoPuzzlePosition;
                if (center != null && mc.thePlayer != null
                    && mc.thePlayer.getDistanceSq(center[0] + 0.5D, center[1] + 0.5D, center[2] + 0.5D) <= 9.0D) {
                    state = State.REVEALING;
                    puzzleWaitTicks = 0;
                    centerClickAttempts = 0;
                    lastCenterClickTick = -40;
                } else {
                    state = State.FAILED;
                    if (exploration != null)
                        exploration.setAutomationStatus("Could not reach the board center; explorer held");
                }
            }
            return;
        }

        if (state == State.POSITIONING_NEXT_STAGE) {
            if (exploration != null && exploration.consumePuzzleCenterArrival()) {
                state = State.REVEALING_NEXT_STAGE;
                puzzleWaitTicks = 0;
                lastCenterClickTick = -40;
            } else if (exploration == null || !exploration.navigating()) {
                state = State.REVEALING_NEXT_STAGE;
                puzzleWaitTicks = 0;
                lastCenterClickTick = -40;
            }
            return;
        }

        if (state == State.REVEALING) {
            puzzleWaitTicks++;
            DetectedGame revealed = puzzleBridge.detect(true);
            Object game = puzzleBridge.nearbyGame(true);
            Object activeGame = game != null ? game : revealed == null ? null : revealed.game;
            if (floorActivatorPosition != null) {
                if (!floorActivatorClicked) {
                    if (puzzleBridge.activateFloorActivator(floorActivatorPosition)) {
                        floorActivatorClicked = true;
                        lastCenterClickTick = puzzleWaitTicks;
                        if (exploration != null) exploration.setAutomationStatus("Opening floor activator");
                    }
                    if (puzzleWaitTicks > 200 && !floorActivatorClicked) {
                        state = State.FAILED;
                        if (exploration != null) exploration.setAutomationStatus("Could not click floor activator");
                    }
                    return;
                }
                if (activeGame == null) {
                    if (puzzleWaitTicks > lastCenterClickTick + 200) {
                        state = State.FAILED;
                        if (exploration != null) exploration.setAutomationStatus("Floor activator did not open puzzle");
                    }
                    return;
                }
                floorActivatorPosition = null;
                centerClickAttempts = 0;
                lastCenterClickTick = puzzleWaitTicks - 20;
            }
            if (revealed != null && centerClickAttempts > 0) {
                if (puzzleWaitTicks - lastCenterClickTick < 20) return;
                startPuzzleSolver(mc, puzzleBridge, session, hooks);
                return;
            }
            if (centerClickAttempts < 4 && activeGame != null
                && puzzleWaitTicks - lastCenterClickTick >= (centerClickAttempts == 0 ? 1 : 20)
                && puzzleBridge.activateBoardCenter(activeGame)) {
                centerClickAttempts++;
                lastCenterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Revealing board");
            }
            if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Board reveal failed");
            }
            return;
        }

        if (state == State.REVEALING_NEXT_STAGE) {
            puzzleWaitTicks++;
            Object game = puzzleBridge.nearbyGame(true);
            DetectedGame board = puzzleBridge.detect(true);
            Object activeGame = game != null ? game : board == null ? null : board.game;
            if (centerClickAttempts > 0 && board != null) {
                if (puzzleWaitTicks - lastCenterClickTick < 20) return;
                startPuzzleSolver(mc, puzzleBridge, session, hooks);
                return;
            }
            if (centerClickAttempts < 4 && puzzleBridge.canOpenMinesweeperBoard(activeGame)
                && puzzleWaitTicks - lastCenterClickTick >= (centerClickAttempts == 0 ? 1 : 20)
                && puzzleBridge.activateBoardCenter(activeGame)) {
                centerClickAttempts++;
                lastCenterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Revealing Minesweeper board");
            }
            if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Stage reveal failed");
            }
            return;
        }

        if (state == State.SOLVING) {
            if (puzzleCompletion.lost()) {
                session.cancel("LootGames reported a loss; explorer held");
                state = State.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Puzzle lost; explorer held");
                return;
            }
            if (puzzleCompletion.won(mc.theWorld != null)) {
                com.kyroxova.lootgamesolver.core.MiniGame completedGame = session.getGame();
                session.cancel("Puzzle solved");
                hooks.releaseMovementKeys(mc);
                puzzleCompletion.clear();
                state = State.IDLE;
                if (hooks.isExplorationDriven() && exploration != null) {
                    exploration.prepareRewardCollection(mc, rewardCenter != null ? rewardCenter : autoPuzzlePosition);
                    hooks.ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewardCenter != null ? rewardCenter : autoPuzzlePosition, completedGame);
                    hooks.setRewardPreCleanupPending(true);
                    hooks.setRewardReturnPending(true);
                } else {
                    hooks.setRewardReturnPending(false);
                    hooks.setRewardPreCleanupPending(false);
                }
                autoPuzzlePosition = null;
            }
        }
    }

    void startPuzzleSolver(Minecraft mc, LootGamesBridge puzzleBridge, SolverSession session, ClientHooks hooks) {
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
