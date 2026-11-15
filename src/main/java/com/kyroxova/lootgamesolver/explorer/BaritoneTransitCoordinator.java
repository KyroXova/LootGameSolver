package com.kyroxova.lootgamesolver.explorer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public final class BaritoneTransitCoordinator {

    public boolean centerMove;
    public boolean structureMove;
    public boolean searchMove;
    public boolean searchHandoff;
    public boolean searchNext;
    public boolean returnMove;
    public boolean returnWaiting;
    public boolean resumeSearch;
    public boolean returnSolved;
    public int returnWait;
    public int[] returnPoint;
    public World returnWorld;
    public int searchX, searchZ;
    public int searchTicks;
    public int handoffTicks;
    public final List<int[]> routePreview = new ArrayList<int[]>();
    public int previewTick;

    public void reset() {
        centerMove = false;
        structureMove = false;
        searchMove = false;
        searchHandoff = false;
        searchNext = false;
        returnMove = false;
        returnWaiting = false;
        resumeSearch = false;
        returnSolved = false;
        returnWait = 0;
        returnPoint = null;
        returnWorld = null;
        searchX = 0;
        searchZ = 0;
        searchTicks = 0;
        handoffTicks = 0;
        routePreview.clear();
        previewTick = 0;
    }

    public boolean active() {
        return centerMove || structureMove || searchMove || returnMove || returnWaiting || resumeSearch;
    }

    public void rememberReturnPoint(Minecraft mc) {
        returnPoint = null;
        returnWorld = null;
    }

    public boolean tick(Minecraft mc, Explorer explorer) {
        if (active() && explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING) {
            explorer.baritoneNavigation.renderOverlay(LootGameSolverConfig.showOverlay);
            if (++previewTick % 5 == 0) {
                routePreview.clear();
                if (LootGameSolverConfig.showOverlay) routePreview.addAll(explorer.baritoneNavigation.route());
            }
        } else if (!routePreview.isEmpty()) {
            routePreview.clear();
        }
        if (returnWaiting || returnMove || resumeSearch) {
            if (mc.thePlayer == null || mc.theWorld == null || returnWorld != mc.theWorld) {
                explorer.baritoneNavigation.stop();
                reset();
                explorer.walking = false;
                explorer.returning = false;
            } else if (resumeSearch) {
                if (++returnWait >= 3) {
                    resumeSearch = false;
                    returnPoint = null;
                    returnWorld = null;
                    explorer.continueProgressiveSearch(mc, returnSolved);
                }
                return true;
            } else if (returnWaiting) {
                BaritoneNavigationBridge.Start started = explorer.baritoneNavigation
                    .start(returnPoint[0], returnPoint[1], returnPoint[2]);
                if (started == BaritoneNavigationBridge.Start.STARTED) {
                    returnWaiting = false;
                    returnMove = true;
                } else if (started != BaritoneNavigationBridge.Start.BUSY || ++returnWait > 40) {
                    explorer.stop(mc, "Baritone could not start the return route");
                }
                return true;
            } else {
                double distToExit = returnPoint == null ? 0D
                    : Math.hypot(mc.thePlayer.posX - returnPoint[0] - 0.5D, mc.thePlayer.posZ - returnPoint[2] - 0.5D);
                boolean onSurface = mc.theWorld.canBlockSeeTheSky(
                    (int) Math.floor(mc.thePlayer.posX),
                    (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ));
                boolean arrived = onSurface
                    || (mc.thePlayer.onGround && distToExit <= 2.5D
                        && Math.abs(mc.thePlayer.boundingBox.minY - returnPoint[1]) <= 2)
                    || (explorer.baritoneNavigation.completed() && distToExit <= 4.0D);
                if (arrived) {
                    explorer.baritoneNavigation.stop();
                    returnMove = false;
                    resumeSearch = true;
                    returnWait = 0;
                    explorer.walking = false;
                    explorer.returning = false;
                    explorer.entrySurfacePoint = null;
                } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                    explorer.stop(mc, "Baritone could not complete the return route");
                }
                return true;
            }
        }
        if (searchMove) {
            if (explorer.world != mc.theWorld || mc.thePlayer == null || mc.theWorld == null) {
                explorer.baritoneNavigation.stop();
                searchMove = false;
                explorer.walking = false;
            } else {
                double aimDx = (searchX + 0.5D) - mc.thePlayer.posX;
                double aimDz = (searchZ + 0.5D) - mc.thePlayer.posZ;
                float aimYaw = (float) (Math.atan2(aimDz, aimDx) * 180D / Math.PI) - 90F;
                explorer.look(aimYaw, 0F);
                if (!searchHandoff && ++searchTicks % 20 == 0) {
                    explorer.scan(mc);
                    if (explorer.target != null) {
                        explorer.baritoneNavigation.stop();
                        searchHandoff = true;
                        searchNext = false;
                        handoffTicks = 0;
                    }
                }
                if (searchHandoff) {
                    BaritoneNavigationBridge.Start started = searchNext
                        ? explorer.baritoneNavigation.startHorizontal(searchX, searchZ)
                        : explorer.baritoneNavigation
                            .startAdjacent(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord);
                    if (started == BaritoneNavigationBridge.Start.STARTED) {
                        searchHandoff = false;
                        if (searchNext) {
                            searchNext = false;
                            explorer.status = "Exploring for structures";
                        } else {
                            searchMove = false;
                            rememberReturnPoint(mc);
                            structureMove = true;
                            explorer.searching = false;
                            explorer.status = "Walking to LootGame";
                        }
                    } else if (started != BaritoneNavigationBridge.Start.BUSY || ++handoffTicks > 40) {
                        explorer.stop(mc, "Baritone could not start structure navigation");
                    }
                } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                    double distToGoal = Math
                        .hypot(mc.thePlayer.posX - searchX - 0.5D, mc.thePlayer.posZ - searchZ - 0.5D);
                    boolean arrived = (explorer.baritoneNavigation.completed() && distToGoal <= 4.0D)
                        || distToGoal <= 2.5D;
                    explorer.baritoneNavigation.stop();
                    if (arrived) {
                        explorer.scan(mc);
                        if (explorer.target != null) {
                            searchHandoff = true;
                            searchNext = false;
                            handoffTicks = 0;
                        } else {
                            int[] next = explorer.search.next();
                            if (next == null) explorer.stop(mc, "No structures found in search range");
                            else {
                                searchX = next[0];
                                searchZ = next[1];
                                searchNext = true;
                                searchHandoff = true;
                                handoffTicks = 0;
                            }
                        }
                    } else explorer.stop(mc, "Baritone exploration stopped before reaching its target");
                }
                return true;
            }
        }
        if (structureMove) {
            if (explorer.world != mc.theWorld || mc.thePlayer == null || mc.theWorld == null) {
                explorer.baritoneNavigation.stop();
                structureMove = false;
                explorer.walking = false;
            } else {
                if (explorer.target != null && explorer.reachableBoard(mc)) {
                    explorer.baritoneNavigation.stop();
                    structureMove = false;
                    explorer.stop(mc, "Arrived at LootGame; opening puzzle");
                    explorer.puzzleArrivalPending = true;
                } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                    explorer.baritoneNavigation.stop();
                    structureMove = false;
                    explorer.stop(mc, "Baritone could not reach the structure");
                }
                return true;
            }
        }
        if (centerMove) {
            if (explorer.world != mc.theWorld || mc.thePlayer == null || mc.theWorld == null) {
                explorer.baritoneNavigation.stop();
                centerMove = false;
                explorer.walking = false;
            } else {
                double distToCenter = Math.hypot(
                    mc.thePlayer.posX - explorer.target.xCoord - 0.5D,
                    mc.thePlayer.posZ - explorer.target.zCoord - 0.5D);
                boolean arrived = explorer.target != null
                    && ((distToCenter <= (explorer.movingToRewardView ? 0.75D : 1.5D)
                        && Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - explorer.target.yCoord - 1) <= 2)
                        || (explorer.baritoneNavigation.completed()
                            && distToCenter <= (explorer.movingToRewardView ? 1.0D : 3.0D)));
                if (arrived) {
                    explorer.baritoneNavigation.stop();
                    centerMove = false;
                    explorer.stop(mc, "At LootGames board center");
                    explorer.puzzleCenter.puzzleCenterArrivalPending = true;
                } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                    explorer.baritoneNavigation.stop();
                    centerMove = false;
                    explorer.stop(mc, "Baritone could not reach the board center");
                }
                return true;
            }
        }
        return false;
    }
}
