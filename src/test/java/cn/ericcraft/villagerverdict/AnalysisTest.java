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
    return enchantedBook("minecraft:sharpness", level);
  }

  static ItemData enchantedBook(String enchantment, int level) {
    return new ItemData("minecraft:enchanted_book", 1, "{}", "{}", Map.of(enchantment, level), "enchanted_book");
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
        new TradeSnapshot(List.of(trades), 100),
        VillagerRecord.Manual.AUTO,
        "",
        Set.of());
  }

  static AnalysisResult analyze(
      List<VillagerRecord> records, int backups) {
    return new Optimizer()
        .analyze(records, backups);
  }

  @Test
  void betterTradeChangesDiscardToKeepOnRescan() {
    var best = v(1, trade(5, book(3)));
    var worker = v(2, trade(10, book(1)));
    var before = analyze(List.of(best, worker), 1);
    assertEquals(AnalysisResult.Status.DISCARD, before.verdicts().get(worker.uuid()).status());
    worker = worker.withSnapshot(new TradeSnapshot(List.of(trade(10, book(1)), trade(20, book(5))), 200));
    var after = analyze(List.of(best, worker), 1);
    assertEquals(AnalysisResult.Status.KEEP, after.verdicts().get(worker.uuid()).status());
    assertEquals(AnalysisResult.Status.DISCARD, after.verdicts().get(best.uuid()).status());
  }

  @Test
  void successfulEmptyReadCountsAsScannedDiscard() {
    var worker = v(1);
    assertEquals(AnalysisResult.Status.DISCARD,
        analyze(List.of(worker), 1).verdicts().get(worker.uuid()).status());
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
  void stockDoesNotEraseLongTermValue() {
    var t = new Trade(item("emerald", 1), item("emerald", 1), null, book(5), 10, 10);
    assertTrue(t.soldOut());
    assertEquals(Set.of(v(1, t).uuid()), analyze(List.of(v(1, t)), 1).recommended());
  }

  @Test
  void crossTradeCoverage() {
    var a = v(1, trade(1, enchantedBook("minecraft:mending", 1)), trade(1, enchantedBook("minecraft:multishot", 1)));
    var b = v(2, trade(1, enchantedBook("minecraft:mending", 1)));
    var c = v(3, trade(1, enchantedBook("minecraft:multishot", 1)));
    assertEquals(Set.of(a.uuid()), analyze(List.of(a, b, c), 1).recommended());
  }

  @Test
  void backupsCountDistinctVillagers() {
    var a = v(1, trade(1, book(5)));
    var b = v(2, trade(1, book(5)));
    assertEquals(2, analyze(List.of(a, b), 2).recommended().size());
  }

  @Test
  void duplicateOffersDoNotCreateBackups() {
    var a = v(1, trade(1, book(5)), trade(1, book(5)));
    var r = analyze(List.of(a), 2);
    assertEquals(1, r.recommended().size());
    assertFalse(r.gaps().isEmpty());
  }

  @Test
  void protectedIsAlwaysRetained() {
    var a = v(1, trade(10, book(4)));
    a = a.withManual(VillagerRecord.Manual.KEEP, "", Set.of());
    var b = v(2, trade(20, book(5)));
    assertEquals(2, analyze(List.of(a, b), 1).recommended().size());
  }

  @Test
  void queryPriceCapNeverChangesHighestLevelRetention() {
    var a = v(1, trade(1, book(4)));
    var b = v(2, trade(64, book(5)));
    var query = new TargetTrade("s", "minecraft:enchanted_book", "minecraft:sharpness", 1, 10);
    assertFalse(query.matches(b));
    assertTrue(query.matches(a));
    assertEquals(Set.of(b.uuid()), analyze(List.of(a, b), 1).recommended());
  }

  @Test
  void uninspectedIsNotAnalyzed() {
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
    assertFalse(analyze(List.of(a), 1).verdicts().containsKey(a.uuid()));
  }



  @Test
  void explanationContainsUUIDAndDifferences() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(10, book(5)));
    var r = analyze(List.of(a, b), 1).verdicts().get(b.uuid()).replacements().getFirst();
    assertEquals(a.uuid(), r.replacement());
    assertEquals(-8, r.baseDifference().values().iterator().next());
  }

  @Test
  void deterministicAcrossInputOrder() {
    var a = v(1, trade(2, book(5)));
    var b = v(2, trade(2, book(5)));
    assertEquals(analyze(List.of(a, b), 1), analyze(List.of(b, a), 1));
  }

  @Test
  void manuallyKeptRecordOverridesRecommendations() {
    var a = v(1, trade(1, book(5))).withManual(VillagerRecord.Manual.KEEP, "", Set.of());
    var r = analyze(List.of(a), 1);
    assertEquals(AnalysisResult.Status.KEEP, r.verdicts().get(a.uuid()).status());
  }
}
