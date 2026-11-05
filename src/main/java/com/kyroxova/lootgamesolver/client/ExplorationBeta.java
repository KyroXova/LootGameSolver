package com.kyroxova.lootgamesolver.client;

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
    private int[] trailTarget;
    private boolean returning, returnAvailable;
    private String excavationMode = "Access route";

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
        if (mc.thePlayer.onGround && dryShore(mc.theWorld, x, y, z) && safe(mc.theWorld, x, y, z)) {
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

    String navigationStatus() {
        return status;
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
            List<int[]> back = accessTrail.reverse(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
            if (!back.isEmpty()) {
                route.clear();
                route.addAll(back);
                segmentX = mc.thePlayer.posX;
                segmentZ = mc.thePlayer.posZ;
                returning = true;
                returnAvailable = false;
                walking = true;
                digging = false;
                searching = false;
                walkingSearch = null;
                continuationSearch = null;
                status = "Returning along saved access path";
                return;
            }
        }
        returnAvailable = false;
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
        if (LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2
            && Math.abs(mc.thePlayer.posX - target.xCoord) + Math.abs(mc.thePlayer.posZ - target.zCoord) <= 32) {
            walking = true;
            route.clear();
            status = "Selecting dry underground approach";
            return;
        }
        plan(mc);
        digging = walkingSearch == null && route.isEmpty()
            && LootGameSolverConfig.staircaseEnabled
            && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord);
        walking = walkingSearch != null || !route.isEmpty() || digging;
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
            if (navigationInput != null) stop(mc, "World changed");
            if (savedFinder != null) savedFinder.cancel();
            savedFinder = null;
            world = mc.theWorld;
            target = null;
            knownStructure = null;
            accessTrail.clear();
            trailTarget = null;
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
        if (reachableBoard(mc)) {
            if (digging && mc.thePlayer.onGround) accessTrail.record(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ));
            returnAvailable = trailTarget != null && trailTarget[0] == target.xCoord
                && trailTarget[1] == target.yCoord
                && trailTarget[2] == target.zCoord
                && !accessTrail
                    .reverse(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ))
                    .isEmpty();
            stop(mc, "Arrived at reachable LootGame");
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
        if (!returning && !digging && !seekingDryEntrance && route.size() >= 2 && route.size() <= 8 && ticks % 10 == 0)
            extendRoute(mc);
        prepareApproach(mc);
        if (route.isEmpty()) {
            if (returning) {
                returning = false;
                returnAvailable = false;
                stop(mc, "Returned to access entrance");
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
                    if (LootGameSolverConfig.staircaseEnabled && horizontal <= 32 && !mc.thePlayer.isInWater()) {
                        digging = true;
                        entranceSelected = true;
                        savedSlot = mc.thePlayer.inventory.currentItem;
                    } else stop(mc, "No verified route around the obstruction");
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled
                    && Math.abs(target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1) {
                    digging = true;
                    entranceSelected = true;
                    savedSlot = mc.thePlayer.inventory.currentItem;
                }
            }
            if (searching) {
                scan(mc);
                if (target != null) {
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
                        digging = true;
                        savedSlot = mc.thePlayer.inventory.currentItem;
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
            if (digging) accessTrail.record(next[0], next[1], next[2]);
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
        if (!centering) look(desired, 5F);
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
        boolean straight = route.size() >= 3 && !digging
            && !mc.thePlayer.isInWater()
            && next[1] == py
            && route.get(1)[1] == py
            && route.get(2)[1] == py
            && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
            && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
            && safe(mc.theWorld, route.get(1)[0], py, route.get(1)[2])
            && safe(mc.theWorld, route.get(2)[0], py, route.get(2)[2]);
        mc.thePlayer.setSprinting(
            straight && Math.abs(turn) < 10F
                && mc.thePlayer.getFoodStats()
                    .getFoodLevel() > 6);
        if (navigationInput != null) {
            navigationInput.forward = Math.abs(turn) > 35F ? 0F
                : route.size() == 1 ? (float) Math.min(0.65D, distance * 1.5D) : 1F;
            navigationInput.hop = (next[1] > py && Math.abs(turn) < 35F && distance < 1.4D) || mc.thePlayer.isInWater();
            if (distance > 0.02D) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                double speed = Math.min(centering ? 0.4D : straight ? 1D : 0.85D, distance * 1.5D);
                navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
                navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
            }
        }
    }

    private void stop(Minecraft mc, String message) {
        if (returning) returnAvailable = true;
        returning = false;
        releaseMiningInput(mc);
        toolTransfer.cancel(mc);
        miningTarget = Long.MIN_VALUE;
        miningSlot = -1;
        walkingSearch = null;
        continuationSearch = null;
        pausedForControl = false;
        approachPlan = null;
        approachAnchor = null;
        cameraActive = false;
        cameraFrame = 0;
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
        double nearest = Double.MAX_VALUE;
        if (savedFinder != null) {
            for (int[] position : savedFinder.positions()) {
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
        if (trailTarget == null || trailTarget[0] != target.xCoord
            || trailTarget[1] != target.yCoord
            || trailTarget[2] != target.zCoord) {
            accessTrail.clear();
            trailTarget = new int[] { target.xCoord, target.yCoord, target.zCoord };
        }
        accessTrail.record(x, y, z);
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

    private boolean findDryEntrance(final Minecraft mc) {
        final World w = mc.theWorld;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (entranceCandidates.isEmpty()) {
            entranceCandidate = 0;
            int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
                { -1, -1 } };
            for (int radius = 4; radius <= 24; radius += 4)
                for (int[] direction : directions) for (int dy = -1; dy <= 6; dy++) {
                    int gx = x + direction[0] * radius, gy = y + dy, gz = z + direction[1] * radius;
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
            List<int[]> candidate = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

                public boolean stand(int a, int b, int c) {
                    return safe(w, a, b, c);
                }

                public boolean clear(int a, int b, int c) {
                    return passable(w, a, b, c);
                }
            }, x, y, z, goal[0], goal[2], 32);
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
        return travel + 0.5D * approach;
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
        if (!world
            .func_147461_a(
                net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(x + 0.19D, y + 0.01D, z + 0.19D, x + 0.81D, y + 1.81D, z + 0.81D))
            .isEmpty()) return false;
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y + 1, z)
            && passable(world, x, y + 2, z)) return true;
        if (world.getBlock(x, y - 1, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y, z)
            && passable(world, x, y + 1, z)) return true;
        return world.getBlock(x, y - 1, z)
            .isNormalCube() && passable(world, x, y, z)
            && passable(world, x, y + 1, z);
    }

    public String hud(Minecraft mc) {
        if (target == null) return "Searching nearby";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
