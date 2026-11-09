package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.AnalysisTest.*;
import static cn.ericcraft.villagerverdict.model.DisplayStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.Settings;
import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Test;

class DisplayStatusTest {
    @Test
    void exposesOnlyScannedKeepDiscardStates() {
        var record = v(1, trade(1, book(5)));
        var unscanned = new VillagerRecord(record.world(), record.uuid(), record.profession(), record.level(),
            record.baby(), record.x(), record.y(), record.z(), record.lastSeen(), null,
            record.manual(), record.note(), record.tags());
        assertEquals(UNSCANNED, resolve(unscanned, null));
        assertEquals(DISCARD, resolve(record, AnalysisResult.Status.DISCARD));
        assertEquals(UNSCANNED, resolve(record, null));
        assertEquals(KEEP, resolve(record, AnalysisResult.Status.KEEP));
        assertEquals(KEEP, resolve(record.withManual(VillagerRecord.Manual.KEEP, "", Set.of()), null));
        assertEquals(UNSCANNED, resolve(unscanned.withManual(VillagerRecord.Manual.KEEP, "", Set.of()), null));
        assertEquals(3, values().length);
    }


    @Test
    void professionSelectionSurvivesSerializationAndServerToggle() {
        var original = Settings.defaults();
        assertTrue(original.professionEnabled("minecraft:librarian"));
        var filtered = original.withExcludedProfessions(Set.of("minecraft:armorer"));
        assertTrue(filtered.professionEnabled("minecraft:librarian"));
        assertFalse(filtered.professionEnabled("minecraft:armorer"));
        assertEquals(filtered, new Gson().fromJson(new Gson().toJson(filtered), Settings.class));
        assertEquals(filtered.excludedProfessions(), filtered.auto("server", true, true).excludedProfessions());
        assertTrue(original.professionEnabled("minecraft:armorer"));
    }
}
