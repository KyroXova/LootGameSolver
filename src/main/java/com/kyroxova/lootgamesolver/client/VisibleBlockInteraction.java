package com.kyroxova.lootgamesolver.client;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

final class VisibleBlockInteraction {

    private VisibleBlockInteraction() {}

    static boolean matches(MovingObjectPosition crosshair, MovingObjectPosition ray, Vec3 eye, int x, int y, int z,
        double reach) {
        if (eye == null || reach <= 0
            || !target(crosshair, x, y, z)
            || !target(ray, x, y, z)
            || crosshair.sideHit != ray.sideHit) return false;
        double crosshairDistance = eye.squareDistanceTo(crosshair.hitVec);
        double rayDistance = eye.squareDistanceTo(ray.hitVec);
        return Double.isFinite(crosshairDistance) && Double.isFinite(rayDistance)
            && crosshairDistance <= reach * reach
            && rayDistance <= reach * reach;
    }

    private static boolean target(MovingObjectPosition hit, int x, int y, int z) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.hitVec != null
            && hit.blockX == x
            && hit.blockY == y
            && hit.blockZ == z;
    }
}
