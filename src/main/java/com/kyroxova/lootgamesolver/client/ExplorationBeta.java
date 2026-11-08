package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public final class ExplorationBeta {

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
    private int[] fallingMinedBlock;
    private net.minecraft.block.Block fallingMinedType;
    private int fallingSettleTicks;
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
    private final MiningAimGate placementAim = new MiningAimGate();
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
    private boolean cameraActive;
    private float cameraYaw, cameraPitch;
    private long cameraFrame;
    private int searchGoalX, searchGoalZ;
    private int[] extendedFrom;
    private int approachAttempts;
    private int[] knownStructure;
    private ExcavationPathfinder approachPlan;
    private int[] approachAnchor;
    private boolean approachReady;
    private final InventoryToolTransfer toolTransfer = new InventoryToolTransfer();
    private final MiningAttackInput miningAttack = new MiningAttackInput();
    private long miningTarget = Long.MIN_VALUE;
    private int miningSlot = -1;
    private int aimTicks;
    private final AccessTrail accessTrail = new AccessTrail();
    private final AccessTrailStore accessTrailStore = new AccessTrailStore(
        new File(new File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"), "access-trails.json"));
    private int[] trailTarget;
    private String trailWorldId;
    private int trailDimension;
    private int lastTrailSaveTick;
    private boolean trailDirty;
    private boolean trailExcavating;
    private boolean returning, returnAvailable;
    private int cameraPitchDirection;
    private static final java.util.Map<net.minecraft.block.Block, Boolean> QUICKSAND_BLOCKS = new java.util.IdentityHashMap<net.minecraft.block.Block, Boolean>();
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
    private boolean walkingSearchNeedsResume;
    private boolean roomEntrySearchNeedsResume;
    private List<int[]> roomEntryCandidates;
    private int roomEntryCandidateIndex;
    private int[] activeRoomEntry;
    private boolean roomEntryPlanning;
    private boolean roomEntryCrossing;
    private int[] cachedRoomBounds;
    private int roomBoundsCacheX, roomBoundsCacheZ, roomBoundsCacheTick = -1000;
    private String excavationMode = "Access route";
    private int[] pendingReturnPlacement;
    private int[] pendingReturnWaypoint;
    private int returnPlacementTicks;
    private int returnPlacementAimTicks;
    private int returnPlacementSlot = -1;
    private int routeRetryTicks;
    private String routeRetryStatus = "";
    private List<int[]> chestPitExits;
    private int chestPitExitIndex;
    private int chestPitTrailIndex = -1;
    private int[] chestPitSource;
    private List<int[]> chestPitApproach;
    private GroundPathfinder.Search chestPitApproachSearch;
    private GroundPathfinder.Search chestPitSearch;
    private int chestPitJumpBoostTicks;
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

    private net.minecraft.util.Vec3 visibleMiningPoint(Minecraft mc, int x, int y, int z) {
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        double height = 1D;
        net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
        if (block == Blocks.snow_layer) {
            block.setBlockBoundsBasedOnState(mc.theWorld, x, y, z);
            net.minecraft.util.AxisAlignedBB bounds = block.getSelectedBoundingBoxFromPool(mc.theWorld, x, y, z);
            if (bounds != null) height = Math.max(0.01D, Math.min(1D, bounds.maxY - y));
        }
        double[] offsets = { 0.5D, 0.3D, 0.7D };
        int[] faces = { 2, 3, 4, 5, 1, 0 };
        for (double a : offsets) for (double b : offsets) for (int face : faces) {
            double dx = face == 4 ? 0.02D : face == 5 ? 0.98D : a;
            double dy = face == 0 ? 0.02D : face == 1 ? 0.98D : face >= 4 ? a : b;
            double dz = face == 2 ? 0.02D : face == 3 ? 0.98D : face >= 4 ? b : face <= 1 ? b : a;
            net.minecraft.util.Vec3 point = net.minecraft.util.Vec3.createVectorHelper(x + dx, y + dy * height, z + dz);
            net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
            if (hit != null && hit.blockX == x
                && hit.blockY == y
                && hit.blockZ == z
                && (hit.sideHit == face || isFoliage(block))
                && eye.squareDistanceTo(hit.hitVec)
                    <= Math.pow(Math.min(4.2D, mc.playerController.getBlockReachDistance()), 2))
                return point;
        }
        return null;
    }

    private boolean pausedForControl;
    private GroundPathfinder.Search walkingSearch;
    private int[] snowRecoveryTarget;
    private boolean walkingPlanFailed;
    private GroundPathfinder.Search continuationSearch;
    private final java.util.Set<Long> returnSupports = new java.util.HashSet<Long>();
    private final java.util.Map<String, Boolean> structureTools = new java.util.HashMap<String, Boolean>();
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
            (x, y, z, nx, ny, nz) -> excavationCost(mc.theWorld, x, y, z, nx, ny, nz),
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
        int[] bounds = dungeonRoomBounds(mc.theWorld, x, y, z);
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
        trailExcavating = false;
        entranceSelected = false;
        seekingDryEntrance = false;
        returnAvailable = false;
        stalled = 0;
        stallRecoveries = 0;
        lastDistance = Double.MAX_VALUE;
        target = null;
        knownStructure = null;
        accessTrail.clear();
        trailTarget = null;
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

    private static boolean samePosition(int[] first, int[] second) {
        return first[0] == second[0] && first[1] == second[1] && first[2] == second[2];
    }

    private void beginAccessTrail(Minecraft mc) {
        if (knownStructure == null || mc.thePlayer == null) return;
        if (trailTarget == null || !samePosition(trailTarget, knownStructure)) {
            persistAccessTrail(true);
            existingAccessChecked = false;
            blockedWalkNodes.clear();
            stallRecoveries = 0;
            accessTrail.clear();
            trailTarget = knownStructure.clone();
            trailWorldId = worldIdentity(mc);
            trailDimension = mc.theWorld.provider.dimensionId;
            accessTrail.replace(
                accessTrailStore.load(trailWorldId, trailDimension, trailTarget[0], trailTarget[1], trailTarget[2]));
            trailDirty = false;
            trailExcavating = accessTrail.size() > 1;
        }
    }

    private boolean reuseSavedAccessPath(Minecraft mc) {
        if (mc.thePlayer == null || target == null || accessTrail.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int index = accessTrail.nearestIndex(x, y, z, 1);
        if (index < 0) return false;
        if (index == accessTrail.size() - 1 && safe(mc.theWorld, x, y, z)) {
            route.clear();
            digging = true;
            walking = true;
            searching = false;
            returning = false;
            entranceSelected = true;
            status = "Continuing excavation from saved tunnel";
            return true;
        }
        List<int[]> saved = accessTrail.forwardFrom(index);
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
        if (!trailExcavating) return;
        if (accessTrail.record(x, y, z)) {
            trailDirty = true;
            persistAccessTrail(false);
        }
    }

    private void persistAccessTrail(boolean force) {
        if (!trailDirty || trailTarget == null || trailWorldId == null || accessTrail.size() < 2) return;
        if (!force && ticks - lastTrailSaveTick < 10) return;
        try {
            accessTrailStore.save(
                trailWorldId,
                trailDimension,
                trailTarget[0],
                trailTarget[1],
                trailTarget[2],
                accessTrail.snapshot());
            trailDirty = false;
            lastTrailSaveTick = ticks;
        } catch (java.io.IOException e) {
            status = "Could not save the structure return route";
        }
    }

    private void removePersistedTrail() {
        if (trailTarget == null || trailWorldId == null) return;
        try {
            accessTrailStore.remove(trailWorldId, trailDimension, trailTarget[0], trailTarget[1], trailTarget[2]);
            trailDirty = false;
        } catch (java.io.IOException e) {
            status = "Returned; could not clear the saved route";
        }
    }

    private boolean startSavedReturn(Minecraft mc, boolean resumeSearch, int maxJoinDistance) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        List<int[]> back = accessTrail.reverse(x, y, z);
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
            int nearest = accessTrail.nearestIndex(x, y, z, maxJoinDistance);
            int[] join = null;
            List<int[]> saved = null;
            for (int candidate = nearest; candidate >= 0; candidate--) {
                List<int[]> candidateRoute = accessTrail.reverseFrom(candidate);
                if (candidateRoute.size() < 2 || !savedRouteWalkable(mc, candidateRoute)) continue;
                join = accessTrail.pointAt(candidate);
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
        if (mc.thePlayer == null || mc.theWorld == null || accessTrail.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int trailIndex = accessTrail.nearestIndex(x, y, z, 128);
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
                Math.abs(a[0] - accessTrail.pointAt(trailIndex)[0])
                    + Math.abs(a[2] - accessTrail.pointAt(trailIndex)[2]),
                Math.abs(b[0] - accessTrail.pointAt(trailIndex)[0])
                    + Math.abs(b[2] - accessTrail.pointAt(trailIndex)[2])));
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
        int[] join = accessTrail.pointAt(chestPitTrailIndex);
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
        int[] join = accessTrail.pointAt(chestPitTrailIndex);
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
            List<int[]> saved = accessTrail.reverseFrom(chestPitTrailIndex);
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

    private static void appendRoutePoint(List<int[]> points, int[] point) {
        if (point == null) return;
        if (points.isEmpty() || !samePosition(points.get(points.size() - 1), point)) points.add(point.clone());
    }

    private static boolean isRewardChest(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z) instanceof net.minecraft.block.BlockChest;
    }

    private boolean startRoomExitSearch(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = dungeonRoomBounds(mc.theWorld, x, y, z);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        roomExitCandidates = findRoomExitCandidates(mc, bounds);
        // Prefer room openings nearest the saved tunnel mouth.
        if (accessTrail.size() >= 2 && roomExitCandidates.size() > 1) {
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
        for (int i = accessTrail.size() - 1; i >= 0; i--) {
            int[] point = accessTrail.pointAt(i);
            boolean besideX = (Math.abs(point[0] - bounds[0]) <= 1 || Math.abs(point[0] - bounds[1]) <= 1)
                && point[2] >= bounds[2]
                && point[2] <= bounds[3];
            boolean besideZ = (Math.abs(point[2] - bounds[2]) <= 1 || Math.abs(point[2] - bounds[3]) <= 1)
                && point[0] >= bounds[0]
                && point[0] <= bounds[1];
            if (besideX || besideZ) return point;
        }
        return accessTrail.pointAt(accessTrail.size() - 1);
    }

    private static double exitMouthDistance(int[] candidate, int[] mouth) {
        double dx = candidate[3] - mouth[0];
        double dy = candidate[4] - mouth[1];
        double dz = candidate[5] - mouth[2];
        return dx * dx + 2D * dy * dy + dz * dz;
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
        int[] bounds = dungeonRoomBounds(mc.theWorld, x, y, z);
        if (bounds == null || target.xCoord <= bounds[0]
            || target.xCoord >= bounds[1]
            || target.zCoord <= bounds[2]
            || target.zCoord >= bounds[3]
            || (x > bounds[0] && x < bounds[1] && z > bounds[2] && z < bounds[3] && y <= target.yCoord + 3))
            return false;
        roomEntryCandidates = findRoomExitCandidates(mc, bounds);
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

    private List<int[]> findRoomExitCandidates(Minecraft mc, int[] bounds) {
        List<int[]> found = new ArrayList<int[]>();
        int[][] sides = { { bounds[0], -1, 0 }, { bounds[1], 1, 0 }, { bounds[2], 0, -1 }, { bounds[3], 0, 1 } };
        for (int dy = -3; dy <= 4; dy++) {
            int floorY = (int) Math.floor(mc.thePlayer.boundingBox.minY) + dy;
            for (int[] side : sides) {
                boolean xSide = side[1] != 0;
                int alongMin = xSide ? bounds[2] + 1 : bounds[0] + 1;
                int alongMax = xSide ? bounds[3] - 1 : bounds[1] - 1;
                for (int along = alongMin; along <= alongMax; along++) {
                    int[] opening = RoomOpeningGeometry.positions(side[0], xSide, along, side[1], side[2], floorY);
                    int inX = opening[0], inZ = opening[2], outX = opening[3], outZ = opening[5];
                    int wallX = opening[6], wallZ = opening[8];
                    if (!passable(mc.theWorld, wallX, floorY, wallZ) || !passable(mc.theWorld, wallX, floorY + 1, wallZ)
                        || !(solidCheckpoint(mc.theWorld, outX, floorY, outZ)
                            || clearableExitColumn(mc.theWorld, outX, floorY, outZ)))
                        continue;
                    boolean levelInside = safe(mc.theWorld, inX, floorY, inZ);
                    boolean lowerInside = !levelInside && safe(mc.theWorld, inX, floorY - 1, inZ)
                        && mc.theWorld.getBlock(wallX, floorY - 1, wallZ)
                            .isReplaceable(mc.theWorld, wallX, floorY - 1, wallZ)
                        && mc.theWorld.getBlock(wallX, floorY - 2, wallZ)
                            .isNormalCube();
                    int approachX = inX - side[1], approachZ = inZ - side[2];
                    boolean twoBelow = !levelInside && !lowerInside
                        && safe(mc.theWorld, inX, floorY - 2, inZ)
                        && safe(mc.theWorld, approachX, floorY - 2, approachZ)
                        && passable(mc.theWorld, inX, floorY, inZ)
                        && passable(mc.theWorld, inX, floorY + 1, inZ)
                        && mc.theWorld.getBlock(inX, floorY - 2, inZ)
                            .isReplaceable(mc.theWorld, inX, floorY - 2, inZ);
                    if (!levelInside && !lowerInside && !twoBelow) continue;
                    boolean sideWallA = isDungeonWall(
                        mc.theWorld,
                        xSide ? wallX : along - 1,
                        floorY,
                        xSide ? along - 1 : wallZ)
                        || isDungeonWall(mc.theWorld, xSide ? wallX : along - 1, floorY + 1, xSide ? along - 1 : wallZ);
                    boolean sideWallB = isDungeonWall(
                        mc.theWorld,
                        xSide ? wallX : along + 1,
                        floorY,
                        xSide ? along + 1 : wallZ)
                        || isDungeonWall(mc.theWorld, xSide ? wallX : along + 1, floorY + 1, xSide ? along + 1 : wallZ);
                    if (!sideWallA && !sideWallB) continue;
                    if (twoBelow) found.add(
                        new int[] { approachX, floorY - 2, approachZ, outX, floorY, outZ, wallX, floorY, wallZ, inX,
                            floorY - 1, inZ });
                    else if (lowerInside)
                        found.add(new int[] { inX, floorY - 1, inZ, outX, floorY, outZ, wallX, floorY, wallZ, 1 });
                    else found.add(opening);
                }
            }
        }
        final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        found.sort(
            (first, second) -> Double.compare(
                Math.hypot(first[0] + 0.5D - px, first[2] + 0.5D - pz),
                Math.hypot(second[0] + 0.5D - px, second[2] + 0.5D - pz)));
        return found;
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
                || !passable(world, x, by, z) && !excavatable(world, x, by, z)) return false;
        }
        return true;
    }

    private boolean clearRoomExitObstruction(Minecraft mc, int[] next, int x, int y, int z) {
        World world = mc.theWorld;
        if (next[1] > y && !passable(world, x, y + 2, z)) {
            net.minecraft.block.Block overhead = world.getBlock(x, y + 2, z);
            if (!(overhead instanceof net.minecraft.block.BlockFalling) && excavatable(world, x, y + 2, z))
                return minePlannedBlock(mc, x, y + 2, z, y + 2);
        }
        if (next[0] == x && next[2] == z) return false;
        for (int by = next[1] + 1; by >= next[1]; by--) {
            if (passable(world, next[0], by, next[2])) continue;
            net.minecraft.block.Block block = world.getBlock(next[0], by, next[2]);
            if (block instanceof net.minecraft.block.BlockFalling
                || ContactHazards.dangerous(block, world.getBlockMetadata(next[0], by, next[2]))
                || !excavatable(world, next[0], by, next[2])) return false;
            return minePlannedBlock(mc, next[0], by, next[2], next[1]);
        }
        return false;
    }

    private int[] dungeonRoomBounds(World world, int x, int y, int z) {
        if (cachedRoomBounds != null && ticks - roomBoundsCacheTick < 200
            && Math.abs(x - roomBoundsCacheX) <= 32
            && Math.abs(z - roomBoundsCacheZ) <= 32) return cachedRoomBounds;
        int[] xWallCounts = new int[65], zWallCounts = new int[65];
        for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++) {
            for (int dy = -3; dy <= 4; dy++) {
                int bx = x + dx, by = y + dy, bz = z + dz;
                if (!world.blockExists(bx, by, bz) || !isDungeonWall(world, bx, by, bz)) continue;
                xWallCounts[dx + 32]++;
                zWallCounts[dz + 32]++;
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        // Use dense wall cross-sections as room edges; tunnel walls are sparse.
        for (int i = 0; i < 65; i++) {
            if (xWallCounts[i] >= 24) {
                minX = Math.min(minX, x + i - 32);
                maxX = Math.max(maxX, x + i - 32);
            }
            if (zWallCounts[i] >= 24) {
                minZ = Math.min(minZ, z + i - 32);
                maxZ = Math.max(maxZ, z + i - 32);
            }
        }
        roomBoundsCacheX = x;
        roomBoundsCacheZ = z;
        roomBoundsCacheTick = ticks;
        cachedRoomBounds = minX == Integer.MAX_VALUE || maxX - minX < 8
            || maxZ - minZ < 8
            || minX <= x - 31
            || maxX >= x + 31
            || minZ <= z - 31
            || maxZ >= z + 31 ? null : new int[] { minX, maxX, minZ, maxZ };
        return cachedRoomBounds;
    }

    private static boolean isDungeonWall(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return false;
        String name = world.getBlock(x, y, z)
            .getClass()
            .getName();
        return name.endsWith(".DungeonBrick") || name.endsWith(".DungeonLightSource") || name.endsWith(".DungeonWall");
    }

    private boolean insideDungeonRoom(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = dungeonRoomBounds(mc.theWorld, x, y, z);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        int roomY = knownStructure != null ? knownStructure[1] : target == null ? Integer.MIN_VALUE : target.yCoord;
        return roomY != Integer.MIN_VALUE && y <= roomY + 3;
    }

    private boolean planRoofEscape(Minecraft mc) {
        if (target == null || knownStructure == null || returnDigging || returning) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (y <= target.yCoord + 3 || !isDungeonWall(mc.theWorld, x, y - 1, z)) return false;
        int[] bounds = dungeonRoomBounds(mc.theWorld, x, y, z);
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
        if (savedRoute.size() < 2 && accessTrail.size() >= 2) {
            int nearest = accessTrail.nearestIndex(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                128);
            savedRoute = accessTrail.reverseFrom(nearest);
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
        long now = System.nanoTime();
        double seconds = cameraFrame == 0 ? 1D / 60D : (now - cameraFrame) / 1_000_000_000D;
        cameraFrame = now;
        if (!walking || !cameraActive
            || mc.thePlayer == null
            || mc.theWorld != world
            || mc.currentScreen != null
            || mc.isGamePaused()) return;
        float yaw = NavigationSteering.cameraStep(mc.thePlayer.rotationYaw, cameraYaw, seconds);
        float pitch = NavigationSteering.cameraStep(mc.thePlayer.rotationPitch, cameraPitch, seconds);
        mc.thePlayer.setAngles(yaw / 0.15F, -pitch / 0.15F);
    }

    private void look(float yaw, float pitch) {
        cameraYaw = yaw;
        cameraPitch = pitch;
        cameraActive = true;
    }

    private void singleStep(Minecraft mc, int x, int y, int z, boolean center) {
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        route.add(new int[] { x, y, z, center ? 2 : 0 });
    }

    private static int staircaseDirection(List<int[]> steps, int startY) {
        int direction = 0, transitions = 0, previousY = startY;
        for (int i = 0; i < Math.min(8, steps.size()); i++) {
            int y = steps.get(i)[1];
            int nextDirection = Integer.compare(y, previousY);
            previousY = y;
            if (nextDirection == 0) continue;
            if (direction == 0 && i > 1) return 0;
            if (direction != 0 && nextDirection != direction) return 0;
            direction = nextDirection;
            transitions++;
        }
        return transitions >= 3 ? direction : 0;
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
        if (accessTrail.size() < 2) {
            int[] saved = accessTrailStore.nearestStructure(
                worldIdentity(mc),
                mc.theWorld.provider.dimensionId,
                center[0],
                center[1],
                center[2],
                24);
            if (saved != null) {
                knownStructure = saved;
                trailTarget = null;
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
        boolean removed = accessTrail.removeLast();
        if (removed) {
            trailDirty = true;
            if (accessTrail.size() >= 2) persistAccessTrail(true);
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
            if (accessTrail.size() >= 2) {
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
        int nearbyTrailIndex = accessTrail.nearestIndex(playerX, playerY, playerZ, 24);
        // Reuse a persisted trail only while near one of its checkpoints.
        if (nearbyTrailIndex < 0 && trailTarget != null && accessTrail.size() > 0) {
            accessTrail.clear();
            trailTarget = knownStructure == null ? null : knownStructure.clone();
            trailExcavating = false;
            trailDirty = false;
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
        cameraActive = false;
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
            accessTrail.clear();
            trailTarget = null;
            puzzleArrivalPending = false;
            puzzleCenterArrivalPending = false;
            movingToPuzzleCenter = false;
            puzzleMasterTarget = null;
            resumeSearchAfterReturn = false;
            planningExistingAccess = false;
            existingAccessPlan = null;
            returning = false;
            returnAvailable = false;
            returnSupports.clear();
            cachedRoomBounds = null;
            roomBoundsCacheTick = -1000;
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
            structureTools.clear();
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
        if (pendingReturnPlacement != null) {
            int px = pendingReturnPlacement[0], py = pendingReturnPlacement[1], pz = pendingReturnPlacement[2];
            if (mc.theWorld.getBlock(px, py, pz) != Blocks.air && mc.theWorld.getBlock(px, py, pz)
                .getMaterial()
                .isSolid()) {
                if (pendingReturnWaypoint != null && returning) {
                    if (!route.isEmpty() && route.get(0)[0] == pendingReturnWaypoint[0]
                        && route.get(0)[2] == pendingReturnWaypoint[2]
                        && route.get(0)[1] == pendingReturnWaypoint[1] + 1) route.remove(0);
                    if (route.isEmpty() || !samePosition(route.get(0), pendingReturnWaypoint))
                        route.add(0, pendingReturnWaypoint);
                    pendingReturnWaypoint = null;
                }
                if (returnPlacementSlot >= 0) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
                mc.playerController.updateController();
                pendingReturnPlacement = null;
                returnPlacementSlot = -1;
            } else if (++returnPlacementTicks > 30) {
                if (returnPlacementSlot >= 0) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
                pendingReturnPlacement = null;
                pendingReturnWaypoint = null;
                returnPlacementSlot = -1;
                stop(mc, "Could not place a safe return step; add cobblestone or dirt");
                return;
            } else {
                status = "Waiting for return step placement";
                return;
            }
        }
        if (ticks % 20 == 0) structureTools.clear();
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
        if (sustain.tick(mc, true, !route.isEmpty() && !digging && !returnDigging && returnPlacementSlot < 0)) {
            status = "Eating or preparing food";
            return;
        }
        if (returning && resumeSearchAfterReturn && !insideDungeonRoom(mc)) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            int[] entrance = accessTrail.pointAt(0);
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
                        int index = accessTrail.nearestIndex(x, y, z, 0);
                        if (accessTrail.size() >= 2) return index >= 0 && index < accessTrail.size() - 1;
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
            if (mc.thePlayer.onGround && trailTarget != null) recordAccessPoint(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
            returnAvailable = trailTarget != null && trailTarget[0] == target.xCoord
                && trailTarget[1] == target.yCoord
                && trailTarget[2] == target.zCoord
                && (!accessTrail
                    .reverse(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ))
                    .isEmpty()
                    || accessTrail.nearestIndex(
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
            && accessTrail.size() <= 1
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
                (a, b, c, nx, ny, nz) -> excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
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
                trailExcavating = false;
                if (resumeSearchAfterReturn) {
                    continueProgressiveSearch(mc, true);
                } else {
                    accessTrail.clear();
                    trailTarget = null;
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
        if (!returning && trailTarget != null && mc.thePlayer.onGround) recordAccessPoint(px, py, pz);
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
            if (!returning && trailTarget != null) recordAccessPoint(px, py, pz);
            route.remove(0);
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
            if (!route.isEmpty()) {
                int[] failed = route.get(0);
                markBlockedWalkNode(failed[0], failed[1], failed[2]);
            }
            if (returning) {
                if (isRewardChestPit(mc)) {
                    chestPitJumpBoostTicks = 12;
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
        boolean shortClearRun = route.size() >= 2 && next[1] == py
            && route.get(1)[1] == py
            && distance > 0.85D
            && Math.abs(turn) < 16F;
        boolean straight = !digging && !mc.thePlayer.isInWater()
            && next[1] == py
            && (movingToPuzzleCenter || shortClearRun
                || route.size() == 1 && distance > 0.35D
                || route.size() >= 3 && !mc.thePlayer.isInWater()
                    && route.get(1)[1] == py
                    && route.get(2)[1] == py
                    && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
                    && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
                    && safe(mc.theWorld, route.get(1)[0], py, route.get(1)[2])
                    && safe(mc.theWorld, route.get(2)[0], py, route.get(2)[2]));
        if (navigationInput != null) {
            navigationInput.forward = Math.abs(turn) > 35F ? 0F
                : route.size() == 1 && !movingToPuzzleCenter ? (float) Math.min(1D, distance * 1.5D) : 1F;
            net.minecraft.block.Block nextBlock = mc.theWorld.getBlock(next[0], next[1], next[2]);
            net.minecraft.util.AxisAlignedBB nextBox = nextBlock
                .getCollisionBoundingBoxFromPool(mc.theWorld, next[0], next[1], next[2]);
            boolean climbRewardChest = nextBlock instanceof net.minecraft.block.BlockChest && nextBox != null
                && nextBox.maxY - mc.thePlayer.boundingBox.minY > 0.45D
                && nextBox.maxY - mc.thePlayer.boundingBox.minY <= 1.0D;
            boolean escapeRewardPit = returning && rewardChestAhead(mc, dx, dz);
            boolean climbSurface = NavigationSurfacePolicy
                .needsJump(mc.thePlayer.boundingBox.minY, walkSurfaceY(mc.theWorld, next[0], next[1], next[2]));
            boolean chestPitJump = returning && chestPitJumpBoostTicks > 0
                && isRewardChestPit(mc)
                && mc.thePlayer.onGround
                && Math.abs(turn) < 45F
                && distance < 4D;
            if (chestPitJumpBoostTicks > 0) chestPitJumpBoostTicks--;
            navigationInput.hop = ((next[1] > py || climbRewardChest || climbSurface) && Math.abs(turn) < 35F
                && distance < 1.4D) || escapeRewardPit && Math.abs(turn) < 35F && distance < 2.5D
                || chestPitJump
                || mc.thePlayer.isInWater();
            if (distance > 0.02D) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                double speed = Math.min(
                    centering ? (movingToPuzzleCenter ? 0.75D : 0.4D) : straight ? 1D : 0.95D,
                    !centering && route.size() > 1 ? 1D : distance * 1.5D);
                navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
                navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
            }
            mc.thePlayer.setSprinting(
                !centering && !digging
                    && !mc.thePlayer.isInWater()
                    && Math.abs(turn) < 35F
                    && navigationInput.forward > 0.8F);
        }
    }

    private boolean rewardChestAhead(Minecraft mc, double dx, double dz) {
        if (!mc.thePlayer.onGround || dx * dx + dz * dz < 0.001D) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            net.minecraft.block.Block block = mc.theWorld.getBlock(x + ox, y, z + oz);
            if (!(block instanceof net.minecraft.block.BlockChest)) continue;
            double cx = x + ox + 0.5D - mc.thePlayer.posX, cz = z + oz + 0.5D - mc.thePlayer.posZ;
            if (cx * dx + cz * dz <= 0 || cx * cx + cz * cz > 2.25D) continue;
            net.minecraft.util.AxisAlignedBB box = block
                .getCollisionBoundingBoxFromPool(mc.theWorld, x + ox, y, z + oz);
            if (box != null && box.maxY - mc.thePlayer.boundingBox.minY > 0.45D
                && box.maxY - mc.thePlayer.boundingBox.minY <= 1D) return true;
        }
        return false;
    }

    private boolean jumpOutOfRewardPit(Minecraft mc) {
        if (navigationInput == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int nearest = accessTrail.nearestIndex(x, y, z, 128);
        int[] goal = nearest < 0 ? null : accessTrail.pointAt(nearest);
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] direction : directions) {
            int nx = x + direction[0], nz = z + direction[1];
            if (!isRewardChest(mc.theWorld, nx, y, nz) || !passable(mc.theWorld, nx, y + 1, nz)
                || !passable(mc.theWorld, x, y + 2, z)) continue;
            double score = goal == null ? 0D : Math.hypot(nx - goal[0], nz - goal[2]);
            if (score < bestScore) {
                best = direction;
                bestScore = score;
            }
        }
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

    private boolean isRewardChestPit(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int sides = 0;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        for (int[] direction : directions) {
            boolean chest = false;
            for (int dy = 0; dy <= 1 && !chest; dy++) {
                int cx = x + direction[0], cy = y + dy, cz = z + direction[1];
                if (!isRewardChest(mc.theWorld, cx, cy, cz)) continue;
                net.minecraft.util.AxisAlignedBB box = mc.theWorld.getBlock(cx, cy, cz)
                    .getCollisionBoundingBoxFromPool(mc.theWorld, cx, cy, cz);
                chest = box != null && box.maxY > mc.thePlayer.boundingBox.minY + 0.3D;
            }
            if (chest) sides++;
        }
        return sides >= 3;
    }

    private boolean tryPlaceReturnStep(Minecraft mc, int[] next) {
        if (!returning || mc.thePlayer == null || !mc.thePlayer.onGround || next == null) return false;
        int playerX = (int) Math.floor(mc.thePlayer.posX);
        int playerY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int playerZ = (int) Math.floor(mc.thePlayer.posZ);
        if (next[1] <= playerY || next[1] > playerY + 2
            || Math.abs(next[0] - playerX) > 1
            || Math.abs(next[2] - playerZ) > 1
            || Math.abs(next[0] - playerX) + Math.abs(next[2] - playerZ) == 0
            || Math.hypot(mc.thePlayer.posX - (next[0] + 0.5D), mc.thePlayer.posZ - (next[2] + 0.5D)) > 1.8D)
            return false;
        if (next[0] != playerX && next[2] != playerZ
            && (!safe(mc.theWorld, next[0], playerY, playerZ) || !safe(mc.theWorld, playerX, playerY, next[2])))
            return false;
        boolean needsIntermediateStep = next[1] == playerY + 2;
        int x = next[0], y = needsIntermediateStep ? playerY : next[1] - 1, z = next[2];
        net.minecraft.block.Block targetBlock = mc.theWorld.getBlock(x, y, z);
        if (pendingReturnPlacement != null) return true;
        if (!targetBlock.isReplaceable(mc.theWorld, x, y, z)) return false;
        int slot = findPlaceableBlock(mc, true);
        if (slot < 0) {
            int inventorySlot = findPlaceableBlock(mc, false);
            if (inventorySlot >= 9 && toolTransfer.start(mc, inventorySlot)) {
                if (returnPlacementSlot < 0) returnPlacementSlot = mc.thePlayer.inventory.currentItem;
                status = "Opening inventory to prepare a return step block";
                return true;
            }
            status = "Return step needs cobblestone or dirt";
            return false;
        }
        if (returnPlacementSlot < 0) returnPlacementSlot = mc.thePlayer.inventory.currentItem;
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.updateController();
            placementAim.reset();
            status = "Preparing return step";
            return true;
        }
        if (mc.theWorld.getBlock(x, y, z) != Blocks.air && !targetBlock.isReplaceable(mc.theWorld, x, y, z))
            return false;
        // Prefer a support block directly below the placement target.
        int[][] adjacent = { { x, y - 1, z, 1 }, { x - 1, y, z, 5 }, { x + 1, y, z, 4 }, { x, y, z - 1, 3 },
            { x, y, z + 1, 2 } };
        if (++returnPlacementAimTicks > 100) {
            returnPlacementAimTicks = 0;
            restoreReturnPlacementSlot(mc);
            return false;
        }
        for (int sideIndex = 0; sideIndex < adjacent.length; sideIndex++) {
            int[] side = adjacent[(sideIndex + (returnPlacementAimTicks - 1) / 20) % adjacent.length];
            net.minecraft.block.Block support = mc.theWorld.getBlock(side[0], side[1], side[2]);
            if (support == Blocks.air || !support.getMaterial()
                .isSolid() || mc.theWorld.getTileEntity(side[0], side[1], side[2]) != null) continue;
            double hx = side[0] + 0.5D + (x - side[0]) * 0.49D;
            double hy = side[1] + 0.5D + (y - side[1]) * 0.49D;
            double hz = side[2] + 0.5D + (z - side[2]) * 0.49D;
            net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
            net.minecraft.util.MovingObjectPosition visible = mc.theWorld
                .rayTraceBlocks(eye, net.minecraft.util.Vec3.createVectorHelper(hx, hy, hz));
            if (visible == null || visible.blockX != side[0]
                || visible.blockY != side[1]
                || visible.blockZ != side[2]
                || visible.sideHit != side[3]
                || eye.squareDistanceTo(visible.hitVec) > 16D) continue;
            net.minecraft.util.Vec3 hit = visible.hitVec;
            hx = hit.xCoord;
            hy = hit.yCoord;
            hz = hit.zCoord;
            double dx = hx - mc.thePlayer.posX, dz = hz - mc.thePlayer.posZ;
            double dy = hy - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            float yaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
            float pitch = (float) (-Math.atan2(dy, Math.hypot(dx, dz)) * 180D / Math.PI);
            look(yaw, pitch);
            net.minecraft.util.MovingObjectPosition aimed = mc.objectMouseOver;
            boolean matches = aimed != null
                && aimed.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
                && aimed.blockX == side[0]
                && aimed.blockY == side[1]
                && aimed.blockZ == side[2]
                && aimed.sideHit == side[3];
            if (!placementAim.ready(
                side[0],
                side[1],
                side[2],
                matches,
                Math.sqrt(eye.squareDistanceTo(hit)),
                Math.min(4D, mc.playerController.getBlockReachDistance()),
                net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw),
                pitch - mc.thePlayer.rotationPitch)) {
                status = "Aiming at return step support";
                return true;
            }
            if (mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                side[0],
                side[1],
                side[2],
                side[3],
                aimed.hitVec)) {
                pendingReturnPlacement = new int[] { x, y, z };
                returnPlacementAimTicks = 0;
                placementAim.reset();
                pendingReturnWaypoint = needsIntermediateStep ? new int[] { next[0], playerY + 1, next[2] } : null;
                returnPlacementTicks = 0;
                status = "Placing a checked block for the return step";
                return true;
            }
        }
        restoreReturnPlacementSlot(mc);
        return false;
    }

    private void restoreReturnPlacementSlot(Minecraft mc) {
        placementAim.reset();
        if (returnPlacementSlot >= 0) {
            mc.thePlayer.inventory.currentItem = returnPlacementSlot;
            mc.playerController.updateController();
            returnPlacementSlot = -1;
        }
    }

    private int findPlaceableBlock(Minecraft mc, boolean hotbarOnly) {
        int limit = hotbarOnly ? 9 : mc.thePlayer.inventory.mainInventory.length;
        int fallback = -1;
        for (int i = hotbarOnly ? 0 : 9; i < limit; i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof net.minecraft.item.ItemBlock)) continue;
            net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) stack.getItem()).field_150939_a;
            if (block == null || block instanceof net.minecraft.block.BlockFalling
                || block.getMaterial()
                    .isLiquid()
                || block.hasTileEntity(0)
                || !block.getMaterial()
                    .isSolid()
                || !block.isNormalCube()) continue;
            if (block == Blocks.cobblestone) return i;
            if (block == Blocks.dirt) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
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
        fallingMinedBlock = null;
        fallingMinedType = null;
        fallingSettleTicks = 0;
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
        if (returnPlacementSlot >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
        pendingReturnPlacement = null;
        pendingReturnWaypoint = null;
        returnPlacementAimTicks = 0;
        returnPlacementSlot = -1;
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
        cameraActive = false;
        cameraFrame = 0;
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
        String currentWorld = worldIdentity(mc);
        double nearest = Double.MAX_VALUE;
        if (savedFinder != null) {
            for (int[] position : savedFinder.positions()) {
                if (solvedStructures
                    .contains(currentWorld, mc.theWorld.provider.dimensionId, position[0], position[1], position[2]))
                    continue;
                double distance = mc.thePlayer
                    .getDistanceSq(position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
                if (distance < nearest) {
                    if (mc.theWorld.blockExists(position[0], position[1], position[2])
                        && !(mc.theWorld.getTileEntity(position[0], position[1], position[2]) instanceof TileEntity))
                        continue;
                    nearest = distance;
                    target = new TileEntity();
                    target.xCoord = position[0];
                    target.yCoord = position[1];
                    target.zCoord = position[2];
                }
            }
        }
        for (Object entry : mc.theWorld.loadedTileEntityList) {
            if (!(entry instanceof TileEntity)) continue;
            TileEntity tile = (TileEntity) entry;
            if (tile.isInvalid() || !tile.getClass()
                .getName()
                .startsWith("ru.timeconqueror.lootgames.")) continue;
            if (solvedStructures
                .contains(currentWorld, mc.theWorld.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord))
                continue;
            try {
                boolean unopened = tile.getClass()
                    .getName()
                    .equals("ru.timeconqueror.lootgames.common.block.tile.PuzzleMasterTile");
                Object game = unopened ? tile
                    : tile.getClass()
                        .getMethod("getGame")
                        .invoke(tile);
                if (game == null) continue;
                double distance = mc.thePlayer
                    .getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D, tile.zCoord + 0.5D);
                if (distance < nearest) {
                    nearest = distance;
                    target = tile;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        if (target != null) {
            knownStructure = new int[] { target.xCoord, target.yCoord, target.zCoord };
            waypointLabelY = target.yCoord + 3D;
            if (mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
                for (int y = Math.min(254, mc.theWorld.getHeightValue(target.xCoord, target.zCoord)); y
                    >= target.yCoord; y--) {
                    net.minecraft.block.Block block = mc.theWorld.getBlock(target.xCoord, y, target.zCoord);
                    if (block.isNormalCube() && block.getMaterial() != net.minecraft.block.material.Material.leaves
                        && block.getMaterial() != net.minecraft.block.material.Material.wood) {
                        waypointLabelY = y + 3D;
                        break;
                    }
                }
            }
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
        return new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return !blockedWalkNodes.contains(supportKey(x, y, z)) && terrainWorld.blockExists(x, y, z)
                    && NavigationSurfacePolicy.allow(
                        surfaceStart,
                        protectedJourney,
                        structureApproach,
                        terrainWorld.getHeightValue(x, z),
                        y,
                        x,
                        z,
                        gx,
                        gz)
                    && (safe(terrainWorld, x, y, z) || movingToPuzzleCenter && target != null
                        && x == target.xCoord
                        && y == target.yCoord + 1
                        && z == target.zCoord
                        && puzzleCenterStandable(terrainWorld, x, y, z));
            }

            public boolean clear(int x, int y, int z) {
                return passable(terrainWorld, x, y, z);
            }

            public boolean checkpoint(int x, int y, int z) {
                return solidCheckpoint(terrainWorld, x, y, z);
            }

            public int extraCost(int x, int y, int z) {
                return terrainWorld.getBlock(x, y, z)
                    .getMaterial() == net.minecraft.block.material.Material.water ? 12 : 0;
            }

            public int transitionCost(int x, int y, int z, int nx, int ny, int nz) {
                int cost = Math.abs(ny - y) == 1 ? 4 : 0;
                if (terrainWorld.getBlock(nx, ny - 1, nz)
                    .getMaterial()
                    .isLiquid()) cost += 8;
                return cost;
            }

            public boolean canTransition(int x, int y, int z, int nx, int ny, int nz) {
                boolean waterAtFeet = terrainWorld.getBlock(x, y, z)
                    .getMaterial() == net.minecraft.block.material.Material.water;
                boolean waterBelow = terrainWorld.getBlock(x, y - 1, z)
                    .getMaterial() == net.minecraft.block.material.Material.water;
                boolean destinationWater = terrainWorld.getBlock(nx, ny, nz)
                    .getMaterial() == net.minecraft.block.material.Material.water
                    || terrainWorld.getBlock(nx, ny - 1, nz)
                        .getMaterial() == net.minecraft.block.material.Material.water;
                if ((waterAtFeet || waterBelow) && !destinationWater && ny > (waterAtFeet ? y + 1 : y)) return false;
                return NavigationSurfacePolicy
                    .reachableRise(walkSurfaceY(terrainWorld, x, y, z), walkSurfaceY(terrainWorld, nx, ny, nz));
            }
        };
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

    private static boolean passable(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z)
            .getMaterial() != net.minecraft.block.material.Material.leaves
            && !world.getBlock(x, y, z)
                .getMaterial()
                .isLiquid()
            && world.getBlock(x, y, z)
                .getCollisionBoundingBoxFromPool(world, x, y, z) == null;
    }

    private static boolean puzzleCenterStandable(World world, int x, int y, int z) {
        if (!world.blockExists(x, y - 1, z) || !passable(world, x, y, z) || !passable(world, x, y + 1, z)) return false;
        net.minecraft.block.Block floor = world.getBlock(x, y - 1, z);
        net.minecraft.util.AxisAlignedBB support = floor.getCollisionBoundingBoxFromPool(world, x, y - 1, z);
        if (support == null || support.maxY < y - 0.05D
            || floor.getMaterial()
                .isLiquid())
            return false;
        return world
            .func_147461_a(
                net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(x + 0.19D, y + 0.01D, z + 0.19D, x + 0.81D, y + 1.81D, z + 0.81D))
            .isEmpty();
    }

    private List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
        int goalY = target.yCoord + 1;
        ExcavationPathfinder.Terrain terrain = (a, b, c, nx, ny,
            nz) -> excavationCost(mc.theWorld, a, b, c, nx, ny, nz);
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
        if (waitingForFallingBlock(mc)) return true;
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
        returnSupports.add(supportKey(x, y - 1, z));
        if (!returnDigging) {
            if (trailTarget == null || trailTarget[0] != target.xCoord
                || trailTarget[1] != target.yCoord
                || trailTarget[2] != target.zCoord) {
                persistAccessTrail(true);
                accessTrail.clear();
                trailTarget = new int[] { target.xCoord, target.yCoord, target.zCoord };
                trailWorldId = worldIdentity(mc);
                trailDimension = mc.theWorld.provider.dimensionId;
                accessTrail.replace(
                    accessTrailStore
                        .load(trailWorldId, trailDimension, trailTarget[0], trailTarget[1], trailTarget[2]));
                trailDirty = false;
                trailExcavating = accessTrail.size() > 1;
            }
            if (!trailExcavating) {
                // Save the return trail from the excavation mouth, not the old start.
                accessTrail.clear();
                trailDirty = false;
                trailExcavating = true;
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
            && accessTrail.size() <= 1
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
                    : excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
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
            || excavationCost(mc.theWorld, x, y, z, next[0], next[1], next[2]) < 0) {
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

    private boolean excavatable(World w, int x, int y, int z) {
        if (!w.blockExists(x, y, z) || w.getTileEntity(x, y, z) != null) return false;
        if (returnSupports.contains(supportKey(x, y, z))) return false;
        net.minecraft.block.Block block = w.getBlock(x, y, z);
        float hardness = block.getBlockHardness(w, x, y, z);
        net.minecraft.block.material.Material material = block.getMaterial();
        boolean structure = block.getClass()
            .getName()
            .equals("eu.usrv.legacylootgames.blocks.DungeonBrick")
            || block.getClass()
                .getName()
                .equals("eu.usrv.legacylootgames.blocks.DungeonLightSource");
        if (structure) {
            if (hardness < 0 || hardness > 10) return false;
            String toolKey = block.getClass()
                .getName() + ":"
                + w.getBlockMetadata(x, y, z);
            Boolean harvestable = structureTools.get(toolKey);
            if (harvestable == null) {
                harvestable = DigToolSelector.canHarvest(Minecraft.getMinecraft(), block, w.getBlockMetadata(x, y, z));
                structureTools.put(toolKey, harvestable);
            }
            return harvestable;
        }
        // Falling blocks in the target column are cleared from the bottom up.
        return hardness >= 0 && hardness <= 10
            && !material.isLiquid()
            && block != Blocks.fire
            && block != Blocks.cactus
            && block != Blocks.portal;
    }

    private boolean clearSnowForExcavation(Minecraft mc, int x, int y, int z) {
        if (target == null || !dryShore(mc.theWorld, x, y, z)) return false;
        for (int[] direction : ExcavationPolicy.approachDirections(x, z, target.xCoord, target.zCoord)) {
            int nx = x + direction[0], nz = z + direction[1];
            for (int by = y + 1; by >= y - 1; by--) {
                if (!mc.theWorld.blockExists(nx, by, nz) || mc.theWorld.getBlock(nx, by, nz) != Blocks.snow_layer
                    || !excavatable(mc.theWorld, nx, by, nz)
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

    private int excavationCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        for (int by = ny - 1; by <= ny + 1; by++) if (isQuicksand(w, nx, by, nz)) return -1;
        if (!w.blockExists(nx, ny - 1, nz) || !w.getBlock(nx, ny - 1, nz)
            .isNormalCube()) return -1;
        boolean overhead = ny > y && !passable(w, x, y + 2, z);
        if (overhead && w.getBlock(x, y + 2, z) instanceof net.minecraft.block.BlockFalling) return -1;
        if (overhead && !excavatable(w, x, y + 2, z)) return -1;
        int cost = 1 + Math.abs(ny - y), firstBreak = Integer.MAX_VALUE, lastBreak = Integer.MIN_VALUE;
        if (overhead) {
            cost += 2;
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) for (int by = y + 1; by <= y + 3; by++) {
                net.minecraft.block.Block block = w.getBlock(x + ox, by, z + oz);
                if (!w.blockExists(x + ox, by, z + oz) || block.getMaterial()
                    .isLiquid() || ox == 0 && oz == 0 && block instanceof net.minecraft.block.BlockFalling) return -1;
            }
        }
        int clearanceTop = Math.max(y, ny) + 1;
        if (!w.blockExists(nx, clearanceTop + 1, nz)) return -1;
        if (clearanceTop > y + 4) return -1;
        for (int by = ny; by <= clearanceTop; by++) {
            if (passable(w, nx, by, nz)) continue;
            if (!excavatable(w, nx, by, nz)) return -1;
            firstBreak = Math.min(firstBreak, by);
            lastBreak = Math.max(lastBreak, by);
            net.minecraft.block.Block block = w.getBlock(nx, by, nz);
            cost += Math.max(1, (int) Math.ceil(block.getBlockHardness(w, nx, by, nz) / 2F));
            if (block.getMaterial() == net.minecraft.block.material.Material.wood) cost += 4;
        }
        for (int ox = -1; ox <= 1; ox++)
            for (int oz = -1; oz <= 1; oz++) for (int by = ny - 1; by <= Math.max(y, ny) + 2; by++) {
                if (!w.blockExists(nx + ox, by, nz + oz)) return -1;
                net.minecraft.block.Block block = w.getBlock(nx + ox, by, nz + oz);
                if (ContactHazards.dangerous(block, w.getBlockMetadata(nx + ox, by, nz + oz))) return -1;
                if ((block.getMaterial()
                    .isLiquid() && by >= firstBreak - 1
                    && by <= lastBreak + 1) || block == Blocks.fire || block == Blocks.cactus || block == Blocks.portal)
                    return -1;
            }
        return cost;
    }

    private static boolean dryShore(World world, int x, int y, int z) {
        if (!solidCheckpoint(world, x, y, z)) return false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 1; dy++) {
            if (!world.blockExists(x + dx, y + dy, z + dz) || world.getBlock(x + dx, y + dy, z + dz)
                .getMaterial()
                .isLiquid()) return false;
        }
        return true;
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

    private static double entranceScore(int[] candidate, double x, double z, int tx, int tz) {
        double travel = Math.hypot(candidate[0] + 0.5D - x, candidate[2] + 0.5D - z);
        double approach = Math.hypot(candidate[0] - tx, candidate[2] - tz);
        return 0.5D * travel + 2D * Math.max(0D, approach - ExcavationPolicy.MIN_ENTRANCE_RADIUS);
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
        if (excavationCost(w, x, y, z, nx, destinationY, nz) < 0) return false;
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
            if (!excavatable(w, nx, by, nz)) return false;
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
        returnSupports.add(supportKey(nx, destinationY - 1, nz));
        if (!excavationRoute.isEmpty()) excavationRoute.remove(0);
        int lastX = nx, lastY = destinationY, lastZ = nz;
        while (!excavationRoute.isEmpty() && route.size() < 6) {
            int[] following = excavationRoute.get(0);
            if (!ExcavationPolicy.beside(lastX, lastZ, following[0], following[2]) || Math.abs(following[1] - lastY) > 1
                || !safe(w, following[0], following[1], following[2])
                || excavationCost(w, lastX, lastY, lastZ, following[0], following[1], following[2]) < 0) break;
            boolean clear = true;
            for (int by = following[1]; by <= Math.max(lastY, following[1]) + 1; by++) {
                if (isFoliage(w.getBlock(following[0], by, following[2]))
                    || !passable(w, following[0], by, following[2])) clear = false;
            }
            if (!clear) break;
            route.add(new int[] { following[0], following[1], following[2], 0 });
            returnSupports.add(supportKey(following[0], following[1] - 1, following[2]));
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

    private static boolean isFoliage(net.minecraft.block.Block block) {
        net.minecraft.block.material.Material material = block.getMaterial();
        return block == Blocks.snow_layer || block instanceof net.minecraft.block.BlockBush
            || material == net.minecraft.block.material.Material.plants
            || material == net.minecraft.block.material.Material.vine
            || material == net.minecraft.block.material.Material.leaves;
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
        if (supportY < destinationY || supportY > Math.max(footY, destinationY) + 1 || !excavatable(w, x, supportY, z))
            return -1;
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
                && excavatable(w, obstruction.blockX, obstruction.blockY, obstruction.blockZ);
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
            fallingMinedBlock = new int[] { mineX, mineY, mineZ };
            fallingMinedType = b;
            fallingSettleTicks = 0;
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

    private boolean waitingForFallingBlock(Minecraft mc) {
        if (fallingMinedBlock == null) return false;
        int x = fallingMinedBlock[0], y = fallingMinedBlock[1], z = fallingMinedBlock[2];
        boolean falling = false;
        for (Object entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof net.minecraft.entity.item.EntityFallingBlock)) continue;
            net.minecraft.entity.Entity block = (net.minecraft.entity.Entity) entity;
            if ((int) Math.floor(block.posX) == x && (int) Math.floor(block.posZ) == z && block.posY >= y - 2) {
                falling = true;
                break;
            }
        }
        if (!falling && fallingSettleTicks == 0 && mc.theWorld.getBlock(x, y, z) == fallingMinedType) return false;
        releaseMiningWhileSandFalls(mc);
        fallingSettleTicks = falling ? 1 : fallingSettleTicks + 1;
        if (fallingSettleTicks < 8) {
            status = "Waiting for sand";
            return true;
        }
        fallingMinedBlock = null;
        fallingMinedType = null;
        fallingSettleTicks = 0;
        return false;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long supportKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    private static boolean safe(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.leaves
            || world.getBlock(x, y + 1, z)
                .getMaterial() == net.minecraft.block.material.Material.leaves)
            return false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block nearby = world.getBlock(x + dx, y + dy, z + dz);
                net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry
                    .lookupFluidForBlock(nearby);
                if (fluid != null && OilAvoidance.isOil(fluid.getName())) return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block block = world.getBlock(x + dx, y + dy, z + dz);
                if (ContactHazards.dangerous(block, world.getBlockMetadata(x + dx, y + dy, z + dz))) return false;
                if (isQuicksand(world, x + dx, y + dy, z + dz)) return false;
                if ((block.getMaterial()
                    .isLiquid() && block.getMaterial() != net.minecraft.block.material.Material.water)
                    || block == Blocks.fire
                    || block == Blocks.cactus
                    || block == Blocks.web
                    || block == Blocks.portal) return false;
            }
        }
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y + 1, z)
            && passable(world, x, y + 2, z)) return true;
        if (world.getBlock(x, y - 1, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y, z)
            && passable(world, x, y + 1, z)) return true;
        double surfaceY = walkSurfaceY(world, x, y, z);
        if (surfaceY < y) return false;
        return world
            .func_147461_a(
                net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(x + 0.19D, surfaceY + 0.01D, z + 0.19D, x + 0.81D, surfaceY + 1.81D, z + 0.81D))
            .isEmpty();
    }

    private static boolean isQuicksand(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) return false;
        net.minecraft.block.Block block = world.getBlock(x, y, z);
        if ("biomesoplenty.common.blocks.BlockMud".equals(
            block.getClass()
                .getName()))
            return world.getBlockMetadata(x, y, z) == 1;
        Boolean quicksand = QUICKSAND_BLOCKS.get(block);
        if (quicksand == null) {
            String id = String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(block))
                .toLowerCase(java.util.Locale.ROOT);
            quicksand = id.contains("quicksand");
            QUICKSAND_BLOCKS.put(block, quicksand);
        }
        return quicksand;
    }

    private static boolean solidCheckpoint(World world, int x, int y, int z) {
        return world.blockExists(x, y - 1, z) && world.blockExists(x, y, z)
            && !world.getBlock(x, y, z)
                .getMaterial()
                .isLiquid()
            && !world.getBlock(x, y - 1, z)
                .getMaterial()
                .isLiquid()
            && walkSurfaceY(world, x, y, z) >= y
            && safe(world, x, y, z);
    }

    private static double walkSurfaceY(World world, int x, int y, int z) {
        net.minecraft.block.Block at = world.getBlock(x, y, z);
        net.minecraft.util.AxisAlignedBB box = at.getCollisionBoundingBoxFromPool(world, x, y, z);
        if (at instanceof net.minecraft.block.BlockStairs) return y + 0.5D;
        if (box != null && box.maxY > y
            && box.maxY < y + 1D
            && box.minX < x + 0.5D
            && box.maxX > x + 0.5D
            && box.minZ < z + 0.5D
            && box.maxZ > z + 0.5D) return box.maxY;
        net.minecraft.block.Block below = world.getBlock(x, y - 1, z);
        if (below.isNormalCube()) return y;
        net.minecraft.util.AxisAlignedBB belowBox = below.getCollisionBoundingBoxFromPool(world, x, y - 1, z);
        if (belowBox != null && belowBox.maxY >= y
            && belowBox.minX < x + 0.5D
            && belowBox.maxX > x + 0.5D
            && belowBox.minZ < z + 0.5D
            && belowBox.maxZ > z + 0.5D) return y;
        return -1D;
    }

    public String hud(Minecraft mc) {
        if (target == null) return "Next Structure";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
