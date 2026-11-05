package com.kyroxova.lootgamesolver.client;

/** Continuous yaw avoids a full render-interpolation spin at the -180/180 boundary. */
final class NavigationSteering {

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
