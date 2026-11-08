package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.client.settings.KeyBinding;

import org.junit.Test;

public class MiningAttackInputTest {

    @Test
    public void holdsAttackAcrossVanillaTicksAndReleasesOnStop() {
        KeyBinding binding = new KeyBinding("test.mining.attack", -95, "test");
        MiningAttackInput input = new MiningAttackInput();
        input.hold(binding);
        for (int tick = 0; tick < 30; tick++) {
            assertTrue("Vanilla must see held attack before each end-of-tick update", binding.getIsKeyPressed());
            input.release(binding, false);
            input.hold(binding);
        }
        input.release(binding, false);
        assertFalse(binding.getIsKeyPressed());
        input.hold(binding);
        input.release(binding, true);
        assertTrue(binding.getIsKeyPressed());
        KeyBinding.setKeyBindState(binding.getKeyCode(), false);
    }

    @Test
    public void workBudgetUsesToolSpeedAndCannotOverflowOrWaitForever() {
        assertEquals(70, MiningAttackInput.allowance(0.1F, false));
        assertEquals(1200, MiningAttackInput.allowance(0.0000001F, false));
        assertEquals(0, MiningAttackInput.allowance(0, false));
        assertEquals(40, MiningAttackInput.allowance(0, true));
    }
}
