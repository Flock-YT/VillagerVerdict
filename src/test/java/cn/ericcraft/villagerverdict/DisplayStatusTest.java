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
    void distinguishesPendingFreshReviewAnalysisAndExpired() {
        var record = v(1, trade(1, book(5)));
        var unscanned = new VillagerRecord(record.world(), record.uuid(), record.profession(), record.level(),
            record.baby(), record.x(), record.y(), record.z(), record.lastSeen(), null,
            record.manual(), record.note(), record.tags());
        assertEquals(PENDING, resolve(unscanned, AnalysisResult.Status.REVIEW, 100, 1000));
        assertEquals(REVIEW, resolve(record, AnalysisResult.Status.REVIEW, 100, 1000));
        assertEquals(ANALYZING, resolve(record, null, 100, 1000));
        assertEquals(STALE, resolve(record, AnalysisResult.Status.KEEP, 1100, 1000));
        assertEquals(STALE, resolve(record, AnalysisResult.Status.KEEP, 99, 1000));
        assertEquals(KEEP, resolve(record.withManual(VillagerRecord.Manual.PROTECT, "", Set.of()), null, 1100, 1000));
    }

    @Test
    void futureLevelsDoNotMeanUnscanned() {
        var record = v(1, trade(1, book(5)));
        record = record.withSnapshot(new TradeSnapshot(record.snapshot().trades(), 1, 100,
            TradeSnapshot.Completeness.UNLOCKED_LEVELS_REMAIN));
        assertEquals(KEEP, resolve(record, AnalysisResult.Status.KEEP, 100, 1000));
        assertEquals(REDUNDANT, resolve(record, AnalysisResult.Status.REDUNDANT, 100, 1000));
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
