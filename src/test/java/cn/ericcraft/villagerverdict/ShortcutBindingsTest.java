package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShortcutBindingsTest {
    @TempDir Path directory;

    @Test
    void missingFileStartsUnboundWithoutImportingGameOptions() throws Exception {
        Files.writeString(directory.resolve("options.txt"),
            "key_key.villagerverdict.scan:key.keyboard.f9\nkey_key.villagerverdict.mark:key.keyboard.f10\n");
        try (var store = new JsonStore(e -> fail(e))) {
            var settings = store.read(directory.resolve("shortcuts.json"), ShortcutBindings.class,
                ShortcutBindings::defaults).get();
            assertTrue(settings.bindings().isEmpty());
        }
    }

    @Test
    void restartRestoresInternalKeysIndependentlyOfManagementKey() throws Exception {
        var path = directory.resolve("shortcuts.json");
        var settings = new ShortcutBindings(1, Map.of("scan", "key.keyboard.f9", "mark", "key.keyboard.f10",
            "cancelNavigate", "key.keyboard.f5"));
        try (var store = new JsonStore(e -> fail(e))) {
            store.save(path, settings);
            store.flush().get();
        }
        try (var store = new JsonStore(e -> fail(e))) {
            assertEquals(settings, store.read(path, ShortcutBindings.class, ShortcutBindings::defaults).get());
        }
        assertThrows(IllegalArgumentException.class,
            () -> new ShortcutBindings(1, Map.of("manage", "key.keyboard.f8")));
    }

}
