package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.AnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PriceCoverageTest {
    @Test
    void cheaperSpecialistDisplacesExpensiveGeneralistWhenOtherGoodsAreCovered() {
        var cheap = v(1, trade(12, book(3)));
        var generalist = v(2, trade(32, book(3)), trade(1, item("glass", 1)));
        var glass = v(3, trade(1, item("glass", 1)));
        var result = analyze(List.of(cheap, generalist, glass), List.of(), 1);
        assertTrue(result.recommended().contains(cheap.uuid()));
        assertEquals(2, result.recommended().size());
        var redundant = result.recommended().contains(generalist.uuid()) ? glass : generalist;
        assertEquals(AnalysisResult.Status.REDUNDANT, result.verdicts().get(redundant.uuid()).status());
        assertTrue(result.gaps().isEmpty());
        assertTrue(result.verdicts().values().stream().noneMatch(r -> r.status() == AnalysisResult.Status.REVIEW));
    }

    @Test
    void redundantGeneralistIsRemovedAfterCheaperSpecialistsAreAdded() {
        var generalist = v(3, trade(32, book(3)), trade(10, item("glass", 1)));
        var cheapBook = v(1, trade(12, book(3)));
        var cheapGlass = v(2, trade(1, item("glass", 1)));
        var result = analyze(List.of(generalist, cheapBook, cheapGlass), List.of(), 1);
        assertEquals(Set.of(cheapBook.uuid(), cheapGlass.uuid()), result.recommended());
        assertEquals(AnalysisResult.Status.REDUNDANT, result.verdicts().get(generalist.uuid()).status());
    }

    @Test
    void expensiveGeneralistWithUniqueGoodsIsRetainedAlongsideCheapSpecialist() {
        var cheap = v(1, trade(12, book(3)));
        var generalist = v(2, trade(32, book(3)), trade(1, item("glass", 1)));
        assertEquals(Set.of(cheap.uuid(), generalist.uuid()),
            analyze(List.of(cheap, generalist), List.of(), 1).recommended());
    }

    @Test
    void preservesBothIncomparableMaterialPrices() {
        var a = v(1, new Trade(item("emerald", 1), item("emerald", 1), item("book", 2), book(5), 0, 12));
        var b = v(2, new Trade(item("emerald", 2), item("emerald", 2), item("book", 1), book(5), 0, 12));
        assertEquals(Set.of(a.uuid(), b.uuid()), analyze(List.of(a, b), List.of(), 1).recommended());
    }

    @Test
    void guaranteesBestPriceEvenWhenBackupAndOtherGoodsFavorExpensiveTraders() {
        var cheap = v(1, trade(1, book(5)));
        var a = v(2, trade(10, book(5)), trade(1, item("glass", 1)));
        var b = v(3, trade(20, book(5)), trade(1, item("apple", 1)));
        var result = analyze(List.of(cheap, a, b), List.of(), 2);
        assertTrue(result.recommended().contains(cheap.uuid()));
        assertTrue(result.gaps().stream().noneMatch(g -> g.startsWith("best price:")));
    }

    @Test
    void targetsDoNotAddPriceDemandsForUnrelatedGoods() {
        var cheapBook = v(1, trade(1, book(5)));
        var unrelated = v(2, trade(1, item("glass", 1)));
        var result = analyze(List.of(cheapBook, unrelated),
            List.of(new TargetTrade("target", "book", "sharpness", 1, 0, 1)), 1);
        assertEquals(Set.of(cheapBook.uuid()), result.recommended());
    }

    @Test
    void protectsBestPriceForTargetsAndRemainsDeterministic() {
        var cheap = v(1, trade(12, book(3)));
        var generalist = v(2, trade(32, book(3)), trade(1, item("glass", 1)));
        var targets = List.of(new TargetTrade("target", "book", "sharpness", 1, 0, 1));
        var result = analyze(List.of(cheap, generalist), targets, 1);
        assertEquals(Set.of(cheap.uuid()), result.recommended());
        assertEquals(result, analyze(List.of(generalist, cheap), targets, 1));
    }
}
