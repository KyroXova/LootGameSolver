package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class OilAvoidanceTest {
    @Test public void recognizesOilFluidsWithoutTreatingSoilAsOil() {
        assertTrue(OilAvoidance.isOil("oil"));
        assertTrue(OilAvoidance.isOil("crude_oil"));
        assertTrue(OilAvoidance.isOil("oil_heavy"));
        assertFalse(OilAvoidance.isOil("water"));
        assertFalse(OilAvoidance.isOil("soil"));
        assertFalse(OilAvoidance.isOil(null));
    }
}
