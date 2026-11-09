package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldStorageTest {
    @TempDir Path directory;

    private WorldData world(String server, String dataset, String dimension) {
        var identity = new WorldIdentity(server, dataset, dimension);
        var original = AnalysisTest.v(1, AnalysisTest.trade(5, AnalysisTest.book(4)));
        var record = new VillagerRecord(identity, original.uuid(), original.profession(), original.level(),
            false, 0, 0, 0, 1, original.snapshot(), VillagerRecord.Manual.KEEP, "note", Set.of("tag"));
        var target = new TargetTrade("t", "minecraft:book", "", 1, 0, 1);
        var region = new TradingHallRegion("r", "Hall", dimension, 0, 0, 0, 5, 5, 5,
            List.of(target), 0xFF99AAFF);
        return new WorldData(4, identity, List.of(CachedTrade.from(record)), List.of(target), List.of(region));
    }

    private Path path(WorldData data) {
        return directory.resolve(JsonStore.fileKey(data.identity()) + ".json");
    }

    @Test
    void clearOneWorldKeepsOtherDimensionsDatasetsAndServers() throws Exception {
        var selected = world("a", "default", "overworld");
        var nether = world("a", "default", "nether");
        var dataset = world("a", "other", "overworld");
        var server = world("b", "default", "overworld");
        try (var store = new JsonStore(e -> fail(e))) {
            for (var data : List.of(selected, nether, dataset, server)) store.save(path(data), data);
            assertEquals(1, store.clearVillagers(directory, selected.identity()::equals).get());
            assertEquals(selected.clearVillagers(), store.read(path(selected), WorldData.class, () -> null).get());
            for (var data : List.of(nether, dataset, server))
                assertEquals(data, store.read(path(data), WorldData.class, () -> null).get());
        }
    }

    @Test
    void clearServerCoversAllItsWorldsAndIsDurableOnCompletion() throws Exception {
        var a = world("a", "default", "overworld");
        var b = world("a", "other", "nether");
        var other = world("b", "default", "overworld");
        try (var store = new JsonStore(e -> fail(e))) {
            for (var data : List.of(a, b, other)) store.save(path(data), data);
            // This races queued saves deliberately: old snapshots must be drained before the clear.
            assertEquals(2, store.clearVillagers(directory, id -> id.server().equals("a")).get());
        }
        try (var reopened = new JsonStore(e -> fail(e))) {
            assertEquals(a.clearVillagers(), reopened.read(path(a), WorldData.class, () -> null).get());
            assertEquals(b.clearVillagers(), reopened.read(path(b), WorldData.class, () -> null).get());
            assertEquals(other, reopened.read(path(other), WorldData.class, () -> null).get());
        }
    }

    @Test
    void catalogAndClearProtectCorruptFutureAndMismatchedFiles() throws Exception {
        var data = world("a", "default", "overworld");
        var corrupt = directory.resolve("broken.json");
        var future = directory.resolve("future.json");
        var mismatch = directory.resolve("wrong-name.json");
        Files.writeString(corrupt, "broken { original");
        Files.writeString(future, "{\"version\":999}");
        try (var store = new JsonStore(e -> {})) {
            store.save(path(data), data);
            store.flush().get();
            Files.copy(path(data), mismatch);
            String original = Files.readString(mismatch);
            assertEquals(List.of(data), store.worlds(directory).get());
            assertEquals(1, store.clearVillagers(directory, id -> true).get());
            for (var file : List.of(corrupt, future, mismatch)) assertTrue(store.blocked(file));
            assertEquals("broken { original", Files.readString(corrupt));
            assertEquals("{\"version\":999}", Files.readString(future));
            assertEquals(original, Files.readString(mismatch));
        }
    }

    @Test
    void missingDirectoryIsEmptyButListingFailureIsReported() throws Exception {
        try (var store = new JsonStore(e -> {})) {
            var missing = directory.resolve("missing");
            assertTrue(store.worlds(missing).get().isEmpty());
            assertEquals(0, store.clearVillagers(missing, id -> true).get());
            var file = directory.resolve("not-a-directory");
            Files.writeString(file, "original");
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.worlds(file).get());
            assertThrows(java.util.concurrent.ExecutionException.class,
                () -> store.clearVillagers(file, id -> true).get());
            assertEquals("original", Files.readString(file));
        }
    }
}
