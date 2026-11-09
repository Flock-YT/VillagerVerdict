package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.SnapshotDelta;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DeltaTest {
  TradeSnapshot snap(Trade... t) {
    return new TradeSnapshot(List.of(t), 100);
  }

  @Test
  void distinguishPriceChanges() {
    var a = AnalysisTest.trade(2, AnalysisTest.book(5));
    var b = AnalysisTest.trade(3, AnalysisTest.book(5));
    var d = SnapshotDelta.between(snap(a), snap(b));
    assertTrue(d.price());
    assertFalse(d.stock());
    assertFalse(d.unlocked());
  }

  @Test
  void distinguishRestock() {
    var a = AnalysisTest.trade(2, AnalysisTest.book(5));
    var b = new Trade(a.baseInput(), a.actualInput(), null, a.output(), 12, 12);
    var d = SnapshotDelta.between(snap(a), snap(b));
    assertFalse(d.price());
    assertTrue(d.stock());
  }

  @Test
  void distinguishNewTrades() {
    var a = AnalysisTest.trade(2, AnalysisTest.book(5));
    var b = AnalysisTest.trade(3, AnalysisTest.item("apple", 1));
    assertTrue(SnapshotDelta.between(snap(a), snap(a, b)).unlocked());
  }

  @Test
  void sameSnapshotHasNoChanges() {
    var a = snap(AnalysisTest.trade(2, AnalysisTest.book(5)));
    assertEquals(new SnapshotDelta(false, false, false), SnapshotDelta.between(a, a));
  }

  @Test
  void localeDoesNotChangeTradeIdentityOrFingerprint() {
    var t = AnalysisTest.trade(2, AnalysisTest.book(5));
    var o = t.output();
    var renamed =
        new ItemData(
            o.item(), o.count(), o.codecJson(), o.valueComponents(), o.enchantments(), "书");
    var b = new Trade(t.baseInput(), t.actualInput(), null, renamed, t.uses(), t.maxUses());
    assertEquals(t.key(), b.key());
    assertEquals(snap(t).fingerprint(), snap(b).fingerprint());
  }
}
