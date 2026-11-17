package com.kyroxova.lootgamesolver.client;

/** Optional integration with the same server request used by BogoSorter's drop-off button. */
final class BogoDropOffBridge {

    private BogoDropOffBridge() {}

    static boolean request() {
        try {
            Class<?> packetType = Class.forName("com.cleanroommc.bogosorter.common.network.IPacket");
            Class<?> dropOff = Class.forName("com.cleanroommc.bogosorter.common.network.CDropOff");
            Class<?> network = Class.forName("com.cleanroommc.bogosorter.common.network.NetworkHandler");
            Object packet = dropOff.getMethod("fromClientPreference")
                .invoke(null);
            if (packet == null || !packetType.isInstance(packet)) return false;
            network.getMethod("sendToServer", packetType)
                .invoke(null, packet);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }
}
