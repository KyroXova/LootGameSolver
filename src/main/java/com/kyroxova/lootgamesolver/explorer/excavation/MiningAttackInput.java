package com.kyroxova.lootgamesolver.explorer.excavation;

import net.minecraft.client.settings.KeyBinding;

public final class MiningAttackInput {

    private boolean owned;

    public void hold(KeyBinding attack) {
        KeyBinding.setKeyBindState(attack.getKeyCode(), true);
        owned = true;
    }

    public void release(KeyBinding attack, boolean physicalDown) {
        if (owned) KeyBinding.setKeyBindState(attack.getKeyCode(), physicalDown);
        owned = false;
    }

    public static int allowance(float damage, boolean creative) {
        if (creative) return 40;
        if (!(damage > 0)) return 0;
        return (int) Math.min(1200D, Math.ceil(1D / damage) + 60D);
    }
}
