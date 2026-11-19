package com.kyroxova.lootgamesolver.client.storage;

import com.kyroxova.lootgamesolver.client.BaritoneInventoryBridge;
import net.minecraft.client.Minecraft;

/** Waits for an accepted transaction and a short inventory synchronization interval. */
public final class VerifiedContainerClick {

    public enum Result {
        WAITING,
        ACCEPTED,
        FAILED
    }

    private final ContainerClickConfirmation confirmation;
    private int ticks, settle = -1;

    private VerifiedContainerClick(ContainerClickConfirmation confirmation) {
        this.confirmation = confirmation;
    }

    public static VerifiedContainerClick start(Minecraft mc, int slot, boolean quick) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.openContainer == null) return null;
        ContainerClickConfirmation confirmation = ContainerClickConfirmation
            .attach(mc, mc.thePlayer.openContainer.windowId, slot, quick ? 1 : 0);
        if (confirmation == null) return null;
        boolean sent = quick ? BaritoneInventoryBridge.quickMove(mc, slot)
            : BaritoneInventoryBridge.leftClick(mc, slot);
        if (!sent) {
            confirmation.close();
            return null;
        }
        return new VerifiedContainerClick(confirmation);
    }

    public Result tick() {
        if (++ticks > 120 || confirmation.response() < 0) {
            close();
            return Result.FAILED;
        }
        if (confirmation.response() == 0) return Result.WAITING;
        if (settle < 0) settle = 3;
        if (settle-- > 0) return Result.WAITING;
        close();
        return Result.ACCEPTED;
    }

    public void close() {
        confirmation.close();
    }
}
