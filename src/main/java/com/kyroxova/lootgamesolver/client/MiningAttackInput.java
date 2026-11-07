package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.settings.KeyBinding;

final class MiningAttackInput {

    private boolean owned;

    void hold(KeyBinding attack) {
        KeyBinding.setKeyBindState(attack.getKeyCode(), true);
        owned = true;
    }

    void release(KeyBinding attack, boolean physicalDown) {
        if (owned) KeyBinding.setKeyBindState(attack.getKeyCode(), physicalDown);
        owned = false;
    }

    static int allowance(float damage, boolean creative) {
        if (creative) return 40;
        if (!(damage > 0)) return 0;
        return (int) Math.min(1200D, Math.ceil(1D / damage) + 60D);
    }
}
