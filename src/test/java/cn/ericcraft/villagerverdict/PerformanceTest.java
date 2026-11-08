package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.*;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PerformanceTest {
  @Test
  void analyzeFiveHundredVillagers() {
    var records = new ArrayList<VillagerRecord>();
    for (int i = 0; i < 500; i++) {
      var trades = new ArrayList<Trade>();
      for (int j = 0; j < 8; j++)
        trades.add(
            AnalysisTest.trade(1 + i % 20, AnalysisTest.item("item" + ((i * 8 + j) % 120), 1)));
      records.add(AnalysisTest.v(i, trades.toArray(Trade[]::new)));
    }
    long start = System.nanoTime();
    var result = AnalysisTest.analyze(records, List.of(), 2);
    long elapsed = System.nanoTime() - start;
    assertTrue(result.gaps().isEmpty());
    assertEquals(500, result.verdicts().size());
    System.out.println(
        "VV_BENCHMARK villagers=500 trades=4000 analyze_ms="
            + elapsed / 1e6
            + " retained="
            + result.recommended().size());
  }
}
