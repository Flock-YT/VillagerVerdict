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
          new WorldData(
              2,
              id,
              List.of(AnalysisTest.v(1, AnalysisTest.trade(5, AnalysisTest.book(4)))),
              List.of(),
              List.of()));
      s.save(
          path,
          new WorldData(
              2,
              id,
              List.of(AnalysisTest.v(1, AnalysisTest.trade(2, AnalysisTest.book(5)))),
              List.of(),
              List.of()));
      s.flush().get();
    }
    try (var s = new JsonStore(e -> fail(e))) {
      var data = s.read(path, WorldData.class, () -> WorldData.empty(id)).get();
      assertEquals(1, data.villagers().size());
      assertEquals(
          5,
          data.villagers()
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
  void legacyEnvelopeUpgradesOnNextWrite() throws Exception {
    var p = dir.resolve("old.json");
    try (var s = new JsonStore(e -> fail(e))) {
      s.save(p, new WorldData(1, id, List.of(), List.of(), List.of()));
      s.flush().get();
      var d = s.read(p, WorldData.class, () -> WorldData.empty(id)).get();
      s.save(p, new WorldData(2, d.identity(), d.villagers(), d.targets(), d.regions()));
      s.flush().get();
      assertEquals(2, s.read(p, WorldData.class, () -> WorldData.empty(id)).get().version());
    }
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
  void rejectDuplicateUUIDAndWrongWorld() {
    var v = AnalysisTest.v(1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorldData(2, id, List.of(v, v), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorldData(2, new WorldIdentity("x", "x", "x"), List.of(v), List.of(), List.of()));
  }

  @Test
  void configurationVersionOneMigrates() throws Exception {
    var p = dir.resolve("v1.json");
    try (var s = new JsonStore(e -> fail(e))) {
      s.save(p, Settings.defaults());
      s.flush().get();
      String json =
          Files.readString(p)
              .replace("\"version\": 3", "\"version\": 1")
              .replace("\"statusMarks\": true,", "");
      Files.writeString(p, json);
      var settings = s.read(p, Settings.class, Settings::defaults).get();
      assertEquals(3, settings.version());
      assertTrue(settings.statusMarks());
      assertTrue(settings.autoScan());
    }
  }

  @Test
  void configurationVersionTwoDefaultsAutoScanOn() throws Exception {
    var p = dir.resolve("v2.json");
    try (var s = new JsonStore(e -> fail(e))) {
      s.save(p, Settings.defaults());
      s.flush().get();
      String json =
          Files.readString(p)
              .replace("\"version\": 3", "\"version\": 2")
              .replace("\"autoScan\": true,", "");
      Files.writeString(p, json);
      var settings = s.read(p, Settings.class, Settings::defaults).get();
      assertEquals(3, settings.version());
      assertTrue(settings.autoScan());
    }
  }
}
