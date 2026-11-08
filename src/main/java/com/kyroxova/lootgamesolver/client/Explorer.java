package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerGeometry.*;
import static com.kyroxova.lootgamesolver.client.ExplorerMiningTarget.visibleMiningPoint;
import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.*;
import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class Explorer {

    public static final boolean ENABLED = true;
    private final List<int[]> route = new ArrayList<int[]>();
    private World world;
    private SavedStructureFinder savedFinder;
    private TileEntity target;
    private int ticks;
    private int stalled;
    private int stallRecoveries;
    private final java.util.Set<Long> blockedWalkNodes = new java.util.LinkedHashSet<Long>();
    private double lastDistance = Double.MAX_VALUE;
    private boolean walking;
    private boolean digging;
    private int savedSlot = -1;
    private int digTicks;
    private final FallingBlockWaiter fallingBlocks = new FallingBlockWaiter();
    private int toolWaitTicks;
    private boolean waitingForTool;
    private String status = "Scanning loaded terrain";
    private ProgressiveSearch search;
    private boolean searching;
    private NavigationInput navigationInput;
    private int stairX;
    private int stairZ;
    private int stairSteps;
    private boolean seekingDryEntrance;
    private boolean entranceSelected;
    private final List<int[]> entranceCandidates = new ArrayList<int[]>();
    private int entranceCandidate;
    private double segmentX;
    private double segmentZ;
    private final MiningAimGate miningAim = new MiningAimGate();

    private final ExplorerSustain sustain = new ExplorerSustain();
    private int settlingTicks;
    private ExcavationPathfinder excavationSearch;
    private int[] roofEscapeGoal;
    private final List<int[]> excavationRoute = new ArrayList<int[]>();
    private final List<int[]> rejectedSteps = new ArrayList<int[]>();
    private final List<int[]> navigationPreview = new ArrayList<int[]>();
    private int[] previewHead;
    private int previewSize = -1;
    private int excavationReplans;
    private int exitRecoveryTicks;
    private int exitRecoveryAttempts;
    private OpenExitSearch openExitSearch;
    private boolean followingOpenExit;
    private final ExplorerCamera camera = new ExplorerCamera();
    private int searchGoalX, searchGoalZ;
    private int[] extendedFrom;
    private int approachAttempts;
    private int[] knownStructure;
    private ExcavationPathfinder approachPlan;
    private int[] approachAnchor;
    private boolean approachReady;
    private final InventoryToolTransfer toolTransfer = new InventoryToolTransfer();
    private final ExplorerReturnStep returnStep = new ExplorerReturnStep(toolTransfer, camera);
    private final MiningAttackInput miningAttack = new MiningAttackInput();
    private long miningTarget = Long.MIN_VALUE;
    private int miningSlot = -1;
    private int aimTicks;
    private final ExplorerTrailSession trail = new ExplorerTrailSession(
        new File(new File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"), "access-trails.json"));
    private boolean returning, returnAvailable;
    private int cameraPitchDirection;
    private int pitchLastY;
    private double pitchLandingX, pitchLandingZ;
    private boolean returnDigging;
    private TileEntity returnOriginalTarget;
    private List<int[]> returnAfterDig;
    private boolean returnDigResumeSearch;
    private boolean rejoiningSavedTrail;
    private int returnJoinX, returnJoinY, returnJoinZ;
    private List<int[]> returnDigSource;
    private int returnDigAnchorIndex;
    private List<int[]> roomExitCandidates;
    private int roomExitCandidateIndex;
    private int[] activeRoomExit;
    private boolean roomExitPlanning;
    private boolean roomExitNeedsResume;
    private boolean roomExitCrossing;
    private int roomExitJumpRetries;
    private boolean walkingSearchNeedsResume;
    private boolean roomEntrySearchNeedsResume;
    private List<int[]> roomEntryCandidates;
    private int roomEntryCandidateIndex;
    private int[] activeRoomEntry;
    private boolean roomEntryPlanning;
    private boolean roomEntryCrossing;
    private final ExplorerRoomGeometry roomGeometry = new ExplorerRoomGeometry();
    private String excavationMode = "Access route";
    private int routeRetryTicks;
    private String routeRetryStatus = "";
    private List<int[]> chestPitExits;
    private int chestPitExitIndex;
    private int chestPitTrailIndex = -1;
    private int[] chestPitSource;
    private List<int[]> chestPitApproach;
    private GroundPathfinder.Search chestPitApproachSearch;
    private GroundPathfinder.Search chestPitSearch;
    private final ExplorerRouteFollower routeFollower = new ExplorerRouteFollower();
    private boolean escapingChestPit;

    String explorationAction() {
        return returnAvailable ? "Return" : returning ? "Stop" : "Explore";
    }

    private double waypointLabelY;

    double waypointLabelY() {
        return waypointLabelY;
    }

    private void releaseMiningInput(Minecraft mc) {
        miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
    }

    private boolean reachableBoard(Minecraft mc) {
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
    private GroundPathfinder.Search walkingSearch;
    private int[] snowRecoveryTarget;
    private boolean walkingPlanFailed;
    private GroundPathfinder.Search continuationSearch;
    private final ExcavationTerrain excavationTerrain = new ExcavationTerrain();
    private int continuationX, continuationZ;
    private final SolvedStructureStore solvedStructures = new SolvedStructureStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "solved-structures.json"));
    private boolean puzzleArrivalPending;
    private ExcavationPathfinder existingAccessPlan;
    private boolean planningExistingAccess;
    private boolean existingAccessChecked;
    private boolean movingToPuzzleCenter;
    private boolean movingToRewardView;
    private boolean puzzleCenterArrivalPending;
    private TileEntity puzzleMasterTarget;
    private boolean resumeSearchAfterReturn;
    private GroundPathfinder.Search returnJoinSearch;
    private List<int[]> returnTrailRoute;

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

    int[] structureWaypoint() {
        return knownStructure;
    }

    boolean consumePuzzleArrival() {
        if (!puzzleArrivalPending) return false;
        puzzleArrivalPending = false;
        return true;
    }

    int[] puzzlePosition() {
        return knownStructure == null ? null : knownStructure.clone();
    }

    boolean resumePuzzleInCurrentRoom(Minecraft mc, int[] center) {
        if (walking || center == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || x <= bounds[0]
            || x >= bounds[1]
            || z <= bounds[2]
            || z >= bounds[3]
            || center[0] <= bounds[0]
            || center[0] >= bounds[1]
            || center[2] <= bounds[2]
            || center[2] >= bounds[3]
            || Math.abs(y - center[1] - 1) > 2) return false;
        if (knownStructure == null || knownStructure[0] <= bounds[0]
            || knownStructure[0] >= bounds[1]
            || knownStructure[2] <= bounds[2]
            || knownStructure[2] >= bounds[3]) {
            knownStructure = center.clone();
        }
        TileEntity current = new TileEntity();
        current.xCoord = knownStructure[0];
        current.yCoord = knownStructure[1];
        current.zCoord = knownStructure[2];
        target = current;
        beginAccessTrail(mc);
        returnAvailable = false;
        puzzleArrivalPending = true;
        status = "Resuming unfinished puzzle in current room";
        return true;
    }

    boolean moveToPuzzleCenter(Minecraft mc, int[] center) {
        return moveToPosition(mc, center, true);
    }

    boolean moveToRewardView(Minecraft mc, int[] center) {
        return moveToPosition(mc, center, false);
    }

    private boolean moveToPosition(Minecraft mc, int[] center, boolean allowDigging) {
        if (mc.thePlayer == null || center == null || center.length < 3 || movingToPuzzleCenter) return false;
        puzzleMasterTarget = target;
        TileEntity point = new TileEntity();
        point.xCoord = center[0];
        point.yCoord = center[1];
        point.zCoord = center[2];
        target = point;
        movingToPuzzleCenter = true;
        movingToRewardView = !allowDigging;
        puzzleCenterArrivalPending = false;
        cameraPitchDirection = 0;
        route.clear();
        entranceCandidates.clear();
        entranceCandidate = 0;
        digging = false;
        searching = false;
        walkingSearch = null;
        continuationSearch = null;
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        walkingSearch = new GroundPathfinder.Search(
            walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            center[0],
            center[1] + 1,
            center[2],
            LootGameSolverConfig.explorationRange);
        if (walkingSearch.advance(3_000_000L)) {
            boolean reachedCenter = walkingSearch.reachedGoal();
            route.addAll(walkingSearch.path());
            walkingSearch = null;
            if (reachedCenter) appendPuzzleCenterWaypoint(center);
        }
        if (walkingSearch == null && route.isEmpty()) {
            double distance = Math
                .hypot(mc.thePlayer.posX - (center[0] + 0.5D), mc.thePlayer.posZ - (center[2] + 0.5D));
            if (distance > 0.35D || Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - center[1] - 1) > 0) {
                if (allowDigging && LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
                    walking = true;
                    status = digging ? "Planning board entry" : "Moving to board entrance";
                    return true;
                }
                if (!allowDigging) {
                    target = puzzleMasterTarget;
                    puzzleMasterTarget = null;
                    movingToPuzzleCenter = false;
                } else abandonPuzzleCenterMove(mc);
                return false;
            }
        }
        walking = walkingSearch != null || !route.isEmpty();
        if (!walking) {
            double distance = Math
                .hypot(mc.thePlayer.posX - (center[0] + 0.5D), mc.thePlayer.posZ - (center[2] + 0.5D));
            if (distance > 0.35D || Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - center[1] - 1) > 0) {
                if (allowDigging && LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
                    walking = true;
                    status = digging ? "Planning an entry to the board center" : "Moving to a safe board entrance";
                    return true;
                }
                if (!allowDigging) {
                    target = puzzleMasterTarget;
                    puzzleMasterTarget = null;
                    movingToPuzzleCenter = false;
                } else abandonPuzzleCenterMove(mc);
                return false;
            }
            movingToPuzzleCenter = false;
            target = puzzleMasterTarget;
            puzzleMasterTarget = null;
            puzzleCenterArrivalPending = true;
        }
        status = "Moving to board center";
        return true;
    }

    private void appendPuzzleCenterWaypoint(int[] center) {
        int[] stand = new int[] { center[0], center[1] + 1, center[2], movingToRewardView ? 0 : 2 };
        if (route.isEmpty() || !samePosition(route.get(route.size() - 1), stand)) route.add(stand);
        else route.set(route.size() - 1, stand);
    }

    private boolean reachPuzzleCenter(Minecraft mc) {
        int[] center = target == null ? null : new int[] { target.xCoord, target.yCoord, target.zCoord };
        if (center == null) {
            abandonPuzzleCenterMove(mc);
            return true;
        }
        double distance = center == null ? Double.MAX_VALUE
            : Math.hypot(mc.thePlayer.posX - (center[0] + 0.5D), mc.thePlayer.posZ - (center[2] + 0.5D));
        boolean atFloor = center != null && Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - center[1] - 1) < 0.01D;
        if (distance <= (movingToRewardView ? 0.75D : 0.35D) && atFloor && mc.thePlayer.onGround) {
            target = puzzleMasterTarget;
            puzzleMasterTarget = null;
            movingToPuzzleCenter = false;
            stop(mc, "At LootGames board center");
            puzzleCenterArrivalPending = true;
            return true;
        }
        walkingSearch = new GroundPathfinder.Search(
            walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            center[0],
            center[2],
            LootGameSolverConfig.explorationRange);
        if (walkingSearch.advance(3_000_000L)) {
            boolean reachedCenter = walkingSearch.reachedGoal();
            route.clear();
            route.addAll(walkingSearch.path());
            walkingSearch = null;
            if (reachedCenter) appendPuzzleCenterWaypoint(center);
            if (!route.isEmpty()) {
                walking = true;
                segmentX = mc.thePlayer.posX;
                segmentZ = mc.thePlayer.posZ;
                status = "Moving to board center";
                return true;
            }
        } else {
            route.clear();
            walking = true;
            status = "Checking route to board center";
            return true;
        }
        if (movingToRewardView) {
            stop(mc, "Chest view unreachable");
            return true;
        }
        if (seekingDryEntrance) {
            seekingDryEntrance = false;
            digging = true;
            entranceSelected = true;
            savedSlot = mc.thePlayer.inventory.currentItem;
        }
        if (digging) {
            if (stairStep(mc)) return true;
            digging = false;
            entranceSelected = false;
            excavationRoute.clear();
            excavationSearch = null;
        }
        if (LootGameSolverConfig.staircaseEnabled && selectDigEntrance(mc)) {
            walking = true;
            status = digging ? "Planning an entry to the board center" : "Moving to a safe board entrance";
            return true;
        }
        abandonPuzzleCenterMove(mc);
        return true;
    }

    private void abandonPuzzleCenterMove(Minecraft mc) {
        if (puzzleMasterTarget != null) target = puzzleMasterTarget;
        puzzleMasterTarget = null;
        movingToPuzzleCenter = false;
        stop(mc, "Board center has no verified safe route; explorer held");
    }

    boolean consumePuzzleCenterArrival() {
        if (!puzzleCenterArrivalPending) return false;
        puzzleCenterArrivalPending = false;
        return true;
    }

    void finishPuzzle(Minecraft mc, boolean solved) {
        persistAccessTrail(true);
        if (solved && knownStructure != null && mc.theWorld != null) {
            try {
                solvedStructures
                    .mark(worldIdentity(mc), mc.theWorld.provider.dimensionId, knownStructure, knownStructure);
            } catch (java.io.IOException e) {
                status = "Solved; could not save local structure history";
            }
        }
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        puzzleArrivalPending = false;
        returnAvailable = false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(x, y, z)) {
            continueProgressiveSearch(mc, solved);
            return;
        }
        if (startChestPitReturn(mc, true)) return;
        if (insideDungeonRoom(mc) && startRoomExitSearch(mc)) return;
        if (startSavedReturn(mc, true, 128)) return;
        if (startRoomExitSearch(mc)) return;
        retryExitLater(mc);
    }

    private void retryExitLater(Minecraft mc) {
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

    private void continueProgressiveSearch(Minecraft mc, boolean solved) {
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
            LootGameSolverConfig.explorationSearchLimit);
        walking = nextSearchLeg(mc);
        if (!walking) stop(mc, solved ? "Puzzle solved; nearby search finished" : "Puzzle ended; search paused");
        else status = solved ? "Puzzle solved; searching for the next structure" : "Puzzle ended; continuing search";
    }

    private void beginAccessTrail(Minecraft mc) {
        if (knownStructure == null || mc.thePlayer == null) return;
        if (trail.target == null || !samePosition(trail.target, knownStructure)) {
            persistAccessTrail(true);
            existingAccessChecked = false;
            blockedWalkNodes.clear();
            stallRecoveries = 0;
            trail.points.clear();
            trail.target = knownStructure.clone();
            trail.worldId = worldIdentity(mc);
            trail.dimension = mc.theWorld.provider.dimensionId;
            trail.points.replace(
                trail.store.load(trail.worldId, trail.dimension, trail.target[0], trail.target[1], trail.target[2]));
            trail.dirty = false;
            trail.excavating = trail.points.size() > 1;
        }
    }

    private boolean reuseSavedAccessPath(Minecraft mc) {
        if (mc.thePlayer == null || target == null || trail.points.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int index = trail.points.nearestIndex(x, y, z, 1);
        if (index < 0) return false;
        if (index == trail.points.size() - 1 && safe(mc.theWorld, x, y, z)) {
            route.clear();
            digging = true;
            walking = true;
            searching = false;
            returning = false;
            entranceSelected = true;
            status = "Continuing excavation from saved tunnel";
            return true;
        }
        List<int[]> saved = trail.points.forwardFrom(index);
        if (saved.size() < 2) return false;
        route.clear();
        route.addAll(saved);
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        walkingSearch = null;
        continuationSearch = null;
        extendedFrom = null;
        existingAccessChecked = true;
        planningExistingAccess = false;
        existingAccessPlan = null;
        digging = false;
        returning = false;
        searching = false;
        walking = true;
        status = "Following saved entrance route";
        return true;
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

    private void recordAccessPoint(int x, int y, int z) {
        if (!trail.excavating) return;
        if (trail.points.record(x, y, z)) {
            trail.dirty = true;
            persistAccessTrail(false);
        }
    }

    private void persistAccessTrail(boolean force) {
        if (!trail.persist(force, ticks)) status = "Could not save the structure return route";
    }

    private void removePersistedTrail() {
        if (!trail.removePersisted()) status = "Returned; could not clear the saved route";
    }

    private boolean startSavedReturn(Minecraft mc, boolean resumeSearch, int maxJoinDistance) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        List<int[]> back = trail.points.reverse(x, y, z);
        returnJoinSearch = null;
        returnTrailRoute = null;
        roomEntryCandidates = null;
        activeRoomEntry = null;
        roomEntryPlanning = false;
        roomEntryCrossing = false;
        roomEntrySearchNeedsResume = false;
        walkingSearchNeedsResume = false;
        if (!back.isEmpty() && savedRouteWalkable(mc, back)) {
            route.clear();
            route.addAll(back);
            rejoiningSavedTrail = false;
        } else {
            int nearest = trail.points.nearestIndex(x, y, z, maxJoinDistance);
            int[] join = null;
            List<int[]> saved = null;
            for (int candidate = nearest; candidate >= 0; candidate--) {
                List<int[]> candidateRoute = trail.points.reverseFrom(candidate);
                if (candidateRoute.size() < 2 || !savedRouteWalkable(mc, candidateRoute)) continue;
                join = trail.points.pointAt(candidate);
                saved = candidateRoute;
                break;
            }
            if (join == null || saved == null) return false;
            returnTrailRoute = saved;
            returnJoinX = join[0];
            returnJoinY = join[1];
            returnJoinZ = join[2];
            rejoiningSavedTrail = true;
            returning = true;
            returnJoinSearch = new GroundPathfinder.Search(
                walkingTerrain(mc.theWorld),
                x,
                y,
                z,
                join[0],
                join[1],
                join[2],
                Math.max(maxJoinDistance, LootGameSolverConfig.explorationRange));
            route.clear();
        }
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        cameraPitchDirection = 0;
        returning = true;
        returnAvailable = false;
        resumeSearchAfterReturn = resumeSearch;
        walking = true;
        searching = false;
        digging = false;
        walkingSearch = null;
        continuationSearch = null;
        status = returnJoinSearch == null ? "Returning along saved access route" : "Rejoining saved access route";
        return true;
    }

    private boolean savedRouteWalkable(Minecraft mc, List<int[]> points) {
        if (mc == null || mc.theWorld == null || points == null || points.isEmpty()) return false;
        int[] endpoint = points.get(points.size() - 1);
        if (!solidCheckpoint(mc.theWorld, endpoint[0], endpoint[1], endpoint[2])) return false;
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (!safe(mc.theWorld, point[0], point[1], point[2])
                || blockedWalkNodes.contains(supportKey(point[0], point[1], point[2]))) return false;
            if (i == 0) continue;
            int[] previous = points.get(i - 1);
            if (Math.abs(point[0] - previous[0]) > 1 || Math.abs(point[1] - previous[1]) > 1
                || Math.abs(point[2] - previous[2]) > 1) return false;
            if (point[0] != previous[0] && point[2] != previous[2]
                && (!safe(mc.theWorld, point[0], previous[1], previous[2])
                    || !safe(mc.theWorld, previous[0], previous[1], point[2])))
                return false;
        }
        return true;
    }

    private boolean startChestPitReturn(Minecraft mc, boolean resumeSearch) {
        if (mc.thePlayer == null || mc.theWorld == null || trail.points.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int trailIndex = trail.points.nearestIndex(x, y, z, 128);
        if (trailIndex < 0) return false;

        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        List<int[]> exits = new ArrayList<int[]>();
        int chestSides = 0;
        for (int[] direction : directions) {
            boolean chest = false;
            for (int dy = -2; dy <= 1 && !chest; dy++)
                chest = isRewardChest(mc.theWorld, x + direction[0], y + dy, z + direction[1]);
            if (!chest) continue;
            chestSides++;
            for (int distance = 1; distance <= 5; distance++) for (int dy = -1; dy <= 2; dy++) {
                int ex = x + direction[0] * distance, ey = y + dy, ez = z + direction[1] * distance;
                if (solidCheckpoint(mc.theWorld, ex, ey, ez)) exits.add(new int[] { ex, ey, ez });
            }
        }
        if (chestSides < 3 || exits.isEmpty()) return false;

        exits.sort(
            (a, b) -> Double.compare(
                Math.abs(a[0] - trail.points.pointAt(trailIndex)[0])
                    + Math.abs(a[2] - trail.points.pointAt(trailIndex)[2]),
                Math.abs(b[0] - trail.points.pointAt(trailIndex)[0])
                    + Math.abs(b[2] - trail.points.pointAt(trailIndex)[2])));
        chestPitExits = exits;
        chestPitExitIndex = 0;
        chestPitTrailIndex = trailIndex;
        chestPitSource = new int[] { x, y, z };
        chestPitApproach = null;
        chestPitApproachSearch = null;
        chestPitSearch = null;
        returnJoinSearch = null;
        returnTrailRoute = null;
        roomExitCandidates = null;
        roomExitPlanning = false;
        roomExitCrossing = false;
        returning = true;
        returnAvailable = false;
        resumeSearchAfterReturn = resumeSearch;
        walking = true;
        searching = false;
        digging = false;
        route.clear();
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        beginNextChestPitSearch(mc);
        status = "Planning a jump out of the reward chest pit";
        return true;
    }

    private void beginNextChestPitSearch(Minecraft mc) {
        if (chestPitExits == null || chestPitExitIndex >= chestPitExits.size()) {
            chestPitSearch = null;
            chestPitApproachSearch = null;
            return;
        }
        int[] exit = chestPitExits.get(chestPitExitIndex);
        int[] join = trail.points.pointAt(chestPitTrailIndex);
        if (join == null || chestPitSource == null) {
            chestPitSearch = null;
            chestPitApproachSearch = null;
            return;
        }
        final World exitWorld = mc.theWorld;
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int px, int py, int pz) {
                return safe(exitWorld, px, py, pz);
            }

            public boolean clear(int px, int py, int pz) {
                return passable(exitWorld, px, py, pz);
            }

            public boolean checkpoint(int px, int py, int pz) {
                return solidCheckpoint(exitWorld, px, py, pz);
            }
        };
        chestPitApproachSearch = new GroundPathfinder.Search(
            terrain,
            chestPitSource[0],
            chestPitSource[1],
            chestPitSource[2],
            exit[0],
            exit[1],
            exit[2],
            8);
        chestPitApproach = null;
        chestPitSearch = null;
    }

    private void beginChestPitJoinSearch(Minecraft mc, int[] exit) {
        int[] join = trail.points.pointAt(chestPitTrailIndex);
        if (join == null) {
            chestPitSearch = null;
            return;
        }
        final World exitWorld = mc.theWorld;
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int px, int py, int pz) {
                return safe(exitWorld, px, py, pz);
            }

            public boolean clear(int px, int py, int pz) {
                return passable(exitWorld, px, py, pz);
            }

            public boolean checkpoint(int px, int py, int pz) {
                return solidCheckpoint(exitWorld, px, py, pz);
            }
        };
        chestPitSearch = new GroundPathfinder.Search(
            terrain,
            exit[0],
            exit[1],
            exit[2],
            join[0],
            join[1],
            join[2],
            Math.max(128, LootGameSolverConfig.explorationRange));
    }

    private boolean advanceChestPitReturn(Minecraft mc) {
        if (chestPitExits == null) return false;
        if (chestPitApproachSearch != null) {
            status = "Finding a reachable jump out of the chest pit";
            if (!chestPitApproachSearch.advance(3_000_000L)) return true;
            if (!chestPitApproachSearch.reachedGoal()) {
                chestPitApproachSearch = null;
                chestPitExitIndex++;
                beginNextChestPitSearch(mc);
                return true;
            }
            chestPitApproach = chestPitApproachSearch.path();
            chestPitApproachSearch = null;
            beginChestPitJoinSearch(mc, chestPitExits.get(chestPitExitIndex));
            return true;
        }
        if (chestPitSearch == null) {
            clearChestPitSearch();
            if (startSavedReturn(mc, true, 128)) return true;
            if (startRoomExitSearch(mc)) return true;
            retryExitLater(mc);
            return true;
        }
        status = "Finding a clear route from the chest pit to the saved tunnel";
        if (!chestPitSearch.advance(3_000_000L)) return true;
        List<int[]> path = chestPitSearch.path();
        if (chestPitSearch.reachedGoal()) {
            route.clear();
            if (chestPitApproach != null) for (int[] step : chestPitApproach) appendRoutePoint(route, step);
            else appendRoutePoint(route, chestPitExits.get(chestPitExitIndex));
            for (int[] step : path) appendRoutePoint(route, step);
            List<int[]> saved = trail.points.reverseFrom(chestPitTrailIndex);
            if (savedRouteWalkable(mc, saved)) {
                for (int[] step : saved) appendRoutePoint(route, step);
            } else {
                clearChestPitSearch();
                if (startSavedReturn(mc, true, 128)) return true;
                if (startRoomExitSearch(mc)) return true;
                retryExitLater(mc);
                return true;
            }
            chestPitSearch = null;
            chestPitApproachSearch = null;
            chestPitApproach = null;
            chestPitSource = null;
            chestPitExits = null;
            chestPitTrailIndex = -1;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            segmentX = mc.thePlayer.posX;
            segmentZ = mc.thePlayer.posZ;
            status = "Jumping clear of the reward chests; returning through the saved tunnel";
            return true;
        }
        chestPitExitIndex++;
        if (chestPitExitIndex < chestPitExits.size()) {
            beginNextChestPitSearch(mc);
            return true;
        }
        clearChestPitSearch();
        if (startSavedReturn(mc, true, 128)) return true;
        if (startRoomExitSearch(mc)) return true;
        retryExitLater(mc);
        return true;
    }

    private void clearChestPitSearch() {
        chestPitSearch = null;
        chestPitApproachSearch = null;
        chestPitApproach = null;
        chestPitSource = null;
        chestPitExits = null;
        chestPitTrailIndex = -1;
        escapingChestPit = false;
    }

    private boolean startRoomExitSearch(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        roomExitCandidates = roomGeometry.findRoomExitCandidates(mc, bounds, this::clearableExitColumn);
        // Prefer room openings nearest the saved tunnel mouth.
        if (trail.points.size() >= 2 && roomExitCandidates.size() > 1) {
            int[] mouth = savedRoomMouth(bounds);
            roomExitCandidates.sort((a, b) -> Double.compare(exitMouthDistance(a, mouth), exitMouthDistance(b, mouth)));
        }
        roomExitCandidateIndex = 0;
        roomExitPlanning = false;
        roomExitNeedsResume = false;
        roomExitCrossing = false;
        return startNextRoomExitSearch(mc);
    }

    private int[] savedRoomMouth(int[] bounds) {
        for (int i = trail.points.size() - 1; i >= 0; i--) {
            int[] point = trail.points.pointAt(i);
            boolean besideX = (Math.abs(point[0] - bounds[0]) <= 1 || Math.abs(point[0] - bounds[1]) <= 1)
                && point[2] >= bounds[2]
                && point[2] <= bounds[3];
            boolean besideZ = (Math.abs(point[2] - bounds[2]) <= 1 || Math.abs(point[2] - bounds[3]) <= 1)
                && point[0] >= bounds[0]
                && point[0] <= bounds[1];
            if (besideX || besideZ) return point;
        }
        return trail.points.pointAt(trail.points.size() - 1);
    }

    private boolean startNextRoomExitSearch(Minecraft mc) {
        if (roomExitCandidates == null || roomExitCandidateIndex >= roomExitCandidates.size()) return false;
        activeRoomExit = roomExitCandidates.get(roomExitCandidateIndex);
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
        roomExitNeedsResume = false;
        returning = true;
        returnAvailable = false;
        resumeSearchAfterReturn = true;
        walking = true;
        searching = false;
        digging = false;
        route.clear();
        status = "Finding room exit";
        return true;
    }

    private void appendRoomExitWaypoint(int[] exit, int offset) {
        int[] point = new int[] { exit[offset], exit[offset + 1], exit[offset + 2] };
        if (route.isEmpty() || !samePosition(route.get(route.size() - 1), point)) route.add(point);
    }

    private boolean startRoomEntrySearch(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || target == null || knownStructure == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || target.xCoord <= bounds[0]
            || target.xCoord >= bounds[1]
            || target.zCoord <= bounds[2]
            || target.zCoord >= bounds[3]
            || (x > bounds[0] && x < bounds[1] && z > bounds[2] && z < bounds[3] && y <= target.yCoord + 3))
            return false;
        roomEntryCandidates = roomGeometry.findRoomExitCandidates(mc, bounds, this::clearableExitColumn);
        roomEntryCandidates.removeIf(candidate -> candidate.length >= 10);
        roomEntryCandidateIndex = 0;
        final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        final int tx = target.xCoord, tz = target.zCoord;
        roomEntryCandidates.sort(
            (a, b) -> Double.compare(
                Math.hypot(a[3] + 0.5D - px, a[5] + 0.5D - pz) + Math.hypot(a[0] - tx, a[2] - tz),
                Math.hypot(b[3] + 0.5D - px, b[5] + 0.5D - pz) + Math.hypot(b[0] - tx, b[2] - tz)));
        if (startNextRoomEntrySearch(mc)) return true;
        roomEntryCandidates = null;
        return false;
    }

    private boolean startNextRoomEntrySearch(Minecraft mc) {
        if (roomEntryCandidates == null || roomEntryCandidateIndex >= roomEntryCandidates.size()) return false;
        activeRoomEntry = roomEntryCandidates.get(roomEntryCandidateIndex);
        walkingSearch = new GroundPathfinder.Search(
            walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            activeRoomEntry[3],
            activeRoomEntry[4],
            activeRoomEntry[5],
            LootGameSolverConfig.explorationRange);
        roomEntryPlanning = true;
        roomEntrySearchNeedsResume = false;
        walking = true;
        searching = false;
        digging = false;
        route.clear();
        status = "Finding room entrance";
        return true;
    }

    private boolean clearableExitColumn(World world, int x, int y, int z) {
        if (!world.blockExists(x, y - 1, z) || !world.getBlock(x, y - 1, z)
            .isNormalCube()) return false;
        for (int by = y; by <= y + 1; by++) {
            if (!world.blockExists(x, by, z)) return false;
            net.minecraft.block.Block block = world.getBlock(x, by, z);
            if (ContactHazards.dangerous(block, world.getBlockMetadata(x, by, z)) || block.getMaterial()
                .isLiquid()
                || block instanceof net.minecraft.block.BlockFalling
                || !passable(world, x, by, z) && !excavationTerrain.excavatable(world, x, by, z)) return false;
        }
        return true;
    }

    private boolean clearRoomExitObstruction(Minecraft mc, int[] next, int x, int y, int z) {
        World world = mc.theWorld;
        if (next[1] > y && !passable(world, x, y + 2, z)) {
            net.minecraft.block.Block overhead = world.getBlock(x, y + 2, z);
            if (!(overhead instanceof net.minecraft.block.BlockFalling)
                && excavationTerrain.excavatable(world, x, y + 2, z)) return minePlannedBlock(mc, x, y + 2, z, y + 2);
        }
        if (next[0] == x && next[2] == z) return false;
        for (int by = next[1] + 1; by >= next[1]; by--) {
            if (passable(world, next[0], by, next[2])) continue;
            net.minecraft.block.Block block = world.getBlock(next[0], by, next[2]);
            if (block instanceof net.minecraft.block.BlockFalling
                || ContactHazards.dangerous(block, world.getBlockMetadata(next[0], by, next[2]))
                || !excavationTerrain.excavatable(world, next[0], by, next[2])) return false;
            return minePlannedBlock(mc, next[0], by, next[2], next[1]);
        }
        return false;
    }

    private boolean insideDungeonRoom(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int roomY = knownStructure != null ? knownStructure[1] : target == null ? Integer.MIN_VALUE : target.yCoord;
        if (roomY == Integer.MIN_VALUE) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        return y <= roomY + 3;
    }

    private boolean planRoofEscape(Minecraft mc) {
        if (target == null || knownStructure == null || returnDigging || returning) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (y <= target.yCoord + 3 || !isDungeonWall(mc.theWorld, x, y - 1, z)) return false;
        int[] bounds = roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        int[][] exits = { { bounds[0] - 3, y, z }, { bounds[1] + 3, y, z }, { x, y, bounds[2] - 3 },
            { x, y, bounds[3] + 3 } };
        int[] best = exits[0];
        for (int[] exit : exits) {
            if (Math.abs(exit[0] - x) + Math.abs(exit[2] - z) < Math.abs(best[0] - x) + Math.abs(best[2] - z))
                best = exit;
        }
        roofEscapeGoal = best;
        excavationRoute.clear();
        excavationSearch = null;
        excavationReplans = 0;
        digging = true;
        entranceSelected = true;
        status = "Planning a safe route off the roof to a side wall";
        return true;
    }

    private boolean rerouteSavedReturn(Minecraft mc, int firstCheckpoint) {
        if (!returning || returnJoinSearch != null || route.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int checkpointIndex = ReturnRoutePolicy.nextSafeCheckpoint(
            route,
            firstCheckpoint,
            point -> solidCheckpoint(mc.theWorld, point[0], point[1], point[2])
                && !blockedWalkNodes.contains(supportKey(point[0], point[1], point[2])));
        if (checkpointIndex < 0) return false;
        int[] join = route.get(checkpointIndex);
        returnTrailRoute = new ArrayList<int[]>();
        for (int p = checkpointIndex; p < route.size(); p++) returnTrailRoute.add(
            route.get(p)
                .clone());
        returnJoinSearch = new GroundPathfinder.Search(
            walkingTerrain(mc.theWorld),
            x,
            y,
            z,
            join[0],
            join[1],
            join[2],
            Math.max(16, LootGameSolverConfig.explorationRange));
        returnJoinX = join[0];
        returnJoinY = join[1];
        returnJoinZ = join[2];
        rejoiningSavedTrail = true;
        route.clear();
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        status = "Finding a clear way back to the saved tunnel";
        return true;
    }

    private boolean digTowardSavedExit(Minecraft mc) {
        if (!returning || returnDigging || mc.thePlayer == null) return false;
        if (insideDungeonRoom(mc)) return roomExitCandidates == null && startRoomExitSearch(mc);
        List<int[]> savedRoute = new ArrayList<int[]>(route.size());
        for (int[] point : route) savedRoute.add(point.clone());
        if (savedRoute.size() < 2 && trail.points.size() >= 2) {
            int nearest = trail.points.nearestIndex(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                128);
            savedRoute = trail.points.reverseFrom(nearest);
        }
        if (savedRoute.size() >= 2 && startReturnDig(mc, savedRoute)) return true;
        return insideDungeonRoom(mc) && roomExitCandidates == null && startRoomExitSearch(mc);
    }

    private boolean startReturnDig(Minecraft mc, List<int[]> savedRoute) {
        if (mc.thePlayer == null || mc.theWorld == null || savedRoute == null || savedRoute.size() < 2) return false;
        returnDigSource = new ArrayList<int[]>(savedRoute.size());
        for (int[] point : savedRoute) returnDigSource.add(point.clone());
        returnDigAnchorIndex = Math.min(3, returnDigSource.size() - 1);
        return planReturnDigToAnchor(mc, returnDigAnchorIndex);
    }

    private boolean planReturnDigToAnchor(Minecraft mc, int anchorIndex) {
        if (mc.thePlayer == null || mc.theWorld == null
            || returnDigSource == null
            || anchorIndex < 1
            || anchorIndex >= returnDigSource.size()) return false;
        int[] anchor = returnDigSource.get(anchorIndex);
        TileEntity digGoal = new TileEntity();
        digGoal.xCoord = anchor[0];
        digGoal.yCoord = anchor[1];
        digGoal.zCoord = anchor[2];
        if (!returnDigging) returnOriginalTarget = target;
        if (savedSlot < 0) savedSlot = mc.thePlayer.inventory.currentItem;
        target = digGoal;
        returnAfterDig = new ArrayList<int[]>();
        for (int i = anchorIndex; i < returnDigSource.size(); i++) returnAfterDig.add(
            returnDigSource.get(i)
                .clone());
        returnDigAnchorIndex = anchorIndex;
        returnDigResumeSearch = resumeSearchAfterReturn;
        returnDigging = true;
        returnJoinSearch = null;
        returnTrailRoute = null;
        rejoiningSavedTrail = false;
        route.clear();
        excavationRoute.clear();
        excavationReplans = 0;
        approachPlan = null;
        approachAnchor = null;
        stairX = 0;
        stairZ = 0;
        settlingTicks = 0;
        excavationSearch = new ExcavationPathfinder(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            anchor[0],
            anchor[1],
            anchor[2]);
        digging = true;
        returning = false;
        searching = false;
        entranceSelected = true;
        walking = true;
        status = "Planning a checked exit link to saved checkpoint " + (anchorIndex + 1);
        return true;
    }

    private boolean tryNextReturnDigAnchor(Minecraft mc) {
        if (returnDigSource == null) return false;
        int next = Math.min(returnDigSource.size() - 1, returnDigAnchorIndex + 3);
        return next > returnDigAnchorIndex && planReturnDigToAnchor(mc, next);
    }

    private void resumeSavedReturnAfterDig() {
        target = returnOriginalTarget;
        returnOriginalTarget = null;
        route.clear();
        if (returnAfterDig != null) route.addAll(returnAfterDig);
        returnAfterDig = null;
        returnDigSource = null;
        returnDigSource = null;
        returnDigging = false;
        digging = false;
        returning = true;
        resumeSearchAfterReturn = returnDigResumeSearch;
        status = "Exit opened; continuing along saved access route";
    }

    static String worldIdentity(Minecraft mc) {
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) return "save:" + mc.getIntegratedServer()
            .getFolderName();
        net.minecraft.client.multiplayer.ServerData server = mc.func_147104_D();
        return "server:" + (server == null ? "unknown" : server.serverIP);
    }

    String navigationStatus() {
        return compactStatus(status);
    }

    void setAutomationStatus(String message) {
        status = compactStatus(message);
    }

    private static String compactStatus(String message) {
        return StatusText.explorer(message);
    }

    public void renderCamera(Minecraft mc) {
        camera.render(mc, world, walking);
    }

    private void look(float yaw, float pitch) {
        camera.look(yaw, pitch);
    }

    private void singleStep(Minecraft mc, int x, int y, int z, boolean center) {
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        route.add(new int[] { x, y, z, center ? 2 : 0 });
    }

    List<int[]> navigationRoute() {
        return navigationPreview;
    }

    List<int[]> excavationPreview() {
        return excavationRoute;
    }

    List<int[]> rejectedPreview() {
        return rejectedSteps;
    }

    boolean navigating() {
        return walking;
    }

    void stopRewardView(Minecraft mc) {
        if (movingToRewardView) stop(mc, "Chest in reach");
    }

    boolean hasPendingReturn() {
        return returning || returnAvailable;
    }

    boolean inDungeonRoom(Minecraft mc) {
        return mc != null && mc.thePlayer != null && mc.theWorld != null && insideDungeonRoom(mc);
    }

    void prepareRewardCollection(Minecraft mc, int[] center) {
        if (walking) stop(mc, "Reward collection started");
        if (mc == null || mc.theWorld == null || center == null) return;
        if (trail.points.size() < 2) {
            int[] saved = trail.store.nearestStructure(
                worldIdentity(mc),
                mc.theWorld.provider.dimensionId,
                center[0],
                center[1],
                center[2],
                24);
            if (saved != null) {
                knownStructure = saved;
                trail.target = null;
                beginAccessTrail(mc);
            }
        }
    }

    void clearLastCheckpoint(Minecraft mc) {
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

    boolean inventoryCleanupSafe(Minecraft mc) {
        return walking && !returning
            && !digging
            && !movingToPuzzleCenter
            && !insideDungeonRoom(mc)
            && mc.thePlayer != null
            && mc.thePlayer.onGround
            && route.size() >= 2;
    }

    public void toggle(Minecraft mc) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
        if (walking) {
            stop(mc, "Walk stopped");
            return;
        }
        if (returnAvailable) {
            blockedWalkNodes.clear();
            if (startChestPitReturn(mc, true)) return;
            if (startSavedReturn(mc, true, 64)) return;
            if (trail.points.size() >= 2) {
                returning = true;
                walking = true;
                resumeSearchAfterReturn = true;
                if (digTowardSavedExit(mc)) return;
                retryExitLater(mc);
                return;
            }
        }
        // Resume the current destination before starting a new exit search.
        if (target != null && !target.isInvalid()) {
            resumeCurrentTarget(mc);
            return;
        }
        if (startChestPitReturn(mc, true)) return;
        if (startRoomExitSearch(mc)) return;
        returnAvailable = false;
        puzzleArrivalPending = false;
        planningExistingAccess = false;
        existingAccessPlan = null;
        existingAccessChecked = false;
        stallRecoveries = 0;
        blockedWalkNodes.clear();
        search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            LootGameSolverConfig.explorationSearchLimit);
        entranceSelected = false;
        excavationSearch = null;
        excavationReplans = 0;
        approachAttempts = 0;
        extendedFrom = null;
        approachPlan = null;
        approachAnchor = null;
        walkingSearch = null;
        continuationSearch = null;
        excavationRoute.clear();
        rejectedSteps.clear();
        entranceCandidates.clear();
        scan(mc);
        if (target == null) {
            walking = nextSearchLeg(mc);
            if (!walking) stop(mc, "No safe exploration route available");
            return;
        }
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
            if (!walking) stop(mc, "No safe exploration route available");
        } else status = "Walking to LootGame";
    }

    private void resumeCurrentTarget(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || target == null) return;
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
            reachPuzzleCenter(mc);
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
        releaseMiningInput(mc);
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
            chestPitExits = null;
            chestPitApproach = null;
            chestPitApproachSearch = null;
            chestPitSource = null;
            chestPitSearch = null;
            chestPitTrailIndex = -1;
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
        ExplorerReturnStep.Outcome placement = returnStep.tick(mc, returning, route);
        if (placement == ExplorerReturnStep.Outcome.WAITING) {
            status = "Waiting for return step placement";
            return;
        }
        if (placement == ExplorerReturnStep.Outcome.FAILED) {
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
            status = "Puzzle in progress";
            return;
        }
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled
            || (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 100)) {
            stop(
                mc,
                !LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled ? "Exploration disabled"
                    : "Stopped: low air underwater");
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
            mc.playerController.resetBlockRemoving();
            miningAim.reset();
            status = manual ? "Paused: release movement keys to resume"
                : mc.currentScreen != null ? "Paused: close menu to resume"
                    : mc.thePlayer.hurtTime > 0 ? "Paused after damage" : "Paused: land and disable flight to resume";
            return;
        }
        if (sustain.tick(mc, true, !route.isEmpty() && !digging && !returnDigging && !returnStep.selectingBlock())) {
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
        if (chestPitExits != null) {
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
            stop(mc, "Arrived at LootGame; opening puzzle");
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
                    if (movingToPuzzleCenter && reachedCenter && target != null)
                        appendPuzzleCenterWaypoint(new int[] { target.xCoord, target.yCoord, target.zCoord });
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
            && route.size() >= 1
            && route.size() <= 32) extendRoute(mc);
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
                if (!nextSearchLeg(mc)) stop(mc, "Search limit reached or no safe route remains");
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
            stop(mc, "No safe route after alternate paths were checked");
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
        boolean active = returnStep.tryPlaceReturnStep(mc, next, returning);
        if (returnStep.status() != null) status = returnStep.status();
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
            || text.contains("could not place a safe return step");
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
            reachPuzzleCenter(mc);
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

    private void stop(Minecraft mc, String message) {
        if (recoverableRouteFailure(message)) {
            queueRouteRetry(mc, message);
            return;
        }
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
        returnStep.reset(mc);
        chestPitExits = null;
        chestPitApproach = null;
        chestPitApproachSearch = null;
        chestPitSource = null;
        chestPitSearch = null;
        chestPitTrailIndex = -1;
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
            mc.thePlayer.setSprinting(false);
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

    private void scan(Minecraft mc) {
        target = null;
        knownStructure = null;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        target = ExplorerStructureDiscovery.find(mc, savedFinder, solvedStructures, worldIdentity(mc));
        if (target != null) {
            knownStructure = new int[] { target.xCoord, target.yCoord, target.zCoord };
            waypointLabelY = ExplorerStructureDiscovery.labelHeight(mc, target);
        }
    }

    private boolean nextSearchLeg(Minecraft mc) {
        if (search == null) return false;
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] goal = search.next();
            if (goal == null) return false;
            int x = (int) Math.floor(mc.thePlayer.posX), z = (int) Math.floor(mc.thePlayer.posZ);
            int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            double dx = goal[0] - x, dz = goal[1] - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 4) continue;
            searchGoalX = goal[0];
            searchGoalZ = goal[1];
            double step = Math.min(8D, length);
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
            status = route.isEmpty() && walkingSearch == null ? "Checking another safe exploration direction"
                : "Exploring outward";
            return true;
        }
        searching = true;
        walking = true;
        status = "Waiting for more loaded terrain to continue exploration";
        return true;
    }

    private GroundPathfinder.Terrain walkingTerrain(final World terrainWorld) {
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
        if (!finished && (route.size() > 8 || continuation.size() < (route.isEmpty() ? 1 : 6))) return;
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
        if (!LootGameSolverConfig.staircaseEnabled || mc.thePlayer.isInWater() || !mc.thePlayer.onGround) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!safe(mc.theWorld, x, y, z) || !dryShore(mc.theWorld, x, y, z)) return false;
        route.clear();
        walkingSearch = null;
        continuationSearch = null;
        excavationSearch = null;
        excavationRoute.clear();
        digging = true;
        entranceSelected = true;
        seekingDryEntrance = false;
        savedSlot = mc.thePlayer.inventory.currentItem;
        status = "Planning a safe tunnel from the current checkpoint";
        return true;
    }

    private List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
        int goalY = target.yCoord + 1;
        ExcavationPathfinder.Terrain terrain = (a, b, c, nx, ny, nz) -> excavationTerrain
            .excavationCost(mc.theWorld, a, b, c, nx, ny, nz);
        if (goalY == y) {
            List<int[]> proposal = ExcavationPatterns.straight(x, y, z, target.xCoord, goalY, target.zCoord);
            List<int[]> checked = ExcavationPatterns
                .checked(proposal.subList(0, Math.min(16, proposal.size())), terrain, x, y, z);
            if (!checked.isEmpty() && checked.size() >= Math.min(4, proposal.size())) {
                excavationMode = goalY == y ? "Straight tunnel" : goalY > y ? "Upward staircase" : "Downward staircase";
                return checked;
            }
        }
        if (goalY != y) {
            int orientation = Math.abs(target.xCoord - x) >= Math.abs(target.zCoord - z) ? target.xCoord >= x ? 0 : 2
                : target.zCoord >= z ? 1 : 3;
            for (int turn = 0; turn < 4; turn++) {
                List<int[]> proposal = ExcavationPatterns
                    .spiral(x, y, z, goalY, LootGameSolverConfig.spiralWidth, (orientation + turn) % 4);
                int section = 8 * (LootGameSolverConfig.spiralWidth - 1);
                List<int[]> checked = ExcavationPatterns
                    .checked(proposal.subList(0, Math.min(section, proposal.size())), terrain, x, y, z);
                if (checked.size() < proposal.size()) {
                    int lap = 4 * (LootGameSolverConfig.spiralWidth - 1);
                    checked = new ArrayList<int[]>(checked.subList(0, checked.size() / lap * lap));
                }
                if (!checked.isEmpty()) {
                    excavationMode = goalY > y ? "Spiral staircase up" : "Spiral staircase down";
                    return checked;
                }
            }
        }
        return new ArrayList<int[]>();
    }

    private boolean stairStep(Minecraft mc) {
        if (fallingBlocks.waiting(mc, () -> releaseMiningWhileSandFalls(mc))) {
            status = "Waiting for sand";
            return true;
        }
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround) return ++settlingTicks <= 40;
        settlingTicks = 0;
        if (roofEscapeGoal != null && Math.abs(x - roofEscapeGoal[0]) + Math.abs(z - roofEscapeGoal[2]) <= 2) {
            roofEscapeGoal = null;
            excavationRoute.clear();
            excavationSearch = null;
            status = "Reached roof edge; planning side access";
        }
        if (roofEscapeGoal == null) planRoofEscape(mc);
        excavationTerrain.protectSupport(supportKey(x, y - 1, z));
        if (!returnDigging) {
            if (trail.target == null || trail.target[0] != target.xCoord
                || trail.target[1] != target.yCoord
                || trail.target[2] != target.zCoord) {
                persistAccessTrail(true);
                trail.points.clear();
                trail.target = new int[] { target.xCoord, target.yCoord, target.zCoord };
                trail.worldId = worldIdentity(mc);
                trail.dimension = mc.theWorld.provider.dimensionId;
                trail.points.replace(
                    trail.store
                        .load(trail.worldId, trail.dimension, trail.target[0], trail.target[1], trail.target[2]));
                trail.dirty = false;
                trail.excavating = trail.points.size() > 1;
            }
            if (!trail.excavating) {
                // Save the return trail from the excavation mouth, not the old start.
                trail.points.clear();
                trail.dirty = false;
                trail.excavating = true;
            }
            recordAccessPoint(x, y, z);
        }
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            singleStep(mc, x, y, z, true);
            return true;
        }
        if (snowRecoveryTarget != null) {
            int[] snow = snowRecoveryTarget;
            if (Math.abs(snow[0] - x) + Math.abs(snow[2] - z) == 1
                && mc.theWorld.getBlock(snow[0], snow[1], snow[2]) == Blocks.snow_layer
                && minePlannedBlock(mc, snow[0], snow[1], snow[2], snow[1])) return true;
            snowRecoveryTarget = null;
        }
        if (excavationRoute.isEmpty() && excavationSearch == null
            && trail.points.size() <= 1
            && roofEscapeGoal == null
            && excavationReplans == 0) {
            excavationRoute.addAll(patternedAccess(mc, x, y, z));
            if (!excavationRoute.isEmpty()) {
                approachPlan = null;
                approachAnchor = null;
            }
        }
        if (approachPlan != null) {
            if (approachAnchor[0] == x && approachAnchor[1] == y && approachAnchor[2] == z) {
                if (approachReady) excavationRoute.addAll(approachPlan.path);
                else excavationSearch = approachPlan;
            }
            approachPlan = null;
            approachAnchor = null;
        }
        if (excavationRoute.isEmpty()) {
            if (excavationSearch == null) {
                excavationSearch = new ExcavationPathfinder(
                    x,
                    y,
                    z,
                    roofEscapeGoal == null ? target.xCoord : roofEscapeGoal[0],
                    roofEscapeGoal == null ? target.yCoord + 1 : roofEscapeGoal[1],
                    roofEscapeGoal == null ? target.zCoord : roofEscapeGoal[2],
                    true);
                rejectedSteps.clear();
            }
            boolean complete = excavationSearch.advance(
                (a, b, c, nx, ny, nz) -> a == x && b == y && c == z && blockedWalkNodes.contains(supportKey(nx, ny, nz))
                    ? -1
                    : excavationTerrain.excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
                24,
                System.nanoTime() + 3_000_000L);
            rejectedSteps.clear();
            rejectedSteps.addAll(excavationSearch.rejected);
            status = "Planning a safe tunnel";
            if (!complete) return true;
            rejectedSteps.clear();
            excavationRoute.addAll(excavationSearch.path);
            excavationSearch = null;
            if (excavationRoute.isEmpty()) return clearSnowForExcavation(mc, x, y, z);
            excavationMode = "Checked access route";
        }
        if (excavationRoute.size() >= 2) {
            int[] first = excavationRoute.get(0), second = excavationRoute.get(1);
            if (first[1] == y && second[1] == y
                && Math.abs(second[0] - x) == 1
                && Math.abs(second[2] - z) == 1
                && safe(mc.theWorld, second[0], y, z)
                && safe(mc.theWorld, x, y, second[2])
                && safe(mc.theWorld, second[0], y, second[2])) {
                singleStep(mc, second[0], y, second[2], false);
                excavationRoute.remove(0);
                excavationRoute.remove(0);
                return true;
            }
        }
        int[] next = excavationRoute.get(0);
        stairX = next[0] - x;
        stairZ = next[2] - z;
        if (!ExcavationPolicy.beside(x, z, next[0], next[2])
            || excavationTerrain.excavationCost(mc.theWorld, x, y, z, next[0], next[1], next[2]) < 0) {
            markBlockedWalkNode(next[0], next[1], next[2]);
            excavationRoute.clear();
            miningAim.reset();
            return ++excavationReplans <= 12;
        }
        if (attemptStairStep(mc, next[1])) return true;
        markBlockedWalkNode(next[0], next[1], next[2]);
        excavationRoute.clear();
        miningAim.reset();
        aimTicks = 0;
        return ++excavationReplans <= 12;
    }

    private boolean clearSnowForExcavation(Minecraft mc, int x, int y, int z) {
        if (target == null || !dryShore(mc.theWorld, x, y, z)) return false;
        for (int[] direction : ExcavationPolicy.approachDirections(x, z, target.xCoord, target.zCoord)) {
            int nx = x + direction[0], nz = z + direction[1];
            for (int by = y + 1; by >= y - 1; by--) {
                if (!mc.theWorld.blockExists(nx, by, nz) || mc.theWorld.getBlock(nx, by, nz) != Blocks.snow_layer
                    || !excavationTerrain.excavatable(mc.theWorld, nx, by, nz)
                    || visibleMiningPoint(mc, nx, by, nz) == null) continue;
                if (by < y && !mc.theWorld.getBlock(nx, by - 1, nz)
                    .isNormalCube()) continue;
                boolean hazard = false;
                for (int dy = -1; dy <= 2; dy++) {
                    if (!mc.theWorld.blockExists(nx, y + dy, nz)) {
                        hazard = true;
                        break;
                    }
                    net.minecraft.block.Block block = mc.theWorld.getBlock(nx, y + dy, nz);
                    if (block.getMaterial()
                        .isLiquid() || ContactHazards.dangerous(block, mc.theWorld.getBlockMetadata(nx, y + dy, nz))) {
                        hazard = true;
                        break;
                    }
                }
                if (hazard) continue;
                snowRecoveryTarget = new int[] { nx, by, nz };
                return minePlannedBlock(mc, nx, by, nz, by);
            }
        }
        return false;
    }

    private boolean selectDigEntrance(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null || target == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround && ExcavationPolicy.closeEntrance(x, z, target.xCoord, target.zCoord)
            && dryShore(mc.theWorld, x, y, z)
            && safe(mc.theWorld, x, y, z)) {
            route.clear();
            extendedFrom = null;
            approachPlan = null;
            walkingSearch = null;
            continuationSearch = null;
            excavationSearch = null;
            excavationRoute.clear();
            digging = true;
            seekingDryEntrance = false;
            entranceSelected = true;
            savedSlot = mc.thePlayer.inventory.currentItem;
            status = "Planning access from the nearest safe structure wall";
            return true;
        }
        digging = false;
        if (findDryEntrance(mc)) {
            walking = true;
            return true;
        }
        return false;
    }

    private boolean findDryEntrance(final Minecraft mc) {
        final World w = mc.theWorld;
        final GroundPathfinder.Terrain approachTerrain = walkingTerrain(w);
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (entranceCandidates.isEmpty()) {
            entranceCandidate = 0;
            int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 },
                { 2, 1 }, { 2, -1 }, { -2, 1 }, { -2, -1 }, { 1, 2 }, { 1, -2 }, { -1, 2 }, { -1, -2 } };
            for (int radius = (int) ExcavationPolicy.MIN_ENTRANCE_RADIUS; radius
                <= ExcavationPolicy.MAX_ENTRANCE_RADIUS; radius += 2) for (int[] direction : directions) {
                    double norm = Math.hypot(direction[0], direction[1]);
                    int gx = target.xCoord + (int) Math.round(direction[0] * radius / norm);
                    int gz = target.zCoord + (int) Math.round(direction[1] * radius / norm);
                    if (!ExcavationPolicy.preferredEntrance(gx, gz, target.xCoord, target.zCoord)) continue;
                    if (!w.blockExists(gx, y, gz)) continue;
                    int surface = w.getHeightValue(gx, gz);
                    int[] heights = { surface - 2, surface - 1, surface, surface + 1, y - 1, y, y + 1 };
                    for (int i = 0; i < heights.length; i++) {
                        int gy = heights[i];
                        boolean duplicate = false;
                        for (int j = 0; j < i; j++) if (heights[j] == gy) duplicate = true;
                        if (!duplicate && gy > 0 && gy < w.getHeight() - 2 && dryShore(w, gx, gy, gz))
                            entranceCandidates.add(new int[] { gx, gy, gz });
                    }
                }
            final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
            final int tx = target.xCoord, tz = target.zCoord;
            entranceCandidates
                .sort((a, b) -> Double.compare(entranceScore(a, px, pz, tx, tz), entranceScore(b, px, pz, tx, tz)));
        }
        for (int attempts = 0; attempts < 1 && entranceCandidate < entranceCandidates.size(); attempts++) {
            int[] goal = entranceCandidates.get(entranceCandidate++);
            if (Math.abs(goal[0] - x) + Math.abs(goal[2] - z) <= 1 && Math.abs(goal[1] - y) <= 1) {
                if (mc.thePlayer.onGround && dryShore(w, x, y, z) && safe(w, x, y, z)) {
                    digging = true;
                    seekingDryEntrance = false;
                    entranceSelected = true;
                    savedSlot = mc.thePlayer.inventory.currentItem;
                    entranceCandidates.clear();
                    status = "Planning access from the nearest safe structure wall";
                    return true;
                }
            }
            List<int[]> candidate = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

                public boolean stand(int a, int b, int c) {
                    return approachTerrain.stand(a, b, c);
                }

                public boolean clear(int a, int b, int c) {
                    return passable(w, a, b, c);
                }

                public boolean checkpoint(int a, int b, int c) {
                    return solidCheckpoint(w, a, b, c);
                }
            }, x, y, z, goal[0], goal[2], 48);
            if (candidate.isEmpty()) continue;
            int[] last = candidate.get(candidate.size() - 1);
            if (last[1] != goal[1] || Math.abs(last[0] - goal[0]) + Math.abs(last[2] - goal[2]) > 1
                || !dryShore(w, last[0], last[1], last[2])) continue;
            if (candidate.size() > Math.max(12D, 2D * Math.hypot(goal[0] - x, goal[2] - z))) continue;
            route.clear();
            route.addAll(candidate);
            walkingSearch = null;
            walkingSearchNeedsResume = false;
            continuationSearch = null;
            extendedFrom = null;
            approachPlan = null;
            approachAnchor = null;
            approachReady = false;
            segmentX = mc.thePlayer.posX;
            segmentZ = mc.thePlayer.posZ;
            seekingDryEntrance = true;
            searching = false;
            entranceCandidates.clear();
            stairX = 0;
            stairZ = 0;
            status = "Moving to selected dry entrance";
            return true;
        }
        if (entranceCandidate < entranceCandidates.size()) {
            status = "Checking alternate dry entrances";
            return true;
        }
        entranceCandidates.clear();
        return false;
    }

    private boolean attemptStairStep(Minecraft mc, int destinationY) {
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround) {
            status = "Waiting to land on the next stair";
            return ++settlingTicks <= 40;
        }
        settlingTicks = 0;
        if (!safe(mc.theWorld, x, y, z)) return false;
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            singleStep(mc, x, y, z, true);
            return true;
        }
        if (stairX == 0 && stairZ == 0) {
            stairX = Integer.signum(target.xCoord - x);
            stairZ = Integer.signum(target.zCoord - z);
            if (Math.abs(target.xCoord - x) >= Math.abs(target.zCoord - z)) stairZ = 0;
            else stairX = 0;
            if (stairX == 0 && stairZ == 0) stairX = 1;
        }
        int nx = x + stairX, nz = z + stairZ;
        if (!ExcavationPolicy.beside(x, z, nx, nz)) return false;
        World w = mc.theWorld;
        for (int by = y; by <= y + 1; by++) {
            net.minecraft.block.Block plant = w.getBlock(x, by, z);
            if (FoliagePolicy.canClear(
                isFoliage(plant),
                w.getTileEntity(x, by, z) != null,
                plant.getBlockHardness(w, x, by, z),
                x,
                by,
                z,
                x,
                y,
                z,
                nx,
                nz,
                plant == Blocks.snow_layer)) return minePlannedBlock(mc, x, by, z, destinationY);
        }
        if (excavationTerrain.excavationCost(w, x, y, z, nx, destinationY, nz) < 0) return false;
        if (destinationY > y && !passable(w, x, y + 2, z)) return minePlannedBlock(mc, x, y + 2, z, y + 2);
        int clearTop = Math.max(y, destinationY) + 1;
        if (!w.blockExists(nx, clearTop + 1, nz)) return false;
        if (clearTop > y + 4) return false;
        for (Object entity : w.loadedEntityList) {
            if (entity instanceof net.minecraft.entity.item.EntityFallingBlock) {
                net.minecraft.entity.Entity falling = (net.minecraft.entity.Entity) entity;
                if ((int) Math.floor(falling.posX) == nx && (int) Math.floor(falling.posZ) == nz
                    && falling.posY >= destinationY
                    && falling.posY <= clearTop + 12) {
                    releaseMiningWhileSandFalls(mc);
                    status = "Waiting for sand";
                    return true;
                }
            }
        }
        for (int by = destinationY; by <= clearTop; by++) {
            if (w.getBlock(nx, by, nz) == Blocks.snow_layer && FoliagePolicy.canClear(
                true,
                w.getTileEntity(nx, by, nz) != null,
                w.getBlock(nx, by, nz)
                    .getBlockHardness(w, nx, by, nz),
                nx,
                by,
                nz,
                x,
                y,
                z,
                nx,
                nz,
                true)) return minePlannedBlock(mc, nx, by, nz, destinationY);
            if (w.getBlock(nx, by, nz) instanceof net.minecraft.block.BlockFalling)
                return minePlannedBlock(mc, nx, by, nz, destinationY);
        }
        for (int by = clearTop; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (passable(w, nx, by, nz)) continue;
            if (FoliagePolicy.canClear(
                isFoliage(b),
                w.getTileEntity(nx, by, nz) != null,
                b.getBlockHardness(w, nx, by, nz),
                nx,
                by,
                nz,
                x,
                y,
                z,
                nx,
                nz,
                b == Blocks.snow_layer)) continue;
            if (!excavationTerrain.excavatable(w, nx, by, nz)) return false;
            if (w.getTileEntity(nx, by, nz) != null || b.getBlockHardness(w, nx, by, nz) < 0) return false;
        }
        for (int by = clearTop; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (isFoliage(b) && FoliagePolicy.canClear(
                true,
                w.getTileEntity(nx, by, nz) != null,
                b.getBlockHardness(w, nx, by, nz),
                nx,
                by,
                nz,
                x,
                y,
                z,
                nx,
                nz,
                b == Blocks.snow_layer)) return minePlannedBlock(mc, nx, by, nz, destinationY);
            if (passable(w, nx, by, nz)) continue;
            return minePlannedBlock(mc, nx, by, nz, destinationY);
        }
        digTicks = 0;
        miningAim.reset();
        if (!safe(w, nx, destinationY, nz)) return false;
        singleStep(mc, nx, destinationY, nz, false);
        excavationTerrain.protectSupport(supportKey(nx, destinationY - 1, nz));
        if (!excavationRoute.isEmpty()) excavationRoute.remove(0);
        int lastX = nx, lastY = destinationY, lastZ = nz;
        while (!excavationRoute.isEmpty() && route.size() < 6) {
            int[] following = excavationRoute.get(0);
            if (!ExcavationPolicy.beside(lastX, lastZ, following[0], following[2]) || Math.abs(following[1] - lastY) > 1
                || !safe(w, following[0], following[1], following[2])
                || excavationTerrain.excavationCost(w, lastX, lastY, lastZ, following[0], following[1], following[2])
                    < 0)
                break;
            boolean clear = true;
            for (int by = following[1]; by <= Math.max(lastY, following[1]) + 1; by++) {
                if (isFoliage(w.getBlock(following[0], by, following[2]))
                    || !passable(w, following[0], by, following[2])) clear = false;
            }
            if (!clear) break;
            route.add(new int[] { following[0], following[1], following[2], 0 });
            excavationTerrain.protectSupport(supportKey(following[0], following[1] - 1, following[2]));
            lastX = following[0];
            lastY = following[1];
            lastZ = following[2];
            excavationRoute.remove(0);
        }
        excavationReplans = 0;
        stairSteps++;
        lastDistance = Double.MAX_VALUE;
        stalled = 0;
        return true;
    }

    private int foliageSupportY(World w, int x, int foliageY, int z, int playerX, int footY, int playerZ,
        int destinationY) {
        net.minecraft.block.Block foliage = w.getBlock(x, foliageY, z);
        if (!FoliagePolicy.canClear(
            isFoliage(foliage),
            w.getTileEntity(x, foliageY, z) != null,
            foliage.getBlockHardness(w, x, foliageY, z),
            x,
            foliageY,
            z,
            playerX,
            footY,
            playerZ,
            x,
            z,
            foliage == Blocks.snow_layer)) return -1;
        int supportY = foliageY;
        for (int scan = 0; scan < 8 && supportY > destinationY - 1; scan++) {
            if (!isFoliage(w.getBlock(x, supportY, z))) break;
            supportY--;
        }
        if (supportY < destinationY || supportY > Math.max(footY, destinationY) + 1
            || !excavationTerrain.excavatable(w, x, supportY, z)) return -1;
        return supportY;
    }

    private boolean minePlannedBlock(Minecraft mc, int nx, int by, int nz, int destinationY) {
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

    private void releaseMiningWhileSandFalls(Minecraft mc) {
        miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        mc.playerController.resetBlockRemoving();
        miningAim.reset();
        digTicks = 0;
    }

    public String hud(Minecraft mc) {
        if (target == null) return "Next Structure";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
