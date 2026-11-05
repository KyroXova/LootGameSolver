package com.kyroxova.lootgamesolver.client;

/** Continuous yaw avoids a full render-interpolation spin at the -180/180 boundary. */
final class NavigationSteering {

    static float heading(double ax, double az, double bx, double bz, float fallback) {
        double dx = bx - ax, dz = bz - az;
        if (dx * dx + dz * dz < 0.01D) return fallback;
        return (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
    }

    static float cameraStep(float current, float desired, double seconds) {
        float error = turn(current, desired, 180F) - current;
        if (Math.abs(error) < 0.08F) return error;
        double elapsed = Math.max(0, Math.min(0.05D, seconds));
        float eased = (float) (error * (1D - Math.exp(-elapsed / 0.06D)));
        float maximum = (float) (300D * elapsed);
        return Math.max(-maximum, Math.min(maximum, eased));
    }

    static boolean passed(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01D) return false;
        double along = (px - bx) * dx + (pz - bz) * dz;
        double cross = Math.abs((px - bx) * dz - (pz - bz) * dx) / length;
        return along >= 0 && cross < 0.30D;
    }

    static float turn(float current, float desired, float maximum) {
        float difference = (desired - current) % 360F;
        if (difference >= 180F) difference -= 360F;
        if (difference < -180F) difference += 360F;
        return current + Math.max(-maximum, Math.min(maximum, difference));
    }

    static boolean reached(double distance, int footY, int nodeY, boolean water, boolean intermediate) {
        return distance < (intermediate ? 0.4D : 0.20D) && (footY == nodeY || (water && Math.abs(footY - nodeY) <= 1));
    }
}
