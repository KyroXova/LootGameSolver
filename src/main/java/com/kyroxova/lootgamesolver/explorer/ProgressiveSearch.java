package com.kyroxova.lootgamesolver.explorer;

public final class ProgressiveSearch {

    private final int x;
    private final int z;
    private final double dirX;
    private final double dirZ;
    private final int limit;
    private final int stepDistance;
    private int step;

    public ProgressiveSearch(int x, int z, int limit) {
        this(x, z, 0.0F, limit, 64);
    }

    public ProgressiveSearch(int x, int z, float yaw, int limit) {
        this(x, z, yaw, limit, 64);
    }

    public ProgressiveSearch(int x, int z, float yaw, int limit, int stepDistance) {
        this.x = x;
        this.z = z;
        this.limit = limit;
        this.stepDistance = stepDistance > 0 ? stepDistance : 64;
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        double len = Math.hypot(dx, dz);
        if (len < 1e-4) {
            this.dirX = 0.0D;
            this.dirZ = 1.0D;
        } else {
            this.dirX = dx / len;
            this.dirZ = dz / len;
        }
    }

    public int[] next() {
        step++;
        int dist = step * stepDistance;
        if (limit > 0 && dist > limit) return null;
        return new int[] { (int) Math.round(x + dirX * dist), (int) Math.round(z + dirZ * dist) };
    }
}
