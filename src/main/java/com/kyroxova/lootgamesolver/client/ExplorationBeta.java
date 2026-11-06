package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

/** Loaded-terrain exploration. Routes deliberately require level, open ground. */
public final class ExplorationBeta {

    // Local testing only. Set false before pushing or publishing.
    public static final boolean ENABLED = true;
    private final List<int[]> route = new ArrayList<int[]>();
    private World world;
    private SavedStructureFinder savedFinder;
    private TileEntity target;
    private int ticks;
    private int stalled;
    private double lastDistance = Double.MAX_VALUE;
    private boolean walking;
    private boolean digging;
    private int savedSlot = -1;
    private int digTicks;
    private int toolWaitTicks;
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
    private int settlingTicks;
    private ExcavationPathfinder excavationSearch;
    private final List<int[]> excavationRoute = new ArrayList<int[]>();
    private final List<int[]> rejectedSteps = new ArrayList<int[]>();
    private final List<int[]> navigationPreview = new ArrayList<int[]>();
    private int[] previewHead;
    private int previewSize = -1;
    private int excavationReplans;
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
    private int returnReplans;
    private int cameraPitchDirection;
    private boolean returnDigging;
    private TileEntity returnOriginalTarget;
    private List<int[]> returnAfterDig;
    private boolean returnDigResumeSearch;
    private String excavationMode = "Access route";
    private int[] pendingReturnPlacement;
    private int returnPlacementTicks;
    private int returnPlacementSlot = -1;

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
        if (returning || searching || knownStructure == null) return false;
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
        double[][] offsets = { { 0.5, 0.5, 0.5 }, { 0.15, 0.85, 0.15 }, { 0.85, 0.85, 0.15 }, { 0.15, 0.85, 0.85 },
            { 0.85, 0.85, 0.85 }, { 0.15, 0.15, 0.15 }, { 0.85, 0.15, 0.85 } };
        for (double[] offset : offsets) {
            net.minecraft.util.Vec3 point = net.minecraft.util.Vec3
                .createVectorHelper(x + offset[0], y + offset[1], z + offset[2]);
            net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
            if (hit != null && hit.blockX == x && hit.blockY == y && hit.blockZ == z) return point;
        }
        return null;
    }

    private boolean pausedForControl;
    private GroundPathfinder.Search walkingSearch;
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
    private boolean movingToPuzzleCenter;
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
        if (digging || seekingDryEntrance || route.isEmpty() || !undergroundNearby(mc)) return;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround && ExcavationPolicy.preferredEntrance(x, z, target.xCoord, target.zCoord)
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
        if (digging || seekingDryEntrance || route.size() < 2 || ticks % 10 != 0) return;
        int[] previous = { (int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ) };
        for (int i = 0; i < Math.min(6, route.size()); i++) {
            int[] step = route.get(i);
            boolean diagonal = step[0] != previous[0] && step[2] != previous[2] && step[1] == previous[1];
            if (!safe(mc.theWorld, step[0], step[1], step[2])
                || (diagonal && (!safe(mc.theWorld, step[0], step[1], previous[2])
                    || !safe(mc.theWorld, previous[0], step[1], step[2])))) {
                if (returning) {
                    stop(mc, "Saved return path is obstructed");
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
            || searching
            || route.isEmpty()
            || route.size() > 8
            || !LootGameSolverConfig.staircaseEnabled) return;
        int[] end = route.get(route.size() - 1);
        if (target.yCoord >= end[1] - 2 || Math.abs(end[0] - target.xCoord) + Math.abs(end[2] - target.zCoord) > 32)
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

    boolean moveToPuzzleCenter(Minecraft mc, int[] center) {
        if (mc.thePlayer == null || center == null || center.length < 3 || movingToPuzzleCenter) return false;
        puzzleMasterTarget = target;
        TileEntity point = new TileEntity();
        point.xCoord = center[0];
        point.yCoord = center[1];
        point.zCoord = center[2];
        target = point;
        movingToPuzzleCenter = true;
        puzzleCenterArrivalPending = false;
        cameraPitchDirection = 0;
        route.clear();
        digging = false;
        searching = false;
        walkingSearch = null;
        continuationSearch = null;
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        plan(mc);
        walking = walkingSearch != null || !route.isEmpty();
        if (!walking) {
            double distance = mc.thePlayer.getDistance(center[0] + 0.5D, center[1] + 1.0D, center[2] + 0.5D);
            if (distance > 2.8D) {
                target = puzzleMasterTarget;
                puzzleMasterTarget = null;
                movingToPuzzleCenter = false;
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
        if (startSavedReturn(mc, true, 32)) return;
        continueProgressiveSearch(mc, solved);
    }

    private void continueProgressiveSearch(Minecraft mc, boolean solved) {
        target = null;
        knownStructure = null;
        accessTrail.clear();
        trailTarget = null;
        returning = false;
        resumeSearchAfterReturn = false;
        returnJoinSearch = null;
        returnTrailRoute = null;
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
        if (!back.isEmpty()) {
            route.clear();
            route.addAll(back);
        } else {
            int nearest = accessTrail.nearestIndex(x, y, z, maxJoinDistance);
            int[] join = accessTrail.pointAt(nearest);
            List<int[]> saved = accessTrail.reverseFrom(nearest);
            if (join == null || saved.size() < 2) return false;
            returnTrailRoute = saved;
            returnJoinSearch = new GroundPathfinder.Search(
                walkingTerrain(mc.theWorld),
                x,
                y,
                z,
                join[0],
                join[2],
                Math.max(maxJoinDistance, LootGameSolverConfig.explorationRange));
            route.clear();
        }
        segmentX = mc.thePlayer.posX;
        segmentZ = mc.thePlayer.posZ;
        cameraPitchDirection = 0;
        returning = true;
        returnAvailable = false;
        returnReplans = 0;
        resumeSearchAfterReturn = resumeSearch;
        walking = true;
        searching = false;
        digging = false;
        walkingSearch = null;
        continuationSearch = null;
        status = returnJoinSearch == null ? "Returning along saved access route" : "Rejoining saved access route";
        return true;
    }

    private boolean rerouteSavedReturn(Minecraft mc) {
        if (!returning || returnJoinSearch != null || route.size() < 2 || returnReplans >= 6) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int first = Math.min(1 + returnReplans++, route.size() - 1);
        for (int i = first; i < Math.min(route.size(), first + 4); i++) {
            int[] join = route.get(i);
            if (!safe(mc.theWorld, join[0], join[1], join[2])) continue;
            returnTrailRoute = new ArrayList<int[]>();
            for (int p = i; p < route.size(); p++) returnTrailRoute.add(
                route.get(p)
                    .clone());
            returnJoinSearch = new GroundPathfinder.Search(
                walkingTerrain(mc.theWorld),
                x,
                y,
                z,
                join[0],
                join[2],
                Math.max(16, LootGameSolverConfig.explorationRange));
            route.clear();
            segmentX = mc.thePlayer.posX;
            segmentZ = mc.thePlayer.posZ;
            status = "Finding a clear way back to the saved tunnel";
            return true;
        }
        return false;
    }

    private boolean startReturnDig(Minecraft mc, List<int[]> savedRoute) {
        if (mc.thePlayer == null || mc.theWorld == null || savedRoute == null || savedRoute.size() < 2) return false;
        int anchorIndex = Math.min(6, savedRoute.size() - 1);
        int[] anchor = savedRoute.get(anchorIndex);
        TileEntity digGoal = new TileEntity();
        digGoal.xCoord = anchor[0];
        digGoal.yCoord = anchor[1];
        digGoal.zCoord = anchor[2];
        returnOriginalTarget = target;
        target = digGoal;
        returnAfterDig = new ArrayList<int[]>();
        for (int i = anchorIndex; i < savedRoute.size(); i++) returnAfterDig.add(
            savedRoute.get(i)
                .clone());
        returnDigResumeSearch = resumeSearchAfterReturn;
        returnDigging = true;
        returnJoinSearch = null;
        returnTrailRoute = null;
        route.clear();
        excavationRoute.clear();
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
        status = "Planning a checked exit link to the saved tunnel";
        return true;
    }

    private void resumeSavedReturnAfterDig() {
        target = returnOriginalTarget;
        returnOriginalTarget = null;
        route.clear();
        if (returnAfterDig != null) route.addAll(returnAfterDig);
        returnAfterDig = null;
        returnDigging = false;
        digging = false;
        returning = true;
        resumeSearchAfterReturn = returnDigResumeSearch;
        status = "Exit opened; continuing along saved access route";
    }

    private String worldIdentity(Minecraft mc) {
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) return "save:" + mc.getIntegratedServer()
            .getFolderName();
        net.minecraft.client.multiplayer.ServerData server = mc.func_147104_D();
        return "server:" + (server == null ? "unknown" : server.serverIP);
    }

    String navigationStatus() {
        return status;
    }

    void setAutomationStatus(String message) {
        status = message;
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
        // Vanilla mouse look keeps the previous rotation in the same angular frame.
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
            if (direction != 0 && nextDirection != direction) return 0;
            direction = nextDirection;
            transitions++;
        }
        return transitions >= 2 ? direction : 0;
    }

    private static boolean hasVerticalTransition(List<int[]> steps, int startY, int lookAhead) {
        int previousY = startY;
        for (int i = 0; i < Math.min(lookAhead, steps.size()); i++) {
            int y = steps.get(i)[1];
            if (y != previousY) return true;
            previousY = y;
        }
        return false;
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

    public void toggle(Minecraft mc) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
        if (walking) {
            stop(mc, "Walk stopped");
            return;
        }
        if (returnAvailable) {
            if (startSavedReturn(mc, true, 64)) return;
        }
        // A stopped or interrupted solve may leave the player inside the room while the exact
        // return toggle was never armed. Prefer the approach trail before scanning a new target.
        if (trailTarget != null && startSavedReturn(mc, true, 64)) {
            return;
        }
        returnAvailable = false;
        puzzleArrivalPending = false;
        planningExistingAccess = false;
        existingAccessPlan = null;
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
        // Inside the structure, the safest shortest path is the exact checked trail we used
        // to enter. Do not ask a fresh path search to choose a nearby wall or corner.
        if (accessTrail.nearTerminal(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            24) && startSavedReturn(mc, true, 64)) return;
        boolean underground = LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2
            && Math.abs(mc.thePlayer.posX - target.xCoord) + Math.abs(mc.thePlayer.posZ - target.zCoord) <= 40;
        if (underground) {
            existingAccessPlan = new ExcavationPathfinder(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                target.xCoord,
                target.yCoord + 1,
                target.zCoord);
            planningExistingAccess = true;
            walking = true;
            status = "Checking for a safe existing entrance";
        } else {
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
                if (returnPlacementSlot >= 0) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
                mc.playerController.updateController();
                pendingReturnPlacement = null;
                returnPlacementSlot = -1;
            } else if (++returnPlacementTicks > 30) {
                if (returnPlacementSlot >= 0) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
                pendingReturnPlacement = null;
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
        if (!walking && target != null) {
            beginAccessTrail(mc);
            if (!returnAvailable && accessTrail.nearTerminal(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                24)) returnAvailable = true;
        }
        if (walking && searching && ticks % 40 == 0) {
            TileEntity previous = target;
            scan(mc);
            if (target != null) {
                searching = false;
                route.clear();
            } else target = previous;
        }
        if (!walking) return;
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled
            || solverActive
            || (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 100)) {
            stop(
                mc,
                solverActive ? "Solver took control"
                    : !LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled ? "Exploration disabled"
                        : "Stopped: low air underwater");
            return;
        }
        boolean manual = GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindRight)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak);
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
        if (!returning && (target == null || target.isInvalid())) {
            stop(mc, "Target no longer loaded");
            return;
        }
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
            returnJoinSearch = null;
            if (join.isEmpty() && returnTrailRoute != null) {
                int[] first = returnTrailRoute.get(0);
                int px = (int) Math.floor(mc.thePlayer.posX), py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int pz = (int) Math.floor(mc.thePlayer.posZ);
                if ((Math.abs(first[0] - px) + Math.abs(first[2] - pz) > 1 || Math.abs(first[1] - py) > 1)
                    && startReturnDig(mc, returnTrailRoute)) return;
                route.addAll(returnTrailRoute);
            } else {
                route.addAll(join);
                if (returnTrailRoute != null) route.addAll(returnTrailRoute);
            }
            returnTrailRoute = null;
            if (route.isEmpty()) {
                returning = false;
                continueProgressiveSearch(mc, true);
            } else status = "Following saved access route out";
            return;
        }
        if (!movingToPuzzleCenter && !returnDigging && reachableBoard(mc)) {
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
        if (pausedForControl) {
            pausedForControl = false;
            excavationRoute.clear();
            excavationSearch = null;
            approachPlan = null;
            approachAnchor = null;
            digging = false;
            seekingDryEntrance = false;
            entranceSelected = false;
            if (!returning) plan(mc);
        }
        inspectUpcomingRoute(mc);
        if (!walking) return;
        chooseApproachDuringWalking(mc);
        if (walkingSearch != null) {
            if (!walkingSearch.advance(3_000_000L)) {
                status = "Continuing route search";
                return;
            }
            route.addAll(walkingSearch.path());
            walkingSearch = null;
            walkingPlanFailed = route.isEmpty();
        }
        if (!returning && !digging
            && !seekingDryEntrance
            && !movingToPuzzleCenter
            && route.size() >= 1
            && route.size() <= 8
            && ticks % 5 == 0) extendRoute(mc);
        prepareApproach(mc);
        if (route.isEmpty()) {
            if (movingToPuzzleCenter) {
                target = puzzleMasterTarget;
                puzzleMasterTarget = null;
                movingToPuzzleCenter = false;
                stop(mc, "At LootGames board center");
                puzzleCenterArrivalPending = true;
                return;
            }
            if (returnDigging) {
                int horizontal = Math.abs((int) Math.floor(mc.thePlayer.posX) - target.xCoord)
                    + Math.abs((int) Math.floor(mc.thePlayer.posZ) - target.zCoord);
                int vertical = Math.abs((int) Math.floor(mc.thePlayer.boundingBox.minY) - target.yCoord);
                if (horizontal <= 3 && vertical <= 1) {
                    resumeSavedReturnAfterDig();
                    return;
                }
                if (!stairStep(mc)) stop(mc, "No safe breakable link to the saved tunnel");
                return;
            }
            if (returning) {
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
                    if (LootGameSolverConfig.staircaseEnabled && horizontal <= 40 && !mc.thePlayer.isInWater()) {
                        if (!selectDigEntrance(mc)) stop(mc, "No safe surface dig entrance in range");
                    } else stop(mc, "No verified route around the obstruction");
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled
                    && Math.abs(target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1) {
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
                        return;
                    }
                    plan(mc);
                    if (!route.isEmpty() || walkingSearch != null) return;
                    if (LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2
                        && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
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
                    if (++approachAttempts <= 3) {
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
                    stop(mc, "No supported entrance found within the search limit");
                }
                return;
            }
            if (digging) {
                digging = false;
                plan(mc);
                if (!route.isEmpty() || walkingSearch != null) return;
            }
            stop(mc, "Arrived beside LootGame");
            return;
        }
        int[] next = route.get(0);
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        boolean diagonal = next[0] != px && next[2] != pz && next[1] == py;
        if (diagonal && (!safe(mc.theWorld, next[0], py, pz) || !safe(mc.theWorld, px, py, next[2]))) {
            if (returning) {
                if (rerouteSavedReturn(mc)) return;
                stop(mc, "Saved return path clearance changed");
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
                if (rerouteSavedReturn(mc)) return;
                stop(mc, "Saved return path is no longer safe");
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
            if (!returning && trailTarget != null) recordAccessPoint(next[0], next[1], next[2]);
            route.remove(0);
            lastDistance = Double.MAX_VALUE;
            stalled = 0;
            if (route.isEmpty()) {
                if (mc.thePlayer.onGround) {
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
                if (returning && rerouteSavedReturn(mc)) return;
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
            : NavigationSteering.heading(segmentX, segmentZ, next[0] + 0.5D, next[2] + 0.5D, mc.thePlayer.rotationYaw);
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(desired - mc.thePlayer.rotationYaw);
        int plannedPitchDirection = staircaseDirection(route, py);
        if (plannedPitchDirection != 0) cameraPitchDirection = plannedPitchDirection;
        else if (!hasVerticalTransition(route, py, 5)) cameraPitchDirection = 0;
        boolean swimming = mc.thePlayer.isInWater() || mc.theWorld.getBlock(next[0], next[1], next[2])
            .getMaterial() == net.minecraft.block.material.Material.water;
        float desiredPitch = swimming ? 0F : cameraPitchDirection > 0 ? -30F : cameraPitchDirection < 0 ? 30F : 0F;
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
            stop(mc, "Walk stopped: no progress");
            return;
        }
        boolean straight = !digging && !mc.thePlayer.isInWater()
            && next[1] == py
            && (route.size() == 1 && distance > 0.35D || route.size() >= 3 && !mc.thePlayer.isInWater()
                && route.get(1)[1] == py
                && route.get(2)[1] == py
                && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
                && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
                && safe(mc.theWorld, route.get(1)[0], py, route.get(1)[2])
                && safe(mc.theWorld, route.get(2)[0], py, route.get(2)[2]));
        mc.thePlayer.setSprinting(
            straight && Math.abs(turn) < 10F
                && mc.thePlayer.getFoodStats()
                    .getFoodLevel() > 6);
        if (navigationInput != null) {
            navigationInput.forward = Math.abs(turn) > 35F ? 0F
                : route.size() == 1 ? (float) Math.min(1D, distance * 1.5D) : 1F;
            navigationInput.hop = (next[1] > py && Math.abs(turn) < 35F && distance < 1.4D) || mc.thePlayer.isInWater();
            if (distance > 0.02D) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                double speed = Math.min(centering ? 0.4D : straight ? 1D : 0.95D, distance * 1.5D);
                navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
                navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
            }
        }
    }

    private boolean tryPlaceReturnStep(Minecraft mc, int[] next) {
        int x = next[0], y = next[1] - 1, z = next[2];
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
        if (mc.theWorld.getBlock(x, y, z) != Blocks.air && !targetBlock.isReplaceable(mc.theWorld, x, y, z))
            return false;
        int[][] adjacent = { { x - 1, y, z, 5 }, { x + 1, y, z, 4 }, { x, y, z - 1, 3 }, { x, y, z + 1, 2 } };
        for (int[] side : adjacent) {
            net.minecraft.block.Block support = mc.theWorld.getBlock(side[0], side[1], side[2]);
            if (support == Blocks.air || !support.getMaterial()
                .isSolid() || mc.theWorld.getTileEntity(side[0], side[1], side[2]) != null) continue;
            int previousSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.updateController();
            net.minecraft.util.Vec3 hit = net.minecraft.util.Vec3
                .createVectorHelper(side[0] + 0.5D, side[1] + 0.5D, side[2] + 0.5D);
            if (mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                side[0],
                side[1],
                side[2],
                side[3],
                hit)) {
                pendingReturnPlacement = new int[] { x, y, z };
                returnPlacementTicks = 0;
                if (returnPlacementSlot < 0) returnPlacementSlot = previousSlot;
                status = "Placing a checked block for the return step";
                return true;
            }
        }
        return false;
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
                    .isSolid())
                continue;
            if (block == Blocks.cobblestone) return i;
            if (block == Blocks.dirt) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    private void stop(Minecraft mc, String message) {
        persistAccessTrail(true);
        if (movingToPuzzleCenter && puzzleMasterTarget != null) target = puzzleMasterTarget;
        puzzleMasterTarget = null;
        movingToPuzzleCenter = false;
        if (returnDigging) {
            target = returnOriginalTarget;
            returnOriginalTarget = null;
            returnAfterDig = null;
            returnDigging = false;
            returnAvailable = true;
        }
        if (returning) returnAvailable = true;
        returning = false;
        returnJoinSearch = null;
        returnTrailRoute = null;
        releaseMiningInput(mc);
        toolTransfer.cancel(mc);
        if (returnPlacementSlot >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = returnPlacementSlot;
        pendingReturnPlacement = null;
        returnPlacementSlot = -1;
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
        searching = false;
        route.clear();
        status = message;
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[LootGames Beta] " + message));
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
        // Bound pathfinding work per decision; stop rather than spin on unreachable goals.
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] goal = search.next();
            if (goal == null) return false;
            int x = (int) Math.floor(mc.thePlayer.posX), z = (int) Math.floor(mc.thePlayer.posZ);
            int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            double dx = goal[0] - x, dz = goal[1] - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 4) continue;
            searchGoalX = goal[0];
            searchGoalZ = goal[1];
            double step = Math.min(16D, length);
            target = new TileEntity();
            target.xCoord = x + (int) Math.round(dx / length * step);
            target.yCoord = y;
            target.zCoord = z + (int) Math.round(dz / length * step);
            if (!mc.theWorld.blockExists(target.xCoord, y, target.zCoord)) continue;
            plan(mc);
            if (route.isEmpty() && walkingSearch == null) continue;
            searching = true;
            digging = false;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            status = "Exploring outward";
            return true;
        }
        return false;
    }

    private GroundPathfinder.Terrain walkingTerrain(final World terrainWorld) {
        return new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return safe(terrainWorld, x, y, z);
            }

            public boolean clear(int x, int y, int z) {
                return passable(terrainWorld, x, y, z);
            }

            public int extraCost(int x, int y, int z) {
                return terrainWorld.getBlock(x, y, z)
                    .getMaterial() == net.minecraft.block.material.Material.water ? 12 : 0;
            }
        };
    }

    private int existingWalkCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        if (Math.abs(nx - x) + Math.abs(nz - z) > 1 || Math.abs(ny - y) > 1) return -1;
        if (!safe(w, nx, ny, nz) || w.getBlock(nx, ny, nz)
            .getMaterial()
            .isLiquid()) return -1;
        if (ny > y && !passable(w, x, y + 2, z)) return -1;
        if (ny < y && !passable(w, nx, y + 1, nz)) return -1;
        return 10 + 10 * Math.abs(ny - y);
    }

    private void extendRoute(Minecraft mc) {
        int[] tail = route.get(route.size() - 1);
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
        if (!continuationSearch.advance(3_000_000L)) return;
        List<int[]> continuation = continuationSearch.path();
        continuationSearch = null;
        if (continuation.isEmpty()) return;
        route.addAll(continuation);
        if (searching) {
            searchGoalX = continuationX;
            searchGoalZ = continuationZ;
            int[] end = route.get(route.size() - 1);
            target.xCoord = end[0];
            target.yCoord = end[1];
            target.zCoord = end[2];
        }
    }

    private void plan(final Minecraft mc) {
        walkingPlanFailed = false;
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
            walkingSearch = null;
        }
    }

    private static boolean passable(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && !world.getBlock(x, y, z)
            .getMaterial()
            .isLiquid()
            && world.getBlock(x, y, z)
                .getCollisionBoundingBoxFromPool(world, x, y, z) == null;
    }

    private List<int[]> patternedAccess(Minecraft mc, int x, int y, int z) {
        int goalY = target.yCoord + 1;
        int horizontal = Math.abs(target.xCoord - x) + Math.abs(target.zCoord - z);
        ExcavationPathfinder.Terrain terrain = (a, b, c, nx, ny,
            nz) -> excavationCost(mc.theWorld, a, b, c, nx, ny, nz);
        if (goalY == y || horizontal >= Math.abs(goalY - y) + 2) {
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
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround) return ++settlingTicks <= 40;
        settlingTicks = 0;
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
                // The surface walk is only an approach. Save a compact trail from the
                // actual excavation mouth so return never walks back to the old start.
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
        if (excavationRoute.isEmpty() && excavationSearch == null) {
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
                    target.xCoord,
                    target.yCoord + 1,
                    target.zCoord,
                    true);
                rejectedSteps.clear();
            }
            boolean complete = excavationSearch.advance(
                (a, b, c, nx, ny, nz) -> excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
                24,
                System.nanoTime() + 3_000_000L);
            rejectedSteps.clear();
            rejectedSteps.addAll(excavationSearch.rejected);
            status = "Planning access: " + excavationSearch.expandedNodes() + " checked nodes";
            if (!complete) return true;
            excavationRoute.addAll(excavationSearch.path);
            excavationSearch = null;
            if (excavationRoute.isEmpty()) return false;
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
            excavationRoute.clear();
            miningAim.reset();
            return ++excavationReplans <= 3;
        }
        return attemptStairStep(mc, next[1]);
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
        return hardness >= 0 && hardness <= 5
            && (material == net.minecraft.block.material.Material.rock
                || material == net.minecraft.block.material.Material.ground
                || material == net.minecraft.block.material.Material.grass
                || material == net.minecraft.block.material.Material.wood
                || material == net.minecraft.block.material.Material.leaves
                || material == net.minecraft.block.material.Material.plants
                || material == net.minecraft.block.material.Material.vine);
    }

    private int excavationCost(World w, int x, int y, int z, int nx, int ny, int nz) {
        if (!w.blockExists(nx, ny - 1, nz) || !w.getBlock(nx, ny - 1, nz)
            .isNormalCube()) return -1;
        boolean overhead = ny > y && !passable(w, x, y + 2, z);
        if (overhead && !excavatable(w, x, y + 2, z)) return -1;
        int cost = 1 + Math.abs(ny - y), firstBreak = Integer.MAX_VALUE, lastBreak = Integer.MIN_VALUE;
        if (overhead) {
            cost += 2;
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) for (int by = y + 1; by <= y + 3; by++) {
                net.minecraft.block.Block block = w.getBlock(x + ox, by, z + oz);
                if (!w.blockExists(x + ox, by, z + oz) || block.getMaterial()
                    .isLiquid() || block == Blocks.sand || block == Blocks.gravel) return -1;
            }
        }
        for (int by = ny; by <= Math.max(y, ny) + 1; by++) {
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
                if ((block.getMaterial()
                    .isLiquid() && by >= firstBreak - 1
                    && by <= lastBreak + 1) || block == Blocks.fire
                    || block == Blocks.cactus
                    || block == Blocks.sand
                    || block == Blocks.gravel
                    || block == Blocks.portal) return -1;
            }
        return cost;
    }

    private static boolean dryShore(World world, int x, int y, int z) {
        if (!passable(world, x, y, z) || !world.getBlock(x, y - 1, z)
            .isNormalCube()) return false;
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
        if (mc.thePlayer.onGround && ExcavationPolicy.preferredEntrance(x, z, target.xCoord, target.zCoord)
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
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (entranceCandidates.isEmpty()) {
            entranceCandidate = 0;
            int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
                { -1, -1 } };
            for (int radius = 18; radius <= 24; radius += 3)
                for (int[] direction : directions) for (int dy = -1; dy <= 6; dy++) {
                    double norm = Math.hypot(direction[0], direction[1]);
                    int gx = target.xCoord + (int) Math.round(direction[0] * radius / norm), gy = y + dy;
                    int gz = target.zCoord + (int) Math.round(direction[1] * radius / norm);
                    if (!ExcavationPolicy.preferredEntrance(gx, gz, target.xCoord, target.zCoord)) continue;
                    if (dryShore(w, gx, gy, gz) && safe(w, gx, gy, gz))
                        entranceCandidates.add(new int[] { gx, gy, gz });
                }
            final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
            final int tx = target.xCoord, tz = target.zCoord;
            entranceCandidates
                .sort((a, b) -> Double.compare(entranceScore(a, px, pz, tx, tz), entranceScore(b, px, pz, tx, tz)));
        }
        // Continue across ticks instead of abandoning all later shores after a few failed candidates.
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
                    return safe(w, a, b, c);
                }

                public boolean clear(int a, int b, int c) {
                    return passable(w, a, b, c);
                }
            }, x, y, z, goal[0], goal[2], 48);
            if (candidate.isEmpty()) continue;
            int[] last = candidate.get(candidate.size() - 1);
            if (last[1] != goal[1] || Math.abs(last[0] - goal[0]) + Math.abs(last[2] - goal[2]) > 1
                || !dryShore(w, last[0], last[1], last[2])) continue;
            if (candidate.size() > Math.max(12D, 2D * Math.hypot(goal[0] - x, goal[2] - z))) continue;
            route.clear();
            route.addAll(candidate);
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
        return travel + 2D * Math.abs(approach - 21D);
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
        // Recheck the same support, hazard and clearance rules used by the planner.
        if (excavationCost(w, x, y, z, nx, destinationY, nz) < 0) return false;
        if (destinationY > y && !passable(w, x, y + 2, z)) return minePlannedBlock(mc, x, y + 2, z, y + 2);
        for (int by = Math.max(y, destinationY) + 1; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (passable(w, nx, by, nz)) continue;
            if (!excavatable(w, nx, by, nz)) return false;
            if (w.getTileEntity(nx, by, nz) != null || b.getBlockHardness(w, nx, by, nz) < 0) return false;
        }
        for (int by = Math.max(y, destinationY) + 1; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (passable(w, nx, by, nz)) continue;
            return minePlannedBlock(mc, nx, by, nz, destinationY);
        }
        digTicks = 0;
        miningAim.reset();
        if (!safe(w, nx, destinationY, nz)) return false;
        singleStep(mc, nx, destinationY, nz, false);
        returnSupports.add(supportKey(nx, destinationY - 1, nz));
        if (!excavationRoute.isEmpty()) excavationRoute.remove(0);
        excavationReplans = 0;
        stairSteps++;
        lastDistance = Double.MAX_VALUE;
        stalled = 0;
        return true;
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
            boolean plant = visible instanceof net.minecraft.block.BlockBush
                || visible.getMaterial() == net.minecraft.block.material.Material.plants
                || visible.getMaterial() == net.minecraft.block.material.Material.vine;
            if (aimPoint == null && FoliagePolicy.canClear(
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
                nz)) {
                int rootY = obstruction.blockY;
                for (int scan = 0; scan < 3; scan++) {
                    net.minecraft.block.material.Material material = w
                        .getBlock(obstruction.blockX, rootY, obstruction.blockZ)
                        .getMaterial();
                    if (material != net.minecraft.block.material.Material.plants
                        && material != net.minecraft.block.material.Material.vine) break;
                    rootY--;
                }
                boolean plannedSupport = obstruction.blockX == nx && obstruction.blockZ == nz
                    && rootY >= destinationY
                    && rootY <= Math.max(y, destinationY) + 1
                    && excavatable(w, nx, rootY, nz);
                net.minecraft.util.Vec3 supportAim = plannedSupport ? visibleMiningPoint(mc, nx, rootY, nz) : null;
                if (supportAim != null) {
                    mineY = rootY;
                    aimPoint = supportAim;
                    b = w.getBlock(nx, rootY, nz);
                } else {
                    mineX = obstruction.blockX;
                    mineY = obstruction.blockY;
                    mineZ = obstruction.blockZ;
                    b = visible;
                    aimPoint = visibleMiningPoint(mc, mineX, mineY, mineZ);
                }
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
            stop(
                mc,
                "\u00A7eAuto-dig paused: \u00A7fKeep a usable \u00A7bpickaxe\u00A7f and \u00A7bshovel\u00A7f in your inventory. Check tool durability and harvest level.");
            return true;
        }
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
        if (mc.thePlayer.getDistanceSq(mineX + 0.5D, mineY + 0.5D, mineZ + 0.5D) > 16D) return false;
        if (aimPoint == null)
            aimPoint = net.minecraft.util.Vec3.createVectorHelper(mineX + 0.5D, mineY + 0.5D, mineZ + 0.5D);
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
        status = excavationMode + ": " + digTicks + "/" + allowance;
        return true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long supportKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    private static boolean safe(World world, int x, int y, int z) {
        // Open headroom and a full floor; reject nearby liquids, fire, cactus and unknown terrain.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block block = world.getBlock(x + dx, y + dy, z + dz);
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

    private static double walkSurfaceY(World world, int x, int y, int z) {
        net.minecraft.block.Block below = world.getBlock(x, y - 1, z);
        if (below.isNormalCube()) return y;
        net.minecraft.util.AxisAlignedBB belowBox = below.getCollisionBoundingBoxFromPool(world, x, y - 1, z);
        if (belowBox != null && belowBox.maxY >= y
            && belowBox.minX < x + 0.5D
            && belowBox.maxX > x + 0.5D
            && belowBox.minZ < z + 0.5D
            && belowBox.maxZ > z + 0.5D) return y;
        net.minecraft.block.Block at = world.getBlock(x, y, z);
        net.minecraft.util.AxisAlignedBB box = at.getCollisionBoundingBoxFromPool(world, x, y, z);
        if (at instanceof net.minecraft.block.BlockStairs) return y + 0.5D;
        if (box == null || box.maxY <= y || box.maxY >= y + 1D) return -1D;
        return box.maxY;
    }

    public String hud(Minecraft mc) {
        if (target == null) return "Searching nearby";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
