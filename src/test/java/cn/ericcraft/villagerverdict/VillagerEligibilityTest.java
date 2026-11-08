package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.VillagerRecord;
import org.junit.jupiter.api.Test;

class VillagerEligibilityTest {
    @Test
    void adultWorkersAreEligibleIncludingCustomProfessions() {
        assertTrue(VillagerRecord.tradable(false, "minecraft:librarian"));
        assertTrue(VillagerRecord.tradable(false, "minecraft:farmer"));
        assertTrue(VillagerRecord.tradable(false, "custom:beekeeper"));
        assertTrue(AnalysisTest.v(1).tradable());
    }

    @Test
    void unemployedAndNitwitsAreExcluded() {
        assertFalse(VillagerRecord.tradable(false, "minecraft:none"));
        assertFalse(VillagerRecord.tradable(false, "minecraft:nitwit"));
    }

    @Test
    void babiesAreExcludedRegardlessOfProfession() {
        assertFalse(VillagerRecord.tradable(true, "minecraft:librarian"));
        assertFalse(VillagerRecord.tradable(true, "minecraft:none"));
        assertFalse(VillagerRecord.tradable(true, "minecraft:nitwit"));
    }
}
