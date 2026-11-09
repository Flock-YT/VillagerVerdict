package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.AnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.ReplacementSelection;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReplacementSelectionTest {
    private final Trade apple = trade(5, enchantedBook("minecraft:mending", 1));
    private final Trade bread = trade(5, enchantedBook("minecraft:multishot", 1));
    private final VillagerRecord source = v(1, apple, bread);
    private final VillagerRecord first = v(2, trade(1, enchantedBook("minecraft:mending", 1)), trade(1, enchantedBook("minecraft:multishot", 1)));
    private final VillagerRecord second = v(3, trade(1, enchantedBook("minecraft:multishot", 1)));

    private ComparisonResult comparison(VillagerRecord target, Trade original) {
        return new ComparisonResult(target.uuid(), target.x(), target.y(), target.z(), original,
            target.snapshot().trades().stream().filter(t -> t.output().equals(original.output())).findFirst()
                .orElse(target.snapshot().trades().getFirst()), Map.of(), Map.of(), false);
    }

    private AnalysisResult result(ComparisonResult... comparisons) {
        return new AnalysisResult(Set.of(first.uuid(), second.uuid()), Map.of(
            source.uuid(), new AnalysisResult.Verdict(AnalysisResult.Status.DISCARD, "", List.of(comparisons), 0, 0),
            first.uuid(), new AnalysisResult.Verdict(AnalysisResult.Status.KEEP, "", List.of(), 0, 0),
            second.uuid(), new AnalysisResult.Verdict(AnalysisResult.Status.KEEP, "", List.of(), 0, 0)), List.of());
    }

    private Map<UUID, VillagerRecord> available() {
        return Map.of(source.uuid(), source, first.uuid(), first, second.uuid(), second);
    }

    @Test
    void deduplicatesVillagersAcrossTrades() {
        var selection = ReplacementSelection.resolve(source,
            result(comparison(first, apple), comparison(first, bread)), available());
        assertEquals(Set.of(first.uuid()), selection.targets());
        assertFalse(selection.partial());
    }

    @Test
    void includesEveryVillagerNeededForJointReplacement() {
        var selection = ReplacementSelection.resolve(source,
            result(comparison(first, apple), comparison(second, bread)), available());
        assertEquals(Set.of(first.uuid(), second.uuid()), selection.targets());
        assertFalse(selection.partial());
    }

    @Test
    void distinguishesPartialAndAbsentReplacements() {
        var partial = ReplacementSelection.resolve(source, result(comparison(first, apple)), available());
        assertEquals(Set.of(first.uuid()), partial.targets());
        assertTrue(partial.partial());
        var absent = ReplacementSelection.resolve(source, result(), available());
        assertTrue(absent.targets().isEmpty());
        assertTrue(absent.partial());
    }

    @Test
    void excludesUnavailableAndUnscannedReplacements() {
        var analysis = result(comparison(first, apple), comparison(second, bread));
        assertTrue(ReplacementSelection.resolve(source, analysis, Map.of()).targets().isEmpty());
        var available = new HashMap<>(available());
        available.put(first.uuid(), first.withSnapshot(null));
        var selection = ReplacementSelection.resolve(source, analysis, available);
        assertEquals(Set.of(second.uuid()), selection.targets());
        assertTrue(selection.partial());
    }

    @Test
    void excludesDiscardedUnrecommendedAndSelfReferences() {
        var analysis = result(comparison(first, apple), comparison(second, bread), comparison(source, apple));
        var verdicts = new HashMap<>(analysis.verdicts());
        verdicts.put(first.uuid(), new AnalysisResult.Verdict(AnalysisResult.Status.DISCARD, "", List.of(), 0, 0));
        analysis = new AnalysisResult(Set.of(first.uuid(), source.uuid()), verdicts, List.of());
        assertTrue(ReplacementSelection.resolve(source, analysis, available()).targets().isEmpty());
    }

    @Test
    void staleOrOrdinaryComparisonsDoNotCreateReplacementTargets() {
        var ordinary = trade(1, item("minecraft:bookshelf", 1));
        var stale = new ComparisonResult(first.uuid(), 0, 0, 0, ordinary, ordinary, Map.of(), Map.of(), false);
        assertTrue(ReplacementSelection.resolve(source, result(stale), available()).targets().isEmpty());
        var invalid = new ComparisonResult(first.uuid(), 0, 0, 0, bread,
            first.snapshot().trades().getFirst(), Map.of(), Map.of(), false);
        assertTrue(ReplacementSelection.resolve(source, result(invalid), available()).targets().isEmpty());
        var available = new HashMap<>(available());
        available.put(first.uuid(), first.withSnapshot(new TradeSnapshot(List.of(ordinary), 101)));
        assertTrue(ReplacementSelection.resolve(source, result(comparison(first, apple)), available).targets().isEmpty());
    }

    @Test
    void onlyDiscardSourcesCanBeViewed() {
        var analysis = result(comparison(first, apple));
        assertTrue(ReplacementSelection.resolve(source.withManual(VillagerRecord.Manual.KEEP, "", Set.of()),
            analysis, available()).targets().isEmpty());
        assertTrue(ReplacementSelection.resolve(source.withSnapshot(null), analysis, available()).targets().isEmpty());
        assertTrue(ReplacementSelection.resolve(source, AnalysisResult.empty(), available()).targets().isEmpty());
    }
}
