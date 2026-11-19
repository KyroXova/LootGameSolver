package com.kyroxova.lootgamesolver.client.storage;

public final class StorageTripPolicy {

    private StorageTripPolicy() {}

    public static boolean destinationConfigured(boolean enabled, String configuredWorld, String currentWorld,
        String destination, String[] targets) {
        if (!enabled || configuredWorld == null
            || configuredWorld.isEmpty()
            || !configuredWorld.equals(currentWorld)
            || destination == null
            || targets == null
            || targets.length == 0) return false;
        boolean marked = false;
        for (String target : targets) if (validTarget(target)) {
            marked = true;
            break;
        }
        if (!marked) return false;
        if ("/home".equals(destination)) return true;
        return destination.startsWith("/warp ") && StorageCommand.validWarpName(destination.substring(6));
    }

    public static boolean validTarget(String target) {
        if (target == null) return false;
        String[] fields = target.split(":");
        try {
            if (fields.length == 5 && "block".equals(fields[0])) {
                Integer.parseInt(fields[1]);
                Integer.parseInt(fields[2]);
                int y = Integer.parseInt(fields[3]);
                Integer.parseInt(fields[4]);
                return y >= 0 && y < 256;
            }
            if (fields.length == 3 && "entity".equals(fields[0])) {
                Integer.parseInt(fields[1]);
                java.util.UUID.fromString(fields[2]);
                return true;
            }
        } catch (IllegalArgumentException ignored) {}
        return false;
    }

    public static boolean near(int currentDimension, double x, double y, double z, int targetDimension, double tx, double ty,
        double tz, double radius) {
        if (currentDimension != targetDimension || radius < 0
            || !Double.isFinite(radius)
            || !Double.isFinite(x)
            || !Double.isFinite(y)
            || !Double.isFinite(z)
            || !Double.isFinite(tx)
            || !Double.isFinite(ty)
            || !Double.isFinite(tz)) return false;
        double dx = x - tx, dy = y - ty, dz = z - tz;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
