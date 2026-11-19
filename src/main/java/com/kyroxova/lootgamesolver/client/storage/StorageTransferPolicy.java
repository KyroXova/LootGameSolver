package com.kyroxova.lootgamesolver.client.storage;

/** Validates observed inventory changes after a server-side transfer attempt. */
public final class StorageTransferPolicy {

    private StorageTransferPolicy() {}

    public static boolean confirmed(int sourceBefore, int sourceAfter, int destinationBefore, int destinationAfter) {
        if (sourceBefore < 0 || sourceAfter < 0 || destinationBefore < 0 || destinationAfter < 0) return false;
        int removed = sourceBefore - sourceAfter;
        int received = destinationAfter - destinationBefore;
        return removed > 0 && removed == received;
    }
}
