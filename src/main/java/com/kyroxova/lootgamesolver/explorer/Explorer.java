package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.*;
import static com.kyroxova.lootgamesolver.explorer.ExplorerGeometry.*;
import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.client.InventoryToolTransfer;
import com.kyroxova.lootgamesolver.client.StatusText;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class Explorer {

    public static final boolean ENABLED = true;
    final List<int[]> route = new ArrayList<int[]>();
    World world;
    SavedStructureFinder savedFinder;
    TileEntity target;
    int ticks;
    int stalled;
    int stallRecoveries;
    final java.util.Set<Long> blockedWalkNodes = new java.util.LinkedHashSet<Long>();
    double lastDistance = Double.MAX_VALUE;
    boolean walking;
    boolean digging;
    int savedSlot = -1;
    int digTicks;
    final FallingBlockWaiter fallingBlocks = new FallingBlockWaiter();
    int toolWaitTicks;
    boolean waitingForTool;
    String status = "Scanning loaded terrain";
    ProgressiveSearch search;
    boolean searching;
    NavigationInput navigationInput;
    int stairX;
    int stairZ;
    int stairSteps;
    boolean seekingDryEntrance;
    boolean entranceSelected;
    final List<int[]> entranceCandidates = new ArrayList<int[]>();
    int entranceCandidate;
    double segmentX;
    double segmentZ;
    final MiningAimGate miningAim = new MiningAimGate();

    final ExplorerSustain sustain = new ExplorerSustain();
    int settlingTicks;
    ExcavationPathfinder excavationSearch;
    int[] roofEscapeGoal;
    final List<int[]> excavationRoute = new ArrayList<int[]>();
    final List<int[]> rejectedSteps = new ArrayList<int[]>();
    final List<int[]> navigationPreview = new ArrayList<int[]>();
    private int[] previewHead;
    private int previewSize = -1;
    int excavationReplans;
    int exitRecoveryTicks;
    int exitRecoveryAttempts;
    OpenExitSearch openExitSearch;
    boolean followingOpenExit;
    final ExplorerCamera camera = new ExplorerCamera();
    int searchGoalX, searchGoalZ;
    int[] extendedFrom;
    int approachAttempts;
    int[] knownStructure;
    private World waypointScanWorld;
    private long waypointScanTick = Long.MIN_VALUE;
    private int[] waypointScanResult;
    private boolean roomWorkActive;
    ExcavationPathfinder approachPlan;
    int[] approachAnchor;
    boolean approachReady;
    final InventoryToolTransfer toolTransfer = new InventoryToolTransfer();
    final BlockPlacer blockPlacer = new BlockPlacer(toolTransfer, camera);
    final MiningAttackInput miningAttack = new MiningAttackInput();
    long miningTarget = Long.MIN_VALUE;
    int miningSlot = -1;
    int aimTicks;
    final ExplorerTrailSession trail = new ExplorerTrailSession(
        new File(new File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"), "access-trails.json"));
    boolean returning, returnAvailable;
    int cameraPitchDirection;
    int pitchLastY;
    double pitchLandingX, pitchLandingZ;
    private int miningCameraCooldownTicks;
    private int inventoryMiningCooldownTicks;
    boolean returnDigging;
    TileEntity returnOriginalTarget;
    List<int[]> returnAfterDig;
    boolean returnDigResumeSearch;
    boolean rejoiningSavedTrail;
    int returnJoinX, returnJoinY, returnJoinZ;
    List<int[]> returnDigSource;
    int returnDigAnchorIndex;
    final ExplorerRoomGeometry roomGeometry = new ExplorerRoomGeometry();
    String excavationMode = "Access route";
    int routeRetryTicks;
    String routeRetryStatus = "";
    int[] entrySurfacePoint;
    final ExplorerRouteFollower routeFollower = new ExplorerRouteFollower();
    boolean escapingChestPit;
    final ExplorerChestPitNav chestPitNav = new ExplorerChestPitNav();
    final RoomEntry roomEntry = new RoomEntry();
    final RoomExit roomExit = new RoomExit();
    final WalkPathfinder walkPathfinder = new WalkPathfinder();
    final PuzzleCenterNavigator puzzleCenter = new PuzzleCenterNavigator();
    final ExcavationPlanner excavationPlanner = new ExcavationPlanner();
    final ReturnNavigator returnNav = new ReturnNavigator();
    final List<int[]> staircaseDugPath = new ArrayList<int[]>();
    int entranceSurfaceY = 64;
    boolean resumeSearchAfterReturn;
    List<int[]> roomExitCandidates;
    int roomExitCandidateIndex;
    int[] activeRoomExit;
    boolean roomExitPlanning;
    boolean roomExitNeedsResume;
    boolean roomExitCrossing;
    int roomExitJumpRetries;

    List<int[]> roomEntryCandidates;
    int roomEntryCandidateIndex;
    int[] activeRoomEntry;
    boolean roomEntryPlanning;
    boolean roomEntryCrossing;
    boolean roomEntrySearchNeedsResume;
    boolean walkingSearchNeedsResume;

    GroundPathfinder.Search walkingSearch;
    int[] snowRecoveryTarget;
    boolean walkingPlanFailed;
    GroundPathfinder.Search continuationSearch;
    final ExcavationTerrain excavationTerrain = new ExcavationTerrain();
    int continuationX, continuationZ;
    final SolvedStructureStore solvedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "solved-structures.json"));
    final SolvedStructureStore blockedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "blocked-structures.json"));
    boolean puzzleArrivalPending;
    ExcavationPathfinder existingAccessPlan;
    boolean planningExistingAccess;
    private boolean existingAccessChecked;
    boolean movingToPuzzleCenter;
    boolean movingToRewardView;
    final BaritoneNavigationBridge baritoneNavigation = new BaritoneNavigationBridge();
    final BaritoneTransitCoordinator baritoneCoordinator = new BaritoneTransitCoordinator();
    private boolean puzzleCenterArrivalPending;
    TileEntity puzzleMasterTarget;
    GroundPathfinder.Search returnJoinSearch;
    List<int[]> returnTrailRoute;
    float searchYaw;

    final ExplorerMiningDriver miningDriver = new ExplorerMiningDriver();
    final ExplorerRoutePlanner routePlanner = new ExplorerRoutePlanner();
    final ExplorerSearchCoordinator searchCoordinator = new ExplorerSearchCoordinator();
    final ExplorerRouteNavigator routeNavigator = new ExplorerRouteNavigator();
    final ExplorerGoalCoordinator goalCoordinator = new ExplorerGoalCoordinator();

    public String explorationAction() {
        return returnAvailable ? "Return" : returning ? "Stop" : "Explore";
    }

    double waypointLabelY;

    public double waypointLabelY() {
        return waypointLabelY;
    }

    private void releaseMiningInput(Minecraft mc) {
        miningDriver.releaseMiningInput(mc, this);
    }

    boolean reachableBoard(Minecraft mc) {
        if (returning || searching
            || movingToPuzzleCenter
            || knownStructure == null
            || target == null
            || !insideDungeonRoom(mc)) return false;
        return com.kyroxova.lootgamesolver.minecraft.LootGamesBridge
            .visibleMasterFace(mc, target.xCoord, target.yCoord, target.zCoord) != null;
    }

    private boolean pausedForControl;
    private boolean inventoryMaintenance;

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

    private boolean undergroundNearby(Minecraft mc) {
        return !returning && !searching
            && LootGameSolverConfig.staircaseEnabled
            && target != null
            && ExcavationPolicy.shouldApproach(
                mc.thePlayer.posX,
                mc.thePlayer.boundingBox.minY,
                mc.thePlayer.posZ,
                target.xCoord,
                target.yCoord,
                target.zCoord);
    }

    private void chooseApproachDuringWalking(Minecraft mc) {
        if (digging || seekingDryEntrance
            || movingToPuzzleCenter
            || roomEntryPlanning
            || roomEntrySearchNeedsResume
            || roomEntryCrossing
            || route.isEmpty()
            || !undergroundNearby(mc)) return;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround && ExcavationPolicy.closeEntrance(x, z, target.xCoord, target.zCoord)
            && dryShore(mc.theWorld, x, y, z)
            && safe(mc.theWorld, x, y, z)) {
            entranceSurfaceY = y;
            entrySurfacePoint = new int[] { x, y, z };
            staircaseDugPath.clear();
            staircaseDugPath.add(new int[] { x, y, z });
            route.clear();
            extendedFrom = null;
            approachPlan = null;
            walkingSearch = null;
            continuationSearch = null;
            excavationSearch = null;
            excavationRoute.clear();
            digging = true;
            entranceSelected = true;
            savedSlot = mc.thePlayer.inventory.currentItem;
            status = "Planning underground access from current dry ground";
        } else if (ticks % 10 == 0) {
            findDryEntrance(mc);
        }
    }

    private void inspectUpcomingRoute(Minecraft mc) {
        if (digging || seekingDryEntrance
            || roomEntryPlanning
            || roomEntrySearchNeedsResume
            || roomEntryCrossing
            || roomExitCrossing
            || route.size() < 2
            || ticks % 10 != 0) return;
        int[] previous = { (int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ) };
        for (int i = 0; i < Math.min(12, route.size()); i++) {
            int[] step = route.get(i);
            boolean diagonal = step[0] != previous[0] && step[2] != previous[2] && step[1] == previous[1];
            boolean returnStep = returning && step[1] > previous[1]
                && step[1] <= previous[1] + 2
                && Math.abs(step[0] - previous[0]) + Math.abs(step[2] - previous[2]) == 1
                && mc.theWorld.blockExists(step[0], previous[1], step[2])
                && mc.theWorld.getBlock(step[0], previous[1], step[2])
                    .isReplaceable(mc.theWorld, step[0], previous[1], step[2]);
            if ((!safe(mc.theWorld, step[0], step[1], step[2]) && !returnStep)
                || (diagonal && (!safe(mc.theWorld, step[0], step[1], previous[2])
                    || !safe(mc.theWorld, previous[0], step[1], step[2])))) {
                if (returning) {
                    if (rerouteSavedReturn(mc, i + 1) || digTowardSavedExit(mc)) {
                        status = "Rerouting saved exit";
                        return;
                    }
                    retryExitLater(mc);
                    return;
                }
                plan(mc);
                status = "Replanning before blocked checkpoint";
                return;
            }
            previous = step;
        }
    }

    private void prepareApproach(Minecraft mc) {
        if (returning || digging
            || seekingDryEntrance
            || roomEntryPlanning
            || roomEntrySearchNeedsResume
            || roomEntryCrossing
            || searching
            || route.isEmpty()
            || route.size() > 8
            || !LootGameSolverConfig.staircaseEnabled) return;
        int[] end = route.get(route.size() - 1);
        if (target.yCoord >= end[1] - 2 || Math.abs(end[0] - target.xCoord) + Math.abs(end[2] - target.zCoord) > 48)
            return;
        if (approachAnchor != end) {
            approachAnchor = end;
            approachPlan = null;
            approachReady = false;
            if (!dryShore(mc.theWorld, end[0], end[1], end[2])) return;
            approachPlan = new ExcavationPathfinder(
                end[0],
                end[1],
                end[2],
                target.xCoord,
                target.yCoord + 1,
                target.zCoord,
                true);
        }
        if (approachPlan != null && !approachReady) approachReady = approachPlan.advance(
            (x, y, z, nx, ny, nz) -> excavationTerrain.excavationCost(mc.theWorld, x, y, z, nx, ny, nz),
            12,
            System.nanoTime() + 1_500_000L);
    }

    public int[] structureWaypoint() {
        Minecraft mc = Minecraft.getMinecraft();
        if (roomWorkActive) return null;
        if (searching || baritoneCoordinator.searchMove) return null;
        if (knownStructure != null) return ExplorerStructureDiscovery.displayedPosition(mc, knownStructure);
        if (target != null) return null;
        if (mc != null && mc.theWorld != null && mc.thePlayer != null) {
            long now = mc.theWorld.getTotalWorldTime();
            if (waypointScanWorld == mc.theWorld && now >= waypointScanTick && now - waypointScanTick < 20)
                return waypointScanResult == null ? null : waypointScanResult.clone();
            waypointScanWorld = mc.theWorld;
            waypointScanTick = now;
            net.minecraft.tileentity.TileEntity found = ExplorerStructureDiscovery
                .find(mc, savedFinder, solvedStructures, blockedStructures, worldIdentity(mc));
            if (found != null) {
                int[] foundPosition = { found.xCoord, found.yCoord, found.zCoord };
                waypointLabelY = ExplorerStructureDiscovery.labelHeight(mc, found);
                waypointScanResult = ExplorerStructureDiscovery.displayedPosition(mc, foundPosition);
                return waypointScanResult == null ? null : waypointScanResult.clone();
            }
            waypointScanResult = null;
        }
        return null;
    }

    public boolean consumePuzzleArrival() {
        if (!puzzleArrivalPending) return false;
        puzzleArrivalPending = false;
        return true;
    }

    public int[] puzzlePosition() {
        return knownStructure == null ? null : knownStructure.clone();
    }

    void signalPuzzleArrival(Minecraft mc, String message) {
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
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (baritoneCoordinator.surfaceMove || returning) {
            baritoneNavigation.stop();
            baritoneCoordinator.reset();
            route.clear();
            returning = false;
            if (!baritoneCoordinator.beginSurfaceExit(mc, this)) retryExitLater(mc);
        } else if (baritoneCoordinator.structureMove && target != null) {
            baritoneNavigation.stop();
            baritoneCoordinator.reset();
            routeToStructure(mc);
        } else {
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
        }
    }

    public void refreshTravelMode(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (!LootGameSolverConfig.caveMode && (baritoneCoordinator.searchMove || baritoneCoordinator.preRouting)) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (mc.theWorld.getHeightValue(x, z) - y > 6) {
                baritoneNavigation.stop();
                baritoneCoordinator.reset();
                if (!baritoneCoordinator.beginSurfaceExit(mc, this, false))
                    stop(mc, "No safe surface destination found");
                return;
            }
        }
        if (baritoneCoordinator.searchMove) {
            int x = baritoneCoordinator.searchX, z = baritoneCoordinator.searchZ;
            baritoneNavigation.stop();
            baritoneCoordinator.reset();
            baritoneCoordinator.searchX = x;
            baritoneCoordinator.searchZ = z;
            baritoneCoordinator.searchMove = true;
            baritoneCoordinator.searchNext = true;
            baritoneCoordinator.searchHandoff = true;
        } else if (baritoneCoordinator.structureMove && baritoneCoordinator.preRouting && target != null) {
            baritoneNavigation.stop();
            baritoneCoordinator.reset();
            routeToStructure(mc);
        }
    }

    public boolean moveToPuzzleCenter(Minecraft mc, int[] center) {
        return puzzleCenter.moveToPuzzleCenter(mc, this, center);
    }

    public boolean moveToRewardView(Minecraft mc, int[] center) {
        return puzzleCenter.moveToRewardView(mc, this, center);
    }

    boolean reachPuzzleCenter(Minecraft mc) {
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

    void appendRoutePoint(List<int[]> target, int[] step) {
        if (target == null || step == null) return;
        if (target.isEmpty() || !samePosition(target.get(target.size() - 1), step)) target.add(step.clone());
    }

    void retryExitLater(Minecraft mc) {
        searchCoordinator.retryExitLater(mc, this);
    }

    void continueProgressiveSearch(Minecraft mc, boolean solved) {
        searchCoordinator.continueProgressiveSearch(mc, this, solved);
    }

    void beginAccessTrail(Minecraft mc) {
        if (knownStructure == null || mc.thePlayer == null) return;
        if (entrySurfacePoint == null && mc.theWorld != null) {
            int sy = mc.theWorld.getHeightValue(knownStructure[0], knownStructure[2]);
            entrySurfacePoint = new int[] { knownStructure[0], sy + 1, knownStructure[2] };
        }
        if (trail.target == null || !samePosition(trail.target, knownStructure)) {
            persistAccessTrail(true);
            existingAccessChecked = false;
            blockedWalkNodes.clear();
            stallRecoveries = 0;
            trail.points.clear();
            trail.target = knownStructure.clone();
            trail.worldId = worldIdentity(mc);
            trail.dimension = mc.theWorld.provider.dimensionId;
            trail.dirty = false;
            trail.excavating = false;
        }
    }

    boolean reuseSavedAccessPath(Minecraft mc) {
        return false;
    }

    boolean beginExistingAccessSearch(Minecraft mc) {
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

    void recordAccessPoint(int x, int y, int z) {
        trail.points.clear();
        trail.dirty = false;
    }

    void persistAccessTrail(boolean force) {}

    void removePersistedTrail() {
        trail.points.clear();
        trail.dirty = false;
    }

    boolean startSavedReturn(Minecraft mc, boolean resumeSearch, int maxJoinDistance) {
        return false;
    }

    boolean savedRouteWalkable(Minecraft mc, List<int[]> points) {
        return returnNav.savedRouteWalkable(mc, this, points);
    }

    void clearReturnTransits() {
        returnNav.clearReturnTransits(this);
    }

    boolean startChestPitReturn(Minecraft mc, boolean resumeSearch) {
        return chestPitNav.start(mc, this, resumeSearch);
    }

    boolean advanceChestPitReturn(Minecraft mc) {
        return chestPitNav.advance(mc, this);
    }

    void clearChestPitSearch() {
        chestPitNav.reset();
        escapingChestPit = false;
    }

    boolean startRoomExitSearch(Minecraft mc) {
        return roomExit.startSearch(mc, this);
    }

    boolean startNextRoomExitSearch(Minecraft mc) {
        return roomExit.startNextSearch(mc, this);
    }

    boolean startRoomEntrySearch(Minecraft mc) {
        return roomEntry.startSearch(mc, this);
    }

    boolean startNextRoomEntrySearch(Minecraft mc) {
        return roomEntry.startNextSearch(mc, this);
    }

    void appendRoomExitWaypoint(int[] exit, int offset) {
        roomExit.appendWaypoint(this, exit, offset);
    }

    boolean clearRoomExitObstruction(Minecraft mc, int[] next, int x, int y, int z) {
        return roomExit.clearObstruction(mc, this, next, x, y, z);
    }

    boolean insideDungeonRoom(Minecraft mc) {
        return roomExit.isInsideDungeonRoom(mc, this);
    }

    boolean clearableExitColumn(World world, int x, int y, int z) {
        return RoomExit.clearableExitColumn(world, this, x, y, z);
    }

    boolean planRoofEscape(Minecraft mc) {
        return roomExit.planRoofEscape(mc, this);
    }

    boolean rerouteSavedReturn(Minecraft mc, int firstCheckpoint) {
        return returnNav.rerouteSavedReturn(mc, this, firstCheckpoint);
    }

    boolean digTowardSavedExit(Minecraft mc) {
        return returnNav.digTowardSavedExit(mc, this);
    }

    boolean startReturnDig(Minecraft mc, List<int[]> savedRoute) {
        return returnNav.startReturnDig(mc, this, savedRoute);
    }

    boolean planReturnDigToAnchor(Minecraft mc, int anchorIndex) {
        return returnNav.planReturnDigToAnchor(mc, this, anchorIndex);
    }

    boolean tryNextReturnDigAnchor(Minecraft mc) {
        return returnNav.tryNextReturnDigAnchor(mc, this);
    }

    void resumeSavedReturnAfterDig() {
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

    private void rememberBaritoneReturnPoint(Minecraft mc) {
        baritoneCoordinator.rememberReturnPoint(mc);
    }

    void singleStep(Minecraft mc, int x, int y, int z, boolean center) {
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        route.add(new int[] { x, y, z, center ? 2 : 0 });
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

    private boolean usingBaritoneNavigation() {
        return baritoneCoordinator.active();
    }

    List<int[]> rejectedPreview() {
        return rejectedSteps;
    }

    public boolean navigating() {
        return walking;
    }

    public int checkpointDistance(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return 0;
        double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        if (baritoneCoordinator != null && baritoneCoordinator.active()) {
            if (baritoneCoordinator.searchMove) {
                return (int) Math.round(
                    Math.hypot(px - (baritoneCoordinator.searchX + 0.5D), pz - (baritoneCoordinator.searchZ + 0.5D)));
            }
            if (target != null) {
                return (int) Math.round(Math.hypot(px - (target.xCoord + 0.5D), pz - (target.zCoord + 0.5D)));
            }
            if (baritoneCoordinator.returnPoint != null) {
                return (int) Math.round(
                    Math.hypot(
                        px - (baritoneCoordinator.returnPoint[0] + 0.5D),
                        pz - (baritoneCoordinator.returnPoint[2] + 0.5D)));
            }
        }
        if (target != null) {
            return (int) Math.round(Math.hypot(px - (target.xCoord + 0.5D), pz - (target.zCoord + 0.5D)));
        }
        if (knownStructure != null) {
            return (int) Math.round(Math.hypot(px - (knownStructure[0] + 0.5D), pz - (knownStructure[2] + 0.5D)));
        }
        if (!route.isEmpty()) {
            int[] last = route.get(route.size() - 1);
            return (int) Math.round(Math.hypot(px - (last[0] + 0.5D), pz - (last[2] + 0.5D)));
        }
        return 0;
    }

    public void routeToStructure(Minecraft mc) {
        if (target == null || mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        rememberBaritoneReturnPoint(mc);
        double playerY = mc.thePlayer.boundingBox.minY;
        double horizontal = Math
            .hypot(mc.thePlayer.posX - target.xCoord - 0.5D, mc.thePlayer.posZ - target.zCoord - 0.5D);
        if (playerY > target.yCoord + 5 || (!LootGameSolverConfig.caveMode && horizontal > 6D)) {
            BaritoneNavigationBridge.Start start = baritoneNavigation.startHorizontal(target.xCoord, target.zCoord);
            if (start == BaritoneNavigationBridge.Start.STARTED) {
                baritoneCoordinator.structureMove = true;
                baritoneCoordinator.preRouting = true;
                walking = true;
                searching = false;
                status = "Pre-routing to Puzzle";
                return;
            }
        }
        BaritoneNavigationBridge.Start baritoneStart = baritoneNavigation
            .startStructureApproach(target.xCoord, target.yCoord, target.zCoord);
        if (baritoneStart == BaritoneNavigationBridge.Start.STARTED) {
            baritoneCoordinator.structureMove = true;
            baritoneCoordinator.preRouting = false;
            walking = true;
            searching = false;
            status = "Walking to LootGame";
            return;
        }
        if (LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
            walking = true;
            searching = false;
            return;
        }
        plan(mc);
        walking = walkingSearch != null || !route.isEmpty();
        if (walking) {
            status = "Walking to LootGame";
        }
    }

    public void stopRewardView(Minecraft mc) {
        if (movingToRewardView) stop(mc, "Chest in reach");
    }

    public boolean hasPendingReturn() {
        return returning || returnAvailable;
    }

    public boolean inDungeonRoom(Minecraft mc) {
        return mc != null && mc.thePlayer != null && mc.theWorld != null && insideDungeonRoom(mc);
    }

    public boolean inTargetRoom(Minecraft mc, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null || position.length != 3)
            return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        return bounds != null && position[0] > bounds[0]
            && position[0] < bounds[1]
            && position[2] > bounds[2]
            && position[2] < bounds[3]
            && roomExit.isInsideRoom(mc, this, position[1]);
    }

    public boolean enterTargetRoom(Minecraft mc, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null || position.length != 3)
            return false;
        if (inTargetRoom(mc, position) || walking) return true;
        target = new TileEntity();
        target.xCoord = position[0];
        target.yCoord = position[1];
        target.zCoord = position[2];
        knownStructure = position.clone();
        if (startRoomEntrySearch(mc)) return true;
        if (LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
            walking = true;
            searching = false;
            return true;
        }
        status = "No safe route into the puzzle room";
        return false;
    }

    public int[] verifiedRoomCenter(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld == null
            || baritoneCoordinator.surfaceMove
            || baritoneCoordinator.returnMove
            || returning) return null;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.theWorld.canBlockSeeTheSky(x, y, z)) return null;
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return null;
        return new int[] { (bounds[0] + bounds[1]) >> 1, y - 1, (bounds[2] + bounds[3]) >> 1 };
    }

    public void skipUnopenableRoom(Minecraft mc, int[] center) {
        if (center == null) return;
        stop(mc, "Skipping unopenable room");
        knownStructure = center.clone();
        try {
            blockedStructures.mark(worldIdentity(mc), mc.theWorld.provider.dimensionId, center, center);
        } catch (java.io.IOException e) {
            status = "Could not save blocked room";
            return;
        }
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        if (!baritoneCoordinator.beginSurfaceExit(mc, this, false)) stop(mc, "No safe surface destination found");
    }

    public void prepareRewardCollection(Minecraft mc, int[] center) {
        if (walking || baritoneCoordinator.active()) stop(mc, "Reward collection started");
        if (mc == null || mc.theWorld == null || center == null) return;
        target = null;
        puzzleMasterTarget = null;
        movingToPuzzleCenter = false;
        movingToRewardView = false;
        knownStructure = center.clone();
        trail.target = null;
        beginAccessTrail(mc);
    }

    public void clearLastCheckpoint(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        boolean onSurfaceOutsideRoom = !insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(x, y, z);
        if (onSurfaceOutsideRoom) {
            continueProgressiveSearch(mc, true);
            return;
        }
        boolean removed = trail.points.removeLast();
        if (removed) {
            trail.dirty = true;
            if (trail.points.size() >= 2) persistAccessTrail(true);
            else removePersistedTrail();
        }
        if (returning || returnAvailable) {
            boolean resumeSearch = resumeSearchAfterReturn;
            route.clear();
            clearChestPitSearch();
            returnJoinSearch = null;
            returnTrailRoute = null;
            if (!startChestPitReturn(mc, resumeSearch) && !startSavedReturn(mc, resumeSearch, 128)) {
                retryExitLater(mc);
            }
        } else if (walking && target != null) {
            route.clear();
            walkingSearch = null;
            continuationSearch = null;
            extendedFrom = null;
            excavationSearch = null;
            excavationRoute.clear();
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            if (!digging) plan(mc);
            status = "Checkpoint cleared; replanning";
        } else status = removed ? "Last saved checkpoint cleared" : "No checkpoint to clear";
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
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
        if (walking) {
            stop(mc, "Walk stopped");
            return;
        }
        returnAvailable = false;
        returning = false;
        puzzleArrivalPending = false;
        planningExistingAccess = false;
        existingAccessPlan = null;
        existingAccessChecked = false;
        stallRecoveries = 0;
        blockedWalkNodes.clear();
        searchYaw = mc.thePlayer.rotationYaw;
        search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            searchYaw,
            LootGameSolverConfig.explorationSearchLimit,
            128);
        entranceSelected = false;
        excavationSearch = null;
        excavationReplans = 0;
        approachAttempts = 0;
        extendedFrom = null;
        approachPlan = null;
        approachAnchor = null;
        walkingSearch = null;
        continuationSearch = null;
        navigationPreview.clear();
        baritoneCoordinator.routePreview.clear();
        route.clear();
        returnTrailRoute = null;
        excavationRoute.clear();
        rejectedSteps.clear();
        entranceCandidates.clear();
        scan(mc);
        if (target == null) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (!LootGameSolverConfig.caveMode && mc.theWorld.getHeightValue(x, z) - y > 6) {
                if (!baritoneCoordinator.beginSurfaceExit(mc, this, false))
                    stop(mc, "No safe surface destination found");
                return;
            }
            int[] next = search.next();
            if (next != null) {
                BaritoneNavigationBridge.Start started = baritoneNavigation.startHorizontal(next[0], next[1]);
                if (started == BaritoneNavigationBridge.Start.STARTED) {
                    baritoneCoordinator.searchX = next[0];
                    baritoneCoordinator.searchZ = next[1];
                    baritoneCoordinator.searchTicks = 0;
                    baritoneCoordinator.searchHandoff = false;
                    baritoneCoordinator.searchMove = true;
                    searching = true;
                    walking = true;
                    status = "Exploring for structures";
                    return;
                }
                if (started != BaritoneNavigationBridge.Start.ABSENT) {
                    status = "Baritone navigation unavailable or busy";
                    return;
                }
            }
            walking = nextSearchLeg(mc);
            return;
        }
        routeToStructure(mc);
        if (walking) return;
        beginAccessTrail(mc);
        int playerX = (int) Math.floor(mc.thePlayer.posX);
        int playerY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int playerZ = (int) Math.floor(mc.thePlayer.posZ);
        int nearbyTrailIndex = trail.points.nearestIndex(playerX, playerY, playerZ, 24);
        if (nearbyTrailIndex < 0 && trail.target != null && trail.points.size() > 0) {
            trail.points.clear();
            trail.target = knownStructure == null ? null : knownStructure.clone();
            trail.excavating = false;
            trail.dirty = false;
            removePersistedTrail();
        }
        if (reuseSavedAccessPath(mc)) {} else if (beginExistingAccessSearch(mc)) {} else {
            plan(mc);
            walking = walkingSearch != null || !route.isEmpty();
            if (!walking && LootGameSolverConfig.staircaseEnabled
                && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
                walking = selectDigEntrance(mc);
            }
        }
        if (digging) savedSlot = mc.thePlayer.inventory.currentItem;
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        if (!walking) {
            walking = nextSearchLeg(mc);
            if (!walking) {
                rotateSearch(mc);
                walking = nextSearchLeg(mc);
                if (!walking) queueRouteRetry(mc, "Planning next search leg");
            }
        } else status = "Walking to LootGame";
    }

    private void resumeCurrentTarget(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || target == null) return;
        if (!searching && navigationInput == null) {
            BaritoneNavigationBridge.Start started = baritoneNavigation
                .startStructureApproach(target.xCoord, target.yCoord, target.zCoord);
            if (started == BaritoneNavigationBridge.Start.STARTED) {
                rememberBaritoneReturnPoint(mc);
                baritoneCoordinator.structureMove = true;
                walking = true;
                status = "Walking to LootGame";
                return;
            }
            if (started != BaritoneNavigationBridge.Start.ABSENT) {
                status = "Baritone navigation unavailable or busy";
                return;
            }
        }
        routeRetryTicks = 0;
        routeRetryStatus = "";
        stalled = 0;
        stallRecoveries = 0;
        lastDistance = Double.MAX_VALUE;
        walking = true;
        if (searching) {
            route.clear();
            walkingSearch = null;
            continuationSearch = null;
            plan(mc);
            status = "Resuming current exploration leg";
            return;
        }
        if (movingToPuzzleCenter) {
            if (++approachAttempts <= 3) {
                reachPuzzleCenter(mc);
            } else {
                approachAttempts = 0;
                int[] room = knownStructure == null ? null : knownStructure.clone();
                stop(mc, "Puzzle approach failed; room work held");
                knownStructure = room;
            }
            return;
        }
        beginAccessTrail(mc);
        route.clear();
        walkingSearch = null;
        continuationSearch = null;
        extendedFrom = null;
        if (reuseSavedAccessPath(mc)) {
            status = "Resuming saved tunnel route";
            return;
        }
        if (beginExistingAccessSearch(mc)) {
            status = "Checking the existing route again";
            return;
        }
        plan(mc);
        if (!route.isEmpty() || walkingSearch != null) {
            status = "Resuming route to current structure";
            return;
        }
        if (LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
            status = digging ? "Planning another tunnel route" : "Moving to another safe entrance";
            return;
        }
        routeRetryStatus = "No safe route to current target yet";
        routeRetryTicks = 1;
        status = "Retrying route to current target";
    }

    public void tick(Minecraft mc, boolean solverActive, boolean roomWorkActive) {
        this.roomWorkActive = roomWorkActive;
        if (inventoryMaintenance && mc.thePlayer != null && mc.theWorld != null) {
            baritoneNavigation.setPaused(true);
            if (navigationInput != null) {
                navigationInput.forward = 0;
                navigationInput.sideways = 0;
                navigationInput.hop = false;
            }
            return;
        }
        boolean isMining = (baritoneCoordinator.active() && baritoneCoordinator.breaking) || digging
            || returnDigging
            || (mc.gameSettings != null && GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        if (isMining) inventoryMiningCooldownTicks = 30;
        else if (inventoryMiningCooldownTicks > 0) inventoryMiningCooldownTicks--;
        if (isMining) {
            miningCameraCooldownTicks = 40;
            sustain.notifyBlockMined();
        } else if (miningCameraCooldownTicks > 0) {
            miningCameraCooldownTicks--;
        }
        if (baritoneCoordinator.active() && mc.thePlayer != null && mc.theWorld != null) {
            if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled || solverActive) {
                stop(mc, solverActive ? "Puzzle in progress" : "Exploration disabled");
                return;
            }
            if (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 150) {
                baritoneNavigation.stop();
                baritoneCoordinator.reset();
                if (!swimToNearestShore(mc)) stop(mc, "Low Air");
                return;
            }
            boolean manual = NavigationControlPolicy.hasManualMovement(
                GameSettings.isKeyDown(mc.gameSettings.keyBindForward),
                GameSettings.isKeyDown(mc.gameSettings.keyBindBack),
                GameSettings.isKeyDown(mc.gameSettings.keyBindLeft),
                GameSettings.isKeyDown(mc.gameSettings.keyBindRight),
                GameSettings.isKeyDown(mc.gameSettings.keyBindSneak),
                GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
            boolean interrupted = manual || mc.currentScreen != null
                || mc.thePlayer.hurtTime > 0
                || mc.thePlayer.capabilities.isFlying;
            if (baritoneCoordinator.breaking) sustain.notifyBlockMined();
            boolean sustaining = sustain.busy();
            if (!interrupted || sustaining) sustaining = sustain.tick(mc, walking, !baritoneCoordinator.breaking);
            baritoneNavigation.setPaused(interrupted || sustaining);
            if (interrupted || sustaining) {
                status = sustaining ? "Eating or preparing food"
                    : manual ? "Paused: release movement keys to resume"
                        : mc.currentScreen != null ? "Paused: close menu to resume"
                            : mc.thePlayer.hurtTime > 0 ? "Paused after damage"
                                : "Paused: land and disable flight to resume";
                return;
            }
        } else {
            if (digging || returnDigging) sustain.notifyBlockMined();
            sustain.tick(mc, walking, walking && !digging && !returnDigging);
        }
        boolean baritoneActive = baritoneCoordinator.active()
            || baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING;
        if (baritoneActive && navigationInput != null) {
            if (mc.thePlayer != null && mc.thePlayer.movementInput == navigationInput) {
                mc.thePlayer.movementInput = navigationInput.original;
            }
            navigationInput = null;
        }
        if (baritoneCoordinator.tick(mc, this)) return;
        if (!digging && !returnDigging) releaseMiningInput(mc);
        camera.clear();
        baritoneActive = baritoneCoordinator.active()
            || baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING;
        if (walking && !baritoneActive && mc.thePlayer != null && navigationInput == null) {
            navigationInput = new NavigationInput(mc.thePlayer.movementInput);
            mc.thePlayer.movementInput = navigationInput;
        } else if (baritoneActive && navigationInput != null) {
            if (mc.thePlayer != null && mc.thePlayer.movementInput == navigationInput) {
                mc.thePlayer.movementInput = navigationInput.original;
            }
            navigationInput = null;
        }
        if (navigationInput != null) {
            navigationInput.forward = 0;
            navigationInput.sideways = 0;
            navigationInput.hop = false;
        }
        if (world != mc.theWorld) {
            sustain.stop(mc);
            persistAccessTrail(true);
            if (navigationInput != null) stop(mc, "World changed");
            if (savedFinder != null) savedFinder.cancel();
            savedFinder = null;
            world = mc.theWorld;
            target = null;
            knownStructure = null;
            trail.points.clear();
            trail.target = null;
            puzzleArrivalPending = false;
            puzzleCenterArrivalPending = false;
            movingToPuzzleCenter = false;
            puzzleMasterTarget = null;
            resumeSearchAfterReturn = false;
            planningExistingAccess = false;
            existingAccessPlan = null;
            returning = false;
            returnAvailable = false;
            excavationTerrain.clearSupports();
            roomGeometry.reset();
            roomEntryCandidates = null;
            activeRoomEntry = null;
            roomEntryPlanning = false;
            roomEntryCrossing = false;
            roomEntrySearchNeedsResume = false;
            walkingSearchNeedsResume = false;
            chestPitNav.reset();
            routeRetryTicks = 0;
            excavationTerrain.clearToolCache();
            walking = false;
            route.clear();
            search = null;
            searching = false;
            if (world != null && mc.isSingleplayer() && mc.getIntegratedServer() != null) {
                java.io.File save = new java.io.File(
                    new java.io.File(mc.mcDataDir, "saves"),
                    mc.getIntegratedServer()
                        .getFolderName());
                String dimension = world.provider.getSaveFolder();
                if (dimension != null) save = new java.io.File(save, dimension);
                savedFinder = new SavedStructureFinder(new java.io.File(save, "region"));
            }
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (sustain.busy() && sustain.tick(mc, walking, false)) {
            status = "Eating or preparing food";
            return;
        }
        if (toolTransfer.active()) {
            status = "Moving tool using inventory screen";
            if (!toolTransfer.tick(mc)) stop(mc, "Inventory transfer interrupted; inspect the tool slots");
            return;
        }
        BlockPlacer.Outcome placement = blockPlacer.tick(mc, returning, route);
        if (placement == BlockPlacer.Outcome.WAITING) {
            status = "Waiting for return step placement";
            return;
        }
        if (placement == BlockPlacer.Outcome.FAILED) {
            stop(mc, "Could not place a safe return step; add cobblestone or dirt");
            return;
        }
        if (ticks % 20 == 0) excavationTerrain.clearToolCache();
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
        if (++ticks % 100 == 0 && !walking && !roomWorkActive) scan(mc);
        if (walking && searching && !roomWorkActive && ticks % 40 == 0) {
            TileEntity previous = target;
            scan(mc);
            if (target != null) {
                searching = false;
                route.clear();
            } else target = previous;
        }
        if (!walking) return;
        if (solverActive) {
            pausedForControl = true;
            mc.thePlayer.setSprinting(false);
            if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSprint.getKeyCode(),
                    net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
            }
            status = "Puzzle in progress";
            return;
        }
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) {
            stop(mc, "Exploration disabled");
            return;
        }
        if (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 150) {
            if (swimToNearestShore(mc)) return;
            stop(mc, "Low Air");
            return;
        }
        boolean manual = NavigationControlPolicy.hasManualMovement(
            GameSettings.isKeyDown(mc.gameSettings.keyBindForward),
            GameSettings.isKeyDown(mc.gameSettings.keyBindBack),
            GameSettings.isKeyDown(mc.gameSettings.keyBindLeft),
            GameSettings.isKeyDown(mc.gameSettings.keyBindRight),
            GameSettings.isKeyDown(mc.gameSettings.keyBindSneak),
            GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
        if (manual || mc.currentScreen != null || mc.thePlayer.hurtTime > 0 || mc.thePlayer.capabilities.isFlying) {
            pausedForControl = true;
            mc.thePlayer.setSprinting(false);
            if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSprint.getKeyCode(),
                    net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
            }
            mc.playerController.resetBlockRemoving();
            miningAim.reset();
            status = manual ? "Paused: release movement keys to resume"
                : mc.currentScreen != null ? "Paused: close menu to resume"
                    : mc.thePlayer.hurtTime > 0 ? "Paused after damage" : "Paused: land and disable flight to resume";
            return;
        }
        if (sustain.tick(mc, true, !route.isEmpty() && !digging && !returnDigging && !blockPlacer.selectingBlock())) {
            status = "Eating or preparing food";
            return;
        }
        if (returning && resumeSearchAfterReturn && !insideDungeonRoom(mc)) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            int[] entrance = trail.points.pointAt(0);
            boolean reachedMouth = entrance != null && Math.abs(x - entrance[0]) + Math.abs(z - entrance[2]) <= 1
                && y >= entrance[1]
                && Math.abs(y - entrance[1]) <= 2
                && solidCheckpoint(mc.theWorld, x, y, z);
            if (reachedMouth || mc.theWorld.canBlockSeeTheSky(x, y, z)) {
                continueProgressiveSearch(mc, true);
                return;
            }
        }
        if (returning && escapingChestPit && mc.thePlayer.onGround && !isRewardChestPit(mc)) {
            escapingChestPit = false;
            clearChestPitSearch();
            route.clear();
            returnJoinSearch = null;
            returnTrailRoute = null;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            if (insideDungeonRoom(mc) && startRoomExitSearch(mc)) return;
            if (startSavedReturn(mc, resumeSearchAfterReturn, 128)) return;
            retryExitLater(mc);
            return;
        }
        if (returning && isRewardChestPit(mc) && jumpOutOfRewardPit(mc)) return;
        if (exitRecoveryTicks > 0) {
            if (--exitRecoveryTicks == 0) {
                if (startChestPitReturn(mc, resumeSearchAfterReturn)) return;
                blockedWalkNodes.clear();
                final World exitWorld = mc.theWorld;
                final GroundPathfinder.Terrain ground = walkingTerrain(exitWorld);
                int sx = (int) Math.floor(mc.thePlayer.posX), sy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int sz = (int) Math.floor(mc.thePlayer.posZ);
                openExitSearch = new OpenExitSearch(new OpenExitSearch.Terrain() {

                    public boolean stand(int x, int y, int z) {
                        return ground.stand(x, y, z);
                    }

                    public boolean clear(int x, int y, int z) {
                        return ground.clear(x, y, z);
                    }

                    public boolean exit(int x, int y, int z) {
                        if (!solidCheckpoint(exitWorld, x, y, z)) return false;
                        int index = trail.points.nearestIndex(x, y, z, 0);
                        if (trail.points.size() >= 2) return index >= 0 && index < trail.points.size() - 1;
                        return exitWorld.canBlockSeeTheSky(x, y, z);
                    }
                }, sx, sy, sz);
            }
            return;
        }
        if (openExitSearch != null) {
            status = "Searching connected open blocks for an exit";
            if (!openExitSearch.advance(96, System.nanoTime() + 3_000_000L)) return;
            List<int[]> openPath = openExitSearch.path();
            openExitSearch = null;
            if (!openPath.isEmpty()) {
                route.clear();
                route.addAll(openPath);
                followingOpenExit = true;
                segmentX = mc.thePlayer.posX;
                segmentZ = mc.thePlayer.posZ;
                status = "Following connected open route";
                return;
            }
            boolean started = startChestPitReturn(mc, true);
            if (!started) started = ++exitRecoveryAttempts % 2 == 1 && digTowardSavedExit(mc);
            if (!started) started = startSavedReturn(mc, true, 128);
            if (!started) started = startRoomExitSearch(mc);
            if (!started) retryExitLater(mc);
            return;
        }
        if (chestPitNav.active()) {
            advanceChestPitReturn(mc);
            return;
        }
        if (goalCoordinator.checkPendingSearches(mc, this)) return;
        if (pausedForControl) {
            pausedForControl = false;
            if (!waitingForTool) {
                stalled = 0;
                lastDistance = Double.MAX_VALUE;
            }
        }
        inspectUpcomingRoute(mc);
        if (returnJoinSearch != null) return;
        if (!walking) return;
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

    boolean jumpOutOfRewardPit(Minecraft mc) {
        return routeNavigator.jumpOutOfRewardPit(mc, this);
    }

    boolean tryPlaceReturnStep(Minecraft mc, int[] next) {
        boolean active = blockPlacer.tryPlaceReturnStep(mc, next, returning);
        if (blockPlacer.status() != null) status = blockPlacer.status();
        return active;
    }

    boolean recoverableRouteFailure(String message) {
        return searchCoordinator.recoverableRouteFailure(message);
    }

    void queueRouteRetry(Minecraft mc, String message) {
        searchCoordinator.queueRouteRetry(mc, this, message);
    }

    void retryCurrentRoute(Minecraft mc, String reason) {
        searchCoordinator.retryCurrentRoute(mc, this, reason);
    }

    void markBlockedWalkNode(int x, int y, int z) {
        searchCoordinator.markBlockedWalkNode(this, x, y, z);
    }

    boolean swimToNearestShore(Minecraft mc) {
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

    void scan(Minecraft mc) {
        searchCoordinator.scan(mc, this);
    }

    boolean nextSearchLeg(Minecraft mc) {
        return searchCoordinator.nextSearchLeg(mc, this);
    }

    GroundPathfinder.Terrain walkingTerrain(final World terrainWorld) {
        return routePlanner.walkingTerrain(Minecraft.getMinecraft(), this, terrainWorld);
    }

    int existingWalkCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        return routePlanner.existingWalkCost(w, this, x, y, z, nx, ny, nz);
    }

    void extendRoute(Minecraft mc) {
        routePlanner.extendRoute(mc, this);
    }

    boolean shortcutToContinuation(Minecraft mc, List<int[]> continuation) {
        return routePlanner.shortcutToContinuation(mc, this, continuation);
    }

    void plan(final Minecraft mc) {
        routePlanner.plan(mc, this);
    }

    boolean beginDiggingAtCurrentPosition(Minecraft mc) {
        return excavationPlanner.beginDiggingAtCurrentPosition(mc, this);
    }

    public boolean beginDiggingToSurface(Minecraft mc) {
        return excavationPlanner.beginDiggingToSurface(mc, this);
    }

    List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
        return excavationPlanner.patternedAccess(mc, this, x, y, z);
    }

    boolean stairStep(Minecraft mc) {
        return excavationPlanner.stairStep(mc, this);
    }

    boolean clearSnowForExcavation(Minecraft mc, int x, int y, int z) {
        return excavationPlanner.clearSnowForExcavation(mc, this, x, y, z);
    }

    boolean selectDigEntrance(Minecraft mc) {
        return excavationPlanner.selectDigEntrance(mc, this);
    }

    boolean findDryEntrance(Minecraft mc) {
        return excavationPlanner.findDryEntrance(mc, this);
    }

    boolean attemptStairStep(Minecraft mc, int destinationY) {
        return excavationPlanner.attemptStairStep(mc, this, destinationY);
    }

    int foliageSupportY(World w, int x, int foliageY, int z, int playerX, int footY, int playerZ, int destinationY) {
        return excavationPlanner.foliageSupportY(w, this, x, foliageY, z, playerX, footY, playerZ, destinationY);
    }

    boolean minePlannedBlock(Minecraft mc, int nx, int by, int nz, int destinationY) {
        return miningDriver.minePlannedBlock(mc, this, nx, by, nz, destinationY);
    }

    void releaseMiningWhileSandFalls(Minecraft mc) {
        miningDriver.releaseMiningWhileSandFalls(mc, this);
    }

    public String hud(Minecraft mc) {
        int[] pos = structureWaypoint();
        if (pos == null) return "Next Structure";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(pos[0] + 0.5D, pos[1] + 0.5D, pos[2] + 0.5D));
        return pos[0] + ", " + pos[1] + ", " + pos[2] + " (" + distance + "m)";
    }
}
