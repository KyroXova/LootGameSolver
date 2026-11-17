package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class StorageTripStateTest {

    @Test
    public void cannotResumeBeforeEachStepIsConfirmed() {
        StorageTripState state = new StorageTripState();
        assertFalse(state.begin(true, false));
        assertTrue(state.begin(true, true));
        state.returnArrival(true);
        assertEquals(StorageTripState.Phase.WAIT_RETURN_WARP, state.phase());
        state.returnWarpResult(true);
        state.storageArrival(true);
        state.depositResult(true, true, true);
        state.returnArrival(true);
        assertEquals(StorageTripState.Phase.READY_TO_RESUME, state.phase());
    }

    @Test
    public void incompleteOrUnconfirmedDepositDoesNotReturn() {
        StorageTripState state = new StorageTripState();
        assertTrue(state.begin(true, true));
        state.returnWarpResult(true);
        state.storageArrival(true);
        state.depositResult(true, false, true);
        state.returnArrival(true);
        assertEquals(StorageTripState.Phase.FAILED, state.phase());
    }
}
