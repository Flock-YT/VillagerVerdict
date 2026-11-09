package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.AnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.*;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SignatureAnalysisTest {
    private VillagerRecord profession(VillagerRecord v, String profession) {
        return new VillagerRecord(v.world(), v.uuid(), "minecraft:" + profession, v.level(), v.baby(),
            v.x(), v.y(), v.z(), v.lastSeen(), v.snapshot(), v.manual(), v.note(), v.tags());
    }

    private Trade book(String id, int level, int price) {
        return trade(price, enchantedBook("minecraft:" + id, level));
    }

    @Test
    void sharedOrdinaryTradesCannotReplaceMultishot() {
        var paper = new Trade(item("minecraft:paper", 24), item("minecraft:paper", 24), null,
            item("minecraft:emerald", 1), 0, 12);
        var glass = trade(1, item("minecraft:glass", 4));
        var left = v(1, paper, glass, book("protection", 3, 31), book("fire_protection", 1, 5));
        var right = v(2, paper, glass, book("multishot", 1, 19));
        var result = analyze(List.of(left, right), 1);
        assertEquals(Set.of(left.uuid(), right.uuid()), result.recommended());
        assertTrue(result.verdicts().get(right.uuid()).replacements().isEmpty());
        var replacement = v(3, book("multishot", 1, 10));
        result = analyze(List.of(left, right, replacement), 1);
        assertEquals(AnalysisResult.Status.DISCARD, result.verdicts().get(right.uuid()).status());
        assertEquals(Set.of(replacement.uuid()), ReplacementSelection.resolve(right, result,
            Map.of(left.uuid(), left, right.uuid(), right, replacement.uuid(), replacement)).targets());
    }

    @Test
    void fourthAndLaterBooksPreserveUniqueValueAndProduceCompleteJointExplanation() {
        var names = List.of("protection", "mending", "unbreaking", "multishot", "fire_protection");
        var source = v(1, names.stream().map(n -> book(n, 1, 20)).toArray(Trade[]::new));
        var firstThree = v(2, names.subList(0, 3).stream().map(n -> book(n, 1, 1)).toArray(Trade[]::new));
        var fourBooks = source.withSnapshot(new TradeSnapshot(source.snapshot().trades().subList(0, 4), 100));
        var fourResult = analyze(List.of(fourBooks, firstThree), 1);
        assertEquals(AnalysisResult.Status.KEEP, fourResult.verdicts().get(source.uuid()).status());
        assertEquals(1, fourResult.verdicts().get(source.uuid()).uniqueTrades());
        var result = analyze(List.of(source, firstThree), 1);
        assertEquals(AnalysisResult.Status.KEEP, result.verdicts().get(source.uuid()).status());
        var lastTwo = v(3, book("multishot", 1, 1), book("fire_protection", 1, 1));
        result = analyze(List.of(source, firstThree, lastTwo), 1);
        assertEquals(AnalysisResult.Status.DISCARD, result.verdicts().get(source.uuid()).status());
        assertEquals(5, result.verdicts().get(source.uuid()).replacements().size());
        var selection = ReplacementSelection.resolve(source, result,
            Map.of(source.uuid(), source, firstThree.uuid(), firstThree, lastTwo.uuid(), lastTwo));
        assertEquals(Set.of(firstThree.uuid(), lastTwo.uuid()), selection.targets());
        assertFalse(selection.partial());
        assertEquals(5, ProfessionHighlights.select(source.profession(), source.snapshot().trades()).size());
    }

    @Test
    void professionsDoNotReplaceEachOthersDiamondAxes() {
        var tool = profession(v(1, trade(30, item("minecraft:diamond_axe", 1))), "toolsmith");
        var weapon = profession(v(2, trade(1, item("minecraft:diamond_axe", 1))), "weaponsmith");
        var result = analyze(List.of(tool, weapon), 1);
        assertEquals(Set.of(tool.uuid(), weapon.uuid()), result.recommended());
        assertTrue(result.verdicts().values().stream().allMatch(v -> v.replacements().isEmpty()));
    }

    @Test
    void armorerAndToolsmithOnlyUseTheirDiamondGoods() {
        var iron = profession(v(1, trade(1, item("minecraft:iron_chestplate", 1))), "armorer");
        var diamond = profession(v(2, trade(10, item("minecraft:diamond_chestplate", 1))), "armorer");
        var wrongTool = profession(v(3, trade(1, item("minecraft:diamond_pickaxe", 1))), "weaponsmith");
        var tool = profession(v(4, trade(10, item("minecraft:diamond_hoe", 1))), "toolsmith");
        var result = analyze(List.of(iron, diamond, wrongTool, tool), 1);
        assertEquals(Set.of(diamond.uuid(), tool.uuid()), result.recommended());
        for (var v : List.of(iron, wrongTool)) {
            assertEquals("no signature goods", result.verdicts().get(v.uuid()).reason());
            assertTrue(result.verdicts().get(v.uuid()).replacements().isEmpty());
        }
        iron = iron.withManual(VillagerRecord.Manual.KEEP, "", Set.of());
        assertTrue(analyze(List.of(iron, diamond), 1).recommended().contains(iron.uuid()));
    }

    @Test
    void ordinaryTradePricesDoNotAffectTieBreakOrExplainDiscard() {
        var first = v(1, book("mending", 1, 10), trade(64, item("minecraft:bookshelf", 1)));
        var second = v(2, book("mending", 1, 10), trade(1, item("minecraft:bookshelf", 1)));
        var result = analyze(List.of(first, second), 1);
        assertEquals(Set.of(first.uuid()), result.recommended());
        assertEquals(1, result.verdicts().get(second.uuid()).replacements().size());
        assertEquals(Set.of(first.uuid(), second.uuid()), analyze(List.of(first, second), 2).recommended());
    }

    @Test
    void productQueriesMatchOnlySignatureGoodsAndBasePrice() {
        var normal = trade(5, enchantedBook("minecraft:multishot", 1));
        var discounted = new Trade(normal.baseInput(), item("emerald", 1), null, normal.output(), 0, 12);
        var worker = v(1, discounted, trade(1, item("minecraft:bookshelf", 1)));
        assertTrue(new TargetTrade("q", "minecraft:enchanted_book", "minecraft:multishot", 1, 5).matches(worker));
        assertFalse(new TargetTrade("q", "minecraft:enchanted_book", "minecraft:multishot", 1, 4).matches(worker));
        assertFalse(new TargetTrade("q", "minecraft:enchanted_book", "minecraft:multishot", 2, 0).matches(worker));
        assertFalse(new TargetTrade("q", "minecraft:enchanted_book", "minecraft:mending", 0, 0).matches(worker));
        assertFalse(new TargetTrade("q", "minecraft:bookshelf", "", 0, 0).matches(worker));
        assertFalse(new TargetTrade("q", "minecraft:enchanted_book", "", 0, 0).matches(worker.withSnapshot(null)));
    }

}
