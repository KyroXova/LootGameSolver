package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.*;
import static com.kyroxova.lootgamesolver.explorer.ExplorerGeometry.*;
import static com.kyroxova.lootgamesolver.explorer.ExplorerMiningTarget.visibleMiningPoint;
import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.client.DigToolSelector;
import com.kyroxova.lootgamesolver.client.InventoryToolTransfer;
import com.kyroxova.lootgamesolver.client.RewardChestGeometry;
import com.kyroxova.lootgamesolver.client.StatusText;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class Explorer {

    public static final boolean ENABLED = true;
    final List<int[]> route = new ArrayList<int[]>();
    World world;
    private SavedStructureFinder savedFinder;
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

    public String explorationAction() {
        return returnAvailable ? "Return" : returning ? "Stop" : "Explore";
    }

    private double waypointLabelY;

    public double waypointLabelY() {
        return waypointLabelY;
    }

    private void releaseMiningInput(Minecraft mc) {
        miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
    }

    boolean reachableBoard(Minecraft mc) {
        if (returning || searching || movingToPuzzleCenter || knownStructure == null) return false;
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        net.minecraft.util.Vec3 goal = net.minecraft.util.Vec3
            .createVectorHelper(target.xCoord + 0.5D, target.yCoord + 0.5D, target.zCoord + 0.5D);
        double reach = Math.min(4.2D, mc.playerController.getBlockReachDistance());
        if (eye.squareDistanceTo(goal) > reach * reach) return false;
        net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, goal);
        return hit != null && hit.blockX == target.xCoord && hit.blockY == target.yCoord && hit.blockZ == target.zCoord;
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

    GroundPathfinder.Search walkingSearch;
    private int[] snowRecoveryTarget;
    private boolean walkingPlanFailed;
    GroundPathfinder.Search continuationSearch;
    final ExcavationTerrain excavationTerrain = new ExcavationTerrain();
    private int continuationX, continuationZ;
    private final SolvedStructureStore solvedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "solved-structures.json"));
    private final SolvedStructureStore blockedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "blocked-structures.json"));
    boolean puzzleArrivalPending;
    private ExcavationPathfinder existingAccessPlan;
    private boolean planningExistingAccess;
    private boolean existingAccessChecked;
    boolean movingToPuzzleCenter;
    boolean movingToRewardView;
    final BaritoneNavigationBridge baritoneNavigation = new BaritoneNavigationBridge();
    final BaritoneTransitCoordinator baritoneCoordinator = new BaritoneTransitCoordinator();
    private boolean puzzleCenterArrivalPending;
    TileEntity puzzleMasterTarget;
    GroundPathfinder.Search returnJoinSearch;
    List<int[]> returnTrailRoute;

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
        if (baritoneCoordinator.searchMove || baritoneCoordinator.structureMove) {
            stop(mc, "Room automation in progress");
        }
    }

    public boolean moveToPuzzleCenter(Minecraft mc, int[] center) {
        return puzzleCenter.moveToPuzzleCenter(mc, this, center);
    }

    public boolean moveToRewardView(Minecraft mc, int[] center) {
        return puzzleCenter.moveToRewardView(mc, this, center);
    }

    private boolean reachPuzzleCenter(Minecraft mc) {
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
            if (staircaseDugPath != null && staircaseDugPath.size() >= 2) {
                route.clear();
                for (int i = staircaseDugPath.size() - 1; i >= 0; i--) {
                    int[] p = staircaseDugPath.get(i);
                    route.add(new int[] { p[0], p[1], p[2], 0 });
                }
                returning = true;
                returnAvailable = false;
                resumeSearchAfterReturn = true;
                walking = true;
                searching = false;
                digging = false;
                status = "Ascending dug path to surface";
                return;
            }
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
        if (returnDigging && returnOriginalTarget != null) target = returnOriginalTarget;
        returnOriginalTarget = null;
        returnDigging = false;
        digging = false;
        returning = true;
        walking = true;
        searching = false;
        route.clear();
        returnJoinSearch = null;
        returnTrailRoute = null;
        roomExitCandidates = null;
        roomExitPlanning = false;
        roomExitCrossing = false;
        excavationSearch = null;
        excavationRoute.clear();
        exitRecoveryTicks = 40;
        openExitSearch = null;
        followingOpenExit = false;
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        status = "Exit blocked; retrying saved tunnel and safe alternatives";
    }

    void continueProgressiveSearch(Minecraft mc, boolean solved) {
        snowRecoveryTarget = null;
        removePersistedTrail();
        route.clear();
        walkingSearch = null;
        continuationSearch = null;
        extendedFrom = null;
        excavationSearch = null;
        excavationRoute.clear();
        approachPlan = null;
        approachAnchor = null;
        approachReady = false;
        clearChestPitSearch();
        roomExitCandidates = null;
        activeRoomExit = null;
        roomExitPlanning = false;
        roomExitNeedsResume = false;
        roomExitCrossing = false;
        exitRecoveryTicks = 0;
        openExitSearch = null;
        followingOpenExit = false;
        blockedWalkNodes.clear();
        digging = false;
        returnDigging = false;
        trail.excavating = false;
        entranceSelected = false;
        seekingDryEntrance = false;
        returnAvailable = false;
        stalled = 0;
        stallRecoveries = 0;
        lastDistance = Double.MAX_VALUE;
        target = null;
        knownStructure = null;
        trail.points.clear();
        trail.target = null;
        returning = false;
        resumeSearchAfterReturn = false;
        returnJoinSearch = null;
        returnTrailRoute = null;
        roomEntryCandidates = null;
        activeRoomEntry = null;
        roomEntryPlanning = false;
        roomEntryCrossing = false;
        roomEntrySearchNeedsResume = false;
        walkingSearchNeedsResume = false;
        rejoiningSavedTrail = false;
        search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            mc.thePlayer.rotationYaw,
            LootGameSolverConfig.explorationSearchLimit,
            128);
        int[] first = search.next();
        if (first != null) {
            BaritoneNavigationBridge.Start started = baritoneNavigation.startHorizontal(first[0], first[1]);
            if (started == BaritoneNavigationBridge.Start.STARTED) {
                baritoneCoordinator.searchX = first[0];
                baritoneCoordinator.searchZ = first[1];
                baritoneCoordinator.searchTicks = 0;
                baritoneCoordinator.searchHandoff = false;
                baritoneCoordinator.searchMove = true;
                searching = true;
                walking = true;
                status = solved ? "Puzzle solved; searching for the next structure" : "Searching for structures";
                return;
            }
            if (started != BaritoneNavigationBridge.Start.ABSENT) {
                walking = false;
                status = "Baritone navigation unavailable or busy";
                return;
            }
        }
        walking = nextSearchLeg(mc);
        status = solved ? "Puzzle solved; searching for the next structure" : "Searching for structures";
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

    private boolean reuseSavedAccessPath(Minecraft mc) {
        return false;
    }

    private boolean beginExistingAccessSearch(Minecraft mc) {
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

    private void removePersistedTrail() {
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

    private boolean startChestPitReturn(Minecraft mc, boolean resumeSearch) {
        return chestPitNav.start(mc, this, resumeSearch);
    }

    private boolean advanceChestPitReturn(Minecraft mc) {
        return chestPitNav.advance(mc, this);
    }

    private void clearChestPitSearch() {
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

    private boolean clearRoomExitObstruction(Minecraft mc, int[] next, int x, int y, int z) {
        return roomExit.clearObstruction(mc, this, next, x, y, z);
    }

    boolean insideDungeonRoom(Minecraft mc) {
        return roomExit.isInsideDungeonRoom(mc, this);
    }

    boolean clearableExitColumn(World world, int x, int y, int z) {
        return RoomExit.clearableExitColumn(world, this, x, y, z);
    }

    private boolean planRoofEscape(Minecraft mc) {
        return roomExit.planRoofEscape(mc, this);
    }

    private boolean rerouteSavedReturn(Minecraft mc, int firstCheckpoint) {
        return returnNav.rerouteSavedReturn(mc, this, firstCheckpoint);
    }

    private boolean digTowardSavedExit(Minecraft mc) {
        return returnNav.digTowardSavedExit(mc, this);
    }

    private boolean startReturnDig(Minecraft mc, List<int[]> savedRoute) {
        return returnNav.startReturnDig(mc, this, savedRoute);
    }

    private boolean planReturnDigToAnchor(Minecraft mc, int anchorIndex) {
        return returnNav.planReturnDigToAnchor(mc, this, anchorIndex);
    }

    private boolean tryNextReturnDigAnchor(Minecraft mc) {
        return returnNav.tryNextReturnDigAnchor(mc, this);
    }

    private void resumeSavedReturnAfterDig() {
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
            camera.render(mc, world, true);
            return;
        }
        if (baritoneCoordinator.active()) {
            double vx = mc.thePlayer.motionX, vz = mc.thePlayer.motionZ;
            Float heading = NavigationSteering
                .walkingHeading(vx, vz, mc.thePlayer.rotationYaw, baritoneCoordinator.routePreview);
            if (heading != null) {
                camera.look(heading, 0F);
                camera.render(mc, world, true);
            }
        } else camera.render(mc, world, true);
    }

    public void look(float yaw, float pitch) {
        look(yaw, pitch, false);
    }

    public void look(float yaw, float pitch, boolean force) {
        camera.look(yaw, pitch, force);
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
        if (playerY > target.yCoord + 5) {
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
        if (walking) stop(mc, "Reward collection started");
        if (mc == null || mc.theWorld == null || center == null) return;
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

    private float searchYaw;

    public void rotateSearch(Minecraft mc) {
        searchYaw = (searchYaw + 45.0F) % 360.0F;
        int px = mc != null && mc.thePlayer != null ? (int) Math.floor(mc.thePlayer.posX) : 0;
        int pz = mc != null && mc.thePlayer != null ? (int) Math.floor(mc.thePlayer.posZ) : 0;
        search = new ProgressiveSearch(px, pz, searchYaw, LootGameSolverConfig.explorationSearchLimit, 128);
    }

    public boolean nextSearchGoal(Minecraft mc) {
        if (search == null) rotateSearch(mc);
        int[] next = search.next();
        if (next == null) {
            rotateSearch(mc);
            next = search.next();
        }
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
                return true;
            }
        }
        return nextSearchLeg(mc);
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
        // Preserve the saved entrance while inside so a restart resumes the current route.
        int playerX = (int) Math.floor(mc.thePlayer.posX);
        int playerY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int playerZ = (int) Math.floor(mc.thePlayer.posZ);
        int nearbyTrailIndex = trail.points.nearestIndex(playerX, playerY, playerZ, 24);
        // Reuse a persisted trail only while near one of its checkpoints.
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
                movingToPuzzleCenter = false;
                if (puzzleMasterTarget != null) {
                    markStructureSolved(
                        mc,
                        new int[] { puzzleMasterTarget.xCoord, puzzleMasterTarget.yCoord, puzzleMasterTarget.zCoord });
                }
                puzzleMasterTarget = null;
                rotateSearch(mc);
                walking = nextSearchLeg(mc);
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

    public void tick(Minecraft mc, boolean solverActive) {
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
        if (baritoneCoordinator.tick(mc, this)) return;
        if (!digging && !returnDigging) releaseMiningInput(mc);
        camera.clear();
        if (walking && mc.thePlayer != null && navigationInput == null) {
            navigationInput = new NavigationInput(mc.thePlayer.movementInput);
            mc.thePlayer.movementInput = navigationInput;
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
        if (++ticks % 100 == 0 && !walking) scan(mc);
        if (walking && searching && ticks % 40 == 0) {
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
        // Open the game, move to the floor center, and reveal the board on arrival.
        if (!returning && !searching && !movingToPuzzleCenter && target != null && reachableBoard(mc)) {
            if (mc.thePlayer.onGround && trail.target != null) recordAccessPoint(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
            returnAvailable = trail.target != null && trail.target[0] == target.xCoord
                && trail.target[1] == target.yCoord
                && trail.target[2] == target.zCoord
                && (!trail.points
                    .reverse(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ))
                    .isEmpty()
                    || trail.points.nearestIndex(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ),
                        32) >= 0);
            int[] arrivedAt = { target.xCoord, target.yCoord, target.zCoord };
            stop(mc, "Arrived at LootGame; opening puzzle");
            knownStructure = arrivedAt;
            puzzleArrivalPending = true;
            return;
        }
        if (!returning && (target == null || target.isInvalid())) {
            stop(mc, "Target no longer loaded");
            return;
        }
        if (!returning && !searching
            && !movingToPuzzleCenter
            && !roomEntryPlanning
            && !roomEntryCrossing
            && roomEntryCandidates == null
            && digging
            && trail.points.size() <= 1
            && target != null
            && knownStructure != null
            && Math.hypot(mc.thePlayer.posX - target.xCoord, mc.thePlayer.posZ - target.zCoord) <= 32
            && startRoomEntrySearch(mc)) return;
        if (planningExistingAccess && existingAccessPlan != null) {
            boolean complete = existingAccessPlan.advance(
                (x, y, z, nx, ny, nz) -> existingWalkCost(mc.theWorld, x, y, z, nx, ny, nz),
                32,
                System.nanoTime() + 2_500_000L);
            status = "Checking existing route: " + existingAccessPlan.expandedNodes() + " positions";
            if (complete) {
                List<int[]> existing = existingAccessPlan.path;
                existingAccessPlan = null;
                planningExistingAccess = false;
                if (!existing.isEmpty()) {
                    route.clear();
                    route.addAll(existing);
                    digging = false;
                    status = "Following safe existing entrance";
                } else {
                    plan(mc);
                    if (walkingSearch == null && route.isEmpty()) {
                        if (!selectDigEntrance(mc)) stop(mc, "No safe surface dig entrance in range");
                        else status = digging ? "No open route; planning a safe dig entrance"
                            : "Walking to the selected dig entrance";
                    } else status = "Walking to the nearest safe entrance";
                }
            }
            return;
        }
        if (returnJoinSearch != null) {
            if (!returnJoinSearch.advance(3_000_000L)) {
                status = "Finding a safe link to saved access route";
                return;
            }
            List<int[]> join = returnJoinSearch.path();
            boolean reachedJoin = returnJoinSearch.reachedGoal();
            returnJoinSearch = null;
            if (roomExitPlanning) {
                roomExitPlanning = false;
                if (reachedJoin) {
                    route.clear();
                    route.addAll(join);
                    if (route.isEmpty()) route.add(
                        new int[] { (int) Math.floor(mc.thePlayer.posX),
                            (int) Math.floor(mc.thePlayer.boundingBox.minY), (int) Math.floor(mc.thePlayer.posZ) });
                    appendRoomExitWaypoint(activeRoomExit, 0);
                    if (activeRoomExit.length >= 12) appendRoomExitWaypoint(activeRoomExit, 9);
                    if (activeRoomExit.length >= 9) appendRoomExitWaypoint(activeRoomExit, 6);
                    appendRoomExitWaypoint(activeRoomExit, 3);
                    roomExitCrossing = true;
                    roomExitJumpRetries = 0;
                    roomExitNeedsResume = false;
                    status = "Walking to room exit";
                } else if (!join.isEmpty()) {
                    route.clear();
                    route.addAll(join);
                    roomExitNeedsResume = true;
                    status = "Following exit route";
                } else {
                    roomExitCandidateIndex++;
                    if (startNextRoomExitSearch(mc)) return;
                    if (digTowardSavedExit(mc)) return;
                    retryExitLater(mc);
                    return;
                }
            } else if (returnTrailRoute != null && reachedJoin) {
                route.addAll(join);
                route.addAll(returnTrailRoute);
                returnTrailRoute = null;
                rejoiningSavedTrail = false;
            } else if (returnTrailRoute != null && !join.isEmpty()) {
                // Walk to the partial-search checkpoint, then continue searching there.
                route.addAll(join);
                status = "Following safe checkpoint toward the saved exit";
            } else if (returnTrailRoute != null) {
                if (digTowardSavedExit(mc)) return;
                retryExitLater(mc);
                return;
            } else {
                route.addAll(join);
            }
            if (route.isEmpty()) {
                if (rejoiningSavedTrail && returnTrailRoute != null) {
                    returnJoinSearch = new GroundPathfinder.Search(
                        walkingTerrain(mc.theWorld),
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ),
                        returnJoinX,
                        returnJoinY,
                        returnJoinZ,
                        Math.max(16, LootGameSolverConfig.explorationRange));
                    return;
                }
                int exitX = (int) Math.floor(mc.thePlayer.posX);
                int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int exitZ = (int) Math.floor(mc.thePlayer.posZ);
                if (mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) continueProgressiveSearch(mc, true);
                else retryExitLater(mc);
            } else status = "Following saved access route out";
            return;
        }
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
            if (!walkingSearch.advance(3_000_000L)) {
                List<int[]> partial = walkingSearch.path();
                if (partial.isEmpty()) {
                    status = "Continuing route search";
                    return;
                }
                route.clear();
                route.addAll(partial);
                walkingSearch = null;
                if (roomEntryPlanning) {
                    roomEntryPlanning = false;
                    roomEntrySearchNeedsResume = true;
                } else if (!movingToPuzzleCenter) {
                    walkingSearchNeedsResume = true;
                }
                segmentX = mc.thePlayer.posX;
                segmentZ = mc.thePlayer.posZ;
                status = "Following safe checkpoint";
            } else {
                if (roomEntryPlanning) {
                    boolean reachedEntry = walkingSearch.reachedGoal();
                    List<int[]> entryPath = walkingSearch.path();
                    walkingSearch = null;
                    roomEntryPlanning = false;
                    roomEntrySearchNeedsResume = false;
                    if (reachedEntry) {
                        route.clear();
                        route.addAll(entryPath);
                        appendRoomExitWaypoint(activeRoomEntry, 3);
                        appendRoomExitWaypoint(activeRoomEntry, 6);
                        appendRoomExitWaypoint(activeRoomEntry, 0);
                        roomEntryCrossing = true;
                        segmentX = mc.thePlayer.posX;
                        segmentZ = mc.thePlayer.posZ;
                        status = "Walking through room entrance";
                    } else {
                        roomEntryCandidateIndex++;
                        if (startNextRoomEntrySearch(mc)) return;
                        roomEntryCandidates = null;
                        stop(mc, "No walkable room entrance");
                        return;
                    }
                } else {
                    boolean reachedCenter = movingToPuzzleCenter && walkingSearch.reachedGoal();
                    boolean reachedGoal = walkingSearch.reachedGoal();
                    route.addAll(walkingSearch.path());
                    walkingSearch = null;
                    walkingSearchNeedsResume = !movingToPuzzleCenter && !reachedGoal && !route.isEmpty();
                    if (movingToPuzzleCenter && reachedCenter && target != null) puzzleCenter
                        .appendPuzzleCenterWaypoint(this, new int[] { target.xCoord, target.yCoord, target.zCoord });
                    walkingPlanFailed = !reachedGoal;
                }
            }
        }
        if (!returning && !digging
            && !seekingDryEntrance
            && !movingToPuzzleCenter
            && !roomEntryPlanning
            && !roomEntrySearchNeedsResume
            && !roomEntryCrossing
            && route.size() <= 48) extendRoute(mc);
        prepareApproach(mc);
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
        if (route.isEmpty()) {
            if (continuationSearch != null && !returning && !digging && !movingToPuzzleCenter) {
                extendRoute(mc);
                if (!route.isEmpty()) return;
                if (continuationSearch != null) {
                    status = "Planning next route";
                    return;
                }
            }
            if (followingOpenExit) {
                followingOpenExit = false;
                int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int z = (int) Math.floor(mc.thePlayer.posZ);
                if (mc.theWorld.canBlockSeeTheSky(x, y, z)) continueProgressiveSearch(mc, true);
                else if (!startSavedReturn(mc, true, 128)) retryExitLater(mc);
                return;
            }
            if (movingToPuzzleCenter) {
                reachPuzzleCenter(mc);
                return;
            }
            if (roomEntryCrossing) {
                roomEntryCrossing = false;
                activeRoomEntry = null;
                if (!insideDungeonRoom(mc)) {
                    roomEntryCandidateIndex++;
                    if (startNextRoomEntrySearch(mc)) {
                        status = "Room entrance blocked; checking the next wall opening";
                        return;
                    }
                    roomEntryCandidates = null;
                    stop(mc, "Room entrance did not cross the wall");
                    return;
                }
                roomEntryCandidates = null;
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) {
                    status = "Following room route";
                    return;
                }
                if (reachableBoard(mc)) {
                    stop(mc, "Arrived at LootGame; opening puzzle");
                    puzzleArrivalPending = true;
                    return;
                }
                stop(mc, "No safe route to room center");
                return;
            }
            if (!returning && roomEntrySearchNeedsResume && activeRoomEntry != null) {
                roomEntrySearchNeedsResume = false;
                roomEntryPlanning = true;
                walkingSearch = new GroundPathfinder.Search(
                    walkingTerrain(mc.theWorld),
                    (int) Math.floor(mc.thePlayer.posX),
                    (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ),
                    activeRoomEntry[3],
                    activeRoomEntry[5],
                    LootGameSolverConfig.explorationRange);
                status = "Finding room entrance";
                return;
            }
            if (!returning && walkingSearchNeedsResume) {
                walkingSearchNeedsResume = false;
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) return;
            }
            if (returnDigging) {
                int horizontal = Math.abs((int) Math.floor(mc.thePlayer.posX) - target.xCoord)
                    + Math.abs((int) Math.floor(mc.thePlayer.posZ) - target.zCoord);
                int vertical = Math.abs((int) Math.floor(mc.thePlayer.boundingBox.minY) - target.yCoord);
                if (horizontal <= 3 && vertical <= 1) {
                    resumeSavedReturnAfterDig();
                    return;
                }
                if (!stairStep(mc)) {
                    if (tryNextReturnDigAnchor(mc)) return;
                    retryExitLater(mc);
                }
                return;
            }
            if (returning) {
                if (roomExitNeedsResume && activeRoomExit != null) {
                    roomExitNeedsResume = false;
                    returnJoinSearch = new GroundPathfinder.Search(
                        walkingTerrain(mc.theWorld),
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ),
                        activeRoomExit[0],
                        activeRoomExit[1],
                        activeRoomExit[2],
                        LootGameSolverConfig.explorationRange);
                    roomExitPlanning = true;
                    status = "Continuing exit route";
                    return;
                }
                if (roomExitCrossing) {
                    roomExitCrossing = false;
                    if (insideDungeonRoom(mc)) {
                        roomExitCandidateIndex++;
                        if (startNextRoomExitSearch(mc)) return;
                        if (digTowardSavedExit(mc)) return;
                        retryExitLater(mc);
                        return;
                    }
                    roomExitCandidates = null;
                    activeRoomExit = null;
                    int ex = (int) Math.floor(mc.thePlayer.posX);
                    int ey = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                    int ez = (int) Math.floor(mc.thePlayer.posZ);
                    if (mc.theWorld.canBlockSeeTheSky(ex, ey, ez)) continueProgressiveSearch(mc, true);
                    else if (!startSavedReturn(mc, true, 128) && !digTowardSavedExit(mc)) retryExitLater(mc);
                    return;
                }
                // Continue a partial link search before considering room walls.
                if (ReturnRoutePolicy.shouldContinueSavedTrailJoin(rejoiningSavedTrail, returnTrailRoute)) {
                    returnJoinSearch = new GroundPathfinder.Search(
                        walkingTerrain(mc.theWorld),
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ),
                        returnJoinX,
                        returnJoinY,
                        returnJoinZ,
                        Math.max(16, LootGameSolverConfig.explorationRange));
                    status = "Continuing route to saved tunnel";
                    return;
                }
                if (insideDungeonRoom(mc)) {
                    // Keep the tried-doorway list to prevent repeating failed routes.
                    if (roomExitCandidates == null && startRoomExitSearch(mc)) return;
                    if (digTowardSavedExit(mc)) return;
                    retryExitLater(mc);
                    return;
                }
                int exitX = (int) Math.floor(mc.thePlayer.posX);
                int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int exitZ = (int) Math.floor(mc.thePlayer.posZ);
                if (!mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) {
                    blockedWalkNodes.clear();
                    if (startSavedReturn(mc, resumeSearchAfterReturn, 128) || digTowardSavedExit(mc)) return;
                    retryExitLater(mc);
                    return;
                }
                returning = false;
                returnAvailable = false;
                removePersistedTrail();
                trail.excavating = false;
                if (resumeSearchAfterReturn) {
                    continueProgressiveSearch(mc, true);
                } else {
                    trail.points.clear();
                    trail.target = null;
                    stop(mc, "Returned to access entrance");
                }
                return;
            }
            if (!searching && !movingToPuzzleCenter && insideDungeonRoom(mc)) {
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) {
                    status = "Following room route";
                    return;
                }
                if (reachableBoard(mc)) {
                    stop(mc, "Arrived at LootGame; opening puzzle");
                    puzzleArrivalPending = true;
                } else stop(mc, "No safe route to room center");
                return;
            }
            if (seekingDryEntrance) {
                seekingDryEntrance = false;
                digging = true;
                entranceSelected = true;
                savedSlot = mc.thePlayer.inventory.currentItem;
            }
            if (!entranceSelected
                && (digging || (!searching && target.yCoord < mc.thePlayer.boundingBox.minY - 2
                    && Math.abs(mc.thePlayer.posX - target.xCoord) + Math.abs(mc.thePlayer.posZ - target.zCoord) <= 32))
                && !dryShore(
                    mc.theWorld,
                    (int) Math.floor(mc.thePlayer.posX),
                    (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ))) {
                digging = false;
                if (!findDryEntrance(mc)) stop(mc, "No verified dry entrance near the water; digging stopped");
                return;
            }
            if (!searching && !digging) {
                double horizontal = Math.abs(mc.thePlayer.posX - target.xCoord)
                    + Math.abs(mc.thePlayer.posZ - target.zCoord);
                if (horizontal > 32 || (horizontal > 3 && target.yCoord >= mc.thePlayer.boundingBox.minY - 2)) {
                    if (!walkingPlanFailed) plan(mc);
                    if (!route.isEmpty() || walkingSearch != null) return;
                    if (horizontal <= 40 && beginDiggingAtCurrentPosition(mc)) return;
                    if (startRoomEntrySearch(mc)) return;
                    if (beginExistingAccessSearch(mc)) return;
                    if (LootGameSolverConfig.staircaseEnabled && horizontal <= 40 && !mc.thePlayer.isInWater()) {
                        if (!selectDigEntrance(mc)) stop(mc, "No safe surface dig entrance in range");
                    } else stop(mc, "No verified route around the obstruction");
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled
                    && Math.abs(target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1) {
                    if (startRoomEntrySearch(mc)) return;
                    if (beginExistingAccessSearch(mc)) return;
                    if (!selectDigEntrance(mc)) stop(mc, "No safe surface dig entrance in range");
                    return;
                }
            }
            if (searching) {
                scan(mc);
                if (target != null) {
                    beginAccessTrail(mc);
                    searching = false;
                    if (mc.thePlayer.getDistanceSq(target.xCoord + 0.5D, target.yCoord + 0.5D, target.zCoord + 0.5D)
                        <= 16D) {
                        stop(mc, "Found nearby LootGame");
                        puzzleArrivalPending = true;
                        return;
                    }
                    plan(mc);
                    if (!route.isEmpty() || walkingSearch != null) return;
                    if (LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2
                        && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
                        if (startRoomEntrySearch(mc)) return;
                        if (!selectDigEntrance(mc) && !nextSearchLeg(mc))
                            stop(mc, "No safe dig entrance near the structure");
                        return;
                    }
                }
                if (!nextSearchLeg(mc)) {
                    rotateSearch(mc);
                    nextSearchLeg(mc);
                }
                return;
            }
            if (digging && (Math.abs(target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1
                || Math.abs(mc.thePlayer.posX - target.xCoord) + Math.abs(mc.thePlayer.posZ - target.zCoord) > 3)) {
                if (!stairStep(mc)) {
                    if (stairSteps == 0 && ++approachAttempts <= 3) {
                        excavationSearch = null;
                        excavationRoute.clear();
                        miningAim.reset();
                        digging = false;
                        entranceSelected = false;
                        if (findDryEntrance(mc)) {
                            status = "Checking a different supported entrance";
                            return;
                        }
                    }
                    stop(mc, "Access route blocked; keeping the existing entrance for reuse");
                }
                return;
            }
            if (digging) {
                digging = false;
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) return;
            }
            stop(mc, "Arrived beside LootGame");
            puzzleArrivalPending = true;
            return;
        }
        if (!digging && !returning
            && !roomExitCrossing
            && !roomEntryCrossing
            && route.size() >= 3
            && mc.thePlayer.onGround) {
            int cx = (int) Math.floor(mc.thePlayer.posX), cy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int cz = (int) Math.floor(mc.thePlayer.posZ);
            for (int i = Math.min(route.size() - 1, 8); i >= 2; i--) {
                int[] step = route.get(i);
                int dx = Math.abs(step[0] - cx), dz = Math.abs(step[2] - cz);
                if (step.length > 3 && step[3] != 0 || step[1] != cy
                    || dx > 1
                    || dz > 1
                    || dx + dz == 0
                    || !safe(mc.theWorld, step[0], cy, step[2])
                    || dx == 1 && dz == 1
                        && (!safe(mc.theWorld, step[0], cy, cz) || !safe(mc.theWorld, cx, cy, step[2])))
                    continue;
                boolean special = false;
                for (int j = 0; j < i; j++) if (route.get(j).length > 3 && route.get(j)[3] != 0) special = true;
                if (special) continue;
                route.subList(0, i)
                    .clear();
                break;
            }
        }
        if (!digging && !roomExitCrossing && !roomEntryCrossing && route.size() >= 2) {
            int[] first = route.get(0), second = route.get(1);
            int cx = (int) Math.floor(mc.thePlayer.posX), cy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int cz = (int) Math.floor(mc.thePlayer.posZ);
            if ((first.length < 4 || first[3] == 0) && (second.length < 4 || second[3] == 0)
                && first[1] == cy
                && second[1] == cy
                && Math.abs(second[0] - cx) == 1
                && Math.abs(second[2] - cz) == 1
                && safe(mc.theWorld, second[0], cy, cz)
                && safe(mc.theWorld, cx, cy, second[2])
                && safe(mc.theWorld, second[0], cy, second[2])) {
                route.remove(0);
            }
        }
        int[] next = route.get(0);
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (returning && roomExitCrossing
            && mc.thePlayer.onGround
            && Math.abs(next[0] - px) + Math.abs(next[2] - pz) <= 1
            && clearRoomExitObstruction(mc, next, px, py, pz)) return;
        if (!returning && trail.target != null && mc.thePlayer.onGround) recordAccessPoint(px, py, pz);
        if (digging && mc.thePlayer.onGround && Math.abs(next[0] - px) + Math.abs(next[2] - pz) <= 1) {
            for (int by = py; by <= py + 1; by++) {
                net.minecraft.block.Block plant = mc.theWorld.getBlock(next[0], by, next[2]);
                if (plant == net.minecraft.init.Blocks.snow_layer && mc.theWorld.getBlockMetadata(next[0], by, next[2]) <= 1) continue;
                if (FoliagePolicy.canClear(
                    isFoliage(plant),
                    mc.theWorld.getTileEntity(next[0], by, next[2]) != null,
                    plant.getBlockHardness(mc.theWorld, next[0], by, next[2]),
                    next[0],
                    by,
                    next[2],
                    px,
                    py,
                    pz,
                    next[0],
                    next[2],
                    plant == Blocks.snow_layer)) {
                    minePlannedBlock(mc, next[0], by, next[2], next[1]);
                    return;
                }
            }
        }
        boolean diagonal = next[0] != px && next[2] != pz && next[1] == py;
        if (diagonal && (!safe(mc.theWorld, next[0], py, pz) || !safe(mc.theWorld, px, py, next[2]))) {
            if (returning) {
                if (rerouteSavedReturn(mc, 1)) return;
                if (digTowardSavedExit(mc)) return;
                retryExitLater(mc);
                return;
            }
            if (!digging) plan(mc);
            else stop(mc, "Diagonal clearance changed");
            return;
        }
        if (!safe(mc.theWorld, next[0], next[1], next[2]) || Math.abs(py - next[1]) > (mc.thePlayer.isInWater() ? 2 : 1)

        ) {
            if (returning) {
                if (next[1] > py && tryPlaceReturnStep(mc, next)) return;
                if (rerouteSavedReturn(mc, 1)) return;
                if (digTowardSavedExit(mc)) return;
                retryExitLater(mc);
                return;
            }
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (!digging && mc.thePlayer.onGround && safe(mc.theWorld, px, py, pz)) {
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) {
                    lastDistance = Double.MAX_VALUE;
                    stalled = 0;
                    status = "Rerouted around obstruction";
                    return;
                }
            }
            stop(mc, "Route obstructed or unsafe");
            return;
        }
        double dx = next[0] + 0.5D - mc.thePlayer.posX;
        double dz = next[2] + 0.5D - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean centering = next.length > 3 && next[3] == 2;
        boolean passed = !centering && py == next[1]
            && NavigationSteering
                .passed(mc.thePlayer.posX, mc.thePlayer.posZ, segmentX, segmentZ, next[0] + 0.5D, next[2] + 0.5D);
        if ((!digging || mc.thePlayer.onGround) && (passed
            || NavigationSteering.reached(distance, py, next[1], mc.thePlayer.isInWater(), route.size() > 1))) {
            segmentX = next[0] + 0.5D;
            segmentZ = next[2] + 0.5D;
            if (!returning && trail.target != null) recordAccessPoint(px, py, pz);
            route.remove(0);
            if (roomExitCrossing) roomExitJumpRetries = 0;
            if (returning && !insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(px, py, pz)) {
                continueProgressiveSearch(mc, true);
                return;
            }
            if (!returning && !digging
                && approachReady
                && approachPlan != null
                && approachAnchor == next
                && !approachPlan.path.isEmpty()) {
                route.clear();
                excavationRoute.clear();
                excavationRoute.addAll(approachPlan.path);
                approachPlan = null;
                approachAnchor = null;
                approachReady = false;
                digging = true;
                entranceSelected = true;
                savedSlot = mc.thePlayer.inventory.currentItem;
                status = "Following planned excavation from this checkpoint";
                return;
            }
            lastDistance = Double.MAX_VALUE;
            stalled = 0;
            stallRecoveries = 0;
            if (route.isEmpty()) {
                if (mc.thePlayer.onGround && !digging) {
                    mc.thePlayer.motionX *= 0.3D;
                    mc.thePlayer.motionZ *= 0.3D;
                }
                return;
            }
            next = route.get(0);
            centering = next.length > 3 && next[3] == 2;
            if (!safe(mc.theWorld, next[0], next[1], next[2])
                || Math.abs(py - next[1]) > (mc.thePlayer.isInWater() ? 2 : 1)) {
                if (returning && next[1] > py && tryPlaceReturnStep(mc, next)) return;
                if (returning && rerouteSavedReturn(mc, 1)) return;
                if (returning && digTowardSavedExit(mc)) return;
                stop(mc, "Next route segment is no longer safe");
                return;
            }
            dx = next[0] + 0.5D - mc.thePlayer.posX;
            dz = next[2] + 0.5D - mc.thePlayer.posZ;
            distance = Math.sqrt(dx * dx + dz * dz);
        }
        if (digging && distance < 0.2D && !mc.thePlayer.onGround) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (++settlingTicks > 40) stop(mc, "Stair landing did not complete");
            return;
        }
        settlingTicks = 0;
        float desired = centering ? mc.thePlayer.rotationYaw
            : NavigationSteering.heading(
                mc.thePlayer.posX,
                mc.thePlayer.posZ,
                next[0] + 0.5D,
                next[2] + 0.5D,
                mc.thePlayer.rotationYaw);
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(desired - mc.thePlayer.rotationYaw);
        int upcomingPitch = staircaseDirection(route, py);
        if (upcomingPitch != 0) {
            int[] firstStep = null;
            for (int[] step : route) if (step[1] != py) {
                firstStep = step;
                break;
            }
            if (firstStep != null
                && Math.hypot(firstStep[0] + 0.5D - mc.thePlayer.posX, firstStep[2] + 0.5D - mc.thePlayer.posZ)
                    <= 1.8D) {
                if (cameraPitchDirection == 0) {
                    pitchLastY = py;
                    pitchLandingX = mc.thePlayer.posX;
                    pitchLandingZ = mc.thePlayer.posZ;
                }
                cameraPitchDirection = upcomingPitch;
            }
        }
        if (cameraPitchDirection != 0 && py != pitchLastY) {
            pitchLastY = py;
            pitchLandingX = mc.thePlayer.posX;
            pitchLandingZ = mc.thePlayer.posZ;
        }
        if (cameraPitchDirection != 0 && upcomingPitch == 0
            && mc.thePlayer.onGround
            && Math.hypot(mc.thePlayer.posX - pitchLandingX, mc.thePlayer.posZ - pitchLandingZ) >= 1D)
            cameraPitchDirection = 0;
        boolean swimming = mc.thePlayer.isInWater() || mc.theWorld.getBlock(next[0], next[1], next[2])
            .getMaterial() == net.minecraft.block.material.Material.water;
        float desiredPitch = swimming ? 0F : cameraPitchDirection > 0 ? -10F : cameraPitchDirection < 0 ? 30F : 0F;
        if (!centering) look(desired, desiredPitch);
        status = centering ? "Centering on supported ground"
            : returning ? "Returning along saved access path" : "Following checked route";
        if (!centering && Math.abs(turn) > 60F) {
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            mc.thePlayer.setSprinting(false);
            status = "Aligning with route";
            return;
        }
        if (distance >= lastDistance - 0.005D) stalled++;
        else stalled = 0;
        lastDistance = distance;
        if (stalled > 40) {
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            if (returning && roomExitCrossing
                && mc.thePlayer.onGround
                && next[1] == py + 1
                && distance < 1.8D
                && distance > 0.02D
                && safe(mc.theWorld, next[0], next[1], next[2])
                && roomExitJumpRetries++ < 3
                && navigationInput != null) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance);
                navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance);
                navigationInput.hop = true;
                status = "Climbing exit step";
                return;
            }
            if (!route.isEmpty()) {
                int[] failed = route.get(0);
                markBlockedWalkNode(failed[0], failed[1], failed[2]);
            }
            if (returning) {
                if (isRewardChestPit(mc)) {
                    routeFollower.boostChestPitJump();
                    status = "Jumping clear of the reward chests";
                    return;
                }
                if (rerouteSavedReturn(mc, 1) || digTowardSavedExit(mc)) {
                    status = "Recovering along the saved exit route";
                    return;
                }
                retryExitLater(mc);
                return;
            }
            if (!route.isEmpty()) {
                int[] failed = route.get(0);
                markBlockedWalkNode(failed[0], failed[1], failed[2]);
            }
            if (++stallRecoveries <= 5) {
                route.clear();
                walkingSearch = null;
                continuationSearch = null;
                extendedFrom = null;
                if (searching) {
                    if (nextSearchLeg(mc)) {
                        status = "Stalled checkpoint skipped; trying another exploration leg";
                        return;
                    }
                } else {
                    if (digging) {
                        excavationRoute.clear();
                        excavationSearch = null;
                        status = "Planning another route from the tunnel";
                        return;
                    }
                    if (beginExistingAccessSearch(mc)) return;
                    plan(mc);
                    if (!route.isEmpty() || walkingSearch != null) {
                        status = "Stalled checkpoint avoided; following alternate route";
                        return;
                    }
                    if (LootGameSolverConfig.staircaseEnabled && !mc.thePlayer.isInWater() && selectDigEntrance(mc)) {
                        status = "Walking route blocked; checking a safe dig entrance";
                        return;
                    }
                }
            }
            rotateSearch(mc);
            nextSearchLeg(mc);
            return;
        }
        routeFollower.follow(
            mc,
            navigationInput,
            route,
            next,
            py,
            turn,
            distance,
            dx,
            dz,
            centering,
            movingToPuzzleCenter,
            digging,
            returning);
    }

    private boolean jumpOutOfRewardPit(Minecraft mc) {
        if (navigationInput == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int nearest = trail.points.nearestIndex(x, y, z, 128);
        int[] goal = nearest < 0 ? null : trail.points.pointAt(nearest);
        int[] best = RewardChestGeometry.exitDirection(mc.theWorld, x, y, z, goal);
        if (best == null) return false;
        float yaw = NavigationSteering.heading(
            mc.thePlayer.posX,
            mc.thePlayer.posZ,
            x + best[0] + 0.5D,
            z + best[1] + 0.5D,
            mc.thePlayer.rotationYaw);
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        look(yaw, -10F);
        if (Math.abs(turn) < 35F) {
            navigationInput.forward = 1F;
            navigationInput.hop = mc.thePlayer.onGround;
            escapingChestPit = true;
        }
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        status = "Jumping clear of reward chests";
        return true;
    }

    private boolean tryPlaceReturnStep(Minecraft mc, int[] next) {
        boolean active = blockPlacer.tryPlaceReturnStep(mc, next, returning);
        if (blockPlacer.status() != null) status = blockPlacer.status();
        return active;
    }

    private boolean recoverableRouteFailure(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("no safe") || text.contains("no verified")
            || text.contains("blocked")
            || text.contains("obstructed")
            || text.contains("stalled")
            || text.contains("route")
            || text.contains("search limit")
            || text.contains("landing did not")
            || text.contains("no walkable")
            || text.contains("entrance did not cross")
            || text.contains("target no longer loaded")
            || text.contains("could not place a safe return step")
            || text.contains("could not reach")
            || text.contains("center")
            || text.contains("no safe surface")
            || text.contains("replanning");
    }

    private void queueRouteRetry(Minecraft mc, String message) {
        if (mc.thePlayer == null) return;
        if (!route.isEmpty()) {
            int[] failed = route.get(0);
            markBlockedWalkNode(failed[0], failed[1], failed[2]);
        }
        if (navigationInput != null) {
            navigationInput.forward = 0;
            navigationInput.sideways = 0;
            navigationInput.hop = false;
        }
        mc.thePlayer.setSprinting(false);
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        miningAim.reset();
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        boolean nearEntrance = !returning && target != null
            && !target.isInvalid()
            && Math.hypot(mc.thePlayer.posX - target.xCoord, mc.thePlayer.posZ - target.zCoord) <= 6D;
        if (nearEntrance && !route.isEmpty()) {
            int[] failed = route.get(0);
            long failedKey = supportKey(failed[0], failed[1], failed[2]);
            blockedWalkNodes.remove(failedKey);
        }
        routeRetryTicks = Math.max(routeRetryTicks, nearEntrance ? 2 : 6);
        routeRetryStatus = message;
        walking = true;
        status = "Checking another safe route";
    }

    private void retryCurrentRoute(Minecraft mc, String reason) {
        routeRetryTicks = 0;
        route.clear();
        walkingSearch = null;
        continuationSearch = null;
        extendedFrom = null;
        if (returning) {
            if (startChestPitReturn(mc, true)) return;
            if (startSavedReturn(mc, true, 128)) return;
            if (startRoomExitSearch(mc)) return;
            retryExitLater(mc);
            return;
        }
        if (movingToPuzzleCenter) {
            if (++approachAttempts <= 3) {
                reachPuzzleCenter(mc);
            } else {
                approachAttempts = 0;
                movingToPuzzleCenter = false;
                if (puzzleMasterTarget != null) {
                    markStructureSolved(
                        mc,
                        new int[] { puzzleMasterTarget.xCoord, puzzleMasterTarget.yCoord, puzzleMasterTarget.zCoord });
                }
                puzzleMasterTarget = null;
                rotateSearch(mc);
                walking = nextSearchLeg(mc);
            }
            return;
        }
        if (digging) {
            excavationRoute.clear();
            excavationSearch = null;
            miningAim.reset();
            if (LootGameSolverConfig.staircaseEnabled && stairStep(mc)) {
                status = "Replanning tunnel from current position";
                return;
            }
            digging = false;
            entranceSelected = false;
        }
        if (searching && nextSearchLeg(mc)) {
            status = "Trying another exploration direction";
            return;
        }
        if (target != null && !target.isInvalid()) {
            boolean nearEntrance = Math.hypot(mc.thePlayer.posX - target.xCoord, mc.thePlayer.posZ - target.zCoord)
                <= 6D;
            if (nearEntrance && !insideDungeonRoom(mc)) {
                if (startRoomEntrySearch(mc)) {
                    status = "Finding a clear structure entrance";
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled && beginDiggingAtCurrentPosition(mc)) return;
            }
            if (beginExistingAccessSearch(mc)) {
                status = "Checking a different existing path";
                return;
            }
            plan(mc);
            if (!route.isEmpty() || walkingSearch != null) {
                status = "Following a newly checked route";
                return;
            }
            if (LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
                status = "Checking another safe approach";
                return;
            }
        }
        if (target == null || target.isInvalid()) {
            scan(mc);
            if (target != null) {
                beginAccessTrail(mc);
                if (reuseSavedAccessPath(mc) || beginExistingAccessSearch(mc)) {
                    status = "Resuming access to the nearest structure";
                    return;
                }
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) {
                    status = "Following route to the nearest structure";
                    return;
                }
            }
        }
        if (nextSearchLeg(mc)) {
            status = "Trying another exploration direction";
            return;
        }
        routeRetryStatus = reason;
        routeRetryTicks = 8;
        walking = true;
        status = "Waiting for a safe route, then retrying";
    }

    private void markBlockedWalkNode(int x, int y, int z) {
        long key = supportKey(x, y, z);
        blockedWalkNodes.remove(key);
        blockedWalkNodes.add(key);
        while (blockedWalkNodes.size() > 128) {
            java.util.Iterator<Long> oldest = blockedWalkNodes.iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    private boolean swimToNearestShore(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isInWater()) return false;
        int px = (int) Math.floor(mc.thePlayer.posX);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int[] best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int r = 1; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = px + dx, z = pz + dz;
                    int y = mc.theWorld.getHeightValue(x, z);
                    if (y <= 0) continue;
                    net.minecraft.block.Block b = mc.theWorld.getBlock(x, y - 1, z);
                    net.minecraft.block.Block above = mc.theWorld.getBlock(x, y, z);
                    net.minecraft.block.Block above2 = mc.theWorld.getBlock(x, y + 1, z);
                    if (b.isNormalCube() && !b.getMaterial()
                        .isLiquid()
                        && !above.getMaterial()
                            .isLiquid()
                        && !above2.getMaterial()
                            .isLiquid()) {
                        double dsq = (x - px) * (x - px) + (z - pz) * (z - pz);
                        if (dsq < bestDistSq) {
                            bestDistSq = dsq;
                            best = new int[] { x, y, z };
                        }
                    }
                }
            }
            if (best != null) break;
        }
        if (best != null) {
            route.clear();
            route.add(best);
            walking = true;
            status = "Swimming: Shore";
            if (navigationInput != null) {
                navigationInput.forward = 1F;
                navigationInput.hop = true;
            }
            return true;
        }
        return false;
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
        releaseMiningInput(mc);
        toolTransfer.cancel(mc);
        blockPlacer.reset(mc);
        chestPitNav.reset();
        routeRetryTicks = 0;
        routeRetryStatus = "";
        miningTarget = Long.MIN_VALUE;
        miningSlot = -1;
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
        miningAim.reset();
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
        if (digging && mc.playerController != null) mc.playerController.resetBlockRemoving();
        if (savedSlot >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        waitingForTool = false;
        digging = false;
        stairX = 0;
        stairZ = 0;
        stairSteps = 0;
        seekingDryEntrance = false;
        entranceSelected = false;
        entranceCandidates.clear();
        digTicks = 0;
        toolWaitTicks = 0;
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
        target = null;
        knownStructure = null;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        target = ExplorerStructureDiscovery
            .find(mc, savedFinder, solvedStructures, blockedStructures, worldIdentity(mc));
        if (target != null) {
            knownStructure = new int[] { target.xCoord, target.yCoord, target.zCoord };
            waypointLabelY = ExplorerStructureDiscovery.labelHeight(mc, target);
        }
    }

    private boolean nextSearchLeg(Minecraft mc) {
        for (int attempt = 0; attempt < 16; attempt++) {
            if (search == null) rotateSearch(mc);
            int[] goal = search.next();
            if (goal == null) {
                rotateSearch(mc);
                goal = search.next();
            }
            if (goal == null) continue;
            int x = (int) Math.floor(mc.thePlayer.posX), z = (int) Math.floor(mc.thePlayer.posZ);
            int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            double dx = goal[0] - x, dz = goal[1] - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 4) continue;
            searchGoalX = goal[0];
            searchGoalZ = goal[1];
            double step = Math.min(64D, length);
            target = new TileEntity();
            target.xCoord = x + (int) Math.round(dx / length * step);
            target.yCoord = y;
            target.zCoord = z + (int) Math.round(dz / length * step);
            if (!mc.theWorld.blockExists(target.xCoord, y, target.zCoord)) continue;
            plan(mc);
            searching = true;
            digging = false;
            walking = true;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            status = route.isEmpty() && walkingSearch == null ? "Checking safe direction" : "Exploring outward";
            return true;
        }
        searching = true;
        walking = true;
        status = "Waiting for loaded terrain";
        return true;
    }

    GroundPathfinder.Terrain walkingTerrain(final World terrainWorld) {
        Minecraft mc = Minecraft.getMinecraft();
        int px = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.posX);
        int py = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.posZ);
        final boolean surfaceStart = mc.thePlayer != null && terrainWorld.blockExists(px, py, pz)
            && terrainWorld.getHeightValue(px, pz) - py <= 6;
        final boolean protectedJourney = returning || digging
            || movingToPuzzleCenter
            || roomEntryPlanning
            || roomEntryCrossing
            || roomExitPlanning
            || roomExitCrossing;
        final boolean structureApproach = knownStructure != null && !searching;
        final int gx = target == null ? px : target.xCoord, gz = target == null ? pz : target.zCoord;
        return new ExplorerWalkingTerrain(
            terrainWorld,
            blockedWalkNodes,
            surfaceStart,
            protectedJourney,
            structureApproach,
            gx,
            gz,
            () -> movingToPuzzleCenter,
            () -> target);
    }

    private int existingWalkCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        if (Math.abs(nx - x) + Math.abs(nz - z) > 1 || Math.abs(ny - y) > 1) return -1;
        if (blockedWalkNodes.contains(supportKey(nx, ny, nz))) return -1;
        if (!safe(w, nx, ny, nz) || w.getBlock(nx, ny, nz)
            .getMaterial()
            .isLiquid()) return -1;
        if (ny > y && !passable(w, x, y + 2, z)) return -1;
        if (ny < y && !passable(w, nx, y + 1, nz)) return -1;
        return 10 + 10 * Math.abs(ny - y);
    }

    private void extendRoute(Minecraft mc) {
        int[] tail = route.isEmpty() ? extendedFrom : route.get(route.size() - 1);
        if (tail == null) return;
        if (tail == extendedFrom && continuationSearch == null) return;
        int gx = searching ? searchGoalX : target.xCoord;
        int gz = searching ? searchGoalZ : target.zCoord;
        if ((tail != extendedFrom || continuationSearch == null)
            && Math.abs(tail[0] - gx) + Math.abs(tail[2] - gz) <= 2) {
            if (!searching || search == null) return;
            int[] goal = search.next();
            if (goal == null) return;
            gx = goal[0];
            gz = goal[1];
        }
        if (tail != extendedFrom || continuationSearch == null) {
            extendedFrom = tail;
            continuationX = gx;
            continuationZ = gz;
            continuationSearch = new GroundPathfinder.Search(
                walkingTerrain(mc.theWorld),
                tail[0],
                tail[1],
                tail[2],
                gx,
                gz,
                LootGameSolverConfig.explorationRange);
        }
        boolean finished = continuationSearch.advance(3_000_000L);
        List<int[]> continuation = continuationSearch.path();
        if (!finished && (route.size() > 32 || continuation.size() < (route.isEmpty() ? 1 : 6))) return;
        continuationSearch = null;
        if (continuation.isEmpty()) {
            extendedFrom = null;
            return;
        }
        boolean joined = shortcutToContinuation(mc, continuation);
        if (!joined) NavigationSteering.appendWithoutLoops(route, continuation);
        walkingSearchNeedsResume = !finished;
        if (searching) {
            searchGoalX = continuationX;
            searchGoalZ = continuationZ;
            int[] end = route.get(route.size() - 1);
            target.xCoord = end[0];
            target.yCoord = end[1];
            target.zCoord = end[2];
        }
    }

    private boolean shortcutToContinuation(Minecraft mc, List<int[]> continuation) {
        if (!mc.thePlayer.onGround || route.size() < 2) return false;
        for (int[] step : route) if (step.length > 3 && step[3] != 0) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] current = route.get(0);
        double forwardX = current[0] + 0.5D - mc.thePlayer.posX;
        double forwardZ = current[2] + 0.5D - mc.thePlayer.posZ;
        for (int i = continuation.size() - 1; i >= 0; i--) {
            int[] step = continuation.get(i);
            int dx = Math.abs(step[0] - x), dz = Math.abs(step[2] - z);
            if (step[1] != y || dx > 1
                || dz > 1
                || dx + dz == 0
                || (step[0] + 0.5D - mc.thePlayer.posX) * forwardX + (step[2] + 0.5D - mc.thePlayer.posZ) * forwardZ
                    <= 0D
                || !safe(mc.theWorld, step[0], y, step[2])
                || dx == 1 && dz == 1 && (!safe(mc.theWorld, step[0], y, z) || !safe(mc.theWorld, x, y, step[2])))
                continue;
            if (route.size() + i < 3) return false;
            route.clear();
            route.addAll(continuation.subList(i, continuation.size()));
            segmentX = mc.thePlayer.posX;
            segmentZ = mc.thePlayer.posZ;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            status = "Taking shorter route";
            return true;
        }
        return false;
    }

    private void plan(final Minecraft mc) {
        walkingPlanFailed = false;
        walkingSearchNeedsResume = false;
        route.clear();
        extendedFrom = null;
        continuationSearch = null;
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        walkingSearch = new GroundPathfinder.Search(
            walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            target.xCoord,
            target.zCoord,
            LootGameSolverConfig.explorationRange);
        if (walkingSearch.advance(3_000_000L)) {
            route.addAll(walkingSearch.path());
            walkingPlanFailed = !walkingSearch.reachedGoal();
            walkingSearchNeedsResume = walkingPlanFailed && !route.isEmpty();
            walkingSearch = null;
        }
    }

    private boolean beginDiggingAtCurrentPosition(Minecraft mc) {
        return excavationPlanner.beginDiggingAtCurrentPosition(mc, this);
    }

    public boolean beginDiggingToSurface(Minecraft mc) {
        return excavationPlanner.beginDiggingToSurface(mc, this);
    }

    private List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
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
        World w = mc.theWorld;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        net.minecraft.block.Block b = w.getBlock(nx, by, nz);
        int mineX = nx, mineY = by, mineZ = nz;
        net.minecraft.util.Vec3 aimPoint = visibleMiningPoint(mc, mineX, mineY, mineZ);
        net.minecraft.util.MovingObjectPosition obstruction = w.rayTraceBlocks(
            net.minecraft.util.Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ),
            net.minecraft.util.Vec3.createVectorHelper(nx + 0.5D, by + 0.5D, nz + 0.5D));
        if (obstruction != null
            && obstruction.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            net.minecraft.block.Block visible = w.getBlock(obstruction.blockX, obstruction.blockY, obstruction.blockZ);
            boolean plant = isFoliage(visible);
            boolean fallingObstruction = b instanceof net.minecraft.block.BlockFalling && obstruction.blockX == nx
                && obstruction.blockZ == nz
                && obstruction.blockY > by
                && obstruction.blockY <= y + 2
                && visible instanceof net.minecraft.block.BlockFalling
                && excavationTerrain.excavatable(w, obstruction.blockX, obstruction.blockY, obstruction.blockZ);
            if (aimPoint == null && (fallingObstruction || FoliagePolicy.canClear(
                plant,
                w.getTileEntity(obstruction.blockX, obstruction.blockY, obstruction.blockZ) != null,
                visible.getBlockHardness(w, obstruction.blockX, obstruction.blockY, obstruction.blockZ),
                obstruction.blockX,
                obstruction.blockY,
                obstruction.blockZ,
                x,
                y,
                z,
                nx,
                nz,
                visible == Blocks.snow_layer))) {
                mineX = obstruction.blockX;
                mineY = obstruction.blockY;
                mineZ = obstruction.blockZ;
                b = visible;
                aimPoint = visibleMiningPoint(mc, mineX, mineY, mineZ);
            }
        }
        int slot = DigToolSelector.equip(mc, b, w.getBlockMetadata(mineX, mineY, mineZ));
        if (slot >= 9) {
            if (toolTransfer.start(mc, slot)) {
                status = "Opening inventory to prepare tool";
                return true;
            }
            if (++toolWaitTicks > 40) {
                stop(
                    mc,
                    "\u00A7eAuto-dig paused: inventory swap could not finish. Close other inventories and clear the cursor.");
                return true;
            }
            status = "Preparing digging tool...";
            return true;
        }
        toolWaitTicks = 0;
        if (slot < 0) {
            waitingForTool = true;
            miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
            mc.playerController.resetBlockRemoving();
            digTicks = 0;
            miningAim.reset();
            status = "Mining paused: no usable tool for " + b.getLocalizedName();
            return true;
        }
        waitingForTool = false;
        long targetKey = supportKey(mineX, mineY, mineZ);
        if (targetKey != miningTarget || slot != miningSlot) {
            miningTarget = targetKey;
            miningSlot = slot;
            digTicks = 0;
            aimTicks = 0;
        }
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;
        mc.thePlayer.inventory.currentItem = slot;
        mc.playerController.updateController();
        if (aimPoint == null) {
            miningAim.reset();
            mc.playerController.resetBlockRemoving();
            status = "Finding visible block face";
            return false;
        }
        double aimX = aimPoint.xCoord - mc.thePlayer.posX;
        double aimY = aimPoint.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double aimZ = aimPoint.zCoord - mc.thePlayer.posZ;
        float yaw = (float) (Math.atan2(aimZ, aimX) * 180D / Math.PI) - 90F;
        float pitch = (float) (-Math.atan2(aimY, Math.sqrt(aimX * aimX + aimZ * aimZ)) * 180D / Math.PI);
        look(yaw, pitch);
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        boolean matches = hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == mineX
            && hit.blockY == mineY
            && hit.blockZ == mineZ;
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        if (!miningAim.ready(
            mineX,
            mineY,
            mineZ,
            matches,
            Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ),
            Math.min(4.2D, mc.playerController.getBlockReachDistance()),
            yawError,
            pitch - mc.thePlayer.rotationPitch)) {
            mc.playerController.resetBlockRemoving();
            if (++aimTicks > 80) {
                status = "Mining target has no reachable visible face";
                return false;
            }
            status = "Aiming at visible excavation block";
            return true;
        }
        aimTicks = 0;
        float damage = b.getPlayerRelativeBlockHardness(mc.thePlayer, w, mineX, mineY, mineZ);
        int allowance = MiningAttackInput.allowance(damage, mc.thePlayer.capabilities.isCreativeMode);
        if (allowance == 0 || ++digTicks > allowance) {
            stop(mc, "Mining stalled; check tool strength or block protection");
            return true;
        }
        miningAttack.hold(mc.gameSettings.keyBindAttack);
        if (b instanceof net.minecraft.block.BlockFalling
            || w.getBlock(mineX, mineY + 1, mineZ) instanceof net.minecraft.block.BlockFalling) {
            fallingBlocks.record(b, mineX, mineY, mineZ);
        }
        status = "Mining a safe path";
        return true;
    }

    void releaseMiningWhileSandFalls(Minecraft mc) {
        miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        mc.playerController.resetBlockRemoving();
        miningAim.reset();
        digTicks = 0;
    }

    public String hud(Minecraft mc) {
        int[] pos = structureWaypoint();
        if (pos == null) return "Next Structure";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(pos[0] + 0.5D, pos[1] + 0.5D, pos[2] + 0.5D));
        return pos[0] + ", " + pos[1] + ", " + pos[2] + " (" + distance + "m)";
    }
}
