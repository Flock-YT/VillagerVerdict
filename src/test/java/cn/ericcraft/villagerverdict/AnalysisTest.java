package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.*;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.*;

class AnalysisTest {
  static final WorldIdentity WORLD = new WorldIdentity("test", "default", "minecraft:overworld");

  static ItemData item(String id, int count) {
    return new ItemData(
        id, count, "{\"id\":\"" + id + "\",\"count\":" + count + "}", "{}", Map.of(), id);
  }

  static ItemData book(int level) {
    return new ItemData("book", 1, "{}", "{}", Map.of("sharpness", level), "book");
  }

  static Trade trade(int price, ItemData output) {
    return new Trade(item("emerald", price), item("emerald", price), null, output, 0, 12);
  }

  static VillagerRecord v(int id, Trade... trades) {
    return new VillagerRecord(
        WORLD,
        new UUID(0, id),
        "minecraft:librarian",
        5,
        false,
        id,
        64,
        0,
        100,
        new TradeSnapshot(List.of(trades), 5, 100, TradeSnapshot.Completeness.CURRENT),
        VillagerRecord.Manual.AUTO,
        "",
        Set.of());
  }

  static AnalysisResult analyze(
      List<VillagerRecord> records, List<TargetTrade> targets, int backups) {
    return new Optimizer()
        .analyze(
            records,
            targets,
            records.stream().map(VillagerRecord::uuid).collect(java.util.stream.Collectors.toSet()),
            100,
            1000,
            backups);
  }

  @Test
  void normalizesPerOutput() {
    assertTrue(TradeComparison.replaces(trade(3, item("apple", 2)), trade(2, item("apple", 1))));
  }

  @Test
  void currenciesIncomparable() {
    var a = trade(1, item("apple", 1));
    var b = new Trade(item("diamond", 1), item("diamond", 1), null, item("apple", 1), 0, 12);
    assertFalse(TradeComparison.replaces(a, b));
    assertFalse(TradeComparison.replaces(b, a));
  }

  @Test
  void twoInputsUseVector() {
    var a =
        new Trade(
            item("emerald", 1), item("emerald", 1), item("diamond", 2), item("apple", 1), 0, 10);
    var b =
        new Trade(
            item("emerald", 2), item("emerald", 2), item("diamond", 1), item("apple", 1), 0, 10);
    assertFalse(TradeComparison.replaces(a, b));
    assertFalse(TradeComparison.replaces(b, a));
  }

  @Test
  void vectorOrderComparesEveryInput() {
    var cheaperSecond =
        new Trade(
            item("emerald", 1), item("emerald", 1), item("diamond", 2), item("apple", 1), 0, 10);
    var moreExpensiveSecond =
        new Trade(
            item("emerald", 1), item("emerald", 1), item("diamond", 3), item("apple", 1), 0, 10);
    assertTrue(
        TradeComparison.vectorOrder(
                cheaperSecond.cost(false), moreExpensiveSecond.cost(false))
            < 0);
  }

  @Test
  void repeatedInputResourceAggregates() {
    var t =
        new Trade(
            item("emerald", 2), item("emerald", 1), item("emerald", 3), item("apple", 1), 0, 1);
    assertEquals(5, t.cost(false).values().iterator().next());
    assertEquals(4, t.cost(true).values().iterator().next());
  }

  @Test
  void componentsDoNotCollapse() {
    var special = new ItemData("apple", 1, "{}", "{custom:1}", Map.of(), "special");
    assertFalse(TradeComparison.replaces(trade(1, special), trade(5, item("apple", 1))));
  }

  @Test
  void higherLevelWinsRegardlessOfPrice() {
    assertTrue(TradeComparison.replaces(trade(64, book(5)), trade(1, book(4))));
    assertFalse(TradeComparison.replaces(trade(1, book(4)), trade(64, book(5))));
  }

  @Test
  void sameLevelUsesPrice() {
    assertTrue(TradeComparison.replaces(trade(2, book(5)), trade(10, book(5))));
    assertFalse(TradeComparison.replaces(trade(10, book(5)), trade(2, book(5))));
  }

  @Test
  void enchantmentTradeoffsDoNotDominate() {
    var a = new ItemData("sword", 1, "{}", "{}", Map.of("a", 5, "b", 1), "sword");
    var b = new ItemData("sword", 1, "{}", "{}", Map.of("a", 1, "b", 5), "sword");
    assertFalse(a.dominates(b));
    assertFalse(b.dominates(a));
  }

  @Test
  void stockDoesNotEraseLongTermValue() {
    var t = new Trade(item("emerald", 1), item("emerald", 1), null, item("apple", 1), 10, 10);
    assertTrue(t.soldOut());
    assertEquals(Set.of(v(1, t).uuid()), analyze(List.of(v(1, t)), List.of(), 1).recommended());
  }

  @Test
  void crossTradeCoverage() {
    var a = v(1, trade(1, item("a", 1)), trade(1, item("b", 1)));
    var b = v(2, trade(1, item("a", 1)));
    var c = v(3, trade(1, item("b", 1)));
    assertEquals(Set.of(a.uuid()), analyze(List.of(a, b, c), List.of(), 1).recommended());
  }

  @Test
  void backupsCountDistinctVillagers() {
    var a = v(1, trade(1, book(5)));
    var b = v(2, trade(1, book(5)));
    assertEquals(2, analyze(List.of(a, b), List.of(), 2).recommended().size());
  }

  @Test
  void duplicateOffersDoNotCreateBackups() {
    var a = v(1, trade(1, book(5)), trade(1, book(5)));
    var r = analyze(List.of(a), List.of(), 2);
    assertEquals(1, r.recommended().size());
    assertFalse(r.gaps().isEmpty());
  }

  @Test
  void protectedIsAlwaysRetained() {
    var a = v(1, trade(10, book(4)));
    a = a.withManual(VillagerRecord.Manual.PROTECT, "", Set.of());
    var b = v(2, trade(20, book(5)));
    assertEquals(2, analyze(List.of(a, b), List.of(), 1).recommended().size());
  }

  @Test
  void excludedReportsGap() {
    var a = v(1, trade(1, book(5))).withManual(VillagerRecord.Manual.REMOVE, "", Set.of());
    var r = analyze(List.of(a), List.of(), 1);
    assertTrue(r.recommended().isEmpty());
    assertFalse(r.gaps().isEmpty());
  }

  @Test
  void highestLevelAboveCapStillRetained() {
    var a = v(1, trade(1, book(4)));
    var b = v(2, trade(64, book(5)));
    var target = new TargetTrade("s", "book", "sharpness", 1, 10, 1);
    var r = analyze(List.of(a, b), List.of(target), 1);
    assertEquals(Set.of(b.uuid()), r.recommended());
    assertTrue(r.gaps().stream().anyMatch(g -> g.contains("price")));
  }

  @Test
  void targetOnlyExcludesUnrelatedCoverage() {
    var a = v(1, trade(1, book(5)));
    var b = v(2, trade(1, item("apple", 1)));
    var r = analyze(List.of(a, b), List.of(new TargetTrade("s", "book", "sharpness", 1, 0, 1)), 1);
    assertEquals(Set.of(a.uuid()), r.recommended());
    assertEquals(AnalysisResult.Status.REVIEW, r.verdicts().get(b.uuid()).status());
  }

  @Test
  void uninspectedIsYellow() {
    var a =
        new VillagerRecord(
            WORLD,
            new UUID(0, 1),
            "minecraft:librarian",
            1,
            false,
            0,
            0,
            0,
            0,
            null,
            VillagerRecord.Manual.AUTO,
            "",
            Set.of());
    assertEquals(
        AnalysisResult.Status.REVIEW,
        analyze(List.of(a), List.of(), 1).verdicts().get(a.uuid()).status());
  }

  @Test
  void futureTradesReduceConfidence() {
    var a = v(1, trade(1, book(5)));
    var b = v(2, trade(10, book(4)));
    b =
        b.withSnapshot(
            new TradeSnapshot(
                b.snapshot().trades(), 1, 100, TradeSnapshot.Completeness.UNLOCKED_LEVELS_REMAIN));
    var r = analyze(List.of(a, b), List.of(), 1);
    assertEquals(AnalysisResult.Confidence.LOW, r.verdicts().get(b.uuid()).confidence());
    assertEquals(AnalysisResult.Status.REDUNDANT, r.verdicts().get(b.uuid()).status());
  }

  @Test
  void unloadedReplacementCannotConfirm() {
    var a = v(1, trade(1, book(5)));
    var b = v(2, trade(10, book(4)));
    var r = new Optimizer().analyze(List.of(a, b), List.of(), Set.of(b.uuid()), 100, 1000, 1);
    assertEquals(AnalysisResult.Status.REVIEW, r.verdicts().get(b.uuid()).status());
    assertFalse(r.verdicts().get(b.uuid()).replacements().getFirst().confirmed());
  }

  @Test
  void explanationContainsUUIDAndDifferences() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(10, book(5)));
    var r = analyze(List.of(a, b), List.of(), 1).verdicts().get(b.uuid()).replacements().getFirst();
    assertEquals(a.uuid(), r.replacement());
    assertEquals(-8, r.baseDifference().values().iterator().next());
  }

  @Test
  void deterministicAcrossInputOrder() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(2, book(5)));
    assertEquals(analyze(List.of(a, b), List.of(), 1), analyze(List.of(b, a), List.of(), 1));
  }

  @Test
  void regionIsThreeDimensionalAndInclusive() {
    var r = new TradingHallRegion("r", "hall", WORLD.dimension(), 0, 0, 0, 2, 64, 2, List.of(), 0);
    assertTrue(r.contains(v(1)));
    assertFalse(r.contains(v(3)));
  }

  @Test
  void priceOrderingIsNumeric() {
    var a = v(1, trade(10, book(5)));
    var b = v(2, trade(2, book(5)));
    assertEquals(Set.of(b.uuid()), analyze(List.of(a, b), List.of(), 1).recommended());
  }

  @Test
  void moreExpensiveBackupStillCoversCommodity() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(10, book(5)));
    var r = analyze(List.of(a, b), List.of(), 2);
    assertEquals(2, r.recommended().size());
    assertTrue(r.gaps().isEmpty());
  }

  @Test
  void targetBackupCanUseDifferentPrices() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(10, book(5)));
    var r = analyze(List.of(a, b), List.of(new TargetTrade("s", "book", "sharpness", 1, 0, 2)), 1);
    assertEquals(2, r.recommended().size());
    assertTrue(r.gaps().isEmpty());
  }

  @Test
  void backupOverPriceCapReportsGap() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(10, book(5)));
    var r = analyze(List.of(a, b), List.of(new TargetTrade("s", "book", "sharpness", 1, 5, 2)), 1);
    assertEquals(2, r.recommended().size());
    assertTrue(r.gaps().stream().anyMatch(g -> g.contains("price")));
  }

  @Test
  void unknownCurrencyNeverBecomesFree() {
    var a = new Trade(item("diamond", 1), item("diamond", 1), null, book(5), 0, 1);
    assertFalse(TradeComparison.replaces(a, trade(64, book(4))));
  }

  @Test
  void staleRecommendedRecordStillNeedsReview() {
    var a = v(1, trade(1, book(5)));
    var r = new Optimizer().analyze(List.of(a), List.of(), Set.of(a.uuid()), 2000, 1000, 1);
    assertTrue(r.recommended().contains(a.uuid()));
    assertEquals(AnalysisResult.Status.REVIEW, r.verdicts().get(a.uuid()).status());
  }

  @Test
  void protectedRecordOverridesStaleness() {
    var a = v(1, trade(1, book(5))).withManual(VillagerRecord.Manual.PROTECT, "", Set.of());
    var r = new Optimizer().analyze(List.of(a), List.of(), Set.of(), 2000, 1000, 1);
    assertEquals(AnalysisResult.Status.KEEP, r.verdicts().get(a.uuid()).status());
  }
}
