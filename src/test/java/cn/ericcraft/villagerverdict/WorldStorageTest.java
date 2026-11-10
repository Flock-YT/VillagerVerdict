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
        var target = new TargetTrade("t", "minecraft:book", "", 1, 0);
        return new WorldData(5, identity, List.of(CachedTrade.from(record)), List.of(target));
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


}
