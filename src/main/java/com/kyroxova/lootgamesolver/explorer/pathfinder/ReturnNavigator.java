package com.kyroxova.lootgamesolver.explorer.pathfinder;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.excavation.ExcavationPathfinder;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.safe;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.solidCheckpoint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public final class ReturnNavigator {

    public GroundPathfinder.Search returnJoinSearch;
    public int returnJoinX, returnJoinY, returnJoinZ;
    public boolean rejoiningSavedTrail;
    public List<int[]> returnTrailRoute;
    public boolean returnDigging;
    public TileEntity returnOriginalTarget;
    public List<int[]> returnAfterDig;
    public boolean returnDigResumeSearch;
    public List<int[]> returnDigSource;
    public int returnDigAnchorIndex;

    public void reset() {
        returnJoinSearch = null;
        returnJoinX = 0;
        returnJoinY = 0;
        returnJoinZ = 0;
        rejoiningSavedTrail = false;
        returnTrailRoute = null;
        returnDigging = false;
        returnOriginalTarget = null;
        returnAfterDig = null;
        returnDigResumeSearch = false;
        returnDigSource = null;
        returnDigAnchorIndex = 0;
    }

    public void clearReturnTransits(Explorer explorer) {
        returnJoinSearch = null;
        returnTrailRoute = null;
        explorer.roomEntry.reset();
        explorer.roomExit.reset();
        explorer.chestPitNav.reset();
    }

    public boolean savedRouteWalkable(Minecraft mc, Explorer explorer, List<int[]> points) {
        if (mc == null || mc.theWorld == null || points == null || points.isEmpty()) return false;
        int[] endpoint = points.get(points.size() - 1);
        if (!solidCheckpoint(mc.theWorld, endpoint[0], endpoint[1], endpoint[2])) return false;
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (!safe(mc.theWorld, point[0], point[1], point[2])
                || explorer.blockedWalkNodes.contains(supportKey(point[0], point[1], point[2]))) return false;
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

    public boolean rerouteSavedReturn(Minecraft mc, Explorer explorer, int firstCheckpoint) {
        if (!explorer.returning || returnJoinSearch != null || explorer.route.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int checkpointIndex = ReturnRoutePolicy.nextSafeCheckpoint(
            explorer.route,
            firstCheckpoint,
            point -> solidCheckpoint(mc.theWorld, point[0], point[1], point[2])
                && !explorer.blockedWalkNodes.contains(supportKey(point[0], point[1], point[2])));
        if (checkpointIndex < 0) return false;
        int[] join = explorer.route.get(checkpointIndex);
        returnTrailRoute = new ArrayList<int[]>();
        for (int p = checkpointIndex; p < explorer.route.size(); p++) returnTrailRoute.add(
            explorer.route.get(p)
                .clone());
        returnJoinSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
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
        explorer.route.clear();
        explorer.segmentX = mc.thePlayer.posX;
        explorer.segmentZ = mc.thePlayer.posZ;
        explorer.status = "Finding a clear way back to the saved tunnel";
        return true;
    }

    public boolean digTowardSavedExit(Minecraft mc, Explorer explorer) {
        if (!explorer.returning || returnDigging || mc.thePlayer == null) return false;
        if (explorer.insideDungeonRoom(mc)) return explorer.startRoomExitSearch(mc);
        List<int[]> savedRoute = new ArrayList<int[]>(explorer.route.size());
        for (int[] point : explorer.route) savedRoute.add(point.clone());
        if (savedRoute.size() < 2 && explorer.trail.points.size() >= 2) {
            int nearest = explorer.trail.points.nearestIndex(
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                128);
            savedRoute = explorer.trail.points.reverseFrom(nearest);
        }
        if (savedRoute.size() >= 2 && startReturnDig(mc, explorer, savedRoute)) return true;
        return explorer.insideDungeonRoom(mc) && explorer.roomExit.candidates == null
            && explorer.startRoomExitSearch(mc);
    }

    public boolean startReturnDig(Minecraft mc, Explorer explorer, List<int[]> savedRoute) {
        if (mc.thePlayer == null || mc.theWorld == null || savedRoute == null || savedRoute.size() < 2) return false;
        returnDigSource = new ArrayList<int[]>(savedRoute.size());
        for (int[] point : savedRoute) returnDigSource.add(point.clone());
        returnDigAnchorIndex = Math.min(3, returnDigSource.size() - 1);
        return planReturnDigToAnchor(mc, explorer, returnDigAnchorIndex);
    }

    public boolean planReturnDigToAnchor(Minecraft mc, Explorer explorer, int anchorIndex) {
        if (mc.thePlayer == null || mc.theWorld == null
            || returnDigSource == null
            || anchorIndex < 1
            || anchorIndex >= returnDigSource.size()) return false;
        int[] anchor = returnDigSource.get(anchorIndex);
        TileEntity digGoal = new TileEntity();
        digGoal.xCoord = anchor[0];
        digGoal.yCoord = anchor[1];
        digGoal.zCoord = anchor[2];
        if (!returnDigging) returnOriginalTarget = explorer.target;
        if (explorer.savedSlot < 0) explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        explorer.target = digGoal;
        returnAfterDig = new ArrayList<int[]>();
        for (int i = anchorIndex; i < returnDigSource.size(); i++) returnAfterDig.add(
            returnDigSource.get(i)
                .clone());
        returnDigAnchorIndex = anchorIndex;
        returnDigResumeSearch = explorer.resumeSearchAfterReturn;
        returnDigging = true;
        returnJoinSearch = null;
        returnTrailRoute = null;
        rejoiningSavedTrail = false;
        explorer.route.clear();
        explorer.excavationPlanner.excavationRoute.clear();
        explorer.excavationPlanner.excavationReplans = 0;
        explorer.excavationPlanner.approachPlan = null;
        explorer.excavationPlanner.approachAnchor = null;
        explorer.excavationPlanner.stairX = 0;
        explorer.excavationPlanner.stairZ = 0;
        explorer.excavationPlanner.settlingTicks = 0;
        explorer.excavationPlanner.excavationSearch = new ExcavationPathfinder(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            anchor[0],
            anchor[1],
            anchor[2]);
        explorer.digging = true;
        explorer.returning = false;
        explorer.searching = false;
        explorer.excavationPlanner.entranceSelected = true;
        explorer.walking = true;
        explorer.status = "Planning a checked exit link to saved checkpoint " + (anchorIndex + 1);
        return true;
    }

    public boolean tryNextReturnDigAnchor(Minecraft mc, Explorer explorer) {
        if (returnDigSource == null) return false;
        int next = Math.min(returnDigSource.size() - 1, returnDigAnchorIndex + 3);
        return next > returnDigAnchorIndex && planReturnDigToAnchor(mc, explorer, next);
    }

    public void resumeSavedReturnAfterDig(Explorer explorer) {
        explorer.target = returnOriginalTarget;
        returnOriginalTarget = null;
        explorer.route.clear();
        if (returnAfterDig != null) explorer.route.addAll(returnAfterDig);
        returnAfterDig = null;
        returnDigSource = null;
        returnDigging = false;
        explorer.digging = false;
        explorer.returning = true;
        explorer.resumeSearchAfterReturn = returnDigResumeSearch;
        explorer.status = "Exit opened; continuing along saved access route";
    }
}
