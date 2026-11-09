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
  void malformedFileNeverOverwritten() throws Exception {
    var path = dir.resolve("world.json");
    Files.writeString(path, "broken { original");
    try (var s = new JsonStore(e -> {})) {
      assertThrows(
          ExecutionException.class,
          () -> s.read(path, WorldData.class, () -> WorldData.empty(id)).get());
      assertTrue(s.blocked(path));
      s.save(path, WorldData.empty(id));
      s.flush().get();
      assertEquals("broken { original", Files.readString(path));
    }
  }

  @Test
  void repairedOrMissingFileUnblocksWrites() throws Exception {
    var path = dir.resolve("repaired.json");
    Files.writeString(path, "broken { original");
    try (var s = new JsonStore(e -> {})) {
      assertThrows(
          ExecutionException.class,
          () -> s.read(path, Settings.class, Settings::defaults).get());
      assertTrue(s.blocked(path));
      Files.delete(path);
      assertEquals(Settings.defaults(), s.read(path, Settings.class, Settings::defaults).get());
      assertFalse(s.blocked(path));
      s.save(path, Settings.defaults());
      s.flush().get();
      assertEquals(Settings.defaults(), s.read(path, Settings.class, Settings::defaults).get());
    }
  }

  @Test
  void nullFileProtected() throws Exception {
    var p = dir.resolve("null.json");
    Files.writeString(p, "null");
    try (var s = new JsonStore(e -> {})) {
      assertThrows(
          ExecutionException.class, () -> s.read(p, Settings.class, Settings::defaults).get());
      assertTrue(s.blocked(p));
    }
  }

  @Test
  void futureVersionProtected() throws Exception {
    var p = dir.resolve("future.json");
    Files.writeString(p, "{\"version\":999}");
    try (var s = new JsonStore(e -> {})) {
      assertThrows(
          ExecutionException.class,
          () -> s.read(p, WorldData.class, () -> WorldData.empty(id)).get());
      assertTrue(s.blocked(p));
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
  void atomicWritesLeaveNoTemporaryFiles() throws Exception {
    try (var s = new JsonStore(e -> fail(e))) {
      var p = dir.resolve("settings.json");
      for (int i = 0; i < 100; i++) s.save(p, Settings.defaults());
      s.flush().get();
      try (var files = Files.list(dir)) {
        assertEquals(List.of(p), files.toList());
      }
      assertEquals(Settings.defaults(), s.read(p, Settings.class, Settings::defaults).get());
    }
  }

  @Test
  void rejectsOldWorldEnvelope() {
    assertThrows(IllegalArgumentException.class,
        () -> new WorldData(3, id, List.of(), List.of(), List.of()));
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
  void rejectDuplicateUUIDAndInvalidCachedTrade() {
    var v = CachedTrade.from(AnalysisTest.v(1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorldData(4, id, List.of(v, v), List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new CachedTrade(v.uuid(), v.profession(), null));
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

  @Test
  void oldSettingsAreRejectedWithoutMigration() throws Exception {
    var p = dir.resolve("old-settings.json");
    try (var store = new JsonStore(e -> {})) {
      store.save(p, Settings.defaults());
      store.flush().get();
      Files.writeString(p, Files.readString(p).replace("\"version\": 4", "\"version\": 3"));
      assertThrows(ExecutionException.class,
          () -> store.read(p, Settings.class, Settings::defaults).get());
      assertTrue(store.blocked(p));
    }
  }
}
