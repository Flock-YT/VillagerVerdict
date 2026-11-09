package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path dir;
  WorldIdentity id = AnalysisTest.WORLD;

  @Test
  void restartRestoresLatestSnapshot() throws Exception {
    var path = dir.resolve("world.json");
    try (var s = new JsonStore(e -> fail(e))) {
      s.save(
          path,
          new WorldData(4,
              id,
              List.of(CachedTrade.from(AnalysisTest.v(1, AnalysisTest.trade(5, AnalysisTest.book(4))))),
              List.of(),
              List.of()));
      s.save(
          path,
          new WorldData(4,
              id,
              List.of(CachedTrade.from(AnalysisTest.v(1, AnalysisTest.trade(2, AnalysisTest.book(5))))),
              List.of(),
              List.of()));
      s.flush().get();
    }
    try (var s = new JsonStore(e -> fail(e))) {
      var data = s.read(path, WorldData.class, () -> WorldData.empty(id)).get();
      assertEquals(1, data.cachedTrades().size());
      assertEquals(
          5,
          data.cachedTrades()
              .getFirst()
              .snapshot()
              .trades()
              .getFirst()
              .output()
              .enchantments()
              .get("sharpness"));
    }
  }





  @Test
  void missingFileUsesDefault() throws Exception {
    try (var s = new JsonStore(e -> fail(e))) {
      assertEquals(
          Settings.defaults(),
          s.read(dir.resolve("settings.json"), Settings.class, Settings::defaults).get());
    }
  }

  @Test
  void identitiesIsolateServerDimensionAndDataset() {
    assertNotEquals(
        JsonStore.fileKey(id),
        JsonStore.fileKey(new WorldIdentity("other", id.dataset(), id.dimension())));
    assertNotEquals(
        JsonStore.fileKey(id),
        JsonStore.fileKey(new WorldIdentity(id.server(), "other", id.dimension())));
    assertNotEquals(
        JsonStore.fileKey(id),
        JsonStore.fileKey(new WorldIdentity(id.server(), id.dataset(), "nether")));
  }



  @Test
  void writesForTwoWorldsDoNotMix() throws Exception {
    var other = new WorldIdentity("other", "default", "nether");
    try (var s = new JsonStore(e -> fail(e))) {
      var p = dir.resolve("a.json");
      var q = dir.resolve("b.json");
      s.save(p, WorldData.empty(id));
      s.save(q, WorldData.empty(other));
      s.flush().get();
      assertEquals(id, s.read(p, WorldData.class, () -> null).get().identity());
      assertEquals(other, s.read(q, WorldData.class, () -> null).get().identity());
    }
  }


  @Test
  void clearingVillagersPreservesWorldConfiguration() {
    var target =
        new TargetTrade("target", "minecraft:enchanted_book", "minecraft:sharpness", 1, 0, 1);
    var region =
        new TradingHallRegion(
            "region",
            "Hall",
            id.dimension(),
            0,
            0,
            0,
            10,
            10,
            10,
            List.of(target),
            0xFF99AAFF);
    var data =
        new WorldData(4,
            id,
            List.of(CachedTrade.from(AnalysisTest.v(1, AnalysisTest.trade(5, AnalysisTest.book(4))))),
            List.of(target),
            List.of(region));

    var cleared = data.clearVillagers();
    assertTrue(cleared.cachedTrades().isEmpty());
    assertEquals(data.identity(), cleared.identity());
    assertEquals(data.targets(), cleared.targets());
    assertEquals(data.regions(), cleared.regions());
  }

  @Test
  void currentSettingsRoundTripWithoutFreshnessFields() throws Exception {
    var p = dir.resolve("settings.json");
    try (var store = new JsonStore(e -> fail(e))) {
      store.save(p, Settings.defaults());
      store.flush().get();
      assertEquals(Settings.defaults(), store.read(p, Settings.class, Settings::defaults).get());
      assertFalse(Files.readString(p).contains("cacheMillis"));
      assertEquals(4, Settings.defaults().version());
    }
  }

}
