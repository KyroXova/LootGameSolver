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
    public boolean preRouting;
    public boolean searchMove;
    public boolean searchHandoff;
    public boolean searchNext;
    public boolean returnMove;
    public boolean returnWaiting;
    public boolean resumeSearch;
    public boolean surfaceMove;
    public boolean shoreMove;
    private int[] shoreGoal;
    private boolean surfaceWaiting;
    private boolean surfaceSolved;
    private int surfaceWait;
    private int surfaceStallTicks;
    private double surfaceLastX, surfaceLastY, surfaceLastZ;
    private int[] surfaceGoal;
    public boolean returnSolved;
    public int returnWait;
    public int[] returnPoint;
    public World returnWorld;
    public int searchX, searchZ;
    public int searchTicks;
    public int handoffTicks;
    private int routeFailures;
    boolean breaking;
    public final List<int[]> routePreview = new ArrayList<int[]>();
    public int previewTick;

    public void reset() {
        centerMove = false;
        structureMove = false;
        preRouting = false;
        searchMove = false;
        searchHandoff = false;
        searchNext = false;
        returnMove = false;
        returnWaiting = false;
        resumeSearch = false;
        surfaceMove = false;
        shoreMove = false;
        shoreGoal = null;
        surfaceWaiting = false;
        surfaceWait = 0;
        surfaceStallTicks = 0;
        surfaceGoal = null;
        returnSolved = false;
        returnWait = 0;
        returnPoint = null;
        returnWorld = null;
        searchX = 0;
        searchZ = 0;
        searchTicks = 0;
        handoffTicks = 0;
        routeFailures = 0;
        breaking = false;
        routePreview.clear();
        previewTick = 0;
    }

    public boolean active() {
        return centerMove || structureMove
            || searchMove
            || returnMove
            || returnWaiting
            || resumeSearch
            || surfaceMove
            || shoreMove;
    }

    static boolean needsSurfaceExit(boolean insideRoom, boolean canSeeSky) {
        return insideRoom || !canSeeSky;
    }

    boolean beginSurfaceExit(Minecraft mc, Explorer explorer) {
        return beginSurfaceExit(mc, explorer, true);
    }

    boolean beginSurfaceExit(Minecraft mc, Explorer explorer, boolean solved) {
        surfaceGoal = findSurfaceGoal(mc, 0);
        if (surfaceGoal == null) return false;
        surfaceSolved = solved;
        surfaceMove = true;
        surfaceWaiting = true;
        surfaceWait = 0;
        surfaceStallTicks = 0;
        surfaceLastX = mc.thePlayer.posX;
        surfaceLastY = mc.thePlayer.posY;
        surfaceLastZ = mc.thePlayer.posZ;
        routeFailures = 0;
        explorer.walking = true;
        explorer.searching = false;
        explorer.status = "Finding a safe route to the surface";
        return true;
    }

    private static int[] findSurfaceGoal(Minecraft mc, int skip) {
        int px = (int) Math.floor(mc.thePlayer.posX), pz = (int) Math.floor(mc.thePlayer.posZ);
        for (int radius = 0; radius <= 16; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
                if (radius != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = px + dx, z = pz + dz;
                if (!mc.theWorld.blockExists(x, 64, z)) continue;
                int y = mc.theWorld.getHeightValue(x, z);
                if (y <= 0 || y >= 254
                    || !mc.theWorld.canBlockSeeTheSky(x, y, z)
                    || !ExplorerTerrain.solidCheckpoint(mc.theWorld, x, y, z)) continue;
                if (skip-- > 0) continue;
                return new int[] { x, y, z };
            }
        }
        return null;
    }

    public void rememberReturnPoint(Minecraft mc) {
        returnPoint = null;
        returnWorld = null;
    }

    void beginShoreMove(int[] goal) {
        shoreGoal = goal.clone();
        shoreMove = true;
    }

    public boolean tick(Minecraft mc, Explorer explorer) {
        breaking = active() && explorer.baritoneNavigation.isBreaking();
        if (active() && explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING) {
            explorer.baritoneNavigation.renderOverlay(LootGameSolverConfig.showOverlay);
            if (mc.thePlayer != null) {
                boolean wantSprint = !mc.thePlayer.isInWater() && !mc.thePlayer.isSneaking()
                    && mc.thePlayer.getFoodStats()
                        .getFoodLevel() > 6;
                explorer.baritoneNavigation.setSprinting(wantSprint);
                if (wantSprint) {
                    mc.thePlayer.setSprinting(true);
                    if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                        net.minecraft.client.settings.KeyBinding
                            .setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    }
                }
            }
            if (++previewTick % 5 == 0) {
                routePreview.clear();
                routePreview.addAll(explorer.baritoneNavigation.route());
            }
            if (shoreMove) explorer.status = "Swimming to a dry entrance";
            else if (surfaceMove || returnMove) explorer.status = "Ascending to Surface";
            else if (structureMove) explorer.status = preRouting ? "Pre-routing to Puzzle" : "Walking to LootGame";
            else if (centerMove) explorer.status = "Moving to board center";
            else if (searchMove) explorer.status = "Exploring for structures";
        } else if (!routePreview.isEmpty()) {
            routePreview.clear();
            explorer.baritoneNavigation.setSprinting(false);
            if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                net.minecraft.client.settings.KeyBinding
                    .setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            }
        }
        if (shoreMove) {
            if (mc.thePlayer == null || mc.theWorld == null || explorer.world != mc.theWorld || shoreGoal == null) {
                explorer.baritoneNavigation.stop();
                shoreMove = false;
                explorer.walking = false;
                return true;
            }
            if (explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING) return true;
            boolean arrived = explorer.baritoneNavigation.completed()
                && ExplorerTerrain.dryShore(mc.theWorld, shoreGoal[0], shoreGoal[1], shoreGoal[2]);
            explorer.baritoneNavigation.stop();
            shoreMove = false;
            shoreGoal = null;
            if (arrived) {
                explorer.seekingDryEntrance = true;
                explorer.entranceCandidates.clear();
                explorer.status = "Reached dry entrance";
            } else if (!explorer.findDryEntrance(mc)) {
                explorer.stop(mc, "No safe route to a dry entrance");
                return true;
            }
            return shoreMove;
        }
        if (surfaceMove) {
            if (mc.thePlayer == null || mc.theWorld == null || explorer.world != mc.theWorld) {
                explorer.baritoneNavigation.stop();
                reset();
                explorer.walking = false;
                return false;
            }
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (!explorer.insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(x, y, z)) {
                explorer.baritoneNavigation.stop();
                surfaceMove = false;
                resumeSearch = true;
                returnWorld = mc.theWorld;
                returnSolved = surfaceSolved;
                returnWait = 0;
                explorer.status = "On the surface; preparing the next search";
                return true;
            }
            if (surfaceWaiting) {
                BaritoneNavigationBridge.Start started = explorer.baritoneNavigation
                    .start(surfaceGoal[0], surfaceGoal[1], surfaceGoal[2]);
                if (started == BaritoneNavigationBridge.Start.STARTED) surfaceWaiting = false;
                else if (started != BaritoneNavigationBridge.Start.BUSY || ++surfaceWait > 40) {
                    surfaceWait = 0;
                    surfaceGoal = findSurfaceGoal(mc, ++routeFailures);
                    if (surfaceGoal == null) {
                        routeFailures = 0;
                        surfaceGoal = findSurfaceGoal(mc, 0);
                    }
                    if (surfaceGoal == null) {
                        surfaceMove = false;
                        explorer.stop(mc, "No safe surface destination found");
                    } else explorer.status = "Replanning the surface route";
                }
            } else {
                double dx = mc.thePlayer.posX - surfaceLastX;
                double dy = mc.thePlayer.posY - surfaceLastY;
                double dz = mc.thePlayer.posZ - surfaceLastZ;
                if (dx * dx + dy * dy + dz * dz >= 0.25D) {
                    surfaceLastX = mc.thePlayer.posX;
                    surfaceLastY = mc.thePlayer.posY;
                    surfaceLastZ = mc.thePlayer.posZ;
                    surfaceStallTicks = 0;
                } else surfaceStallTicks++;
                if (explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING
                    && surfaceStallTicks < 600) return true;
                explorer.baritoneNavigation.stop();
                surfaceStallTicks = 0;
                surfaceGoal = findSurfaceGoal(mc, ++routeFailures);
                if (surfaceGoal == null || routeFailures >= 3) {
                    surfaceMove = false;
                    if (explorer.insideDungeonRoom(mc) && LootGameSolverConfig.staircaseEnabled
                        && explorer.beginDiggingToSurface(mc)) return true;
                    explorer.stop(mc, "No safe surface route found");
                } else {
                    surfaceWaiting = true;
                    surfaceWait = 0;
                    explorer.status = "Replanning the surface route";
                }
            }
            return true;
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
                    returnWaiting = false;
                    resumeSearch = true;
                    returnWait = 0;
                    explorer.status = "Continuing exploration";
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
                    returnMove = false;
                    resumeSearch = true;
                    returnWait = 0;
                    explorer.status = "Continuing exploration";
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
                if (!searchHandoff && ++searchTicks % 10 == 0) {
                    explorer.scan(mc);
                    if (explorer.target != null) {
                        explorer.baritoneNavigation.stop();
                        searchMove = false;
                        searchHandoff = false;
                        rememberReturnPoint(mc);
                        explorer.routeToStructure(mc);
                        return true;
                    }
                }
                if (searchHandoff) {
                    BaritoneNavigationBridge.Start started = searchNext
                        ? explorer.baritoneNavigation.startHorizontal(searchX, searchZ)
                        : explorer.baritoneNavigation.startStructureApproach(
                            explorer.target.xCoord,
                            explorer.target.yCoord,
                            explorer.target.zCoord);
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
                        searchHandoff = false;
                        explorer.rotateSearch(mc);
                        searchNext = true;
                        explorer.status = "Replanning search";
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
                            searchMove = false;
                            searchHandoff = false;
                            rememberReturnPoint(mc);
                            explorer.routeToStructure(mc);
                        } else {
                            int[] next = explorer.search == null ? null : explorer.search.next();
                            if (next == null) {
                                explorer.rotateSearch(mc);
                                next = explorer.search == null ? null : explorer.search.next();
                            }
                            if (next != null) {
                                searchX = next[0];
                                searchZ = next[1];
                                searchNext = true;
                                searchHandoff = true;
                                handoffTicks = 0;
                                routeFailures = 0;
                            }
                        }
                    } else if (distToGoal > 4.0D && ++routeFailures <= 12) {
                        searchNext = true;
                        searchHandoff = true;
                        handoffTicks = 0;
                        explorer.status = "Exploring for structures";
                    } else {
                        routeFailures = 0;
                        int[] next = explorer.search == null ? null : explorer.search.next();
                        if (next == null) {
                            explorer.rotateSearch(mc);
                            next = explorer.search == null ? null : explorer.search.next();
                        }
                        if (next != null) {
                            searchX = next[0];
                            searchZ = next[1];
                            searchNext = true;
                            searchHandoff = true;
                            handoffTicks = 0;
                        }
                    }
                }
                return true;
            }
        }
        if (structureMove) {
            if (explorer.world != mc.theWorld || mc.thePlayer == null || mc.theWorld == null) {
                explorer.baritoneNavigation.stop();
                structureMove = false;
                preRouting = false;
                explorer.walking = false;
            } else {
                if (explorer.target != null && explorer.reachableBoard(mc)) {
                    int[] arrivedAt = { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord };
                    explorer.baritoneNavigation.stop();
                    structureMove = false;
                    preRouting = false;
                    explorer.stop(mc, "Arrived at LootGame; opening puzzle");
                    explorer.knownStructure = arrivedAt;
                    explorer.puzzleArrivalPending = true;
                } else if (preRouting && explorer.target != null) {
                    double hDist = Math.hypot(
                        mc.thePlayer.posX - explorer.target.xCoord - 0.5D,
                        mc.thePlayer.posZ - explorer.target.zCoord - 0.5D);
                    if (hDist <= 6.0D || explorer.baritoneNavigation.completed()) {
                        preRouting = false;
                        explorer.baritoneNavigation.stop();
                        if (mc.thePlayer.isInWater() && explorer.findDryEntrance(mc)) {
                            structureMove = false;
                            explorer.walking = true;
                            explorer.searching = false;
                            return true;
                        }
                        BaritoneNavigationBridge.Start start = explorer.baritoneNavigation.startStructureApproach(
                            explorer.target.xCoord,
                            explorer.target.yCoord,
                            explorer.target.zCoord);
                        if (start == BaritoneNavigationBridge.Start.STARTED) {
                            explorer.status = "Walking to LootGame";
                        } else if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) {
                            structureMove = false;
                            explorer.walking = true;
                            explorer.searching = false;
                        } else {
                            structureMove = false;
                            explorer.routeToStructure(mc);
                        }
                    } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                        explorer.baritoneNavigation.startHorizontal(explorer.target.xCoord, explorer.target.zCoord);
                    }
                } else if (explorer.baritoneNavigation.state() != BaritoneNavigationBridge.State.RUNNING) {
                    explorer.baritoneNavigation.stop();
                    structureMove = false;
                    preRouting = false;
                    if (explorer.target != null && ++routeFailures <= 3) {
                        explorer.routeToStructure(mc);
                    } else {
                        routeFailures = 0;
                        if (explorer.target != null && LootGameSolverConfig.staircaseEnabled
                            && explorer.selectDigEntrance(mc)) {
                            explorer.walking = true;
                            explorer.searching = false;
                        } else {
                            explorer.target = null;
                            explorer.rotateSearch(mc);
                            searchMove = true;
                            searchNext = true;
                            searchHandoff = true;
                            handoffTicks = 0;
                            explorer.status = "Exploring for structures";
                        }
                    }
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
                if (explorer.target == null) {
                    centerMove = false;
                    explorer.stop(mc, "Board center target lost");
                    return true;
                }
                double distToCenter = Math.hypot(
                    mc.thePlayer.posX - explorer.target.xCoord - 0.5D,
                    mc.thePlayer.posZ - explorer.target.zCoord - 0.5D);
                boolean arrived = explorer.target != null
                    && ((distToCenter <= (explorer.movingToRewardView ? 0.75D : 1.5D)
                        && Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - explorer.target.yCoord - 1) <= 2)
                        || (explorer.baritoneNavigation.completed()
                            && distToCenter <= (explorer.movingToRewardView ? 1.0D : 2.2D)));
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
