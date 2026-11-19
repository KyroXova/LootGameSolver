package com.kyroxova.lootgamesolver.explorer.pathfinder;

public final class NavigationSteering {

    public static Float straightHeading(java.util.List<int[]> route, int x, int y, int z) {
        int directionX = 0, directionZ = 0, steps = 0;
        int start = 0;
        for (int i = 0; i < route.size(); i++) {
            int[] position = route.get(i);
            if (position[0] == x && position[1] == y && position[2] == z) {
                start = i + 1;
                break;
            }
        }
        for (int i = start; i < route.size(); i++) {
            int[] next = route.get(i);
            int dx = next[0] - x, dz = next[2] - z;
            if (dx == 0 && dz == 0) continue;
            if (Math.abs(dx) > 1 || Math.abs(dz) > 1) break;
            if (steps > 0 && (dx != directionX || dz != directionZ)) break;
            directionX = dx;
            directionZ = dz;
            x = next[0];
            z = next[2];
            if (++steps >= 3) return (float) Math.toDegrees(Math.atan2(directionZ, directionX)) - 90F;
        }
        return null;
    }

    public static boolean yieldCameraToBaritone(boolean baritoneActive, boolean interacting, boolean attacking, int cooldown) {
        return baritoneActive && (interacting || attacking || cooldown > 0);
    }

    public static Float pathLookHeading(java.util.List<int[]> route, double posX, double posZ) {
        if (route == null || route.isEmpty()) return null;
        if (route.size() == 1) {
            int[] point = route.get(0);
            double dx = point[0] + 0.5D - posX;
            double dz = point[2] + 0.5D - posZ;
            if (dx * dx + dz * dz > 0.04D) {
                return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            }
            return null;
        }
        int closestIdx = 0;
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            int[] point = route.get(i);
            double dx = point[0] + 0.5D - posX;
            double dz = point[2] + 0.5D - posZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < minDistSq) {
                minDistSq = distSq;
                closestIdx = i;
            }
        }
        int targetIdx;
        if (minDistSq > 0.5D && closestIdx > 0) {
            targetIdx = closestIdx;
        } else {
            targetIdx = Math.min(closestIdx + 1, route.size() - 1);
        }
        int[] target = route.get(targetIdx);
        double dx = target[0] + 0.5D - posX;
        double dz = target[2] + 0.5D - posZ;
        if (dx * dx + dz * dz < 0.16D && targetIdx + 1 < route.size()) {
            target = route.get(targetIdx + 1);
            dx = target[0] + 0.5D - posX;
            dz = target[2] + 0.5D - posZ;
        }
        if (dx * dx + dz * dz < 0.01D) {
            if (targetIdx > 0) {
                int[] prev = route.get(targetIdx - 1);
                dx = target[0] - prev[0];
                dz = target[2] - prev[2];
                if (dx * dx + dz * dz > 0.01D) {
                    return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
                }
            }
            return null;
        }
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
    }

    public static Float walkingHeading(double vx, double vz, float fallback, java.util.List<int[]> route) {
        if (vx * vx + vz * vz > 0.0025D) return Float.valueOf(heading(0, 0, vx, vz, fallback));
        return pathHeading(route);
    }

    public static Float pathHeading(java.util.List<int[]> route) {
        for (int i = 1; i < route.size(); i++) {
            int[] previous = route.get(i - 1), next = route.get(i);
            int dx = next[0] - previous[0], dz = next[2] - previous[2];
            if (dx != 0 || dz != 0) return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        }
        return null;
    }

    public static void appendWithoutLoops(java.util.List<int[]> route, java.util.List<int[]> continuation) {
        for (int[] step : continuation) {
            int match = -1;
            for (int i = 0; i < route.size(); i++) {
                int[] prior = route.get(i);
                if (prior[0] == step[0] && prior[1] == step[1] && prior[2] == step[2]) {
                    match = i;
                    break;
                }
            }
            if (match >= 0) {
                while (route.size() > match + 1) route.remove(route.size() - 1);
            } else route.add(step);
        }
    }

    public static float heading(double ax, double az, double bx, double bz, float fallback) {
        double dx = bx - ax, dz = bz - az;
        if (dx * dx + dz * dz < 0.01D) return fallback;
        return (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
    }

    public static float cameraStep(float current, float desired, double seconds) {
        return cameraStep(current, desired, seconds, 0.09D, 300D);
    }

    public static float interactionCameraStep(float current, float desired, double seconds) {
        return cameraStep(current, desired, seconds, 0.04D, 500D);
    }

    private static float cameraStep(float current, float desired, double seconds, double smoothing, double speed) {
        float error = turn(current, desired, 180F) - current;
        if (Math.abs(error) < 0.08F) return error;
        double elapsed = Math.max(0, Math.min(0.05D, seconds));
        float eased = (float) (error * (1D - Math.exp(-elapsed / smoothing)));
        float maximum = (float) (speed * elapsed);
        return Math.max(-maximum, Math.min(maximum, eased));
    }

    public static boolean passed(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01D) return false;
        double along = (px - bx) * dx + (pz - bz) * dz;
        double cross = Math.abs((px - bx) * dz - (pz - bz) * dx) / length;
        return along >= 0 && cross < 0.30D;
    }

    public static float turn(float current, float desired, float maximum) {
        float difference = (desired - current) % 360F;
        if (difference >= 180F) difference -= 360F;
        if (difference < -180F) difference += 360F;
        return current + Math.max(-maximum, Math.min(maximum, difference));
    }

    public static boolean reached(double distance, int footY, int nodeY, boolean water, boolean intermediate) {
        return distance < (intermediate ? 0.48D : 0.20D) && (footY == nodeY || (water && Math.abs(footY - nodeY) <= 1));
    }
}
