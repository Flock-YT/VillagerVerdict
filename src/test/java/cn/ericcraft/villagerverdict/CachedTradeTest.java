package cn.ericcraft.villagerverdict;

import static cn.ericcraft.villagerverdict.AnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.WorldData;
import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Test;

class CachedTradeTest {
    @Test
    void cacheContainsTradesButNoActiveState() {
        var record = v(1, trade(5, book(5)))
            .withManual(VillagerRecord.Manual.KEEP, "temporary note", Set.of("temporary tag"));
        var cached = CachedTrade.from(record);
        var gson = new Gson();
        var data = new WorldData(4, WORLD, List.of(cached), List.of(), List.of());
        var json = gson.toJson(data);
        assertEquals(data, gson.fromJson(json, WorldData.class));
        var fields = gson.toJsonTree(cached).getAsJsonObject().keySet();
        assertEquals(Set.of("uuid", "profession", "snapshot"), fields);
        assertFalse(json.contains("temporary"));
        assertFalse(json.contains("manual"));
        assertFalse(json.contains("lastSeen"));
        assertSame(record.snapshot(), cached.forProfession(record.profession()));
        assertNull(cached.forProfession("minecraft:farmer"));
    }

    @Test
    void oldWorldDataIsProtectedOnRead() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("vv-old-format");
        var path = directory.resolve("world.json");
        String original = "{\"version\":3}";
        try (var store = new cn.ericcraft.villagerverdict.storage.JsonStore(e -> {})) {
            java.nio.file.Files.writeString(path, original);
            assertThrows(java.util.concurrent.ExecutionException.class,
                () -> store.read(path, WorldData.class, () -> WorldData.empty(WORLD)).get());
            assertFalse(store.save(path, WorldData.empty(WORLD)));
            store.flush().get();
            assertEquals(original, java.nio.file.Files.readString(path));
        } finally {
            java.nio.file.Files.deleteIfExists(path);
            java.nio.file.Files.delete(directory);
        }
    }
}
