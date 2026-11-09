package cn.ericcraft.villagerverdict.storage;

import java.util.Map;
import java.util.Set;

/** Only internal actions live here; the management key belongs to Minecraft's options. */
public record ShortcutBindings(int version, Map<String, String> bindings) {
    public static final Set<String> ACTIONS = Set.of("scan", "mark", "navigate", "cancelNavigate", "probe");

    public ShortcutBindings {
        if (version != 1) throw new IllegalArgumentException("unsupported shortcut version");
        bindings = Map.copyOf(bindings);
        if (!ACTIONS.containsAll(bindings.keySet()) || bindings.values().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("invalid shortcut bindings");
    }

    public static ShortcutBindings defaults() {
        return new ShortcutBindings(1, Map.of());
    }
}
