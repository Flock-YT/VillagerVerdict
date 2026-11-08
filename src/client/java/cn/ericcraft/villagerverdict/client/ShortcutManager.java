package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.storage.JsonStore;
import cn.ericcraft.villagerverdict.storage.ShortcutBindings;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Called on the client thread. Internal bindings never enter Options.keyMappings. */
public final class ShortcutManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("VillagerVerdict shortcuts");
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path path;
    private final List<VillagerverdictClient.Shortcut> shortcuts;
    private final JsonStore store = new JsonStore(e -> LOG.error("Shortcut data operation failed", e));
    private boolean ready, failed;
    private long loadRevision;

    public ShortcutManager(Path path, List<VillagerverdictClient.Shortcut> shortcuts) {
        this.path = path;
        this.shortcuts = List.copyOf(shortcuts);
    }

    public boolean ready() {
        return ready;
    }

    public boolean editable() {
        return ready && !failed;
    }

    public void load() {
        ready = failed = false;
        long request = ++loadRevision;
        for (var shortcut : shortcuts)
            if (ShortcutBindings.ACTIONS.contains(shortcut.id()))
                set(shortcut.mapping(), InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        store.read(path, ShortcutBindings.class, ShortcutBindings::defaults).whenComplete((data, failure) ->
            minecraft.execute(() -> {
                if (request != loadRevision) return;
                try {
                    if (failure != null) throw new IllegalStateException("Could not read shortcuts", failure);
                    var parsed = new LinkedHashMap<String, InputConstants.Key>();
                    for (var id : ShortcutBindings.ACTIONS)
                        parsed.put(id, InputConstants.getKey(
                            data.bindings().getOrDefault(id, InputConstants.UNKNOWN.getName())));
                    for (var shortcut : shortcuts)
                        if (parsed.containsKey(shortcut.id())) set(shortcut.mapping(), parsed.get(shortcut.id()));
                    KeyMapping.resetMapping();
                } catch (RuntimeException e) {
                    failed = true;
                    LOG.error("Could not load shortcuts; original file preserved", e);
                }
                ready = true;
            }));
    }

    public void bind(VillagerverdictClient.Shortcut shortcut, InputConstants.Key key) {
        if (!editable()) return;
        if (!shortcuts.contains(shortcut)) throw new IllegalArgumentException("unknown shortcut");
        if (!key.equals(InputConstants.UNKNOWN)) {
            for (var other : shortcuts)
                if (other != shortcut && other.mapping().matches(key)) set(other.mapping(), InputConstants.UNKNOWN);
        }
        set(shortcut.mapping(), key);
        save();
    }

    public void reset() {
        if (!editable()) return;
        for (var shortcut : shortcuts) set(shortcut.mapping(), shortcut.mapping().getDefaultKey());
        save();
    }

    private void set(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        mapping.setDown(false);
        while (mapping.consumeClick()) {}
    }

    private void save() {
        KeyMapping.resetMapping();
        minecraft.options.save();
        var bindings = new LinkedHashMap<String, String>();
        for (var shortcut : shortcuts)
            if (ShortcutBindings.ACTIONS.contains(shortcut.id()))
                bindings.put(shortcut.id(), shortcut.mapping().saveString());
        store.save(path, new ShortcutBindings(1, bindings));
    }

    public CompletableFuture<Void> flush() {
        return store.flush();
    }

    public void close() {
        ready = false;
        loadRevision++;
        store.close();
    }
}
