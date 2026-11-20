package com.kyroxova.lootgamesolver.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public final class AimHelper {

    private AimHelper() {}

    public static float wrapAngle(float angle) {
        return MathHelper.wrapAngleTo180_float(angle);
    }

    public static float stepRotation(float current, float target, float maxStep) {
        float diff = wrapAngle(target - current);
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current + diff;
    }

    public static float calculateYaw(double dx, double dz) {
        return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    }

    public static float calculatePitch(double dx, double dy, double dz) {
        return (float) (-Math.atan2(dy, Math.hypot(dx, dz)) * 180.0D / Math.PI);
    }

    public static float[] calculateYawPitch(double dx, double dy, double dz) {
        return new float[] { calculateYaw(dx, dz), calculatePitch(dx, dy, dz) };
    }

    public static float[] calculateAim(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return calculateYawPitch(toX - fromX, toY - fromY, toZ - fromZ);
    }

    public static boolean stepTurn(EntityPlayer player, float targetYaw, float targetPitch, float maxStepYaw,
        float maxStepPitch, float toleranceYaw, float tolerancePitch) {
        if (player == null) return false;
        player.rotationYaw = stepRotation(player.rotationYaw, targetYaw, maxStepYaw);
        player.rotationPitch = stepRotation(player.rotationPitch, targetPitch, maxStepPitch);
        float yawDiff = Math.abs(wrapAngle(targetYaw - player.rotationYaw));
        float pitchDiff = Math.abs(wrapAngle(targetPitch - player.rotationPitch));
        return yawDiff <= toleranceYaw && pitchDiff <= tolerancePitch;
    }
}
