package com.kyroxova.lootgamesolver.explorer;

import com.kyroxova.lootgamesolver.explorer.excavation.BlockPlacer;
import com.kyroxova.lootgamesolver.explorer.excavation.ExcavationPathfinder;
import com.kyroxova.lootgamesolver.explorer.excavation.ExcavationPlanner;
import com.kyroxova.lootgamesolver.explorer.excavation.ExcavationTerrain;
import com.kyroxova.lootgamesolver.explorer.excavation.ExplorerMiningDriver;
import com.kyroxova.lootgamesolver.explorer.excavation.FallingBlockWaiter;
import com.kyroxova.lootgamesolver.explorer.excavation.MiningAimGate;
import com.kyroxova.lootgamesolver.explorer.excavation.MiningAttackInput;
import com.kyroxova.lootgamesolver.explorer.pathfinder.BaritoneTransitCoordinator;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ExplorerRouteFollower;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ExplorerRouteNavigator;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ExplorerRoutePlanner;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ExplorerTransitCoordinator;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;
import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationControlPolicy;
import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationInput;
import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationSteering;
import com.kyroxova.lootgamesolver.explorer.pathfinder.OpenExitSearch;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ReturnNavigator;
import com.kyroxova.lootgamesolver.explorer.pathfinder.WalkPathfinder;
import com.kyroxova.lootgamesolver.explorer.structure.ExplorerChestPitNav;
import com.kyroxova.lootgamesolver.explorer.structure.ExplorerRoomCoordinator;
import com.kyroxova.lootgamesolver.explorer.structure.ExplorerRoomGeometry;
import com.kyroxova.lootgamesolver.explorer.structure.ProgressiveSearch;
import com.kyroxova.lootgamesolver.explorer.structure.PuzzleCenterNavigator;
import com.kyroxova.lootgamesolver.explorer.structure.RoomEntry;
import com.kyroxova.lootgamesolver.explorer.structure.RoomExit;
import com.kyroxova.lootgamesolver.explorer.structure.SavedStructureFinder;
import com.kyroxova.lootgamesolver.explorer.structure.SolvedStructureStore;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTrailCoordinator;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTrailSession;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.client.storage.InventoryToolTransfer;
import com.kyroxova.lootgamesolver.client.ui.StatusText;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class Explorer {

    public static final boolean ENABLED = true;
    public final List<int[]> route = new ArrayList<int[]>();
    public World world;
    public SavedStructureFinder savedFinder;
    public TileEntity target;
    public int ticks;
    public int stalled;
    public int stallRecoveries;
    public final java.util.Set<Long> blockedWalkNodes = new java.util.LinkedHashSet<Long>();
    public double lastDistance = Double.MAX_VALUE;
    public boolean walking;
    public boolean digging;
    public int savedSlot = -1;
    public int digTicks;
    public final FallingBlockWaiter fallingBlocks = new FallingBlockWaiter();
    public int toolWaitTicks;
    public boolean waitingForTool;
    public String status = "Scanning loaded terrain";
    public ProgressiveSearch search;
    public boolean searching;
    public NavigationInput navigationInput;
    public int stairX;
    public int stairZ;
    public int stairSteps;
    public boolean seekingDryEntrance;
    public boolean entranceSelected;
    public final List<int[]> entranceCandidates = new ArrayList<int[]>();
    public int entranceCandidate;
    public double segmentX;
    public double segmentZ;
    public final MiningAimGate miningAim = new MiningAimGate();

    public final ExplorerSustain sustain = new ExplorerSustain();
    public int settlingTicks;
    public ExcavationPathfinder excavationSearch;
    public int[] roofEscapeGoal;
    public final List<int[]> excavationRoute = new ArrayList<int[]>();
    public final List<int[]> rejectedSteps = new ArrayList<int[]>();
    public final List<int[]> navigationPreview = new ArrayList<int[]>();
    private int[] previewHead;
    private int previewSize = -1;
    public int excavationReplans;
    public int exitRecoveryTicks;
    public int exitRecoveryAttempts;
    public OpenExitSearch openExitSearch;
    public boolean followingOpenExit;
    public final ExplorerCamera camera = new ExplorerCamera();
    public int searchGoalX, searchGoalZ;
    public int[] extendedFrom;
    public int approachAttempts;
    public int[] knownStructure;
    public boolean roomWorkActive;
    public ExcavationPathfinder approachPlan;
    public int[] approachAnchor;
    public boolean approachReady;
    public final InventoryToolTransfer toolTransfer = new InventoryToolTransfer();
    public final BlockPlacer blockPlacer = new BlockPlacer(toolTransfer, camera);
    public final MiningAttackInput miningAttack = new MiningAttackInput();
    public long miningTarget = Long.MIN_VALUE;
    public int miningSlot = -1;
    public int aimTicks;
    public final ExplorerTrailSession trail = new ExplorerTrailSession(
        new File(new File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"), "access-trails.json"));
    public boolean returning, returnAvailable;
    public int cameraPitchDirection;
    public int pitchLastY;
    public double pitchLandingX, pitchLandingZ;
    public int miningCameraCooldownTicks;
    public int inventoryMiningCooldownTicks;
    public boolean returnDigging;
    public TileEntity returnOriginalTarget;
    public List<int[]> returnAfterDig;
    public boolean returnDigResumeSearch;
    public boolean rejoiningSavedTrail;
    public int returnJoinX, returnJoinY, returnJoinZ;
    public List<int[]> returnDigSource;
    public int returnDigAnchorIndex;
    public final ExplorerRoomGeometry roomGeometry = new ExplorerRoomGeometry();
    public String excavationMode = "Access route";
    public int routeRetryTicks;
    public String routeRetryStatus = "";
    public int[] entrySurfacePoint;
    public final ExplorerRouteFollower routeFollower = new ExplorerRouteFollower();
    public boolean escapingChestPit;
    public final ExplorerChestPitNav chestPitNav = new ExplorerChestPitNav();
    public final RoomEntry roomEntry = new RoomEntry();
    public final RoomExit roomExit = new RoomExit();
    public final WalkPathfinder walkPathfinder = new WalkPathfinder();
    public final PuzzleCenterNavigator puzzleCenter = new PuzzleCenterNavigator();
    public final ExcavationPlanner excavationPlanner = new ExcavationPlanner();
    public final ReturnNavigator returnNav = new ReturnNavigator();
    public final List<int[]> staircaseDugPath = new ArrayList<int[]>();
    public int entranceSurfaceY = 64;
    public boolean resumeSearchAfterReturn;
    public List<int[]> roomExitCandidates;
    public int roomExitCandidateIndex;
    public int[] activeRoomExit;
    public boolean roomExitPlanning;
    public boolean roomExitNeedsResume;
    public boolean roomExitCrossing;
    public int roomExitJumpRetries;

    public List<int[]> roomEntryCandidates;
    public int roomEntryCandidateIndex;
    public int[] activeRoomEntry;
    public boolean roomEntryPlanning;
    public boolean roomEntryCrossing;
    public boolean roomEntrySearchNeedsResume;
    public boolean walkingSearchNeedsResume;

    public GroundPathfinder.Search walkingSearch;
    public int[] snowRecoveryTarget;
    public boolean walkingPlanFailed;
    public GroundPathfinder.Search continuationSearch;
    public final ExcavationTerrain excavationTerrain = new ExcavationTerrain();
    public int continuationX, continuationZ;
    public final SolvedStructureStore solvedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "solved-structures.json"));
    public final SolvedStructureStore blockedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "blocked-structures.json"));
    public boolean puzzleArrivalPending;
    public ExcavationPathfinder existingAccessPlan;
    public boolean planningExistingAccess;
    public boolean existingAccessChecked;
    public boolean movingToPuzzleCenter;
    public boolean movingToRewardView;
    public final BaritoneNavigationBridge baritoneNavigation = new BaritoneNavigationBridge();
    public final BaritoneTransitCoordinator baritoneCoordinator = new BaritoneTransitCoordinator();
    public boolean puzzleCenterArrivalPending;
    public TileEntity puzzleMasterTarget;
    public GroundPathfinder.Search returnJoinSearch;
    public List<int[]> returnTrailRoute;
    public float searchYaw;

    public final ExplorerMiningDriver miningDriver = new ExplorerMiningDriver();
    public final ExplorerRoutePlanner routePlanner = new ExplorerRoutePlanner();
    public final ExplorerSearchCoordinator searchCoordinator = new ExplorerSearchCoordinator();
    public final ExplorerRouteNavigator routeNavigator = new ExplorerRouteNavigator();
    public final ExplorerGoalCoordinator goalCoordinator = new ExplorerGoalCoordinator();
    public final ExplorerRoomCoordinator roomCoordinator = new ExplorerRoomCoordinator();
    public final ExplorerTrailCoordinator trailCoordinator = new ExplorerTrailCoordinator();
    public final ExplorerTransitCoordinator transitCoordinator = new ExplorerTransitCoordinator();
    public final ExplorerSessionController sessionController = new ExplorerSessionController();

    public String explorationAction() {
        return returnAvailable ? "Return" : returning ? "Stop" : "Explore";
    }

    public double waypointLabelY;

    public double waypointLabelY() {
        return waypointLabelY;
    }

    public boolean reachableBoard(Minecraft mc) {
        if (returning || searching
            || movingToPuzzleCenter
            || knownStructure == null
            || target == null
            || !insideDungeonRoom(mc)) return false;
        return com.kyroxova.lootgamesolver.minecraft.LootGamesBridge
            .visibleMasterFace(mc, target.xCoord, target.yCoord, target.zCoord) != null;
    }

    public boolean pausedForControl;
    public boolean inventoryMaintenance;

    public void setInventoryMaintenance(boolean active) {
        inventoryMaintenance = active;
        baritoneNavigation.setPaused(active);
    }

    public boolean inventoryMaintenanceReady(Minecraft mc) {
        return inventoryMaintenance && mc.thePlayer != null
            && mc.thePlayer.onGround
            && mc.thePlayer.hurtTime == 0
            && !mc.thePlayer.capabilities.isFlying
            && !NavigationControlPolicy.hasManualMovement(
                GameSettings.isKeyDown(mc.gameSettings.keyBindForward),
                GameSettings.isKeyDown(mc.gameSettings.keyBindBack),
                GameSettings.isKeyDown(mc.gameSettings.keyBindLeft),
                GameSettings.isKeyDown(mc.gameSettings.keyBindRight),
                GameSettings.isKeyDown(mc.gameSettings.keyBindSneak),
                GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
    }

    private void inspectUpcomingRoute(Minecraft mc) {
        transitCoordinator.inspectUpcomingRoute(mc, this);
    }

    private void prepareApproach(Minecraft mc) {
        transitCoordinator.prepareApproach(mc, this);
    }

    public int[] structureWaypoint() {
        return trailCoordinator.structureWaypoint(Minecraft.getMinecraft(), this);
    }

    public boolean consumePuzzleArrival() {
        if (!puzzleArrivalPending) return false;
        puzzleArrivalPending = false;
        return true;
    }

    public int[] puzzlePosition() {
        return knownStructure == null ? null : knownStructure.clone();
    }

    public void signalPuzzleArrival(Minecraft mc, String message) {
        int[] position = target != null ? new int[] { target.xCoord, target.yCoord, target.zCoord }
            : knownStructure == null ? null : knownStructure.clone();
        stop(mc, message);
        knownStructure = position;
        puzzleArrivalPending = position != null;
    }

    public boolean resumePuzzleInCurrentRoom(Minecraft mc, int[] center) {
        return puzzleCenter.resumePuzzleInCurrentRoom(mc, this, center);
    }

    public boolean interruptSearchForRoomPuzzle(Minecraft mc, int[] center) {
        if (!walking || returning
            || movingToPuzzleCenter
            || baritoneCoordinator.surfaceMove
            || baritoneCoordinator.returnMove
            || baritoneCoordinator.returnWaiting
            || baritoneCoordinator.resumeSearch
            || !inDungeonRoom(mc)
            || !puzzleCenter.isInSameRoom(mc, this, center)) return false;
        stop(mc, "Opening puzzle in current room");
        return puzzleCenter.resumePuzzleInCurrentRoom(mc, this, center);
    }

    public void suspendStructureSearchForRoomWork(Minecraft mc) {
        if (baritoneCoordinator.searchMove || baritoneCoordinator.structureMove || (walking && searching)) {
            stop(mc, "Room automation in progress");
        }
    }

    public void suspendUnrelatedRouteForRewards(Minecraft mc, int[] expectedView) {
        boolean matching = rewardRouteMatches(
            walking,
            movingToRewardView,
            target == null ? null : new int[] { target.xCoord, target.yCoord, target.zCoord },
            expectedView);
        if (walking && !matching) {
            stop(mc, "Reward collection in progress");
            target = null;
            puzzleMasterTarget = null;
            knownStructure = null;
        }
    }

    public static boolean rewardRouteMatches(boolean walking, boolean rewardView, int[] target, int[] expected) {
        return walking && rewardView && target != null && expected != null && java.util.Arrays.equals(target, expected);
    }

    public void pauseAutomation(boolean paused) {
        baritoneNavigation.setPaused(paused);
        if (paused && navigationInput != null) {
            navigationInput.forward = 0;
            navigationInput.sideways = 0;
            navigationInput.hop = false;
        }
    }

    public void recoverActiveRoute(Minecraft mc) {
        transitCoordinator.recoverActiveRoute(mc, this);
    }

    public void refreshTravelMode(Minecraft mc) {
        transitCoordinator.refreshTravelMode(mc, this);
    }

    public boolean moveToPuzzleCenter(Minecraft mc, int[] center) {
        return puzzleCenter.moveToPuzzleCenter(mc, this, center);
    }

    public boolean moveToRewardView(Minecraft mc, int[] center) {
        return puzzleCenter.moveToRewardView(mc, this, center);
    }

    public boolean reachPuzzleCenter(Minecraft mc) {
        return puzzleCenter.reachPuzzleCenter(mc, this);
    }

    public boolean consumePuzzleCenterArrival() {
        return puzzleCenter.consumeArrival();
    }

    public void markStructureSolved(Minecraft mc, int[] position) {
        if (mc == null || mc.theWorld == null) return;
        try {
            String world = worldIdentity(mc);
            int dim = mc.theWorld.provider.dimensionId;
            int[] center = position != null ? position : knownStructure;
            if (center != null) solvedStructures.mark(world, dim, center, center);
        } catch (java.io.IOException ignored) {}
    }

    public void finishPuzzle(Minecraft mc, boolean solved) {
        if (solved && mc.theWorld != null) {
            int[] pos = knownStructure != null ? knownStructure
                : new int[] { (int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ) };
            markStructureSolved(mc, pos);
        }
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        puzzleArrivalPending = false;
        returnAvailable = false;
        baritoneNavigation.stop();
        baritoneCoordinator.reset();
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (solved && BaritoneTransitCoordinator
            .needsSurfaceExit(insideDungeonRoom(mc), mc.theWorld.canBlockSeeTheSky(x, y, z))) {
            route.clear();
            returning = false;
            digging = false;
            returnDigging = false;
            if (!baritoneCoordinator.beginSurfaceExit(mc, this)) {
                if (LootGameSolverConfig.staircaseEnabled && beginDiggingToSurface(mc)) return;
                stop(mc, "No safe surface destination found");
            }
            return;
        }
        continueProgressiveSearch(mc, solved);
    }

    public void retryExitLater(Minecraft mc) {
        searchCoordinator.retryExitLater(mc, this);
    }

    public void continueProgressiveSearch(Minecraft mc, boolean solved) {
        searchCoordinator.continueProgressiveSearch(mc, this, solved);
    }

    public void beginAccessTrail(Minecraft mc) {
        trailCoordinator.beginAccessTrail(mc, this);
    }

    public boolean reuseSavedAccessPath(Minecraft mc) {
        return false;
    }

    public boolean beginExistingAccessSearch(Minecraft mc) {
        if (!LootGameSolverConfig.staircaseEnabled || mc.thePlayer == null
            || mc.theWorld == null
            || target == null
            || target.yCoord >= mc.thePlayer.boundingBox.minY - 2
            || Math.max(
                Math.abs((int) Math.floor(mc.thePlayer.posX) - target.xCoord),
                Math.abs((int) Math.floor(mc.thePlayer.posZ) - target.zCoord)) > 40
            || existingAccessChecked) return false;
        existingAccessChecked = true;
        existingAccessPlan = new ExcavationPathfinder(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            target.xCoord,
            target.yCoord + 1,
            target.zCoord);
        planningExistingAccess = true;
        walking = true;
        digging = false;
        status = "Checking for a safe existing entrance";
        return true;
    }

    public void recordAccessPoint(int x, int y, int z) {
        trailCoordinator.recordAccessPoint(this, x, y, z);
    }

    public void persistAccessTrail(boolean force) {
        trailCoordinator.persistAccessTrail(this, force);
    }

    public void removePersistedTrail() {
        trailCoordinator.removePersistedTrail(this);
    }

    public boolean startSavedReturn(Minecraft mc, boolean resumeSearch, int maxJoinDistance) {
        return false;
    }

    public boolean savedRouteWalkable(Minecraft mc, List<int[]> points) {
        return returnNav.savedRouteWalkable(mc, this, points);
    }

    public void clearReturnTransits() {
        returnNav.clearReturnTransits(this);
    }

    public boolean startChestPitReturn(Minecraft mc, boolean resumeSearch) {
        return chestPitNav.start(mc, this, resumeSearch);
    }

    public boolean advanceChestPitReturn(Minecraft mc) {
        return chestPitNav.advance(mc, this);
    }

    public void clearChestPitSearch() {
        chestPitNav.reset();
        escapingChestPit = false;
    }

    public boolean startRoomExitSearch(Minecraft mc) {
        return roomCoordinator.startRoomExitSearch(mc, this);
    }

    public boolean startNextRoomExitSearch(Minecraft mc) {
        return roomCoordinator.startNextRoomExitSearch(mc, this);
    }

    public boolean startRoomEntrySearch(Minecraft mc) {
        return roomCoordinator.startRoomEntrySearch(mc, this);
    }

    public boolean startNextRoomEntrySearch(Minecraft mc) {
        return roomCoordinator.startNextRoomEntrySearch(mc, this);
    }

    public void appendRoomExitWaypoint(int[] exit, int offset) {
        roomCoordinator.appendRoomExitWaypoint(this, exit, offset);
    }

    public boolean clearRoomExitObstruction(Minecraft mc, int[] next, int x, int y, int z) {
        return roomCoordinator.clearRoomExitObstruction(mc, this, next, x, y, z);
    }

    public boolean insideDungeonRoom(Minecraft mc) {
        return roomCoordinator.insideDungeonRoom(mc, this);
    }

    public boolean clearableExitColumn(World world, int x, int y, int z) {
        return roomCoordinator.clearableExitColumn(world, this, x, y, z);
    }

    public boolean planRoofEscape(Minecraft mc) {
        return roomCoordinator.planRoofEscape(mc, this);
    }

    public boolean rerouteSavedReturn(Minecraft mc, int firstCheckpoint) {
        return returnNav.rerouteSavedReturn(mc, this, firstCheckpoint);
    }

    public boolean digTowardSavedExit(Minecraft mc) {
        return returnNav.digTowardSavedExit(mc, this);
    }

    public boolean startReturnDig(Minecraft mc, List<int[]> savedRoute) {
        return returnNav.startReturnDig(mc, this, savedRoute);
    }

    public boolean planReturnDigToAnchor(Minecraft mc, int anchorIndex) {
        return returnNav.planReturnDigToAnchor(mc, this, anchorIndex);
    }

    public boolean tryNextReturnDigAnchor(Minecraft mc) {
        return returnNav.tryNextReturnDigAnchor(mc, this);
    }

    public void resumeSavedReturnAfterDig() {
        returnNav.resumeSavedReturnAfterDig(this);
    }

    public static String worldIdentity(Minecraft mc) {
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) return "save:" + mc.getIntegratedServer()
            .getFolderName();
        net.minecraft.client.multiplayer.ServerData server = mc.func_147104_D();
        return "server:" + (server == null ? "unknown" : server.serverIP);
    }

    public String navigationStatus() {
        if (status != null && status.startsWith("Retry ")) return status;
        if (baritoneCoordinator.active() && baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING) {
            if (baritoneCoordinator.shoreMove) return "Swimming to Shore";
            if (baritoneCoordinator.surfaceMove || baritoneCoordinator.returnMove) return "Ascending to Surface";
            if (baritoneCoordinator.structureMove) return "Navigating to Puzzle";
            if (baritoneCoordinator.centerMove) return "Moving to Board";
            if (baritoneCoordinator.searchMove) return "Searching Structures";
            return "Following Route";
        }
        if (walking && !inventoryMaintenance) {
            String lower = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("packing") || lower.contains("trash")) {
                return searching ? "Searching Structures" : "Following Route";
            }
        }
        return compactStatus(status);
    }

    public void setAutomationStatus(String message) {
        if (message == null || message.isEmpty()) {
            status = searching ? "Searching for the next structure" : "Following checked route";
            return;
        }
        status = compactStatus(message);
    }

    private static String compactStatus(String message) {
        return StatusText.explorer(message);
    }

    public void renderCamera(Minecraft mc) {
        if (inventoryMaintenance || mc.thePlayer == null || mc.theWorld == null) return;
        boolean baritoneActive = baritoneCoordinator.active();
        boolean attacking = mc.gameSettings != null && GameSettings.isKeyDown(mc.gameSettings.keyBindAttack);
        if (NavigationSteering.yieldCameraToBaritone(
            baritoneActive,
            baritoneActive && (baritoneCoordinator.breaking || baritoneNavigation.isBreaking()),
            attacking,
            miningCameraCooldownTicks)) {
            camera.clear();
            return;
        }
        boolean isMining = miningCameraCooldownTicks > 0
            || (baritoneCoordinator.active() && baritoneCoordinator.breaking)
            || digging
            || returnDigging
            || attacking;
        if (isMining) {
            camera.render(mc, world, false);
            return;
        }
        if (baritoneCoordinator.active()) {
            if (baritoneCoordinator.routePreview.isEmpty()) {
                camera.clear();
                return;
            }
            Float heading = NavigationSteering.straightHeading(
                baritoneCoordinator.routePreview,
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
            if (heading == null) {
                camera.clear();
                return;
            }
            camera.look(heading, 0F);
            camera.render(mc, world, true);
        } else camera.render(mc, world, true);
    }

    public void look(float yaw, float pitch) {
        look(yaw, pitch, false);
    }

    public void look(float yaw, float pitch, boolean force) {
        camera.look(yaw, pitch, force);
    }

    public void lookSmooth(float yaw, float pitch) {
        camera.lookSmooth(yaw, pitch);
    }

    public void clearAim() {
        camera.clear();
    }

    public void rememberBaritoneReturnPoint(Minecraft mc) {
        baritoneCoordinator.rememberReturnPoint(mc);
    }

    public boolean digging() {
        return digging;
    }

    public List<int[]> navigationRoute() {
        return baritoneCoordinator.active() ? baritoneCoordinator.routePreview : navigationPreview;
    }

    public List<int[]> excavationPreview() {
        return digging && !baritoneCoordinator.active() ? excavationRoute : java.util.Collections.emptyList();
    }

    public boolean navigating() {
        return walking;
    }

    public int checkpointDistance(Minecraft mc) {
        return trailCoordinator.checkpointDistance(mc, this);
    }

    public void routeToStructure(Minecraft mc) {
        transitCoordinator.routeToStructure(mc, this);
    }

    public void stopRewardView(Minecraft mc) {
        if (movingToRewardView) stop(mc, "Chest in reach");
    }

    public boolean hasPendingReturn() {
        return returning || returnAvailable;
    }

    public boolean inDungeonRoom(Minecraft mc) {
        return roomCoordinator.inDungeonRoom(mc, this);
    }

    public boolean inTargetRoom(Minecraft mc, int[] position) {
        return roomCoordinator.inTargetRoom(mc, this, position);
    }

    public boolean enterTargetRoom(Minecraft mc, int[] position) {
        return roomCoordinator.enterTargetRoom(mc, this, position);
    }

    public int[] verifiedRoomCenter(Minecraft mc) {
        return roomCoordinator.verifiedRoomCenter(mc, this);
    }

    public void skipUnopenableRoom(Minecraft mc, int[] center) {
        roomCoordinator.skipUnopenableRoom(mc, this, center);
    }

    public void prepareRewardCollection(Minecraft mc, int[] center) {
        trailCoordinator.prepareRewardCollection(mc, this, center);
    }

    public void clearLastCheckpoint(Minecraft mc) {
        trailCoordinator.clearLastCheckpoint(mc, this);
    }

    public boolean inventoryCleanupSafe(Minecraft mc) {
        return walking
            && (returning || baritoneCoordinator.surfaceMove
                || baritoneCoordinator.returnMove
                || (baritoneCoordinator.structureMove && !baritoneCoordinator.preRouting))
            && !digging
            && !returnDigging
            && inventoryCleanupDelayPassed()
            && !movingToPuzzleCenter
            && !sustain.busy()
            && !toolTransfer.active()
            && !blockPlacer.selectingBlock()
            && mc.thePlayer != null
            && mc.thePlayer.onGround
            && mc.thePlayer.hurtTime == 0
            && !mc.thePlayer.capabilities.isFlying
            && (baritoneCoordinator.active() ? !baritoneCoordinator.breaking : route.size() >= 2);
    }

    public boolean inventoryCleanupDelayPassed() {
        return inventoryMiningCooldownTicks == 0;
    }

    public boolean observingMinedDrops() {
        return walking && inventoryMiningCooldownTicks > 0;
    }

    public void rotateSearch(Minecraft mc) {
        searchCoordinator.rotateSearch(mc, this);
    }

    public boolean nextSearchGoal(Minecraft mc) {
        return searchCoordinator.nextSearchGoal(mc, this);
    }

    public void toggle(Minecraft mc) {
        transitCoordinator.toggle(mc, this);
    }

    public void tick(Minecraft mc, boolean solverActive, boolean roomWorkActive) {
        if (sessionController.checkPreTick(mc, this, solverActive, roomWorkActive)) return;
        if (routeNavigator.handleReturnAndExitRecovery(mc, this)) return;
        if (goalCoordinator.checkPendingSearches(mc, this)) return;
        if (pausedForControl) {
            pausedForControl = false;
            if (!waitingForTool) {
                stalled = 0;
                lastDistance = Double.MAX_VALUE;
            }
        }
        inspectUpcomingRoute(mc);
        if (returnJoinSearch != null || !walking) return;
        if (routeRetryTicks > 0) {
            if (--routeRetryTicks > 0) return;
            retryCurrentRoute(mc, routeRetryStatus);
            return;
        }
        if (walkingSearch != null) {
            routePlanner.advanceWalkingSearch(mc, this);
        }
        if (!returning && !digging
            && !seekingDryEntrance
            && !movingToPuzzleCenter
            && !roomEntryPlanning
            && !roomEntrySearchNeedsResume
            && !roomEntryCrossing
            && route.size() <= 48) extendRoute(mc);
        prepareApproach(mc);
        advanceExcavationSearch(mc);
        if (route.isEmpty()) {
            goalCoordinator.handleEmptyRoute(mc, this);
            return;
        }
        routeNavigator.advanceRoute(mc, this);
    }

    public void updateRoutePreview(Minecraft mc) {
        int[] head = route.isEmpty() ? null : route.get(0);
        if (head != previewHead || route.size() != previewSize) {
            previewHead = head;
            previewSize = route.size();
            navigationPreview.clear();
            for (int i = 0; i < Math.min(96, route.size()); i++) {
                int[] step = route.get(i);
                boolean water = mc.theWorld.getBlock(step[0], step[1], step[2])
                    .getMaterial() == net.minecraft.block.material.Material.water
                    || mc.theWorld.getBlock(step[0], step[1] - 1, step[2])
                        .getMaterial() == net.minecraft.block.material.Material.water;
                navigationPreview.add(new int[] { step[0], step[1], step[2], water ? 1 : 0 });
            }
        }
    }

    private void advanceExcavationSearch(Minecraft mc) {
        if (digging && !returnDigging && !route.isEmpty() && excavationRoute.isEmpty() && target != null) {
            int[] end = route.get(route.size() - 1);
            if (excavationSearch == null) {
                excavationSearch = new ExcavationPathfinder(
                    end[0],
                    end[1],
                    end[2],
                    roofEscapeGoal == null ? target.xCoord : roofEscapeGoal[0],
                    roofEscapeGoal == null ? target.yCoord + 1 : roofEscapeGoal[1],
                    roofEscapeGoal == null ? target.zCoord : roofEscapeGoal[2],
                    true);
            }
            if (excavationSearch.advance(
                (a, b, c, nx, ny, nz) -> excavationTerrain.excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
                24,
                System.nanoTime() + 3_000_000L)) {
                excavationRoute.addAll(excavationSearch.path);
                excavationSearch = null;
            }
        }
    }

    public boolean jumpOutOfRewardPit(Minecraft mc) {
        return routeNavigator.jumpOutOfRewardPit(mc, this);
    }

    public boolean tryPlaceReturnStep(Minecraft mc, int[] next) {
        boolean active = blockPlacer.tryPlaceReturnStep(mc, next, returning);
        if (blockPlacer.status() != null) status = blockPlacer.status();
        return active;
    }

    public boolean recoverableRouteFailure(String message) {
        return searchCoordinator.recoverableRouteFailure(message);
    }

    public void queueRouteRetry(Minecraft mc, String message) {
        searchCoordinator.queueRouteRetry(mc, this, message);
    }

    public void retryCurrentRoute(Minecraft mc, String reason) {
        searchCoordinator.retryCurrentRoute(mc, this, reason);
    }

    public void markBlockedWalkNode(int x, int y, int z) {
        searchCoordinator.markBlockedWalkNode(this, x, y, z);
    }

    public boolean swimToNearestShore(Minecraft mc) {
        return routeNavigator.swimToNearestShore(mc, this);
    }

    public void stop(Minecraft mc, String message) {
        if (!baritoneCoordinator.active() && recoverableRouteFailure(message)) {
            queueRouteRetry(mc, message);
            return;
        }
        baritoneNavigation.stop();
        baritoneCoordinator.reset();
        baritoneCoordinator.routePreview.clear();
        navigationPreview.clear();
        returnTrailRoute = null;
        target = null;
        knownStructure = null;
        clearAim();
        sustain.stop(mc);
        fallingBlocks.reset();
        snowRecoveryTarget = null;
        exitRecoveryTicks = 0;
        exitRecoveryAttempts = 0;
        openExitSearch = null;
        followingOpenExit = false;
        persistAccessTrail(true);
        if (movingToPuzzleCenter && puzzleMasterTarget != null) target = puzzleMasterTarget;
        puzzleMasterTarget = null;
        movingToPuzzleCenter = false;
        movingToRewardView = false;
        if (returnDigging) {
            target = returnOriginalTarget;
            returnOriginalTarget = null;
            returnAfterDig = null;
            returnDigging = false;
            returnAvailable = true;
        }
        returnDigSource = null;
        rejoiningSavedTrail = false;
        if (returning) returnAvailable = true;
        returning = false;
        returnJoinSearch = null;
        returnTrailRoute = null;
        roomEntryCandidates = null;
        activeRoomEntry = null;
        roomEntryPlanning = false;
        roomEntryCrossing = false;
        roomEntrySearchNeedsResume = false;
        walkingSearchNeedsResume = false;
        roomExitCandidates = null;
        activeRoomExit = null;
        roomExitPlanning = false;
        roomExitNeedsResume = false;
        roomExitCrossing = false;
        miningDriver.reset(mc, this);
        blockPlacer.reset(mc);
        chestPitNav.reset();
        routeRetryTicks = 0;
        routeRetryStatus = "";
        walkingSearch = null;
        continuationSearch = null;
        pausedForControl = false;
        approachPlan = null;
        approachAnchor = null;
        camera.reset();
        cameraPitchDirection = 0;
        excavationSearch = null;
        excavationRoute.clear();
        rejectedSteps.clear();
        roofEscapeGoal = null;
        settlingTicks = 0;
        if (navigationInput != null && mc.thePlayer != null) {
            if (mc.thePlayer.movementInput == navigationInput) mc.thePlayer.movementInput = navigationInput.original;
            navigationInput = null;
        }
        if (mc.thePlayer != null) mc.thePlayer.setSprinting(false);
        if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSprint.getKeyCode(),
                net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
        }
        stairX = 0;
        stairZ = 0;
        stairSteps = 0;
        seekingDryEntrance = false;
        entranceSelected = false;
        entranceCandidates.clear();
        if (walking && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
        walking = false;
        if (searching) target = null;
        searching = false;
        route.clear();
        status = compactStatus(message);
    }

    public void scan(Minecraft mc) {
        searchCoordinator.scan(mc, this);
    }

    public boolean nextSearchLeg(Minecraft mc) {
        return searchCoordinator.nextSearchLeg(mc, this);
    }

    public GroundPathfinder.Terrain walkingTerrain(final World terrainWorld) {
        return routePlanner.walkingTerrain(Minecraft.getMinecraft(), this, terrainWorld);
    }

    public void extendRoute(Minecraft mc) {
        routePlanner.extendRoute(mc, this);
    }

    public void plan(final Minecraft mc) {
        routePlanner.plan(mc, this);
    }

    public void singleStep(Minecraft mc, int x, int y, int z, boolean center) {
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        route.add(new int[] { x, y, z, center ? 2 : 0 });
    }

    public void appendRoutePoint(List<int[]> points, int[] point) {
        ExplorerGeometry.appendRoutePoint(points, point);
    }

    public boolean beginDiggingAtCurrentPosition(Minecraft mc) {
        return excavationPlanner.beginDiggingAtCurrentPosition(mc, this);
    }

    public boolean beginDiggingToSurface(Minecraft mc) {
        return excavationPlanner.beginDiggingToSurface(mc, this);
    }

    public List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
        return excavationPlanner.patternedAccess(mc, this, x, y, z);
    }

    public boolean stairStep(Minecraft mc) {
        return excavationPlanner.stairStep(mc, this);
    }

    public boolean clearSnowForExcavation(Minecraft mc, int x, int y, int z) {
        return excavationPlanner.clearSnowForExcavation(mc, this, x, y, z);
    }

    public boolean selectDigEntrance(Minecraft mc) {
        return excavationPlanner.selectDigEntrance(mc, this);
    }

    public boolean findDryEntrance(Minecraft mc) {
        return excavationPlanner.findDryEntrance(mc, this);
    }

    public boolean attemptStairStep(Minecraft mc, int destinationY) {
        return excavationPlanner.attemptStairStep(mc, this, destinationY);
    }

    public int foliageSupportY(World w, int x, int foliageY, int z, int playerX, int footY, int playerZ, int destinationY) {
        return excavationPlanner.foliageSupportY(w, this, x, foliageY, z, playerX, footY, playerZ, destinationY);
    }

    public boolean minePlannedBlock(Minecraft mc, int nx, int by, int nz, int destinationY) {
        return miningDriver.minePlannedBlock(mc, this, nx, by, nz, destinationY);
    }

    public void releaseMiningWhileSandFalls(Minecraft mc) {
        miningDriver.releaseMiningWhileSandFalls(mc, this);
    }

    public String hud(Minecraft mc) {
        int[] pos = structureWaypoint();
        if (pos == null) return "Next Structure";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(pos[0] + 0.5D, pos[1] + 0.5D, pos[2] + 0.5D));
        return pos[0] + ", " + pos[1] + ", " + pos[2] + " (" + distance + "m)";
    }
}
